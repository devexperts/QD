/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.impl.DXFeedImpl;
import com.dxfeed.api.impl.DXFeedScheme;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DXFeedFilterTest {

    private static final String SYMBOL = "A";

    private DXEndpointImpl endpoint;
    private DXPublisher publisher;
    private QDFilter filter = CompositeFilters.valueOf("!" + SYMBOL, DXFeedScheme.getInstance());

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = (DXEndpointImpl) DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        // use in-place execution to avoid context switches
        endpoint.executor(Runnable::run);
        publisher = endpoint.getPublisher();
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("deprecation")
    @Test
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
    @Test
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
    @Test
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
    @Test
    public void testDynamicFilter() {
        SimpleDynamicFilter filter = new SimpleDynamicFilter();
        DXFeed feed = new DXFeedImpl(endpoint, filter);

        // Keep subscription
        List<Trade> trades = new ArrayList<>();
        DXFeedSubscription<Trade> sub = feed.createSubscription(Trade.class);
        sub.addEventListener(trades::addAll);
        sub.addSymbols(SYMBOL);

        // Initial state - ticker is empty
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(0, trades.size());

        // Empty filter - nothing goes through
        publisher.publishEvents(Collections.singletonList(createTrade(1)));
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(0, trades.size());

        // Modify the filter to let symbol pass through
        filter = filter.addSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTrade(2)));
        assertEquals(2, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(2, trades.get(0).getSequence());
        assertEquals(1, trades.size());

        // Filter out symbol - check that last event was removed from the ticker
        filter = filter.removeSymbol(SYMBOL);
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(1, trades.size());

        // Check nothing pass or stored cause of filter
        publisher.publishEvents(Collections.singletonList(createTrade(3)));
        assertEquals(0, feed.getLastEvent(new Trade(SYMBOL)).getSequence());
        assertEquals(1, trades.size());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDynamicFilterTimeSeriesEvent() {
        SimpleDynamicFilter filter = new SimpleDynamicFilter();
        DXFeed feed = new DXFeedImpl(endpoint, filter);

        // Keep subscription
        List<TimeAndSale> tns = new ArrayList<>();
        DXFeedSubscription<TimeAndSale> sub = feed.createSubscription(TimeAndSale.class);
        sub.addEventListener(tns::addAll);
        sub.addSymbols(new TimeSeriesSubscriptionSymbol<>(SYMBOL, 0));

        // Initial state - no data
        assertEquals(0, tns.size());

        // Empty filter - nothing goes through
        publisher.publishEvents(Collections.singletonList(createTns(1)));
        assertEquals(0, tns.size());

        // Modify the filter to let symbol pass through
        filter = filter.addSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTns(2)));
        assertEquals(1, tns.size());
        assertEquals(2, tns.get(0).getSequence());

        // Filter out symbol - new events must be filtered out
        filter = filter.removeSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTns(3)));
        assertEquals(1, tns.size());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDynamicFilterTimeSeriesSubscription() {
        SimpleDynamicFilter filter = new SimpleDynamicFilter();
        DXFeed feed = new DXFeedImpl(endpoint, filter);

        // Keep subscription
        List<TimeAndSale> tns = new ArrayList<>();
        DXFeedTimeSeriesSubscription<TimeAndSale> sub = feed.createTimeSeriesSubscription(TimeAndSale.class);
        sub.setFromTime(0);
        sub.addEventListener(tns::addAll);
        sub.addSymbols(SYMBOL);

        // Initial state - no data
        assertEquals(0, tns.size());

        // Empty filter - nothing goes through
        publisher.publishEvents(Collections.singletonList(createTns(1)));
        assertEquals(0, tns.size());
        List<TimeAndSale> events = feed.getTimeSeriesIfSubscribed(TimeAndSale.class, SYMBOL, 0, Long.MAX_VALUE);
        assertNull(events);

        // Modify the filter to let symbol pass through
        filter = filter.addSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTns(2)));
        assertEquals(1, tns.size());
        assertEquals(2, tns.get(0).getSequence());
        events = feed.getTimeSeriesIfSubscribed(TimeAndSale.class, SYMBOL, 0, Long.MAX_VALUE);
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(2, events.get(0).getSequence());

        // Filter out symbol - new events must be filtered out
        filter = filter.removeSymbol(SYMBOL);
        publisher.publishEvents(Collections.singletonList(createTns(3)));
        assertEquals(1, tns.size());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDynamicFilterTimeSeriesAgentLeak() {
        SimpleDynamicFilter filter = new SimpleDynamicFilter();
        filter = filter.addSymbol(SYMBOL);
        DXFeed feed = new DXFeedImpl(endpoint, filter);

        // Keep subscription
        List<TimeAndSale> tns = new ArrayList<>();
        DXFeedTimeSeriesSubscription<TimeAndSale> sub = feed.createTimeSeriesSubscription(TimeAndSale.class);
        sub.setFromTime(0);
        sub.addEventListener(tns::addAll);
        sub.addSymbols(SYMBOL);

        // Initial state - no data
        assertEquals(0, tns.size());

        publisher.publishEvents(Collections.singletonList(createTns(1)));
        assertEquals(1, tns.size());
        assertEquals(1, tns.get(0).getSequence());

        // Update filter, e.g. by adding new symbol
        filter = filter.addSymbol("HABA");

        // Check that subscription is not corrupted
        // (if broken it could deliver 2 events: one for HISTORY and one for STREAM)
        publisher.publishEvents(Collections.singletonList(createTns(2)));
        assertEquals(2, tns.size());
        assertEquals(2, tns.get(1).getSequence());
    }

    @Test
    public void testTimeSeriesSubscription() {
        DXFeed feed = new DXFeedImpl(endpoint);
        DXFeedTimeSeriesSubscription<TimeAndSale> sub = feed.createTimeSeriesSubscription(TimeAndSale.class);

        sub.addSymbols("A");
        TimeSeriesSubscriptionSymbol<String> symbol = new TimeSeriesSubscriptionSymbol<>("A", 0);

        assertTrue(sub.getSymbols().contains("A"));
        assertFalse(sub.getSymbols().contains(symbol));
        assertFalse(sub.getDecoratedSymbols().contains("A"));
        assertTrue(sub.getDecoratedSymbols().contains(symbol));

        // Check that iterator and contains are consistent
        DXFeedTimeSeriesSubscription<TimeAndSale> sub2 = feed.createTimeSeriesSubscription(TimeAndSale.class);
        sub2.addSymbols("A");
        assertEquals(sub.getSymbols(), sub2.getSymbols());
        assertEquals(sub.getDecoratedSymbols(), sub2.getDecoratedSymbols());
    }

    private static Trade createTrade(int sequence) {
        Trade trade = new Trade(SYMBOL);
        trade.setSequence(sequence);
        return trade;
    }

    private static TimeAndSale createTns(int sequence) {
        TimeAndSale tns = new TimeAndSale(SYMBOL);
        tns.setSequence(sequence);
        tns.setTime(0);
        return tns;
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
            return symbols.contains(record.getScheme().getCodec().decode(cipher, symbol));
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
