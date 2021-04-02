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
 * The <code>CompactIntField</code> represents an integer field
 * with compact serialized form. It can be used for fields which
 * are usually represented with small integer values, such as
 * frequencies, quantities, sizes, prices, boolean and bitwise flags, etc.
 */
public class CompactIntField extends AbstractDataIntField {
    public CompactIntField(int index, String name) {
        this(index, name, SerialFieldType.COMPACT_INT.forNamedField(name));
    }

    public CompactIntField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameSerialTypeAs(SerialFieldType.COMPACT_INT))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    public final void writeInt(DataOutput out, int value) throws IOException {
        IOUtil.writeCompactInt(out, value);
    }

    public final void writeInt(BufferedOutput out, int value) throws IOException {
        out.writeCompactInt(value);
    }

    public final int readInt(DataInput in) throws IOException {
        return IOUtil.readCompactInt(in);
    }

    public final int readInt(BufferedInput in) throws IOException {
        return in.readCompactInt();
    }
}
