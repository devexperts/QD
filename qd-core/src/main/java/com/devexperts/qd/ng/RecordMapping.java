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

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.util.Decimal;
import com.devexperts.util.TimeUtil;
import com.devexperts.util.WideDecimal;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, String> nonDefaultPropertyName = new HashMap<>();

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

    protected final int getInt(RecordCursor cursor, int intFieldIndex) {
        return cursor.getIntMappedImpl(record, intFieldIndex);
    }

    protected final long getLong(RecordCursor cursor, int intFieldIndex) {
        return cursor.getLongMappedImpl(record, intFieldIndex);
    }

    protected final Object getObj(RecordCursor cursor, int objFieldIndex) {
        return cursor.getObjMappedImpl(record, objFieldIndex);
    }

    protected final void setInt(RecordCursor cursor, int intFieldIndex, int value) {
        cursor.setIntMappedImpl(record, intFieldIndex, value);
    }

    protected final void setLong(RecordCursor cursor, int intFieldIndex, long value) {
        cursor.setLongMappedImpl(record, intFieldIndex, value);
    }

    protected final void setObj(RecordCursor cursor, int objFieldIndex, Object value) {
        cursor.setObjMappedImpl(record, objFieldIndex, value);
    }

    protected final int findIntField(String localName, boolean required) {
        DataField field = record.findFieldByName(localName);
        if (field instanceof DataIntField)
            return (field.getIndex() << 8) | (field.getSerialType().getId() & 0xff);
        if (required)
            throw new IllegalArgumentException("Required int field " + localName + " is missing in record " + record);
        return Integer.MIN_VALUE;
    }

    // Integer, Long and Decimal field converters

    protected final int getAsInt(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            return cursor.getIntMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_LONG:
            return (int) cursor.getLongMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return (int) Decimal.toDouble(cursor.getIntMappedImpl(record, fieldId >> 8));
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            return (int) WideDecimal.toLong(cursor.getLongMappedImpl(record, fieldId >> 8));
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final long getAsLong(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            return cursor.getIntMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_LONG:
            return cursor.getLongMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return (long) Decimal.toDouble(cursor.getIntMappedImpl(record, fieldId >> 8));
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            return WideDecimal.toLong(cursor.getLongMappedImpl(record, fieldId >> 8));
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final double getAsDouble(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            return cursor.getIntMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_LONG:
            return cursor.getLongMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return Decimal.toDouble(cursor.getIntMappedImpl(record, fieldId >> 8));
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            return WideDecimal.toDouble(cursor.getLongMappedImpl(record, fieldId >> 8));
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final int getAsTinyDecimal(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            return Decimal.composeDecimal(cursor.getIntMappedImpl(record, fieldId >> 8), 0);
        case SerialFieldType.Bits.FLAG_LONG:
            return Decimal.composeDecimal(cursor.getLongMappedImpl(record, fieldId >> 8), 0);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return cursor.getIntMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            return Decimal.wideToTiny(cursor.getLongMappedImpl(record, fieldId >> 8));
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final long getAsWideDecimal(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            return WideDecimal.composeWide(cursor.getIntMappedImpl(record, fieldId >> 8), 0);
        case SerialFieldType.Bits.FLAG_LONG:
            return WideDecimal.composeWide(cursor.getLongMappedImpl(record, fieldId >> 8), 0);
        case SerialFieldType.Bits.FLAG_DECIMAL:
            return Decimal.tinyToWide(cursor.getIntMappedImpl(record, fieldId >> 8));
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            return cursor.getLongMappedImpl(record, fieldId >> 8);
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsInt(RecordCursor cursor, int fieldId, int value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            cursor.setIntMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_LONG:
            cursor.setLongMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.composeDecimal(value, 0));
            break;
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            cursor.setLongMappedImpl(record, fieldId >> 8, WideDecimal.composeWide(value, 0));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsLong(RecordCursor cursor, int fieldId, long value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) value);
            break;
        case SerialFieldType.Bits.FLAG_LONG:
            cursor.setLongMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.composeDecimal(value, 0));
            break;
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            cursor.setLongMappedImpl(record, fieldId >> 8, WideDecimal.composeWide(value, 0));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsDouble(RecordCursor cursor, int fieldId, double value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) value);
            break;
        case SerialFieldType.Bits.FLAG_LONG:
            cursor.setLongMappedImpl(record, fieldId >> 8, (long) value);
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.compose(value));
            break;
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            cursor.setLongMappedImpl(record, fieldId >> 8, WideDecimal.composeWide(value));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsTinyDecimal(RecordCursor cursor, int fieldId, int value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) Decimal.toDouble(value));
            break;
        case SerialFieldType.Bits.FLAG_LONG:
            cursor.setLongMappedImpl(record, fieldId >> 8, (long) Decimal.toDouble(value));
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            cursor.setLongMappedImpl(record, fieldId >> 8, Decimal.tinyToWide(value));
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsWideDecimal(RecordCursor cursor, int fieldId, long value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_INT:
            cursor.setIntMappedImpl(record, fieldId >> 8, (int) WideDecimal.toLong(value));
            break;
        case SerialFieldType.Bits.FLAG_LONG:
            cursor.setLongMappedImpl(record, fieldId >> 8, WideDecimal.toLong(value));
            break;
        case SerialFieldType.Bits.FLAG_DECIMAL:
            cursor.setIntMappedImpl(record, fieldId >> 8, Decimal.wideToTiny(value));
            break;
        case SerialFieldType.Bits.FLAG_WIDE_DECIMAL:
            cursor.setLongMappedImpl(record, fieldId >> 8, value);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    // Time field converters

    protected final int getAsTimeSeconds(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_TIME_SECONDS:
            return cursor.getIntMappedImpl(record, fieldId >> 8);
        case SerialFieldType.Bits.FLAG_TIME_MILLIS:
            return TimeUtil.getSecondsFromTime(cursor.getLongMappedImpl(record, fieldId >> 8));
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsTimeSeconds(RecordCursor cursor, int fieldId, int value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_TIME_SECONDS:
            cursor.setIntMappedImpl(record, fieldId >> 8, value);
            break;
        case SerialFieldType.Bits.FLAG_TIME_MILLIS:
            cursor.setLongMappedImpl(record, fieldId >> 8, value * 1000L);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final long getAsTimeMillis(RecordCursor cursor, int fieldId) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_TIME_SECONDS:
            return cursor.getIntMappedImpl(record, fieldId >> 8) * 1000L;
        case SerialFieldType.Bits.FLAG_TIME_MILLIS:
            return cursor.getLongMappedImpl(record, fieldId >> 8);
        default:
            throw new IllegalArgumentException();
        }
    }

    protected final void setAsTimeMillis(RecordCursor cursor, int fieldId, long value) {
        switch (fieldId & SerialFieldType.Bits.REPRESENTATION_MASK) {
        case SerialFieldType.Bits.FLAG_TIME_SECONDS:
            cursor.setIntMappedImpl(record, fieldId >> 8, TimeUtil.getSecondsFromTime(value));
            break;
        case SerialFieldType.Bits.FLAG_TIME_MILLIS:
            cursor.setLongMappedImpl(record, fieldId >> 8, value);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }
}
