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
 * The <code>DataIterator</code> defines protocol of serial access to the data
 * using Iterator pattern. It allows data consumer with complicated data storage
 * effectively receive data from external data provider.
 * <p>
 * Specifically, <code>DataIterator</code> allows data consumer to take proper
 * synchronization locks only once per large data block (and not per iterated entity)
 * and save on navigation through data storage by calculating and caching locally
 * required references and indices.
 * <p>
 * Therefore, the implementor of <code>DataIterator</code> must perform its operations
 * quickly without need for synchronization and prolonged external actions for each step.
 * If data provider in its turn requires synchronized access or is otherwise slow,
 * then it should accept {@link DataVisitor} as a destination for data and use
 * intermediate data buffer ({@link DataBuffer} or equivalent).
 * <p>
 * The <code>DataIterator</code> iterates all available data records and for each record
 * it iterates all fields in their serial order (see {@link DataRecord}).
 * The corresponding state diagram is shown below (note that the number and types of
 * iterated fields exactly matches number and types of the fields in the current record):
 * <pre>
 * +-----&gt; [Ready]
 * |          |
 * |          1 nextRecord
 * |          |
 * |          V
 * |  +--&gt; [Iterating Int]
 *
 * |  |       |
 * |  +-------* nextIntField
 * |          |
 * |          V
 * |  +--&gt; [Iterating Obj]
 * |  |       |
 * |  +-------* nextObjField
 * |          |
 * +----------+
 * </pre>
 * <b>NOTE:</b> if iteration is ever aborted in a state different from [Ready],
 * then behavior of both parties (data iterator and data consumer) is undefined.
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
public interface DataIterator {
    public static final DataIterator VOID = Void.VOID;

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
     * Returns next record. Returns null if no more records available.
     *
     * @throws IllegalStateException if iterator is not in [Ready] state.
     */
    public DataRecord nextRecord();

    /**
     * Returns next Int-field within current record being iterated.
     *
     * @throws IllegalStateException if iterator is not in a state to iterate Int-field.
     */
    public int nextIntField();

    /**
     * Returns next Obj-field within current record being iterated.
     *
     * @throws IllegalStateException if iterator is not in a state to iterate Obj-field.
     */
    public Object nextObjField();
}
