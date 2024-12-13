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

import com.devexperts.annotation.Experimental;
import com.devexperts.io.URLInputStream;
import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.qd.config.ConfigProvider;
import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.qd.tools.AbstractTool;
import com.devexperts.qd.tools.Option;
import com.devexperts.qd.tools.ToolSummary;
import com.devexperts.qd.tools.Tools;
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.services.Services;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.devexperts.qd.tools.launcher.Launcher.State.CLOSED;
import static com.devexperts.qd.tools.launcher.Launcher.State.INITIALIZED;
import static com.devexperts.qd.tools.launcher.Launcher.State.NEW;
import static com.devexperts.qd.tools.launcher.Launcher.State.STARTED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Launcher controls execution of long-living services (called "modules") defined with a centralized configuration file.
 *
 * <p>EXPERIMENTAL: the whole Launcher-related functionality is in experimental status
 */
@ToolSummary(
    info = "[EXPERIMENTAL] Launches long-living services",
    argString = {"<config>"},
    arguments = {
        "<config> -- configuration file path (HOCON format)" // TODO: support remote URL
    }
)
@ServiceProvider
@Experimental
public class Launcher extends AbstractTool implements LauncherMXBean, Closeable {

    enum State { NEW, INITIALIZED, STARTED, CLOSED }

    // TODO: log limit shall be configurable option
    static final int MODULE_EVENT_LOG_LIMIT =
        SystemProperties.getIntProperty(Launcher.class, "moduleEventLogLimit", 100_000);

    public static final String CFG_MODULES_PATH = "modules";
    private static final String CFG_ENDPOINT_PROPERTIES_PATH = "endpointProperties";
    private static final String CFG_LAUNCHER_CONFIG_PATH = "launcherConfig";

    private static final Map<String, ModuleFactory<?>> MODULE_FACTORIES = lookupModuleFactories();
    static final long DEFAULT_START_TIMEOUT = 1000; // Default timeout launcher waits for modules activation (millis)
    static final long DEFAULT_POLL_PERIOD = 1000; // Default period launcher polls for modules status (millis)

    private final Option check = new Option('c', "check", "check config without launching any service");

    long startTimeout = DEFAULT_START_TIMEOUT; // wait for modules to start on initialization (millis)
    long activityPollPeriod = DEFAULT_POLL_PERIOD; // period of polling modules activity

    boolean enableWatcher = true; // tests can disable activity watcher

    private URL configUrl; // configuration URL (may be null)
    private Config config; // top-level configuration in effect as acquired from configUrl or provided in constructor
    private volatile LauncherConfig launcherConfig;

    private Properties endpointProps;
    private JMXEndpoint jmxEndpoint;
    private MARSEndpoint marsEndpoint;
    private Management.Registration launcherRegistration;

    // FIXME: SynchronizedIndexedSet ?
    private final Map<String, ModuleContainer> modules = new ConcurrentHashMap<>();
    private final List<DefaultEventLog> closedModules = new ArrayList<>();

    private Watcher watcher; // watcher resposible for config updates and closing when all modules finished
    private volatile State state = NEW;

    public static void main(String[] args) {
        Tools.executeSingleTool(Launcher.class, args);
    }

    // AbstractTool shall have a public default constructor
    public Launcher() {
        // Fail-fast in qds-tools initialization
    }

    Launcher(URL configUrl) {
        init(configUrl, readConfig(configUrl), true);
    }

    Launcher(File configFile) throws IOException {
        this(configFile.toURI().toURL());
    }

    Launcher(Config config, boolean activateMonitoring) {
        init(null, config, activateMonitoring);
    }

    private void init(URL configUrl, Config config, boolean activate) {
        assert state == NEW;
        state = INITIALIZED;
        this.configUrl = configUrl;
        this.config = config;

        launcherConfig = getLauncherConfig(config);

        // FIXME: is endpointProperties a mandatory element?
        endpointProps = getEndpointProps(config.getConfig(CFG_ENDPOINT_PROPERTIES_PATH));

        if (!activate)
            return;

        jmxEndpoint = JMXEndpoint.newBuilder().withProperties(endpointProps).acquire();
        marsEndpoint = MARSEndpoint.newBuilder().withProperties(endpointProps).acquire();

        // FIXME: can we have multiple Launchers (at least for test)?
        launcherRegistration =
            Management.registerMBean(this, LauncherMXBean.class, "com.devexperts.mars:type=Launcher");
    }

    private LauncherConfig getLauncherConfig(Config config) {
        return ConfigProvider.getConfigBean(config.getConfig(CFG_LAUNCHER_CONFIG_PATH), LauncherConfig.class);
    }

