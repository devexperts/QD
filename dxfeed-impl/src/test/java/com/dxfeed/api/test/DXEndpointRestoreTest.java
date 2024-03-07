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

import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ConnectOrder;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.util.SynchronizedIndexedSet;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.Promises;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DXEndpointRestoreTest {

    private static final int PUB_COUNT = 3;
    private static final int WAIT_TIMEOUT = 20; // seconds

    private String testId;

    List<DXEndpoint> publishers;
    List<Integer> ports;
    DXEndpoint feedEndpoint;

    @Before
    public void setUp() throws Exception {
        testId = UUID.randomUUID().toString();
        publishers = new ArrayList<>(PUB_COUNT);
        ports = new ArrayList<>(PUB_COUNT);
    }

    @After
    public void tearDown() throws Exception {
        if (feedEndpoint != null)
            feedEndpoint.close();
        if (publishers != null)
            publishers.forEach(DXEndpoint::close);
    }

    @Test
    public void testConnectRestoreManualPriority() throws Exception {
        testConnectRestore(ConnectOrder.PRIORITY, true);
    }

    @Test
    public void testConnectRestoreManualOrdered() throws Exception {
        testConnectRestore(ConnectOrder.ORDERED, true);
    }

    @Test
    public void testConnectRestoreAutomaticPriority() throws Exception {
        testConnectRestore(ConnectOrder.PRIORITY, false);
    }

    @Test
    public void testConnectRestoreAutomaticOrdered() throws Exception {
        testConnectRestore(ConnectOrder.ORDERED, false);
    }

    private void testConnectRestore(ConnectOrder order, boolean manual) throws Exception {
        startPublishers();
        String address = ports.stream().map((port) -> "127.0.0.1:" + port).collect(Collectors.joining(","));
        address += String.format("[name=uplink,reconnectDelay=1,connectOrder=%s]", order.toString());

        Set<Integer> blockedPorts = SynchronizedIndexedSet.create();
        TestConnectorFactory.addClientSocketConnectorForBlockedPorts(testId + ":", blockedPorts);

        feedEndpoint = DXEndpoint.create(DXEndpoint.Role.STREAM_FEED);

        Phaser phaser = new Phaser();
        phaser.register();
        feedEndpoint.addStateChangeListener(evt -> {
            if (evt.getNewValue() == DXEndpoint.State.CONNECTED) {
                phaser.arrive();
            }
        });

        // block first port to force connecting to second one
        blockedPorts.add(ports.get(0));
        feedEndpoint.connect(testId + ":" + address);
        phaser.awaitAdvanceInterruptibly(0, WAIT_TIMEOUT, TimeUnit.SECONDS);

        ClientSocketConnector connector = getFirstConnector(feedEndpoint);
        assertEquals(ports.get(1).intValue(), connector.getCurrentPort());

        // reactivate all ports to test restore to first port
        blockedPorts.clear();

        if (manual) {
            connector.restoreNow();
        } else {
            connector.restoreGracefully("0.001");
        }

        phaser.awaitAdvanceInterruptibly(1, WAIT_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(ports.get(0).intValue(), connector.getCurrentPort());
    }

    private ClientSocketConnector getFirstConnector(DXEndpoint feedEndpoint) {
        List<MessageConnector> connectors = ((DXEndpointImpl) feedEndpoint).getQDEndpoint().getConnectors();
        assertEquals(1, connectors.size());
        assertTrue(connectors.get(0) instanceof ClientSocketConnector);
        return (ClientSocketConnector) connectors.get(0);
    }

    private Promise<Integer> addPublisher(String symbol) {
        String name = testId + "-pub-" + symbol;
        Promise<Integer> port = ServerSocketTestHelper.createPortPromise(name);
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_PUBLISHER)
            .build()
            .connect(":0[name=" + name + ",bindAddr=127.0.0.1]");
        publishers.add(endpoint);
        return port;
    }

    private void startPublishers() {
        List<Promise<Integer>> promises = new ArrayList<>();
        for (int i = 0; i < PUB_COUNT; i++) {
            String symbol = String.valueOf((char) ('A' + i));
            promises.add(addPublisher(symbol));
        }
        Promises.allOf(promises).await(WAIT_TIMEOUT, TimeUnit.SECONDS);
        promises.stream().map(Promise::getResult).forEach(ports::add);
    }
}
