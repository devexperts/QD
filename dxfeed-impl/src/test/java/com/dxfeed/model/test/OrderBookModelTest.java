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
package com.dxfeed.model.test;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import com.dxfeed.model.market.OrderBookModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OrderBookModelTest {

    public static final char Q = 'Q';
    public static final char Z = 'Z';
    public static final String MMID = "NYSE";
    public static final String MMID2 = "NSDQ";

    private DXPublisher publisher;
    private OrderBookModel model;
    private List<Order> buys;
    private List<Order> sells;
    private int buyQueued;
    private int sellQueued;

    private String symbol = "IBM";

    @Before
    public void setUp() {
        // single threaded -- execute in place
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB).executor(Runnable::run);

        publisher = endpoint.getPublisher();
        model = new OrderBookModel();
        model.setSymbol(symbol);
        model.attach(endpoint.getFeed());
        buys = model.getBuyOrders();
        model.getBuyOrders().addListener(change -> buyQueued++);
        sells = model.getSellOrders();
        model.getSellOrders().addListener(change -> sellQueued++);
    }

    @After
    public void tearDown() throws Exception {
        model.close();
    }

    @Test
    public void testLotSize() throws Exception {
        model.setLotSize(100);

        publisher.publishEvents(Collections.singletonList(compositeBuy(1)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(1); // composites show even with size 0
        assertEquals(1, buys.size());
        assertEquals(100, buys.get(0).getSize());

        publisher.publishEvents(Collections.singletonList(regionalBuy(Q, 2)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(1, buys.size());
        assertEquals(200, buys.get(0).getSize());

        publisher.publishEvents(Collections.singletonList(aggregateBuy(2, 3, Q, MMID)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(1, buys.size());
        assertEquals(300, buys.get(0).getSize());

        publisher.publishEvents(Collections.singletonList(orderBuy(3, 400, Q, MMID)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(1, buys.size());
        assertEquals(400, buys.get(0).getSize());
    }

    @Test
    public void testChangeLotSize() throws Exception {
        publisher.publishEvents(Collections.singletonList(compositeBuy(1)));
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(model.getLotSize(), buys.get(0).getSize());

        model.setLotSize(100);
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(100, buys.get(0).getSize());

        model.setLotSize(1);
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(1, buys.get(0).getSize());
    }

    @Test
    public void testChangeLotSize2() throws Exception {
        // See [QD-838] dxFeed API: OrderBookModel incorrectly processes change of lot size
        publisher.publishEvents(Arrays.asList(aggregateBuy(1, 1, Q, MMID), aggregateBuy(2, 0, Q, MMID)));
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(model.getLotSize(), buys.get(0).getSize());

        model.setLotSize(100);
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(100, buys.get(0).getSize());

        model.setLotSize(1);
        assertNBuyChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(1, buys.get(0).getSize());
    }

    @Test
    public void testSoleZeroSizeCompositeQuote() throws Exception {
        publisher.publishEvents(Collections.singletonList(compositeBuy(1)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(1);

        publisher.publishEvents(Collections.singletonList(regionalBuy(Q, 1)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);

        publisher.publishEvents(Collections.singletonList(regionalBuy(Q, 0)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);

        publisher.publishEvents(Collections.singletonList(compositeBuy(0)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(1);

        assertEquals(1, buys.size());
        assertEquals(0, buys.get(0).getSize());
        assertEquals(0.0, buys.get(0).getPrice(), 0.0);
    }

    @Test
    public void testChangeSymbol() {
        // publish 100 orders for one symbol
        int n = 100;
        for (int i = 1; i <= n; i++) {
            publisher.publishEvents(Collections.singletonList(orderBuy(i, i, Q, MMID)));
        }
        assertNBuyChangesQueued(n);
        // change symbol
        symbol = "OTHER";
        model.setSymbol(symbol);
        assertNBuyChangesQueued(1);
        // ensure model is empty
        assertEquals(0, buys.size());
        // publish 100 order for another symbol
        for (int i = 1; i <= n; i++) {
            publisher.publishEvents(Collections.singletonList(orderBuy(i, i, Q, MMID)));
            assertNBuyChangesQueued(1);
        }
        assertNSellChangesQueued(0);
    }

    @Test
    public void testOrderBookModelIgnoresAnalyticOrder() {
        publisher.publishEvents(Collections.singletonList(analyticOrderBuy(4, 500, Q, MMID)));
        assertNBuyChangesQueued(0);
        assertNSellChangesQueued(0);
    }

    @Test
    public void testOrderBookModelIgnoresOtcMarketsOrder() {
        publisher.publishEvents(Collections.singletonList(otcMarketOrderBuy(4, 500, Q, MMID)));
        assertNBuyChangesQueued(0);
        assertNSellChangesQueued(0);
    }

    private void assertNBuyChangesQueued(int n) {
        assertEquals(n, buyQueued);
        buyQueued = 0;
    }

    private void assertNSellChangesQueued(int n) {
        assertEquals(n, sellQueued);
        sellQueued = 0;
    }

    // test a mix of composites, regionals, and orders
    @Test
    public void testMix() {
        // post composite
        publisher.publishEvents(Collections.singletonList(compositeBuy(1)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(1);
        assertEquals(1, buys.size());
        assertEquals(1, sells.size());
        assertOrder(buys, 0, Scope.COMPOSITE, 1);
        assertOrder(sells, 0, Scope.COMPOSITE, 0);

        // post regionalQ
        publisher.publishEvents(Collections.singletonList(regionalBuy(Q, 2)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(1, buys.size());
        assertOrder(buys, 0, Scope.REGIONAL, 2);

        // post regionalZ
        publisher.publishEvents(Collections.singletonList(regionalBuy(Z, 3)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(2, buys.size());
        assertOrder(buys, 0, Scope.REGIONAL, 3);
        assertOrder(buys, 1, Scope.REGIONAL, 2);

        // post aggregate to Q exchange (regional from Q exchange shall disappear, but Z one remain)
        publisher.publishEvents(Collections.singletonList(aggregateBuy(4, 4, Q, MMID)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(2, buys.size());
        assertOrder(buys, 0, Scope.AGGREGATE, 4);
        assertOrder(buys, 1, Scope.REGIONAL, 3);

        // post one more aggregate to Q exchange (diferent mmid)
        publisher.publishEvents(Collections.singletonList(aggregateBuy(5, 5, Q, MMID2)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(3, buys.size());
        assertOrder(buys, 0, Scope.AGGREGATE, 5);
        assertOrder(buys, 1, Scope.AGGREGATE, 4);
        assertOrder(buys, 2, Scope.REGIONAL, 3);

        // post order to Q exchange (a corresponding aggregate should disappear)
        publisher.publishEvents(Collections.singletonList(orderBuy(6, 6, Q, MMID)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(3, buys.size());
        assertOrder(buys, 0, Scope.ORDER, 6);
        assertOrder(buys, 1, Scope.AGGREGATE, 5);
        assertOrder(buys, 2, Scope.REGIONAL, 3);

        // post order to Q exchange for different MMID (a corresponding aggregate should disappear)
        publisher.publishEvents(Collections.singletonList(orderBuy(7, 7, Q, MMID2)));
        assertNBuyChangesQueued(1);
        assertNSellChangesQueued(0);
        assertEquals(3, buys.size());
        assertOrder(buys, 0, Scope.ORDER, 7);
        assertOrder(buys, 1, Scope.ORDER, 6);
        assertOrder(buys, 2, Scope.REGIONAL, 3);
    }

    private void assertOrder(List<Order> orders, int index, Scope scope, int value) {
        assertEquals(scope, orders.get(index).getScope());
        assertEquals(value, orders.get(index).getSize());
    }

    // Test correct replacing of buy/sell orders
    // It also tests removing everything with an "empty snapshot" message
    @Test
    public void testStressBuySellOrders() {
        Random rnd = new Random(1);
        int bookSize = 100;
        Order[] book = new Order[bookSize];
        int expectedBuy = 0;
        int expectedSell = 0;
        for (int i = 0; i < 10_000; i++) {
            int index = rnd.nextInt(bookSize);
            // Note: every 1/10 order will have size == 0 and will "remove"
            Order order = createOrder(Scope.ORDER,
                rnd.nextBoolean() ? Side.BUY : Side.SELL, index, rnd.nextInt(10), (char) 0, null);
            Order old = book[index];
            book[index] = order;
            int deltaBuy = oneIfBuy(order) - oneIfBuy(old);
            int deltaSell = oneIfSell(order) - oneIfSell(old);
            expectedBuy += deltaBuy;
            expectedSell += deltaSell;
            publisher.publishEvents(Collections.singletonList(order));
            switch (order.getOrderSide()) {
                case BUY:
                    assertNBuyChangesQueued(
                        deltaBuy != 0 || !same(order, old) && old.getOrderSide() == Side.BUY ? 1 : 0);
                    assertNSellChangesQueued(oneIfSell(old));
                    break;
                case SELL:
                    assertNSellChangesQueued(
                        deltaSell != 0 || !same(order, old) && old.getOrderSide() == Side.SELL ? 1 : 0);
                    assertNBuyChangesQueued(oneIfBuy(old));
                    break;
                default:
                    fail();
            }
            assertEquals(expectedBuy, buys.size());
            assertEquals(expectedSell, sells.size());
        }
        // now send "empty snapshot"
        Order order = createOrder(Scope.ORDER, Side.UNDEFINED, 0, 0, (char) 0, null);
        order.setEventFlags(IndexedEvent.SNAPSHOT_BEGIN | IndexedEvent.SNAPSHOT_END | Order.REMOVE_EVENT);
        publisher.publishEvents(Collections.singletonList(order));
        assertNBuyChangesQueued(expectedBuy > 0 ? 1 : 0);
        assertNSellChangesQueued(expectedSell > 0 ? 1 : 0);
        assertEquals(0, buys.size());
        assertEquals(0, sells.size());
    }

    // Test different sources (all publishable ones in scheme)
    // It also tests removing everything via individual "REMOVE_EVENT" messages on each index
    @Test
    public void testStressSources() {
        Random rnd = new Random(1);
        int bookSize = 100;
        // book per source
        Map<OrderSource, Order[]> books = new HashMap<>();
        int expectedBuy = 0;
        int expectedSell = 0;
        List<OrderSource> sources = OrderSource.publishable(Order.class);
        for (int i = 0; i < 10_000; i++) {
            // Note: every 1/10 order will have size == 0 and will "remove"
            int index = rnd.nextInt(bookSize);
            Order order = createOrder(Scope.ORDER,
                rnd.nextBoolean() ? Side.BUY : Side.SELL, index, rnd.nextInt(10), (char) 0, null);
            OrderSource source = sources.get(rnd.nextInt(sources.size()));
            order.setSource(source);
            Order[] book = books.computeIfAbsent(source, k -> new Order[bookSize]);
            Order old = book[index];
            book[index] = order;
            int deltaBuy = oneIfBuy(order) - oneIfBuy(old);
            int deltaSell = oneIfSell(order) - oneIfSell(old);
            expectedBuy += deltaBuy;
            expectedSell += deltaSell;
            publisher.publishEvents(Collections.singletonList(order));
            switch (order.getOrderSide()) {
                case BUY:
                    assertNBuyChangesQueued(
                        deltaBuy != 0 || !same(order, old) && old.getOrderSide() == Side.BUY ? 1 : 0);
                    assertNSellChangesQueued(oneIfSell(old));
                    break;
                case SELL:
                    assertNSellChangesQueued(
                        deltaSell != 0 || !same(order, old) && old.getOrderSide() == Side.SELL ? 1 : 0);
                    assertNBuyChangesQueued(oneIfBuy(old));
                    break;
                default:
                    fail();
            }
            assertEquals(expectedBuy, buys.size());
            assertEquals(expectedSell, sells.size());
        }
        // Now remove orders from all books in random order
        List<Order> orders = new ArrayList<>();
        for (Order[] bookOrders : books.values()) {
            for (Order bookOrder : bookOrders) {
                if (bookOrder != null)
                    orders.add(bookOrder);
            }
        }
        Collections.shuffle(orders, rnd);
        for (Order order : orders) {
            Order remove = createOrder(Scope.ORDER, Side.UNDEFINED, order.getIndex(), 0, (char) 0, null);
            remove.setEventFlags(IndexedEvent.REMOVE_EVENT);
            publisher.publishEvents(Collections.singletonList(remove));
            assertNBuyChangesQueued(oneIfBuy(order));
            assertNSellChangesQueued(oneIfSell(order));
        }
        assertEquals(0, buys.size());
        assertEquals(0, sells.size());
    }

    // Test a mix of composite, regional, aggregate, and order updates (BUY side only)
    // It does not actually check what's going one, but looks for NPEs in tree code
    @Test
    public void testStressMix() {
        Random rnd = new Random(1);
        int bookSize = 100;
        for (int i = 0; i < 10_000; i++) {
            // Note: every 1/10 order will have size == 0 and will "remove"
            int value = rnd.nextInt(10);
            char exchange = MarketEventSymbols.SUPPORTED_EXCHANGES.charAt(
                rnd.nextInt(MarketEventSymbols.SUPPORTED_EXCHANGES.length()));
            int index = rnd.nextInt(bookSize);
            String mmid = rnd.nextBoolean() ? MMID : MMID2;
            switch (rnd.nextInt(4)) {
                case 0:
                    publisher.publishEvents(Collections.singletonList(compositeBuy(value)));
                    break;
                case 1:
                    publisher.publishEvents(Collections.singletonList(regionalBuy(exchange, value)));
                    break;
                case 2:
                    publisher.publishEvents(Collections.singletonList(aggregateBuy(index, value, exchange, mmid)));
                    break;
                case 3:
                    publisher.publishEvents(Collections.singletonList(orderBuy(index, value, exchange, mmid)));
            }
        }
    }

    // Utility methods

    protected Quote compositeBuy(int value) {
        Quote quote = new Quote();
        quote.setBidPrice(value * 10.0);
        quote.setBidSize(value);
        quote.setEventSymbol(symbol);
        return quote;
    }

    protected Quote regionalBuy(char exchange, int value) {
        Quote quote = new Quote();
        quote.setBidPrice(value * 10.0);
        quote.setBidSize(value);
        quote.setEventSymbol(MarketEventSymbols.changeExchangeCode(symbol, exchange));
        return quote;
    }

    protected Order aggregateBuy(int index, int value, char exchange, String mmid) {
        return createOrder(Scope.AGGREGATE, Side.BUY, index, value, exchange, mmid);
    }

    protected Order orderBuy(int index, int value, char exchange, String mmid) {
        return createOrder(Scope.ORDER, Side.BUY, index, value, exchange, mmid);
    }

    protected Order createOrder(Scope scope, Side side, long index, int value, char exchange, String mmid) {
        Order order = new Order();
        order.setScope(scope);
        order.setIndex(index);
        order.setOrderSide(side);
        order.setPrice(value * 10.0);
        order.setSize(value);
        order.setExchangeCode(exchange);
        order.setMarketMaker(mmid);
        order.setEventSymbol(symbol);
        return order;
    }

    private AnalyticOrder analyticOrderBuy(int index, int value, char exchange, String mmid) {
        return createAnalyticOrder(Scope.ORDER, Side.BUY, index, value, exchange, mmid);
    }

    private AnalyticOrder createAnalyticOrder(Scope scope, Side side, long index, int value, char exchange,
        String mmid)
    {
        AnalyticOrder analyticOrder = new AnalyticOrder();
        analyticOrder.setScope(scope);
        analyticOrder.setIndex(index);
        analyticOrder.setOrderSide(side);
        analyticOrder.setPrice(value * 10.0);
        analyticOrder.setSize(value);
        analyticOrder.setExchangeCode(exchange);
        analyticOrder.setMarketMaker(mmid);
        analyticOrder.setEventSymbol(symbol);
        analyticOrder.setIcebergHiddenSize(value * 1.0);
        analyticOrder.setIcebergPeakSize(value * 1.0);
        return analyticOrder;
    }

    private OtcMarketsOrder otcMarketOrderBuy(int index, int value, char exchange, String mmid) {
        return createOtcMarketsOrder(Scope.ORDER, Side.BUY, index, value, exchange, mmid);
    }

    private OtcMarketsOrder createOtcMarketsOrder(Scope scope, Side side, long index, int value, char exchange,
        String mmid)
    {
        OtcMarketsOrder otcMarketsOrder = new OtcMarketsOrder();
        otcMarketsOrder.setScope(scope);
        otcMarketsOrder.setIndex(index);
        otcMarketsOrder.setOrderSide(side);
        otcMarketsOrder.setPrice(value * 10.0);
        otcMarketsOrder.setSize(value);
        otcMarketsOrder.setExchangeCode(exchange);
        otcMarketsOrder.setMarketMaker(mmid);
        otcMarketsOrder.setEventSymbol(symbol);
        otcMarketsOrder.setQuoteAccessPayment(value);
        return otcMarketsOrder;
    }

    private boolean same(Order order, Order old) {
        return old == null || order.getSize() == 0 ?
            order.getSize() == 0 : // order with zero size is the same as null (missing)
            // check just relevant attrs
            order.getScope() == old.getScope() &&
            order.getOrderSide() == old.getOrderSide() &&
            order.getIndex() == old.getIndex() &&
            order.getSize() == old.getSize() &&
            order.getSource() == old.getSource();
    }

    private int oneIfBuy(Order order) {
        return order != null && order.getOrderSide() == Side.BUY && order.getSize() != 0 ? 1 : 0;
    }

    private int oneIfSell(Order order) {
        return order != null && order.getOrderSide() == Side.SELL && order.getSize() != 0 ? 1 : 0;
    }
}
