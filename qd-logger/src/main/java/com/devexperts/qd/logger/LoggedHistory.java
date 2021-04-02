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
package com.devexperts.qd.logger;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.ng.RecordSink;

public class LoggedHistory extends LoggedCollector implements QDHistory {
    private final QDHistory delegate;

    public LoggedHistory(Logger log, QDHistory delegate, Builder<?> builder) {
        super(log, delegate, builder);
        this.delegate = delegate;
    }

    @Override
    public long getMinAvailableTime(DataRecord record, int cipher, String symbol) {
        return delegate.getMinAvailableTime(record, cipher, symbol);
    }

    @Override
    public long getMaxAvailableTime(DataRecord record, int cipher, String symbol) {
        return delegate.getMaxAvailableTime(record, cipher, symbol);
    }

    @Override
    public int getAvailableCount(DataRecord record, int cipher, String symbol, long startTime, long endTime) {
        return delegate.getAvailableCount(record, cipher, symbol, startTime, endTime);
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime, DataVisitor visitor) {
        return delegate.examineData(record, cipher, symbol, startTime, endTime, visitor);
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime,
        long endTime, RecordSink sink)
    {
        return delegate.examineData(record, cipher, symbol, startTime, endTime, sink);
    }
}