    @Override
    protected Option[] getOptions() {
        return new Option[]{ check };
    }

    @Override
    public List<Closeable> closeOnExit() {
        return Collections.singletonList(this);
    }

    @Override
    public Thread mustWaitForThread() {
        return watcher != null ? watcher.getThread() : null;
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();
        if (args.length != 1)
            wrongNumberOfArguments();

        URL configUrl = convertToUrl(args[0]);
        Config config;
        try {
            log.info("Loading configuration from " + configUrl.toExternalForm() + "...");
            config = readConfig(configUrl);
        } catch (Throwable t) {
            log.error("Configuration error", t);
            throw new RuntimeException("Failed to read config at the startup, exiting...");
        }

        if (check.isSet())  { // just validate modules config and exit
            log.info("Validating configuration ...");
            init(configUrl, config, false);
            if (validateModulesConfig(config) == null)
                throw new RuntimeException("Config validation failed");
            log.info("Config validation successfully completed");
            return;
        }

        boolean started = false;
        try {
            init(configUrl, config, true);
            start();
            started = true;
        } finally {
            // cleanup if startup failed. Tools environment doesn't handle this case for the moment
            if (!started)
                close();
        }
    }

    /**
     * Initialize and run modules as specified by the launcher config.
     * @throws RuntimeException if some module initialization failed.
     */
    synchronized void start() {
        if (state != INITIALIZED)
            throw new IllegalStateException("Unexpected launcher state: " + state);
        state = STARTED;
        configureAllModules(config, true);
        if (!awaitCondition(startTimeout, 100, this::isActive)) {
            throw new RuntimeException("Failed to start any module at startup, exit");
        }
        if (enableWatcher) {
            watcher = new Watcher();
            watcher.start();
        }
    }

    @Override
    public boolean isActive() {
        return modules.values().stream().anyMatch(ModuleContainer::isActive);
    }

    @Override
    public synchronized void forceReadConfig() {
        if (watcher != null) {
            watcher.forceReadConfig();
        } else {
            // this should never happen in a normal configuration
            log.error("Config watcher is not initialized");
        }
    }

    @Override
    public String reportCurrentState(String regex, String moduleRegex) {
        Matcher moduleMatcher = getMatcher(moduleRegex);
        StringBuilder sb = new StringBuilder();
        for (String name : new TreeSet<>(modules.keySet())) {
            if (moduleMatcher.reset(name).find())
                sb.append(modules.get(name).reportCurrentState(regex));
        }
        return sb.toString();
    }

    /**
     * Builds case-insensitive matcher by a string provided by user.
     * Treat empty/null/"*" values as "match-all".
     *
     * <p>TODO: handle other usual mistakes
     *
     * @param regex regex-string provided by user
     * @return Matcher object corresponding to the provided spec
     *
     */
    private static Matcher getMatcher(String regex) {
        if (regex == null || regex.isEmpty() || regex.equals("*"))
            regex = ".*";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher("");
    }

    @Override
    public String reportEventLog(String regex, String moduleRegex) {
        Matcher moduleMatcher = getMatcher(moduleRegex);
        StringBuilder sb = new StringBuilder();
        for (String name : new TreeSet<>(modules.keySet())) {
            if (moduleMatcher.reset(name).find())
                sb.append(modules.get(name).getEventLog().reportEventLog(regex));
        }
        return sb.toString();
    }

    @Override
    public String reportClosedModulesEventLogs(String regex, String moduleRegex) {
        Matcher moduleMatcher = getMatcher(moduleRegex);
        StringBuilder sb = new StringBuilder();
        for (DefaultEventLog eventLog : closedModules) {
            if (moduleMatcher.reset(eventLog.getModuleName()).find())
                sb.append(eventLog.reportEventLog(regex));
        }
        return sb.toString();
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        if (state == CLOSED)
            return;
        state = CLOSED;
        log.info("Closing launcher ...");
        for (String name : modules.keySet().toArray(new String[0])) {
            closeModule(name);
        }
        if (jmxEndpoint != null)
            jmxEndpoint.release();
        if (marsEndpoint != null)
            marsEndpoint.release();
        if (launcherRegistration != null)
            launcherRegistration.unregister();
        if (watcher != null)
            watcher.stop();
        log.info("Launcher closed");
    }

    // ========== Launcher private implementation ==========

    private void closeModule(String name) {
        try {
            ModuleContainer module = modules.remove(name);
            module.closeModule();
            // FIXME: consider storing ModuleContainer in closed modules list
            closedModules.add(module.getEventLog());
        } catch (Throwable t) {
            log.error("Unexpected error while closing module name " + name, t);
        }
    }

