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
import com.devexperts.qd.util.LegacyAdapter;

/**
 * Bridge class that adapts {@link DataProvider} and {@link SubscriptionProvider} APIs
 * to {@link RecordProvider} API. All you have to do is to implement
 * {@link #getMode()} and {@link #retrieve(RecordSink)} methods and optionally override
 * {@link #setRecordListener(RecordListener)} method,
 * while all the methods of {@link DataProvider} and {@link SubscriptionProvider} interfaces are
 * already implemented and invoke the above method implementations.
 */
public abstract class AbstractRecordProvider implements RecordProvider {

    // ----------------------- public overrideable methods -----------------------

    /** {@inheritDoc} */
    @Override
    public abstract RecordMode getMode();

    /** {@inheritDoc} */
    @Override
    public abstract boolean retrieve(RecordSink sink);

    /**
     * {@inheritDoc}
     * <p>This implementation throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setRecordListener(RecordListener listener) {
        throw new UnsupportedOperationException();
    }

    // ----------------------- public final bridge methods -----------------------

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public final boolean retrieveData(final DataVisitor visitor) {
        return retrieve(LegacyAdapter.of(visitor));
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public final boolean retrieveSubscription(final SubscriptionVisitor visitor) {
        return retrieve(LegacyAdapter.of(visitor));
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #setRecordListener(RecordListener)}
     */
    @Override
    public final void setDataListener(final DataListener listener) {
        setRecordListener(LegacyAdapter.of(listener));
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #setRecordListener(RecordListener)}
     */
    @Override
    public final void setSubscriptionListener(final SubscriptionListener listener) {
        setRecordListener(LegacyAdapter.of(listener));
    }

}
