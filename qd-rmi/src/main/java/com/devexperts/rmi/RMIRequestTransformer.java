/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi;

import com.devexperts.annotation.Experimental;
import com.devexperts.rmi.message.RMIRequestMessage;

/**
 * Client request transformer interface (can change properties of the request)
 */
@Experimental
@SuppressWarnings("rawtypes")
@FunctionalInterface
public interface RMIRequestTransformer {
    /**
     * Optionally updates the given RMI request message, potentially modifying its properties.
     * For the moment, only modification of request properties is supported.
     * Changing other elements of the request message will result in an undefined behavior.
     *
     * @param message the original request message to be updated
     * @param port the RMI client port through which the request is being processed
     * @return a new or original {@link RMIRequestMessage} instance
     */
    RMIRequestMessage updateRequest(RMIRequestMessage message, RMIClientPort port);
}
