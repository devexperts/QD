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
import com.dxfeed.event.market.Order;
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
import static org.junit.Assert.assertTrue;

public class AnalyticOrderTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<AnalyticOrder> sub;

    private final BlockingQueue<AnalyticOrder> queue = new ArrayBlockingQueue<>(10);
    private final long t0 = System.currentTimeMillis() / 1000 * 1000; // round to seconds
    private final long t1 = t0 - 1000; // round to seconds

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(AnalyticOrder.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testAnalyticOrder() throws InterruptedException {
        AnalyticOrder order = new AnalyticOrder(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setMarketMaker("NSDQ");
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        order.setIcebergHiddenSize(2.0);
        order.setIcebergPeakSize(3.0);
        order.setIcebergExecutedSize(4.0);
        publisher.publishEvents(Collections.singleton(order));

        AnalyticOrder received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(Side.BUY, received.getOrderSide());
        assertEquals(Scope.ORDER, received.getScope());
        assertEquals(10.0, received.getPrice(), 0.0);
        assertEquals(1, received.getSize());
        // by default the following fields are turned off in the scheme
        assertTrue(Double.isNaN(received.getIcebergHiddenSize()));
        assertTrue(Double.isNaN(received.getIcebergPeakSize()));
        assertTrue(Double.isNaN(received.getIcebergExecutedSize()));
    }

    @Test
    public void testOrderNotReceived() throws InterruptedException {
        Order order = new Order(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setMarketMaker("NSDQ");
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        publisher.publishEvents(Collections.singleton(order));

        Order received = queue.poll(2, TimeUnit.SECONDS);
        assertNull(received);
    }
}
