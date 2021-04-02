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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.kit.TimeField;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.SystemProperties;
import com.dxfeed.event.candle.impl.CandleMapping;
import com.dxfeed.event.candle.impl.TradeHistoryMapping;
import com.dxfeed.event.market.impl.TimeAndSaleMapping;
import com.dxfeed.event.option.impl.GreeksMapping;
import com.dxfeed.event.option.impl.TheoPriceMapping;
import com.dxfeed.event.option.impl.UnderlyingMapping;

import java.util.Arrays;

@ServiceProvider(order = 100)
public class HistorySubscriptionFilterImpl implements HistorySubscriptionFilter {

    private final int candleMaxRecordCount = SystemProperties.getIntProperty(
        HistorySubscriptionFilterImpl.class, "maxRecordCount.candle",
        SystemProperties.getIntProperty(HistorySubscriptionFilterImpl.class, "candleMaxRecordCount", 8000));

    private final int timeSeriesMaxRecordCount = SystemProperties.getIntProperty(
        HistorySubscriptionFilterImpl.class, "maxRecordCount.timeSeries",
        SystemProperties.getIntProperty(HistorySubscriptionFilterImpl.class, "tradeMaxRecordCount", 1000));

    private final int indexedMaxRecordCount = SystemProperties.getIntProperty(
        HistorySubscriptionFilterImpl.class, "maxRecordCount.indexed",
        Integer.MAX_VALUE);


    private final SynchronizedIndexedSet<DataRecord, RecordLimit> maxRecordCounts = SynchronizedIndexedSet.create(RecordLimit::getRecord);
    private volatile RecordLimit[] maxRecordCache = new RecordLimit[256];

    @Override
    public long getMinHistoryTime(DataRecord record, int cipher, String symbol) {
        return Long.MIN_VALUE;
    }

    @Override
    public int getMaxRecordCount(DataRecord record, int cipher, String symbol) {
        // Cache is intentionally NOT synchronized in any way. Only minimal array visibility is provided.
        // Concurrent threads independently compute same result and reach eventual consistency in a stable environment.
        int id = record.getId();
        RecordLimit[] cache = maxRecordCache; // Atomic volatile read.
        if (id >= cache.length) {
            cache = Arrays.copyOf(cache, Math.max(cache.length * 2, id + 1));
            maxRecordCache = cache; // Atomic volatile write.
        }
        RecordLimit limit = cache[id];
        if (limit == null || limit.getRecord() != record) {
            limit = maxRecordCounts.getByKey(record);
            if (limit == null)
                limit = maxRecordCounts.putIfAbsentAndGet(createRecordLimit(record));
            cache[id] = limit;
        }
        return limit.getLimit();
    }

    private RecordLimit createRecordLimit(DataRecord record) {
        int defaultMaxRecordCount;
        if (record.getMapping(CandleMapping.class) != null) {
            defaultMaxRecordCount = candleMaxRecordCount;
        } else if (record.getMapping(TimeAndSaleMapping.class) != null ||
            record.getMapping(TradeHistoryMapping.class) != null ||
            record.getMapping(UnderlyingMapping.class) != null ||
            record.getMapping(TheoPriceMapping.class) != null ||
            record.getMapping(GreeksMapping.class) != null ||
            //checks that unknown record should be considered as time series event
            record.getIntFieldCount() > 0 && record.getIntField(0) instanceof TimeField)
        {
            defaultMaxRecordCount = timeSeriesMaxRecordCount;
        } else if (record.hasTime()) { // all other records with QD time are considered indexed
            defaultMaxRecordCount = indexedMaxRecordCount;
        } else { // records without QD time shall not appear here, so...
            defaultMaxRecordCount = 0;
        }

        //event property >= category property >= old-fashioned property
        String propName = generatePropertyName(record);
        int maxRecordCount = SystemProperties.getIntProperty(HistorySubscriptionFilterImpl.class, propName, defaultMaxRecordCount);
        return new RecordLimit(record, maxRecordCount);
    }

    @Override
    public String toString() {
        return "HistorySubscriptionFilterImpl{" +
            "candle=" + candleMaxRecordCount + ", " +
            "timeSeries=" + timeSeriesMaxRecordCount + ", " +
            "indexed=" + indexedMaxRecordCount + "}";
    }

    private String generatePropertyName(DataRecord record) {
        return "maxRecordCount." + record.getName();
    }

    private static class RecordLimit {
        private final DataRecord record;
        private final int limit;

        RecordLimit(DataRecord record, int limit) {
            this.record = record;
            this.limit = limit;
        }

        DataRecord getRecord() {
            return record;
        }

        public int getLimit() {
            return limit;
        }
    }
}
