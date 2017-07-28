/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.SystemProperties;
import com.dxfeed.event.candle.impl.CandleMapping;
import com.dxfeed.event.candle.impl.TradeHistoryMapping;
import com.dxfeed.event.market.impl.TimeAndSaleMapping;

@ServiceProvider(order = 100)
public class HistorySubscriptionFilterImpl implements HistorySubscriptionFilter {
    private final int candleMaxRecordCount = SystemProperties.getIntProperty(
        HistorySubscriptionFilterImpl.class, "candleMaxRecordCount", 8000);
    private final int tradeMaxRecordCount = SystemProperties.getIntProperty(
        HistorySubscriptionFilterImpl.class, "tradeMaxRecordCount", 1000);

    @Override
    public long getMinHistoryTime(DataRecord record, int cipher, String symbol) {
        return Long.MIN_VALUE;
    }

    @Override
    public int getMaxRecordCount(DataRecord record, int cipher, String symbol) {
        if (record.getMapping(CandleMapping.class) != null) {
            return candleMaxRecordCount;
        } else if (record.getMapping(TimeAndSaleMapping.class) != null || record.getMapping(TradeHistoryMapping.class) != null) {
            return tradeMaxRecordCount;
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public String toString() {
        return "HistorySubscriptionFilterImpl{" +
            "candleMaxRecordCount=" + candleMaxRecordCount + ", " +
            "tradeMaxRecordCount=" + tradeMaxRecordCount + "}";
    }
}
