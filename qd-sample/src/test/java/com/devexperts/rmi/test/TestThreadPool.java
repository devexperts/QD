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
package com.devexperts.rmi.test;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Thread pool for test that reports exception in the pool.
 */
public class TestThreadPool extends ThreadPoolExecutor {
    private final List<Throwable> exceptions;

    public TestThreadPool(int nThreads, String name, List<Throwable> exceptions) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), createThreadFactory(name));
        this.exceptions = exceptions;
    }

    @Nonnull
    private static ThreadFactory createThreadFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return r -> new Thread(r, name + "-" + counter.incrementAndGet());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        // We ignore RejectedExceptionException that can happen when endpoints are being shut down. They are Ok.
        if (t != null && !(t instanceof RejectedExecutionException))
            exceptions.add(t);
    }
}
