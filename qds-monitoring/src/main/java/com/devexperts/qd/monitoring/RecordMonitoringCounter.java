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
package com.devexperts.qd.monitoring;

import com.devexperts.qd.stats.QDStats;

/**
 * Utility class to compute delta of per-record values from {@link QDStats}.
 * It keeps previously retrieved values.
 */
class RecordMonitoringCounter {
    private static final long[] EMPTY_LONGS = new long[0];

    private long oldValue;
    private long[] values = EMPTY_LONGS;
    private long[] dest = EMPTY_LONGS;

    public synchronized void addDeltaToCur(QDStats stats, Cur cur) {
        if (cur.statValue.isRid()) {
            int n = stats.getRidCount(); // always retrieve all stats
            if (n > 0) {
                if (values.length < n)
                    resize(n);
                stats.addValues(cur.statValue, false, dest);
                for (int i = 0; i < n; i++) {
                    long old = values[i];
                    long delta = dest[i] - old;
                    values[i] = dest[i];
                    dest[i] = 0;
                    if (i < cur.recordDelta.length) // but Cur may have smaller number of slots reserved for deltas
                        cur.recordDelta[i] += delta;
                }
            }
        }
        long newValue = stats.getValue(cur.statValue);
        long delta = newValue - oldValue;
        oldValue = newValue;
        cur.totalDelta += delta;
    }

    private void resize(int n) {
        if (values.length < n) {
            long[] newValues = new long[n];
            System.arraycopy(values, 0, newValues, 0, values.length);
            values = newValues;
            long[] newDest = new long[n];
            System.arraycopy(dest, 0, newDest, 0, dest.length);
            dest = newDest;
        }
    }
}
