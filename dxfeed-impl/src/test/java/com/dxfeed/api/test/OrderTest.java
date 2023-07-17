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
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OrderTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<Order> sub;

    private final BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10);
    private final long t0 = System.currentTimeMillis() / 1000 * 1000; // round to seconds
    private final long t1 = t0 - 1000; // round to seconds

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(Order.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testCompositeOrders() throws InterruptedException {
        // publish composite quote
        Quote composite = new Quote(SYMBOL);
        composite.setBidExchangeCode('A');
        composite.setBidPrice(12.34);
        composite.setBidSize(10);
        composite.setBidTime(t0);
        composite.setAskExchangeCode('B');
        composite.setAskPrice(12.35);
        composite.setAskSize(11);
        composite.setAskTime(t1);
        publisher.publishEvents(Collections.singleton(composite));
        // check that two orders are received
        Order o1 = queue.take();
        Order o2 = queue.take();
        if (o1.getOrderSide() == Side.SELL) {
            Order tmp = o1;
            o1 = o2;
            o2 = tmp;
        }
        // bid
        assertEquals(SYMBOL, o1.getEventSymbol());
        assertEquals(Side.BUY, o1.getOrderSide());
        assertEquals(Scope.COMPOSITE, o1.getScope());
        assertEquals('A', o1.getExchangeCode());
        assertEquals(12.34, o1.getPrice(), 0.0);
        assertEquals(10, o1.getSize());
        assertEquals(t0, o1.getTime());
        // ask
        assertEquals(SYMBOL, o2.getEventSymbol());
        assertEquals(Side.SELL, o2.getOrderSide());
        assertEquals(Scope.COMPOSITE, o2.getScope());
        assertEquals('B', o2.getExchangeCode());
        assertEquals(12.35, o2.getPrice(), 0.0);
        assertEquals(11, o2.getSize());
        assertEquals(t1, o2.getTime());
    }

    @Test
    public void testRegionalOrders() throws InterruptedException {
        // publish composite quote
        Quote composite = new Quote(SYMBOL + "&Z");
        composite.setBidPrice(12.34);
        composite.setBidSize(10);
        composite.setBidTime(t0);
        composite.setAskPrice(12.35);
        composite.setAskSize(11);
        composite.setAskTime(t1);
        publisher.publishEvents(Collections.singleton(composite));
        // check that two orders are received
        Order o1 = queue.take();
        Order o2 = queue.take();
        if (o1.getOrderSide() == Side.SELL) {
            Order tmp = o1;
            o1 = o2;
            o2 = tmp;
        }
        // bid
        assertEquals(SYMBOL, o1.getEventSymbol());
        assertEquals(Side.BUY, o1.getOrderSide());
        assertEquals(Scope.REGIONAL, o1.getScope());
        assertEquals('Z', o1.getExchangeCode());
        assertEquals(12.34, o1.getPrice(), 0.0);
        assertEquals(10, o1.getSize());
        assertEquals(t0, o1.getTime());
        // ask
        assertEquals(SYMBOL, o2.getEventSymbol());
        assertEquals(Side.SELL, o2.getOrderSide());
        assertEquals(Scope.REGIONAL, o2.getScope());
        assertEquals('Z', o2.getExchangeCode());
        assertEquals(12.35, o2.getPrice(), 0.0);
        assertEquals(11, o2.getSize());
        assertEquals(t1, o2.getTime());
    }

    @Test
    public void testOrder() throws InterruptedException {
        Order order = new Order(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setMarketMaker("NSDQ");
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        publisher.publishEvents(Collections.singleton(order));

        Order received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(Side.BUY, received.getOrderSide());
        assertEquals(Scope.ORDER, received.getScope());
        assertEquals(10.0, received.getPrice(), 0.0);
        assertEquals(1, received.getSize());
    }

    @Test
    public void testAnalyticOrderNotReceived() throws InterruptedException {
        AnalyticOrder analyticOrder = new AnalyticOrder(SYMBOL);
        analyticOrder.setOrderSide(Side.BUY);
        analyticOrder.setMarketMaker("NSDQ");
        analyticOrder.setScope(Scope.ORDER);
        analyticOrder.setPrice(10.0);
        analyticOrder.setSize(1);
        analyticOrder.setIndex(1);
        publisher.publishEvents(Collections.singleton(analyticOrder));

        Order received = queue.poll(2, TimeUnit.SECONDS);
        assertNull(received);
    }

    @Test
    public void testOtcMarketsOrderNotReceived() throws InterruptedException {
        OtcMarketsOrder otcMarketsOrder = new OtcMarketsOrder(SYMBOL);
        otcMarketsOrder.setOrderSide(Side.BUY);
        otcMarketsOrder.setMarketMaker("NSDQ");
        otcMarketsOrder.setScope(Scope.ORDER);
        otcMarketsOrder.setPrice(10.0);
        otcMarketsOrder.setSize(1);
        otcMarketsOrder.setIndex(1);
        publisher.publishEvents(Collections.singleton(otcMarketsOrder));

        Order received = queue.poll(2, TimeUnit.SECONDS);
        assertNull(received);
    }
}
