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
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
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

public class OrderEventFlagsTest {
    private static final int PORT = 4455;
    private static final String SYMBOL = "IBM";
    private static final String MARKET_MAKER = "TEST";

    private DXEndpoint publisherEndpoint;
    private DXEndpoint feedEndpoint;

    private final BlockingQueue<Object> subQueue = new ArrayBlockingQueue<>(10);
    private final BlockingQueue<Order> orderQeuue = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        publisherEndpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
        feedEndpoint = DXEndpoint.create(DXEndpoint.Role.FEED);
    }

    @After
    public void tearDown() throws Exception {
        feedEndpoint.close();
        publisherEndpoint.close();
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOrderEventFlags() throws InterruptedException {
        publisherEndpoint.connect(":" + PORT);
        feedEndpoint.connect("localhost:" + PORT);

        IndexedEventSubscriptionSymbol<String> expectedSubSymbol =
            new IndexedEventSubscriptionSymbol<>(SYMBOL, OrderSource.DEFAULT);

        DXFeedSubscription<Order> sub = feedEndpoint.getFeed().createSubscription(Order.class);
        sub.addEventListener(orderQeuue::addAll);
        sub.addSymbols(expectedSubSymbol);

        // Wait until subscription arrives to publisher
        publisherEndpoint.getPublisher().getSubscription(Order.class).addChangeListener(subQueue::addAll);

        // wait until expected symbol sub is received
        while (true) {
            IndexedEventSubscriptionSymbol<String> subSymbol =
                (IndexedEventSubscriptionSymbol<String>) subQueue.poll(10, TimeUnit.SECONDS);
            if (expectedSubSymbol.equals(subSymbol))
                break;
        }

        // Publish remove event empty snapshot
        Order pub1 = new Order(SYMBOL);
        pub1.setOrderSide(Side.BUY);
        pub1.setMarketMaker(MARKET_MAKER);
        pub1.setScope(Scope.ORDER);
        pub1.setEventFlags(IndexedEvent.SNAPSHOT_BEGIN | IndexedEvent.SNAPSHOT_END | IndexedEvent.REMOVE_EVENT);
        publisherEndpoint.getPublisher().publishEvents(Collections.singleton(pub1));

        Order got1 = orderQeuue.poll(10, TimeUnit.SECONDS);
        assertEquals(SYMBOL, got1.getEventSymbol());
        assertEquals(0, got1.getIndex());
        assertEquals(
            IndexedEvent.SNAPSHOT_BEGIN | IndexedEvent.SNAPSHOT_END | IndexedEvent.REMOVE_EVENT, got1.getEventFlags());
        // the other fields will get lost, because of remove event

        // Publish regular order
        Order pub2 = new Order(SYMBOL);
        pub2.setOrderSide(Side.BUY);
        pub2.setMarketMaker(MARKET_MAKER);
        pub2.setScope(Scope.ORDER);
        pub2.setPrice(123.45);
        pub2.setSize(67);
        pub2.setIndex(1);
        publisherEndpoint.getPublisher().publishEvents(Collections.singleton(pub2));

        Order got2 = orderQeuue.poll(10, TimeUnit.SECONDS);
        assertEquals(SYMBOL, got2.getEventSymbol());
        assertEquals(Side.BUY, got2.getOrderSide());
        assertEquals(MARKET_MAKER, got2.getMarketMaker());
        assertEquals(Scope.ORDER, got2.getScope());
        assertEquals(123.45, got2.getPrice(), 0.0);
        assertEquals(67, got2.getSize());
        assertEquals(1, got2.getIndex());
        assertEquals(0, got2.getEventFlags());
    }
}
