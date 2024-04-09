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
package com.devexperts.rmi.test;

import com.devexperts.logging.LogFormatter;
import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.util.SynchronizedIndexedSet;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.stream.Collectors;
import javax.management.ObjectName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RMIMonitoringTest {

    @Rule public Timeout globalTimeout= new Timeout(60, TimeUnit.SECONDS);

    public static final int PORT_00 = (100 + ThreadLocalRandom.current().nextInt(300)) * 100;

    int getPort(int offset) {
        return PORT_00 + offset;
    }

    private RMIEndpoint client;
    private RMIEndpoint server;

    @After
    public void tearDown() {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
    }

    @Test
    public void testRedundantInit() throws IOException, InterruptedException {
        TempLogCatcher logCatcher = new TempLogCatcher();
        logCatcher.start();
        try {
            // this initializes MARS
            MARSNode.getRoot().subNode("test.fail").setValue("Version 1.0");
            // this shall reuse initialized MARS endpoint
            client = RMIEndpoint.createEndpoint();
            String clientPort = ":" + getPort(99);
            client.connect(clientPort);
            client.close();
        } finally {
            Thread.sleep(500); // wait a bit to let it settle writing to log
            logCatcher.stop();
            assertNoErrorsAndWarningsInLog(logCatcher.getLog());
        }
    }

    private void assertNoErrorsAndWarningsInLog(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null)
            assertFalse(line, line.startsWith("E ") || line.startsWith("W "));
    }

    @Test
    public void testResourcesCleanupConnectorOnly() throws InterruptedException {
        Set<String> initialBeans = getBeans();
        Set<String> initialThreads = getThreadNames();

        // we just need a connector to test
        client = RMIEndpoint.newBuilder().withName("testCleanup").build();

        for (int attempt = 1; attempt <= 2; attempt++) {
            String clientPort = ":" + getPort(45);
            client.connect(clientPort + "[name=testCleanupConn]");

            // now check resources that ware created in the process
            Set<String> createdBeans = getCreatedBeans(initialBeans);
            Set<String> createdThreads = getCreatedThreads(initialThreads);

            // root stats
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=testCleanup,c=Any,id=!AnyStats"));
            // named connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            // connectors-specific JMXStats
            assertTrue(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            // acceptor thread
            assertTrue(createdThreads.contains("testCleanupConn-" + clientPort + "-Acceptor"));
            // something else might have been create -- we don't care

            // new disconnect endpoint
            client.disconnect();

            // Threads need time to close
            Thread.sleep(500);

            // <ake sure that connection-specif stuff was cleaned up
            createdBeans = getCreatedBeans(initialBeans);
            createdThreads = getCreatedThreads(initialThreads);

            assertFalse(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            assertFalse(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            assertFalse(createdThreads.contains("testCleanupConn-" + clientPort + "-Acceptor"));
        }

        // completely close endpoint
        client.close();

        // Threads need time to close
        Thread.sleep(500);

        // Everything should be released
        Set<String> resultingBeans = getCreatedBeans(initialBeans);
        Set<String> resultingThreads = getCreatedThreads(initialThreads);

        // dump all remains
        resultingBeans.forEach(System.out::println);
        resultingThreads.forEach(System.out::println);

        // we should not have anything remaining
        assertTrue("Should not leave any JMX beans behind", resultingBeans.isEmpty());
        assertTrue("Should not leave any threads behind", resultingThreads.isEmpty());
    }

    @Test
    public void testResourcesCleanupWithExport() throws InterruptedException {
        Set<String> initialBeans = getBeans();
        Set<String> initialThreads = getThreadNames();

        server = RMIEndpoint.newBuilder()
            .withName("server")
            .withProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY, "11192")
            .withProperty(JMXEndpoint.JMX_RMI_PORT_PROPERTY, "11193")
            .withProperty(MARSNode.MARS_ROOT_PROPERTY, "testClientRoot")
            .withProperty(MARSNode.MARS_ADDRESS_PROPERTY, ":11194")
            .build();

        // we just need a connector to test
        client = RMIEndpoint.newBuilder()
            .withName("client")
            .withProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY, "11195")
            .withProperty(JMXEndpoint.JMX_RMI_PORT_PROPERTY, "11196")
            .withProperty(MARSNode.MARS_ROOT_PROPERTY, "testServerRoot")
            .build();
        client.getClient().setRequestSendingTimeout(3000L);

        for (int attempt = 1; attempt <= 2; attempt++) {
            int port = getPort(12);
            server.connect(":" + port);
            Thread.sleep(500);
            client.connect("localhost:" + port);

            // now check resources that ware created in the process
            Set<String> createdBeans = getCreatedBeans(initialBeans);
            Set<String> createdThreads = getCreatedThreads(initialThreads);

            // adaptors
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=HtmlAdaptor,port=11192"));
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=RmiServer,port=11193"));
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=HtmlAdaptor,port=11195"));
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=RmiServer,port=11196"));
            // root stats
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=server,c=Any,id=!AnyStats"));
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=client,c=Any,id=!AnyStats"));
            // named server connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ServerSocket-RMI"));
            // named client connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ClientSocket-RMI"));
            // connectors-specific JMXStats
            assertTrue(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=server,connector=ServerSocket-RMI,c=Any,id="));
            assertTrue(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=client,connector=ClientSocket-RMI,c=Any,id="));
            // acceptor thread
            assertTrue(createdThreads.contains("ServerSocket-RMI-:" + port + "-Acceptor"));
            assertTrue(createdThreads.contains("localhost:" + port + "-Reader"));
            assertTrue(createdThreads.contains("localhost:" + port + "-Writer"));
            assertTrue(createdThreads.contains("com.devexperts.qd.monitoring:type=HtmlAdaptor,port=11192"));
            assertTrue(createdThreads.contains("com.devexperts.qd.monitoring:type=HtmlAdaptor,port=11195"));

            server.getServer().export(new SimpleStartService(), StartService.class);
            server.getServer().export(new SimpleUpdateService(), UpdateService.class);
            server.getServer().export(new SimpleCloseService(), CloseService.class);
            server.getServer().export(new SimpleActiveLoginService(), ActiveLoginService.class);

            StartService startService = client.getClient().getProxy(StartService.class);
            UpdateService updateService = client.getClient().getProxy(UpdateService.class);
            CloseService closeService = client.getClient().getProxy(CloseService.class);
            ActiveLoginService activeLoginService = client.getClient().getProxy(ActiveLoginService.class);

            startService.start("StartLogin");
            assertEquals("StartLogin", activeLoginService.getActive()[0]);
            assertTrue(closeService.close("StartLogin"));
            assertEquals(activeLoginService.getActive().length, 0);

            startService.start("StartLogin");
            assertTrue(updateService.update("StartLogin", "UpdateLogin"));
            assertEquals("UpdateLogin", activeLoginService.getActive()[0]);
            assertFalse(closeService.close("StartLogin"));
            assertTrue(closeService.close("UpdateLogin"));
            assertEquals(activeLoginService.getActive().length, 0);

            Thread.sleep(500);

            // new disconnect endpoint
            client.disconnect();
            server.disconnect();
            // Threads need time to close
            Thread.sleep(500);

            // Make sure that connection-specif stuff was cleaned up
            createdBeans = getCreatedBeans(initialBeans);
            createdThreads = getCreatedThreads(initialThreads);

            assertFalse(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ServerSocket-RMI"));
            assertFalse(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ClientSocket-RMI"));
            assertFalse(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=server,connector=ServerSocket-RMI,c=Any,id="));
            assertFalse(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=client,connector=ClientSocket-RMI,c=Any,id="));
            assertFalse(createdThreads.contains("localhost:" + port + "-Reader"));
            assertFalse(createdThreads.contains("localhost:" + port + "-Writer"));
        }

        // completely close endpoint
        client.close();
        server.close();

        // Threads need time to close
        Thread.sleep(500);

        // Everything should be released
        Set<String> resultingBeans = getCreatedBeans(initialBeans);
        Set<String> resultingThreads = getCreatedThreads(initialThreads);

        // dump all remains
        resultingBeans.forEach(System.out::println);
        resultingThreads.forEach(System.out::println);

        // we should not have anything remaining
        assertTrue("Should not leave any JMX beans behind", resultingBeans.isEmpty());
        assertTrue("Should not leave any threads behind", resultingThreads.isEmpty());
    }

    interface StartService {
        void start(String login);
    }

    static Set<String> activeLogin = new SynchronizedIndexedSet<>();

    static class SimpleStartService implements StartService {
        @Override
        public void start(String login) {
            activeLogin.add(login);
        }
    }

    interface UpdateService {
        boolean update(String oldLogin, String newLogin);
    }

    static class SimpleUpdateService implements UpdateService {
        @Override
        public boolean update(String oldLogin, String newLogin) {
            boolean result = activeLogin.remove(oldLogin);
            if (!result)
                return false;
            result = activeLogin.add(newLogin);
            return result;
        }
    }

    interface CloseService {
        boolean close(String login);
    }

    static class SimpleCloseService implements CloseService {
        @Override
        public boolean close(String login) {
            return activeLogin.remove(login);
        }
    }

    interface ActiveLoginService {
        String[] getActive();
    }

    static class SimpleActiveLoginService implements ActiveLoginService {
        @Override
        public String[] getActive() {
            return activeLogin.toArray(new String[activeLogin.size()]);
        }
    }

    private boolean containsStartingWith(Set<String> set, String prefix) {
        for (String s : set) {
            if (s.startsWith(prefix))
                return true;
        }
        return false;
    }

    private Set<String> getBeans() {
        Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer().queryNames(null, null);
        return names.stream()
            .map(name -> name.getDomain() + ":" + name.getKeyPropertyListString())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> getCreatedBeans(Set<String> initialBeans) {
        Set<String> createdBeans = getBeans();
        createdBeans.removeAll(initialBeans);
        return createdBeans;
    }

    // collects all non-interrupted threads
    private Set<String> getThreadNames() {
        return ThreadCleanCheck.getThreads().stream()
            .filter(t -> !t.isInterrupted())
            .map(Thread::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> getCreatedThreads(Set<String> initialThreads) {
        Set<String> createdThreads = getThreadNames();
        createdThreads.removeAll(initialThreads);
        return createdThreads;
    }

    private static class TempLogCatcher {
        private final ByteArrayOutputStream out;
        private final StreamHandler logHandler;

        public TempLogCatcher() {
            // Initialize dxlib logging
            // Note that this logging only works for DefaultLogging based java.util.logging!
            Logging.getLogging("").configureDebugEnabled(true);

            out = new ByteArrayOutputStream();
            logHandler = new StreamHandler(out, new LogFormatter());
            logHandler.setLevel(Level.ALL);
        }

        public void start() {
            Logger.getLogger("").addHandler(logHandler);
        }

        public void stop() {
            Logger.getLogger("").removeHandler(logHandler);
            logHandler.close();
        }

        public BufferedReader getLog() {
            return new BufferedReader(new StringReader(out.toString()));
        }
    }
}
