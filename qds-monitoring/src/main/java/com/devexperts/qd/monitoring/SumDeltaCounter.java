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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.stats.QDStats;

import java.util.Arrays;

/**
 * Collects total and per-record delta values for specific stat values.
 */
class SumDeltaCounter {
    private static final long[] EMPTY = new long[0];

    final QDStats.SValue statValue;

    long totalDelta;
    long[] recordDelta = EMPTY; // Per-record deltas, only used when statValue.isRid()

    SumDeltaCounter(QDStats.SValue statValue) {
        this.statValue = statValue;
    }

    void clear() {
        totalDelta = 0;
    }

    void clearRid(int recordCount) {
        if (statValue.isRid()) {
            // Reserve space for record deltas
            if (recordCount == 0) {
                recordDelta = EMPTY;
            } else if (recordDelta.length != recordCount) {
                recordDelta = new long[recordCount];
            } else {
                Arrays.fill(recordDelta, 0);
            }
        }
    }

    int findMaxRecord() {
        // Don't report if less than 1% of total bytes or delta is too small
        long max = totalDelta / 100;
        if (max <= 0)
            return -1;
        int index = -1;
        for (int i = 0; i < recordDelta.length; i++) {
            if (recordDelta[i] > max) {
                max = recordDelta[i];
                index = i;
            }
        }
        return index;
    }

    String formatMax(DataScheme scheme, StringBuilder buff, String name, int index, String sep) {
        // Note: totalDelta may be zero even when curVal[index] > 0, because of async reading
        if (index < 0 || totalDelta <= 0) {
            return sep;
        }
        // Note: because of async reading v may be over 100%, so we clamp it to 100% to avoid surprising log results
        int v = Math.min(100, (int) (recordDelta[index] * 100 / totalDelta));
        buff.append(sep).append(name).append(' ').append(scheme.getRecord(index).getName())
            .append(": ").append(v).append("%");
        return "; ";
    }
}
