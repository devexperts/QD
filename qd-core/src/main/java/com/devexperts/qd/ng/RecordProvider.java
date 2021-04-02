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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;

/**
 * An object that can notify about new records and let retrieve them.
 * This interface is replacing legacy interfaces {@link DataProvider} and {@link SubscriptionProvider}.
 */
public interface RecordProvider extends DataProvider, SubscriptionProvider {
    public static final RecordProvider VOID = new AbstractRecordProvider() {
        @Override
        public RecordMode getMode() {
            return RecordMode.DATA;
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            return false;
        }

        @Override
        public void setRecordListener(RecordListener listener) {
            // does nothing
        }
    };

    /**
     * Returns record mode at which the records are provided
     * by {@link #retrieve(RecordSink) retrieve} method.
     */
    public RecordMode getMode();

    /**
     * Retrieves accumulated records into the specified record sink
     * while the sink {@link RecordSink#hasCapacity() has capacity}.
     * Returns <code>true</code> if some records still remains in the provider
     * or <code>false</code> if all accumulated records were retrieved.
     * @param sink the sink.
     * @return {@code true}  if some records still remains in the provider,
     *         {@code false} if all accumulated records were retrieved.
     */
    public boolean retrieve(RecordSink sink);

    /**
     * Sets new record listener to receive notifications about available records.
     * Only one listener at a time is supported; the former listener is discarded.
     * Use <code>null</code> to set empty data listener (no notifications).
     *
     * <p><b>NOTE:</b> if there are accumulated data available, then specified
     * listener will be notified by this method.
     * @param listener the listener.
     */
    public void setRecordListener(RecordListener listener);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public boolean retrieveData(DataVisitor visitor);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public boolean retrieveSubscription(SubscriptionVisitor visitor);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #setRecordListener(RecordListener)}
     */
    @Override
    public void setDataListener(DataListener listener);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #setRecordListener(RecordListener)}
     */
    @Override
    public void setSubscriptionListener(SubscriptionListener listener);
}
