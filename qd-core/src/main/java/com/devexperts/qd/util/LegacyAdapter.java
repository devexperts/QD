/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.util;

import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.Deprecation;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;

/**
 * This class contains static methods to adapt legacy interface of {@link com.devexperts.qd} package to
 * NG interfaces defined this package.
 */
public class LegacyAdapter {

    private static final Deprecation LEGACY_DATA_VISITOR =
        Deprecation.ofImpl(DataVisitor.class, "Use AbstractRecordSink instead.");
    private static final Deprecation LEGACY_SUBSCRIPTION_VISITOR =
        Deprecation.ofImpl(SubscriptionVisitor.class, "Use AbstractRecordSink instead.");
    private static final Deprecation LEGACY_DATA_PROVIDER =
        Deprecation.ofImpl(DataProvider.class, "Use AbstractRecordProvider instead.");
    private static final Deprecation LEGACY_SUBSCRIPTION_PROVIDER =
        Deprecation.ofImpl(SubscriptionProvider.class, "Use AbstractRecordProvider instead.");
    private static final Deprecation LEGACY_DATA_LISTENER =
        Deprecation.ofImpl(DataListener.class, "Implement RecordListener instead.");
    private static final Deprecation LEGACY_SUBSCRIPTION_LISTENER =
        Deprecation.ofImpl(SubscriptionListener.class, "Implement RecordListener instead.");
    private static final Deprecation LEGACY_SUBSCRIPTION_ITERATOR =
        Deprecation.ofImpl(SubscriptionIterator.class, "Implement RecordBuffer instead.");

    private LegacyAdapter() {} // do not create

    // --------------------- public static methods ---------------------

    public static RecordSink of(DataVisitor visitor) {
        if (visitor instanceof RecordSink)
            return (RecordSink) visitor;
        return wrapDataVisitor(visitor);
    }

    public static RecordSink of(SubscriptionVisitor visitor) {
        if (visitor instanceof RecordSink)
            return (RecordSink) visitor;
        return wrapSubscriptionVisitor(visitor);
    }

    public static RecordProvider of(DataProvider provider) {
        if (provider instanceof RecordProvider)
            return (RecordProvider) provider;
        return wrapDataProvider(provider);
    }

    public static RecordProvider of(SubscriptionProvider provider) {
        if (provider instanceof RecordProvider)
            return (RecordProvider) provider;
        return wrapSubscriptionProvider(provider);
    }

    public static RecordListener of(final DataListener listener) {
        if (listener == null || listener instanceof RecordListener)
            return (RecordListener) listener;
        return wrapDataListener(listener);
    }

    public static RecordListener of(final SubscriptionListener listener) {
        if (listener == null || listener instanceof RecordListener)
            return (RecordListener) listener;
        return wrapSubscriptionListener(listener);
    }

    public static RecordSource of(SubscriptionIterator iterator) {
        if (iterator == null || iterator instanceof RecordSource)
            return (RecordSource) iterator;
        return wrapSubscriptionIterator(iterator);
    }

    public static void release(SubscriptionIterator iterator, RecordSource source) {
        if (iterator == null || iterator instanceof RecordSource || !(source instanceof RecordBuffer))
            return;
        ((RecordBuffer) source).release();
    }
    // --------------------- private static "wrapXXX" methods ---------------------

    private static RecordSink wrapDataVisitor(final DataVisitor visitor) {
        LEGACY_DATA_VISITOR.warnClass(visitor.getClass());
        return new AbstractRecordSink() {
            @Override
            public boolean hasCapacity() {
                return visitor.hasCapacity();
            }

            @Override
            public void append(RecordCursor cursor) {
                cursor.examineData(visitor);
            }

            @Override
            public String toString() {
                return visitor.toString();
            }
        };
    }

    private static RecordSink wrapSubscriptionVisitor(final SubscriptionVisitor visitor) {
        LEGACY_SUBSCRIPTION_VISITOR.warnClass(visitor.getClass());
        return new AbstractRecordSink() {
            @Override
            public boolean hasCapacity() {
                return visitor.hasCapacity();
            }

            @Override
            public void append(RecordCursor cursor) {
                cursor.examineSubscription(visitor);
            }

            @Override
            public String toString() {
                return visitor.toString();
            }
        };
    }

    private static RecordProvider wrapDataProvider(final DataProvider provider) {
        LEGACY_DATA_PROVIDER.warnClass(provider.getClass());
        return new AbstractRecordProvider() {
            @Override
            public RecordMode getMode() {
                return RecordMode.DATA;
            }

            @Override
            public boolean retrieve(RecordSink sink) {
                return provider.retrieveData(sink);
            }
        };
    }

    private static RecordProvider wrapSubscriptionProvider(final SubscriptionProvider provider) {
        LEGACY_SUBSCRIPTION_PROVIDER.warnClass(provider.getClass());
        return new AbstractRecordProvider() {
            @Override
            public RecordMode getMode() {
                return RecordMode.HISTORY_SUBSCRIPTION;
            }

            @Override
            public boolean retrieve(RecordSink sink) {
                return provider.retrieveSubscription(sink);
            }
        };
    }

    private static RecordListener wrapDataListener(final DataListener listener) {
        LEGACY_DATA_LISTENER.warnClass(listener.getClass());
        return new RecordListener() {
            public void recordsAvailable(RecordProvider provider) {
                listener.dataAvailable(provider);
            }

            @Override
            public String toString() {
                return listener.toString();
            }
        };
    }

    private static RecordListener wrapSubscriptionListener(final SubscriptionListener listener) {
        LEGACY_SUBSCRIPTION_LISTENER.warnClass(listener.getClass());
        return new RecordListener() {
            public void recordsAvailable(RecordProvider provider) {
                listener.subscriptionAvailable(provider);
            }

            @Override
            public String toString() {
                return listener.toString();
            }
        };
    }

    private static RecordSource wrapSubscriptionIterator(SubscriptionIterator iterator) {
        LEGACY_SUBSCRIPTION_ITERATOR.warnClass(iterator.getClass());
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        sub.processSubscription(iterator);
        return sub;
    }
}
