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

import com.devexperts.services.ServiceProvider;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory registered through {@code META-INF/services} for tests; its serial form is its marker followed by
 * the serial form of Java Object Serialization.
 * It takes a service order after {@link JavaSerializationFactory}, so that the tests of this module write
 * with Java Object Serialization unless one selects this factory.
 */
@ServiceProvider(order = 200)
public class TestSerializationFactory
    implements ObjectSerializationReaderFactory, ObjectSerializationWriterFactory
{

    public static final byte MARKER = (byte) 0xF9;

    public static final AtomicInteger WRITER_RESETS = new AtomicInteger();
    public static final AtomicInteger READER_RESETS = new AtomicInteger();

    // makes writers and readers fail to forget their sessions, so that a test can check that none is reused
    public static volatile boolean failReset;

    // makes writers and readers throw an unchecked exception when they forget their sessions
    public static volatile boolean throwOnReset;

    public static final AtomicInteger WRITERS_CREATED = new AtomicInteger();

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public byte getMarker() {
        return MARKER;
    }

    @Override
    public ObjectSerializationWriter createWriter(ByteArrayOutput out) throws IOException {
        WRITERS_CREATED.incrementAndGet();
        return new MarkedWriter(out, new JavaSerializationWriter(out));
    }

    @Override
    public ObjectSerializationReader createReader(ByteArrayInput in) throws IOException {
        return new MarkedReader(in, new JavaSerializationReader(in));
    }

    private static class MarkedWriter implements ObjectSerializationWriter {
        private final ByteArrayOutput out;
        private final ObjectSerializationWriter java;

        MarkedWriter(ByteArrayOutput out, ObjectSerializationWriter java) {
            this.out = out;
            this.java = java;
        }

        @Override
        public void start() throws IOException {
            out.writeByte(MARKER);
            java.start();
        }

        @Override
        public void write(Object object) throws IOException {
            java.write(object);
        }

        @Override
        public void flush() throws IOException {
            java.flush();
        }

        @Override
        public boolean reset() {
            WRITER_RESETS.incrementAndGet();
            if (throwOnReset)
                throw new IllegalStateException("The test serialization factory throws on reset");
            return !failReset && java.reset();
        }
    }

    private static class MarkedReader implements ObjectSerializationReader {
        private final ByteArrayInput in;
        private final ObjectSerializationReader java;

        MarkedReader(ByteArrayInput in, ObjectSerializationReader java) {
            this.in = in;
            this.java = java;
        }

        @Override
        public void start(SerialClassContext context) throws IOException {
            if (in.readByte() != MARKER)
                throw new StreamCorruptedException("Not a test serialization stream");
            java.start(context);
        }

        @Override
        public Object read() throws IOException {
            return java.read();
        }

        @Override
        public boolean reset() {
            READER_RESETS.incrementAndGet();
            if (throwOnReset)
                throw new IllegalStateException("The test serialization factory throws on reset");
            return !failReset && java.reset();
        }
    }
}
