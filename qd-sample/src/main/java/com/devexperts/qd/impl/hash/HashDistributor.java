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

import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

/**
 * The <code>HashDistributor</code> is a hash-based implementation of {@link QDDistributor}.
 */
final class HashDistributor extends AbstractDistributor {
    private final HashTicker ticker;
    private final SubscriptionFilter filter;

    private final QDStats stats;

    private final HashSubProvider added_provider;
    private final HashSubProvider removed_provider;

    private boolean closed;

    HashDistributor(HashTicker ticker, SubscriptionFilter filter, QDStats stats) {
        this.ticker = ticker;
        this.filter = filter;

        this.stats = stats;
        this.added_provider = new HashSubProvider(ticker, stats.create(QDStats.SType.DISTRIBUTOR_ASUB));
        this.removed_provider = new HashSubProvider(ticker, stats.create(QDStats.SType.DISTRIBUTOR_RSUB));
    }

    public QDStats getStats() {
        return stats;
    }

    boolean addSub(RecordValue value) {
        if (filter == null || filter.acceptRecord(value.getRecord(), value.getCipher(), value.getSymbol())) {
            removed_provider.removeSub(value);
            return added_provider.addSub(value);
        }
        return false;
    }

    boolean removeSub(RecordValue value) {
        if (filter == null || filter.acceptRecord(value.getRecord(), value.getCipher(), value.getSymbol())) {
            added_provider.removeSub(value);
            return removed_provider.addSub(value);
        }
        return false;
    }

    void notifyAdded() {
        added_provider.notifyListener();
    }

    void notifyRemoved() {
        removed_provider.notifyListener();
    }

    // ========== QDDistributor Implementation ==========

    public void process(RecordSource source) {
        if (closed)
            return;
        ticker.process(source); // Do it unsynchronized because of notifications.
    }

    public RecordProvider getAddedRecordProvider() {
        return added_provider;
    }

    public RecordProvider getRemovedRecordProvider() {
        return removed_provider;
    }

    public void close() {
        synchronized (ticker) {
            if (closed)
                return;
            closed = true;
            ticker.closed(this);

            added_provider.close();
            removed_provider.close();
            stats.close();
        }
    }
}
