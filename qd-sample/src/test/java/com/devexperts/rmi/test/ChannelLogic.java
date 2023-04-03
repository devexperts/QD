/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.RMIService;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class ChannelLogic {

    private final InitFunction initClientPort;
    private final InitFunction initServerPort;
    RMIRequest<Void> request;
    RMIClientPort clientPort;
    TestService testService;
    TestType type;
    private AtomicBoolean forward = new AtomicBoolean(false);

    public ChannelLogic(TestType type, RMIEndpoint client, RMIEndpoint server, RMIEndpoint remoteServer) {
        this.type = type;
        switch (type) {
        case REGULAR:
            initClientPort = () -> clientPort = client.getClient().getPort(null);
            initServerPort = () -> {};
            break;
        case CLIENT_CHANNEL:
            testService = new TestService();
            initClientPort = () -> {
                request = client.getClient().createRequest(
                    new RMIRequestMessage<>(RMIRequestType.DEFAULT, TestService.OPERATION));
                clientPort = request.getChannel();
                request.send();
            };
            initServerPort = () -> {
                testService.update();
                if (remoteServer != null && forward.get()) {
                    remoteServer.getServer().export(testService);
                } else {
                    server.getServer().export(testService);
                }
                assertTrue(testService.getStart());
            };
            break;
        case SERVER_CHANNEL:
        default:
            testService = new TestService();
            initClientPort = () -> {
                testService.update();
                clientPort = testService.awaitChannel();
                assertTrue(testService.getStart());
            };
            initServerPort = () -> {
                request = client.getClient().createRequest(
                    new RMIRequestMessage<>(RMIRequestType.DEFAULT, TestService.OPERATION));
                for (RMIService<?> handler : testService.handlers) {
                    request.getChannel().addChannelHandler(handler);
                }
                request.send();
                if (remoteServer != null && forward.get()) {
                    remoteServer.getServer().export(testService);
                } else {
                    server.getServer().export(testService);
                }
            };
            break;
        }
    }

    void initServerPort() throws InterruptedException {
        initServerPort.apply();
    }

    void initClientPort() throws InterruptedException {
        initClientPort.apply();
    }

    void initPorts() throws InterruptedException {
        if (type != TestType.SERVER_CHANNEL) {
            initClientPort.apply();
            initServerPort.apply();
        } else {
            initServerPort.apply();
            initClientPort.apply();
        }
    }

    boolean isChannel() {
        return type.isChannel;
    }

    void setForward(boolean forward) {
        this.forward.set(forward);
    }
}
