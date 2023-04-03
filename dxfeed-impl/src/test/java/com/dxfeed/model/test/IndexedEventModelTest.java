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
package com.dxfeed.model.test;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Side;
import com.dxfeed.model.IndexedEventModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link IndexedEventModel} class.
 */
public class IndexedEventModelTest {
    private static final String SYMBOL = "INDEX-TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;

    private final List<Runnable> executionQueue = new ArrayList<>();
    private final IndexedEventModel<Order> indexedOrders = new IndexedEventModel<>(Order.class);

    private boolean changed;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        publisher = endpoint.getPublisher();
        DXFeed feed = endpoint.getFeed();
        endpoint.executor(executionQueue::add);
        indexedOrders.setSymbol(SYMBOL);
        indexedOrders.getEventsList().addListener(change -> changed = true);
        indexedOrders.attach(feed);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    // Simple test -- add two orders and remove them
    @Test
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

    @Test
    public void testEventListIterator() {
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().iterator().hasNext());

        publish(1, 12.34, 0);
        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 1, 12.34);
        check(1, 2, 56.78);

        Iterator<Order> it = indexedOrders.getEventsList().iterator();
        assertTrue(it.hasNext());
        checkOrder(it.next(), 1, 12.34);
        assertTrue(it.hasNext());
        checkOrder(it.next(), 2, 56.78);
        assertFalse(it.hasNext());

        publish(1, 12.34, IndexedEvent.REMOVE_EVENT);
        publish(2, 56.78, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().iterator().hasNext());
    }

    @Test
    public void testListIterator() {
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().listIterator().hasNext());
        assertFalse(indexedOrders.getEventsList().listIterator().hasPrevious());

        publish(1, 12.34, 0);
        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 1, 12.34);
        check(1, 2, 56.78);

        // iterate from start to end
        ListIterator<Order> it = indexedOrders.getEventsList().listIterator();
        assertTrue(it.hasNext());
        checkOrder(it.next(), 1, 12.34);
        assertTrue(it.hasNext());
        checkOrder(it.next(), 2, 56.78);
        assertFalse(it.hasNext());
        // ... and back to the start point
        assertTrue(it.hasPrevious());
        checkOrder(it.previous(), 2, 56.78);
        assertTrue(it.hasPrevious());
        checkOrder(it.previous(), 1, 12.34);
        assertFalse(it.hasPrevious());

        publish(1, 12.34, IndexedEvent.REMOVE_EVENT);
        publish(2, 56.78, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().listIterator().hasNext());
        assertFalse(indexedOrders.getEventsList().listIterator().hasPrevious());
    }

    @Test
    public void testListIteratorWithIndex() {
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().listIterator(0).hasNext());
        assertFalse(indexedOrders.getEventsList().listIterator(0).hasPrevious());

        publish(1, 12.34, 0);
        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        check(0, 1, 12.34);
        check(1, 2, 56.78);

        // iterate from start
        ListIterator<Order> it = indexedOrders.getEventsList().listIterator(0);
        assertTrue(it.hasNext());
        checkOrder(it.next(), 1, 12.34);
        // ... from middle
        it = indexedOrders.getEventsList().listIterator(1);
        assertTrue(it.hasNext());
        assertTrue(it.hasPrevious());
        checkOrder(it.next(), 2, 56.78);
        assertFalse(it.hasNext());
        // ...from end
        it = indexedOrders.getEventsList().listIterator(2);
        assertTrue(it.hasPrevious());
        checkOrder(it.previous(), 2, 56.78);
        /// ... from middle backwards
        it = indexedOrders.getEventsList().listIterator(1);
        assertTrue(it.hasPrevious());
        checkOrder(it.previous(), 1, 12.34);
        assertFalse(it.hasPrevious());

        publish(1, 12.34, IndexedEvent.REMOVE_EVENT);
        publish(2, 56.78, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);
        assertFalse(indexedOrders.getEventsList().listIterator(0).hasNext());
        assertFalse(indexedOrders.getEventsList().listIterator(0).hasPrevious());
    }

    @Test
    public void testCloseEmpty() {
        checkSize(0);
        indexedOrders.close();
    }

    @Test
    public void testCloseEmptyAfterWork() {
        checkSize(0);
        // get a couple of orders
        publish(1, 12.34, 0);
        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);
        // ... then remove them
        publish(1, 12.34, IndexedEvent.REMOVE_EVENT);
        publish(2, 56.78, IndexedEvent.REMOVE_EVENT);
        process();
        checkChanged(true);
        checkSize(0);

        indexedOrders.close();
    }

    @Test
    public void testCloseNonEmpty() {
        checkSize(0);
        // get a couple of orders
        publish(1, 12.34, 0);
        publish(2, 56.78, 0);
        process();
        checkChanged(true);
        checkSize(2);

        indexedOrders.close();
    }

    @Test
    public void testCloseAbruptly() {
        checkSize(0);
        // get a couple of orders
        publish(1, 12.34, 0);
        process();

        Iterator<Order> it = indexedOrders.getEventsList().iterator();

        indexedOrders.close();

        // emulate stale events processing
        publish(2, 56.78, 0);
        process();

        checkOrder(it.next(), 1, 12.34);
        assertFalse(it.hasNext());
    }

    // Add two orders snapshot
    @Test
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
    @Test
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

    @Test
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

    @Test
    public void testGrow() {
        int n = 100;
        for (int i = 0; i < n; i++) {
            int index = n - i - 1;
            publish(
                index,
                index * 100.0,
                i == 0 ? IndexedEvent.SNAPSHOT_BEGIN : i == n - 1 ? IndexedEvent.SNAPSHOT_END : 0
            );
        }
        process();
        checkChanged(true);
        checkSize(n);
        for (int i = 0; i < n; i++) {
            check(i, i, i * 100.0);
        }
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
        checkOrder(order, index, price);
    }

    private void checkOrder(Order order, int index, double price) {
        assertEquals("symbol", SYMBOL, order.getEventSymbol());
        assertEquals("index", index, order.getIndex());
        assertEquals("price", price, order.getPrice(), 0.0);
    }

    private void process() {
        for (Runnable runnable : executionQueue) {
            runnable.run();
        }
        executionQueue.clear();
    }
}
