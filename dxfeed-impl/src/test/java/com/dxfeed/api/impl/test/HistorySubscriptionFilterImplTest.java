/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.dxfeed.api.impl.HistorySubscriptionFilterImpl;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistorySubscriptionFilterImplTest {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();

    @After
    public void tearDown() {
        for (int i = 0; i < SCHEME.getRecordCount(); i++) {
            System.clearProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount." +
                SCHEME.getRecord(i).getName());
        }
        System.clearProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.candle");
        System.clearProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.timeSeries");
        System.clearProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.indexed");
    }

    @Test
    public void defaultMaxRecordCountCorrect() {
        HistorySubscriptionFilterImpl filter = new HistorySubscriptionFilterImpl();

        assertEquals(8000, filter.getMaxRecordCount(SCHEME.findRecordByName("Candle"), 0, "ABC"));
        assertEquals(8000, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.Day"), 0, "ABC"));
        assertEquals(8000, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.2hour"), 0, "ABC"));
        assertEquals(8000, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.1min"), 0, "ABC"));
        assertEquals(8000, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.133ticks"), 0, "ABC"));

        assertEquals(1000, filter.getMaxRecordCount(SCHEME.findRecordByName("TimeAndSale"), 0, "ABC"));
        assertEquals(1000, filter.getMaxRecordCount(SCHEME.findRecordByName("TradeHistory"), 0, "ABC"));

        assertEquals(Integer.MAX_VALUE, filter.getMaxRecordCount(SCHEME.findRecordByName("Order"), 0, "ABC"));
        assertEquals(Integer.MAX_VALUE, filter.getMaxRecordCount(SCHEME.findRecordByName("MarketMaker"), 0, "ABC"));
        assertEquals(Integer.MAX_VALUE, filter.getMaxRecordCount(SCHEME.findRecordByName("Series"), 0, "ABC"));

        assertEquals(1000, filter.getMaxRecordCount(SCHEME.findRecordByName("Underlying"), 0, "ABC"));
        assertEquals(1000, filter.getMaxRecordCount(SCHEME.findRecordByName("TheoPrice"), 0, "ABC"));
        assertEquals(1000, filter.getMaxRecordCount(SCHEME.findRecordByName("Greeks"), 0, "ABC"));
    }

    @Test
    public void adjustedMaxRecordCountCorrect() {
        System.setProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.Candle", "1");
        System.setProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.Trade.Day", "1");
        System.setProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.candle", "10");
        System.setProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.timeSeries", "2");
        System.setProperty("com.dxfeed.api.impl.HistorySubscriptionFilterImpl.maxRecordCount.indexed", "3");

        HistorySubscriptionFilterImpl filter = new HistorySubscriptionFilterImpl();

        assertEquals(1, filter.getMaxRecordCount(SCHEME.findRecordByName("Candle"), 0, "ABC"));
        assertEquals(1, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.Day"), 0, "ABC"));
        assertEquals(10, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.2hour"), 0, "ABC"));
        assertEquals(10, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.1min"), 0, "ABC"));
        assertEquals(10, filter.getMaxRecordCount(SCHEME.findRecordByName("Trade.133ticks"), 0, "ABC"));

        assertEquals(2, filter.getMaxRecordCount(SCHEME.findRecordByName("TimeAndSale"), 0, "ABC"));
        assertEquals(2, filter.getMaxRecordCount(SCHEME.findRecordByName("TradeHistory"), 0, "ABC"));

        assertEquals(3, filter.getMaxRecordCount(SCHEME.findRecordByName("Order"), 0, "ABC"));
        assertEquals(3, filter.getMaxRecordCount(SCHEME.findRecordByName("MarketMaker"), 0, "ABC"));
        assertEquals(3, filter.getMaxRecordCount(SCHEME.findRecordByName("Series"), 0, "ABC"));

        assertEquals(2, filter.getMaxRecordCount(SCHEME.findRecordByName("Underlying"), 0, "ABC"));
        assertEquals(2, filter.getMaxRecordCount(SCHEME.findRecordByName("TheoPrice"), 0, "ABC"));
        assertEquals(2, filter.getMaxRecordCount(SCHEME.findRecordByName("Greeks"), 0, "ABC"));
    }
}
