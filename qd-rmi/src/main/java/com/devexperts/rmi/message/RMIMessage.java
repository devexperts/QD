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
package com.devexperts.rmi.message;

import com.devexperts.rmi.RMIRequest;

/**
 * This abstract class forms a basis for all for all incoming and outgoing messages: {@link RMIRequestMessage}
 * and {@link RMIResponseMessage}
 * @see RMIRequest
 */
public abstract class RMIMessage {
    /**
     * Route in the network of this message.
     */
    protected final RMIRoute route;

    protected RMIMessage(RMIRoute route) {
        this.route = route == null ? RMIRoute.EMPTY : route;
    }

    /**
     * Returns route in the network of this message.
     * @return route in the network of this message
     */
    public RMIRoute getRoute() {
        return route;
    }
}
