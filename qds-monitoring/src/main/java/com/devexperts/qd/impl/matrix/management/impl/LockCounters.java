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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.util.TimeDistribution;

import static com.devexperts.util.TimeDistribution.Precision.LOW;

/**
 * Performance counters for locks.
 * These counters are implementation-dependent and are subject to change in any future version.
 */
public class LockCounters {
    private final TimeDistribution wait;
    private final TimeDistribution lock;

    public LockCounters() {
        wait = new TimeDistribution(LOW);
        lock = new TimeDistribution(LOW);
    }

    public LockCounters(LockCounters other) {
        wait = new TimeDistribution(other.wait);
        lock = new TimeDistribution(other.lock);
    }

    public LockCounters(LockCounters cur, LockCounters old) {
        wait = new TimeDistribution(cur.wait, old.wait);
        lock = new TimeDistribution(cur.lock, old.lock);
    }

    public void add(LockCounters other) {
        wait.add(other.wait);
        lock.add(other.lock);
    }

    public TimeDistribution getWaitTimes() {
        return wait;
    }

    public TimeDistribution getLockTimes() {
        return lock;
    }

    public void countLock(long waitNanos, long lockNanos) {
        // Null checks are needed for [QD-516] Support data dumps in debug dump reader,
        // so that older version of dumps can be read (TimeDistribution instances were not previously dumped)
        if (waitNanos > 0 && wait != null)
            wait.addMeasurement(waitNanos);
        if (lock != null)
            lock.addMeasurement(lockNanos);
    }

    public String fmtString(long millis) {
        return "{" + (wait.getCount() > 0 ? "wait " + fmtDistribution(wait, millis) + "; " : "") +
            "lock " + fmtDistribution(lock, millis) + "}";
    }

    public void reportDataTo(ReportBuilder rb, long millis) {
        reportDistributionDataTo(rb, wait, millis);
        reportDistributionDataTo(rb, lock, millis);
    }

    public static void reportHeaderTo(ReportBuilder rb) {
        reportDistributionHeaderTo(rb, "WAIT");
        reportDistributionHeaderTo(rb, "LOCK");
    }

    private static String fmtDistribution(TimeDistribution dist, long millis) {
        int percent = millis == 0 ? 0 : (int) (dist.getSumNanos() / (millis * 1.0e4));
        return (percent == 0 ? "" : (percent + "% 1cpu, ")) + dist.toString();
    }

    private static void reportDistributionDataTo(ReportBuilder rb, TimeDistribution dist, long millis) {
        int percent = millis == 0 ? 0 : (int) (dist.getSumNanos() / (millis * 1.0e4));
        rb.td(percent).td(dist.getCount());
        rb.td(TimeDistribution.formatNanos(dist.getAverageNanos()));
        rb.td(TimeDistribution.formatNanos(dist.getMinNanos()));
        rb.td(TimeDistribution.formatNanos(dist.getLowerNanos()));
        rb.td(TimeDistribution.formatNanos(dist.getMedianNanos()));
        rb.td(TimeDistribution.formatNanos(dist.getUpperNanos()));
        rb.td(TimeDistribution.formatNanos(dist.getMaxNanos()));
    }

    private static void reportDistributionHeaderTo(ReportBuilder rb, String type) {
        rb.td(type + "%").td("times");
        rb.td("avg");
        rb.td("min");
        rb.td("lower");
        rb.td("median");
        rb.td("upper");
        rb.td("max");
    }

}
