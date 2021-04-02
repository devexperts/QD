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

import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;

/**
 * The <code>SubscriptionVisitor</code> provides serial access to subscription.
 * It follows the same pattern as <code>DataVisitor</code> except it does not go
 * through data fields.
 * <p>
 * <b>NOTE:</b> This interface is formally unrelated to its data analogue to enforce
 * strict type safety; also their state diagrams are not compatible.
 *
 * <h3>Legacy interface</h3>
 *
 * <b>FUTURE DEPRECATION NOTE:</b>
 *    New code shall not implement this interface due to its complexity and inherent slowness.
 *    Use {@link AbstractRecordSink} or {@link RecordBuffer}
 *    as a high-performance implementation of it. New code is also discouraged from using this
 *    interface unless it is need for interoperability with legacy code. Various legacy APIs
 *    will be gradually migrated to NG interfaces and classes.
 */
public interface SubscriptionVisitor {
    public static SubscriptionVisitor VOID = Void.VOID;

    /**
     * Returns whether visitor has capacity to efficiently visit next record.
     * This method may be used to advise subscription provider that it is
     * desirable to stop current string of visiting and to keep remaining
     * subscription. However, at present, subscription provider is not obliged
     * to adhere to this method contract.
     * <p>
     * <b>NOTE:</b> subscription visitor must process all subscription that is passed
     * to it via visitXXX calls no matter whether it has capacity to do it efficiently.
     */
    public boolean hasCapacity();

    /**
     * Visits next record.
     */
    public void visitRecord(DataRecord record, int cipher, String symbol, long time);
}
