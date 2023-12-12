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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;

class FeedDelayerMonitoringTask implements Runnable {
    private final FeedDelayer delayer;
    private final Logging log;
    private long lastOutgoing;

    FeedDelayerMonitoringTask(FeedDelayer delayer, Logging log) {
        this.delayer = delayer;
        this.log = log;
    }

    public void run() {
        long outgoing = delayer.getOutgoingRecords();
        long incoming = delayer.getIncomingRecords();
        long time = delayer.getEldestRecordTimestamp();
        long prevOutgoing = lastOutgoing;
        lastOutgoing = outgoing;
        log.info("\bRecords passed delayer: " + (outgoing - prevOutgoing) +
            ", in delayer buffer: " + (incoming - outgoing) +
            ", delay time: " + (time == 0 ? 0.0 : (System.currentTimeMillis() - time) / 1000.0));
    }
}
