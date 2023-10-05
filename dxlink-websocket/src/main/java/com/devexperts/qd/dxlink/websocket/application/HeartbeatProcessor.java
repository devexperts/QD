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

import com.devexperts.util.TimePeriod;

class HeartbeatProcessor {
    private final DxLinkWebSocketApplicationConnectionFactory factory;
    private long heartbeatTimeoutInMs;
    private long heartbeatPeriodInMs;
    private long newHeartbeatPeriodInMs;

    HeartbeatProcessor(DxLinkWebSocketApplicationConnectionFactory factory, long heartbeatTimeoutInMs,
                       long heartbeatPeriodInMs, long initialHeartbeatPeriod)
    {
        this.factory = factory;
        this.heartbeatTimeoutInMs = heartbeatTimeoutInMs;
        this.heartbeatPeriodInMs = heartbeatPeriodInMs;
        this.newHeartbeatPeriodInMs = initialHeartbeatPeriod;
    }

    void receiveUpdateHeartbeatTimeout(long heartbeatTimeoutInMs) {
        if (heartbeatTimeoutInMs != this.heartbeatTimeoutInMs)
            this.heartbeatTimeoutInMs = heartbeatTimeoutInMs;
        if (heartbeatTimeoutInMs != this.factory.getHeartbeatTimeout().getTime())
            this.factory.setHeartbeatTimeout(TimePeriod.valueOf(heartbeatTimeoutInMs));
    }

    void receiveUpdateHeartbeatPeriod(long heartbeatPeriodInMs) {
        newHeartbeatPeriodInMs = heartbeatPeriodInMs;
        if (heartbeatPeriodInMs != factory.getHeartbeatPeriod().getTime())
            factory.setHeartbeatPeriod(TimePeriod.valueOf(heartbeatPeriodInMs));
    }

    long calculateNextDisconnectTime() { return System.currentTimeMillis() + heartbeatTimeoutInMs; }

    long calculateNextHeartbeatTime() {
        heartbeatPeriodInMs = Math.min(heartbeatPeriodInMs * 2, newHeartbeatPeriodInMs);
        return System.currentTimeMillis() + (heartbeatPeriodInMs >> 1);
    }

    long getHeartbeatTimeoutInMs() { return heartbeatTimeoutInMs; }

    long getHeartbeatPeriodInMs() { return heartbeatPeriodInMs; }
}
