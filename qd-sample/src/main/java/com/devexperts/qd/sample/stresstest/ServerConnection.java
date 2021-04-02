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
package com.devexperts.qd.sample.stresstest;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

class ServerConnection extends ApplicationConnection<TSTServer> {
    final ArrayBlockingQueue<Integer> requests = new ArrayBlockingQueue<Integer>(100);

    private final AtomicLong messages = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();

    ServerConnection(TSTServer tstServer, TransportConnection transportConnection) {
        super(tstServer, transportConnection);
        requests.offer(1);
        synchronized (tstServer.connections) {
            tstServer.connections.add(this);
            tstServer.connections.notify();
        }
    }

    @Override
    protected void startImpl() {
        sendChunks();
    }

    public ChunkList retrieveChunks(Object owner) {
        ChunkList chunks = ChunkPool.DEFAULT.getChunkList(owner);
        for (Integer size; (size = requests.poll()) != null; ) {
            chunks.add(Chunk.wrap(factory.data, 0, size, owner), owner);
            messages.incrementAndGet();
            bytes.addAndGet(size);
        }
        return chunks;
    }

    public boolean processChunks(ChunkList chunks, Object owner) {
        chunks.recycle(owner);
        return true;
    }

    protected void closeImpl() {
        factory.failed = true;
    }

    void sendChunks() {
        notifyChunksAvailable();
    }

    long getMessagesCount() {
        return messages.getAndSet(0);
    }

    long getBytesCount() {
        return bytes.getAndSet(0);
    }
}
