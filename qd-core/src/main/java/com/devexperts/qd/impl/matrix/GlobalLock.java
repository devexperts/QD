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
package com.devexperts.qd.impl.matrix;

import com.devexperts.logging.Logging;
import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.util.SystemProperties;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Specialized exclusive nonreentrant lock for QD global data structures
 * that supports management and "priority" lockers.
 */
final class GlobalLock {
    private static final boolean TRACE_LOG = GlobalLock.class.desiredAssertionStatus();

    private static final Logging log = Logging.getLogging(GlobalLock.class);

    // ability to statically turn off all fancy features, so that they will not even be compiled by HotSpot
    private static final boolean MANAGED = SystemProperties.getBooleanProperty(GlobalLock.class, "Managed", true);

    private final CollectorManagement management;
    private final CollectorCounters counters;
    private final RecordCursorKeeper keeper; // auto-clear on reset

    private final Sync sync = new Sync();

    /*
     * last_owner and last_op are read with a data race for debugging purposes only
     * (to report who holds the lock and for what reason, if entering the lock took too long).
     */
    private Thread last_owner;
    private CollectorOperation last_op;

    private final ThreadLocal<LockedThreadState> state = new ThreadLocal<LockedThreadState>();

    GlobalLock(CollectorManagement management, CollectorCounters counters, RecordCursorKeeper keeper) {
        this.management = management;
        this.counters = counters;
        this.keeper = keeper;
    }

    LocalLock newLocalLock() {
        return new LocalLock(this, management);
    }

    LockedThreadState getLockedThreadState() {
        LockedThreadState lts = state.get();
        if (lts == null)
            state.set(lts = new LockedThreadState());
        return lts;
    }

    void lock(CollectorOperation op) {
        LockedThreadState state = getLockedThreadState();
        state.checkLevel(LockedThreadState.LOCK_GLOBAL);
        int arg = MANAGED && !management.useLockPriority(op) ? 0 : 1;
        if (MANAGED && management.useLockCounters(op)) {
            state.countEnterGlobal(op);
            if (sync.tryAcquire(arg))
                state.countAcquiredUncontendedGlobal(); // acquires without contention
            else {
                acquireContended(arg, op);
                state.countAcquiredContendedGlobal();
            }
        } else
            sync.acquire(arg);
        last_owner = Thread.currentThread();
        last_op = op;
        state.makeAcquired(LockedThreadState.LOCK_GLOBAL);
        if (TRACE_LOG)
            log.trace(management.getContract() + " global lock locked for " + op);
    }

    private void acquireContended(int arg, CollectorOperation op) {
        boolean interrupted = false;
        while (true) {
            try {
                if (sync.tryAcquireNanos(arg, management.getLockWaitLogIntervalNanos()))
                    break;
                /*
                 * Racy read below. Its correctness is based on the fact, that attempt to
                 * acquire a lock will synchronize with some thread that was holding a lock,
                 * so last_owner and last_op will refer to some thread and operation that was
                 * interfering with acquisition of this lock.
                 */
                warnTooLong("global", op, last_owner, last_op);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    void unlock() {
        if (TRACE_LOG)
            log.trace(management.getContract() + " global lock unlocking after " + last_op);
        keeper.reset();
        sync.release(0);
        LockedThreadState state = getLockedThreadState();
        state.makeReleased(LockedThreadState.LOCK_GLOBAL);
        if (MANAGED)
            state.updateCountersGlobal(counters);
    }

    void warnTooLong(String type, CollectorOperation op, Thread lastOwner, CollectorOperation last_op) {
        Exception exception = null;
        if (lastOwner != null) {
            exception = new Exception("Last owner thread was " + lastOwner.getName() + ". Current stack trace is");
            exception.setStackTrace(lastOwner.getStackTrace());
        }
        log.warn(
            String.format("%s %s lock is taking too long to acquire for %s operation. Last operation was %s.",
            management.getContract(), type, op, last_op),
            exception);
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync() {}

        @Override
        protected boolean tryAcquire(int arg) {
            if (getState() != 0)
                return false;
            if (arg != 0) {
                // priority acquire
                if (compareAndSetState(0, 1))
                    return true;
            } else {
                // non-priority (fair) acquire
                Thread first = getFirstQueuedThread();
                if ((first == null || first == Thread.currentThread()) && compareAndSetState(0, 1))
                    return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int releases) {
            // paranoia
            if (getState() == 0)
                throw new IllegalStateException("Not locked!!!");
            setState(0);
            return true;
        }
    }
}
