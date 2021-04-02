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
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.GuardedBy;

class ClientSideServices {

    private ServiceRouter<RMIConnection> anonymousRouter;
    private final Map<RMIServiceId, ServiceRouter<RMIConnection>> routers = new HashMap<>();
    private final Map<String, RMIClientService> services = new HashMap<>();

    private final LoadBalancers loadBalancers;
    private final RMIClientImpl client;

    ClientSideServices(RMIClientImpl client, List<RMILoadBalancerFactory> rmiLoadBalancerFactories) {
        this.client = client;
        loadBalancers = new LoadBalancers(rmiLoadBalancerFactories);
    }

    synchronized RMIClientService getService(String serviceName) {
        return getOrCreateRMIServiceClient(serviceName);
    }

    synchronized RMIConnection getConnection(RMIServiceId serviceId) {
        ServiceRouter<RMIConnection> router = routers.get(serviceId);
        if (router == null)
            router = anonymousRouter;
        return router == null ? null : router.pickRandom();
    }

    synchronized void updateAnonymousRouter(RMIConnection connection) {
        if (!connection.closed) {
            if (anonymousRouter == null)
                anonymousRouter = ServiceRouter.createAnonymousRouter(client.endpoint.getEndpointId());
            anonymousRouter.updateDescriptor(null, connection.weight, connection);
            return;
        }
        if (anonymousRouter == null)
            return;
        anonymousRouter.removeDescriptor(null, connection);
        if (anonymousRouter.isEmpty())
            anonymousRouter = null;
    }

    synchronized Promise<BalanceResult> balance(RMIRequestMessage<?> message) {
        return loadBalancers.balance(message);
    }

    synchronized void updateDescriptorAndUpdateServices(List<RMIServiceDescriptor> descriptors, RMIConnection connection) {
        for (RMIServiceDescriptor descriptor : descriptors) {
            if (updateServiceRouter(descriptor, connection))
                loadBalancers.updateDescriptorInLoadBalancer(descriptor);
        }
    }

    private synchronized void updateServices(List<RMIServiceDescriptor> descriptors) {
        Map<RMIClientService, List<RMIServiceDescriptor>> batchedMap = new HashMap<>();
        List<RMIServiceDescriptor> acceptedDescriptors = new ArrayList<>();
        for (RMIClientService service : services.values()) {
            for (RMIServiceDescriptor descriptor : descriptors) {
                if (service.getFilter().accept(descriptor.getServiceName()))
                    acceptedDescriptors.add(descriptor);
            }
            if (acceptedDescriptors.isEmpty())
                continue;
            batchedMap.put(service, new ArrayList<>(acceptedDescriptors));
            acceptedDescriptors.clear();
        }
        for (Map.Entry<RMIClientService, List<RMIServiceDescriptor>> entry : batchedMap.entrySet())
            entry.getKey().updateDescriptors(entry.getValue());
    }

    // Returns true if new services was exported or un-exported in the process
    @GuardedBy("this")
    private boolean updateServiceRouter(RMIServiceDescriptor descriptor, RMIConnection connection) {
        RMIServiceId serviceId = descriptor.getServiceId();
        ServiceRouter<RMIConnection> router = routers.get(serviceId);
        boolean changes = false;
        if (router == null) {
            if (!descriptor.isAvailable())
                return false;
            router = ServiceRouter.createRouter(client.endpoint.getEndpointId(), serviceId);
            router.addServiceDescriptorsListener(new ClientRouterProcessor());
            routers.put(serviceId, router);
            changes = true;
        }
        router.updateDescriptor(descriptor, descriptor.getDistance(), connection);
        if (router.isEmpty()) {
            routers.remove(descriptor.getServiceId());
            changes = true;
        }
        return changes;
    }

    @GuardedBy("this")
    private ServiceRouter<RMIConnection> getRouter(String serviceName) {
        for (Map.Entry<RMIServiceId, ServiceRouter<RMIConnection>> serviceIdRouterEntry : routers.entrySet()) {
            if (serviceIdRouterEntry.getKey().getName().equals(serviceName))
                return serviceIdRouterEntry.getValue();
        }
        return anonymousRouter;
    }

    @GuardedBy("this")
    private RMIClientService getOrCreateRMIServiceClient(String name) {
        if (!services.containsKey(name)) {
            RMIClientService service = new RMIClientService(name, client);
            List<RMIServiceDescriptor> result = new ArrayList<>();
            for (Map.Entry<RMIServiceId, ServiceRouter<RMIConnection>> serviceIdRouterEntity : routers.entrySet()) {
                if (service.getFilter().accept(serviceIdRouterEntity.getKey().getName()))
                    result.add(serviceIdRouterEntity.getValue().pickFirstDescriptor());
            }
            service.updateDescriptors(result);
            services.put(name, service);
        }
        return services.get(name);
    }

    synchronized void close() {
        loadBalancers.close();
    }

    // --------------- RMIServiceDescriptorsListener for client ServiceRouter ---------------

    private class ClientRouterProcessor extends AbstractServiceDescriptorsProcessor {

        private ClientRouterProcessor() {
            super(client.getDefaultExecutor());
        }

        @Override
        protected void process(List<RMIServiceDescriptor> descriptors) {
            updateServices(descriptors);
        }

        @Override
        public String toString() {
            return "ClientRouterProcessor{" + client.endpoint + "}";
        }
    }
}
