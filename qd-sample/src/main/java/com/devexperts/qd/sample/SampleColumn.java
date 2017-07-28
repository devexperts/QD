/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.RecordCursor;

/**
 * The <code>SampleColumn</code> wraps any data field into sample GUI column.
 */
public abstract class SampleColumn implements GUIColumn {
    protected final DataRecord record;
    protected final String name;

    protected SampleColumn(DataRecord record, String field_name) {
        this.record = record;
        this.name = field_name.substring(record.getName().length() + 1);
    }

    public DataRecord getRecord() {
        return record;
    }

    public String getName() {
        return name;
    }

    public static class IntColumn extends SampleColumn {
        protected final DataIntField field;

        public IntColumn(DataIntField field) {
            super(field.getRecord(), field.getName());
            this.field = field;
        }

        public Object getValue(QDTicker ticker, int cipher, String symbol) {
            int value = ticker.getInt(field, cipher, symbol);
            if (value == 0 && !ticker.isAvailable(record, cipher, symbol))
                return null;
            return formatValue(value);
        }

        public Object getValue(RecordCursor cur) {
            return formatValue(cur.getInt(field.getIndex()));
        }

        private final Date date = new Date();
        private final DateFormat date_format = new SimpleDateFormat("mm:ss");

        protected Object formatValue(int value) {
            if (field.getName().endsWith(".Time")) {
                date.setTime(value * 1000L);
                return date_format.format(date);
            }
            return field.toString(value);
        }
    }

    public static class ObjColumn extends SampleColumn {
        protected final DataObjField field;

        public ObjColumn(DataObjField field) {
            super(field.getRecord(), field.getName());
            this.field = field;
        }

        public Object getValue(QDTicker ticker, int cipher, String symbol) {
            Object value = ticker.getObj(field, cipher, symbol);
            if (value == null && !ticker.isAvailable(record, cipher, symbol))
                return null;
            return field.toString(value);
        }

        public Object getValue(RecordCursor cur) {
            return field.toString(cur.getObj(field.getIndex()));
        }
    }
}
