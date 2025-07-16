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
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.market.AuctionType;
import com.dxfeed.event.market.ImbalanceSide;
import com.dxfeed.event.market.OrderImbalance;
import com.dxfeed.event.market.OrderSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OrderImbalanceTest {
    private static final String SYMBOL = "TEST";
    private static final OrderSource SOURCE = OrderSource.NUAM;
    private static final long TIME = 1752675383000L;

    private final Queue<OrderImbalance> receivedEvents = new ArrayDeque<>();

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<OrderImbalance> sub;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        endpoint.executor(Runnable::run);
        publisher = endpoint.getPublisher();
        sub = endpoint.getFeed().createSubscription(OrderImbalance.class);
        sub.addEventListener(receivedEvents::addAll);
    }

    @After
    public void tearDown() {
        sub.close();
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testOrderImbalanceWithSource() {
        sub.addSymbols(new IndexedEventSubscriptionSymbol<>(SYMBOL, SOURCE));

        OrderImbalance imbalance = createOrderImbalance();
        imbalance.setSource(SOURCE);
        publisher.publishEvents(Collections.singleton(imbalance));
        assertEvent(imbalance, receivedEvents.poll());

        imbalance.setSource(OrderSource.DEFAULT);
        publisher.publishEvents(Collections.singleton(imbalance));
        assertTrue(receivedEvents.isEmpty());
    }

    @Test
    public void testOrderImbalanceWithoutSource() {
        sub.addSymbols(SYMBOL);

        OrderImbalance imbalance = createOrderImbalance();
        imbalance.setSource(SOURCE);
        publisher.publishEvents(Collections.singleton(imbalance));
        assertEvent(imbalance, receivedEvents.poll());

        imbalance.setSource(OrderSource.DEFAULT);
        publisher.publishEvents(Collections.singleton(imbalance));
        assertEvent(imbalance, receivedEvents.poll());
    }

    private OrderImbalance createOrderImbalance() {
        OrderImbalance imbalance = new OrderImbalance(SYMBOL);
        imbalance.setTime(TIME);
        imbalance.setSequence(2);
        imbalance.setRefPrice(1.2);
        imbalance.setPairedSize(3.4);
        imbalance.setImbalanceSize(5.6);
        imbalance.setNearPrice(7.8);
        imbalance.setFarPrice(9.0);
        imbalance.setImbalanceSide(ImbalanceSide.BUY);
        imbalance.setAuctionType(AuctionType.OTHER);
        return imbalance;
    }

    private void assertEvent(OrderImbalance expected, OrderImbalance actual) {
        assertNotNull(actual);
        assertEquals(expected.getEventSymbol(), actual.getEventSymbol());
        assertEquals(expected.getSource(), actual.getSource());
        assertEquals(TIME, actual.getTime());
        assertEquals(expected.getSequence(), actual.getSequence());
        assertEquals(expected.getPairedSize(), actual.getPairedSize(), 0.0);
        assertEquals(expected.getImbalanceSize(), actual.getImbalanceSize(), 0.0);
        assertEquals(expected.getNearPrice(), actual.getNearPrice(), 0.0);
        assertEquals(expected.getFarPrice(), actual.getFarPrice(), 0.0);
        assertEquals(expected.getRefPrice(), actual.getRefPrice(), 0.0);
        assertEquals(expected.getImbalanceSide(), actual.getImbalanceSide());
        assertEquals(expected.getAuctionType(), actual.getAuctionType());
    }
}
