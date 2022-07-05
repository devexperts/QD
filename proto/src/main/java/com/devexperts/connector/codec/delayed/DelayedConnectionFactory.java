/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector.codec.delayed;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.TransportConnection;

import java.io.IOException;

public class DelayedConnectionFactory extends CodecConnectionFactory {

    private long delay = 0; // delay period in millis
    private long bufferLimit; // delay buffer limit in bytes (0 = unlimited)

    DelayedConnectionFactory(ApplicationConnectionFactory delegate) {
        super(delegate);
    }

    public long getDelay() {
        return delay;
    }

    @Configurable(description = "output packets delay (millis)")
    public void setDelay(long delay) {
        if (delay < 0)
            throw new IllegalArgumentException("non-negative value expected");
        this.delay = delay;
    }

    public long getBufferLimit() {
        return bufferLimit;
    }

    @Configurable(description = "delayed data buffering limit (bytes), 0 - unlimited")
    public void setBufferLimit(long bufferLimit) {
        this.bufferLimit = bufferLimit;
    }

    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
        return new DelayedConnection(getDelegate(), this, transportConnection);
    }

    public String toString() {
        return "delayed+" + getDelegate().toString();
    }

}
