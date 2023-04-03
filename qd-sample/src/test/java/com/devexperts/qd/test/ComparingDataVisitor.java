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
package com.devexperts.qd.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Compares visited data against given source.
 */
class ComparingDataVisitor implements DataVisitor {
    private final DataIterator source;
    private DataRecord nextRecord;
    private int fieldsLeft;

    public static void compare(DataProvider provider, DataIterator source) {
        ComparingDataVisitor visitor = new ComparingDataVisitor(source);
        assertFalse("provider has more data than source", provider.retrieveData(visitor));
        assertFalse("provider has less data than source", visitor.hasCapacity());
    }

    public ComparingDataVisitor(DataIterator source) {
        this.source = source;
        nextRecord = source.nextRecord();
    }

    public boolean hasCapacity() {
        return nextRecord != null;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol) {
        assertEquals("record", record, nextRecord);
        assertEquals("cipher", cipher, source.getCipher());
        assertEquals("symbol", symbol, source.getSymbol());
        fieldsLeft = record.getIntFieldCount() + record.getObjFieldCount();
        if (fieldsLeft == 0)
            nextRecord = source.nextRecord();
    }

    public void visitIntField(DataIntField field, int value) {
        assertEquals("int_field:" + field, value, source.nextIntField());
        decFields();
    }

    public void visitObjField(DataObjField field, Object value) {
        assertEquals("obj_field:" + field, value, source.nextObjField());
        decFields();
    }

    private void decFields() {
        if (--fieldsLeft == 0)
            nextRecord = source.nextRecord();
    }
}
