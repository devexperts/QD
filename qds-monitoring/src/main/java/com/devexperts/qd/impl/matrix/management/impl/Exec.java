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
package com.devexperts.qd.impl.matrix.management.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Exec {
    /**
     * Save executor for long-running collector management operations that creates at most 1 thread, kills it when it
     * is no longer needed and limits the number of tasks in queue.
     */
    public static final Executor EXEC = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10),
        new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "CollectorManagement");
                t.setDaemon(true);
                return t;
            }
        }, new ThreadPoolExecutor.AbortPolicy());
}
