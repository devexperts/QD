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

/**
 * Writes objects to the output it was created for, one session after another.
 * A session is {@link #start() start}, {@link #write(Object) write} per object and {@link #flush() flush},
 * after which the caller takes the bytes of the session from the output, and then {@link #reset() reset}.
 */
public interface ObjectSerializationWriter {
    /**
     * Starts a new session: writes the whole header, the marker included.
     */
    public void start() throws IOException;

    /**
     * Writes the next object of the session, which may be {@code null}.
     */
    public void write(Object object) throws IOException;

    /**
     * Flushes the objects written so far into the output.
     * The output holds the bytes of the session only after it, and the caller takes them before {@link #reset()}.
     */
    public void flush() throws IOException;

    /**
     * Forgets the session and returns whether this writer can be reused.
     * The caller takes all bytes before reset.
     * The caller may also reset a writer that started no session.
     */
    public boolean reset();
}
