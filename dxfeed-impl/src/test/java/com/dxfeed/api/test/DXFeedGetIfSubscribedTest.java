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
package com.dxfeed.api.test;

import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DXFeedGetIfSubscribedTest {
    private static final String SYMBOL = "IBM";
    private static final CandleSymbol CANDLE_SYMBOL = CandleSymbol.valueOf(SYMBOL, CandlePeriod.DAY);

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
    }

    @Test
    public void testGetLast() {
        Quote q = new Quote(SYMBOL);
        assertSame(q, feed.getLastEvent(q));
        assertEmpty(q);
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertNull(q);
        DXFeedSubscription<Quote> sub = feed.createSubscription(Quote.class);
        sub.addSymbols(SYMBOL);
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertNotNull(q);
        assertEmpty(q);
        // publish something
        q = new Quote(SYMBOL);
        q.setAskPrice(10.5);
        publisher.publishEvents(Collections.singletonList(q));
        q = feed.getLastEventIfSubscribed(Quote.class, SYMBOL);
        assertNotNull(q);
        assertEquals(10.5, q.getAskPrice(), 0.0);
        q.setAskPrice(Double.NaN);
        assertEmpty(q);
    }

    private void assertEmpty(Quote q) {
        Quote e = new Quote(SYMBOL);
        assertEquals(e.getEventSymbol(), q.getEventSymbol());
        assertEquals(e.getBidPrice(), q.getBidPrice(), 0.0);
        assertEquals(e.getBidSize(), q.getBidSize());
        assertEquals(e.getBidTime(), q.getBidTime());
        assertEquals(e.getBidExchangeCode(), q.getBidExchangeCode());
        assertEquals(e.getAskPrice(), q.getAskPrice(), 0.0);
        assertEquals(e.getAskSize(), q.getAskSize());
        assertEquals(e.getAskTime(), q.getAskTime());
        assertEquals(e.getAskExchangeCode(), q.getAskExchangeCode());
    }

    @Test
    public void testGetIndexed() {
        List<Order> os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertNull(os);
        DXFeedSubscription<Order> sub = feed.createSubscription(Order.class);
        sub.addSymbols(SYMBOL);
        os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertNotNull(os);
        assertTrue(os.isEmpty());
        // publish something
        Order o = new Order(SYMBOL);
        o.setIndex(1);
        o.setOrderSide(Side.BUY);
        o.setPrice(10.5);
        o.setSize(100);
        publisher.publishEvents(Collections.singletonList(o));
        os = feed.getIndexedEventsIfSubscribed(Order.class, SYMBOL, OrderSource.DEFAULT);
        assertNotNull(os);
        assertEquals(1, os.size());
        o = os.get(0);
        assertEquals(1, o.getIndex());
        assertEquals(Side.BUY, o.getOrderSide());
        assertEquals(10.5, o.getPrice(), 0.0);
        assertEquals(100, o.getSize());
    }

    @Test
    public void testGetTimeSeries() {
        long fromTime = TimeFormat.DEFAULT.parse("20150101").getTime();
        long toTime = TimeFormat.DEFAULT.parse("20150710").getTime();
        List<Candle> cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertNull(cs);
        DXFeedSubscription<Candle> sub = feed.createSubscription(Candle.class);
        // wrong sub time
        sub.addSymbols(new TimeSeriesSubscriptionSymbol<>(CANDLE_SYMBOL, toTime));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertNull(cs);
        // right sub time
        sub.addSymbols(new TimeSeriesSubscriptionSymbol<>(CANDLE_SYMBOL, fromTime));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertNotNull(cs);
        assertTrue(cs.isEmpty());
        // publish something
        Candle c = new Candle(CANDLE_SYMBOL);
        c.setTime(fromTime);
        c.setClose(10.5);
        c.setVolume(100);
        publisher.publishEvents(Collections.singletonList(c));
        cs = feed.getTimeSeriesIfSubscribed(Candle.class, CANDLE_SYMBOL, fromTime, toTime);
        assertNotNull(cs);
        assertEquals(1, cs.size());
        c = cs.get(0);
        assertEquals(fromTime, c.getTime());
        assertEquals(10.5, c.getClose(), 0.0);
        assertEquals(100, c.getVolume());
    }
}
