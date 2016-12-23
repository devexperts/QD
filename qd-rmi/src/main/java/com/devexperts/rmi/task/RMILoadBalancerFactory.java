/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.task;

/**
 * A factory that creates {@link RMILoadBalancer load balancer}.
 */
public interface RMILoadBalancerFactory {

    /**
     * Creates new {@link RMILoadBalancer} for a given service name.
     * @param serviceName the service name
     * @return new {@link RMILoadBalancer}
     */
    public RMILoadBalancer createLoadBalancer(String serviceName);
}
