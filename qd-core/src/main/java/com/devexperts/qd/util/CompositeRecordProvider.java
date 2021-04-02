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

import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides an efficient implementation of composite {@link RecordProvider} that listens for multiple
 * {@code RecordProvider} instances and on {@link #retrieve retrieve} method invocation only retrieves
 * data from the providers that have data available.
 *
 * This class is thread-safe and lock-free. Change carefully.
 */
public final class CompositeRecordProvider extends AbstractRecordProvider {
    private final RecordMode mode;
    private final Handler[] handlers;
    private volatile RecordListener listener;
    private final AtomicBoolean notified = new AtomicBoolean();

    /**
     * Constructs an instance of {@code CompositeRecordProvider} for a given list of individual providers.
     */
    public CompositeRecordProvider(RecordProvider... providers) {
        mode = providers[0].getMode();
        handlers = new Handler[providers.length];
        for (int i = 0; i < providers.length; i++)
            handlers[i] = new Handler(providers[i]);
    }

    @Override
    public RecordMode getMode() {
        return mode;
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        notified.set(false);
        for (Handler handler : handlers)
            if (handler.retrieve(sink)) {
                notified.set(true);
                return true;
            }
        return false;
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        RecordListener old = this.listener;
        if (old == listener)
            return;
        this.listener = listener;
        notified.set(false);
        if (old != null)
            clearListeners();
        if (listener != null)
            setListeners();
    }

    private void setListeners() {
        for (Handler handler : handlers)
            handler.provider.setRecordListener(handler);
    }

    private void clearListeners() {
        for (Handler handler : handlers)
            handler.provider.setRecordListener(null);
    }

    private void notifyListenerIfNeeded() {
        if (notified.compareAndSet(false, true)) {
            RecordListener listener = this.listener; // atomic read
            if (listener != null)
                listener.recordsAvailable(this);
        }
    }

    private class Handler implements RecordListener {
        final RecordProvider provider;
        volatile boolean available;

        Handler(RecordProvider provider) {
            this.provider = provider;
        }

        public void recordsAvailable(RecordProvider provider) {
            available = true;
            notifyListenerIfNeeded();
        }

        boolean retrieve(RecordSink sink) {
            if (available) {
                available = false;
                if (provider.retrieve(sink)) {
                    available = true;
                    return true;
                }
            }
            return false;
        }
    }
}
