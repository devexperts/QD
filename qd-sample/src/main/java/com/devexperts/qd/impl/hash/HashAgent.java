/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.hash;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.impl.AbstractAgent;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import java.util.HashSet;
import java.util.Iterator;

/**
 * The <code>HashAgent</code> is a hash-based implementation of {@link QDAgent}.
 * It tracks consumer's subscription and accumulates incoming data for later retrieval.
 */
final class HashAgent extends AbstractAgent {

    private final HashTicker ticker;
    private final SubscriptionFilter filter;

    private final QDStats stats;
    private final QDStats stats_data;
    private final QDStats stats_sub;

    private final HashSet<RecordValue> subscription = new HashSet<>();

    private int max_changes_size;
    private HashSet<RecordValue> changes = new HashSet<>();

    private boolean closed;
    private RecordListener listener;

    HashAgent(HashTicker ticker, Builder builder, QDStats stats) {
        super(ticker.getContract(), builder);
        this.ticker = ticker;
        this.filter = builder.getFilter();

        this.stats = stats;
        this.stats_data = stats.create(QDStats.SType.AGENT_DATA);
        this.stats_sub = stats.create(QDStats.SType.AGENT_SUB);
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    private void checkRehash() {
        int size = changes.size();
        if (size < (max_changes_size >> 1) && max_changes_size > 8) {
            max_changes_size = size;
            HashSet<RecordValue> set = new HashSet<>();
            if (size > 0)
                set.addAll(changes);
            changes = set;
        }
    }

    void addSub(RecordValue value) {
        if (subscription.add(value)) {
            stats_sub.updateAdded(value.getRecord().getId());
        }
    }

    void removeSub(RecordValue value) {
        max_changes_size = Math.max(max_changes_size, changes.size());
        if (subscription.remove(value)) {
            stats_sub.updateRemoved(value.getRecord().getId());
        }
        if (changes.remove(value)) {
            stats_data.updateRemoved(value.getRecord().getId());
        }
    }

    boolean recordChanged(RecordValue value) {
        if (changes.add(value)) {
            stats_data.updateAdded(value.getRecord().getId());
            return changes.size() == 1;
        }
        stats_data.updateChanged(value.getRecord().getId());
        return false;
    }

    void notifyListener() {
        RecordListener listener = this.listener; // Atomic read.
        if (listener != null)
            try {
                listener.recordsAvailable(this);
            } catch (Throwable t) {
                ticker.error_handler.handleDataError(this, t);
            }
    }

    // ========== QDAgent Implementation ==========

    @Override
    public boolean retrieve(RecordSink sink) {
        synchronized (ticker) {
            if (changes.isEmpty())
                return false;
            max_changes_size = Math.max(max_changes_size, changes.size());
            RecordCursor.Owner temp = RecordCursor.allocateOwner();
            for (Iterator<RecordValue> it = changes.iterator(); it.hasNext() && sink.hasCapacity();) {
                RecordValue value = it.next();
                it.remove();
                stats_data.updateRemoved(value.getRecord().getId());
                sink.append(value.getData(temp, RecordMode.DATA));
            }
            checkRehash();
            return !changes.isEmpty();
        }
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        synchronized (ticker) {
            this.listener = listener;
            if (changes.isEmpty())
                return;
        }
        notifyListener();
    }

    @Override
    public void addSubscription(RecordSource source) {
        boolean added = false;
        boolean removed = false;
        boolean changed;
        synchronized (ticker) {
            if (closed)
                return;
            changed = changes.isEmpty();
            RecordCursor cur;
            while ((cur = source.next()) != null) {
                DataRecord record = cur.getRecord();
                if (EventFlag.REMOVE_SYMBOL.in(cur.getEventFlags())) {
                    // remove
                    if (ticker.removeSub(record, cur.getCipher(), cur.getSymbol(), this))
                        removed = true;
                } else {
                    // add
                    if (filter == null || filter.acceptRecord(record, cur.getCipher(), cur.getSymbol())) {
                        if (ticker.addSub(record, cur.getCipher(), cur.getSymbol(), this))
                            added = true;
                    } else
                        stats_sub.updateFiltered(record.getId());
                }
            }
            changed &= !changes.isEmpty();
        }
        if (added)
            ticker.notifyAdded();
        if (removed)
            ticker.notifyRemoved();
        if (changed)
            notifyListener();
    }

    @Override
    public void removeSubscription(RecordSource source) {
        boolean removed = false;
        synchronized (ticker) {
            if (closed)
                return;
            max_changes_size = Math.max(max_changes_size, changes.size());
            RecordCursor cur;
            while ((cur = source.next()) != null) {
                DataRecord record = cur.getRecord();
                if (ticker.removeSub(record, cur.getCipher(), cur.getSymbol(), this))
                    removed = true;
            }
            checkRehash();
        }
        if (removed)
            ticker.notifyRemoved();
    }

    @Override
    public void setSubscription(RecordSource source) {
        boolean added = false;
        boolean removed = false;
        boolean changed = false;
        synchronized (ticker) {
            HashSet<RecordValue> sub = new HashSet<>();
            if (!closed) {
                // The "add" section is skipped for closed agents.
                changed = changes.isEmpty();
                RecordCursor cur;
                while ((cur = source.next()) != null) {
                    DataRecord record = cur.getRecord();
                    if (filter == null || filter.acceptRecord(record, cur.getCipher(), cur.getSymbol())) {
                        if (ticker.addSub(record, cur.getCipher(), cur.getSymbol(), this))
                            added = true;
                        sub.add(ticker.getValue(record, cur.getCipher(), cur.getSymbol()));
                    } else
                        stats_sub.updateFiltered(record.getId());
                }
                changed &= !changes.isEmpty();
            }
            max_changes_size = Math.max(max_changes_size, changes.size());
            for (Iterator<RecordValue> it = subscription.iterator(); it.hasNext();) {
                RecordValue value = it.next();
                if (!sub.contains(value)) {
                    it.remove(); // Have to do it here to avoid ConcurrentModificationException.
                    stats_sub.updateRemoved(value.getRecord().getId());
                    if (ticker.removeSub(value.getRecord(), value.getCipher(), value.getSymbol(), this))
                        removed = true;
                }
            }
            checkRehash();
        }
        if (added)
            ticker.notifyAdded();
        if (removed)
            ticker.notifyRemoved();
        if (changed)
            notifyListener();
    }

    @Override
    public void close() {
        synchronized (ticker) {
            if (closed)
                return;
            closed = true;
        }
        // Clear subscription properly.
        // Do it outside synchronization because of notifications.
        setSubscription(null);

        stats_data.close();
        stats_sub.close();
        stats.close();
    }

    @Override
    public void closeAndExamineDataBySubscription(RecordSink sink) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return ticker.isSub(record, cipher, symbol, this);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return ticker.examineSnapshot(subscription, false, sink, RecordMode.SUBSCRIPTION);
    }

    @Override
    public int getSubscriptionSize() {
        return subscription.size();
    }
}
