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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class VoidIntField extends AbstractDataIntField {
    public VoidIntField(int index, String name) {
        super(index, name, SerialFieldType.VOID);
    }

    public final String getString(RecordCursor cursor) {
        // return plain integer string for debugging purposes
        return super.getString(cursor);
    }

    public final void setString(RecordCursor cursor, String value) {
        // does not parse nor set anything
    }

    public final void write(BufferedOutput out, RecordCursor cursor) throws IOException {
        // does not write anything
    }

    public final void read(BufferedInput in, RecordCursor cursor) throws IOException {
        // does not read nor set anything
    }

    public final void writeInt(DataOutput out, int value) {
        // does not write anything
    }

    public final void writeInt(BufferedOutput out, int value) {
        // does not write anything
    }

    public final int readInt(DataInput in) {
        return 0;
    }

    public final int readInt(BufferedInput in) {
        return 0;
    }
}
