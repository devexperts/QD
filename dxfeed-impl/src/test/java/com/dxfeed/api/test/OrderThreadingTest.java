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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class OrderThreadingTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<Order> sub;

    private final BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10);
    private final ConcurrentLinkedQueue<Runnable> executorQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger counterCallback = new AtomicInteger(0);

    private final long t0 = System.currentTimeMillis() / 1000 * 1000; // round to seconds
    private final long t1 = t0 - 1000; // round to seconds
    private final long t2 = t0 + 1000; // round to seconds

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(Order.class);
        endpoint.executor(executorQueue::add);
        sub.addEventListener(orders -> {
            counterCallback.getAndIncrement();
            queue.addAll(orders);
        });
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testConcurrency() {
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

        // ensure that we have one task in executor queue
        assertEquals(1, executorQueue.size());

        // publish on order event
        Order order = new Order(SYMBOL);
        order.setScope(Scope.ORDER);
        order.setOrderSide(Side.SELL);
        order.setExchangeCode('C');
        order.setMarketMaker("NSDQ");
        order.setPrice(12.36);
        order.setSize(25);
        order.setTime(t2);
        publisher.publishEvents(Collections.singleton(order));

        // execute the task
        Runnable task;
        while ((task = executorQueue.poll()) != null)
            task.run();

        // ensure that we have one task in call-back queue
        assertEquals(1, counterCallback.get());

        // ensure that we have no most tasks
        assertEquals(0, executorQueue.size());

        // ensure that order queue has 3 incoming orders
        assertEquals(3, queue.size());
    }
}
