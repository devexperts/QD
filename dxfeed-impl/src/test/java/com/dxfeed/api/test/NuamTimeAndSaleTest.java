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
import com.dxfeed.event.market.NuamTimeAndSale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NuamTimeAndSaleTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<NuamTimeAndSale> sub;

    private final BlockingQueue<NuamTimeAndSale> queue = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(NuamTimeAndSale.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testNuamTimeAndSale() throws InterruptedException {
        NuamTimeAndSale tns = new NuamTimeAndSale(SYMBOL);
        tns.setTime(1000L);
        tns.setPrice(100D);
        tns.setActorId(1);
        tns.setParticipantId(2);
        tns.setOrderId(3L);
        tns.setClientOrderId("ClientOrderId0");
        tns.setTradeId(4L);
        tns.setCustomerAccount("CustomerAccount0");
        tns.setCustomerInfo("CustomerInfo0");

        publisher.publishEvents(Collections.singleton(tns));

        NuamTimeAndSale received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(1000L, received.getTime());
        assertEquals(100.0, received.getPrice(), 0.0);
        assertEquals(1, received.getActorId());
        assertEquals(2, received.getParticipantId());
        assertEquals(3L, received.getOrderId());
        assertEquals("ClientOrderId0", received.getClientOrderId());
        assertEquals(4L, received.getTradeId());
        assertEquals("CustomerAccount0", received.getCustomerAccount());
        assertEquals("CustomerInfo0", received.getCustomerInfo());
        assertEquals(tns.toString(), received.toString());

        tns.setTime(2000L);
        tns.setPrice(200D);
        tns.setActorId(1);
        tns.setParticipantId(2);
        tns.setOrderId(4L);
        tns.setClientOrderId("ClientOrderId1");
        tns.setTradeId(5L);
        tns.setCustomerAccount("CustomerAccount1");
        tns.setCustomerInfo("CustomerInfo1");
        publisher.publishEvents(Collections.singleton(tns));

        received = queue.take();

        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(2000L, received.getTime());
        assertEquals(200.0, received.getPrice(), 0.0);
        assertEquals(1, received.getActorId());
        assertEquals(2, received.getParticipantId());
        assertEquals(4L, received.getOrderId());
        assertEquals("ClientOrderId1", received.getClientOrderId());
        assertEquals(5L, received.getTradeId());
        assertEquals("CustomerAccount1", received.getCustomerAccount());
        assertEquals("CustomerInfo1", received.getCustomerInfo());
        assertEquals(tns.toString(), received.toString());
    }

    @Test
    public void testEmptyNuamTimeAndSale() {
        NuamTimeAndSale tns = new NuamTimeAndSale();

        assertNull(tns.getEventSymbol());
        assertEquals(0, tns.getActorId());
        assertEquals(0, tns.getParticipantId());
        assertEquals(0, tns.getOrderId());
        assertNull(tns.getClientOrderId());
        assertEquals(0, tns.getTradeId());
        assertNull(tns.getCustomerAccount());
        assertNull(tns.getCustomerInfo());
    }
}
