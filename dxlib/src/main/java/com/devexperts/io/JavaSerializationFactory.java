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

import com.devexperts.annotation.Internal;
import com.devexperts.services.ServiceProvider;

import java.io.IOException;

/**
 * Java Object Serialization as an {@link ObjectSerializationReaderFactory} under the marker {@code 0xAC}, the first
 * byte of the stream header, and as an {@link ObjectSerializationWriterFactory} under the name {@code DX.java}.
 */
@Internal
@ServiceProvider(order = 100)
public final class JavaSerializationFactory
    implements ObjectSerializationReaderFactory, ObjectSerializationWriterFactory
{
    static final String NAME = "DX.java";
    static final byte MARKER = ObjectIOImplUtil.STREAM_HEADER[0];

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public byte getMarker() {
        return MARKER;
    }

    @Override
    public ObjectSerializationWriter createWriter(ByteArrayOutput out) {
        return new JavaSerializationWriter(out);
    }

    @Override
    public ObjectSerializationReader createReader(ByteArrayInput in) throws IOException {
        return new JavaSerializationReader(in);
    }
}
