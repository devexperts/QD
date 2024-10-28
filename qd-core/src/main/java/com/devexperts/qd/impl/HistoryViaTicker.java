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
package com.devexperts.qd.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.LegacyAdapter;

import java.util.function.Consumer;

/**
 * The {@code HistoryViaTicker} simulates {@link QDHistory} interface using
 * specified {@link QDTicker}. As a result it passes and stores only one record
 * for each subscribed symbol-record pair which is 'current' according to original
 * ticker contract.
 */
public class HistoryViaTicker extends AbstractCollector implements QDHistory {
    protected final QDTicker ticker;

    public HistoryViaTicker(QDTicker ticker, Builder<?> builder) {
        super(builder);
        if (ticker.getScheme() != builder.getScheme())
            throw new IllegalArgumentException("Scheme doesn't match");
        this.ticker = ticker;
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return ticker.buildAgent(builder);
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        return ticker.buildDistributor(builder);
    }


    @Override
    public boolean isStoreEverything() {
        return ticker.isStoreEverything();
    }

    @Override
    public void setStoreEverything(boolean store_everything) {
        ticker.setStoreEverything(store_everything);
    }

    @Override
    public long getMinAvailableTime(DataRecord record, int cipher, String symbol) {
        if (!record.hasTime())
            throw new IllegalArgumentException("Record does not contain time.");
        return ((long) ticker.getInt(record.getIntField(0), cipher, symbol) << 32) |
            ((long) ticker.getInt(record.getIntField(1), cipher, symbol) & 0xFFFFFFFFL);
    }

    @Override
    public long getMaxAvailableTime(DataRecord record, int cipher, String symbol) {
        return getMinAvailableTime(record, cipher, symbol);
    }

    @Override
    public int getAvailableCount(DataRecord record, int cipher, String symbol, long startTime, long endTime) {
        long time = getMinAvailableTime(record, cipher, symbol);
        if (time == 0 && !ticker.isAvailable(record, cipher, symbol))
            return 0;
        if (time < Math.min(startTime, endTime) || time > Math.max(startTime, endTime))
            return 0;
        return 1;
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime,
        RecordSink sink)
    {
        if (!sink.hasCapacity())
            return true;
        RecordCursor.Owner owner = RecordCursor.allocateOwner();
        ticker.getData(owner, record, cipher, symbol);
        sink.append(owner.cursor());
        sink.flush();
        return false;
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime, DataVisitor visitor) {
        return examineData(record, cipher, symbol, startTime, endTime, LegacyAdapter.of(visitor));
    }

    @Override
    public QDStats getStats() {
        return ticker.getStats();
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return ticker.isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return ticker.examineSubscription(sink);
    }

    @Override
    public int getSubscriptionSize() {
        return ticker.getSubscriptionSize();
    }

    @Override
    public boolean examineData(RecordSink sink) {
        return ticker.examineData(sink);
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        return ticker.examineDataBySubscription(sink, sub);
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        ticker.setErrorHandler(errorHandler);
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return ticker.getSymbol(chars, offset, length);
    }

    @Override
    public void close() {
        ticker.close();
    }

    @Override
    public void setDroppedLog(Consumer<String> droppedLog) {
        super.setDroppedLog(droppedLog);
        if (ticker instanceof AbstractCollector) {
            ((AbstractCollector) ticker).setDroppedLog(droppedLog);
        }
    }
}
