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
package com.devexperts.qd.qtp.nio;

import com.devexperts.monitoring.Monitored;
import com.devexperts.util.TimeDistribution;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Statistics about NIO Writer or Reader thread pool.
 */
public class NioPoolCounters {
    public static final TimeDistribution.Precision PRECISION = TimeDistribution.Precision.HIGH;
    public static final NioPoolCounters EMPTY = new NioPoolCounters(0);

    int size;
    final AtomicInteger activeThreads = new AtomicInteger();
    volatile int registeredSockets; // in selector
    volatile long totalSelectedSockets; // by selector
    final TimeDistribution appTime; // time spend by application protocol (to retrieve / process)
    final TimeDistribution wakeupTime; // time to wake up selector
    final TimeDistribution busyTime; // time to process selected sockets

    public NioPoolCounters(int size) {
        this.size = size;
        appTime = new TimeDistribution(PRECISION);
        wakeupTime = new TimeDistribution(PRECISION);
        busyTime = new TimeDistribution(PRECISION);
    }

    public void copyFrom(NioPoolCounters other) {
        copyInternal(other);
        totalSelectedSockets = other.totalSelectedSockets;
        appTime.copyFrom(other.appTime);
        wakeupTime.copyFrom(other.wakeupTime);
        busyTime.copyFrom(other.busyTime);
    }

    public void copyFromDiff(NioPoolCounters cur, NioPoolCounters old) {
        copyInternal(cur);
        totalSelectedSockets = cur.totalSelectedSockets - old.totalSelectedSockets;
        appTime.copyFromDiff(cur.appTime, old.appTime);
        wakeupTime.copyFromDiff(cur.wakeupTime, old.wakeupTime);
        busyTime.copyFromDiff(cur.busyTime, old.busyTime);
    }

    private void copyInternal(NioPoolCounters other) {
        size = other.size;
        activeThreads.set(other.activeThreads.get());
        registeredSockets = other.registeredSockets;
    }

    @Monitored(name = "size", description = "Number of threads in the pool")
    public int getSize() {
        return size;
    }

    @Monitored(name = "active", description = "Number of active threads in the pool")
    public int getActiveThreads() {
        return activeThreads.get();
    }

    @Monitored(name = "sockets_registered", description = "Number of sockets registered in selector")
    public int getRegisteredSockets() {
        return registeredSockets;
    }

    @Monitored(name = "sockets_selected", description = "Average number of sockets selected")
    public double getAvgSelectedSockets() {
        double value = (double) totalSelectedSockets / Math.max(1, busyTime.getCount());
        int roundTo = 100; // round to two digits
        return Math.floor(value * roundTo + 0.5) / roundTo;
    }

    @Monitored(name = "time_app", description = "Distribution of time spent in application protocol code to retrieve/process bytes", expand = true)
    public TimeDistribution getAppTime() {
        return appTime;
    }

    @Monitored(name = "time_wakeup", description = "Distribution of time for selector wakeup", expand = true)
    public TimeDistribution getWakeupTime() {
        return wakeupTime;
    }

    @Monitored(name = "time_busy", description = "Distribution of time selector thread is busy with selected sockets", expand = true)
    public TimeDistribution getBusyTime() {
        return busyTime;
    }

    @Override
    public String toString() {
        return "NioPoolCounters{" +
            "size=" + size +
            ", activeThreads=" + activeThreads +
            ", registeredSockets=" + registeredSockets +
            ", avgSelectedSockets=" + getAvgSelectedSockets() +
            ", app={" + appTime + "}" +
            ", wakeup={" + wakeupTime + "}" +
            ", busy={" + busyTime + "}" +
            '}';
    }
}
