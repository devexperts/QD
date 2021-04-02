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


import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.util.ExecutorProvider;
import com.dxfeed.promise.Promise;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

public class RMIServerImpl extends RMIServer {

    // ==================== private instance fields ====================

    final RMIEndpointImpl endpoint;

    private final ExecutorProvider.Reference defaultExecutorReference;

    private final ServerSideServices services;

    RMIServerImpl(RMIEndpointImpl endpoint) {
        this.endpoint = endpoint;
        services = new ServerSideServices(this, endpoint.getRMILoadBalancerFactories());
        defaultExecutorReference = endpoint.getDefaultExecutorProvider().newReference();
    }

    // ==================== public methods ====================

    @Override
    public <T> void export(T implementation, Class<T> serviceInterface) {
        export(new RMIServiceImplementation<>(implementation, serviceInterface));
    }

    @Override
    public <T> void export(T implementation, Class<T> serviceInterface, String serviceName) {
        export(new RMIServiceImplementation<>(implementation, serviceInterface, serviceName));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void export(final RMIService<?> service) {
        if (service == null)
            throw new NullPointerException("service is null");
        services.put(service);
    }

    @Override
    public void unexport(RMIService<?> service) {
        if (service == null)
            throw new NullPointerException("service is null");
        services.remove(service);
    }

    @Override
    public Executor getDefaultExecutor() {
        return defaultExecutorReference.getOrCreateExecutor();
    }

    @Override
    public void setDefaultExecutor(Executor executor) {
        defaultExecutorReference.setExecutor(executor);
    }

    // ==================== private implementation ====================

    void close() {
        services.close();
        defaultExecutorReference.close();
    }

    RMIService<?> getProvidedService(RMIServiceId target) {
        return services.getService(target);
    }

    void sendAllDescriptorsToConnection(RMIConnection connection) {
        services.sendAllDescriptorsToConnection(connection);
    }

    void sendDescriptorsToAllConnections(List<RMIServiceDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty())
            return;
        for (Iterator<RMIConnection> it = endpoint.concurrentConnectionsIterator(); it.hasNext(); ) {
            RMIConnection connection = it.next();
            connection.serverDescriptorsManager.addServiceDescriptors(descriptors);
        }
    }

    Promise<BalanceResult> balance(RMIRequestMessage<?> message) {
        return services.balance(message);
    }
}
