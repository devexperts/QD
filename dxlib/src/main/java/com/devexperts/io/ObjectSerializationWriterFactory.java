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
 * Factory for the {@link ObjectSerializationWriter writer} of one object serialization, plugged in as a
 * {@link Service service}.
 * The system property {@link IOUtil#OBJECT_SERIALIZATION_PROPERTY} selects the factory that writes by its
 * {@link #getName() name}; <a href="IOUtil.html#object">Object Serialization</a> in {@link IOUtil} describes
 * the whole mechanism.
 *
 * <h3>Order</h3>
 *
 * If several writer factories use the same name then the first one in the service order
 * ({@link ServiceProvider#order()}) is used.
 * If the system property {@link IOUtil#OBJECT_SERIALIZATION_PROPERTY} is empty or missing, then the first writer
 * factory in the service order is used.
 *
 * <h3>Threads</h3>
 *
 * A factory is thread-safe. A writer it creates is used by one thread at a time.
 */
@Service
public interface ObjectSerializationWriterFactory {
    /**
     * Returns the constant name of this factory.
     */
    public String getName();

    /**
     * Creates a writer that uses the specified underlying output.
     */
    public ObjectSerializationWriter createWriter(ByteArrayOutput out) throws IOException;
}
