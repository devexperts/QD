/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.test;

import java.util.Collections;
import java.util.List;

import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.util.TimeFormat;
import com.dxfeed.api.*;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.*;
import com.dxfeed.event.market.*;
import junit.framework.TestCase;

public class DXFeedGetIfSubscribedTest extends TestCase {
    private static final String SYMBOL = "IBM";
    private static final CandleSymbol CANDLE_SYMBOL = CandleSymbol.valueOf(SYMBOL, CandlePeriod.DAY);

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
    }

    public void testGetLast() {
        Quote q = new Quote(SYMBOL);
        assertSame(q, feed.getLastEvent(q));
        assertEmpty(q);
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertTrue(q == null);
        DXFeedSubscription<Quote> sub = feed.createSubscription(Quote.class);
        sub.addSymbols(SYMBOL);
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertTrue(q != null);
        assertEmpty(q);
        // publish something
        q = new Quote(SYMBOL);
        q.setAskPrice(10.5);
        publisher.publishEvents(Collections.singletonList(q));
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertTrue(q != null);
        assertEquals(10.5, q.getAskPrice());
        q.setAskPrice(Double.NaN);
        assertEmpty(q);
    }

    private void assertEmpty(Quote q) {
        Quote e = new Quote(SYMBOL);
        assertEquals(e.getEventSymbol(), q.getEventSymbol());
        assertEquals(e.getBidPrice(), q.getBidPrice());
        assertEquals(e.getBidSize(), q.getBidSize());
        assertEquals(e.getBidTime(), q.getBidTime());
        assertEquals(e.getBidExchangeCode(), q.getBidExchangeCode());
        assertEquals(e.getAskPrice(), q.getAskPrice());
        assertEquals(e.getAskSize(), q.getAskSize());
        assertEquals(e.getAskTime(), q.getAskTime());
        assertEquals(e.getAskExchangeCode(), q.getAskExchangeCode());
    }

    public void testGetIndexed() {
        List<Order> os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertTrue(os == null);
        DXFeedSubscription<Order> sub = feed.createSubscription(Order.class);
        sub.addSymbols(SYMBOL);
        os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertTrue(os != null);
        assertTrue(os.isEmpty());
        // publish something
        Order o = new Order(SYMBOL);
        o.setIndex(1);
        o.setOrderSide(Side.BUY);
        o.setPrice(10.5);
        o.setSize(100);
        publisher.publishEvents(Collections.singletonList(o));
        os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertTrue(os != null);
        assertEquals(1, os.size());
        o = os.get(0);
        assertEquals(1, o.getIndex());
        assertEquals(Side.BUY, o.getOrderSide());
        assertEquals(10.5, o.getPrice());
        assertEquals(100, o.getSize());
    }

    public void testGetTimeSeries() {
        long fromTime = TimeFormat.DEFAULT.parse("20150101").getTime();
        long toTime = TimeFormat.DEFAULT.parse("20150710").getTime();
        List<Candle> cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertTrue(cs == null);
        DXFeedSubscription<Candle> sub = feed.createSubscription(Candle.class);
        // wrong sub time
        sub.addSymbols(new TimeSeriesSubscriptionSymbol<>(CANDLE_SYMBOL, toTime));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertTrue(cs == null);
        // right sub time
        sub.addSymbols(new TimeSeriesSubscriptionSymbol<>(CANDLE_SYMBOL, fromTime));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertTrue(cs != null);
        assertTrue(cs.isEmpty());
        // publish something
        Candle c = new Candle(CANDLE_SYMBOL);
        c.setTime(fromTime);
        c.setClose(10.5);
        c.setVolume(100);
        publisher.publishEvents(Collections.singletonList(c));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertTrue(cs != null);
        assertEquals(1, cs.size());
        c = cs.get(0);
        assertEquals(fromTime, c.getTime());
        assertEquals(10.5, c.getClose());
        assertEquals(100, c.getVolume());
    }
}
