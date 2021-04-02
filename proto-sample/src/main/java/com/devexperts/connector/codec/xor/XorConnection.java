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
package com.devexperts.connector.codec.xor;

import com.devexperts.connector.codec.CodecConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;

import java.io.IOException;

/**
 * Wrapping connection that XOR's all incoming and outgoing data.
 */
class XorConnection extends CodecConnection<XorConnectionFactory> {

    private final ChunksXorer readingXorer;
    private final ChunksXorer writingXorer;

    XorConnection(ApplicationConnectionFactory delegateFactory, XorConnectionFactory factory, TransportConnection transportConnection) throws IOException {
        super(delegateFactory, factory, transportConnection);
        this.readingXorer = new ChunksXorer(factory);
        this.writingXorer = new ChunksXorer(factory);
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        ChunkList result = writingXorer.xorChunks(delegate.retrieveChunks(writingXorer));
        if (result != null)
            result.handOver(writingXorer, owner);
        return result;
    }

    @Override
    public boolean processChunks(ChunkList chunks, Object owner) {
        chunks.handOver(owner, readingXorer);
        return delegate.processChunks(readingXorer.xorChunks(chunks), readingXorer);
    }
}
