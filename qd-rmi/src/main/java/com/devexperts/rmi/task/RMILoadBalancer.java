/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.task;

import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;


/**
 * A strategy that defines how to choose a target for {@link RMIRequest}.
 * <p>
 * <b>Implementation of this strategy must be thread-safe<b/>
 */
public interface RMILoadBalancer {

    /**
     * Added descriptor in strategy.
     * @param descriptor the service descriptor
     */
    public void addService(RMIServiceDescriptor descriptor);

    /**
     * Removed descriptor in strategy.
     * @param descriptor the service descriptor
     */
    public void removeService(RMIServiceDescriptor descriptor);

    /**
     * Returns {@link RMIServiceId}, which will be route the request.
     * @param request the request which must be routed.
     * @return {@link RMIServiceId}, which will be route the request
     */
    public RMIServiceId pickServiceInstance(RMIRequestMessage<?> request);

    /**
     * Returns true if this strategy does not contain any descriptor.
     * @return true if this strategy does not contain any descriptor
     */
    public boolean isEmpty();
}
