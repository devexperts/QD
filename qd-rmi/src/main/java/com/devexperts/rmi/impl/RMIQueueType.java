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

import com.devexperts.qd.qtp.MessageType;

enum RMIQueueType {
    DESCRIBE(MessageType.RMI_DESCRIBE_OPERATION),
    RESPONSE(MessageType.RMI_RESPONSE),
    REQUEST(MessageType.RMI_REQUEST),
    ADVERTISE(MessageType.RMI_ADVERTISE_SERVICES);

    private MessageType maskType;

    RMIQueueType(MessageType maskType) {
        this.maskType = maskType;
    }

    public MessageType maskType() {
        return maskType;
    }
}
