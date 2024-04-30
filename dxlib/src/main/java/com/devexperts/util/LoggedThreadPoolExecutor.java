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
package com.devexperts.util;

import com.devexperts.logging.Logging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@code ScheduledThreadPoolExecutor} that additionally logs uncaught exceptions
 * and creates threads named with a given name prefix.
 */
public class LoggedThreadPoolExecutor extends ScheduledThreadPoolExecutor {
    private final Logging log;

    /**
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given initial parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *                     if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param name prefix for thread names.
     * @param log to print uncaught exceptions.
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     */
    public LoggedThreadPoolExecutor(int corePoolSize, String name, Logging log) {
        super(corePoolSize, new PoolThreadFactory(name));
        this.log = log;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null && r instanceof Future<?> && !((Future<?>) r).isCancelled()) {
            try {
                ((Future<?>) r).get();
            } catch (ExecutionException e) {
                t = e.getCause();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt(); // ignore/reset
            }
        }
        if (t != null) {
            log.error("Uncaught exception", t);
        }
    }

    private static class PoolThreadFactory implements ThreadFactory {
        private final String name;
        private final AtomicInteger index = new AtomicInteger();
        private final ThreadGroup group;

        {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
        }

        PoolThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(group, r, name + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
