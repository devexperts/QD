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

import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Specialized exlusive non-renetrant lock for QD agent-local data strucutres.
 */
final class LocalLock {
    private final GlobalLock global_lock;
    private final CollectorManagement management;
    private final Sync sync = new Sync();
    private Sync.ConditionObject condition; // initialized under lock on first need
    private Thread last_owner;
    private CollectorOperation last_op;

    LocalLock(GlobalLock global_lock, CollectorManagement management) {
        this.global_lock = global_lock;
        this.management = management;
    }

    boolean tryLock(CollectorOperation op) {
        LockedThreadState state = global_lock.getLockedThreadState();
        state.checkLevel(LockedThreadState.LOCK_LOCAL);
        boolean success = sync.tryAcquire(0);
        if (success)
            makeAcquired(state, op);
        return success;
    }

    void lock(CollectorOperation op) {
        LockedThreadState state = global_lock.getLockedThreadState();
        state.checkLevel(LockedThreadState.LOCK_LOCAL);
        boolean interrupted = false;
        while (true) {
            try {
                if (sync.tryAcquireNanos(0, management.getLockWaitLogIntervalNanos()))
                    break;
                global_lock.warnTooLong("local", op, this.last_owner, last_op);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
        makeAcquired(state, op);
    }

    private void makeAcquired(LockedThreadState state, CollectorOperation op) {
        last_owner = Thread.currentThread();
        last_op = op;
        state.makeAcquired(LockedThreadState.LOCK_LOCAL);
    }

    void unlock() {
        sync.release(0);
        LockedThreadState state = global_lock.getLockedThreadState();
        state.makeReleased(LockedThreadState.LOCK_LOCAL);
    }

    void await() throws InterruptedException {
        condition().await();
    }

    void signalAll() {
        condition().signalAll();
    }

    private Sync.ConditionObject condition() {
        if (condition == null)
            condition = sync.new ConditionObject();
        return condition;
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync() {}

        protected boolean tryAcquire(int arg) {
            if (getState() != 0)
                return false;
            // non-fair acquire
            return compareAndSetState(0, 1);
        }

        protected boolean tryRelease(int releases) {
            // paranoia
            if (getState() == 0)
                throw new IllegalStateException("Not locked!!!");
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return true;
        }
    }
}
