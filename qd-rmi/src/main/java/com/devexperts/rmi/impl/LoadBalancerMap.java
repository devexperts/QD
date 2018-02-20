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
package com.devexperts.rmi.impl;

import java.util.HashMap;
import java.util.Map;

import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.*;

//Guarded by ClientSideService or ServerSideServices
class LoadBalancerMap {
    private final Map<String, RMILoadBalancer> loadBalancers = new HashMap<>();
    private final RMILoadBalancerFactory factory;

    LoadBalancerMap(RMIEndpointImpl endpoint) {
        this.factory = endpoint.getRMILoadBalancerFactory();
    }

    RMIServiceId loadBalance(RMIRequestMessage<?> message) {
        String serviceName = message.getOperation().getServiceName();
        RMILoadBalancer loadBalancer = loadBalancers.get(serviceName);
        return loadBalancer == null ? null : loadBalancer.pickServiceInstance(message);
    }

    void updateDescriptorInLoadBalancer(RMIServiceDescriptor descriptor) {
            RMILoadBalancer loadBalancer = loadBalancers.get(descriptor.getServiceName());
            if (!descriptor.isAvailable()) {
                if (loadBalancer == null)
                    return;
                loadBalancer.removeService(descriptor);
                if (loadBalancer.isEmpty())
                    loadBalancers.remove(descriptor.getServiceName());
                return;
            }
            if (loadBalancer == null) {
                loadBalancer = factory.createLoadBalancer(descriptor.getServiceName());
                loadBalancers.put(descriptor.getServiceName(), loadBalancer);
            }
            loadBalancer.addService(descriptor);
    }

}
