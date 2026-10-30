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

/**
 * Reads objects from the input it was created for, one session after another.
 * A session is {@link #start(SerialClassContext) start}, {@link #read() read} per object and
 * {@link #reset() reset}, in this order.
 */
public interface ObjectSerializationReader {
    /**
     * Starts a new session: reads the whole header, the marker included, and verifies it.
     * The caller selects the reader by the first byte of the input without consuming it.
     *
     * @param context controls which classes are allowed to load.
     */
    public void start(SerialClassContext context) throws IOException;

    /**
     * Reads the next object of the session and advances the input past it.
     * The caller reads as many objects as the writer wrote in the session.
     *
     * @throws InvalidClassException if a class of the object cannot be loaded.
     */
    public Object read() throws IOException;

    /**
     * Forgets the session and returns whether this reader can be reused.
     * The caller calls it once the input of the session is read to its end.
     */
    public boolean reset();
}
