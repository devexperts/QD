/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.kit;

import java.io.DataInput;
import java.io.DataOutput;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.SerialFieldType;

public class VoidIntField extends AbstractDataIntField {
    public VoidIntField(int index, String name) {
        super(index, name, SerialFieldType.VOID);
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
