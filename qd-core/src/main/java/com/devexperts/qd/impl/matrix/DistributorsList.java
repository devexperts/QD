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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.AtomicArrays;

/**
 * This is thread-safe class that manages a list of distributors under its own lock, so that new distributors
 * can be created without acquiring {@link Collector#globalLock}.
 */
final class DistributorsList {
    private static final int INITIAL_DISTRIBUTORS_SIZE = 8;

    private volatile Distributor[] distributors = new Distributor[INITIAL_DISTRIBUTORS_SIZE];
    private int last_distributor_index;

    // SYNC: none
    synchronized QDDistributor createDistributor(Collector collector, QDDistributor.Builder builder) {
        Distributor[] dist = distributors; // Volatile read
        int i = last_distributor_index = ArrayUtil.findFreeIndex(dist, last_distributor_index, Collector.MIN_DISTRIBUTOR_INDEX);
        if (i >= dist.length)
            this.distributors = dist = ArrayUtil.grow(dist, 0); // Volatile write
        // [QD-509] Do not track per-record stats for anonymous agents and distributors (reduces memory consumption)
        QDStats distributorStats = collector.stats.create(QDStats.SType.DISTRIBUTOR,
            builder.getKeyProperties(), builder.getKeyProperties() != null);
        Distributor d = new Distributor(collector, i, builder.getFilter(), distributorStats);
        AtomicArrays.INSTANCE.setVolatileObj(dist, i, d);
        return d;
    }

    // SYNC: none
    synchronized void removeDistributorFromList(int number) {
        /*
           Must be synchronized because array is reallocated by createDistributor and without synchronization
           we can write null to the wrong array.
         */
        AtomicArrays.INSTANCE.setVolatileObj(distributors, number, null);
    }

    // SYNC: none
    void notifyAdded() {
        Distributor[] dist = distributors; // Volatile read.
        for (int i = dist.length; --i >= 0;) {
            Distributor d = AtomicArrays.INSTANCE.getVolatileObj(dist, i);
            if (d != null)
                d.notifyAdded();
        }
    }

    // SYNC: none
    void notifyRemoved() {
        Distributor[] dist = distributors; // Volatile read.
        for (int i = dist.length; --i >= 0;) {
            Distributor d = AtomicArrays.INSTANCE.getVolatileObj(dist, i);
            if (d != null)
                d.notifyRemoved();
        }
    }

    // SYNC: global
    boolean addSub(int key, int rid,long time) {
        Distributor[] dist = distributors; // Volatile read
        boolean result = false;
        for (int i = dist.length; --i >= 0;) {
            Distributor d = AtomicArrays.INSTANCE.getVolatileObj(dist, i);
            if (d != null && d.addSub(key, rid, time))
                result = true;
        }
        return result;
    }

    // SYNC: global
    boolean removeSub(int key, int rid) {
        Distributor[] dist = distributors; // Volatile read
        boolean result = false;
        for (int i = dist.length; --i >= 0;) {
            Distributor d = AtomicArrays.INSTANCE.getVolatileObj(dist, i);
            if (d != null && d.removeSub(key, rid))
                result = true;
        }
        return result;
    }

    // ========== Debugging ==========

    void visitDistributorsSymbols(CollectorDebug.SymbolReferenceVisitor srv) {
        for (Distributor distributor : distributors) {
            if (distributor != null)
                distributor.visitDistributorSymbols(srv);
        }
    }
}
