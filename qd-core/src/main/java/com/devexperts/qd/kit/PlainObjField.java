/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.kit;

import java.io.*;

import com.devexperts.io.*;
import com.devexperts.qd.SerialFieldType;

/**
 * The <code>PlainObjField</code> represents an object field with plain serialized form.
 * It uses standard Java object serialization. Note that this serialization is highly
 * ineffective and shall be substituted with more effective specialized version if possible.
 *
 * @deprecated  Use {@link ByteArrayField} with custom serialization whenever possible,
 * or use {@link MarshalledObjField} that prevents unnecessary costly deserialization of
 * objects in multiplexor nodes.
 *
 * <p>Note: extension of this class is not supported because of the high-performance architecture for
 *    binary protocol reading/writing.
 */
public final class PlainObjField extends AbstractDataObjField {
    public PlainObjField(int index, String name) {
        super(index, name, SerialFieldType.SERIAL_OBJECT);
    }

    public void writeObj(DataOutput out, Object value) throws IOException {
        IOUtil.writeObject(out, value);
    }

    public void writeObj(BufferedOutput out, Object value) throws IOException {
        out.writeObject(value);
    }

    public Object readObj(DataInput in) throws IOException {
        return IOUtil.readObject(in);
    }

    public Object readObj(BufferedInput in) throws IOException {
        return in.readObject();
    }
}
