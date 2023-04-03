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

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.option.TheoPrice;
import com.dxfeed.event.option.Underlying;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.Assert.assertEquals;

public class OptionEventTimeSeriesSubscriptionTest extends AbstractDXPublisherTest {
    private static final String SYMBOL = "ABC";

    public OptionEventTimeSeriesSubscriptionTest(DXEndpoint.Role role) {
        super(role);
    }

    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {DXEndpoint.Role.FEED}
        });
    }

    @Test
    public void testUnderlyingEvents() throws InterruptedException {
        setUp("underlyingTimeSeriesSubscription");
        DXFeedTimeSeriesSubscription<Underlying> sub = feed.createTimeSeriesSubscription(Underlying.class);
        Queue<Underlying> eventQueue = new ArrayBlockingQueue<>(10);
        sub.addEventListener(eventQueue::addAll);
        sub.setSymbols(SYMBOL);
        sub.setFromTime(0);
        publishUnderlying(300, 3.0, IndexedEvent.SNAPSHOT_BEGIN);
        publishUnderlying(200, 2.0, 0);
        publishUnderlying(100, 1.0, 0);
        publishUnderlying(0, 0.5, IndexedEvent.SNAPSHOT_END);
        assertEquals(4, eventQueue.size());
        List<Underlying> events = new ArrayList<>(eventQueue);
        assertUnderlying(events.get(0), 300, 3.0, IndexedEvent.SNAPSHOT_BEGIN);
        assertUnderlying(events.get(1), 200, 2.0, 0);
        assertUnderlying(events.get(2), 100, 1.0, 0);
        assertUnderlying(events.get(3), 0, 0.5, IndexedEvent.SNAPSHOT_END);
        tearDown();
    }

    @Test
    public void testTheoPriceEvents() throws InterruptedException {
        setUp("theoPriceTimeSeriesSubscription");
        DXFeedTimeSeriesSubscription<TheoPrice> sub = feed.createTimeSeriesSubscription(TheoPrice.class);
        Queue<TheoPrice> eventQueue = new ArrayBlockingQueue<>(10);
        sub.addEventListener(eventQueue::addAll);
        sub.setSymbols(SYMBOL);
        sub.setFromTime(0);
        publishTheoPrice(300, 3.0, IndexedEvent.SNAPSHOT_BEGIN);
        publishTheoPrice(200, 2.0, 0);
        publishTheoPrice(100, 1.0, 0);
        publishTheoPrice(0, 0.5, IndexedEvent.SNAPSHOT_END);
        assertEquals(4, eventQueue.size());
        List<TheoPrice> events = new ArrayList<>(eventQueue);
        assertTheoPrice(events.get(0), 300, 3.0, IndexedEvent.SNAPSHOT_BEGIN);
        assertTheoPrice(events.get(1), 200, 2.0, 0);
        assertTheoPrice(events.get(2), 100, 1.0, 0);
        assertTheoPrice(events.get(3), 0, 0.5, IndexedEvent.SNAPSHOT_END);
        tearDown();
    }

    private void publishUnderlying(long time, double volatility, int eventFlags) {
        Underlying underlying = new Underlying(SYMBOL);
        underlying.setTime(time);
        underlying.setEventFlags(eventFlags);
        underlying.setVolatility(volatility);
        publisher.publishEvents(Collections.singletonList(underlying));
        checkpoint();
    }

    private void assertUnderlying(Underlying underlying, long time, double volatility, int eventFlags) {
        assertEquals(SYMBOL, underlying.getEventSymbol());
        assertEquals(time, underlying.getTime());
        assertEquals(volatility, underlying.getVolatility(), 0.0);
        assertEquals(eventFlags, underlying.getEventFlags());
    }

    private void publishTheoPrice(long time, double price, int eventFlags) {
        TheoPrice theoPrice = new TheoPrice(SYMBOL);
        theoPrice.setTime(time);
        theoPrice.setEventFlags(eventFlags);
        theoPrice.setPrice(price);
        publisher.publishEvents(Collections.singletonList(theoPrice));
        checkpoint();
    }

    private void assertTheoPrice(TheoPrice theoPrice, long time, double price, int eventFlags) {
        assertEquals(SYMBOL, theoPrice.getEventSymbol());
        assertEquals(time, theoPrice.getTime());
        assertEquals(price, theoPrice.getPrice(), 0.0);
        assertEquals(eventFlags, theoPrice.getEventFlags());
    }
}
