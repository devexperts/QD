/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.task.RMIService;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.DXEndpointImpl;

/**
 * Networking utility methods for tests that run over network.
 */
public class NTU {
    // Use IPv4 for consistency
    public static final String LOCAL_HOST = "127.0.0.1";

    // This makes sure that each fork of the test suit is going to use different base address for ports
    public static final int PORT_00 = (100 + ThreadLocalRandom.current().nextInt(300)) * 100;

    private static final Logging log = Logging.getLogging(NTU.class);

    private NTU() {} // do not create

    public static int port(int offset) {
        return PORT_00 + offset;
    }

    public static void connect(RMIEndpoint endpoint, String address) {
        endpoint.connect(address);
        waitConnected(((RMIEndpointImpl) endpoint).getQdEndpoint());
    }

    public static void connect(DXEndpoint endpoint, String address) {
        endpoint.connect(address);
        waitConnected(((DXEndpointImpl) endpoint).getQDEndpoint());
    }

    public static void exportServices(RMIServer server, RMIService<?> service, ChannelLogic channelLogic) {
        if (channelLogic.isChannel()) {
            channelLogic.testService.addChannelHandler(service);
        } else {
            server.export(service);
        }
    }

    private static void waitConnected(QDEndpoint qdEndpoint) {
        List<MessageConnector> serverConnectors = qdEndpoint.getConnectors().stream()
            .filter(connector -> connector instanceof ServerSocketConnector)
            .collect(Collectors.toList());
        if (serverConnectors.isEmpty())
            return;
        Thread currentThread = Thread.currentThread();
        serverConnectors.forEach(connector -> {
            log.info("Waiting for CONNECTED state on " + connector);
            connector.addMessageConnectorListener(c -> LockSupport.unpark(currentThread));
        });
        while (serverConnectors.stream().anyMatch(connector -> connector.getState() != MessageConnectorState.CONNECTED)) {
            // There's some evidence a plain "park" may hang here, so we park with timeout
            LockSupport.parkNanos(100_000_000);
        }
    }
}
