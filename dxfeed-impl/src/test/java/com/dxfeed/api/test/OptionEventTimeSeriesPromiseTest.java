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
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.option.TheoPrice;
import com.dxfeed.event.option.Underlying;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OptionEventTimeSeriesPromiseTest {
    private static final String SYMBOL = "XYZ";

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private final Queue<Object> added = new ArrayDeque<>();
    private final Queue<Object> removed = new ArrayDeque<>();

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        endpoint.executor(tasks::add);
        ObservableSubscriptionChangeListener subscriptionChangeListener = new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                added.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removed.addAll(symbols);
            }
        };
        publisher.getSubscription(Underlying.class).addChangeListener(subscriptionChangeListener);
        publisher.getSubscription(TheoPrice.class).addChangeListener(subscriptionChangeListener);
        runAllTasks(); // initialize subscription
    }

    private void runAllTasks() {
        while (!tasks.isEmpty())
            runTask();
        ThreadCleanCheck.after();
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
    }

    @Test
    public void testUnderlyingPromise() {
        Promise<List<Underlying>> promise = feed.getTimeSeriesPromise(Underlying.class, SYMBOL, 0, 300);
        assertFalse(promise.isDone());
        assertEquals(0, added.size());
        runTask(); // process sub task
        assertNoTasks();
        assertTrue(added.poll().toString().startsWith(SYMBOL));
        assertNoAddedOrRemoved();
        assertFalse(promise.isDone());

        // now publish underlying snapshot
        publishUnderlying(300, 10.01, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishUnderlying(200, 10.02, 0);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishUnderlying(100, 10.03, 0);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishUnderlying(0, 10.04, 0);
        assertTrue(promise.isDone());
        assertNoAddedOrRemoved();
        runTask(); // process remove sub
        assertNoTasks();
        assertTrue(removed.poll().toString().startsWith(SYMBOL));
        assertNoAddedOrRemoved();
        List<Underlying> list = promise.getResult();
        assertEquals(4, list.size());
        assertUnderlying(list.get(0), 0, 10.04);
        assertUnderlying(list.get(1), 100, 10.03);
        assertUnderlying(list.get(2), 200, 10.02);
        assertUnderlying(list.get(3), 300, 10.01);
    }

    @Test
    public void testTheoPricePromise() {
        Promise<List<TheoPrice>> promise = feed.getTimeSeriesPromise(TheoPrice.class, SYMBOL, 0, 300);
        assertFalse(promise.isDone());
        assertEquals(0, added.size());
        runTask(); // process sub task
        assertNoTasks();
        assertTrue(added.poll().toString().startsWith(SYMBOL));
        assertNoAddedOrRemoved();
        assertFalse(promise.isDone());

        // now publish underlying snapshot
        publishTheoPrice(300, 10.01, IndexedEvent.SNAPSHOT_BEGIN);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishTheoPrice(200, 10.02, 0);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishTheoPrice(100, 10.03, 0);
        assertFalse(promise.isDone());
        assertNoTasks();
        publishTheoPrice(0, 10.04, 0);
        assertTrue(promise.isDone());
        assertNoAddedOrRemoved();
        runTask(); // process remove sub
        assertNoTasks();
        assertTrue(removed.poll().toString().startsWith(SYMBOL));
        assertNoAddedOrRemoved();
        List<TheoPrice> list = promise.getResult();
        assertEquals(4, list.size());
        assertTheoPrice(list.get(0), 0, 10.04);
        assertTheoPrice(list.get(1), 100, 10.03);
        assertTheoPrice(list.get(2), 200, 10.02);
        assertTheoPrice(list.get(3), 300, 10.01);
    }

    private void assertNoAddedOrRemoved() {
        assertEquals(0, added.size());
        assertEquals(0, removed.size());
    }

    private void publishUnderlying(long time, double volatility, int eventFlags) {
        Underlying underlying = new Underlying(SYMBOL);
        underlying.setTime(time);
        underlying.setEventFlags(eventFlags);
        underlying.setVolatility(volatility);
        publisher.publishEvents(Collections.singletonList(underlying));
    }

    private void assertUnderlying(Underlying underlying, long time, double volatility) {
        assertEquals(SYMBOL, underlying.getEventSymbol());
        assertEquals(time, underlying.getTime());
        assertEquals(0, underlying.getEventFlags());
        assertEquals(volatility, underlying.getVolatility(), 0.0);
    }

    private void publishTheoPrice(long time, double price, int eventFlags) {
        TheoPrice theoPrice = new TheoPrice(SYMBOL);
        theoPrice.setTime(time);
        theoPrice.setEventFlags(eventFlags);
        theoPrice.setPrice(price);
        publisher.publishEvents(Collections.singletonList(theoPrice));
    }

    private void assertTheoPrice(TheoPrice theoPrice, long time, double price) {
        assertEquals(SYMBOL, theoPrice.getEventSymbol());
        assertEquals(time, theoPrice.getTime());
        assertEquals(0, theoPrice.getEventFlags());
        assertEquals(price, theoPrice.getPrice(), 0.0);
    }

    private void assertNoTasks() {
        assertEquals(0, tasks.size());
    }

    private void runTask() {
        tasks.poll().run();
    }
}
