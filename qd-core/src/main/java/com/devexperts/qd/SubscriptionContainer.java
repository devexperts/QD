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
package com.devexperts.qd;

import com.devexperts.qd.ng.RecordSink;

/**
 * The <code>SubscriptionContainer</code> represents data structure that keeps subscription
 * in map-like fashion. Methods to examine subscription in different granularity levels are provided.
 */
public interface SubscriptionContainer {
    /**
     * Returns <code>true</code> if subscribed to the corresponding record and symbol with the
     * corresponding time. Non-historic (stream and ticker) QD ignores time parameter.
     *
     * <p><b>Note:</b> Check by time in history is not guaranteed yet
     * (may transiently give wrong result), because it performs unsynchronized
     * read on two ints to get time from internal data structure.
     * In history it is guaranteed to work properly only when time parameter is set to
     * {@link Long#MAX_VALUE}.
     */
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time);

    /**
     * Examines subscription and passes it to the visitor.
     * Returns <code>true</code> if not all subscription was examined or
     * <code>false</code> otherwise.
     *
     * <p><b>Note:</b> Visited subscription time in history is not guaranteed yet
     * (may transiently give wrong result), because it performs unsynchronized
     * read on two ints to get time from internal data structure.
     *
     * @deprecated Use {@link #examineSubscription(RecordSink)}.
     */
    public boolean examineSubscription(SubscriptionVisitor visitor);

    /**
     * Examines subscription and passes it to the record sink.
     * This method periodically calls {@link RecordSink#flush() RecordSink.flush} method outside of locks.
     * Returns <code>true</code> if not all subscription was examined or
     * <code>false</code> otherwise.
     *
     * <p><b>Note:</b> Visited subscription time in history is not guaranteed yet
     * (may transiently give wrong result), because it performs unsynchronized
     * read on two ints to get time from internal data structure.
     */
    public boolean examineSubscription(RecordSink sink);

    /**
     * Returns subscription size in terms of (record, symbol) pairs.
     */
    public int getSubscriptionSize();
}
