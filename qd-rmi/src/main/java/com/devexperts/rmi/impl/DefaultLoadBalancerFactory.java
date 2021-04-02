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

import com.devexperts.rmi.task.ConsistentLoadBalancer;
import com.devexperts.rmi.task.RMILoadBalancer;
import com.devexperts.rmi.task.RMILoadBalancerFactory;
import com.devexperts.services.ServiceProvider;

@ServiceProvider(order = 100)
public class DefaultLoadBalancerFactory implements RMILoadBalancerFactory {
    @Override
    public RMILoadBalancer createLoadBalancer(String serviceName) {
        return new ConsistentLoadBalancer();
    }
}
