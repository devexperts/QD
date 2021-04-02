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

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

class LockBoundTaskQueue implements Runnable {
    /**
     * {@code true} when task is scheduled to run or is current running.
     */
    private boolean scheduled;

    /**
     * Pairs of {@link Executor} and {@link Runnable}. When running it starts with {@link Executor}, when
     * not running (but scheduled), it start with next {@link Runnable} to execute.
     */
    private ArrayDeque<Object> queue;

    synchronized void add(Executor executor, Runnable task) {
        if (queue == null)
            queue = new ArrayDeque<>();
        queue.add(executor);
        queue.add(task);
        if (!scheduled) {
            scheduled = true;
            schedule();
        }
    }

    @Override
    public void run() {
        try {
            poll().run();
        } finally {
            finish();
        }
    }

    private synchronized Runnable poll() {
        assert scheduled;
        return (Runnable) queue.poll();
    }

    private synchronized void finish() {
        assert scheduled;
        if (queue.isEmpty())
            scheduled = false;
        else
            schedule();
    }

    private void schedule() {
        ((Executor) queue.poll()).execute(this);
    }
}
