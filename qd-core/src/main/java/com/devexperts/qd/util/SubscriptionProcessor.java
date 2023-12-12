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
package com.devexperts.qd.util;

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is base class for listening QD for subscription availability and rescheduling its processing using {@link Executor}.
 * Classes which extends {@code SubscriptionProcessor} listen for subscription coming from QD and process incoming subscription
 * in separate thread or threads.
 *
 * {@code Executor} may be single thread or thread pool executor. One Executor may be shared by different subscription processors.
 * It is guaranteed that new subscription processing handler will not be called until previous has completed its work.
 *
 * {@code SubscriptionProcessor} registers {@link RecordListener} in all {@link RecordProvider} instances when
 * {@link #startProcessing} method is called.
 * Class client is responsible to call {@link #stopProcessing} when processing is not required any more.
 *
 * This class is thread-safe and lock-free. Change carefully.
 */
public abstract class SubscriptionProcessor {
    private static final boolean TRACE_LOG = SubscriptionProcessor.class.desiredAssertionStatus();

    private static final Logging log = Logging.getLogging(SubscriptionProcessor.class);

    private final Executor executor;
    private final QDContract contract;
    private final SubscriptionHandler addedHandler = new SubscriptionHandler();
    private final SubscriptionHandler removedHandler = new SubscriptionHandler();
    private final AtomicBoolean taskScheduled = new AtomicBoolean(false);

    /**
     * Constructs new subscription processor for the specified executor.
     *
     * @param executor the executor.
     * @param contract the contract for which subscription shall be processed.
     * @throws NullPointerException if executor is null.
     */
    protected SubscriptionProcessor(Executor executor, QDContract contract) {
        if (executor == null)
            throw new NullPointerException("executor is null");
        this.executor = executor;
        this.contract = contract;
    }

    /**
     * Method for processing incoming added subscription from QD.
     * When added subscription is received, {@code SubscriptionProcessor} calls this method in {@code Executor} thread.
     */
    protected abstract void processAddedSubscription(RecordSource source);

    /**
     * Method for processing incoming removed subscription from QD.
     * When removed subscription is received, {@code SubscriptionProcessor} calls this method in {@code Executor} thread.
     */
    protected abstract void processRemovedSubscription(RecordSource source);

    /**
     * Starts subscription processing for the specified {@link QDDistributor}.
     * This method is equivalent to the following code:
     * {@code startProcessing(distributor.getAddedRecordProvider(), distributor.getRemovedRecordProvider())}.
     * This method or {@link #startProcessing(RecordProvider, RecordProvider)} may be called at most once after construction.
     * @throws NullPointerException if distributor is null.
     * @throws IllegalStateException if called multiple times. It is checked weakly to ensure fail-fast error detection
     *     behaviour. There is no strict thread-safe guarantee for this exception to be thrown if this method is called
     *     concurrently from multiple threads.
     */
    public void startProcessing(QDDistributor distributor) {
        startProcessing(distributor.getAddedRecordProvider(), distributor.getRemovedRecordProvider());
    }

    /**
     * Starts subscription processing for the specified added and removed {@link RecordProvider} instances.
     * This method accepts {@code null} for one of the providers.
     * This method or {@link #startProcessing(QDDistributor)} may be called at most once after construction.
     * @throws NullPointerException if both {@link RecordProvider} arguments are {@code null}.
     * @throws IllegalStateException if called multiple times. It is checked weakly to ensure fail-fast error detection
     *     behaviour. There is no strict thread-safe guarantee for this exception to be thrown if this method is called
     *     concurrently from multiple threads.
     */
    public void startProcessing(RecordProvider addedSubscriptionProvider, RecordProvider removedSubscriptionProvider) {
        if (addedSubscriptionProvider == null && removedSubscriptionProvider == null)
            throw new NullPointerException("Both subscription providers are null");
        if (addedHandler.provider != null || removedHandler.provider != null)
            throw new IllegalStateException("startProcessing was already called");
        addedHandler.provider = addedSubscriptionProvider;
        removedHandler.provider = removedSubscriptionProvider;
        addedHandler.setListener(addedHandler);
        removedHandler.setListener(removedHandler);
    }

    /**
     * Stop subscription processing. Unregisters {@link RecordProvider} instances from {@link RecordProvider} instances.
     * Note: client code is responsible for disposing SubscriptionProviders originally passed to SubscriptionProcessor.
     * @throws IllegalStateException if called before {@link #startProcessing(QDDistributor)} or
     *         {@link #startProcessing(RecordProvider, RecordProvider)}.
     */
    public void stopProcessing() {
        if (addedHandler.provider == null && removedHandler.provider == null)
            throw new IllegalStateException("startProcessing was not called");
        addedHandler.setListener(null);
        removedHandler.setListener(null);
    }

    /**
     * Returns true when this SubscriptionProcessor has more subscription available to process or its
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
        executor.execute(addedHandler);
    }

    private void scheduleTaskIfNeeded() {
        if (taskScheduled.compareAndSet(false, true))
            rescheduleTask();
    }

    private void executeTask() {
        boolean rescheduleTask = true; // Reschedule task if an exception was thrown.
        try {
            // INVARIANT: taskScheduled == true here
            // added subscription is contract-specific
            RecordBuffer buf = RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract));
            buf.setCapacityLimited(true); // retrieve up to buffer capacity only
            addedHandler.retrieve(buf);
            if (TRACE_LOG)
                log.trace("executeTask added size=" + buf.size());
            if (!buf.isEmpty()) {
                processAddedSubscription(buf);
                buf.clear();
            }
            buf.setMode(RecordMode.SUBSCRIPTION);
            removedHandler.retrieve(buf);
            if (TRACE_LOG)
                log.trace("executeTask removed size=" + buf.size());
            if (!buf.isEmpty())
                processRemovedSubscription(buf);
            buf.release();
            rescheduleTask = addedHandler.available || removedHandler.available;
        } finally {
            if (rescheduleTask)
                rescheduleTask();
            else {
                try {
                    signalNoMoreToProcess();
                } finally {
                    taskScheduled.set(false);
                    // Concurrent recordsAvailable notification might have happened - recheck flag
                    if (addedHandler.available || removedHandler.available)
                        scheduleTaskIfNeeded();
                }
            }
        }
    }

    private final class SubscriptionHandler implements RecordListener, Runnable {
        RecordProvider provider;
        volatile boolean available;

        SubscriptionHandler() {}

        void setListener(RecordListener listener) {
            if (provider != null)
                provider.setRecordListener(listener);
        }

        void retrieve(RecordBuffer buf) {
            if (!available)
                return;
            boolean more = true; // Consider subscription is still available if an exception was thrown.
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
            if (TRACE_LOG)
                log.trace("recordsAvailable from " + provider);
            available = true;
            scheduleTaskIfNeeded();
        }

        @Override
        public void run() {
            executeTask();
        }
    }
}
