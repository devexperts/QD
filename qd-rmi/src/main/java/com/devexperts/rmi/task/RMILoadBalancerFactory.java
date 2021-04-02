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
package com.devexperts.rmi.task;

import com.devexperts.services.Service;
import com.devexperts.services.ServiceProvider;

/**
 * A factory that creates {@link RMILoadBalancer load balancer} instances.
 * A separate load balancer is created for each distinct service name.
 * <p>
 * Multiple factories can be configured via SPI. To create a balancer the factories are consulted in order of
 * their {@link ServiceProvider#order()}.
 */
@Service
public interface RMILoadBalancerFactory {

    /**
     * Creates a new {@link RMILoadBalancer} for a given service name.
     * @param serviceName service name
     * @return new {@link RMILoadBalancer}. If a factory returns {@code null} it indicates that it cannot
     * create a balancer for a given service.
     */
    public RMILoadBalancer createLoadBalancer(String serviceName);
}
