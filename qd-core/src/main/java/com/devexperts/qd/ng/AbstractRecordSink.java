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
 * Bridge class that adapts {@link DataVisitor} and {@link SubscriptionVisitor} APIs
 * to {@link RecordSink} API. All you have to do is to implement {@link #append(RecordCursor)} method,
 * while all the methods of {@link DataVisitor} and {@link SubscriptionVisitor} interfaces are
 * already implemented and invoke {@link #append(RecordCursor)} implementation.
 */
public abstract class AbstractRecordSink implements RecordSink {

    private final RecordCursor cursor = new RecordCursor(false);

    private boolean hasData;
    private int intField;
    private int objField;

    // ----------------------- public overrideable methods -----------------------

    /**
     * {@inheritDoc}
     * <p>This implementation returns {@code true}.
     */
    public boolean hasCapacity() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public abstract void append(RecordCursor cursor);

    /**
     * {@inheritDoc}
     * <p>This implementation does nothing.
     */
    @Override
    public void flush() {}

    // ----------------------- public final bridge methods -----------------------

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    public final void visitRecord(DataRecord record, int cipher, String symbol) {
        addInternal(record, cipher, symbol, RecordMode.DATA);
        intField = 0;
        objField = 0;
        appendCursorIfNeeded(); // in case when record has no fields
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    public final void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        addInternal(record, cipher, symbol, RecordMode.HISTORY_SUBSCRIPTION);
        cursor.setTime(time);
        appendCursor();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    public final void visitIntField(DataIntField field, int value) {
        cursor.setInt(intField++, value);
        appendCursorIfNeeded();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #append(RecordCursor)}.
     */
    public final void visitObjField(DataObjField field, Object value) {
        cursor.setObj(objField++, value);
        appendCursorIfNeeded();
    }

    // ----------------------- private helper methods -----------------------

    private void addInternal(DataRecord record, int cipher, String symbol, RecordMode mode) {
        if (hasData)
            appendCursor();
        cursor.setRecordInternal(record, mode);
        cursor.setSymbolInternal(cipher, symbol);
        cursor.ensureCapacityInternal();
        hasData = true;
    }

    private void appendCursorIfNeeded() {
        if (intField == cursor.getIntCount() && objField == cursor.getObjCount())
            appendCursor();
    }

    private void appendCursor() {
        append(cursor);
        cursor.clearFields();
        hasData = false;
        // the following lines will cause exception if somebody calls visitXXXField without visitRecord
        intField = Integer.MAX_VALUE / 2;
        objField = Integer.MAX_VALUE / 2;
    }
}
