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
import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataVisitor;
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

    private static final Logging log = Logging.getLogging(LegacyAdapter.class);

    private static volatile boolean legacyDataVisitorWarningShown;
    private static volatile boolean legacySubscriptionVisitorWarningShown;
    private static volatile boolean legacyDataProviderWarningShown;
    private static volatile boolean legacySubscriptionProviderWarningShown;
    private static volatile boolean legacyDataListenerWarningShown;
    private static volatile boolean legacySubscriptionListenerWarningShown;
    private static volatile boolean legacySubscriptionIteratorWarningShown;

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
        if (!legacyDataVisitorWarningShown)
            legacyDataVisitorWarningSync(visitor);
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
        if (!legacySubscriptionVisitorWarningShown)
            legacySubscriptionVisitorWarningSync(visitor);
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
        if (!legacyDataProviderWarningShown)
            legacyDataProviderWarningSync(provider);
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
        if (!legacySubscriptionProviderWarningShown)
            legacySubscriptionProviderWarningSync(provider);
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
        if (!legacyDataListenerWarningShown)
            legacyDataListenerWarningSync(listener);
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
        if (!legacySubscriptionListenerWarningShown)
            legacySubscriptionListenerWarningSync(listener);
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
        if (!legacySubscriptionIteratorWarningShown)
            legacySubscriptionIteratorWarningSync(iterator);
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        sub.processSubscription(iterator);
        return sub;
    }

    // --------------------- private static "legacyXXXWarningSync" methods ---------------------

    private static synchronized void legacyDataVisitorWarningSync(DataVisitor visitor) {
        if (legacyDataVisitorWarningShown)
            return;
        legacyDataVisitorWarningShown = true;
        log.warn("WARNING: DEPRECATED use of custom DataVisitor implementation class " + visitor.getClass().getName() +
            " from " + getSource() + ". Do not implement DataVisitor interface. Use AbstractRecordSink instead.");
    }

    private static synchronized void legacySubscriptionVisitorWarningSync(SubscriptionVisitor visitor) {
        if (legacySubscriptionVisitorWarningShown)
            return;
        legacySubscriptionVisitorWarningShown = true;
        log.warn("WARNING: DEPRECATED use of custom SubscriptionVisitor implementation class " + visitor.getClass().getName() +
            " from " + getSource() + ". Do not implement SubscriptionVisitor interface. Use AbstractRecordSink instead.");
    }

    private static synchronized void legacyDataProviderWarningSync(DataProvider provider) {
        if (legacyDataProviderWarningShown)
            return;
        legacyDataProviderWarningShown = true;
        log.warn("WARNING: DEPRECATED use of custom DataProvider implementation class " + provider.getClass().getName() +
            " from " + getSource() + ". Do not implement DataProvider interface. Use AbstractRecordProvider instead.");
    }

    private static synchronized void legacySubscriptionProviderWarningSync(SubscriptionProvider provider) {
        if (legacySubscriptionProviderWarningShown)
            return;
        legacySubscriptionProviderWarningShown = true;
        log.warn("WARNING: DEPRECATED use of custom SubscriptionProvider implementation class " + provider.getClass().getName() +
            " from " + getSource() + ". Do not implement SubscriptionProvider interface. Use AbstractRecordProvider instead.");
    }

    private static synchronized void legacyDataListenerWarningSync(DataListener listener) {
        if (legacyDataListenerWarningShown)
            return;
        legacyDataListenerWarningShown = true;
        log.warn("WARNING: DEPRECATED use of legacy DataListener interface implementation " + listener.getClass().getName() +
            " from " + getSource() + ". Do not implement DataListener interface. Implement RecordListener instead.");
    }

    private static synchronized void legacySubscriptionListenerWarningSync(SubscriptionListener listener) {
        if (legacySubscriptionListenerWarningShown)
            return;
        legacySubscriptionListenerWarningShown = true;
        log.warn("WARNING: DEPRECATED use of legacy SubscriptionListener interface implementation " + listener.getClass().getName() +
            " from " + getSource() + ". Do not implement SubscriptionListener interface. Implement RecordListener instead.");
    }

    private static synchronized void legacySubscriptionIteratorWarningSync(SubscriptionIterator iterator) {
        if (legacySubscriptionIteratorWarningShown)
            return;
        legacySubscriptionIteratorWarningShown = true;
        log.warn("WARNING: DEPRECATED use of legacy SubscriptionIterator interface implementation " + iterator.getClass().getName() +
            " from " + getSource() + ". Do not implement SubscriptionIterator interface. Use RecordBuffer instead.");
    }

    // --------------------- other methods ---------------------

    private static String getSource() {
        StackTraceElement[] trace = new Exception().getStackTrace();
        for (StackTraceElement ste : trace) {
            if (ste.getClassName().startsWith("com.devexperts.qd."))
                continue;
            return ste.getClassName() + "." + ste.getMethodName();
        }
        return "<unknown>";
    }
}
