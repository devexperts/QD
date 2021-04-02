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

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;

/**
 * Types of {@link RMIChannel}.
 */
public enum RMIChannelType {

    /**
     * Channel on the {@link RMIEndpoint.Side#SERVER server side}.
     * @see RMITask#getChannel()
     */
    SERVER_CHANNEL,

    /**
     * Channel on the {@link RMIEndpoint.Side#CLIENT client side}.
     * @see RMIRequest#getChannel()
     */
    CLIENT_CHANNEL
}
