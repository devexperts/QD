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
package com.devexperts.rmi.impl;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMILoadBalancer;
import com.devexperts.rmi.task.RMILoadBalancerFactory;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.dxfeed.promise.Promise;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

// Guarded by ClientSideService or ServerSideServices
class LoadBalancers {
    private static final Logging log = Logging.getLogging(LoadBalancers.class);

    private final Map<String, RMILoadBalancer> loadBalancers = new HashMap<>();
    private final List<RMILoadBalancerFactory> factories;
    private boolean closed = false;

    LoadBalancers(List<RMILoadBalancerFactory> rmiLoadBalancerFactories) {
        this.factories = rmiLoadBalancerFactories;
    }

    @Nonnull
    Promise<BalanceResult> balance(RMIRequestMessage<?> message) {
        if (closed) {
            // This should never happen
            log.error("Attempt to balance a message on closed endpoint: " + message, new Exception());
            return Promise.failed(new RMIFailedException("Attempt to balance a message on closed endpoint"));
        }
        try {
            String serviceName = message.getOperation().getServiceName();
            RMILoadBalancer loadBalancer = getBalancer(serviceName);
            Promise<BalanceResult> result = loadBalancer.balance(message);

            // An ill-behaving pluggable balancer can return null. Instead of throwing NPEs at random places,
            // let's return a failed promise so that the request is rejected.
            //noinspection ConstantConditions
            if (result == null)
                return Promise.failed(new RMIFailedException("Load balancer returned null"));

            return result;
        } catch (Throwable e) {
            // Any exception from a pluggable load balancer is converted to a failed promise.
            // This means a load balancer violates its contract, so the request is rejected up the stack.
            return Promise.failed(e);
        }
    }

    @Nonnull
    private RMILoadBalancer getBalancer(String serviceName) {
        RMILoadBalancer loadBalancer = loadBalancers.get(serviceName);
        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer(serviceName);
            loadBalancers.put(serviceName, loadBalancer);
        }
        assert loadBalancer != null;
        return loadBalancer;
    }

    void updateDescriptorInLoadBalancer(RMIServiceDescriptor descriptor) {
        if (closed)
            return;

        RMILoadBalancer loadBalancer = getBalancer(descriptor.getServiceName());
        try {
            loadBalancer.updateServiceDescriptor(descriptor);
        } catch (Exception e) {
            log.error("Error updating descriptor " + descriptor + " in RMI load balancer " + loadBalancer, e);
        }
    }

    void close() {
        loadBalancers.forEach((k, v) -> closeBalancer(v));
        loadBalancers.clear();
        closed = true;
    }

    private void closeBalancer(RMILoadBalancer loadBalancer) {
        try {
            loadBalancer.close();
        } catch (Exception e) {
            log.error("Error closing RMI load balancer " + loadBalancer, e);
        }
    }

    private RMILoadBalancer createLoadBalancer(String serviceName) {
        if (RMIEndpointImpl.RMI_TRACE_LOG)
            log.trace("Creating RMI load balancer for service " + serviceName);

        for (RMILoadBalancerFactory factory : factories) {
            RMILoadBalancer loadBalancer = factory.createLoadBalancer(serviceName);
            if (loadBalancer != null) {
                return loadBalancer;
            }
        }
        throw new IllegalStateException("Could not create a load balancer for " + serviceName);
    }
}
