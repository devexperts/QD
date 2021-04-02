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

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.ng.RecordSource;

import java.util.concurrent.Executor;

/**
 * This class is base class for listening QD for data availability and rescheduling its processing using {@link Executor}.
 * @deprecated Use {@link RecordProcessor}.
 */
public abstract class DataProcessor extends RecordProcessor {
    /**
     * Constructs new data processor for the specified executor.
     * @throws NullPointerException if executor is null.
     */
    protected DataProcessor(Executor executor) {
        super(executor);
    }

    /**
     * Method for processing incoming data from QD.
     * When data is received, <code>DataProcessor</code> calls this method in <code>Executor</code> thread.
     */
    protected abstract void processData(RecordSource source);

    @Override
    protected final void process(RecordSource source) {
        processData(source);
    }

    /**
     * Starts processing for the specified {@link DataProvider}.
     * This method may be called at most once after construction.
     * @throws NullPointerException if {@code provider} is null.
     * @throws IllegalStateException if called multiple times. It is checked weakly to ensure fail-fast error detection
     *     behaviour. There is no strict thread-safe guarantee for this exception to be thrown if this method is called
     *     concurrently from multiple threads.
     */
    public void startProcessing(DataProvider provider) {
        startProcessing(LegacyAdapter.of(provider));
    }
}
