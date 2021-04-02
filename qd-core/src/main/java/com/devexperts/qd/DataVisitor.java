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
 * The <code>DataVisitor</code> defines protocol of serial access to the data
 * using Visitor pattern. It allows data provider with complicated data storage
 * effectively give away data to external data consumer.
 * <p>
 * Specifically, <code>DataVisitor</code> allows data provider to take proper
 * synchronization locks only once per large data block (and not per visited entity)
 * and save on navigation through data storage by calculating and caching locally
 * required references and indices.
 * <p>
 * Therefore, the implementor of <code>DataVisitor</code> must perform its operations
 * quickly without need for synchronization and prolonged external actions for each step.
 * If data consumer in its turn requires synchronized access or is otherwise slow,
 * then it should accept {@link DataIterator} as a source of data and use
 * intermediate data buffer ({@link DataBuffer} or equivalent).
 * <p>
 * The <code>DataVisitor</code> visits all available data records and for each record
 * it visits all fields in their serial order (see {@link DataRecord}).
 * The corresponding state diagram is shown below (note that the number and types of
 * visited fields exactly matches number and types of the fields in the current record):
 * <pre>
 * +-----&gt; [Ready]
 * |          |
 * |          1 visitRecord
 * |          |
 * |          V
 * |  +--&gt; [Visiting Int]
 * |  |       |
 * |  +-------* visitIntField
 * |          |
 * |          V
 * |  +--&gt; [Visiting Obj]
 * |  |       |
 * |  +-------* visitObjField
 * |          |
 * +----------+
 * </pre>
 * <b>NOTE:</b> if visiting is ever aborted in a state different from [Ready],
 * then behavior of both parties (data visitor and data provider) is undefined.
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
public interface DataVisitor {
    public static final DataVisitor VOID = Void.VOID;

    /**
     * Returns whether visitor has capacity to efficiently visit next record.
     * This method may be used to advise data provider that it is desirable
     * to stop current string of visiting and to keep remaining data. However,
     * at present, data provider is not obliged to adhere to this method contract.
     * <p>
     * <b>NOTE:</b> data visitor must process all data that is passed to it
     * via visitXXX calls no matter whether it has capacity to do it efficiently.
     */
    public boolean hasCapacity();

    /**
     * Visits next record.
     *
     * @throws IllegalStateException if visitor is not in [Ready] state.
     */
    public void visitRecord(DataRecord record, int cipher, String symbol);

    /**
     * Visits next Int-field within current record.
     *
     * @throws IllegalStateException if visitor is not in a state to visit specified field.
     */
    public void visitIntField(DataIntField field, int value);

    /**
     * Visits next Obj-field within current record.
     *
     * @throws IllegalStateException if visitor is not in a state to visit specified field.
     */
    public void visitObjField(DataObjField field, Object value);
}
