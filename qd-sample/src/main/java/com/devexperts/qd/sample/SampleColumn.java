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
package com.devexperts.qd.sample;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordCursor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The <code>SampleColumn</code> wraps any data field into sample GUI column.
 */
public abstract class SampleColumn implements GUIColumn {
    protected final DataRecord record;
    protected final String name;
    protected final RecordCursor.Owner owner;

    protected SampleColumn(DataRecord record, String field_name) {
        this.record = record;
        this.name = field_name.substring(record.getName().length() + 1);
        this.owner = RecordCursor.allocateOwner(record);
    }

    public DataRecord getRecord() {
        return record;
    }

    public String getName() {
        return name;
    }

    public Object getValue(QDTicker ticker, int cipher, String symbol) {
        if (!ticker.getDataIfAvailable(owner, record, cipher, symbol))
            return null;
        return getValue(owner.cursor());
    }

    public static class IntColumn extends SampleColumn {
        protected final DataIntField field;

        public IntColumn(DataIntField field) {
            super(field.getRecord(), field.getName());
            this.field = field;
        }

        private final Date date = new Date();
        private final DateFormat date_format = new SimpleDateFormat("mm:ss");

        public Object getValue(RecordCursor cur) {
            if (field.getName().endsWith(".Time")) {
                date.setTime(cur.getInt(field.getIndex()) * 1000L);
                return date_format.format(date);
            }
            return field.getString(cur);
        }
    }

    public static class ObjColumn extends SampleColumn {
        protected final DataObjField field;

        public ObjColumn(DataObjField field) {
            super(field.getRecord(), field.getName());
            this.field = field;
        }

        public Object getValue(RecordCursor cur) {
            return field.getString(cur);
        }
    }
}
