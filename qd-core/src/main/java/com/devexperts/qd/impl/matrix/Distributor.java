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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The <code>Distributor</code> is a matrix-based implementation of {@link QDDistributor}.
 */
final class Distributor extends AbstractDistributor {
    private static final int STATE_NEW = 0;
    private static final int STATE_ACTIVE = 1;
    private static final int STATE_CLOSED = 2;

    final Collector collector;
    private final int number;
    final QDFilter filter; // @NotNull
    private final QDStats stats;

    private final SubProvider added_provider; // SYNC: write(global), read(none)
    private final SubProvider removed_provider;  // SYNC: write(global), read(none)

    private volatile SubSnapshot snapshot; // != null during initialization of providers; SYNC: write(global), read(none)

    private final AtomicInteger state = new AtomicInteger(STATE_NEW);

    // SYNC: none
    Distributor(Collector collector, int number, QDFilter filter, QDStats stats) {
        this.collector = collector;
        this.number = number;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.stats = stats;
        this.added_provider = new SubProvider(this, true, stats.create(QDStats.SType.DISTRIBUTOR_ASUB));
        this.removed_provider = new SubProvider(this, false, stats.create(QDStats.SType.DISTRIBUTOR_RSUB));
    }

    // SYNC: none
    void initDistributorProviders() {
        if (state.get() != STATE_NEW)
            return; // bailout w/o global lock if already initialized or closed
        collector.globalLock.lock(CollectorOperation.INIT_DISTRIBUTOR);
        try {
            initDistributorProvidersGLocked();
        } finally {
            collector.globalLock.unlock();
        }
    }

    // SYNC: global
    private void initDistributorProvidersGLocked() {
        // atomically move NEW->ACTIVE state or exit
        if (!state.compareAndSet(STATE_NEW, STATE_ACTIVE))
            return; // was already initialized by initDistributorProviders or closed
        /*
             We are already holding global lock here, so the below methods will always complete
             before closeDistributorProvidersGLocked is invoked from close method.
         */
        added_provider.init();
        removed_provider.init();
        snapshot = new SubSnapshot(collector.total, filter);
    }

    // SYNC: none
    private void closeDistributorProviders() {
        collector.globalLock.lock(CollectorOperation.CLOSE_DISTRIBUTOR);
        try {
            closeDistributorProvidersGLocked();
        } finally {
            collector.globalLock.unlock();
        }
    }

    // SYNC: global
    private void closeDistributorProvidersGLocked() {
        added_provider.close();
        removed_provider.close();
        clearSnapshot();
    }

    // SYNC: none
    public QDStats getStats() {
        return stats;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    boolean addSub(int key, int rid, long time) {
        if (state.get() != STATE_ACTIVE)
            return false; // not initialized -- don't do anything
        if (filter.accept(collector.getContract(), collector.records[rid], collector.getCipher(key), collector.getSymbol(key))) {
            removed_provider.remove(key, rid);
            return added_provider.add(key, rid, time);
        }
        return false;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    boolean removeSub(int key, int rid) {
        if (state.get() != STATE_ACTIVE)
            return false; // not initialized -- don't do anything
        if (filter.accept(collector.getContract(), collector.records[rid], collector.getCipher(key), collector.getSymbol(key))) {
            added_provider.remove(key, rid);
            return removed_provider.add(key, rid, 0);
        }
        return false;
    }

    // SYNC: none
    boolean isActive() {
        return state.get() == STATE_ACTIVE;
    }

    // SYNC: none
    void notifyAdded() {
        // notification is suppressed while snapshot retrieval is in progress
        if (snapshot == null)
            added_provider.notifyListenerIfQueued();
    }

    // SYNC: none
    void notifyRemoved() {
        // notification is suppressed while snapshot retrieval is in progress
        if (snapshot == null)
            removed_provider.notifyListenerIfQueued();
    }

    // SYNC: none
    SubSnapshot getSnapshot() {
        return snapshot;
    }

    // SYNC: none
    void clearSnapshot() {
        snapshot = null;
    }

    // ========== QDDistributor Implementation ==========

    // SYNC: none
    public void process(RecordSource source) {
        collector.processData(this, source);
    }

    // SYNC: none
    public RecordProvider getAddedRecordProvider() {
        return added_provider;
    }

    // SYNC: none
    public RecordProvider getRemovedRecordProvider() {
        return removed_provider;
    }

    // SYNC: none
    public void close() {
        // atomically move {NEW,ACTIVE}->CLOSED state or exit
        int prevState;
        do {
            prevState = state.get();
            if (prevState == STATE_CLOSED)
                return;
        } while (!state.compareAndSet(prevState, STATE_CLOSED));
        // At most one thread will be closing this distributor here
        collector.distributors.removeDistributorFromList(number);
        if (prevState == STATE_ACTIVE)
            closeDistributorProviders(); // we have to undo initDistributorProviders operation
        // we do it last, because it is the least controlled part and can throw exception
        added_provider.closeStats();
        removed_provider.closeStats();
        stats.close();
    }

    // ========== Debugging ==========


    @Override
    public String toString() {
        return "distributor #" + number +
            (stats != null ? " [" + stats.getFullKeyProperties() + "]" : "") +
            " of " + collector;
    }

    void visitDistributorSymbols(CollectorDebug.SymbolReferenceVisitor srv) {
        CollectorDebug.SymbolReferenceLocation srl = new CollectorDebug.SymbolReferenceLocation();
        srl.object = this;
        srl.added = true;
        if (added_provider != null) // support partially reconstructed dumps
            added_provider.visitProviderSymbols(srv, srl);
        srl.added = false;
        srl.removed = true;
        if (removed_provider != null)  // support partially reconstructed dumps
            removed_provider.visitProviderSymbols(srv, srl);
    }
}
