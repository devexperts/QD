/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

/**
 * Reader of Java Object Serialization that reuses one {@link ObjectInputStream} for all sessions.
 */
class JavaSerializationReader implements ObjectSerializationReader {
    private final ByteArrayInput in;
    private final Stream stream;

    JavaSerializationReader(ByteArrayInput in) throws IOException {
        this.in = in;
        this.stream = new Stream(in);
    }

    /**
     * @throws StreamCorruptedException if the stream header is invalid
     */
    @Override
    public void start(SerialClassContext serialContext) throws IOException {
        stream.serialContext = serialContext;
        for (byte b : ObjectIOImplUtil.STREAM_HEADER) {
            if (in.readByte() != b)
                throw new StreamCorruptedException("invalid stream header");
        }
    }

    @Override
    public Object read() throws IOException {
        try {
            return stream.readObject();
        } catch (ClassNotFoundException e) {
            InvalidClassException invalidClass = new InvalidClassException("Class not found: " + e.getMessage());
            invalidClass.initCause(e);
            throw invalidClass;
        }
    }

    @Override
    public boolean reset() {
        stream.serialContext = null;
        in.setBuffer(ObjectIOImplUtil.STREAM_RESET);
        try {
            return stream.readLong() == ObjectIOImplUtil.RESET_MAGIC && in.getPosition() == in.getLimit();
        } catch (IOException e) {
            return false;
        } finally {
            in.setBuffer(null);
        }
    }

    private static class Stream extends ObjectInputStream {
        private SerialClassContext serialContext;

        Stream(ByteArrayInput in) throws IOException {
            super(in);
        }

        @Override
        protected void readStreamHeader() throws IOException {
            // There is no header available during instantiation of this stream. Skip header check.
        }

        @Override
        @SuppressWarnings("rawtypes")
        protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (ClassUtil.isPrimitiveType(desc.getName()))
                return ClassUtil.getTypeClass(desc.getName(), null);
            try {
                serialContext.check(desc.getName());
            } catch (ClassNotFoundException e) {
                throw new InvalidClassException(e.getMessage());
            }
            try {
                return ClassUtil.getTypeClass(desc.getName(), serialContext.getClassLoader());
            } catch (ClassNotFoundException ignored) {
                // try super implementation
            }
            return super.resolveClass(desc);
        }
    }
}
