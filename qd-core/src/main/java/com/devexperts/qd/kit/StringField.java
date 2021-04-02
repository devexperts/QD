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
import com.devexperts.io.IOUtil;
import com.devexperts.qd.SerialFieldType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The <code>StringField</code> represents a character sequence field
 * with CESU-8 serialized form. See {@link IOUtil} for details.
 * Default representation of the value is {@link String} as returned by {@link #readObj},
 * but <code>char[]</code>, <code>byte[]</code> and arbitrary objects are also supported
 * by {@link #writeObj} and {@link #toString(Object)}.
 */
public final class StringField extends AbstractDataObjField {
    private final boolean utfString;

    public StringField(int index, String name) {
        this(index, name, false);
    }

    public StringField(int index, String name, boolean utfString) {
        super(index, name, utfString ? SerialFieldType.STRING : SerialFieldType.UTF_CHAR_ARRAY);
        this.utfString = utfString;
    }

    @Override
    public String toString(Object value) {
        if (value == null || value instanceof String)
            return (String) value;
        else if (value instanceof char[])
            return new String((char[]) value);
        else if (value instanceof byte[])
            return new String((byte[]) value, StandardCharsets.UTF_8);
        else
            return value.toString();
    }

    @Override
    public boolean equals(Object value1, Object value2) {
        if (value1 == value2)
            return true;
        else if (value1 == null || value2 == null)
            return false;
        else if (value1 instanceof char[] && value2 instanceof char[])
            return Arrays.equals((char[]) value1, (char[]) value2);
        else if (value1 instanceof byte[] && value2 instanceof byte[])
            return Arrays.equals((byte[]) value1, (byte[]) value2);
        else
            return super.equals(toString(value1), toString(value2));
    }

    @Override
    public final void writeObj(DataOutput out, Object value) throws IOException {
        if (utfString) {
            if (value == null || value instanceof String)
                IOUtil.writeUTFString(out, (String) value);
            else if (value instanceof char[])
                IOUtil.writeUTFString(out, new String((char[]) value));
            else if (value instanceof byte[])
                IOUtil.writeByteArray(out, (byte[]) value);
            else
                IOUtil.writeUTFString(out, value.toString());
        } else {
            if (value == null || value instanceof String)
                IOUtil.writeCharArray(out, (String) value);
            else if (value instanceof char[])
                IOUtil.writeCharArray(out, (char[]) value);
            else if (value instanceof byte[])
                IOUtil.writeCharArray(out, new String((byte[]) value, StandardCharsets.UTF_8));
            else
                IOUtil.writeCharArray(out, value.toString());
        }
    }

    @Override
    public final void writeObj(BufferedOutput out, Object value) throws IOException {
        if (utfString) {
            if (value == null || value instanceof String)
                out.writeUTFString((String) value);
            else if (value instanceof char[])
                out.writeUTFString(new String((char[]) value));
            else if (value instanceof byte[])
                out.writeByteArray((byte[]) value);
            else
                out.writeUTFString(value.toString());
        } else {
            if (value == null || value instanceof String)
                IOUtil.writeCharArray(out, (String) value);
            else if (value instanceof char[])
                IOUtil.writeCharArray(out, (char[]) value);
            else if (value instanceof byte[])
                IOUtil.writeCharArray(out, new String((byte[]) value, StandardCharsets.UTF_8));
            else
                IOUtil.writeCharArray(out, value.toString());
        }
    }

    @Override
    public final Object readObj(DataInput in) throws IOException {
        return utfString ? IOUtil.readUTFString(in): IOUtil.readCharArrayString(in);
    }

    @Override
    public final Object readObj(BufferedInput in) throws IOException {
        return utfString ? in.readUTFString(): IOUtil.readCharArrayString(in);
    }
}
