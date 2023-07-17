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
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.OtcMarketsPriceType;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OtcMarketsOrderTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<OtcMarketsOrder> sub;

    private final BlockingQueue<OtcMarketsOrder> queue = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(OtcMarketsOrder.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testOtcMarketsOrder() throws InterruptedException {
        OtcMarketsOrder order = new OtcMarketsOrder(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setMarketMaker("NSDQ");
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        order.setMarketMaker("TEST");
        order.setQuoteAccessPayment(-30);
        order.setOpen(true);
        order.setUnsolicited(true);
        order.setOtcMarketsPriceType(OtcMarketsPriceType.ACTUAL);
        order.setSaturated(true);
        order.setAutoExecution(true);
        order.setNmsConditional(true);
        publisher.publishEvents(Collections.singleton(order));

        OtcMarketsOrder received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(Side.BUY, received.getOrderSide());
        assertEquals(Scope.ORDER, received.getScope());
        assertEquals(10.0, received.getPrice(), 0.0);
        assertEquals(1, received.getSize());
        assertEquals("TEST", received.getMarketMaker());
        assertEquals(-30, received.getQuoteAccessPayment());
        assertTrue(received.isOpen());
        assertTrue(received.isUnsolicited());
        assertEquals(OtcMarketsPriceType.ACTUAL, received.getOtcMarketsPriceType());
        assertTrue(received.isSaturated());
        assertTrue(received.isAutoExecution());
        assertTrue(received.isNmsConditional());

        order.setQuoteAccessPayment(25);
        order.setOpen(true);
        order.setUnsolicited(false);
        order.setOtcMarketsPriceType(OtcMarketsPriceType.WANTED);
        order.setSaturated(true);
        order.setAutoExecution(false);
        order.setNmsConditional(true);
        publisher.publishEvents(Collections.singleton(order));

        received = queue.take();
        assertEquals(25, received.getQuoteAccessPayment());
        assertTrue(received.isOpen());
        assertFalse(received.isUnsolicited());
        assertEquals(OtcMarketsPriceType.WANTED, received.getOtcMarketsPriceType());
        assertTrue(received.isSaturated());
        assertFalse(received.isAutoExecution());
        assertTrue(received.isNmsConditional());
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

    @Test
    public void testEmptyOtcMarketsOrder() {
        OtcMarketsOrder order = new OtcMarketsOrder();
        assertNull(order.getEventSymbol());
        assertNull(order.getMarketMaker());
        assertEquals(0, order.getQuoteAccessPayment());
        assertFalse(order.isOpen());
        assertFalse(order.isUnsolicited());
        assertEquals(OtcMarketsPriceType.UNPRICED, order.getOtcMarketsPriceType());
        assertFalse(order.isSaturated());
        assertFalse(order.isAutoExecution());
        assertFalse(order.isNmsConditional());
    }
}
