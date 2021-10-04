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
package com.dxfeed.webservice.comet;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class DelayableExecutor implements Executor {
    private final Supplier<Executor> executorSupplier;

    /**
     * Modified only by synchronized methods so needs not to be additionally thread-safe.
     */
    private final Queue<Runnable> delayedTasks = new ArrayDeque<>();

    private boolean delayProcessing;

    public DelayableExecutor(Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
    }

    @Override
    public synchronized void execute(Runnable task) {
        delayedTasks.add(task);
        if (!delayProcessing) {
            flush();
        }
    }

    synchronized void setDelayProcessing(boolean delayProcessing) {
        this.delayProcessing = delayProcessing;
        if (!delayProcessing) {
            flush();
        }
    }

    private void flush() {
        while (!delayedTasks.isEmpty()) {
            executorSupplier.get().execute(delayedTasks.remove());
        }
    }
}