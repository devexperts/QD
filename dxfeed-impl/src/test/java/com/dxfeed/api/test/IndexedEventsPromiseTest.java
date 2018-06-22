/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import java.util.*;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.option.Series;
import com.dxfeed.promise.Promise;
import junit.framework.TestCase;

public class IndexedEventsPromiseTest extends TestCase {
    private static final String SYMBOL = "XYZ";

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private final Queue<Object> added = new ArrayDeque<>();
    private final Queue<Object> removed = new ArrayDeque<>();

    public IndexedEventsPromiseTest() {
    }

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        endpoint.executor(tasks::add);
        publisher.getSubscription(Series.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                added.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removed.addAll(symbols);
            }
        });
        runAllTasks(); // initialize subscription
    }

    private void runAllTasks() {
        while (!tasks.isEmpty())
            runTask();
        ThreadCleanCheck.after();
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
    }

    public void testSeriesPromise() {
        // crate indexed events promise
        Promise<List<Series>> promise = feed.getIndexedEventsPromise(Series.class, SYMBOL, IndexedEventSource.DEFAULT);
        assertTrue(!promise.isDone());
        assertEquals(0, added.size());
        runTask(); // process sub task
        assertNoTasks();
        assertEquals(SYMBOL, added.poll());
        assertNoAddedOrRemoved();
        assertTrue(!promise.isDone());

        // now publish snapshot
        publish(3, 300, 10.01, IndexedEvent.SNAPSHOT_BEGIN);
        assertTrue(!promise.isDone());
        assertNoTasks();
        publish(2, 200, 10.02, 0);
        assertTrue(!promise.isDone());
        assertNoTasks();
        publish(1, 100, 10.03, 0);
        assertTrue(!promise.isDone());
        assertNoTasks();
        publish(0, 0, Double.NaN, IndexedEvent.SNAPSHOT_END | IndexedEvent.REMOVE_EVENT);
        assertTrue(promise.isDone());
        assertNoAddedOrRemoved();
        runTask(); // process remove sub
        assertNoTasks();
        assertEquals(SYMBOL, removed.poll());
        assertNoAddedOrRemoved();
        List<Series> list = promise.getResult();
        assertEquals(3, list.size());
        assertSeries(list.get(0), 1, 100, 10.03);
        assertSeries(list.get(1), 2, 200, 10.02);
        assertSeries(list.get(2), 3, 300, 10.01);
    }

    private void assertNoAddedOrRemoved() {
        assertEquals(0, added.size());
        assertEquals(0, removed.size());
    }

    private void publish(long index, int expiration, double volatility, int eventFlags) {
        Series series = new Series(SYMBOL);
        series.setIndex(index);
        series.setExpiration(expiration);
        series.setEventFlags(eventFlags);
        series.setVolatility(volatility);
        publisher.publishEvents(Collections.singletonList(series));
    }

    private void assertSeries(Series series, long index, int expiration, double volatility) {
        assertEquals(SYMBOL, series.getEventSymbol());
        assertEquals(index, series.getIndex());
        assertEquals(expiration, series.getExpiration());
        assertEquals(0, series.getEventFlags());
        assertEquals(volatility, series.getVolatility());
    }

    private void assertNoTasks() {
        assertEquals(0, tasks.size());
    }

    private void runTask() {
        tasks.poll().run();
    }
}
