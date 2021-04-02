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

import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMILoadBalancerFactory;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ServerSideServices {

    private final RMIServerImpl server;
    private final Map<RMIServiceId, ServiceRouter<RMIService<?>>> clientServiceMap = new HashMap<>();
    private final Map<RMIServiceId, RMIService<?>> localServices = new HashMap<>();
    private final Set<RMIService<?>> services = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LoadBalancers loadBalancers;

    ServerSideServices(RMIServerImpl server, List<RMILoadBalancerFactory> rmiLoadBalancerFactories) {
        this.server = server;
        this.loadBalancers = new LoadBalancers(rmiLoadBalancerFactories);
    }

    synchronized void put(final RMIService<?> service) {
        if (services.add(service))
            service.addServiceDescriptorsListener(new ServiceProcessor(service));
    }

    synchronized void remove(RMIService<?> service) {
        if (services.remove(service))
            service.removeServiceDescriptorsListener(new ServiceProcessor(service));
    }

    synchronized RMIService<?> getService(RMIServiceId serviceId) {
        RMIService<?> result = localServices.get(serviceId);
        if (result != null)
            return result;
        ServiceRouter<RMIService<?>> router = clientServiceMap.get(serviceId);
        if (router != null)
            return router.pickRandom();
        return null;
    }

    synchronized boolean sendAllDescriptorsToConnection(RMIConnection connection) {
        ArrayList<RMIServiceDescriptor> descriptors = new ArrayList<>();
        for (ServiceRouter<RMIService<?>> router : clientServiceMap.values()) {
            if (!router.isEmpty())
                descriptors.add(router.pickFirstDescriptor());
        }
        for (RMIService<?> service : localServices.values()) {
            for (RMIServiceDescriptor descriptor : service.getDescriptors()) {
                if (descriptor.getDistance() == 0)
                    descriptors.add(descriptor);
            }
        }
        connection.serverDescriptorsManager.addServiceDescriptors(descriptors);
        return !descriptors.isEmpty();
    }

    synchronized Promise<BalanceResult> balance(RMIRequestMessage<?> message) {
        return loadBalancers.balance(message);
    }

    synchronized void close() {
        loadBalancers.close();
    }

    private synchronized void descriptorsUpdateImpl(RMIService<?> service, List<RMIServiceDescriptor> descriptors) {
        List<RMIServiceDescriptor> localServiceDescriptors = new ArrayList<>();
        for (RMIServiceDescriptor descriptor : descriptors) {
            // Update local services
            if (descriptor.getDistance() == 0) {
                localServices.put(descriptor.getServiceId(), service);
                localServiceDescriptors.add(descriptor);
            } else {
                if (localServices.remove(descriptor.getServiceId()) != null)
                    localServiceDescriptors.add(descriptor);
                updateInClientServicesMap(service, descriptor);
            }
            loadBalancers.updateDescriptorInLoadBalancer(descriptor);
        }
        server.sendDescriptorsToAllConnections(localServiceDescriptors);
    }

    // called from a synchronized section (descriptorsUpdateImpl)
    private void updateInClientServicesMap(RMIService<?> service, RMIServiceDescriptor descriptor) {
        RMIServiceId serviceId = descriptor.getServiceId();
        ServiceRouter<RMIService<?>> router = clientServiceMap.get(serviceId);
        if (router == null) {
            if (!descriptor.isAvailable())
                return;
            router = ServiceRouter.createRouter(server.endpoint.getEndpointId(), serviceId);
            router.addServiceDescriptorsListener(new ServerRouterProcessor());
            clientServiceMap.put(serviceId, router);
        }
        router.updateDescriptor(descriptor, descriptor.getDistance(), service);
        if (router.isEmpty())
            clientServiceMap.remove(descriptor.getServiceId());
    }

    // --------------- RMIServiceDescriptorsListener for RMIServices ---------------

    private class ServiceProcessor extends AbstractServiceDescriptorsProcessor {
        private final RMIService<?> service;

        private ServiceProcessor(RMIService<?> service) {
            super(server.getDefaultExecutor());
            this.service = service;
        }

        @Override
        protected void process(List<RMIServiceDescriptor> descriptors) {
            descriptorsUpdateImpl(service, descriptors);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ServiceProcessor))
                return false;
            ServiceProcessor other = (ServiceProcessor) obj;
            return other.service.equals(service);
        }

        @Override
        public int hashCode() {
            return service.hashCode();
        }

        @Override
        public String toString() {
            return "ServiceProcessor{" + service + "}";
        }
    }

    // --------------- RMIServiceDescriptorsListener for server ServiceRouter ---------------

    private class ServerRouterProcessor extends AbstractServiceDescriptorsProcessor {
        ServerRouterProcessor() {
            super(server.getDefaultExecutor());
        }

        @Override
        protected void process(List<RMIServiceDescriptor> descriptors) {
            server.sendDescriptorsToAllConnections(descriptors);
        }

        @Override
        public String toString() {
            return "ServerRouterProcessor{" + server.endpoint + "}";
        }
    }
}
