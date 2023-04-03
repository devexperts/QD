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
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.option.Series;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DXFeedPromiseTest {
    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    private final BlockingQueue<Object> added = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> removed = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        endpoint.executor(Runnable::run);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    // ----- LastEventPromise -----

    @Test
    public void testLastEventPromise() {
        trackSubscription(Trade.class);

        Promise<Trade> aPromise = feed.getLastEventPromise(Trade.class, "A");
        assertAdded("A");
        Promise<Trade> bPromise = feed.getLastEventPromise(Trade.class, "B");
        assertAdded("B");
        Promise<Trade> cPromise = feed.getLastEventPromise(Trade.class, "C");
        assertAdded("C");
        Promise<Trade> bPromise2 = feed.getLastEventPromise(Trade.class, "B");
        assertEmptyAddedAndRemoved();

        assertFalse(aPromise.isDone());
        assertFalse(bPromise.isDone());
        assertFalse(cPromise.isDone());
        assertFalse(bPromise2.isDone());

        publisher.publishEvents(Collections.singletonList(new Trade("A")));
        assertRemoved("A");
        assertTrue(aPromise.isDone());
        assertEquals("A", aPromise.getResult().getEventSymbol());
        assertFalse(bPromise.isDone());
        assertFalse(cPromise.isDone());
        assertFalse(bPromise2.isDone());

        publisher.publishEvents(Collections.singletonList(new Trade("B")));
        assertRemoved("B");
        assertTrue(bPromise.isDone());
        assertEquals("B", bPromise.getResult().getEventSymbol());
        assertTrue(bPromise2.isDone());
        assertEquals("B", bPromise2.getResult().getEventSymbol());
        assertFalse(cPromise.isDone());

        publisher.publishEvents(Collections.singletonList(new Trade("C")));
        assertRemoved("C");
        assertTrue(cPromise.isDone());
        assertEquals("C", cPromise.getResult().getEventSymbol());
    }

    @Test
    public void testLastEventPromiseCancel() {
        trackSubscription(Trade.class);

        Promise<Trade> promise = feed.getLastEventPromise(Trade.class, "T");
        assertAdded("T");
        assertFalse(promise.isDone());

        promise.cancel();
        assertRemoved("T");
        assertTrue(promise.isDone());
    }

    // ----- IndexedEventsPromise -----

    private static final String SERIES_SYMBOL = "TEST";

    @Test
    public void testIndexedEventsPromise() {
        // Series is a simple indexed event with plain delegate logic (unlike Order)
        trackSubscription(Series.class);

        Promise<List<Series>> promise =
            feed.getIndexedEventsPromise(Series.class, SERIES_SYMBOL, IndexedEventSource.DEFAULT);
        assertAdded(SERIES_SYMBOL);
        assertFalse(promise.isDone());

        publishSeries(3, 300, 10.01, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise.isDone());
        publishSeries(2, 200, 10.02, 0);
        assertFalse(promise.isDone());
        publishSeries(1, 100, 10.03, 0);
        assertFalse(promise.isDone());
        publishSeries(0, 0, Double.NaN, IndexedEvent.SNAPSHOT_END | IndexedEvent.REMOVE_EVENT);
        assertRemoved(SERIES_SYMBOL);
        assertTrue(promise.isDone());
        List<Series> list = promise.getResult();
        assertEquals(3, list.size());
        assertSeries(list.get(0), 1, 100, 10.03);
        assertSeries(list.get(1), 2, 200, 10.02);
        assertSeries(list.get(2), 3, 300, 10.01);
    }

    private void publishSeries(long index, int expiration, double volatility, int eventFlags) {
        Series series = new Series(SERIES_SYMBOL);
        series.setIndex(index);
        series.setExpiration(expiration);
        series.setVolatility(volatility);
        series.setEventFlags(eventFlags);
        publisher.publishEvents(Collections.singletonList(series));
    }

    private void assertSeries(Series series, long index, int expiration, double volatility) {
        assertEquals(SERIES_SYMBOL, series.getEventSymbol());
        assertEquals(index, series.getIndex());
        assertEquals(expiration, series.getExpiration());
        assertEquals(volatility, series.getVolatility(), 0.0);
        assertEquals(0, series.getEventFlags());
    }

    // ----- TimeSeriesPromise -----

    private static final CandleSymbol CANDLE_SYMBOL =
        CandleSymbol.valueOf("TEST", CandlePeriod.valueOf(1, CandleType.MINUTE));

    @Test
    public void testTimeSeriesPromise() {
        trackSubscription(Candle.class);
        long time = TimeFormat.DEFAULT.parse("20200116-120000-0500").getTime();
        long period = 60_000;
        long subTime = time - 2 * period;

        Promise<List<Candle>> promise = feed.getTimeSeriesPromise(Candle.class, CANDLE_SYMBOL, subTime, time);
        TimeSeriesSubscriptionSymbol<?> addedSymbol = (TimeSeriesSubscriptionSymbol<?>) added.poll();
        assertNotNull(addedSymbol);
        assertEquals(CANDLE_SYMBOL, addedSymbol.getEventSymbol());
        assertTrue(addedSymbol.getFromTime() <= subTime);
        assertEmptyAddedAndRemoved();
        assertFalse(promise.isDone());

        publishCandle(time, 0, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise.isDone());
        publishCandle(time - period, 1, 0);
        assertFalse(promise.isDone());
        publishCandle(subTime, 2, 0);
        assertRemoved(addedSymbol);
        assertTrue(promise.isDone());
        List<Candle> list = promise.getResult();
        assertEquals(3, list.size());
        assertCandle(list.get(0), subTime, 2);
        assertCandle(list.get(1), time - period, 1);
        assertCandle(list.get(2), time, 0);
    }

    // Test case for [QD-1109] Sometimes getTimeSeriesPromise returns empty list
    @Test
    public void testEmptyTimeSeriesPromise() {
        // Candle is an intricate time series event with complex fetch time heuristic logic
        trackSubscription(Candle.class);
        long time = TimeFormat.DEFAULT.parse("20200116-120000-0500").getTime();
        long period = 60_000;
        long subTime = time - 2 * period;

        Promise<List<Candle>> promise = feed.getTimeSeriesPromise(Candle.class, CANDLE_SYMBOL, subTime, time);
        TimeSeriesSubscriptionSymbol<?> addedSymbol = (TimeSeriesSubscriptionSymbol<?>) added.poll();
        assertNotNull(addedSymbol);
        assertEquals(CANDLE_SYMBOL, addedSymbol.getEventSymbol());
        assertTrue(addedSymbol.getFromTime() <= subTime);
        assertEmptyAddedAndRemoved();
        assertFalse(promise.isDone());

        publishCandle(time, 0, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise.isDone());
        publishCandle(time - period, 1, 0);
        assertFalse(promise.isDone());
        publishCandle(subTime, 2, 0);
        assertRemoved(addedSymbol);
        assertTrue(promise.isDone());
        List<Candle> list = promise.getResult();
        assertEquals(3, list.size());
        assertCandle(list.get(0), subTime, 2);
        assertCandle(list.get(1), time - period, 1);
        assertCandle(list.get(2), time, 0);

        // The DXFeed implementation actually subscribes to some fromTime which is lower than subTime.
        // Data provider sends data up to subscribed fromTime. Sometimes the entire block of data is split.
        // When split interleaves with creation of second promise - that promise is completed with no data.
        // This is the case behind [QD-1109] reproduced here with artificial split interleaved with second promise.

        Promise<List<Candle>> promise2 = feed.getTimeSeriesPromise(Candle.class, CANDLE_SYMBOL, subTime, time);
        TimeSeriesSubscriptionSymbol<?> addedSymbol2 = (TimeSeriesSubscriptionSymbol<?>) added.poll();
        assertNotNull(addedSymbol2);
        assertEquals(CANDLE_SYMBOL, addedSymbol2.getEventSymbol());
        assertEquals(addedSymbol.getFromTime(), addedSymbol2.getFromTime());
        assertEmptyAddedAndRemoved();
        assertFalse(promise2.isDone());

        // Pretend to continue sending of data after split
        publishCandle(time - 3 * period, 3, 0);
        assertFalse(promise2.isDone());

        // Pretend to catch some part of full answer but without proper SNAPSHOT_BEGIN flag
        publishCandle(time - period, 1, 0);
        publishCandle(subTime, 2, 0);
        publishCandle(time - 3 * period, 3, 0);
        assertFalse(promise2.isDone());

        // Send proper snapshot from scratch
        publishCandle(time, 0, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise2.isDone());
        publishCandle(time - period, 1, 0);
        assertFalse(promise2.isDone());
        publishCandle(subTime, 2, 0);
        assertRemoved(addedSymbol);
        assertTrue(promise2.isDone());
        list = promise2.getResult();
        assertEquals(3, list.size());
        assertCandle(list.get(0), subTime, 2);
        assertCandle(list.get(1), time - period, 1);
        assertCandle(list.get(2), time, 0);
    }

    private void publishCandle(long time, long count, int eventFlags) {
        Candle candle = new Candle(CANDLE_SYMBOL);
        candle.setTime(time);
        candle.setCount(count);
        candle.setEventFlags(eventFlags);
        publisher.publishEvents(Collections.singletonList(candle));
    }

    private void assertCandle(Candle candle, long time, long count) {
        assertEquals(CANDLE_SYMBOL, candle.getEventSymbol());
        assertEquals(time, candle.getTime());
        assertEquals(count, candle.getCount());
        assertEquals(0, candle.getEventFlags());
    }

    // ========== shared test utility ==========

    private void trackSubscription(Class<?> eventType) {
        assertEmptyAddedAndRemoved();
        publisher.getSubscription(eventType).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                added.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removed.addAll(symbols);
            }
        });
    }

    private void assertAdded(Object symbol) {
        assertEquals(symbol, added.poll());
        assertEmptyAddedAndRemoved();
    }

    private void assertRemoved(Object symbol) {
        assertEquals(symbol, removed.poll());
        assertEmptyAddedAndRemoved();
    }

    private void assertEmptyAddedAndRemoved() {
        assertEquals(0, added.size());
        assertEquals(0, removed.size());
    }
}
