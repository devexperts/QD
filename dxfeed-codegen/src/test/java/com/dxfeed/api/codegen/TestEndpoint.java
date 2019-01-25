/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

import java.util.Arrays;

import com.devexperts.qd.*;
import com.devexperts.qd.qtp.*;
import com.dxfeed.api.*;
import com.dxfeed.api.impl.DXEndpointImpl;

class TestEndpoint implements AutoCloseable {
    private final QDEndpoint endpoint;
    private DXEndpoint publisherEndpoint;
    private DXEndpoint feedEndpoint;

    TestEndpoint(DataScheme scheme) {
        endpoint = QDEndpoint.newBuilder()
            .withScheme(scheme)
            .withCollectors(Arrays.asList(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .build();
    }

    TestEndpoint connect(int port) {
        MessageAdapter.AbstractFactory distributorFactory = new DistributorAdapter.Factory(endpoint, null);
        endpoint.addConnectors(MessageConnectors.createMessageConnectors(distributorFactory, "127.0.0.1:" + port, endpoint.getRootStats()));
        endpoint.startConnectors();
        return this;
    }

    TestEndpoint bind(int port) {
        AgentAdapter.Factory agentFactory = new AgentAdapter.Factory(endpoint, null);
        endpoint.addConnectors(MessageConnectors.createMessageConnectors(agentFactory, ":" + port, endpoint.getRootStats()));
        endpoint.startConnectors();
        return this;
    }

    DXPublisher getPublisher() {
        if (publisherEndpoint == null)
            publisherEndpoint = new DXEndpointImpl(DXEndpoint.Role.PUBLISHER, endpoint.getCollectors().toArray(new QDCollector[0]));
        return publisherEndpoint.getPublisher();
    }

    DXFeed getFeed() {
        if (feedEndpoint == null)
            feedEndpoint = new DXEndpointImpl(DXEndpoint.Role.FEED, endpoint.getCollectors().toArray(new QDCollector[0]));
        return feedEndpoint.getFeed();
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
