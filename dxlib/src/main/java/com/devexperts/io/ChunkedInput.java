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

/**
 * Implementation of {@link BufferedInput} using {@link ChunkList} as an input.
 */
public final class ChunkedInput extends BufferedInput {

    /**
     * Underlying chunk list.
     * Zero-length chunks are not allowed here.
     */
    private final ChunkList chunks;

    /**
     * Index of the current chunk being read.
     * This can point beyond chunk list size if all data is read.
     */
    private int index;

    /**
     * Constructs new chunked input with {@link ChunkPool#DEFAULT default} pool.
     */
    public ChunkedInput() {
        this(ChunkPool.DEFAULT);
    }

    /**
     * Constructs new chunked input with a specified pool to acquire chunk list from..
     */
    public ChunkedInput(ChunkPool pool) {
        chunks = pool.getChunkList(this);
    }

    /**
     * Clears this chunked input, invalidates the mark and recycles all contained chunks.
     * The {@link #totalPosition() totalPosition} is reset to 0.
     */
    public void clear() {
        buffer = EMPTY_BYTE_ARRAY;
        position = 0;
        limit = 0;
        totalPositionBase = 0;
        markPosition = -1;
        index = chunks.size();
        recycleUpToIndex();
    }

    /**
     * Adds specified bytes to the input.
     *
     * @param bytes the bytes.
     * @param offset offset in the bytes.
     * @param length the length.
     */
    public void addToInput(byte[] bytes, int offset, int length) {
        if (needsAdvanceBuffer())
            advanceBuffer();
        chunks.add(bytes, offset, length);
        if (needsAdvanceBuffer())
            setBuffer(chunks.get(index));
    }

    /**
     * Adds specified chunk to the input.
     *
     * @param chunk the chunk to be added
     * @param owner owner of the chunk
     * @throws IllegalStateException if the given chunk is not read-only and its owner differs from the one specified
     */
    public void addToInput(Chunk chunk, Object owner) {
        if (chunk.getLength() > 0) {
            chunk.handOver(owner, this);
            chunks.add(chunk, this);
            if (index == chunks.size() - 1)
                setBuffer(chunk);
        } else
            chunk.recycle(owner);
    }

    /**
     * Adds specified chunks to the input.
     *
     * @param chunkList the chunk list with chunks to be added
     * @param owner owner of the chunks
     * @throws IllegalStateException if the given chunk is not read-only and its owner differs from the one specified
     */
    public void addAllToInput(ChunkList chunkList, Object owner) {
        if (chunkList.isReadOnly())
            for (int i = 0, n = chunkList.size(); i < n; i++)
                addToInput(chunkList.get(i), null); // owner doesn't matter since all chunks are also read-only
        else {
            for (Chunk c; (c = chunkList.poll(owner)) != null;)
                addToInput(c, owner);
            chunkList.recycle(owner);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAvailable() {
        return position < limit || index + 1 < chunks.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAvailable(int bytes) {
        long result = limit - position;
        if (result >= bytes)
            return true;
        for (int i = index + 1, n = chunks.size(); i < n; i++) {
            result += chunks.get(i).getLength();
            if (result >= bytes)
                return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() {
        long result = limit - position;
        for (int i = index + 1, n = chunks.size(); i < n; i++)
            result += chunks.get(i).getLength();
        return (int) Math.min(result, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) {
        IOUtil.checkRange(b, off, len);
        int result = 0;
        while (result < len) {
            if (position >= limit && readData() <= 0)
                return result > 0 ? result : -1;
            int n = Math.min(len - result, limit - position);
            System.arraycopy(buffer, position, b, off + result, n);
            position += n;
            result += n;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mark() {
        super.mark();
        recycleUpToMark();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unmark() {
        super.unmark();
        recycleUpToMark();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewind(long n) throws IllegalStateException {
        checkRewind(n); // throws exception if cannot rewind for specified distance
        while (n > 0) {
            if (index >= chunks.size() || position <= chunks.get(index).getOffset()) {
                Chunk c = chunks.get(--index);
                long totalPosition = totalPosition();
                buffer = c.getBytes();
                position = limit = c.getOffset() + c.getLength();
                totalPositionBase = totalPosition - position;
            }
            int k = (int) Math.min(n, position - chunks.get(index).getOffset());
            position -= k;
            n -= k;
        }
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "ChunkedInput{" +
            "totalPosition=" + totalPosition() + "," +
            "markPosition=" + markPosition + "," +
            "buffer.length=" + buffer.length + "," +
            "position=" + position + "," +
            "limit=" + position + "," +
            "chunks=" + chunks +
            "}";
    }

    // ==================== Auxiliary routines ====================

    private static final Chunk EMPTY_CHUNK = Chunk.wrap(EMPTY_BYTE_ARRAY, null);

    private void setBuffer(Chunk c) {
        long totalPosition = totalPosition();
        buffer = c.getBytes();
        position = c.getOffset();
        limit = position + c.getLength();
        totalPositionBase = totalPosition - position;
    }

    private void advanceBuffer() {
        assert needsAdvanceBuffer();
        setBuffer(++index < chunks.size() ? chunks.get(index) : EMPTY_CHUNK);
    }

    private boolean needsAdvanceBuffer() {
        return position == limit && index < chunks.size();
    }

    private void recycleUpToIndex() {
        while (index > 0) {
            chunks.poll(this).recycle(this);
            index--;
        }
    }

    private void recycleUpToMark() {
        if (markPosition < 0) {
            // not marked -- recycle everything we don't need now
            recycleUpToIndex();
            return;
        }
        if (index == 0)
            return; // working with the first chunk -- cannot recycle anything
        int curIndex = index;
        long curChunkStartPosition = totalPositionBase;
        if (index < chunks.size())
            curChunkStartPosition += chunks.get(index).getOffset();
        while (curChunkStartPosition > markPosition) {
            curIndex--;
            curChunkStartPosition -= chunks.get(curIndex).getLength();
        }
        // HERE: curChunkStartPosition <= markPosition ==> cannot recycle chunk at curIndex
        // recycle up to curIndex
        while (curIndex > 0) {
            chunks.poll(this).recycle(this);
            index--;
            curIndex--;
        }
    }

    // ==================== BufferedInput implementation ====================

    @Override
    protected int readData() {
        checkEOB(); // throws exception if not all data was read
        if (index >= chunks.size())
            return -1;
        advanceBuffer();
        if (markPosition < 0)
            recycleUpToIndex();
        return index >= chunks.size() ? -1 : limit - position;
    }
}
