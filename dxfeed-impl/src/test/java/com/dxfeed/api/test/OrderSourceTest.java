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

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.market.*;
import junit.framework.TestCase;

public class OrderSourceTest extends TestCase {
    private DXEndpoint endpoint;
    private DXFeedSubscription<Order> sub;

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final Queue<Object> addOrderSub = new ArrayDeque<>();
    private final Queue<Object> removeOrderSub = new ArrayDeque<>();
    private final Queue<Object> addQuoteSub = new ArrayDeque<>();
    private final Queue<Object> removeQuoteSub = new ArrayDeque<>();
    private final Queue<Order> orders = new ArrayDeque<>();
    private final Random rnd = new Random(20140930);

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        sub = endpoint.getFeed().createSubscription(Order.class);
        endpoint.executor(tasks::add);
        endpoint.getPublisher().getSubscription(Order.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                addOrderSub.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removeOrderSub.addAll(symbols);
            }
        });
        endpoint.getPublisher().getSubscription(Quote.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                addQuoteSub.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removeQuoteSub.addAll(symbols);
            }
        });
        sub.addEventListener(orders::addAll);
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
    }

    private void runTasks() {
        while (!tasks.isEmpty())
            tasks.poll().run();
    }

    public void testPubSubDepthSource() throws InterruptedException {
        checkPubSubDepthSource(OrderSource.DEFAULT);
        checkPubSubDepthSource(OrderSource.NTV);
        checkPubSubDepthSource(OrderSource.ISE);
    }

    private void checkPubSubDepthSource(OrderSource source) {
        String symbol = "TEST1";
        IndexedEventSubscriptionSymbol<String> indexedSymbol =
            new IndexedEventSubscriptionSymbol<>(symbol, source);
        sub.addSymbols(indexedSymbol);
        runTasks();
        assertEquals(indexedSymbol, addOrderSub.poll());
        assertEquals(0, addOrderSub.size());
        checkEventWithSource(symbol, source);
    }

    private void checkEventWithSource(String symbol, OrderSource source) {
        Order order = new Order(symbol);
        double expectedPrice = (1234 + rnd.nextInt(1000)) * 0.01;
        Side expectedSide = rnd.nextBoolean() ? Side.BUY : Side.SELL;
        order.setSource(source);
        order.setPrice(expectedPrice);
        order.setOrderSide(expectedSide);
        endpoint.getPublisher().publishEvents(Collections.singletonList(order));
        // check
        runTasks();
        Order in = orders.poll();
        assertEquals(0, orders.size());
        assertEquals(symbol, in.getEventSymbol());
        assertEquals(source, in.getSource());
        assertEquals(expectedPrice, in.getPrice());
        assertEquals(expectedSide, in.getOrderSide());
    }

    public void testPubSubAllSources() throws InterruptedException {
        // subscribe to all source using a plain string
        String symbol = "TEST2";
        sub.addSymbols(symbol);
        // check that we got sub on all publishable sources
        Set<Object> expectedOrderSub = new HashSet<>();
        for (OrderSource source : OrderSource.publishable(Order.class)) {
            expectedOrderSub.add(new IndexedEventSubscriptionSymbol<>(symbol, source));
        }
        // get all actually subscribed
        runTasks();
        assertEquals(expectedOrderSub, takeSubSet(addOrderSub));
        // check that Quote subscription had arrived on all regional and composite symbols
        // poll all actually subscribed
        assertEquals(allQuotesSet(symbol), takeSubSet(addQuoteSub));
        // now publish at each source and check that events arrive
        // all directly publishable sources first
        for (OrderSource source : OrderSource.publishable(Order.class)) {
            Order order = new Order(symbol);
            int index = rnd.nextInt(100000);
            int size = rnd.nextInt(100000);
            order.setIndex(index);
            order.setSource(source);
            order.setSize(size);
            order.setOrderSide(rnd.nextBoolean() ? Side.BUY : Side.SELL);
            long composedIndex = order.getIndex();
            endpoint.getPublisher().publishEvents(Collections.singletonList(order));
            // check
            runTasks();
            Order in = orders.poll();
            assertEquals(0, orders.size());
            assertEquals(symbol, in.getEventSymbol());
            assertEquals(source, in.getSource());
            assertEquals(composedIndex, in.getIndex());
            assertEquals(size, in.getSize());
        }
        // now publish composite quote and check its arrival as two orders
        checkSyntheticQuoteOrders(symbol, '\0', OrderSource.COMPOSITE_BID, OrderSource.COMPOSITE_ASK);
        // now publish regionals quotes and check their arrival as two orders
        for (char c = 'A'; c <= 'Z'; c++)
            checkSyntheticQuoteOrders(symbol, c, OrderSource.REGIONAL_BID, OrderSource.REGIONAL_ASK);
    }

    private Set<Object> allQuotesSet(String symbol) {
        Set<Object> expectedQuoteSub = new HashSet<>();
        expectedQuoteSub.add(symbol);
        for (char c = 'A'; c <= 'Z'; c++)
            expectedQuoteSub.add(symbol + "&" + c);
        return expectedQuoteSub;
    }

    private Set<Object> takeSubSet(Queue<?> addSub) {
        Set<Object> subSet = new HashSet<>(addSub);
        addSub.clear();
        return subSet;
    }

    private void checkSyntheticQuoteOrders(String symbol, char exchange, OrderSource bidSource, OrderSource askSource) {
        Quote quote = new Quote(MarketEventSymbols.changeExchangeCode(symbol, exchange));
        int bidSize = rnd.nextInt(100000);
        int askSize = rnd.nextInt(100000);
        quote.setBidSize(bidSize);
        quote.setAskSize(askSize);
        endpoint.getPublisher().publishEvents(Collections.singletonList(quote));
        // check
        runTasks();
        // pull at most 2 orders
        Order bidOrder = orders.poll();
        Order askOrder = orders.poll();
        assertEquals(0, orders.size());
        if (bidSource == null || !bidOrder.getSource().equals(bidSource)) {
            Order tmp = bidOrder;
            bidOrder = askOrder;
            askOrder = tmp;
        }
        // bid order
        if (bidSource != null) {
            assertEquals(symbol, bidOrder.getEventSymbol());
            assertEquals(bidSource, bidOrder.getSource());
            assertEquals(bidSize, bidOrder.getSize());
            assertEquals(exchange, bidOrder.getExchangeCode());
        } else
            assertTrue(bidOrder == null);
        // ask order
        if (askSource != null) {
            assertEquals(symbol, askOrder.getEventSymbol());
            assertEquals(askSource, askOrder.getSource());
            assertEquals(askSize, askOrder.getSize());
            assertEquals(exchange, askOrder.getExchangeCode());
        } else
            assertTrue(askOrder == null);
    }

    // Mix subscription on different sources in a single subscription
    public void testMixSources() throws InterruptedException {
        String symbol = "TEST3";
        IndexedEventSubscriptionSymbol<String> subBid = new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.COMPOSITE_BID);
        IndexedEventSubscriptionSymbol<String> subAsk = new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.COMPOSITE_ASK);
        sub.addSymbols(subBid, subAsk);
        // check that quote subscription appears
        runTasks();
        assertEquals(symbol, addQuoteSub.poll());
        assertEquals(0, addQuoteSub.size());
        assertEquals(0, addOrderSub.size());
        // check that quote event is converted into bid and ask orders
        checkSyntheticQuoteOrders(symbol, '\0', OrderSource.COMPOSITE_BID, OrderSource.COMPOSITE_ASK);
        // unsubscribe from composite bid
        sub.removeSymbols(subBid);
        // it should not unsubscribe from quote (yet!)
        runTasks();
        assertEquals(0, addQuoteSub.size());
        assertEquals(0, removeQuoteSub.size());
        // should produce only askOrder on event
        checkSyntheticQuoteOrders(symbol, '\0', null, OrderSource.COMPOSITE_ASK);
        // now unsubscribe from composite ask
        sub.removeSymbols(subAsk);
        // it should unsubscribe from quote
        runTasks();
        assertEquals(0, addQuoteSub.size());
        assertEquals(symbol, removeQuoteSub.poll());
        assertEquals(0, removeQuoteSub.size());
    }

    // Mix subscription on a source with generic subscription on all sources
    public void testMixSourceAndGeneric() {
        String symbol = "TEST4";
        OrderSource source = OrderSource.ISE;
        IndexedEventSubscriptionSymbol<String> subIse = new IndexedEventSubscriptionSymbol<>(symbol, source);
        sub.addSymbols(subIse, symbol);
        // check that ISE subscription appears and quote sub appears, too
        runTasks();
        assertTrue(takeSubSet(addOrderSub).contains(subIse));
        assertEquals(allQuotesSet(symbol), takeSubSet(addQuoteSub));
        // there should be only one copy of event delivered, despite the fact we've subscribed "twice"
        checkEventWithSource(symbol, source);
        // unsubscribe from a generic symbol, subIse shall remain
        sub.removeSymbols(symbol);
        runTasks();
        assertEquals(Collections.emptySet(), takeSubSet(addOrderSub));
        assertEquals(Collections.emptySet(), takeSubSet(addQuoteSub));
        assertTrue(!takeSubSet(removeOrderSub).contains(subIse));
        assertEquals(allQuotesSet(symbol), takeSubSet(removeQuoteSub));
        // check that sourced event is still delivered
        checkEventWithSource(symbol, source);
        // unsubscribe completely
        sub.removeSymbols(subIse);
        runTasks();
        assertEquals(Collections.emptySet(), takeSubSet(addOrderSub));
        assertEquals(Collections.emptySet(), takeSubSet(addQuoteSub));
        assertEquals(Collections.<Object>singleton(subIse), takeSubSet(removeOrderSub));
    }
}
