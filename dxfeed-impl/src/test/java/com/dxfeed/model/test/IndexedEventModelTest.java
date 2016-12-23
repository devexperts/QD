/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.model.test;

import java.util.*;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Side;
import com.dxfeed.model.IndexedEventModel;
import junit.framework.TestCase;

/**
 * Unit test for {@link IndexedEventModel} class.
 */
public class IndexedEventModelTest extends TestCase {
    private static final String SYMBOL = "INDEX-TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeed feed;

    private final List<Runnable> executionQueue = new ArrayList<>();
    private final IndexedEventModel<Order> indexedOrders = new IndexedEventModel<>(Order.class);

    private boolean changed;

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        publisher = endpoint.getPublisher();
        feed = endpoint.getFeed();
        endpoint.executor(executionQueue::add);
        indexedOrders.setSymbol(SYMBOL);
        indexedOrders.getEventsList().addListener(change -> changed = true);
        indexedOrders.attach(feed);
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    // Simple test -- add two orders and remove them
    public void testSimple() {
        checkSize(0);

        publish(1, 12.34, 0);
        process();
        checkChanged(true);
        checkSize(1);
        check(0, 1, 12.34);

        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 1, 12.34);
        check(1, 2, 56.78);

        publish(1, 12.34, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(1);
        check(0, 2, 56.78);

        publish(2, 56.78, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);
    }

    // Add two orders snapshot
    public void testSnapshot() {
        checkSize(0);

        publish(1, 12.34, IndexedEvent.SNAPSHOT_BEGIN);
        process();
        checkChanged(false);
        checkSize(0); // not processed yet !!!

        publish(0, 34.12, IndexedEvent.SNAPSHOT_END);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 0, 34.12);
        check(1, 1, 12.34);
    }

    // Add an order, then do transaction
    public void testTx() {
        checkSize(0);

        publish(0, 12.34, IndexedEvent.SNAPSHOT_BEGIN | IndexedEvent.SNAPSHOT_END);
        process();
        checkChanged(true);
        checkSize(1);
        check(0, 0, 12.34);

        publish(0, 34.12, IndexedEvent.TX_PENDING);
        process();
        checkChanged(false); // not processed yet !!!
        checkSize(1);
        check(0, 0, 12.34); // old order

        publish(1, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 0, 34.12); // new order
        check(1, 1, 56.78);
    }

    public void testSnapshotClean() {
        checkSize(0);

        publish(2, 12.34, IndexedEvent.SNAPSHOT_BEGIN);
        publish(1, 23.45, 0);
        publish(0, 34.56, IndexedEvent.SNAPSHOT_END);
        process();
        checkChanged(true);
        checkSize(3);
        check(0, 0, 34.56);
        check(1, 1, 23.45);
        check(2, 2, 12.34);

        publish(0, 0, IndexedEvent.SNAPSHOT_BEGIN | IndexedEvent.SNAPSHOT_END | IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);
    }

    public void testGrow() {
        int n = 100;
        for (int i = 0; i < n; i++) {
            int index = n - i - 1;
            publish(index, index * 100.0, i == 0 ? IndexedEvent.SNAPSHOT_BEGIN : i == n - 1 ? IndexedEvent.SNAPSHOT_END : 0);
        }
        process();
        checkChanged(true);
        checkSize(n);
        for (int i = 0; i < n; i++)
            check(i, i, i * 100.0);
    }

    private void publish(int index, double price, int eventFlags) {
        Order order = new Order(SYMBOL);
        order.setIndex(index);
        order.setPrice(price);
        order.setEventFlags(eventFlags);
        order.setOrderSide(Side.BUY);
        publisher.publishEvents(Arrays.asList(order));
    }

    private void checkChanged(boolean expected) {
        assertEquals(expected, changed);
        changed = false;
    }

    private void checkSize(int size) {
        assertEquals("size", size, indexedOrders.getEventsList().size());
        assertEquals("isEmpty", size == 0, indexedOrders.getEventsList().isEmpty());
    }

    private void check(int i, int index, double price) {
        Order order = indexedOrders.getEventsList().get(i);
        assertEquals("symbol", SYMBOL, order.getEventSymbol());
        assertEquals("index", index, order.getIndex());
        assertEquals("price", price, order.getPrice());
    }

    private void process() {
        for (Runnable runnable : executionQueue)
            runnable.run();
        executionQueue.clear();
    }
}
