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
package com.devexperts.qd.util;

import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is base class for listening for records availability and rescheduling their processing using {@link Executor}.
 * Classes that extend {@code RecordProcessor} listen for incoming records and process them in
 * separate thread or threads.
 *
 * {@code Executor} may be single thread or thread pool executor. One {@code Executor} may be shared by different record processors.
 * It is guaranteed that new processing handler will not be called until previous has completed its work.
 *
 * {@code RecordProcessor} registers {@link RecordListener} in {@link RecordProvider} when
 * {@link #startProcessing(RecordProvider)} startProcessing} method is called.
 * Class client is responsible to call {@link #stopProcessing() stopProcessing} when processing is not required any more.
 *
 * This class is thread-safe and lock-free. Change carefully.
 */
public abstract class RecordProcessor {
    private final Executor executor;
    private final RecordHandler handler = new RecordHandler();
    private final AtomicBoolean taskScheduled = new AtomicBoolean(false);

    /**
     * Constructs new record processor for the specified executor.
     * @throws NullPointerException if executor is null.
     */
    protected RecordProcessor(Executor executor) {
        if (executor == null)
            throw new NullPointerException("executor is null");
        this.executor = executor;
    }

    /**
     * Method for processing incoming records.
     * When record is received, <code>RecordProcessor</code> calls this method in <code>Executor</code> thread.
     */
    protected abstract void process(RecordSource source);

    /**
     * Starts processing for the specified {@link RecordProvider}.
     * This method may be called at most once after construction.
     * @throws NullPointerException if {@code provider} is null.
     * @throws IllegalStateException if called multiple times. It is checked weakly to ensure fail-fast error detection
     *     behaviour. There is no strict thread-safe guarantee for this exception to be thrown if this method is called
     *     concurrently from multiple threads.
     */
    public void startProcessing(RecordProvider provider) {
        if (provider == null)
            throw new NullPointerException("provider is null");
        if (handler.provider != null)
            throw new IllegalStateException("startProcessing was already called");
        handler.provider = provider;
        provider.setRecordListener(handler);
    }

    /**
     * Stops processing. Unregisters <code>RecordListener</code> on <code>RecordProvider</code>.
     * This method may be called only after {@link #startProcessing(RecordProvider)}.
     * Note: client code is responsible for disposing RecordProvider originally passed to RecordProcessor.
     * @throws IllegalStateException if called before {@link #startProcessing(RecordProvider)}.
     */
    public void stopProcessing() {
        if (handler.provider == null)
            throw new IllegalStateException("startProcessing was not called");
        handler.provider.setRecordListener(null);
    }

    /**
     * Returns true when this RecordProcessor has more records available to process or its
     * task is scheduled for processing.
     */
    public boolean hasMoreToProcess() {
        return taskScheduled.get();
    }

    /**
     * This method is called immediately before {@link #hasMoreToProcess()} becomes false.
     */
    protected void signalNoMoreToProcess() {}

    // ========== Implementation ==========

    private void rescheduleTask() {
        executor.execute(handler);
    }

    private void scheduleTaskIfNeeded() {
        if (taskScheduled.compareAndSet(false, true))
            rescheduleTask();
    }

    private void executeTask() {
        boolean rescheduleTask = true; // Reschedule task if an exception was thrown.
        try {
            // INVARIANT: taskScheduled == true here
            RecordBuffer buf = RecordBuffer.getInstance(handler.provider.getMode());
            buf.setCapacityLimited(true); // retrieve up to buffer capacity only
            handler.retrieve(buf);
            if (!buf.isEmpty())
                process(buf);
            buf.release();
            rescheduleTask = handler.available;
        } finally {
            if (rescheduleTask)
                rescheduleTask();
            else {
                try {
                    signalNoMoreToProcess();
                } finally {
                    taskScheduled.set(false);
                    // Concurrent recordsAvailable notification might have happened - recheck flag
                    if (handler.available)
                        scheduleTaskIfNeeded();
                }
            }
        }
    }

    private final class RecordHandler implements RecordListener, Runnable {
        RecordProvider provider;
        volatile boolean available;

        RecordHandler() {}

        void retrieve(RecordBuffer buf) {
            if (!available)
                return;
            boolean more = true; // Consider data is still available if an exception was thrown.
            try {
                available = false;
                more = provider.retrieve(buf);
            } finally {
                if (more)
                    available = true;
            }
        }

        @Override
        public void recordsAvailable(RecordProvider provider) {
            available = true;
            scheduleTaskIfNeeded();
        }

        @Override
        public void run() {
            executeTask();
        }
    }
}
