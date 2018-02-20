/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.nio.test;

import java.io.IOException;

import com.devexperts.connector.proto.*;
import com.devexperts.io.ChunkList;

// test factory that does nothing
class TestApplicationConnectionFactory extends ApplicationConnectionFactory {
    @Override
    public ApplicationConnection<?> createConnection(TransportConnection transportConnection)
        throws IOException
    {
        return new ApplicationConnection<TestApplicationConnectionFactory>(this, transportConnection) {
            @Override
            public ChunkList retrieveChunks(Object owner) {
                return null;
            }

            @Override
            public boolean processChunks(ChunkList chunks, Object owner) {
                return false;
            }
        };
    }

    @Override
    public String toString() {
        return "Test";
    }
}