    /**
     * Current time millis. Extracted for test purposes
     * @return current time millis as in {@link System#currentTimeMillis}
     */
    protected long now() {
        return System.currentTimeMillis();
    }

    /**
     * Reload configuration from the external source synchronously.
     *
     * @return true if the configuration was successfully reloaded
     */
    boolean reloadConfig() {
        Config config;
        try {
            log.info("Reloading config...");
            config = readConfig(configUrl);
        } catch (Throwable t) {
            log.error("Configuration error", t);
            log.error("Failed to read config, ignore the bad config and continue");
            return false;
        }
        return updateConfig(config);
    }

    /**
     * Applies provided configuration immediately following all validation phases.
     *
     * @param config configuration to be applied
     * @return true if configuration was applied
     */
    synchronized boolean updateConfig(Config config) {
        if (state == CLOSED)
            return false;
        checkIfEnpointPropertiesModified(config);
        // get launcherConfig before modules update (passive validation)
        LauncherConfig newLauncherConfig = getLauncherConfig(config);
        if (!configureAllModules(config, false))
            return false;
        // apply after modules validation
        if (!launcherConfig.equals(newLauncherConfig)) {
            launcherConfig = newLauncherConfig;
            if (watcher != null)
                watcher.reset();
        }
        this.config = config;
        return true;
    }

    private void checkIfEnpointPropertiesModified(Config config) {
        Config newEndpointPropsConfig = config.getConfig(CFG_ENDPOINT_PROPERTIES_PATH);
        Config currentEndpointPropsConfig = this.config.getConfig(CFG_ENDPOINT_PROPERTIES_PATH);
        if (!Objects.equals(newEndpointPropsConfig, currentEndpointPropsConfig)) {
            log.warn(CFG_ENDPOINT_PROPERTIES_PATH +
                " cofiguration has changed but will be ignored (live update is not supported)");
        }
    }

    /**
     * Apply a new modules' configuration.
     *
     * @implNote the method initially validates the configuration of all modules, if some new modules to be created,
     *      they will be initialized and configured only after the validation phase for all modules has passed.
     *
     * @param config Launcher configuration to be applied
     * @param isStarting true if it's a Launcher initialization phase.
     * @return true if the new configuration was applied.
     * @throws RuntimeException if some module initialization has failed and {@code isStarting == true}
     *      (otherwise error will be reported and swallowed)
     */
    boolean configureAllModules(Config config, boolean isStarting) {
        log.info("Validating modules configuration...");
        Map<String, ModuleUpdate> validatedModules = validateModulesConfig(config);
        if (validatedModules == null) {
            log.error("Validation of modules configuration has failed");
            if (isStarting)
                throw new RuntimeException("Initialization failed");
            return false;
        }
        // FIXME: what if all modules are closed in the middle (won't the launcher auto-close)?
        log.info("Updating modules configuration...");
        // close existing modules that are missing in new config
        for (ModuleContainer module: modules.values().toArray(new ModuleContainer[0])) {
            ModuleUpdate update = validatedModules.get(module.getName());
            if (update == null || update.module != module)
                closeModule(module.getName());
        }

        // register and configure new modules
        for (ModuleUpdate update: validatedModules.values()) {
            configureOneModule(update.module, update.config, isStarting);
        }
        // FIXME: shall we wait for the new modules (re-)activation like on Launcher's startup?
        return true;
    }

    // Pair of a module container (existing or new one) and a fresh config to be applied
    private static class ModuleUpdate {
        ModuleUpdate(ModuleContainer module, Object config) {
            this.module = module;
            this.config = config;
        }

        final ModuleContainer module;
        final Object config;
    }

    /**
     * Validate modules configuration.
     *
     * @param config launcher configuration
     * @return a non-empty map of updated modules (container (existing or new one) + updated config bean)
     *     or {@code null} if validation has failed.
     */
    private Map<String, ModuleUpdate> validateModulesConfig(Config config) {
        if (!config.hasPath(CFG_MODULES_PATH)) {
            log.error("Configuration has no 'modules' element");
            return null;
        }

        Map<String, ModuleUpdate> modUpdates = new LinkedHashMap<>(); // preserve config order just in case
        for (Config moduleConfig : config.getConfigList(CFG_MODULES_PATH)) {
            if (!validateOneModule(moduleConfig, modUpdates))
                return null;
        }
        if (modUpdates.isEmpty()) {
            log.error("No modules configured");
            return null;
        }
        return modUpdates;
    }

