/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
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
import com.dxfeed.event.custom.NuamOrder;
import com.dxfeed.event.custom.NuamOrderType;
import com.dxfeed.event.custom.NuamTimeInForceType;
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

public class NuamOrderTest {
    private static final String SYMBOL = "TEST";
    private static final String WEIRD_EXCHANGE_INFO_1 = "weird\2\1\0";
    private static final String WEIRD_EXCHANGE_INFO_2 = "\0\0";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<NuamOrder> sub;

    private final BlockingQueue<NuamOrder> queue = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(NuamOrder.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testNuamOrder() throws InterruptedException {
        NuamOrder order = new NuamOrder(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        order.setActorId(1);
        order.setParticipantId(2);
        order.setSubmitterId(3);
        order.setOnBehalfOfSubmitterId(4);
        order.setClientOrderId("ClientOrderId");
        order.setCustomerAccount("CustomerAccount");
        order.setCustomerInfo("CustomerInfo");
        order.setExchangeInfo(WEIRD_EXCHANGE_INFO_1);
        order.setTimeInForce(NuamTimeInForceType.NUMBER_OF_DAYS);
        order.setTimeInForceData(5);
        order.setTriggerOrderBookId(13);
        order.setTriggerPrice(155D);
        order.setTriggerSessionType(1);
        order.setOrderQuantity(100);
        order.setDisplayQuantity(20);
        order.setRefreshQuantity(40);
        order.setLeavesQuantity(50);
        order.setMatchedQuantity(10);
        order.setOrderType(NuamOrderType.IMBALANCE);

        publisher.publishEvents(Collections.singleton(order));

        NuamOrder received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(Side.BUY, received.getOrderSide());
        assertEquals(Scope.ORDER, received.getScope());
        assertEquals(10.0, received.getPrice(), 0.0);
        assertEquals(1, received.getSize());

        assertEquals(1, received.getActorId());
        assertEquals(2, received.getParticipantId());
        assertEquals(3, received.getSubmitterId());
        assertEquals(4, received.getOnBehalfOfSubmitterId());
        assertEquals("ClientOrderId", received.getClientOrderId());
        assertEquals("CustomerAccount", received.getCustomerAccount());
        assertEquals("CustomerInfo", received.getCustomerInfo());

        assertEquals(WEIRD_EXCHANGE_INFO_1, received.getExchangeInfo());
        assertEquals(NuamTimeInForceType.NUMBER_OF_DAYS, received.getTimeInForce());
        assertEquals(5, received.getTimeInForceData());
        assertEquals(13, received.getTriggerOrderBookId());
        assertEquals(155D, received.getTriggerPrice(), 0.0);
        assertEquals(1, received.getTriggerSessionType());
        assertEquals(100, received.getOrderQuantity(), 0.0);
        assertEquals(20, received.getDisplayQuantity(), 0.0);
        assertEquals(40, received.getRefreshQuantity(), 0.0);
        assertEquals(50, received.getLeavesQuantity(), 0.0);
        assertEquals(10, received.getMatchedQuantity(), 0.0);
        assertEquals(NuamOrderType.IMBALANCE, received.getOrderType());
        assertEquals(order.toString(), received.toString());

        order.setOrderType(NuamOrderType.MARKET);
        order.setTimeInForce(NuamTimeInForceType.REST_OF_DAY);
        order.setExchangeInfo(WEIRD_EXCHANGE_INFO_2);
        order.setTimeInForceData(0);
        publisher.publishEvents(Collections.singleton(order));

        received = queue.take();
        assertEquals(NuamOrderType.MARKET, received.getOrderType());
        assertEquals(NuamTimeInForceType.REST_OF_DAY, received.getTimeInForce());
        assertEquals(0, received.getTimeInForceData());
        assertEquals(WEIRD_EXCHANGE_INFO_2, received.getExchangeInfo());
        assertEquals(order.toString(), received.toString());
    }

    @Test
    public void testOrderNotReceived() throws InterruptedException {
        Order order = new Order(SYMBOL);
        order.setOrderSide(Side.BUY);
        order.setScope(Scope.ORDER);
        order.setPrice(10.0);
        order.setSize(1);
        order.setIndex(1);
        publisher.publishEvents(Collections.singleton(order));

        Order received = queue.poll(1, TimeUnit.SECONDS);
        assertNull(received);
    }

    @Test
    public void testEmptyNuamOrder() {
        NuamOrder order = new NuamOrder();
        assertNull(order.getEventSymbol());
        assertNull(order.getMarketMaker());

        assertEquals(0, order.getActorId());
        assertEquals(0, order.getParticipantId());
        assertEquals(0, order.getSubmitterId());
        assertEquals(0, order.getOnBehalfOfSubmitterId());
        assertNull(order.getClientOrderId());
        assertNull(order.getCustomerAccount());
        assertNull(order.getCustomerInfo());
        assertNull(order.getExchangeInfo());
        assertEquals(NuamTimeInForceType.UNDEFINED, order.getTimeInForce());
        assertEquals(0, order.getTimeInForceData());
        assertEquals(0, order.getTriggerOrderBookId());

        assertEquals(Double.NaN, order.getTriggerPrice(), 0.0);
        assertEquals(0, order.getTriggerSessionType());
        assertEquals(Double.NaN, order.getOrderQuantity(), 0.0);
        assertEquals(Double.NaN, order.getDisplayQuantity(), 0.0);
        assertEquals(Double.NaN, order.getRefreshQuantity(), 0.0);
        assertEquals(Double.NaN, order.getLeavesQuantity(), 0.0);
        assertEquals(Double.NaN, order.getMatchedQuantity(), 0.0);
        assertEquals(NuamOrderType.UNDEFINED, order.getOrderType());
    }
}
