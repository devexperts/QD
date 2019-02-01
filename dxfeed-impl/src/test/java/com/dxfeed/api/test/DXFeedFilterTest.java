/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.api.impl.*;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.promise.Promise;
import junit.framework.TestCase;

public class DXFeedFilterTest extends TestCase {

    public static final String SYMBOL = "A";

    private DXEndpointImpl endpoint;
    private DXPublisher publisher;
    private QDFilter filter = CompositeFilters.valueOf("!" + SYMBOL, DXFeedScheme.getInstance());

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = (DXEndpointImpl) DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        publisher = endpoint.getPublisher();
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("deprecation")
    public void testFilter() throws InterruptedException {
        DXFeed feed = endpoint.getFeed();
        DXFeed filterFeed = new DXFeedImpl(endpoint, filter);

        BlockingQueue<Trade> tradesReceived = new LinkedBlockingDeque<>();
        BlockingQueue<Trade> filterTradesReceived = new LinkedBlockingDeque<>();

        DXFeedSubscription<Trade> sub = feed.createSubscription(Trade.class);
        sub.addEventListener(tradesReceived::addAll);
        sub.addSymbols(SYMBOL);

        DXFeedSubscription<Trade> filterSub = filterFeed.createSubscription(Trade.class);
        filterSub.addEventListener(filterTradesReceived::addAll);
        filterSub.addSymbols(SYMBOL);

        publisher.publishEvents(Collections.singletonList(new Trade(SYMBOL)));

        Trade t = tradesReceived.poll(10, TimeUnit.SECONDS);
        assertNotNull(t);
        assertEquals(SYMBOL, t.getEventSymbol());

        assertTrue(filterTradesReceived.isEmpty());
    }

    @SuppressWarnings("deprecation")
    public void testSeparateFeedClose() throws InterruptedException {
        DXFeed feed = endpoint.getFeed();
        DXFeedImpl filterFeed = new DXFeedImpl(endpoint, filter);

        BlockingQueue<Trade> tradesReceived = new LinkedBlockingDeque<>();
        DXFeedSubscription<Trade> sub = feed.createSubscription(Trade.class);
        sub.addEventListener(tradesReceived::addAll);
        sub.addSymbols(SYMBOL);

        DXFeedSubscription<Trade> filterSub = filterFeed.createSubscription(Trade.class);
        filterSub.addSymbols(SYMBOL);
        filterFeed.closeImpl();

        publisher.publishEvents(Collections.singletonList(new Trade(SYMBOL)));

        Trade t = tradesReceived.poll(10, TimeUnit.SECONDS);
        assertNotNull(t);
        assertEquals(SYMBOL, t.getEventSymbol());
    }

    @SuppressWarnings("deprecation")
    public void testLastEventPromise() {
        DXFeed filterFeed = new DXFeedImpl(endpoint, filter);
        publisher.publishEvents(Collections.singletonList(new Trade(SYMBOL)));

        long time = System.currentTimeMillis();
        TimeAndSale tns = new TimeAndSale(SYMBOL);
        tns.setTime(time);
        publisher.publishEvents(Collections.singletonList(tns));

        Promise<Trade> promise = filterFeed.getLastEventPromise(Trade.class, SYMBOL);
        assertTrue(promise.isDone());
        assertTrue(promise.hasException());

        Promise<List<TimeAndSale>> timeSeriesPromise = filterFeed.getTimeSeriesPromise(
            TimeAndSale.class, SYMBOL, time - 10_000, time + 10_000);
        assertTrue(timeSeriesPromise.isDone());
        assertTrue(timeSeriesPromise.hasException());
    }
}
