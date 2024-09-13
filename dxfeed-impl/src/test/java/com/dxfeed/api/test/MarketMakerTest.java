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
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.MarketMaker;
import com.dxfeed.event.market.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MarketMakerTest {
    private static final String SYMBOL = "TEST";

    private final BlockingQueue<MarketMaker> marketMakerQueue = new ArrayBlockingQueue<>(10);
    private final BlockingQueue<Order> orderQueue = new ArrayBlockingQueue<>(10);

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<IndexedEvent<?>> sub;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        endpoint.executor(Runnable::run);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(MarketMaker.class, Order.class);
        sub.addEventListener(events -> {
            for (IndexedEvent<?> event : events) {
                if (event instanceof MarketMaker) {
                    marketMakerQueue.add((MarketMaker) event);
                } else if (event instanceof Order) {
                    orderQueue.add((Order) event);
                } else {
                    throw new IllegalArgumentException("Unexpected event type: " + event.getClass().getName());
                }
            }
        });
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testMarketMaker() throws InterruptedException {
        MarketMaker marketMaker = new MarketMaker(SYMBOL);
        marketMaker.setExchangeCode('Q');
        marketMaker.setMarketMaker("NSDQ");
        marketMaker.setBidSize(1.0);
        marketMaker.setBidPrice(2.0);
        marketMaker.setAskSize(3.0);
        marketMaker.setAskPrice(4.0);
        publisher.publishEvents(Collections.singleton(marketMaker));

        MarketMaker receivedMarketMaker = marketMakerQueue.take(); // received market maker
        assertEquals(SYMBOL, receivedMarketMaker.getEventSymbol());
        assertEquals('Q', receivedMarketMaker.getExchangeCode());
        assertEquals("NSDQ", receivedMarketMaker.getMarketMaker());
        assertEquals(1.0, receivedMarketMaker.getBidSize(), 0.0);
        assertEquals(2.0, receivedMarketMaker.getBidPrice(), 0.0);
        assertEquals(3.0, receivedMarketMaker.getAskSize(), 0.0);
        assertEquals(4.0, receivedMarketMaker.getAskPrice(), 0.0);

        Order receivedBidOrder = orderQueue.take(); // received bid order created from a market maker
        assertEquals(SYMBOL, receivedBidOrder.getEventSymbol());
        assertEquals('Q', receivedBidOrder.getExchangeCode());
        assertEquals("NSDQ", receivedBidOrder.getMarketMaker());
        assertEquals(1.0, receivedBidOrder.getSize(), 0.0);
        assertEquals(2.0, receivedBidOrder.getPrice(), 0.0);

        Order receivedAskOrder = orderQueue.take(); // received ask order created from a market maker
        assertEquals(SYMBOL, receivedAskOrder.getEventSymbol());
        assertEquals('Q', receivedAskOrder.getExchangeCode());
        assertEquals("NSDQ", receivedAskOrder.getMarketMaker());
        assertEquals(3.0, receivedAskOrder.getSize(), 0.0);
        assertEquals(4.0, receivedAskOrder.getPrice(), 0.0);

        assertEquals(0, marketMakerQueue.size());
        assertEquals(0, orderQueue.size());
    }

    @Test
    public void testEmptyMarketMaker() {
        MarketMaker marketMaker = new MarketMaker();
        assertNull(marketMaker.getEventSymbol());
        assertEquals(IndexedEventSource.DEFAULT, marketMaker.getSource());
        assertEquals(0, marketMaker.getIndex());
        assertEquals('\0', marketMaker.getExchangeCode());
        assertNull(marketMaker.getMarketMaker());
        assertTrue(Double.isNaN(marketMaker.getBidPrice()));
        assertTrue(Double.isNaN(marketMaker.getBidSize()));
        assertTrue(Double.isNaN(marketMaker.getAskPrice()));
        assertTrue(Double.isNaN(marketMaker.getAskSize()));
    }
}
