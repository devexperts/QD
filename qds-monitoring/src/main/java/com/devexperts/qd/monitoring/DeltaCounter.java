/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import com.devexperts.qd.stats.QDStats;

import java.util.Arrays;

/**
 * Utility class to compute delta of per-record values from {@link QDStats}.
 * It keeps previously retrieved values.
 */
class DeltaCounter {
    private static final long[] EMPTY = new long[0];

    private long oldValue;
    private long newValue;
    private long[] oldValues = EMPTY;
    private long[] newValues = EMPTY;

    public synchronized void collect(QDStats stats, QDStats.SValue statValue) {
        if (statValue.isRid()) {
            // Always retrieve all stats
            int count = stats.getRidCount();
            if (count > 0) {
                if (oldValues.length < count) {
                    // Resize internal arrays
                    oldValues = Arrays.copyOfRange(oldValues, 0, count);
                    newValues = Arrays.copyOfRange(newValues, 0, count);
                }

                // Move previous values to the oldValues array
                System.arraycopy(oldValues, 0, newValues, 0, count);
                Arrays.fill(newValues, 0);
                // Copy record values from stats
                stats.addValues(statValue, false, newValues);
            }
        }
        oldValue = newValue;
        newValue = stats.getValue(statValue);
    }

    public synchronized void aggregate(SumDeltaCounter sumCounter) {
        if (sumCounter.statValue.isRid()) {
            // Sum counter may have a smaller number of slots reserved for deltas
            int count = Math.min(oldValues.length, sumCounter.recordDelta.length);
            for (int i = 0; i < count; i++) {
                sumCounter.recordDelta[i] += (newValues[i] - oldValues[i]);
            }
        }
        sumCounter.totalDelta += (newValue - oldValue);
    }
}
