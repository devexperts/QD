/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.stats.QDStatsContainer;
import com.devexperts.util.SystemProperties;

/**
 * The <code>SubProvider</code> is a matrix-based implementation of {@link SubscriptionProvider}.
 * <p>
 * The <code>SubProvider</code> contains a matrix with the set of subscription
 * entries added by corresponding subscription operations but not retrieved yet.
 * Such entries are linked into 'queue list' which preserves order in which
 * entries were added and they are retrieved in this order (use QUEUE_NEXT
 * offset) and use queue mark when they were added (use QUEUE_MARK offset) in
 * the same manner as queue list of data in {@link Ticker}.
 */
final class SubProvider extends AbstractRecordProvider implements QDStatsContainer {
    /**
     *  We will keep at least 2000 entries allocated for distributor subscription provider by default.
     */
    private static final int MAX_SUB_SHIFT =
        Hashing.getShift(SystemProperties.getIntProperty(SubProvider.class, "minSubSize", 2000));

    private static final int QUEUE_NEXT = 2;
    private static final int QUEUE_MARK = 3;
    private static final int TIME_SUB = 4;
    private static final int TIME_SUB_X = 5;

    final Collector collector;

    private final Distributor distributor;
    private final boolean is_added_provider;
    private final boolean has_time;
    private final RecordMode mode;

    private final QDStats stats;

    private volatile RecordListener listener; // SYNC: write(global), read(none)

    private SubMatrix sub; // SYNC: all(global)

    private int queue_head = -1;
    private int queue_tail = -1;


    // SYNC: global
    SubProvider(Distributor distributor, boolean is_added_provider, QDStats stats) {
        this.distributor = distributor;
        this.is_added_provider = is_added_provider;
        this.has_time = is_added_provider && distributor.collector.hasTime;
        this.collector = distributor.collector;
        RecordMode mode = has_time ? RecordMode.HISTORY_SUBSCRIPTION : RecordMode.SUBSCRIPTION;
        if (collector.hasEventTimeSequence())
            mode = mode.withEventTimeSequence();
        this.mode = mode;
        this.stats = stats;
    }

    // SYNC: none
    public QDStats getStats() {
        return stats;
    }

