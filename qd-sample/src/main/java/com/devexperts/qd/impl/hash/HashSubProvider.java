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

import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

import java.util.HashSet;
import java.util.Iterator;

/**
 * The <code>HashSubProvider</code> is a hash-based implementation of {@link SubscriptionProvider}.
 * It accumulates subscription changes for later retrieval.
 */
final class HashSubProvider extends AbstractRecordProvider {
    private final HashTicker ticker;
    private final QDStats stats;

    private int max_changes_size;
    private HashSet<RecordValue> changes = new HashSet<>();

    private RecordListener listener;

    HashSubProvider(HashTicker ticker, QDStats stats) {
        this.ticker = ticker;
        this.stats = stats;
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

    boolean addSub(RecordValue value) {
        if (changes.add(value)) {
            stats.updateAdded(value.getRecord().getId());
            return changes.size() == 1;
        }
        stats.updateChanged(value.getRecord().getId());
        return false;
    }

    void removeSub(RecordValue value) {
        max_changes_size = Math.max(max_changes_size, changes.size());
        if (changes.remove(value)) {
            stats.updateRemoved(value.getRecord().getId());
        } else
            stats.updateFiltered(value.getRecord().getId());
    }

    void close() {
        max_changes_size = Math.max(max_changes_size, changes.size());
        if (!changes.isEmpty()) {
            for (RecordValue value : changes)
                stats.updateRemoved(value.getRecord().getId());
            changes.clear();
        }
        checkRehash();
        listener = null;

        stats.close();
    }

    void notifyListener() {
        RecordListener listener = this.listener; // Atomic read.
        if (listener != null)
            try {
                listener.recordsAvailable(this);
            } catch (Throwable t) {
                ticker.error_handler.handleSubscriptionError(this, t);
            }
    }

    // ========== SubscriptionProvider Implementation ==========


    @Override
    public RecordMode getMode() {
        return RecordMode.SUBSCRIPTION;
    }

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
                stats.updateRemoved(value.getRecord().getId());
                sink.append(value.getData(temp, RecordMode.SUBSCRIPTION));
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
}
