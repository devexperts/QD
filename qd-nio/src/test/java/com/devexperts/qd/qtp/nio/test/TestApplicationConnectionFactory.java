/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.nio.test;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;

import java.io.IOException;

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
