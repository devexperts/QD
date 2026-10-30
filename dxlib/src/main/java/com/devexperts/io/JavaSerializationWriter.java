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
import java.io.ObjectOutputStream;

/**
 * Writer of Java Object Serialization that reuses one {@link ObjectOutputStream} for all sessions.
 * The first session and each session after {@link #reset()} write the bytes that a new
 * {@code ObjectOutputStream} writes for the same objects.
 */
class JavaSerializationWriter implements ObjectSerializationWriter {
    private final BufferedOutput out;
    private ObjectOutputStream oos;

    JavaSerializationWriter(BufferedOutput out) {
        this.out = out;
    }

    @Override
    public void start() throws IOException {
        if (oos == null) {
            oos = new ObjectOutputStream(out); // the constructor writes the stream header
        } else {
            out.write(ObjectIOImplUtil.STREAM_HEADER);
        }
    }

    @Override
    public void write(Object object) throws IOException {
        oos.writeObject(object);
    }

    @Override
    public void flush() throws IOException {
        oos.flush();
    }

    @Override
    public boolean reset() {
        try {
            if (oos != null) {
                oos.reset(); // forget object references; write reset token
                oos.flush(); // flush reset token to clear internal state
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
