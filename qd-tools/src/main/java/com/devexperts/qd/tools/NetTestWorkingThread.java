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
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.QDEndpoint;

abstract class NetTestWorkingThread extends Thread {

    protected final NetTestSide side;
    protected final int index;
    protected final QDEndpoint endpoint;
    protected long sumLatency;
    protected volatile long processedRecords;

    NetTestWorkingThread(String name, int index, NetTestSide side, QDEndpoint endpoint) {
        super(name + "-" + index);
        setDaemon(true);
        this.index = index;
        this.side = side;
        this.endpoint = endpoint;
        processedRecords = 0;
    }

    synchronized void addStats(long currentLatency, long currentRecords) {
        sumLatency += currentLatency;
        processedRecords += currentRecords;
    }

    synchronized void getStats(Stats target) {
        target.sumLatency = sumLatency;
        target.processedRecords = processedRecords;
    }

    static class Stats {
        long sumLatency;
        long processedRecords;
    }

    @Override
    public abstract void run();
}
