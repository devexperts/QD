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

public class NioPoolCountersHolder {
    private final NioPoolCounters cur;
    private NioPoolCounters countersOnReset = new NioPoolCounters(0);
    private NioPoolCounters counters = new NioPoolCounters(0);
    private NioPoolCounters countersOnDelta = new NioPoolCounters(0);
    private NioPoolCounters delta = new NioPoolCounters(0);

    public NioPoolCountersHolder(NioPoolCounters counters) {
        this.cur = counters;
    }

    public NioPoolCounters getCounters() {
        counters.copyFromDiff(cur, countersOnReset);
        return counters;
    }

    public void resetCounters() {
        countersOnReset.copyFrom(cur);
    }

    public NioPoolCounters getCountersDelta() {
        delta.copyFromDiff(cur, countersOnDelta);
        countersOnDelta.copyFrom(cur);
        return delta;
    }
}
