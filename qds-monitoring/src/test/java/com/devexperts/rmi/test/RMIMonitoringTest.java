/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.test;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.management.ObjectName;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.util.SynchronizedIndexedSet;
import junit.framework.TestCase;

public class RMIMonitoringTest extends TestCase {

    private RMIEndpoint client;
    private RMIEndpoint server;

    @Override
    public void tearDown() {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
    }

    public void testRedundantInit() throws IOException, InterruptedException {
        String tmpLogFileName = "RMIMonitoringTest.log";
        new File(tmpLogFileName).delete();
        Logging.configureLogFile(tmpLogFileName);
        try {
            // this initializes mars
            MARSNode.getRoot().subNode("test.fail").setValue("Version 1.0");
            // this shall reuse initialized mars endpoint
            client = RMIEndpoint.createEndpoint();
            client.connect(":9999");
            client.close();
        } finally {
            Thread.sleep(500); // wait a bit to let it settle writing to log
            Logging.configureLogFile(System.getProperty("log.file"));
            assertNoErrorsAndWarningsInLog(tmpLogFileName);
            assertTrue(new File(tmpLogFileName).delete());
        }
    }

    private void assertNoErrorsAndWarningsInLog(String fileName) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(fileName));
        try {
            String line;
            while ((line = in.readLine()) != null)
                assertFalse(line, line.startsWith("E ") || line.startsWith("W "));
        } finally {
            in.close();
        }
    }

    public void testResourcesCleanupConnectorOnly() throws InterruptedException {
        Set<String> initialBeans = getBeans();
        Set<String> initialThreads = getThreadNames();

        // we just need a connector to test
        client = RMIEndpoint.newBuilder().withName("testCleanup").build();

        for (int attempt = 1; attempt <= 2; attempt++) {
            client.connect(":12345[name=testCleanupConn]");

            // now check resources that ware created in the process
            Set<String> createdBeans = getCreatedBeans(initialBeans);
            Set<String> createdThreads = getCreatedThreads(initialThreads);

            // root stats
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=testCleanup,c=Any,id=!AnyStats"));
            // named connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            // connectors-specific JMXStats
            assertTrue(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            // acceptor thread
            assertTrue(createdThreads.contains("testCleanupConn-:12345-Acceptor"));
            // something else might have been create -- we don't care

            // new disconnect endpoint
            client.disconnect();

            // Threads need time to close
            Thread.sleep(500);

            // <ake sure that connection-specif stuff was cleaned up
            createdBeans = getCreatedBeans(initialBeans);
            createdThreads = getCreatedThreads(initialThreads);

            assertFalse(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            assertFalse(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            assertFalse(createdThreads.contains("testCleanupConn-:12345-Acceptor"));
        }

        // completely close endpoint
        client.close();

        // Threads need time to close
        Thread.sleep(500);

        // Everything should be released
        Set<String> resultingBeans = getCreatedBeans(initialBeans);
        Set<String> resultingThreads = getCreatedThreads(initialThreads);

        // dump all remains
        for (String s : resultingBeans)
            System.out.println(s);
        for (String s : resultingThreads)
            System.out.println(s);

        // we should not have anything remaining
        assertTrue("Should not leave any JMX beans behind", resultingBeans.isEmpty());
        assertTrue("Should not leave any threads behind", resultingThreads.isEmpty());
    }

    public void testResourcesCleanupWithExport() throws InterruptedException {
        Set<String> initialBeans = getBeans();
        Set<String> initialThreads = getThreadNames();

        server = RMIEndpoint.newBuilder().withName("server").build();

        // we just need a connector to test
        client = RMIEndpoint.newBuilder().withName("client").build();
        client.getClient().setRequestSendingTimeout(3000L);

        for (int attempt = 1; attempt <= 2; attempt++) {
            server.connect(":1212");
            Thread.sleep(500);
            client.connect("localhost:1212");

            // now check resources that ware created in the process
            Set<String> createdBeans = getCreatedBeans(initialBeans);
            Set<String> createdThreads = getCreatedThreads(initialThreads);

            // root stats
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=server,c=Any,id=!AnyStats"));
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=client,c=Any,id=!AnyStats"));
            // named server connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ServerSocket-RMI"));
            // named client connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=ClientSocket-RMI"));
            // connectors-specific JMXStats
            assertTrue(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=server,connector=ServerSocket-RMI,c=Any,id="));
            assertTrue(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=client,connector=ClientSocket-RMI,c=Any,id="));
            // acceptor thread
            assertTrue(createdThreads.contains("ServerSocket-RMI-:1212-Acceptor"));
            assertTrue(createdThreads.contains("localhost:1212-Reader"));
            assertTrue(createdThreads.contains("localhost:1212-Writer"));

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
            assertFalse(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=server,connector=ServerSocket-RMI,c=Any,id="));
            assertFalse(containsStartingWith(createdBeans, "com.devexperts.qd.stats:name=client,connector=ClientSocket-RMI,c=Any,id="));
            assertFalse(createdThreads.contains("localhost:1212-Reader"));
            assertFalse(createdThreads.contains("localhost:1212-Writer"));
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
        System.out.println("Beans:");
        for (String s : resultingBeans)
            System.out.println(s);
        System.out.println("Threads:");
        for (String s : resultingThreads)
            System.out.println(s);

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
        Set<String> result = new TreeSet<>();
        for (ObjectName name : names) {
            result.add(name.getDomain() + ":" + name.getKeyPropertyListString());
        }
        return result;
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
}
