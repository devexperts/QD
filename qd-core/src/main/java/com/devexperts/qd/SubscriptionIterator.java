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

/**
 * The <code>SubscriptionIterator</code> provides serial access to subscription.
 * It follows the same pattern as <code>DataIterator</code> except it does not go
 * through data fields.
 * <p>
 * <b>NOTE:</b> This interface is formally unrelated to its data analogue to enforce
 * strict type safety; also their state diagrams are not compatible.
 *
 * <h3>Legacy interface</h3>
 *
 * <b>FUTURE DEPRECATION NOTE:</b>
 *    New code shall not implement this interface due to its complexity and inherent slowness.
 *    Implement {@link com.devexperts.qd.ng.RecordSource} or use {@link com.devexperts.qd.ng.RecordBuffer}
 *    as a high-performance implementation of it. New code is also discouraged from using this
 *    interface unless it is need for interoperability with legacy code. Various legacy APIs
 *    will be gradually migrated to NG interfaces and classes.
 */
public interface SubscriptionIterator {
    public static final SubscriptionIterator VOID = Void.VOID;

    /**
     * Returns cipher for the current record returned by last call to {@link #nextRecord}.
     * Returns 0 if not encoded or if no current record is being iterated.
     */
    public int getCipher();

    /**
     * Returns symbol for the current record returned by last call to {@link #nextRecord}.
     * Returns null if encoded or if no current record is being iterated.
     */
    public String getSymbol();

    /**
     * Returns time for the current record returned by last call to {@link #nextRecord}.
     * Returns 0 if not historical or if no current record is being iterated.
     */
    public long getTime();

    /**
     * Returns next record. Returns null if no more records available.
     */
    public DataRecord nextRecord();
}
