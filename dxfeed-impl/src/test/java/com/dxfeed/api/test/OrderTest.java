/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.event.market.*;
import junit.framework.TestCase;

public class OrderTest extends TestCase {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<Order> sub;

    private final BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10);
    private final long t0 = System.currentTimeMillis() / 1000 * 1000; // round to seconds
    private final long t1 = t0 - 1000; // round to seconds

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(Order.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

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
        assertEquals(12.34, o1.getPrice());
        assertEquals(10, o1.getSize());
        assertEquals(t0, o1.getTime());
        // ask
        assertEquals(SYMBOL, o2.getEventSymbol());
        assertEquals(Side.SELL, o2.getOrderSide());
        assertEquals(Scope.COMPOSITE, o2.getScope());
        assertEquals('B', o2.getExchangeCode());
        assertEquals(12.35, o2.getPrice());
        assertEquals(11, o2.getSize());
        assertEquals(t1, o2.getTime());
    }

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
        assertEquals(12.34, o1.getPrice());
        assertEquals(10, o1.getSize());
        assertEquals(t0, o1.getTime());
        // ask
        assertEquals(SYMBOL, o2.getEventSymbol());
        assertEquals(Side.SELL, o2.getOrderSide());
        assertEquals(Scope.REGIONAL, o2.getScope());
        assertEquals('Z', o2.getExchangeCode());
        assertEquals(12.35, o2.getPrice());
        assertEquals(11, o2.getSize());
        assertEquals(t1, o2.getTime());
    }
}
