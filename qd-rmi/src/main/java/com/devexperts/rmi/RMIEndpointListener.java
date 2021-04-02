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
package com.devexperts.rmi;


/**
 * Listener for the {@link RMIEndpoint}. It is notified when
 * connections are established or broken.
 */
public interface RMIEndpointListener {

    /**
     * Invoked when connections are established or broken in the endpoint.
     * @param endpoint {@link RMIEndpoint} whose state changed.
     */
    public void stateChanged(RMIEndpoint endpoint);

}
