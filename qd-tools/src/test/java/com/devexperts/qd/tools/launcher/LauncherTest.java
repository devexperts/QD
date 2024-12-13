/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools.launcher;

import com.devexperts.logging.Logging;
import com.devexperts.qd.tools.Tools;
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.ModuleFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static com.devexperts.qd.tools.launcher.TestModule.Event.CLOSE;
import static com.devexperts.qd.tools.launcher.TestModule.Event.RECONFIGURE;
import static com.devexperts.qd.tools.launcher.TestModule.Event.START;
import static com.devexperts.qd.tools.launcher.TestModule.Event.VALIDATE;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class LauncherTest {

    // TODO: Untested scenarios
    // Configuration changes:
    // - changing config contents of module
    // - module removal
    // - changing name of the module (should be treated as closing one module and opening another one)
    // - changing type of the module (shall be treated as closing existing and creating another)
    // Configuration autoupdate
    // - detecting file change
    // - update by timer
    // - invalid config scenarios (modules shall not be affected if config didn't pass validation)
    // "Catastrophic" failures:
    // - start / reconfigure failure of the module
    // Forced configuration update (like from JMX)
    // Racy scenarios

    private static final String TEST_MODULE_TYPE = TestModuleConfig.MODULE_TYPE;
    public static Logging log = Logging.getLogging(LauncherTest.class);

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private Launcher launcher;
    private TestModuleFactory moduleFactory;
    private ExecutorService executor;
    private final List<String> trace = Collections.synchronizedList(new ArrayList<>());
    private WireMockServer wireMockServer;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        if (launcher != null)
            launcher.close();
        if (moduleFactory != null)
            moduleFactory.reset();
        if (executor != null) {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, SECONDS))
                log.error("Executor did not terminate");
        }
        if (wireMockServer != null)
            wireMockServer.stop();
    }

    @Test
    public void testFactoryLookup() {
        ModuleFactory<TestModuleConfig> factory = Launcher.getModuleFactory(TEST_MODULE_TYPE);
        assertEquals(TEST_MODULE_TYPE, factory.getType().toLowerCase(Locale.ROOT));
        assertEquals(TestModuleFactory.class, factory.getClass());
    }

    // Lifecycle tests

    @Test
    public void testInitialization() throws Exception {
        File configFile = tmpDir.newFile("config.conf");
        writeConfigFile(configFile, "{ name = test, type = " + TEST_MODULE_TYPE + "}");

        launcher = new Launcher(configFile);
        // FIXME: it seems generics here don't help
        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder(TracingTestModule::new);

        launcher.enableWatcher = false;
        launcher.start();

        List<Module> modules = launcher.getModules();
        assertEquals(1, modules.size());
        TestModule module = (TestModule) modules.get(0);
        assertEquals("test", module.getName());
        assertTrue(module.isActive());
        checkInitSequence("test");

        trace.clear();
        writeConfigFile(configFile,
            "{ name = test, type = " + TEST_MODULE_TYPE + "}",
            "{ name = test2, type = " + TEST_MODULE_TYPE + "}"
        );
        assertTrue(launcher.reloadConfig());

        modules = launcher.getModules();
        assertEquals(2, modules.size());
        // The original module is expected to be in place & passed reconfiguration
        assertTrue(modules.contains(module));
        assertTrue(module.isActive());
        checkReconfigureSequence("test");

        // New module shall be also intact
        Module module2 = modules.stream().filter(t -> t.getName().equals("test2")).findFirst().get();
        assertTrue(module.isActive());
        checkInitSequence("test2");

        //dumpTrace(trace);
        trace.clear();
        launcher.close();
        assertFalse(module.isActive());
        assertFalse(module2.isActive());
    }

    @Test
    public void testInitializationWithURL() throws Exception {
        tmpDir.newFolder("__files");
        File configFile = tmpDir.newFile("__files/config.conf");
        writeConfigFile(configFile, "{ name = test, type = " + TEST_MODULE_TYPE + "}");

        WireMockServer wm = getWireMockServer();
        wm.stubFor(
            get("/conf/config.conf").willReturn(
                ok()
                    .withHeader("Content-Type", "application/hocon")
                    .withBodyFile("config.conf"))
        );
        // LockSupport.park();

        launcher = new Launcher(new URL("http://127.0.0.1:" + wm.port() + "/conf/config.conf"));
        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder(TracingTestModule::new);

        launcher.enableWatcher = false;
        launcher.start();

        List<Module> modules = launcher.getModules();
        assertEquals(1, modules.size());
        TestModule module = (TestModule) modules.get(0);
        assertEquals("test", module.getName());
        assertTrue(module.isActive());
        checkInitSequence("test");

        trace.clear();
        writeConfigFile(configFile,
            "{ name = test, type = " + TEST_MODULE_TYPE + "}",
            "{ name = test2, type = " + TEST_MODULE_TYPE + "}"
        );
        assertTrue(launcher.reloadConfig());

        modules = launcher.getModules();
        assertEquals(2, modules.size());
        // The original module is expected to be in place & passed reconfiguration
        assertTrue(modules.contains(module));
        assertTrue(module.isActive());
        checkReconfigureSequence("test");

        // New module shall be also intact
        Module module2 = modules.stream().filter(t -> t.getName().equals("test2")).findFirst().get();
        assertTrue(module.isActive());
        checkInitSequence("test2");

        //dumpTrace(trace);
        trace.clear();
        launcher.close();
        assertFalse(module.isActive());
        assertFalse(module2.isActive());
    }

    // Test launcher module invocation from a qds-tools jar CLI ("java -jar qds-tools.jar launcher ...")
    @Test
    public void testToolsStart() throws Exception {
        File configFile = tmpDir.newFile("config.conf");
        writeConfigFile(configFile, "{ name = test, type = " + TEST_MODULE_TYPE + "}");

        AtomicBoolean moduleActive = new AtomicBoolean(true);
        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder((context) ->
            new TracingTestModule(context) {
                @Override
                public boolean isActive() {
                    return moduleActive.get();
                }
            });

        Future<Boolean> tools = invokeToolsWithLauncher(false, configFile);
        await("module initialized").atMost(60, SECONDS).until(() -> trace.contains("test:" + START));
        // Give launcher time to stabilize
        LockSupport.parkNanos(MILLISECONDS.toNanos(Launcher.DEFAULT_START_TIMEOUT * 3 / 2));
        assertFalse("Launcher should work while modules are active", tools.isDone());
        // shutdown the tool
        moduleActive.set(false);
        await("Tools.invoke finished").atMost(60, SECONDS).until(tools::isDone);
        assertTrue("Tools shall finish successfully", tools.get());
        assertThat("Test module shall be closed", trace, containsInRelativeOrder("test:" + CLOSE));
    }

    // Test launcher tool invocation from a qds-tools jar CLI with config check option
    @Test
    public void testToolsConfigCheck() throws Exception {
        File configFile = tmpDir.newFile("config.conf");
        writeConfigFile(configFile, "{ name = test, type = " + TEST_MODULE_TYPE + "}\n");

        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder(TracingTestModule::new);

        Future<Boolean> tools = invokeToolsWithLauncher(true, configFile);
        await("module validated").atMost(60, SECONDS).until(() -> trace.contains("test:" + VALIDATE));
        await("Tools.invoke finished").atMost(60, SECONDS).until(tools::isDone);
        assertTrue("Tools shall finish successfully", tools.get());
    }

    // Test launcher tool invocation from a qds-tools jar CLI with config check option and no config
    @Test
    public void testToolsConfigCheckNoConfig() throws Exception {
        doTestNoConfig(true);
    }

    // Test launcher tool invocation from a qds-tools jar CLI with no config
    @Test
    public void testToolsStartNoConfig() throws Exception {
        doTestNoConfig(false);
    }

    private void doTestNoConfig(boolean withCheckOption) throws InterruptedException, ExecutionException {
        File configFile = new File(tmpDir.getRoot(), "config.conf");

        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder(TracingTestModule::new);

        Future<Boolean> tools = invokeToolsWithLauncher(withCheckOption, configFile);
        await("Tools.invoke finished").atMost(60, SECONDS).until(tools::isDone);
        assertThat(trace, empty());
        assertFalse("Tools shall finish with error", tools.get());
    }

    private Future<Boolean> invokeToolsWithLauncher(boolean withCheckOption, File configFile) {
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "asyncTools"));
        return executor.submit(withCheckOption ?
            (() -> Tools.invoke(Launcher.class.getSimpleName(), "--check", configFile.getPath())) :
            (() -> Tools.invoke(Launcher.class.getSimpleName(), configFile.getPath()))
        );
    }

    // Test launcher invocation from a qds-tools jar CLI with config check option and some module validation failed
    @Test
    public void testToolsConfigCheckValidationFailed() throws Exception {
        doTestValidationFailed(true);
    }

    // Test launcher invocation from a qds-tools jar CLI with some module validation failed
    @Test
    public void testToolsStartValidationFailed() throws Exception {
        doTestValidationFailed(false);
    }

    private void doTestValidationFailed(boolean whithCheckOption) throws Exception {
        File configFile = tmpDir.newFile("config.conf");
        writeConfigFile(configFile, "{ name = test, type = " + TEST_MODULE_TYPE + "}");

        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder((context) ->
            new TracingTestModule(context) {
                @Override
                public Object validate(TestModuleConfig config) {
                    super.validate(config);
                    return "some validation error";
                }
            });

        Future<Boolean> tools = invokeToolsWithLauncher(whithCheckOption, configFile);
        await("Tools.invoke finished").atMost(60, SECONDS).until(tools::isDone);
        assertThat(trace, contains("test:" + VALIDATE));
        assertFalse("Tools shall finish with error", tools.get());
    }

    @Test
    public void testConfigWatcherCheckPeriod() throws Exception {
        doTestConfigFileWatching(SECONDS.toMillis(300 + 10), "300s", "1h");
    }

    @Test
    public void testConfigWatcherReadPeriod() throws Exception {
        doTestConfigFileWatching(SECONDS.toMillis(3600 + 10), "0s", "1h");
    }

    void doTestConfigFileWatching(long timeStep, String configCheckPeriod, String configReadPeriod) throws IOException {
        File configFile = new File(tmpDir.getRoot(), "config.conf");
        Config cfg =
            ConfigFactory.parseString(getConfigText("{ name = test, type = " + TEST_MODULE_TYPE + "}"))
                .withValue("launcherConfig.configCheckPeriod", ConfigValueFactory.fromAnyRef(configCheckPeriod))
                .withValue("launcherConfig.configReadPeriod", ConfigValueFactory.fromAnyRef(configReadPeriod));
        writeConfigFile(configFile, cfg);

        moduleFactory = (TestModuleFactory) Launcher.<TestModuleConfig>getModuleFactory(TEST_MODULE_TYPE);
        moduleFactory.setModuleBuilder(TracingTestModule::new);

        AtomicLong clock = new AtomicLong(configFile.lastModified());
        assertTrue(clock.get() > 0);
        launcher = new Launcher(configFile) {
            {
                activityPollPeriod = 50; // speedup polling
            }

            @Override
            protected long now() {
                return clock.get();
            }
        };
        launcher.start();
        await("module initialized").atMost(30, SECONDS).until(() -> trace.contains("test:" + START));
        trace.clear();
        Config cfg2 =
            ConfigFactory.parseString(getConfigText("{ name = test2, type = " + TEST_MODULE_TYPE + "}"))
                .withValue("launcherConfig.configCheckPeriod", ConfigValueFactory.fromAnyRef(configCheckPeriod))
                .withValue("launcherConfig.configReadPeriod", ConfigValueFactory.fromAnyRef(configReadPeriod));
        writeConfigFile(configFile, cfg2);
        // we could check here that config would be ignored, until the time is passed.
        clock.addAndGet(timeStep);
        assertThat(clock.get(), greaterThan(configFile.lastModified()));
        await("config is updated").atMost(30, SECONDS).until(() -> trace.contains("test2:" + START));
    }

    private void checkInitSequence(String module) {
        assertThat(trace, containsInRelativeOrder(
            module + ":" + VALIDATE,
            module + ":" + START
        ));
        assertThat(trace, not(hasItem(module + ":" + RECONFIGURE)));
    }

    private void checkReconfigureSequence(String module) {
        assertThat(trace, containsInRelativeOrder(
            module + ":" + VALIDATE,
            module + ":" + RECONFIGURE
        ));
        assertThat(trace, not(hasItem(module + ":" + START)));
    }

    private static void dumpTrace(List<String> trace) {
        System.out.println("trace: [\n" + String.join("\n", trace) + "\n]");
    }

    private static void writeConfigFile(File configFile, String... modules) throws IOException {
        String configText = getConfigText(modules);
        Files.write(configFile.toPath(), configText.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeConfigFile(File configFile, Config config) throws IOException {
        ConfigRenderOptions options = ConfigRenderOptions.defaults().setJson(false);
        String configText = config.root().render(options);
        Files.write(configFile.toPath(), configText.getBytes(StandardCharsets.UTF_8));
    }

    private static String getConfigText(String... modules) {
        return
            "{\n" +
            "    launcherConfig: {\n" +
            "        configCheckPeriod = 0s\n" +
            "        configReadPeriod = 0s\n" +
            "    }\n" +
            "    endpointProperties: {\n" +
            "        mars.root = \"test\"\n" +
            "    }\n" +
                "    " + Launcher.CFG_MODULES_PATH + ": [\n" + String.join("\n", modules) + "\n]\n" +
            "}\n";
    }

    private WireMockServer getWireMockServer() {
        // TODO: WireMock supports JUnit rules that would manage server automatically, but will start it for each test
        //     (or keep running for whole class, which would require additional management). Let's reconsider WireMock
        //     management when/if remote tests will be grouped in a separate test-class.
        if (wireMockServer == null) {
            //Logging.getLogging("org.eclipse.jetty.util").configureDebugEnabled(false);
            WireMockServer wm = new WireMockServer(
                options()
                    .dynamicPort()
                    .usingFilesUnderDirectory(tmpDir.getRoot().getAbsolutePath())
            );
            wm.start();
            log.debug("Started WireMock server: httpPort=" + wm.port());
            wireMockServer = wm;
        }
        return wireMockServer;
    }

    private class TracingTestModule extends TestModule {

        public TracingTestModule(ModuleContext context) {
            super(context);
        }

        @Override
        public void trace(Event event, Object data) {
            super.trace(event, data);
            trace.add(name + ":" + event);
        }
    }
}
