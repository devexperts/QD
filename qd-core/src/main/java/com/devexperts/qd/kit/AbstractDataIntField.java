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
package com.devexperts.qd.kit;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.ng.RecordCursor;

import java.io.IOException;

public abstract class AbstractDataIntField extends AbstractDataField implements DataIntField {
    private static final String[] INT_STRING_CACHE = new String[1001];

    AbstractDataIntField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
    }

    @Override
    public String getString(RecordCursor cursor) {
        return toString(cursor.getInt(getIndex()));
    }

    @Override
    public void setString(RecordCursor cursor, String value) {
        cursor.setInt(getIndex(), parseString(value));

    }

    @Override
    public void write(BufferedOutput out, RecordCursor cursor) throws IOException {
        writeInt(out, cursor.getInt(getIndex()));
    }

    @Override
    public void read(BufferedInput in, RecordCursor cursor) throws IOException {
        cursor.setInt(getIndex(), readInt(in));
    }

    /**
     * Returns string representation of specified field value.
     * This method is used for debugging purposes.
     * This implementation returns <code>Integer.toString(value)</code>.
     */
    public String toString(int value) {
        if (value >= 0 && value < INT_STRING_CACHE.length) {
            String s = INT_STRING_CACHE[value]; // Atomic read
            return s != null ? s : (INT_STRING_CACHE[value] = Integer.toString(value));
        }
        return Integer.toString(value);
    }

    /**
     * Parses string representation of specified field value.
     * This method is used for debugging purposes.
     * This implementation returns <code>Integer.parseInt(value)</code>.
     * @throws IllegalArgumentException if string cannot be parsed.
     */
    public int parseString(String value) {
        return Integer.parseInt(value);
    }

    /**
     * Converts raw QD int-value to meaningful double value, or to <code>Double.NaN</code> if inapplicable.
     */
    public double toDouble(int value) {
        if (value == Integer.MAX_VALUE)
            return Double.POSITIVE_INFINITY;
        if (value == Integer.MIN_VALUE)
            return Double.NEGATIVE_INFINITY;
        return value;
    }

    /**
     * Converts meaningful double value to raw QD int-value, or to <code>0</code> if inapplicable.
     */
    public int toInt(double value) {
        return (int) value;
    }

    /**
     * Compares two specified field values for equality.
     * This method is used for implementation of ticker contract.
     * This implementation returns <code>value1 == value2</code>.
     */
    public boolean equals(int value1, int value2) {
        return value1 == value2;
    }
}
