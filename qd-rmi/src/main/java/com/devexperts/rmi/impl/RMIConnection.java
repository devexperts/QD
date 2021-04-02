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
package com.devexperts.rmi.impl;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.util.TypedMap;

/**
 * An entity representing a single connection in {@link RMIEndpointImpl}.
 *
 * @see RMIMessageAdapter
 * @see RequestsManager
 * @see TasksManager
 */
class RMIConnection {

    volatile boolean closed;
    final RMIEndpointImpl endpoint;
    final RequestsManager requestsManager;
    final TasksManager tasksManager;
    final ClientDescriptorsManager clientDescriptorsManager;
    final ServerDescriptorsManager serverDescriptorsManager;
    final ChannelsManager channelsManager;
    final MessageComposer messageComposer;
    final MessageProcessor messageProcessor;
    final RMIMessageAdapter messageAdapter;
    final RMIEndpoint.Side side;
    final int weight;
    final ServiceFilter configuredServices;
    final AdvertisementFilter adFilter;

    RMIConnection(RMIEndpointImpl endpoint, QDStats stats, MessageAdapter attachedAdapter, ServiceFilter services,
        AdvertisementFilter adFilter, int weight)
    {
        this.endpoint = endpoint;
        this.side = endpoint.side;
        this.configuredServices = services;
        this.adFilter = adFilter;
        this.messageComposer = new MessageComposer(this);
        this.messageProcessor = new MessageProcessor(this);
        this.requestsManager = new RequestsManager(this);
        this.tasksManager = new TasksManager(this);
        this.clientDescriptorsManager = new ClientDescriptorsManager();
        this.serverDescriptorsManager = new ServerDescriptorsManager(this);
        this.channelsManager = new ChannelsManager();
        // we construct message adapter at the very end... just in case...
        this.messageAdapter = new RMIMessageAdapter(this, stats, attachedAdapter);
        this.weight = weight;
    }

    void start() {
        endpoint.registerConnection(this);
    }

    void close() {
        // Mark connection as closed first (as client- & server- side will not register anything new after that)
        closed = true;
        // remove this connection from endpoint structures lists and rebalance outgoing requests
        endpoint.unregisterConnection(this);
        // close client side (drop sent requests)
        requestsManager.close();
        // cancel requests that are being balanced on the server
        messageProcessor.close();
        // close server side (cancel running tasks)
        tasksManager.close();
        // abort already composed messages
        messageComposer.close();
    }

    TypedMap variables() {
        return messageAdapter.getConnectionVariables();
    }

    String getRemoteHostAddress() {
        return variables().get(TransportConnection.REMOTE_HOST_ADDRESS_KEY);
    }

    EndpointId getRemoteEndpointId() {
        return messageAdapter.getRemoteEndpointId();
    }

    Object getSubject() {
        return variables().get(TransportConnection.SUBJECT_KEY);
    }

    @Override
    public String toString() {
        return "RMIConnection{" + endpoint + "}";
    }
}
