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

import java.util.*;
import java.util.concurrent.*;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.api.impl.*;
import com.dxfeed.event.market.*;
import com.dxfeed.promise.Promise;
import junit.framework.TestCase;

public class DXFeedFilterTest extends TestCase {

    private static final String SYMBOL = "A";

    private DXEndpointImpl endpoint;
    private DXPublisher publisher;
    private QDFilter filter = CompositeFilters.valueOf("!" + SYMBOL, DXFeedScheme.getInstance());

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = (DXEndpointImpl) DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        // use in-place execution to avoid context switches
        endpoint.executor(Runnable::run);
        publisher = endpoint.getPublisher();
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("deprecation")
    public void testFilter() {
        DXFeed feed = endpoint.getFeed();
        DXFeedImpl filterFeed = new DXFeedImpl(endpoint, filter);

        List<Trade> tradesReceived = new ArrayList<>();
        List<Trade> filterTradesReceived = new ArrayList<>();

        DXFeedSubscription<Trade> sub = feed.createSubscription(Trade.class);
        sub.setExecutor(Runnable::run);
        sub.addEventListener(tradesReceived::addAll);
        sub.addSymbols(SYMBOL);

        DXFeedSubscription<Trade> filterSub = filterFeed.createSubscription(Trade.class);
        filterSub.addEventListener(filterTradesReceived::addAll);
        filterSub.addSymbols(SYMBOL);

        publisher.publishEvents(Collections.singletonList(new Trade(SYMBOL)));

        assertEquals(1, tradesReceived.size());
        assertEquals(SYMBOL, tradesReceived.get(0).getEventSymbol());

        assertTrue(filterTradesReceived.isEmpty());
        filterFeed.closeImpl();
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
            TimeAndSale.class, SYMBOL, time - 1_000, time + 1_000);
        assertTrue(timeSeriesPromise.isDone());
        assertTrue(timeSeriesPromise.hasException());
    }

    @SuppressWarnings("deprecation")
    public void testDynamicFilter() {
        SimpleDynamicFilter filter = new SimpleDynamicFilter();
        DXFeed feed = new DXFeedImpl(endpoint, filter);

        // Keep subscription
        List<Trade> trades = new ArrayList<>();
        DXFeedSubscription<Trade> sub = feed.createSubscription(Trade.class);
        sub.addEventListener(trades::addAll);
        sub.addSymbols(SYMBOL);

        // Initially - ticker is empty
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(0, trades.size());

        // Empty filter - nothing goes through
        publisher.publishEvents(Collections.singletonList(createTrade(1)));
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(0, trades.size());

        // Let symbol pass through filter
        filter = filter.addSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTrade(2)));
        assertEquals(2, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(2, trades.get(0).getSequence());
        assertEquals(1, trades.size());

        // Filter out symbol - check last event removed from ticker
        filter = filter.removeSymbol(SYMBOL);
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(1, trades.size());

        // Check nothing pass or stored cause of filter
        publisher.publishEvents(Collections.singletonList(createTrade(3)));
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(1, trades.size());
    }

    private static Trade createTrade(int sequence) {
        Trade trade = new Trade(SYMBOL);
        trade.setSequence(sequence);
        return trade;
    }

    private static class SimpleDynamicFilter extends QDFilter {
        private Set<String> symbols = new HashSet<>();

        SimpleDynamicFilter() {
            super(DXFeedScheme.getInstance());
            setName("dynamic");
        }

        SimpleDynamicFilter(SimpleDynamicFilter source, String symbol, boolean add) {
            super(DXFeedScheme.getInstance(), source);
            setName("dynamic");
            this.symbols = new HashSet<>(source.symbols);
            if (add) {
                this.symbols.add(symbol);
            } else {
                this.symbols.remove(symbol);
            }
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return symbols.contains(symbol);
        }

        @Override
        public boolean isDynamic() {
            return true;
        }

        SimpleDynamicFilter addSymbol(String symbol) {
            SimpleDynamicFilter newFilter = new SimpleDynamicFilter(this, symbol, true);
            fireFilterUpdated(newFilter);
            return newFilter;
        }

        SimpleDynamicFilter removeSymbol(String symbol) {
            SimpleDynamicFilter newFilter = new SimpleDynamicFilter(this, symbol, false);
            fireFilterUpdated(newFilter);
            return newFilter;
        }
    }
}