    /**
     * Validate module config for a new or already existing module.
     * In case the type of module has changed, it's considered a new module.
     *
     * @param config module configuration node
     * @param modUpdates map of module name to a module container (new or existing) and updated config bean
     * @return true if the config was successfully validated, false otherwise
     */
    private boolean validateOneModule(Config config, Map<String, ModuleUpdate> modUpdates) {
        String type = "?";
        String name = "?";
        try {
            type = config.getString("type");
            name = config.getString("name");
            if (type == null || type.isEmpty() || name == null || name.isEmpty())
                return logError("Module has no type or name: " + config);
            if (modUpdates.containsKey(name))
                return logError("Module '" + name + "' is used by another module: " + config);

            ModuleFactory<?> moduleFactory = getModuleFactory(type);
            if (moduleFactory == null)
                return logError("Unknown module type: " + type);

            // Parse config before creating module to catch errors earlier.
            Object configBean = ConfigProvider.getConfigBean(config, moduleFactory.getConfigClass());

            ModuleContainer module = this.modules.get(name); // check existing container first
            if (module != null && !module.getType().equals(moduleFactory.getType())) {
                // module type changed, consider it a new module
                module = null;
            }
            if (module == null) {
                module = new ModuleContainer(name, endpointProps, moduleFactory);
                module.createModule();
            }
            try {
                Object validationError = module.validate(configBean);
                if (validationError != null)
                    return logError("Invalid config for module type " + type + ", name " + name + ": " + validationError);
            } catch (Throwable e) {
                log.error("Error while validating module type " + type + ", name " + name, e);
                return false;
            }
            modUpdates.put(name, new ModuleUpdate(module, configBean));
            return true;
        } catch (ConfigException e) {
            log.error("Configuration error for module type " + type + ", name " + name, e);
        } catch (Throwable t) {
            log.error("Unexpected error while configuring module type " + type + ", name " + name, t);
        }
        return false;
    }

    private boolean logError(String config) {
        log.error(config);
        return false;
    }

    /**
     * Process initial or updated module configuration.
     * Prerequisites:
     * - module container already initialized with a proper module
     * - config bean is validated
     *
     * @param module ModuleContainer with a module to be configured
     * @param config module configuration bean
     * @param isStarting true if it's a Launcher initialization phase.
     * @throws RuntimeException if module initialization has failed and {@code isStarting == true}
     *      (otherwise error will be reported and swallowed)
     */
    private void configureOneModule(ModuleContainer module, Object config, boolean isStarting) {
        String modType = module.getType();
        String modName = module.getName();
        if (!modules.containsKey(modName)) {
            // register new module
            try {
                module.start(config);
            } catch (Throwable t) {
                log.error("Failed to start module type " + modType + ", name " + modName +
                    (isStarting ? "" : ", ignore bad module and continue"), t);
                if (isStarting) // critical failure if it's the launcher's initialization phase
                    throw new RuntimeException(t);
            }
            modules.put(modName, module);
        } else {
            // reconfigure existing module
            try {
                module.reconfigure(config);
            } catch (Throwable t) {
                log.error("Unexpected error while configuring module type " + modType + ", name " + modName +
                    (isStarting ? "" : ", ignore bad module and continue"), t);
                if (isStarting) // critical failure if it's the launcher's initialization phase
                    throw new RuntimeException(t);
            }
        }
    }
    
    private Config readConfig(URL url) {
        // keep as a separate method so more complex config processing is expected
        ConfigParseOptions options = ConfigParseOptions.defaults().setAllowMissing(false);
        return ConfigFactory.parseURL(url, options).resolve();
    }

    private Config readConfig(File configFile) {
        try {
            URL url = configFile.toURI().toURL();
            return readConfig(url);
        } catch (Throwable t) {
            log.error("Configuration error", t);
        }
        return null;
    }

    public synchronized List<Module> getModules() {
        return modules.values().stream().map(ModuleContainer::getModule).collect(Collectors.toList());
    }

    // ========== Static utility ==========

