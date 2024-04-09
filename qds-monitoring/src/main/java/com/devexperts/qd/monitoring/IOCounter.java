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

import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.stats.QDStats;

/**
 * A collection of IO counters for a given connector.
 */
class IOCounter {

    static final int READ_BYTES = 0;
    static final int WRITE_BYTES = 1;
    static final int READ_RTTS = 2;
    static final int WRITE_RTTS = 3;
    static final int SUB_READ_RECORDS = 4;
    static final int SUB_WRITE_RECORDS = 5;
    static final int DATA_READ_RECORDS = 6;
    static final int DATA_WRITE_RECORDS = 7;
    static final int DATA_READ_LAGS = 8;
    static final int DATA_WRITE_LAGS = 9;

    static final int COUNTER_COUNT = 10;

    static final QDStats.SValue[] VALUES = {
        QDStats.SValue.IO_READ_BYTES,
        QDStats.SValue.IO_WRITE_BYTES,
        QDStats.SValue.IO_READ_RTTS,
        QDStats.SValue.IO_WRITE_RTTS,
        QDStats.SValue.IO_SUB_READ_RECORDS,
        QDStats.SValue.IO_SUB_WRITE_RECORDS,
        QDStats.SValue.IO_DATA_READ_RECORDS,
        QDStats.SValue.IO_DATA_WRITE_RECORDS,
        QDStats.SValue.IO_DATA_READ_LAGS,
        QDStats.SValue.IO_DATA_WRITE_LAGS,
    };

    private final MessageConnector connector;
    private final QDStats stats;
    private final DeltaCounter[] counters;
    boolean unused;

    IOCounter(MessageConnector connector, QDStats stats) {
        this.connector = connector;
        this.stats = stats;
        this.counters = new DeltaCounter[COUNTER_COUNT];
        for (int i = 0; i < COUNTER_COUNT; i++) {
            counters[i] = new DeltaCounter();
        }
    }

    MessageConnector getConnector() {
        return connector;
    }

    QDStats getStats() {
        return stats;
    }

    void resetUnused() {
        unused = true;
    }

    public boolean isUnused() {
        return unused;
    }

    void collect() {
        unused = false;
        if (stats != null) {
            for (int i = 0; i < COUNTER_COUNT; i++) {
                counters[i].collect(stats, VALUES[i]);
            }
        }
    }

    void aggregate(SumDeltaCounter[] sumCounters) {
        for (int i = 0; i < COUNTER_COUNT; i++) {
            counters[i].aggregate(sumCounters[i]);
        }
    }
}
