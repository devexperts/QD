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
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.monitoring.JMXEndpoint;
import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Trade;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.management.ObjectName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DXFeedMonitoringTest {

    private static final String TEST_SYMBOL = "TEST";

    public static final int PORT_00 = (100 + ThreadLocalRandom.current().nextInt(300)) * 100;

    int getPort(int offset) {
        return PORT_00 + offset;
    }

    @Test
    public void testResourcesCleanup() throws InterruptedException {
        // IMPORTANT: Some logging facilities also initiate some beans and threads
        Logging.getLogging(DXFeedMonitoringTest.class).info("Starting testResourcesCleanup...");

        Set<String> initialBeans = getBeans();
        Set<String> initialThreads = getThreadsNames();

        // we just need a connector to test
        String jmxRmiPort = String.valueOf(getPort(93));
        String jmxHtmlPort = String.valueOf(getPort(92));
        String marsAddressPort = ":" + getPort(94);
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withProperty(MonitoringEndpoint.NAME_PROPERTY, "testCleanup")
            .withProperty(JMXEndpoint.JMX_HTML_PORT_PROPERTY, jmxHtmlPort)
            .withProperty(JMXEndpoint.JMX_RMI_PORT_PROPERTY, jmxRmiPort)
            .withProperty(MARSNode.MARS_ROOT_PROPERTY, "testCleanupRoot")
            .withProperty(MARSNode.MARS_ADDRESS_PROPERTY, marsAddressPort)
            .build();

        // we need to publish and process one event to get processing threads created
        DXFeedSubscription<Trade> sub = endpoint.getFeed().createSubscription(Trade.class);
        final ArrayBlockingQueue<Trade> eventsQueue = new ArrayBlockingQueue<>(1);
        sub.addEventListener(eventsQueue::addAll);
        sub.addSymbols(TEST_SYMBOL);

        Trade pubTrade = new Trade(TEST_SYMBOL);
        pubTrade.setPrice(1234);
        endpoint.getPublisher().publishEvents(Arrays.asList(pubTrade));
        Trade subTrade = eventsQueue.take();
        assertEquals(TEST_SYMBOL, subTrade.getEventSymbol());
        assertEquals(pubTrade.getPrice(), subTrade.getPrice(), 0.0);

        // now perform connect-disconnect cycle
        for (int attempt = 1; attempt <= 2; attempt++) {
            // connect endpoint
            endpoint.connect("demo.dxfeed.com:7300[name=testCleanupConn]");

            // now check resources that ware created in the process
            Set<String> createdBeans = getCreatedBeans(initialBeans);
            Set<String> createdThreads = getCreatedThreads(initialThreads);

            // collector management with this specific name
            assertTrue(createdBeans.contains("com.devexperts.qd.impl.matrix:name=testCleanup,scheme=DXFeed,c=Ticker"));
            assertTrue(createdBeans.contains("com.devexperts.qd.impl.matrix:name=testCleanup,scheme=DXFeed,c=Stream"));
            assertTrue(createdBeans.contains("com.devexperts.qd.impl.matrix:name=testCleanup,scheme=DXFeed,c=History"));
            // adaptors
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=HtmlAdaptor,port=" + jmxHtmlPort));
            assertTrue(createdBeans.contains("com.devexperts.qd.monitoring:type=RmiServer,port=" + jmxRmiPort));
            // root stats
            assertTrue(createdBeans.contains("com.devexperts.qd.stats:name=testCleanup,c=Any,id=!AnyStats"));
            // named connector
            assertTrue(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            // connectors-specific JMXStats
            assertTrue(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            // reader & writer threads
            assertTrue(createdThreads.contains("demo.dxfeed.com:7300-Reader"));
            assertTrue(createdThreads.contains("demo.dxfeed.com:7300-Writer"));
            // something else might have been create -- we don't care

            // now disconnect endpoint
            endpoint.disconnect();

            // Threads need time to close
            Thread.sleep(1000);

            // Make sure that connection-specif stuff was cleaned up
            createdBeans = getCreatedBeans(initialBeans);
            createdThreads = getCreatedThreads(initialThreads);

            assertFalse(createdBeans.contains("com.devexperts.qd.qtp:type=Connector,name=testCleanupConn"));
            assertFalse(containsStartingWith(createdBeans,
                "com.devexperts.qd.stats:name=testCleanup,connector=testCleanupConn,c=Any,id="));
            assertFalse(createdThreads.contains("demo.dxfeed.com:7300-Reader"));
            assertFalse(createdThreads.contains("demo.dxfeed.com:7300-Writer"));
        }

        // new close endpoint and make sure everything is cleaned up
        endpoint.closeAndAwaitTermination();

        // Some threads still need time to close
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

    private Set<String> getCreatedThreads(Set<String> initialThreads) {
        Set<String> resultingThreads = getThreadsNames();
        resultingThreads.removeAll(initialThreads);
        return resultingThreads;
    }

    // collects all non-interrupted threads
    private Set<String> getThreadsNames() {
        return ThreadCleanCheck.getThreads().stream()
            .filter(t -> !t.isInterrupted())
            .map(Thread::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> getCreatedBeans(Set<String> initialBeans) {
        Set<String> createdBeans = getBeans();
        createdBeans.removeAll(initialBeans);
        return createdBeans;
    }
}
