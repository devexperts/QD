/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

class HeartbeatProcessor {
    private volatile long heartbeatTimeout;
    private volatile long disconnectTimeout;

    HeartbeatProcessor(long heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
        this.disconnectTimeout = heartbeatTimeout; // suggest the server use our timeout
    }

    long getDisconnectTimeout() { return disconnectTimeout; }

    void setDisconnectTimeout(long disconnectTimeout) {
        this.disconnectTimeout = disconnectTimeout;
    }

    long getHeartbeatTimeout() { return heartbeatTimeout; }

    void setHeartbeatTimeout(long heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    long calculateNextDisconnectTime() { return System.currentTimeMillis() + disconnectTimeout; }

    long calculateNextHeartbeatTime() { return System.currentTimeMillis() + (heartbeatTimeout >> 1); }
}
