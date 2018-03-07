/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.ng;

import java.util.HashMap;
import java.util.Map;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.util.Decimal;

/**
 * Base class for record mappings.
 * Record mapping maps record field indices onto specific getters and setters.
 * Actual implementation of this class store the indices of the corresponding data record fields and
 * provide a set of methods like
 * {@code T getXXX(RecordCursor cursor)} and
 * {@code void setXXX(RecordCursor cursor, T value)} for them.
 */
public abstract class RecordMapping {
    private final DataRecord record;
    private final Map<String, String> nonDefaultPropertyName = new HashMap<String, String>();

    /**
     * Creates record mapping for a specified record.
     */
    protected RecordMapping(DataRecord record) {
        this.record = record;
    }

    /**
     * Defines non-default property name in descendant classes constructors.
     */
    protected void putNonDefaultPropertyName(String localName, String propertyName) {
        nonDefaultPropertyName.put(localName, propertyName);
    }

    /**
     * Returns field {@link DataField#getPropertyName() property name} that corresponding to
     * its {@link DataField#getLocalName() local name} in case when it is not
     * {@link AbstractDataField#getDefaultPropertyName(String) default}.
     * @return non-default local name or {@code null} if default.
     */
    public String getNonDefaultPropertyName(String localName) {
        return nonDefaultPropertyName.get(localName);
    }

    public Object getEventSymbolByQDSymbol(String qdSymbol) {
        return qdSymbol;
    }

    public String getQDSymbolByEventSymbol(Object symbol) {
        return symbol.toString();
    }

    /**
     * Returns data record that this record mapping works for.
     * The result of invoking {@link DataRecord#getMapping(Class) getMapping(mappingClass)} method on
     * the resulting data record is equal to this mapping.
     */
    public final DataRecord getRecord() {
        return record;
    }

    protected final int getInt(RecordCursor cursor, int int_field_index) {
        return cursor.getIntMappedImpl(record, int_field_index);
    }

    protected final Object getObj(RecordCursor cursor, int obj_field_index) {
        return cursor.getObjMappedImpl(record, obj_field_index);
    }

    protected final void setInt(RecordCursor cursor, int int_field_index, int value) {
        cursor.setIntMappedImpl(record, int_field_index, value);
    }

    protected final void setObj(RecordCursor cursor, int obj_field_index, Object value) {
        cursor.setObjMappedImpl(record, obj_field_index, value);
    }

    protected final int findIntField(String localName, boolean required) {
        DataField field = record.findFieldByName(localName);
        if (field instanceof DataIntField)
            return (field.getIndex() << 8) | (field.getSerialType().getId() & 0xff);
        if (required)
            throw new IllegalArgumentException("Required int field " + localName + " is missing in record " + record);
        return Integer.MIN_VALUE;
    }

    protected final int getAsInt(RecordCursor cursor, int fieldId) {
        int raw = cursor.getIntMappedImpl(record, fieldId >> 8);
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            return raw;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return (int) Decimal.toDouble(raw);
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final double getAsDouble(RecordCursor cursor, int fieldId) {
        int raw = cursor.getIntMappedImpl(record, fieldId >> 8);
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            return raw;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return Decimal.toDouble(raw);
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final int getAsDecimal(RecordCursor cursor, int fieldId) {
        int raw = cursor.getIntMappedImpl(record, fieldId >> 8);
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            return Decimal.composeDecimal(raw, 0);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return raw;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsInt(RecordCursor cursor, int fieldId, int value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            cursor.setIntMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.composeDecimal(value, 0));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsDouble(RecordCursor cursor, int fieldId, double value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) value);
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.compose(value));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsDecimal(RecordCursor cursor, int fieldId, int value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case 0:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) Decimal.toDouble(value));
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, value);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }
}
