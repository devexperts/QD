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
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

class StripedHistory extends StripedCollector<QDHistory> implements QDHistory {
    private final QDHistory[] collectors;

    StripedHistory(QDFactory base, Builder<?> builder, SymbolStriper striper) {
        super(builder, striper);
        collectors = new QDHistory[n];
        for (int i = 0; i < n; i++) {
            collectors[i] = base.historyBuilder()
                .copyFrom(builder)
                .withStats(stats.create(QDStats.SType.HISTORY, "stripe=" + striper.getStripeFilter(i)))
                .build();
        }
    }

    @Override
    QDHistory[] collectors() {
        return collectors;
    }

    @Override
    public long getMinAvailableTime(DataRecord record, int cipher, String symbol) {
        return collector(cipher, symbol).getMinAvailableTime(record, cipher, symbol);
    }

    @Override
    public long getMaxAvailableTime(DataRecord record, int cipher, String symbol) {
        return collector(cipher, symbol).getMaxAvailableTime(record, cipher, symbol);
    }

    @Override
    public int getAvailableCount(DataRecord record, int cipher, String symbol, long startTime, long endTime) {
        return collector(cipher, symbol).getAvailableCount(record, cipher, symbol, startTime, endTime);
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime, DataVisitor visitor) {
        return collector(cipher, symbol).examineData(record, cipher, symbol, startTime, endTime, visitor);
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime,
        RecordSink sink)
    {
        return collector(cipher, symbol).examineData(record, cipher, symbol, startTime, endTime, sink);
    }
}
