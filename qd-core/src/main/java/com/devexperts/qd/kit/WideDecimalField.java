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
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.util.WideDecimal;

import java.io.IOException;

/**
 * The <code>WideDecimalField</code> represents a decimal field with compact serialized form.
 * See {@link WideDecimal} for description of internal representation.
 * It can be used for fields which are usually represented with
 * floating point values, such as prices, amounts, etc.
 */
public class WideDecimalField extends CompactIntField {
    public WideDecimalField(int index, String name) {
        this(index, name, SerialFieldType.WIDE_DECIMAL.forNamedField(name));
    }

    public WideDecimalField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.WIDE_DECIMAL))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String getString(RecordCursor cursor) {
        return WideDecimal.toString(cursor.getLong(getIndex()));
    }

    @Override
    public void setString(RecordCursor cursor, String value) {
        cursor.setLong(getIndex(), WideDecimal.parseWide(value));
    }

    @Override
    public void write(BufferedOutput out, RecordCursor cursor) throws IOException {
        out.writeCompactLong(cursor.getLong(getIndex()));
    }

    @Override
    public void read(BufferedInput in, RecordCursor cursor) throws IOException {
        cursor.setLong(getIndex(), in.readCompactLong());
    }

    @Override
    public String toString(int value) {
        return WideDecimal.toString(value);
    }

    @Override
    public int parseString(String value) {
        return (int) WideDecimal.parseWide(value);
    }

    @Override
    public double toDouble(int value) {
        return WideDecimal.toDouble(value);
    }

    @Override
    public int toInt(double value) {
        return (int) WideDecimal.composeWide(value);
    }
}
