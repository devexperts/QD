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

import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;

/**
 * Manages the state of the current thread with respect to QD global and local locks.
*/
final class LockedThreadState {
    static final int LOCK_NONE = 0;
    static final int LOCK_GLOBAL = 1;
    static final int LOCK_LOCAL = 2;
    static final int LOCK_GLOBAL_AND_LOCAL = LOCK_GLOBAL + LOCK_LOCAL;

    private int lock_level;

    // counters are only collected when management.useLockCounters(op) is true
    private CollectorOperation op;
    private long enterNanos;
    private long acquiredNanos;

    void checkLevel(int desired_level) {
        if (lock_level >= desired_level)
            throw new IllegalStateException("QD lock is already being held by this thread. " +
                "Do not reenter QD from inside of data or subscription visitors.");
    }

    void makeAcquired(int desired_level) {
        lock_level += desired_level;
    }

    void makeReleased(int desired_level) {
        lock_level -= desired_level;
    }

    public void countEnterGlobal(CollectorOperation op) {
        this.op = op;
        enterNanos = System.nanoTime();
    }

    void countAcquiredUncontendedGlobal() {
        acquiredNanos = enterNanos;
    }

    void countAcquiredContendedGlobal() {
        acquiredNanos = System.nanoTime();
    }

    void updateCountersGlobal(CollectorCounters counters) {
        if (op == null)
            return;
        long releaseNanos = System.nanoTime();
        counters.countLock(op, acquiredNanos - enterNanos, releaseNanos - acquiredNanos);
        op = null;
    }

}
