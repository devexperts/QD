/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.promise.Promise;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class TestEndpoint implements AutoCloseable {
    private final QDEndpoint endpoint;
    private DXEndpoint publisherEndpoint;
    private DXEndpoint feedEndpoint;
    private int serverPort;

    TestEndpoint(DataScheme scheme) {
        endpoint = QDEndpoint.newBuilder()
            .withScheme(scheme)
            .withCollectors(Arrays.asList(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .build();
    }

    TestEndpoint connect(int port) {
        MessageAdapter.AbstractFactory distributorFactory = new DistributorAdapter.Factory(endpoint, null);
        endpoint.addConnectors(MessageConnectors.createMessageConnectors(
            distributorFactory, "127.0.0.1:" + port, endpoint.getRootStats()));
        endpoint.startConnectors();
        return this;
    }

    @Deprecated
    TestEndpoint bind(int port) {
        AgentAdapter.Factory agentFactory = new AgentAdapter.Factory(endpoint, null);
        endpoint.addConnectors(
            MessageConnectors.createMessageConnectors(agentFactory, ":" + port, endpoint.getRootStats()));
        endpoint.startConnectors();
        return this;
    }

    TestEndpoint bindAuto() {
        AgentAdapter.Factory agentFactory = new AgentAdapter.Factory(endpoint, null);
        String name = UUID.randomUUID().toString();
        Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(name);
        endpoint.addConnectors(
            MessageConnectors.createMessageConnectors(agentFactory, ":0[name=" + name + "]", endpoint.getRootStats()));
        endpoint.startConnectors();
        serverPort = portPromise.await(10, TimeUnit.SECONDS);
        return this;
    }

    DXPublisher getPublisher() {
        if (publisherEndpoint == null) {
            publisherEndpoint =
                new DXEndpointImpl(DXEndpoint.Role.PUBLISHER, endpoint.getCollectors().toArray(new QDCollector[0]));
        }
        return publisherEndpoint.getPublisher();
    }

    DXFeed getFeed() {
        if (feedEndpoint == null) {
            feedEndpoint =
                new DXEndpointImpl(DXEndpoint.Role.FEED, endpoint.getCollectors().toArray(new QDCollector[0]));
        }
        return feedEndpoint.getFeed();
    }

    int getServerPort() {
        if (serverPort == 0)
            throw new IllegalStateException("Not bound to server port");
        return serverPort;
    }

    @Override
    public void close() throws InterruptedException {
        if (feedEndpoint != null)
            feedEndpoint.close();
        if (publisherEndpoint != null)
            publisherEndpoint.close();
        endpoint.stopConnectorsAndWait();
        endpoint.close();
    }
}
