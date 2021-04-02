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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The <code>PlainIntField</code> represents an integer field with plain serialized form.
 * It can be used for fields which are usually represented with large integer or
 * floating-point values, such as times, coefficients, amounts, etc.
 */
public class PlainIntField extends AbstractDataIntField {
    public PlainIntField(int index, String name) {
        this(index, name, SerialFieldType.INT);
    }

    public PlainIntField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameSerialTypeAs(SerialFieldType.INT))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    public final void writeInt(DataOutput out, int value) throws IOException {
        out.writeInt(value);
    }

    public final void writeInt(BufferedOutput out, int value) throws IOException {
        out.writeInt(value);
    }

    public final int readInt(DataInput in) throws IOException {
        return in.readInt();
    }

    public final int readInt(BufferedInput in) throws IOException {
        return in.readInt();
    }
}
