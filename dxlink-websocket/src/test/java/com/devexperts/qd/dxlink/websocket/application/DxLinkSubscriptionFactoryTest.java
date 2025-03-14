/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.dxfeed.api.impl.DXFeedScheme;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static com.devexperts.qd.QDContract.HISTORY;
import static com.devexperts.qd.QDContract.STREAM;
import static com.devexperts.qd.QDContract.TICKER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DxLinkSubscriptionFactoryTest {
    private final DXFeedScheme scheme = DXFeedScheme.getInstance();
    private final Queue<DxLinkSubscription> subscriptions = new ArrayDeque<>();
    private final DxLinkSubscriptionFactory factory = new DxLinkSubscriptionFactory();

    @After
    public void tearDown() {
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void testHistorySubscription() {
        addSubscription(HISTORY, "Candle", "AAPL{=d}", 0L);
        assertSubscription("Candle", "AAPL{=d}", 0L);

        addSubscription(HISTORY, "TimeAndSale", "AAPL", 10L);
        assertSubscription("TimeAndSale", "AAPL", 10L);
    }

    @Test
    public void testHistorySubscriptionWithExchangeCode() {
        addSubscription(HISTORY, "Candle", "AAPL&Q{=d}", 0L);
        assertSubscription("Candle", "AAPL&Q{=d}", 0L);
    }

    @Test
    public void testHistorySubscriptionWithRegionalRecords() {
        addSubscription(HISTORY, "TimeAndSale&Q", "AAPL", 100L);
        assertSubscription("TimeAndSale", "AAPL&Q", 100L);
    }

    @Test
    public void testNonZeroFromTimeValidOnlyForHistorySubscription() {
        addSubscription(TICKER, "Candle", "AAPL{=d}", 10L);
        addSubscription(STREAM, "Candle", "AAPL{=d}", 10L);
        assertTrue(subscriptions.isEmpty()); // skip records with the non-history contract and non-zero from time

        addSubscription(TICKER, "TimeAndSale", "AAPL", 0L);
        assertSubscription("TimeAndSale", "AAPL");
    }

    @Test
    public void testRegionalRecords() {
        addSubscription(TICKER, "Quote&Q", "AAPL");
        assertSubscription("Quote", "AAPL&Q");

        addSubscription(STREAM, "Trade&K", "IBM");
        assertSubscription("Trade", "IBM&K");

        addSubscription(HISTORY, "TimeAndSale&Q", "MSFT", 100L);
        assertSubscription("TimeAndSale", "MSFT&Q", 100L);
    }

    @Test
    public void testIndexedRecordsWithoutSource() {
        addSubscription(HISTORY, "Order", "AAPL");
        assertSubscription("Order", "AAPL", "DEFAULT");

        addSubscription(STREAM, "Order", "AAPL");
        assertSubscription("Order", "AAPL", "DEFAULT");

        addSubscription(HISTORY, "OtcMarketsOrder", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "DEFAULT");

        addSubscription(STREAM, "OtcMarketsOrder", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "DEFAULT");

        addSubscription(HISTORY, "OptionSale", "AAPL");
        assertSubscription("OptionSale", "AAPL", "DEFAULT");

        addSubscription(STREAM, "OptionSale", "AAPL");
        assertSubscription("OptionSale", "AAPL", "DEFAULT");
    }

    @Test
    public void testIndexedRecordsWithSource() {
        addSubscription(HISTORY, "Order#ntv", "AAPL");
        assertSubscription("Order", "AAPL", "ntv");

        addSubscription(STREAM, "Order#ntv", "AAPL");
        assertSubscription("Order", "AAPL", "ntv");

        addSubscription(HISTORY, "Order#NTV", "AAPL");
        assertSubscription("Order", "AAPL", "NTV");

        addSubscription(STREAM, "Order#NTV", "AAPL");
        assertSubscription("Order", "AAPL", "NTV");

        addSubscription(HISTORY, "OtcMarketsOrder#pink", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "pink");

        addSubscription(STREAM, "OtcMarketsOrder#pink", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "pink");

        addSubscription(HISTORY, "OtcMarketsOrder#pink", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "pink");

        addSubscription(STREAM, "OtcMarketsOrder#pink", "AAPL");
        assertSubscription("OtcMarketsOrder", "AAPL", "pink");
    }

    @Test
    public void testIndexedRecordWithZeroFromTime() {
        addSubscription(HISTORY, "Order#ntv", "AAPL", 0L);
        assertSubscription("Order", "AAPL", "ntv");

        addSubscription(HISTORY, "MarketMaker", "IBM", 0L);
        assertSubscription("MarketMaker", "IBM", "DEFAULT");

        addSubscription(HISTORY, "OptionSale", "IBM", 0L);
        assertSubscription("OptionSale", "IBM", "DEFAULT");

        addSubscription(HISTORY, "Series", "IBM", 0L);
        assertSubscription("Series", "IBM", "DEFAULT");
    }

    @Test
    public void testSkipIndexedRecordsWithNonZeroFromTime() {
        addSubscription(HISTORY, "Order#ntv", "AAPL", 10L);
        addSubscription(HISTORY, "OtcMarketsOrder#pink", "AAPL", 100L);
        assertTrue(subscriptions.isEmpty()); // skip records with the source and non-zero from time
    }

    @Test
    public void testRecordsWithWildcardSymbol() {
        addSubscription(STREAM, "Quote", "*");
        assertSubscription("Quote", "*");
    }

    @Test
    public void testSkipRegionalRecordWithWildcardSymbol() {
        addSubscription(TICKER, "Quote&Q", "*");
        addSubscription(STREAM, "Trade&K", "*");
        addSubscription(HISTORY, "TimeAndSale&Q", "*", 0);
        assertTrue(subscriptions.isEmpty()); // skip wildcard subscription for regional records
    }

    private void addSubscription(QDContract contract, String recordName, String symbol) {
        addSubscription(contract, recordName, symbol, 0);
    }

    private void addSubscription(QDContract contract, String recordName, String symbol, long fromTime) {
        int cipher = scheme.getCodec().encode(symbol);
        DataRecord record = scheme.findRecordByName(recordName);
        fromTime = TimeSequenceUtil.getTimeSequenceFromTimeMillis(fromTime);
        DxLinkSubscription subscription = factory.createSubscription(contract, record, cipher, symbol, fromTime);
        if (subscription != null)
            subscriptions.add(subscription);
    }

    private void assertSubscription(String eventType, String symbol) {
        assertSubscription(eventType, symbol, null, null);
    }

    private void assertSubscription(String eventType, String symbol, String source) {
        assertSubscription(eventType, symbol, source, null);
    }

    private void assertSubscription(String eventType, String symbol, Long fromTime) {
        assertSubscription(eventType, symbol, null, fromTime);
    }

    private void assertSubscription(String eventType, String symbol, String source, Long fromTime) {
        DxLinkSubscription subscription = subscriptions.poll();
        assertNotNull(subscription);
        assertEquals(eventType, subscription.type);
        assertEquals(symbol, subscription.symbol);
        assertEquals(source, subscription.source);
        assertEquals(fromTime, subscription.fromTime);
    }
}
