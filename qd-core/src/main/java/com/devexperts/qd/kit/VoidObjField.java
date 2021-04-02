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

public final class VoidObjField extends AbstractDataObjField {
    public VoidObjField(int index, String name) {
        super(index, name, SerialFieldType.VOID);
    }

    public final void writeObj(DataOutput out, Object value) {
        // does not write anything
    }

    public final void writeObj(BufferedOutput out, Object value) {
        // does not write anything
    }

    public final Object readObj(DataInput in) {
        return null;
    }

    public final Object readObj(BufferedInput in) {
        return null;
    }
}
