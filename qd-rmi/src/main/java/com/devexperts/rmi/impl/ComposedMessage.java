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
package com.devexperts.rmi.impl;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.ThreadLocalPool;

import java.io.IOException;

class ComposedMessage {
    static final int RESERVE = 20;
    static final int MESSAGE_PART_MAX_SIZE = SystemProperties.getIntProperty("com.devexperts.rmi.messagePartMaxSize",
        ChunkPool.DEFAULT.getChunkSize() - RESERVE);

    private static final ChunkPoolWithReserveBytesForHeader CHUNK_POOL =
        new ChunkPoolWithReserveBytesForHeader("com.devexperts.rmi");

    private static final ThreadLocalPool<ComposedMessage> MESSAGE_POOL =
        new ThreadLocalPool<>("com.devexperts.rmi.Message", 3, 1024);


    static ComposedMessage allocateComposedMessage(MessageType messageType, RMIMessageKind kind, Object object) {
        ComposedMessage result = MESSAGE_POOL.poll();
        if (result == null)
            result = new ComposedMessage();
        result.kind = kind;
        result.type = messageType.getId();
        result.object = object;
        return result;
    }

    static void releaseComposedMessage(ComposedMessage composedMessage) {
        composedMessage.clear();
        MESSAGE_POOL.offer(composedMessage);
    }

    private final ChunkedOutput output = new ChunkedOutput(CHUNK_POOL);
    private ChunkList chunks;
    private RMIMessageKind kind;
    private int type;
    private Object object;
    private long sequence = -1;
    private boolean startedTransmission;

    // NOTE: hides true type of output from outside users
    BufferedOutput output() {
        return output;
    }

    RMIMessageKind kind() {
        return kind;
    }

    int type() {
        return type;
    }

    Object getObject() {
        return object;
    }

    long sequence() {
        return sequence;
    }

    boolean startedTransmission() {
        return startedTransmission;
    }

    void chunkTransmitted() {
        startedTransmission = true;
        chunks.poll(this).recycle(this);
    }

    Chunk firstChunk() {
        return chunks.get(0);
    }

    boolean isEmpty() {
        return chunks.isEmpty();
    }

    int chunksCount() {
        return chunks.size();
    }

    void flushOutputChunks() {
        chunks = output.getOutput(this);
    }

    void completeMessageParts(int sequence, ByteArrayOutput aux) {
        try {
            // append original message length and type id before message body
            long messageLength = IOUtil.getCompactLength(type()) + chunks.getTotalLength();
            aux.clear();
            aux.writeCompactLong(messageLength);
            aux.writeCompactInt(type());
            int prefixLength = aux.getPosition();

            Chunk firstChunk = chunks.get(0);
            chunks.setChunkRange(0, firstChunk.getOffset() - prefixLength, firstChunk.getLength() + prefixLength, this);
            System.arraycopy(aux.getBuffer(), 0, firstChunk.getBytes(), firstChunk.getOffset(), prefixLength);

            // append sequence number before every part of the message
            this.sequence = sequence;
            aux.clear();
            aux.writeCompactLong(sequence());
            int sequenceLength = aux.getPosition();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                chunks.setChunkRange(i, chunk.getOffset() - sequenceLength, chunk.getLength() + sequenceLength, this);
                System.arraycopy(aux.getBuffer(), 0, chunk.getBytes(), chunk.getOffset(), sequenceLength);
            }
            aux.clear();
            type = MessageType.PART.getId();
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    void completeMonolithicMessage() {
        try {
            // this case is for compatibility only, thus performance does not matter that much
            byte[] bytes = new byte[(int) chunks.getTotalLength()];
            ChunkedInput input = new ChunkedInput();
            input.addAllToInput(chunks, this);
            input.read(bytes);
            input.clear();
            chunks = ChunkList.wrap(bytes, this);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    void abortRemainingMessageParts() {
        // message part without message body cancels partially sent message
        chunks.recycle(this);
        try {
            output.writeCompactLong(sequence());
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException");
        }
        chunks = output.getOutput(this);
    }

    private void clear() {
        output.clear();
        if (chunks != null) {
            chunks.recycle(this);
            chunks = null;
        }
        kind = null;
        object = null;
        type = -1;
        sequence = -1;
        startedTransmission = false;
    }

    private static final class ChunkPoolWithReserveBytesForHeader extends ChunkPool {
        ChunkPoolWithReserveBytesForHeader(String poolName) {
            super(poolName, 3, 4096, 1024, MESSAGE_PART_MAX_SIZE + RESERVE, 1024);
        }

        @Override
        public Chunk getChunk(Object owner) {
            Chunk chunk = super.getChunk(owner);
            chunk.setRange(RESERVE, MESSAGE_PART_MAX_SIZE, owner);
            return chunk;
        }
    }
}
