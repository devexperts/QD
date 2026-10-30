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

import com.devexperts.services.Service;
import com.devexperts.services.ServiceProvider;

import java.io.IOException;

/**
 * Factory for the {@link ObjectSerializationReader reader} of one object serialization, plugged in as a
 * {@link Service service}.
 * Reading selects the factory by the {@link #getMarker() marker} that starts the serial form;
 * <a href="IOUtil.html#object">Object Serialization</a> in {@link IOUtil} describes the whole mechanism.
 *
 * <h3>Order</h3>
 *
 * If several reader factories use the same marker then the first one in the service order
 * ({@link ServiceProvider#order()}) is used.
 *
 * <h3>Threads</h3>
 *
 * A factory is thread-safe. A reader it creates is used by one thread at a time.
 */
@Service
public interface ObjectSerializationReaderFactory {
    /**
     * Returns the constant first byte of every serial form this factory reads.
     *
     * @see IOUtil#checkObjectSerializationMarker(byte)
     */
    public byte getMarker();

    /**
     * Creates a reader that uses the specified underlying input.
     */
    public ObjectSerializationReader createReader(ByteArrayInput in) throws IOException;
}
