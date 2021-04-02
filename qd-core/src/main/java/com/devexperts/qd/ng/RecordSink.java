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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionVisitor;

/**
 * An object to which data records can be appended.
 * This interface is replacing legacy interfaces {@link DataVisitor} and {@link SubscriptionVisitor}.
 */
public interface RecordSink extends DataVisitor, SubscriptionVisitor {
    /**
     * Record sink that just ignores all records.
     */
    public static final RecordSink VOID = (RecordSink) DataVisitor.VOID;

    /**
     * Returns whether sink has capacity to efficiently {@link #append(RecordCursor) append} next record.
     * This method may be used to advise {@link RecordProvider} that it is desirable
     * to stop current batch and keep remaining records. However,
     * record provider is not obliged to adhere to this method contract.
     *
     * <p><b>NOTE:</b> sink must process all records that are passed to it
     * via {@link #append(RecordCursor) append} method calls no matter whether it
     * has capacity to do it efficiently.
     */
    @Override
    public boolean hasCapacity();

    /**
     * Adds a record to this sink from the specified {@link RecordCursor}.
     */
    public void append(RecordCursor cursor);

    /**
     * Signal that it is safe to flush {@link #append(RecordCursor) appended} records to disk,
     * e.g. no locks are currently held.
     */
    public void flush();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    @Override
    public void visitIntField(DataIntField field, int value);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    @Override
    public void visitObjField(DataObjField field, Object value);
}
