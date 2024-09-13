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

import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedParametersRunnerFactory;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.MarketMaker;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderBaseDelegateImpl;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import static com.dxfeed.event.IndexedEvent.REMOVE_EVENT;
import static com.dxfeed.event.IndexedEvent.SNAPSHOT_BEGIN;
import static com.dxfeed.event.IndexedEvent.SNAPSHOT_END;
import static com.dxfeed.event.IndexedEvent.TX_PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(IsolatedParametersRunnerFactory.class)
@Isolated({"com.dxfeed.api", "com.dxfeed.event", "com.devexperts.qd", "com.devexperts.rmi"})
public class OrderSourceTest {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final Queue<Object> addOrderSub = new ArrayDeque<>();
    private final Queue<Object> removeOrderSub = new ArrayDeque<>();
    private final Queue<Object> addQuoteSub = new ArrayDeque<>();
    private final Queue<Object> removeQuoteSub = new ArrayDeque<>();
    private final Queue<Object> addMarketMakerSub = new ArrayDeque<>();
    private final Queue<Object> removeMarketMakerSub = new ArrayDeque<>();
    private final Queue<Order> orders = new ArrayDeque<>();
    private final Random rnd = new Random(20140930);

    private DXEndpoint endpoint;
    private DXFeedSubscription<Order> sub;
    private boolean useUnitarySource;
    private String originUnitaryOrderSourceProp; // saved property value

    @Parameter
    public String unitaryOrderSourceProp;

