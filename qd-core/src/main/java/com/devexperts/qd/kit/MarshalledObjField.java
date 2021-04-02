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
import com.devexperts.io.Marshalled;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.util.Base64;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The <code>MarshalledObjField</code> represents an object field with plain Java serialized form.
 * After reading from {@link DataInput} the object is stored in {@link Marshalled} class,
 * so that [slow] deseralization can be perfromed only when needed.
 * It uses standard Java object serialization. Note that this serialization is highly
 * ineffective and shall be substituted with more effective specialized version.
 * It is recommended to use {@link ByteArrayField} with custom serialization whenever possible.
 *
 * <p>Whenever the code is written to work with this field type, one should use
 * {@link Marshalled#unwrap} to correctly handle the case of the naked object being received locally
 * from the same JVM and {@link Marshalled} that was received from somewhere else.
 */
public class MarshalledObjField extends AbstractDataObjField {
    public MarshalledObjField(int index, String name) {
        super(index, name, SerialFieldType.SERIAL_OBJECT);
    }

    public final void writeObj(DataOutput out, Object value) throws IOException {
        IOUtil.writeObject(out, value);
    }

    public final void writeObj(BufferedOutput out, Object value) throws IOException {
        out.writeObject(value);
    }

    public final Object readObj(DataInput in) throws IOException {
        return Marshalled.forBytes(IOUtil.readByteArray(in));
    }

    public final Object readObj(BufferedInput in) throws IOException {
        return Marshalled.forBytes(in.readByteArray());
    }

    @Override
    public String toString(Object value) {
        if (value instanceof Marshalled<?>) {
            Marshalled<?> marshalled = (Marshalled<?>) value;
            // human-readable string, plus BASE64 binary to restore it in parse
            return marshalled + " " + Base64.DEFAULT_UNPADDED.encode(marshalled.getBytes());
        }
        if (value == null)
            return null;
        return String.valueOf(value);
    }

    @Override
    public Object parseString(String value) {
        if (value == null)
            return null;
        // find Base64 part from end and decode
        int i = value.lastIndexOf(' ');
        return Marshalled.forBytes(Base64.DEFAULT_UNPADDED.decode(value.substring(i + 1)));
    }
}


