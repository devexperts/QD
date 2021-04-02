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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageConnector;

/**
 * Collects throughput statistics.
 */
class NetTestStatisticsCollector implements Runnable {
    private static final Logging log = Logging.getLogging(NetTestStatisticsCollector.class);

    private final NetTestSide side;
    private long lastTime = Long.MIN_VALUE;
    private long lastCount = 0;

    NetTestStatisticsCollector(NetTestSide side) {
        this.side = side;
    }

    public void run() {
        long curTime = System.currentTimeMillis();
        long curCount = 0;
        for (NetTestWorkingThread workingThread : side.threads) {
            curCount += workingThread.processedRecords;
        }
        if (lastTime == Long.MIN_VALUE) {
            lastTime = curTime;
            lastCount = curCount;
            return;
        }
        long curValue = (curCount - lastCount) * 1000 / (curTime - lastTime);

        int connected = 0;
        for (MessageConnector connector : side.connectors) {
            connected += connector.getConnectionCount();
        }
        log.info("\b{*nettest*} " + curValue + " RPS (" + connected + " connections)");
        lastTime = curTime;
        lastCount = curCount;
    }
}