    private boolean awaitCondition(long timeout, long pollPeriod, BooleanSupplier condition) {
        long deadline = now() + timeout;
        while (!condition.getAsBoolean()) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(pollPeriod));
            if (now() > deadline)
                return condition.getAsBoolean();
        }
        return true;
    }

    private static URL convertToUrl(String configSource) {
        try {
            return URLInputStream.resolveURL(configSource);
        } catch (IOException e) {
            throw new InvalidFormatException("Invalid config path: " + configSource, e);
        }
    }

    private static Properties getEndpointProps(Config config) {
        Properties endpointProps = new Properties();
        //noinspection ResultOfMethodCallIgnored
        config.root().entrySet();
        endpointProps.putAll(getEndpointProps(config.entrySet(), ConfigValue::unwrapped));
        endpointProps.putAll(getEndpointProps(System.getProperties().entrySet(), Function.identity()));
        return endpointProps;
    }

    private static <K, V> Properties getEndpointProps(Set<Map.Entry<K, V>> entries, Function<V, Object> unwrapValue) {
        Properties endpointProps = new Properties();
        MonitoringEndpoint.Builder builder = MonitoringEndpoint.newBuilder();
        for (Map.Entry<K, V> e : entries) {
            // System.out.println(e.getKey() + " = " + e.getValue() + "   " + e.getValue().getClass().getSimpleName());
            // Config.entrySet() skips null values and System.getProperties() can't contain null values at all.
            // Thus, the Value.toString() call below both relies on this fact and verifies it at the same time.
            String key = e.getKey().toString();
            if (builder.supportsProperty(key) && !MonitoringEndpoint.NAME_PROPERTY.equals(key))
                endpointProps.put(key, unwrapValue.apply(e.getValue()).toString());
        }
        return endpointProps;
    }

    static <C> ModuleFactory<C> getModuleFactory(String type) {
        //noinspection unchecked
        return (ModuleFactory<C>) MODULE_FACTORIES.get(type.toLowerCase(Locale.ROOT));
    }

    private static Map<String, ModuleFactory<?>> lookupModuleFactories() {
        Map<String, ModuleFactory<?>> factories = new HashMap<>();
        for (ModuleFactory<?> factory : Services.createServices(ModuleFactory.class, null)) {
            factories.put(factory.getType().toLowerCase(Locale.ROOT), factory);
        }
        return Collections.unmodifiableMap(factories);
    }

    class Watcher implements Runnable {
        private final Thread thread = new Thread(this, "Watcher");
        private volatile boolean stop;
        private File configFile;
        private long prevLastModified;
        private long nextCheckConfig;
        private volatile long nextReadConfig; // next unconditional configuration check time (millis)
        private volatile TimePeriod configReadPeriod;
        private volatile TimePeriod configCheckPeriod;

        public void start() {
            thread.start();
        }

        public Thread getThread() {
            return thread;
        }

        public void stop() {
            stop = true;
            thread.interrupt();
        }

        public void forceReadConfig() {
            nextReadConfig = 0;
        }

        // watch in a separate thread for remaining modules activity and configuration updates (if needed)
        public void run() {
            reset();
            while (!stop) {
                try {
                    LockSupport.parkNanos(MILLISECONDS.toNanos(activityPollPeriod));
                    if (stop)
                        break;
                    if (!isActive()) {
                        log.info("No more active modules, exit");
                        break;
                    }
                    long currentTime = now();
                    if (nextReadConfig < currentTime || checkUpdate(currentTime)) {
                        nextReadConfig = currentTime + configReadPeriod.getTime();
                        reloadConfig();
                        // FIXME: shall we do anything special (change update schedule?) if reload failed?
                    }
                } catch (Throwable t) {
                    log.error("Unexpected error", t);
                }
            }
        }

        public void reset() {
            LauncherConfig config = launcherConfig; // volatile read
            configReadPeriod = config.getConfigReadPeriod();
            if (configUrl != null && configReadPeriod.getTime() != 0) {
                log.info("Unconditional configuration update period: " + configReadPeriod);
                nextReadConfig = now() + configReadPeriod.getTime();
            }
            configCheckPeriod = config.getConfigCheckPeriod();
            if (configUrl != null && configCheckPeriod.getTime() != 0) {
                // TODO: support update checking for http(s) URLs
                if ("file".equals(configUrl.getProtocol())) {
                    log.info("Checking configuration update period: " + configCheckPeriod);
                    configFile = new File(configUrl.getPath());
                    prevLastModified = configFile.lastModified();
                    nextCheckConfig = now() + configCheckPeriod.getTime();
                } else {
                    log.warn("Configuration updates checking is not supported for '" +
                        configUrl.getProtocol() + "' protocol");
                }
            }
        }

        private boolean checkUpdate(long currentTime) {
            // TODO: arbitrary URL support
            if (configFile != null && nextCheckConfig < currentTime) {
                nextCheckConfig = currentTime + configCheckPeriod.getTime();
                long lastModified = configFile.lastModified();
                if (lastModified != prevLastModified) {
                    prevLastModified = lastModified;
                    log.debug("Configuration change detected: " + configFile.getPath() + " - " +
                        TimeFormat.DEFAULT.format(lastModified));
                    return true;
                }
            }
            return false;
        }
    }

}
