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
import com.devexperts.qd.util.RateLimiter;

import java.util.function.IntConsumer;

abstract class NetTestWorkingThread extends Thread {

    private final RateLimiter rateLimiter;
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
        if (side.config.rateLimiters != null) {
            RateLimiter rateLimiter = side.config.rateLimiters.get(Integer.toString(index));
            if (rateLimiter == null) {
                rateLimiter = side.config.rateLimiters.get("*");
            }
            this.rateLimiter = rateLimiter;
        } else {
            this.rateLimiter = null;
        }
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

    protected void availableOrWait(IntConsumer availableConsumer) throws InterruptedException {
        if (rateLimiter != null) {
            availableConsumer.accept((int) rateLimiter.availableOrWait());
        }
    }

    protected void processed(int records) {
        if (rateLimiter != null) {
            rateLimiter.reportConsumed(records);
        }
    }

    protected void consume(int records) throws InterruptedException {
        if (rateLimiter != null) {
            rateLimiter.consume(records);
        }
    }
}
