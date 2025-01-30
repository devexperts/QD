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
package com.dxfeed.model;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Side;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.dxfeed.event.IndexedEvent.REMOVE_EVENT;
import static com.dxfeed.event.IndexedEvent.SNAPSHOT_BEGIN;
import static com.dxfeed.event.IndexedEvent.SNAPSHOT_END;
import static com.dxfeed.event.IndexedEvent.SNAPSHOT_SNIP;
import static com.dxfeed.event.IndexedEvent.TX_PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IndexedEventTxModelTest {
    private static final String SYMBOL = "TEST-SYMBOL";
    private static final IndexedEventSource SOURCE = IndexedEventSource.DEFAULT;

    private final List<Order> publishedEvents = new ArrayList<>();
    private final Queue<Order> receivedEvents = new ArrayDeque<>();

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private IndexedEventTxModel<Order> model;

    private int listenerNotificationCounter;
    private int snapshotNotificationCounter;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
    }

    @After
    public void tearDown() {
        snapshotNotificationCounter = 0;
        listenerNotificationCounter = 0;
        receivedEvents.clear();
        publishedEvents.clear();
        model.close();
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testInitialState() {
        IndexedEventTxModel.Builder<Order> builder = IndexedEventTxModel.newBuilder(Order.class);
        assertThrows(NullPointerException.class, builder::build); // if the symbol is not set
        builder.withSymbol(SYMBOL);
        assertThrows(NullPointerException.class, builder::build); // if the source is not set
        builder.withSource(SOURCE);
        assertThrows(NullPointerException.class, builder::build); // if the listener is not set

        model = builder.withListener((events, isSnapshot) -> {}).build();
        assertEquals(SYMBOL, model.getSymbol());
        assertEquals(SOURCE, model.getSource());
        assertFalse(model.isClosed());
        model.close();

        IndexedEventSubscriptionSymbol<?> symbol = new IndexedEventSubscriptionSymbol<>("TEST", OrderSource.ntv);
        model = builder.withSymbol(symbol).build(); // use IndexedEventSubscriptionSymbol overload
        assertEquals(symbol, model.getIndexedEventSubscriptionSymbol());
        assertFalse(model.isClosed());
    }

    @Test
    public void testSnapshotAndUpdate() {
        model = builder().build();

        addOrderToPublish(1, 1, SNAPSHOT_BEGIN);
        addOrderToPublish(0, 2, SNAPSHOT_END);
        addOrderToPublish(1, 3);
        addOrderToPublish(3, 4);
        addOrderToPublish(2, 5);
        publishDeferred(false);
        assertSnapshotNotification(1); // only one snapshot
        assertListenerNotification(2);
        assertReceivedEventCount(5);
        assertOrder(1, 1, 0);
        assertOrder(0, 2, 0);
        assertOrder(1, 3, 0); // event with index 1 is not merged
        assertOrder(3, 4, 0);
        assertOrder(2, 5, 0);
    }

    @Test
    public void testEmptySnapshot() {
        model = builder().build();

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END | REMOVE_EVENT);
        publishDeferred(false);
        assertSnapshotNotification(1); // only one snapshot
        assertReceivedEventCount(0); // event with REMOVE_EVENT flag was removed inside the snapshot
    }

    @Test
    public void testSnapshotWithPending() {
        model = builder().build();

        addOrderToPublish(1, 1, SNAPSHOT_BEGIN);
        publishDeferred(false);
        assertIsChanged(false); // not processed yet
        assertReceivedEventCount(0);

        addOrderToPublish(0, 2, SNAPSHOT_END | TX_PENDING);
        publishDeferred(false);
        assertIsChanged(false); // not processed yet, because the pending flag is set

        addOrderToPublish(1, 3); // event without pending
        publishDeferred(false);
        assertListenerNotification(1); // since the transaction ended here
        assertSnapshotNotification(1); // and it's all a one snapshot
        assertReceivedEventCount(2); // the same indices within a snapshot were merged
        assertOrder(1, 3, 0);
        assertOrder(0, 2, 0);
    }

    @Test
    public void testMultipleSnapshot() {
        model = builder().build();

        addOrderToPublish(1, 1, SNAPSHOT_BEGIN);
        publishDeferred(false);
        assertIsChanged(false); // not processed yet
        assertReceivedEventCount(0);

        addOrderToPublish(0, 2, SNAPSHOT_END);
        addOrderToPublish(2, 3, SNAPSHOT_BEGIN);
        publishDeferred(false);
        assertListenerNotification(1);
        assertSnapshotNotification(1); // only one snapshot so far, beginning of the second one is in the buffer
        assertReceivedEventCount(2);
        assertOrder(1, 1, 0);
        assertOrder(0, 2, 0);

        addOrderToPublish(0, 4, SNAPSHOT_END); // end of second snapshot
        addOrderToPublish(3, 5); // update after second snapshot
        publishDeferred(false);
        assertSnapshotNotification(1);
        assertListenerNotification(2);
        assertReceivedEventCount(3);
        assertOrder(2, 3, 0);
        assertOrder(0, 4, 0);
        assertOrder(3, 5, 0);
    }

    @Test
    public void testMultipleSnapshotInOneBatch() {
        model = builder().build();

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        addOrderToPublish(0, 2, SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        addOrderToPublish(0, 3, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_SNIP | SNAPSHOT_END);
        publishDeferred(false);
        assertListenerNotification(3);
        assertSnapshotNotification(3);
        assertReceivedEventCount(2); // no event with REMOVE_EVENT flag
        assertOrder(0, 1, 0);
        assertOrder(0, 2, 0);
    }

    @Test
    public void testMultipleSnapshotWithUpdatesInOneBatch() {
        model = builder().build();

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        addOrderToPublish(1, 2);
        addOrderToPublish(2, 3);
        addOrderToPublish(0, 4, SNAPSHOT_BEGIN | SNAPSHOT_SNIP);
        addOrderToPublish(1, 5);
        addOrderToPublish(0, 6, SNAPSHOT_BEGIN | REMOVE_EVENT | SNAPSHOT_SNIP | SNAPSHOT_END);
        addOrderToPublish(1, 7);
        publishDeferred(false);
        assertSnapshotNotification(3);
        assertListenerNotification(6); // all the snapshots and updates have been received
        assertReceivedEventCount(6); // no event with REMOVE_EVENT flag
        assertOrder(0, 1, 0);
        assertOrder(1, 2, 0);
        assertOrder(2, 3, 0);
        assertOrder(0, 4, 0);
        assertOrder(1, 5, 0);
        assertOrder(1, 7, 0);
    }

    @Test
    public void testIncompleteSnapshot() {
        model = builder().build();

        addOrderToPublish(1, 1, SNAPSHOT_BEGIN);
        publishDeferred(false);
        assertIsChanged(false); // not processed yet
        assertReceivedEventCount(0);

        addOrderToPublish(2, 2, SNAPSHOT_BEGIN); // yet another snapshot begins
        addOrderToPublish(3, 3); // event part of a snapshot
        publishDeferred(false);
        assertIsChanged(false); // not processed yet
        assertReceivedEventCount(0);

        addOrderToPublish(4, 4, SNAPSHOT_BEGIN); // start new snapshot
        publishDeferred(false);
        assertIsChanged(false);
        assertReceivedEventCount(0);

        addOrderToPublish(0, 5, SNAPSHOT_END); // full snapshot
        addOrderToPublish(5, 6); // update event after the snapshot end in the same batch
        addOrderToPublish(6, 7); // yet another update event after the snapshot end in the same batch
        publishDeferred(false);
        assertListenerNotification(2);
        assertSnapshotNotification(1); // of which one snapshot
        assertReceivedEventCount(4); // chunks of previous snapshots have been deleted
        assertOrder(4, 4, 0);
        assertOrder(0, 5, 0);
        assertOrder(5, 6, 0);
        assertOrder(6, 7, 0);

        addOrderToPublish(7, 4, SNAPSHOT_BEGIN); // the snapshot hasn't ended yet
        publishDeferred(false);
        assertIsChanged(false); // not processed yet
    }

    @Test
    public void testPending() {
        model = builder().build();

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(false);
        assertIsChanged(true);
        assertIsSnapshot(true);
        assertReceivedEventCount(1);

        addOrderToPublish(1, 2, TX_PENDING); // publish pending event
        addOrderToPublish(1, 3, TX_PENDING); // publish pending event, same index as the previous one
        publishDeferred(false);
        assertIsChanged(false); // not processed yet

        addOrderToPublish(2, 4, 0); // publish without pending
        addOrderToPublish(3, 5, 0); // publish without pending
        publishDeferred(false);
        assertListenerNotification(1);
        assertIsSnapshot(false);
        assertReceivedEventCount(5); // all published events, without merge
        assertOrder(0, 1, 0);
        assertOrder(1, 2, TX_PENDING);
        assertOrder(1, 3, TX_PENDING);
        assertOrder(2, 4, 0);
        assertOrder(3, 5, 0);
    }

    @Test
    public void testPendingEventsClearedAfterSnapshotReceived() {
        model = builder().build();

        addOrderToPublish(2, 1, TX_PENDING);
        addOrderToPublish(3, 2, TX_PENDING);
        addOrderToPublish(1, 3, TX_PENDING);
        addOrderToPublish(0, 4, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(false);
        assertReceivedEventCount(1); // previous pending events have been cleared
        assertOrder(0, 4, 0);
    }

    @Test
    public void testExceptionInListenerDoesNotBreakModelState() {
        model = builder()
            .withExecutor(task -> {
                try {
                    task.run();
                } catch (Throwable ignored) {
                    // ignore
                }
            })
            .withListener((events, isSnapshot) -> {
                ++listenerNotificationCounter;
                if (isSnapshot)
                    ++snapshotNotificationCounter;
                receivedEvents.addAll(events);
                throw new RuntimeException(); // throw
            })
            .build();

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true);
        assertIsChanged(true);
        assertReceivedEventCount(1);
        assertOrder(0, 1, 0);

        addOrderToPublish(1, 1, TX_PENDING);
        addOrderToPublish(2, 2, TX_PENDING);
        addOrderToPublish(3, 3, TX_PENDING);
        publishDeferred(true);
        assertIsChanged(false);

        addOrderToPublish(4, 4);
        publishDeferred(true);
        assertReceivedEventCount(4);
        assertOrder(1, 1, TX_PENDING);
        assertOrder(2, 2, TX_PENDING);
        assertOrder(3, 3, TX_PENDING);
        assertOrder(4, 4, 0);
    }

    @Test
    public void testEventsWithoutSnapshot() {
        model = builder().build();

        addOrderToPublish(2, 1);
        addOrderToPublish(3, 2);
        addOrderToPublish(1, 3);
        addOrderToPublish(1, 4); // same index as the previous one
        addOrderToPublish(0, 5);
        publishDeferred(false);
        assertListenerNotification(1);
        assertReceivedEventCount(5); // events prior to the snapshot are transmitted as is
        assertOrder(2, 1, 0);
        assertOrder(3, 2, 0);
        assertOrder(1, 3, 0);
        assertOrder(1, 4, 0); // same index as the previous one, without merge
        assertOrder(0, 5, 0);

        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        addOrderToPublish(1, 2);
        publishDeferred(false);
        assertIsChanged(true);
        assertReceivedEventCount(2);
        assertOrder(0, 1, 0);
        assertOrder(1, 2, 0);
    }

    @Test
    public void testSnapshotWithRemoveAndPending() {
        model = builder().build();

        addOrderToPublish(7, 1, SNAPSHOT_BEGIN);
        addOrderToPublish(6, 2);
        addOrderToPublish(5, 3, REMOVE_EVENT);
        addOrderToPublish(4, 4);
        addOrderToPublish(3, 5);
        addOrderToPublish(2, 6, TX_PENDING);
        addOrderToPublish(2, 7);
        addOrderToPublish(1, 8);
        addOrderToPublish(0, Double.NaN, SNAPSHOT_END | TX_PENDING | REMOVE_EVENT);
        addOrderToPublish(1, 9);
        publishDeferred(false);
        assertReceivedEventCount(6);
        assertOrder(7, 1, 0);
        assertOrder(6, 2, 0);
        assertOrder(4, 4, 0);
        assertOrder(3, 5, 0);
        assertOrder(2, 7, 0);
        assertOrder(1, 9, 0);
    }

    @Test
    public void testAttachFeed() {
        model = builder().withFeed(null).build(); // create a model instance without attaching it to a feed

        // add an event to be published, with SNAPSHOT_BEGIN and SNAPSHOT_END flags
        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true); // publish the event
        assertIsChanged(false); // verify that there is no change since the model is not attached to the feed

        // attach the model to the feed and publish another event
        model.getSubscriptionController().attach(feed);
        addOrderToPublish(0, 1, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true); // publish the event
        assertIsChanged(true); // verify that the model registers the change
        assertReceivedEventCount(1); // verify that the event is received

        // detach the model from the feed and publish another event
        model.getSubscriptionController().detach(feed);
        addOrderToPublish(0, 2, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true); // publish the event
        assertIsChanged(false); // verify that there is no change since the model is detached

        // reattach the model to the feed and publish another event
        model.getSubscriptionController().attach(feed);
        addOrderToPublish(0, 3, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true); // publish the event
        assertIsChanged(true); // verify that the model registers the change
        assertReceivedEventCount(2); // verify that two events are now received
        assertOrder(0, 1, 0);
        assertOrder(0, 3, 0);
    }

    @Test
    public void testBatchLimit() {
        model = builder().withEventsBatchLimit(1).build();
        assertEquals(1, model.getSubscriptionController().getEventsBatchLimit());

        addOrderToPublish(2, 1, SNAPSHOT_BEGIN);
        addOrderToPublish(1, 2);
        addOrderToPublish(0, 3, SNAPSHOT_END);
        publishDeferred(true);
        assertListenerNotification(1); // batch limit does not affect transactions

        addOrderToPublish(3, 1);
        addOrderToPublish(4, 1);
        addOrderToPublish(5, 1);
        publishDeferred(true);
        assertListenerNotification(3); // three calls

        model.getSubscriptionController().setEventsBatchLimit(3);
        assertEquals(3, model.getSubscriptionController().getEventsBatchLimit());
        addOrderToPublish(6, 1);
        addOrderToPublish(7, 1);
        addOrderToPublish(8, 1);
        addOrderToPublish(9, 1);
        publishDeferred(true);
        assertListenerNotification(2); // two calls, after change batch limit
    }

    @Test
    public void testCloseAbruptly() {
        model = builder().build();

        addOrderToPublish(0, 12.34, SNAPSHOT_BEGIN | SNAPSHOT_END);
        publishDeferred(true);
        assertIsChanged(true);
        assertIsSnapshot(true);

        assertFalse(model.isClosed());
        model.close();
        assertTrue(model.isClosed());

        addOrderToPublish(2, 56.78, 0); // emulate stale events processing
        publishDeferred(true);
        assertIsChanged(false); // no change after close
    }

    private void addOrderToPublish(int index, double size) {
        addOrderToPublish(index, size, 0);
    }

    private void addOrderToPublish(int index, double size, int eventFlags) {
        Order order = new Order(SYMBOL);
        order.setIndex(index);
        order.setSource(OrderSource.DEFAULT);
        order.setSizeAsDouble(size);
        order.setEventFlags(eventFlags);
        order.setOrderSide(Side.BUY);
        publishedEvents.add(order);
    }

    private void publishDeferred(boolean withPublisher) {
        if (withPublisher) {
            publisher.publishEvents(publishedEvents);
        } else {
            model.processEvents(publishedEvents);
        }
        publishedEvents.clear();
    }

    private void assertIsChanged(boolean isChanged) {
        assertTrue(isChanged ? listenerNotificationCounter > 0 : listenerNotificationCounter == 0);
        listenerNotificationCounter = 0;
    }

    private void assertIsSnapshot(boolean isSnapshot) {
        assertTrue(isSnapshot ? snapshotNotificationCounter > 0 : snapshotNotificationCounter == 0);
        snapshotNotificationCounter = 0;
    }

    private void assertListenerNotification(int count) {
        assertEquals(count, listenerNotificationCounter);
        listenerNotificationCounter = 0;
    }

    private void assertSnapshotNotification(int count) {
        assertEquals(count, snapshotNotificationCounter);
        snapshotNotificationCounter = 0;
    }

    private void assertReceivedEventCount(int count) {
        assertEquals(count, receivedEvents.size());
    }

    private void assertOrder(int index, double size, int eventFlags) {
        Order order = receivedEvents.poll();
        assertNotNull(String.format("Order in the %d index cannot be null", index), order);
        assertEquals(String.format("At index %d: Unexpected symbol", index), SYMBOL, order.getEventSymbol());
        assertEquals(String.format("At index %d: Unexpected index", index), index, order.getIndex());
        assertEquals(String.format("At index %d: Unexpected size", index), size, order.getSizeAsDouble(), 0.0);
        assertEquals(String.format("At index %d: Unexpected flags", index), eventFlags, order.getEventFlags());
    }

    private IndexedEventTxModel.Builder<Order> builder() {
        return IndexedEventTxModel.newBuilder(Order.class)
            .withFeed(feed)
            .withSymbol(SYMBOL)
            .withSource(SOURCE)
            .withListener(( events, isSnapshot) -> {
                ++listenerNotificationCounter;
                if (isSnapshot)
                    ++snapshotNotificationCounter;
                receivedEvents.addAll(events);
            })
            .withExecutor(Runnable::run);
    }
}
