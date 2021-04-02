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
package com.devexperts.util;

import com.devexperts.logging.Logging;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Manages access to a single automatically-created {@link Executor} from a multiple references.
 * {@link LoggedThreadPoolExecutor} is created on the first get via reference
 * {@link Reference#getOrCreateExecutor() getOrCreateExecutor}
 * unless an non-default executor was set with that reference using
 * {@link ExecutorProvider.Reference#setExecutor(Executor) setExecutor}, and is released when no longer needed.
 *
 * <p>The {@code ExecutorProvider} itself is a light-weight object, whose creation, by itself, does not create any
 *  threads.
 */
public class ExecutorProvider {
    private static final int AUTO_THREADS = -1;
    private static final int FIXED_EXECUTOR_THREADS = 0;
    private static final int MIN_THREADS = 1;

    private final int nThreads;
    private final String name;
    private final Logging log;

    // Pointer to the executor created by this provide, which must be ultimately destroyed.
    private Executor createdExecutor;
    // Number of active reference to the createdExecutor.
    private int refCount;

    /**
     * Creates executor provider that creates {@link LoggedThreadPoolExecutor} with a default number of threads
     * with a given name and log file when needed.
     * The executor is shut down when there are no {@link Reference references} that are using it.
     */
    public ExecutorProvider(String name, Logging log) {
        this.log = log;
        nThreads = AUTO_THREADS;
        this.name = name;
    }

    /**
     * Creates executor provider that creates {@link LoggedThreadPoolExecutor} with a specified number of threads,
     * a given name and log file when needed.
     * The executor is shut down when there are no {@link Reference references} that are using it.
     */
    public ExecutorProvider(int nThreads, String name, Logging log) {
        this.nThreads = Math.max(MIN_THREADS, nThreads);
        this.name = name;
        this.log = log;
    }

    /**
     * Creates executor provider that wraps a given (specified) executor that never shuts it down.
     */
    public ExecutorProvider(Executor executor) {
        nThreads = FIXED_EXECUTOR_THREADS;
        name = null;
        log = null;
        createdExecutor = executor;
    }

    public Reference newReference() {
        return new Reference();
    }

    private synchronized Executor useReference() {
        if (refCount++ == 0 && nThreads != FIXED_EXECUTOR_THREADS) {
            int nThreads = this.nThreads == AUTO_THREADS ? Math.max(MIN_THREADS, Runtime.getRuntime().availableProcessors()) : this.nThreads;
            createdExecutor = new LoggedThreadPoolExecutor(nThreads, name, log);
        }
        return createdExecutor;
    }

    private synchronized void releaseReference(Executor executor) {
        log.trace("Release reference for " + executor + ". CreatedExecutor = " + createdExecutor + ", refCount = " + refCount);
        if (executor == null || executor != createdExecutor)
            return;
        assert refCount > 0;
        if (--refCount == 0 && nThreads != FIXED_EXECUTOR_THREADS) {
            ((ExecutorService) createdExecutor).shutdown();
            createdExecutor = null;
        }
    }

    /**
     * Writable reference to {@link Executor} that is created using provider if not explicitly specified.
     */
    public class Reference  {
        private volatile Executor executor;
        private boolean closed;

        private Reference() {}

        public Executor getOrCreateExecutor() {
            Executor executor = this.executor;
            if (executor != null)
                return executor;
            return getOrCreateExecutorSync();
        }

        private synchronized Executor getOrCreateExecutorSync() {
            if (executor != null)
                return executor;
            if (!closed)
                executor = useReference();
            return executor;
        }

        public synchronized void setExecutor(Executor executor) {
            if (executor == null)
                throw new NullPointerException();
            if (executor == this.executor)
                return;
            if (!closed)
                releaseReference(this.executor);
            this.executor = executor;
        }

        public synchronized void close() {
            if (!closed) {
                releaseReference(this.executor);
                closed = true;
            }
        }
    }
}
