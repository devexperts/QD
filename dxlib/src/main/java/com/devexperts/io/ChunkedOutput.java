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
package com.devexperts.io;

import com.devexperts.util.SystemProperties;

import java.io.IOException;

/**
 * Implementation of {@link BufferedOutput} using {@link ChunkList} as an output.
 * Written chunks can be retrieved with {@link #getOutput(Object) getOutput} method.
 * Subclasses of this implementation can override {@link #flush() flush} method to
 * write composed chunks to an underlying persistence mechanism as soon as they are complete.
 */
public class ChunkedOutput extends BufferedOutput {
    /**
     * Maximal number of bytes to copy when making a decision to merge two chunks together
     * in {@link #writeFromChunk(Chunk, Object)} method.
     */
    protected static final int MAX_COPY_BYTES =
        SystemProperties.getIntProperty(ChunkedOutput.class, "maxCopyBytes", 256);

    private final ChunkPool pool;
    private ChunkList chunks;

    /**
     * Constructs new chunked output with {@link ChunkPool#DEFAULT default} pool.
     */
    public ChunkedOutput() {
        this(ChunkPool.DEFAULT);
    }

    /**
     * Constructs new chunked output with a specified pool.
     *
     * @param pool chunk pool to use for acquiring new chunks
     */
    public ChunkedOutput(ChunkPool pool) {
        if (pool == null)
            throw new NullPointerException();
        this.pool = pool;
    }

    /**
     * Clears this chunked output and recycles all contained chunks.
     * The {@link #totalPosition() totalPosition} is reset to 0.
     */
    public void clear() {
        totalPositionBase = 0;
        resetBuffer();
        ChunkList chunks = this.chunks;
        if (chunks == null)
            return;
        this.chunks = null;
        chunks.recycle(this);
    }

    /**
     * Retrieves generated output chunks for specified owner.
     * This method preserves {@link #totalPosition() totalPosition}.
     *
     * @param owner new owner for generated chunk list
     * @return generated output chunk list or {@code null} if there is no output yet
     */
    public ChunkList getOutput(Object owner) {
        if (chunks == null)
            return null;
        completeOrRecycleLastChunk();
        totalPositionBase += position; // retain totalPositionBase+position invariant
        resetBuffer();
        ChunkList result = chunks;
        chunks = null;
        result.handOver(this, owner);
        return result;
    }

    /**
     * {@inheritDoc}
     * This implementation does {@link #flush()}.
     */
    @Override
    public void close() throws IOException {
        flush();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Subclasses shall override {@link #flush() flush}, call {@link #getOutput(Object) getOutput}
     * from inside it to obtain completed chunks, and write them to an underlying persistence mechanism.
     *
     * <p>This implementation does nothing.
     */
    @Override
    public void flush() throws IOException {}

    /**
     * Discards n last bytes of output.
     *
     * @param n the number of bytes to be discarded
     * @return the actual number of bytes discarded
     */
    public long discard(long n) {
        if (n <= 0)
            return 0;
        if (chunks == null)
            return 0;
        long remaining = n;
        Chunk c = chunks.get(chunks.size() - 1);
        while (remaining >= position - c.getOffset()) {
            remaining -= position - c.getOffset();
            totalPositionBase += c.getOffset(); // dropping this chunk, totalPositionBase is now a totalPosition
            chunks.pollLast(this).recycle(this);
            if (chunks.isEmpty()) {
                chunks.recycle(this);
                chunks = null;
                resetBuffer();
                return n - remaining;
            }
            c = chunks.get(chunks.size() - 1);
            buffer = c.getBytes();
            position = limit = c.getOffset() + c.getLength();
            totalPositionBase -= c.getOffset(); // correct totalPosition invariant = totalPositionBase + offset
        }
        position -= remaining;
        return n;
    }

    // ========== BufferedOutput Implementation ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFromChunk(Chunk chunk, Object owner) throws IOException {
        if (chunk.getLength() <= MAX_COPY_BYTES || chunk.getPool() != pool) {
            // Incoming chunk is small or it comes from a different pool:
            // just copy incoming chunk and release incoming chunk.
            super.writeFromChunk(chunk, owner);
            return;
        }
        // Big chunk from the same pool -- append incoming chunk to the list
        ensureChunks();
        completeOrRecycleLastChunk();
        chunk.handOver(owner, this);
        chunks.add(chunk, this);
        totalPositionBase += chunk.getLength(); // so that totalPosition + position increases by chunk length
        setCompletedBuffer(chunk);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeAllFromChunkList(ChunkList chunks, Object owner) throws IOException {
        if (chunks.getPool() == pool && this.chunks == null) {
            // fast path for incoming chunks from the same pool
            chunks.handOver(owner, this);
            this.chunks = chunks;
            if (!chunks.isEmpty()) {
                // reopen last chunk for writing
                int lastIndex = chunks.size() - 1;
                Chunk c = chunks.get(lastIndex);
                buffer = c.getBytes();
                position = c.getOffset() + c.getLength();
                limit = buffer.length;
                // adjust total position before we reopen chunk
                totalPositionBase += chunks.getTotalLength() - position;
                // now actually adjust chunk limit
                chunks.setChunkRange(lastIndex, c.getOffset(), limit - c.getOffset(), this);
            }
            return;
        }
        Chunk chunk;
        while ((chunk = chunks.poll(owner)) != null)
            writeFromChunk(chunk, owner);
        chunks.recycle(owner);
    }

    @Override
    protected void needSpace() throws IOException {
        // Note: strictly speaking ChunkedOutput can work when (position < limit), but it will require call of
        // currentChunk.setLength() with proper argument. We avoid that code here in favor of assertion check.
        checkEOB();
        flush();
        ensureChunks();
        Chunk c = pool.getChunk(this);
        chunks.add(c, this);
        totalPositionBase += position - c.getOffset(); // so that totalPosition + position remains invariant
        buffer = c.getBytes();
        position = c.getOffset();
        limit = position + c.getLength();
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "ChunkedOutput{" +
            "totalPosition=" + totalPosition() + "," +
            "buffer.length=" + buffer.length + "," +
            "position=" + position + "," +
            "limit=" + position + "," +
            "chunks=" + chunks +
            "}";
    }

    // ========== private utility methods ==========

    private void ensureChunks() {
        if (chunks == null)
            chunks = pool.getChunkList(this);
    }

    private void completeOrRecycleLastChunk() {
        if (chunks.isEmpty())
            return;
        Chunk c = chunks.get(chunks.size() - 1);
        if (position > c.getOffset()) {
            // something was written to the last chunk -- trim its range & set limit to position
            chunks.setChunkRange(chunks.size() - 1, c.getOffset(), position - c.getOffset(), this);
            limit = position;
        } else {
            // last chunk is empty -- recycle it
            chunks.pollLast(this).recycle(this);
            if (chunks.isEmpty()) {
                // no more chunks -- reset
                totalPositionBase += position; // retain totalPositionBase+position invariant
                resetBuffer();
            } else {
                // reposition at the end of last chunk
                setCompletedBuffer(chunks.get(chunks.size() - 1));
            }
        }
    }

    private void setCompletedBuffer(Chunk c) {
        totalPositionBase += position - c.getOffset() - c.getLength(); // so that totalPosition + position remains invariant
        buffer = c.getBytes();
        position = c.getOffset() + c.getLength();
        limit = position;
    }

    private void resetBuffer() {
        buffer = EMPTY_BYTE_ARRAY;
        position = 0;
        limit = 0;
    }
}
