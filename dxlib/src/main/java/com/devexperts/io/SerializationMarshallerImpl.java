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
package com.devexperts.io;

import java.io.IOException;
import java.io.InvalidClassException;

/**
 * Converts object to/from bytes using Java Object Serialization via
 * {@link IOUtil#objectToBytes(Object) IOUtil.objectToBytes(Object)} and
 * {@link IOUtil#bytesToObject(byte[], ClassLoader) IOUtil.bytesToObject(byte[], ClassLoader)} methods.
 */
final class SerializationMarshallerImpl<T> extends Marshaller<T> {
    static final Class<Object> OBJECT_TYPE = Object.class;

    private final Class<T> type;

    SerializationMarshallerImpl(Class<T> type) {
        if (type == null)
            throw new NullPointerException();
        this.type = type;
    }

    @Override
    public void writeObjectTo(BufferedOutput out, T object) throws IOException {
        if (object == null)
            throw new NullPointerException();
        if (!isCorrectType(object))
            throw new IllegalArgumentException("Invalid type " + object.getClass().getName() + ", expected " + type.getName());
        ObjectSerializer.writeBody(out, object);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T readObjectFrom(BufferedInput in, int length, SerialClassContext serialClassContext) throws IOException {
        if (length < 0)
            throw new IllegalArgumentException();
        if (serialClassContext == null)
            throw new NullPointerException();
        Object object = ObjectDeserializer.readBody(in, length, serialClassContext);
        if (!isCorrectType(object))
            throw new InvalidClassException("Invalid type " + object.getClass().getName() + ", expected " + type.getName());
        return (T) object;
    }

    private boolean isCorrectType(Object object) {
        return type == OBJECT_TYPE || type.isInstance(object);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SerializationMarshallerImpl && type.equals(((SerializationMarshallerImpl<?>) o).type));
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return "SerializationMarshaller{type=" + type + '}';
    }
}
