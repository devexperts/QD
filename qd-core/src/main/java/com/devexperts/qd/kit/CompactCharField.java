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

/**
 * The <code>CompactCharField</code> represents a single character field
 * with UTF-8 serialized form. See {@link IOUtil} for details.
 */
public class CompactCharField extends AbstractDataIntField {
    private static final String[] CACHE = new String[128];

    public CompactCharField(int index, String name) {
        this(index, name, SerialFieldType.UTF_CHAR.forNamedField(name));
    }

    public CompactCharField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameSerialTypeAs(SerialFieldType.UTF_CHAR))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String toString(int value) {
        if (value >= 0 && value < CACHE.length) {
            String s = CACHE[value]; // Atomic read
            return s != null ? s : (CACHE[value] = String.valueOf((char) value));
        }
        return String.valueOf((char) value);
    }

    @Override
    public int parseString(String value) {
        if (value.length() != 1)
            throw new IllegalArgumentException("String must contain just one character");
        return value.charAt(0);
    }

    public final void writeInt(DataOutput out, int value) throws IOException {
        IOUtil.writeUTFChar(out, value);
    }

    public final void writeInt(BufferedOutput out, int value) throws IOException {
        out.writeUTFChar(value);
    }

    public final int readInt(DataInput in) throws IOException {
        return IOUtil.readUTFChar(in);
    }

    public final int readInt(BufferedInput in) throws IOException {
        return in.readUTFChar();
    }
}
