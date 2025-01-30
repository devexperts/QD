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

import com.dxfeed.event.EventType;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes transactions for a single {@link EventType event type}, {@link EventType#getEventSymbol() symbol},
 * and {@link IndexedEventSource source}.
 * If multiple symbols or sources need to be processed, create separate instances of this class for each one.
 *
 * <p>This class processes events based on their {@link IndexedEvent#getEventFlags() event flags}.
 * It handles pending events, snapshots, etc.
 *
 * <p>Users of this class see only the list of events (passed to the {@link TxListener listener}) in a consistent state.
 * For example, due to possible reconnections and retransmissions, snapshots can overlap, causing event flags
 * to intersect in the event stream. It's possible to encounter {@link IndexedEvent#SNAPSHOT_END SNAPSHOT_END}
 * before {@link IndexedEvent#SNAPSHOT_BEGIN SNAPSHOT_BEGIN}, or to find
 * {@link IndexedEvent#SNAPSHOT_BEGIN SNAPSHOT_BEGIN} after {@link IndexedEvent#SNAPSHOT_END SNAPSHOT_END}, and so on.
 * This class correctly handles such cases.
 *
 * <h3>Threads and locks</h3>
 *
 * <p>This class is <b>NOT</b> thread-safe and cannot be used from multiple threads
 * without external synchronization.
 *
 * @param <E> the type of indexed events processed by this class.
 */
final class TxEventProcessor<E extends IndexedEvent<?>> {
    private final List<Runnable> deferredNotifications = new ArrayList<>();
    private final List<E> pendingEvents = new ArrayList<>();
    private final TxListener<E> listener;
    private boolean isPartialSnapshot;
    private boolean isCompleteSnapshot;

    /**
     * The listener interface for receiving indexed processed events.
     *
     * @param <E> the type of events.
     */
    @FunctionalInterface
    public interface TxListener<E> {
        /**
         * Invoked when a complete transaction (one or more) is received.
         *
         * @param events the list of received events representing one or more completed transactions.
         * @param isSnapshot indicates if the events form a snapshot.
         */
        public void eventsReceived(List<E> events, boolean isSnapshot);
    }

    /**
     * Constructs a new {@link TxEventProcessor} with the specified listener.
     *
     * @param listener the listener.
     */
    public TxEventProcessor(TxListener<E> listener) {
        this.listener = listener;
    }

    /**
     * Processes a batch of indexed events.
     * Notifications to set listener are deferred until the batch processing is complete.
     *
     * <p><b>Warning:</b> This method does not check the {@link EventType#getEventSymbol() symbol}
     * or {@link IndexedEventSource source} of the event, but it is expected that only each instance of
     * this class will be passed events with the same symbol and source; otherwise, it's undefined behavior.
     * If multiple symbols/sources need to be processed, create multiple instances of this class, one for each.
     *
     * @param events the events batch to process.
     */
    public void processEvents(List<E> events) {
        List<E> updates = new ArrayList<>();
        for (E event : events) {
            if (isSnapshotBegin(event)) {
                isPartialSnapshot = true;
                isCompleteSnapshot = false;
                pendingEvents.clear(); // remove any unprocessed leftovers on new snapshot
                if (!updates.isEmpty()) { // if any completed updates exist, save them until the end of batch processing
                    deferNotification(updates, false);
                    updates = new ArrayList<>();
                }
            }
            if (isPartialSnapshot && isSnapshotEndOrSnip(event)) {
                isPartialSnapshot = false;
                isCompleteSnapshot = true;
            }
            pendingEvents.add(event); // defer processing of this event while snapshot in progress or tx pending
            if (isPartialSnapshot || isTxPending(event))
                continue; // waiting for the end of snapshot or updates

            // we have completed updates or snapshot
            if (isCompleteSnapshot) { // completed snapshot
                isCompleteSnapshot = false;
                processSnapshot();
            } else { // completed updates
                updates.addAll(pendingEvents);
            }
            pendingEvents.clear();
        }
        if (!updates.isEmpty())
            deferNotification(updates, false);
        runDeferredNotifications();
    }

    private void processSnapshot() {
        Map<Long, E> snapshot = new LinkedHashMap<>(pendingEvents.size());
        for (E event : pendingEvents) {
            if (isRemove(event)) {
                snapshot.remove(event.getIndex());
            } else {
                event.setEventFlags(0);
                snapshot.put(event.getIndex(), event);
            }
        }
        deferNotification(new ArrayList<>(snapshot.values()), true);
    }

    private void deferNotification(List<E> events, boolean isSnapshot) {
        deferredNotifications.add(() -> listener.eventsReceived(events, isSnapshot));
    }

    private void runDeferredNotifications() {
        try {
            for (Runnable notification : deferredNotifications) {
                notification.run();
            }
        } finally {
            deferredNotifications.clear();
        }
    }

    private boolean isSnapshotBegin(E event) {
        return (event.getEventFlags() & IndexedEvent.SNAPSHOT_BEGIN) != 0;
    }

    private boolean isSnapshotEnd(E event) {
        return (event.getEventFlags() & IndexedEvent.SNAPSHOT_END) != 0;
    }

    private boolean isSnapshotSnip(E event) {
        return (event.getEventFlags() & IndexedEvent.SNAPSHOT_SNIP) != 0;
    }

    private boolean isSnapshotEndOrSnip(E event) {
        return isSnapshotEnd(event) || isSnapshotSnip(event);
    }

    private boolean isTxPending(E event) {
        return (event.getEventFlags() & IndexedEvent.TX_PENDING) != 0;
    }

    private boolean isRemove(E event) {
        return (event.getEventFlags() & IndexedEvent.REMOVE_EVENT) != 0;
    }
}