    @Parameters(name = "unitaryOrderSourceProp={0}")
    public static List<String> params() {
        return Arrays.asList(null, "false", "true");
    }

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        originUnitaryOrderSourceProp =
            setSystemProperty(OrderBaseDelegateImpl.DXSCHEME_UNITARY_ORDER_SOURCE, unitaryOrderSourceProp);
        useUnitarySource =
            SystemProperties.getBooleanProperty(OrderBaseDelegateImpl.DXSCHEME_UNITARY_ORDER_SOURCE, false);
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        sub = endpoint.getFeed().createSubscription(Order.class);
        endpoint.executor(tasks::add);
        endpoint.getPublisher().getSubscription(Order.class).addChangeListener(
            new ObservableSubscriptionChangeListener() {
                @Override
                public void symbolsAdded(Set<?> symbols) {
                    addOrderSub.addAll(symbols);
                }

                @Override
                public void symbolsRemoved(Set<?> symbols) {
                    removeOrderSub.addAll(symbols);
                }
            }
        );
        endpoint.getPublisher().getSubscription(Quote.class).addChangeListener(
            new ObservableSubscriptionChangeListener() {
                @Override
                public void symbolsAdded(Set<?> symbols) {
                    addQuoteSub.addAll(symbols);
                }

                @Override
                public void symbolsRemoved(Set<?> symbols) {
                    removeQuoteSub.addAll(symbols);
                }
            }
        );
        endpoint.getPublisher().getSubscription(MarketMaker.class).addChangeListener(
            new ObservableSubscriptionChangeListener() {
                @Override
                public void symbolsAdded(Set<?> symbols) {
                    addMarketMakerSub.addAll(symbols);
                }

                @Override
                public void symbolsRemoved(Set<?> symbols) {
                    removeMarketMakerSub.addAll(symbols);
                }
            }
        );
        sub.addEventListener(orders::addAll);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        setSystemProperty(OrderBaseDelegateImpl.DXSCHEME_UNITARY_ORDER_SOURCE, originUnitaryOrderSourceProp);
    }

    private String setSystemProperty(String prop, String value) {
        return value != null ? System.setProperty(prop, value) : System.clearProperty(prop);
    }

    private void runTasks() {
        while (!tasks.isEmpty())
            tasks.poll().run();
    }

    @Test
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
        assertEquals(expectedPrice, in.getPrice(), 0.0);
        assertEquals(expectedSide, in.getOrderSide());
    }

    @Test
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
        // now publish composite quote and check its arrival as two orders (with unitary or separate sources)
        checkSyntheticQuoteOrders(symbol, '\0',
            useUnitarySource ? OrderSource.COMPOSITE : OrderSource.COMPOSITE_BID,
            useUnitarySource ? OrderSource.COMPOSITE : OrderSource.COMPOSITE_ASK);
        // now publish regionals quotes and check their arrival as two orders (with unitary or separate sources)
        for (char c : MarketEventSymbols.DEFAULT_EXCHANGES.toCharArray()) {
            checkSyntheticQuoteOrders(symbol, c,
                useUnitarySource ? OrderSource.REGIONAL : OrderSource.REGIONAL_BID,
                useUnitarySource ? OrderSource.REGIONAL : OrderSource.REGIONAL_ASK);
        }
        // now publish aggregate market maker and check its arrival as two orders (with unitary or separate sources)
        checkAggregateMarketMakerOrders(symbol,
            useUnitarySource ? OrderSource.AGGREGATE : OrderSource.AGGREGATE_BID,
            useUnitarySource ? OrderSource.AGGREGATE : OrderSource.AGGREGATE_ASK);
    }

    private Set<Object> allQuotesSet(String symbol) {
        Set<Object> expectedQuoteSub = new HashSet<>();
        expectedQuoteSub.add(symbol);
        for (char c : MarketEventSymbols.DEFAULT_EXCHANGES.toCharArray()) {
            expectedQuoteSub.add(symbol + "&" + c);
        }
        return expectedQuoteSub;
    }

    private Set<Object> allMarketMakerSet(String symbol) {
        return new HashSet<>(Collections.singleton(symbol));
    }

    private Set<Object> takeSubSet(Queue<?> addSub) {
        Set<Object> subSet = new HashSet<>(addSub);
        addSub.clear();
        return subSet;
    }

    private void checkSyntheticQuoteOrders(String symbol, char exchange, OrderSource... sources) {
        Quote quote = new Quote(MarketEventSymbols.changeExchangeCode(symbol, exchange));
        int bidSize = rnd.nextInt(100000);
        int askSize = rnd.nextInt(100000);
        quote.setBidSize(bidSize);
        quote.setAskSize(askSize);
        endpoint.getPublisher().publishEvents(Collections.singletonList(quote));
        // check
        runTasks();
        checkReceivedOrders(symbol, exchange, sources, bidSize, askSize);
    }

    private void checkAggregateMarketMakerOrders(String symbol, OrderSource... sources) {
        MarketMaker marketMaker = new MarketMaker(symbol);
        int bidSize = rnd.nextInt(100000);
        int askSize = rnd.nextInt(100000);
        marketMaker.setBidSize(bidSize);
        marketMaker.setAskSize(askSize);
        endpoint.getPublisher().publishEvents(Collections.singletonList(marketMaker));
        // check
        runTasks();
        checkReceivedOrders(symbol, '\0', sources, bidSize, askSize);
    }

    private void checkReceivedOrders(String symbol, char exchange, OrderSource[] sources, int bidSize, int askSize) {
        for (OrderSource source : sources) {
            Order order = orders.poll();
            // check order
            assertNotNull(order);
            assertEquals(symbol, order.getEventSymbol());
            assertEquals(exchange, order.getExchangeCode());
            assertEquals(source, order.getSource());
            assertEquals(order.getOrderSide() == Side.BUY ? bidSize : askSize, order.getSize());
        }
        assertEquals(0, orders.size()); // must be found orders for all sources
    }

    // Mix subscription on different sources in a single subscription
    @Test
    public void testMixSources() throws InterruptedException {
        String symbol = "TEST3";
        IndexedEventSubscriptionSymbol<String> subSeparateCompositeBid =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.COMPOSITE_BID);
        IndexedEventSubscriptionSymbol<String> subSeparateCompositeAsk =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.COMPOSITE_ASK);
        IndexedEventSubscriptionSymbol<String> subUnitaryComposite =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.COMPOSITE);
        IndexedEventSubscriptionSymbol<String> subSeparateAggregateBid =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.AGGREGATE_BID);
        IndexedEventSubscriptionSymbol<String> subSeparateAggregateAsk =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.AGGREGATE_ASK);
        IndexedEventSubscriptionSymbol<String> subUnitaryAggregate =
            new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.AGGREGATE);
        // add symbols to the subscription one by one to preserve the order of the delegates
        Arrays.asList(subSeparateCompositeBid, subSeparateCompositeAsk, subUnitaryComposite,
            subSeparateAggregateBid, subSeparateAggregateAsk, subUnitaryAggregate).forEach(s -> sub.addSymbols(s));

        // check that quote and market maker subscription appears
        runTasks();
        assertEquals(symbol, addQuoteSub.poll());
        assertEquals(0, addQuoteSub.size());
        assertEquals(0, addOrderSub.size());
        assertEquals(symbol, addMarketMakerSub.poll());
        assertEquals(0, addMarketMakerSub.size());
        assertEquals(0, addOrderSub.size());

        // check that quote event is converted into bid and ask orders (for separate and unitary sources)
        checkSyntheticQuoteOrders(symbol, '\0',
            OrderSource.COMPOSITE_BID, OrderSource.COMPOSITE_ASK, OrderSource.COMPOSITE, OrderSource.COMPOSITE);
        // unsubscribe from composite bid and unitary source
        sub.removeSymbols(subSeparateCompositeBid, subUnitaryComposite);
        // it should not unsubscribe from quote (yet!)
        runTasks();
        assertEquals(0, addQuoteSub.size());
        assertEquals(0, removeQuoteSub.size());
        // should produce only askOrder on event
        checkSyntheticQuoteOrders(symbol, '\0', OrderSource.COMPOSITE_ASK);
        // now unsubscribe from composite ask
        sub.removeSymbols(subSeparateCompositeAsk);
        // it should unsubscribe from quote
        runTasks();
        assertEquals(0, addQuoteSub.size());
        assertEquals(symbol, removeQuoteSub.poll());
        assertEquals(0, removeQuoteSub.size());

        // check that market maker event is converted into bid and ask orders (for separate and unitary sources)
        checkAggregateMarketMakerOrders(symbol,
            OrderSource.AGGREGATE_BID, OrderSource.AGGREGATE_ASK, OrderSource.AGGREGATE, OrderSource.AGGREGATE);
        // unsubscribe from composite bid and unitary source
        sub.removeSymbols(subSeparateAggregateBid, subUnitaryAggregate);
        // it should not unsubscribe from market maker (yet!)
        runTasks();
        assertEquals(0, addMarketMakerSub.size());
        assertEquals(0, removeMarketMakerSub.size());
        // should produce only askOrder on event
        checkAggregateMarketMakerOrders(symbol, OrderSource.AGGREGATE_ASK);
        // now unsubscribe from aggregate ask
        sub.removeSymbols(subSeparateAggregateAsk);
        // it should unsubscribe from market maker
        runTasks();
        assertEquals(0, addMarketMakerSub.size());
        assertEquals(symbol, removeMarketMakerSub.poll());
        assertEquals(0, removeMarketMakerSub.size());
    }

    // Mix subscription on a source with generic subscription on all sources
    @Test
    public void testMixSourceAndGeneric() {
        String symbol = "TEST4";
        OrderSource source = OrderSource.ISE;
        IndexedEventSubscriptionSymbol<String> subIse = new IndexedEventSubscriptionSymbol<>(symbol, source);
        sub.addSymbols(subIse, symbol);
        // check that ISE subscription appears, market maker and quote sub appears, too
        runTasks();
        assertTrue(takeSubSet(addOrderSub).contains(subIse));
        assertEquals(allQuotesSet(symbol), takeSubSet(addQuoteSub));
        assertEquals(allMarketMakerSet(symbol), takeSubSet(addMarketMakerSub));
        // there should be only one copy of event delivered, despite the fact we've subscribed "twice"
        checkEventWithSource(symbol, source);
        // unsubscribe from a generic symbol, subIse shall remain
        sub.removeSymbols(symbol);
        runTasks();
        assertEquals(Collections.emptySet(), takeSubSet(addOrderSub));
        assertEquals(Collections.emptySet(), takeSubSet(addQuoteSub));
        assertEquals(Collections.emptySet(), takeSubSet(addMarketMakerSub));
        assertFalse(takeSubSet(removeOrderSub).contains(subIse));
        assertEquals(allQuotesSet(symbol), takeSubSet(removeQuoteSub));
        assertEquals(allMarketMakerSet(symbol), takeSubSet(removeMarketMakerSub));
        // check that sourced event is still delivered
        checkEventWithSource(symbol, source);
        // unsubscribe completely
        sub.removeSymbols(subIse);
        runTasks();
        assertEquals(Collections.emptySet(), takeSubSet(addOrderSub));
        assertEquals(Collections.emptySet(), takeSubSet(addQuoteSub));
        assertEquals(Collections.emptySet(), takeSubSet(addMarketMakerSub));
        assertEquals(Collections.<Object>singleton(subIse), takeSubSet(removeOrderSub));
    }

    // For unitary sources, the order of delegates first bid and then ask must be preserved
    @Test
    public void testUnitarySourceBidAskOrder() {
        String symbol = "TEST5";
        // mix sub with unitary, separate and individual order
        List<OrderSource> sources = Arrays.asList(OrderSource.COMPOSITE_BID, OrderSource.COMPOSITE_ASK,
            OrderSource.COMPOSITE, OrderSource.AGGREGATE_BID, OrderSource.AGGREGATE_ASK, OrderSource.AGGREGATE,
            OrderSource.NTV);
        for (int i = 0; i < 1000; i++) { // shuffle the sources randomly n-times
            // add symbols to the subscription one by one to preserve the order of the delegates
            sources.forEach(source -> sub.addSymbols(new IndexedEventSubscriptionSymbol<>(symbol, source)));
            int bidSize = rnd.nextInt(100000);
            int askSize = rnd.nextInt(100000);
            MarketMaker marketMaker = new MarketMaker(symbol);
            marketMaker.setBidSize(bidSize);
            marketMaker.setAskSize(askSize);
            Quote quote = new Quote(symbol);
            quote.setBidSize(bidSize);
            quote.setAskSize(askSize);
            Order pubOrder = new Order(symbol);
            pubOrder.setSource(OrderSource.NTV);
            pubOrder.setOrderSide(Side.BUY);
            pubOrder.setSize(bidSize);
            endpoint.getPublisher().publishEvents(Arrays.asList(quote, marketMaker, pubOrder));
            runTasks();
            // check total numbers of orders for all sources + additional bid/ask for a unitary source
            assertEquals(sources.size() + 2, orders.size());
            int compositeOrderCount = 0;
            int aggregateOrderCount = 0;
            Order order;
            while ((order = orders.poll()) != null) {
                OrderSource source = order.getSource();
                // order is only important for unitary sources
                if (source == OrderSource.COMPOSITE || source == OrderSource.AGGREGATE) {
                    assertEquals(symbol, order.getEventSymbol());
                    assertEquals("Bid side is expected first. Current order of sources in subscription: " + sources,
                        Side.BUY, order.getOrderSide());
                    assertEquals(bidSize, order.getSize());
                    order = orders.poll(); // process the next order, which should be the corresponding ask side order
                    assertNotNull(order);
                    assertEquals(symbol, order.getEventSymbol());
                    assertEquals(source, order.getSource());
                    assertEquals("Ask side is expected second. Current order of sources in subscription: " + sources,
                         Side.SELL, order.getOrderSide());
                    assertEquals(askSize, order.getSize());
                    if (source == OrderSource.COMPOSITE)
                        compositeOrderCount += 2; // each pair of COMPOSITE orders (bid/ask) counts as 2
                    if (source == OrderSource.AGGREGATE)
                        aggregateOrderCount += 2; // each pair of AGGREGATE orders (bid/ask) counts as 2
                }
            }
            // ensure that exactly 2 composite orders and 2 aggregate orders are processed
            assertEquals(2, compositeOrderCount);
            assertEquals(2, aggregateOrderCount);
            Collections.shuffle(sources, rnd); // shuffle sources
            sub.clear(); // clear sub for next iteration
        }
    }

    @Test
    public void testUnitarySourceEventFlags() {
        String symbol = "TEST6";
        sub.addSymbols(new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.AGGREGATE));

        // snapshot
        publishMarketMaker(symbol, 2, 1, 2, SNAPSHOT_BEGIN);
        publishMarketMaker(symbol, 1, 3, 4, 0);
        publishMarketMaker(symbol, 0, Double.NaN, Double.NaN, SNAPSHOT_END | REMOVE_EVENT);
        runTasks();
        checkOrder(symbol, 2, Side.BUY, 1, SNAPSHOT_BEGIN | TX_PENDING);
        checkOrder(symbol, 2, Side.SELL, 2, 0);
        checkOrder(symbol, 1, Side.BUY, 3, TX_PENDING);
        checkOrder(symbol, 1, Side.SELL, 4, 0);
        checkOrder(symbol, 0, Side.BUY, Double.NaN, TX_PENDING | REMOVE_EVENT);
        checkOrder(symbol, 0, Side.SELL, Double.NaN, SNAPSHOT_END | REMOVE_EVENT);

        // update with pending
        publishMarketMaker(symbol, 3, 5, 6, TX_PENDING);
        publishMarketMaker(symbol, 4, 7, 8, 0);
        runTasks();
        checkOrder(symbol, 3, Side.BUY, 5, TX_PENDING);
        checkOrder(symbol, 3, Side.SELL, 6, TX_PENDING);
        checkOrder(symbol, 4, Side.BUY, 7, TX_PENDING);
        checkOrder(symbol, 4, Side.SELL, 8, 0);

        // remove
        publishMarketMaker(symbol, 4, Double.NaN, Double.NaN, REMOVE_EVENT);
        runTasks();
        checkOrder(symbol, 4, Side.BUY, Double.NaN, TX_PENDING | REMOVE_EVENT);
        checkOrder(symbol, 4, Side.SELL, Double.NaN, REMOVE_EVENT);

        assertEquals(0, orders.size());
    }

    private void publishMarketMaker(String symbol, int index, double bidSize, double askSize, int eventFlags) {
        MarketMaker event = new MarketMaker(symbol);
        event.setIndex(index);
        event.setBidSize(bidSize);
        event.setAskSize(askSize);
        event.setEventFlags(eventFlags);
        endpoint.getPublisher().publishEvents(Collections.singleton(event));
    }

    private void checkOrder(String symbol, int index, Side side, double size, int eventFlags) {
        Order order = orders.poll();
        assertNotNull(order);
        assertEquals(symbol, order.getEventSymbol());
        assertEquals(index, order.getIndex() & 0xFFFFFFFFL);
        assertEquals(side, order.getOrderSide());
        assertEquals(size, order.getSizeAsDouble(), 0);
        assertEquals(eventFlags, order.getEventFlags());
    }
}
