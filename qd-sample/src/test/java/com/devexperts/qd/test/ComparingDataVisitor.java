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
package com.devexperts.qd.test;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import junit.framework.Assert;

/**
 * Compares visited data against given source.
 */
class ComparingDataVisitor implements DataVisitor {
    private final DataIterator source;
    private DataRecord next_record;
    private int fields_left;

    public static void compare(DataProvider provider, DataIterator source) {
        ComparingDataVisitor visitor = new ComparingDataVisitor(source);
        Assert.assertFalse("provider has more data than source", provider.retrieveData(visitor));
        Assert.assertFalse("provider has less data than source", visitor.hasCapacity());
    }

    public ComparingDataVisitor(DataIterator source) {
        this.source = source;
        next_record = source.nextRecord();
    }

    public boolean hasCapacity() {
        return next_record != null;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol) {
        Assert.assertEquals("record", record, next_record);
        Assert.assertEquals("cipher", cipher, source.getCipher());
        Assert.assertEquals("symbol", symbol, source.getSymbol());
        fields_left = record.getIntFieldCount() + record.getObjFieldCount();
        if (fields_left == 0)
            next_record = source.nextRecord();
    }

    public void visitIntField(DataIntField field, int value) {
        Assert.assertEquals("int_field:" + field, value, source.nextIntField());
        decFields();
    }

    public void visitObjField(DataObjField field, Object value) {
        Assert.assertEquals("obj_field:" + field, value, source.nextObjField());
        decFields();
    }

    private void decFields() {
        if (--fields_left == 0)
            next_record = source.nextRecord();
    }
}