    // SYNC: none
    void closeStats() {
        stats.close();
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // It will die after having performed changes.
    // SYNC: global
    boolean add(int key, int rid, long time) {
        rehashIfNeeded();
        boolean added = false;
        SubMatrix sub = this.sub;
        int index = sub.addIndex(key, rid);
        if (sub.getInt(index + QUEUE_NEXT) == 0) {
            int lindex = queue_tail;
            if (lindex < 0)
                queue_head = index;
            else
                sub.setInt(lindex + QUEUE_NEXT, index);
            queue_tail = index;
            sub.setInt(index + QUEUE_NEXT, -1);
        }
        if (sub.getInt(index + QUEUE_MARK) == 0) {
            sub.setInt(index + QUEUE_MARK, 1); // :TODO: proper queue mark
            sub.updateAddedPayload(rid);
            added = true;
        }
        if (has_time)
            sub.setLong(index + TIME_SUB, time);
        return added;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // It will die after having performed changes.
    // SYNC: global
    void remove(int key, int rid) {
        SubMatrix sub = this.sub;
        int index = sub.getIndex(key, rid, 0);
        if (sub.getInt(index + QUEUE_MARK) == 0) // Not found or not payload.
            return;
        sub.setInt(index + QUEUE_MARK, 0);
        sub.updateRemovedPayload(rid);
        if (has_time)
            sub.setLong(index + TIME_SUB, 0);
        if (queue_head == index) {
            do {
                int next = sub.getInt(index + QUEUE_NEXT);
                // remove from queue (also makes cell "removed")
                sub.setInt(index + QUEUE_NEXT, 0);
                sub.clearRemovedCellsTrail(index, QUEUE_NEXT);
                index = next;
            } while (index > 0 && sub.getInt(index + QUEUE_MARK) == 0);
            queue_head = index;
            if (index < 0)
                queue_tail = -1;
        }
        if (queue_head < 0) // postpone downsize rehashing until queue is empty
            rehashIfNeeded();
    }

    private void rehashIfNeeded() {
        if (sub.needRehash(MAX_SUB_SHIFT))
            rehash();
        collector.mapper.rehashIfNeeded();
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    private void rehash() {
        SubMatrix osub = sub;
        SubMatrix asub = sub = osub.rehash(MAX_SUB_SHIFT);
        int oindex = queue_head;
        if (oindex > 0) {
            // Fix links: QUEUE_NEXT must reference to new index.
            int lindex = -1;
            while (oindex > 0) {
                int aindex = asub.getIndex(osub.getInt(oindex), osub.getInt(oindex + 1), 0);
                if (aindex > 0) {
                    if (osub.getInt(oindex + QUEUE_MARK) != 0) {
                        if (lindex < 0)
                            queue_head = aindex;
                        else
                            asub.setInt(lindex + QUEUE_NEXT, aindex);
                        lindex = aindex;
                    } else
                        asub.setInt(aindex + QUEUE_NEXT, 0);
                } // If aindex <= 0, then old QUEUE_MARK == 0. Nothing to do.
                oindex = osub.getInt(oindex + QUEUE_NEXT);
            }
            queue_tail = lindex;
            if (lindex < 0)
                queue_head = -1;
            else
                asub.setInt(lindex + QUEUE_NEXT, -1);
        }
    }

    // SYNC: global
    void init() {
        sub = new SubMatrix(collector.mapper, has_time ? 6 : 4, 0, QUEUE_MARK, 0, 0, Hashing.MAX_SHIFT, stats);
        collector.mapper.incMaxCounter(collector.getScheme().getRecordCount());
    }

    // SYNC: global
    void close() {
        queue_head = -1; // don't notify listeners anymore
        if (sub != null) {
            sub.close();
            sub = null;
            collector.mapper.decMaxCounter(collector.getScheme().getRecordCount());
        }
    }

    // SYNC: none
    void notifyListenerIfQueued() {
        /*
           This is a weak check for queue_head. Its correctness is based on the fact, that
           queue should have became non-empty in the same thread that performs notification.
         */
        if (queue_head > 0)
            notifyListener();
    }

    // SYNC: none
    void notifyListener() {
        RecordListener listener = this.listener; // Volatile read.
        if (listener != null)
            try {
                listener.recordsAvailable(this);
            } catch (Throwable t) {
                collector.errorHandler.handleSubscriptionError(this, t);
            }
    }

    // ========== SubscriptionProvider Implementation ==========

    @Override
    public RecordMode getMode() {
        return mode;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // This method returns true if there are more subscription items to retrieve.
    // SYNC: none
    @Override
    public boolean retrieve(RecordSink sink) {
        try {
            return retrieveImpl(sink);
        } catch (final Throwable error) {
            collector.management.setFatalError(error);
            throw error;
        }
    }

    public boolean retrieveImpl(RecordSink sink) {
        if (sink != RecordSink.VOID)
            distributor.initDistributorProviders();
        return is_added_provider ? retrieveAddedSubscription(sink) : retrieveQueuedSubscription(sink);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // This method returns true if there are more subscription items to retrieve.
    // SYNC: none
    private boolean retrieveAddedSubscription(RecordSink sink) {
        // assert is_added_provider;
        boolean notify_removed = false;
        SubSnapshot snapshot = distributor.getSnapshot(); // Volatile read
        if (snapshot != null) {
            if (snapshot.retrieveSubscription(sink))
                return true; // has more subscription from snapshot
            // nothing more in snapshot
            // Note: we will retrieve pending subscription _after_ the snapshot is cleared, so we don't
            // have to notifyAdded, but we'll have to notifyRemoved later.
            distributor.clearSnapshot();
            notify_removed = true;
        }
        // will take global lock
        boolean has_more = retrieveQueuedSubscription(sink);
        // We have to notify removed if we had cleared snapshot, since something could have been added to it
        if (notify_removed)
            distributor.notifyRemoved();
        return has_more;
    }

    // SYNC: none
    private boolean retrieveQueuedSubscription(RecordSink sink) {
        if (!distributor.isActive() || distributor.getSnapshot() != null)
            return false; // bailout w/o taking global lock if already or not active or reading snapshot
        collector.globalLock.lock(CollectorOperation.RETRIEVE_SUBSCRIPTION);
        try {
            return retrieveQueuedSubscriptionGLocked(sink);
        } finally {
            collector.globalLock.unlock();
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // This method returns true if there are more subscription items to retrieve.
    // SYNC: global
    boolean retrieveQueuedSubscriptionGLocked(RecordSink sink) {
        // double-checked locking
        if (!distributor.isActive() || distributor.getSnapshot() != null)
            return false;
        SubMatrix sub = this.sub;
        if (sub == null)
            return false; // already closed, thus nothing to retrieve
        int index = queue_head;
        while (index > 0 && sink.hasCapacity()) {
            if (sub.getInt(index + QUEUE_MARK) != 0) {
                int key = sub.getInt(index);
                int rid = sub.getInt(index + 1);
                int cipher = key;
                String symbol = null;
                if ((key & SymbolCodec.VALID_CIPHER) == 0) {
                    if (key == 0)
                        throw new IllegalArgumentException("Undefined key.");
                    cipher = 0;
                    symbol = collector.mapper.getSymbol(key);
                }
                long time = 0;
                if (has_time)
                    time = sub.getLong(index + TIME_SUB);
                sink.visitRecord(collector.records[rid], cipher, symbol, time);

                sub.setInt(index + QUEUE_MARK, 0);
                sub.updateRemovedPayload(rid);
                if (has_time)
                    sub.setLong(index + TIME_SUB, 0);
            }
            int next = sub.getInt(index + QUEUE_NEXT);
            // remove from queue (also makes cell "removed")
            sub.setInt(index + QUEUE_NEXT, 0);
            sub.clearRemovedCellsTrail(index, QUEUE_NEXT);
            index = next;

            // Adjust first/last here to protect from exception in visitor.
            queue_head = index;
            if (index < 0)
                queue_tail = -1;
        }
        if (queue_head < 0) // postpone downsize rehashing until queue is empty
            rehashIfNeeded();
        return queue_head > 0;
    }

    // SYNC: none
    @Override
    public void setRecordListener(RecordListener listener) {
        if (this.listener == listener)
            return;
        /*
           We set listener even on closed distributors. However, closed distributor will not notify
           its listeners anyway, because of the checks in notifyListenerIfQueued method.
         */
        this.listener = listener;
        /*
           Always notify new added listener. If this listener is non-void and calls,
           then it forces initialization of data provider. Void listeners retrieve data into
           void data buffer, thus not triggering initialization and saving resources.
         */
        if (is_added_provider)
            notifyListener();
    }

    // ========== Debugging ==========

    void visitProviderSymbols(CollectorDebug.SymbolReferenceVisitor srv, CollectorDebug.SymbolReferenceLocation srl) {
        if (sub != null)
            CollectorDebug.visitSubMatrixSymbols(srv, null, sub, srl);
    }

    @Override
    public String toString() {
        return "SubProvier(" + (is_added_provider ? "added" : "removed") + ") of " + distributor;
    }
}
