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

import com.devexperts.io.ChunkList;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Queue of ChunkLists with associated time stamps.
 */
class StampedChunksQueue {

    private static final int INITIAL_CAPACITY = 16; // shall be power of 2

    // total size of collected chunks in bytes (beware of ChunkList#getTotalLength complexity)
    private long totalSize = 0;

    private int head;
    private int tail;

    private ChunkList[] chunks;
    private long[] stamps;

    public StampedChunksQueue() {
        chunks = new ChunkList[INITIAL_CAPACITY];
        stamps = new long[INITIAL_CAPACITY];
    }

    public int size() {
        return (tail - head) & (chunks.length - 1);
    }

    public boolean isEmpty() {
        return tail == head;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getFirstTimestamp() {
        if (isEmpty())
            throw new NoSuchElementException();
        return stamps[head];
    }

    /**
     * Checks if the first chunk in the queue (if any) has passed specified border time
     *
     * @param borderTime defines the earliest timestamp allowed to send
     * @return {@code true} if the first chunk exists and is old enough to send, {@code false} otherwise
     */
    public boolean hasChunkToSend(long borderTime) {
        return tail != head && stamps[head] <= borderTime;
    }

    public void add(ChunkList chunkList, long currentTime) {
        ensureCapacity();
        int t = tail;
        chunks[t] = chunkList;
        stamps[t] = currentTime;
        tail = (t + 1) & (chunks.length - 1);
        totalSize += chunkList.getTotalLength();
    }

    public ChunkList remove() {
        if (isEmpty())
            throw new NoSuchElementException();
        int h = head;
        ChunkList chunkList = chunks[h];
        chunks[h] = null;
        stamps[h] = 0;
        head = (h + 1) & (chunks.length - 1);
        totalSize -= chunkList.getTotalLength();
        return chunkList;
    }

    public void adjustTimeStamps(long delta) {
        int h = head;
        int t = tail;
        if (t == h)
            return;
        int mask = chunks.length - 1;
        for (int i = h; i != t; i = (i + 1) & mask) {
            stamps[i] += delta;
        }
    }

    public void clearAndRecycle(Object owner) {
        int h = head;
        int t = tail;
        if (t == h)
            return;
        int mask = chunks.length - 1;
        for (int i = h; i != t; i = (i + 1) & mask) {
            chunks[i].recycle(owner);
        }
        Arrays.fill(chunks, null);
        Arrays.fill(stamps, 0);
        head = 0;
        tail = 0;
        totalSize = 0;
    }

    private void ensureCapacity() {
        int n = chunks.length;
        int size = (tail - head) & (n - 1);
        if (size != (n - 1))
            return;
        // the queue is almost full, so no precise calculation of ranges is needed
        int p = head;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, queue too big");
        // allocate both arrays before field assignment, so the queue stays intact in case of OOM
        ChunkList[] newChunks = new ChunkList[newCapacity];
        long[] newStamps = new long[newCapacity];
        System.arraycopy(chunks, p, newChunks, 0, r);
        System.arraycopy(chunks, 0, newChunks, r, p);
        System.arraycopy(stamps, p, newStamps, 0, r);
        System.arraycopy(stamps, 0, newStamps, r, p);
        chunks = newChunks;
        stamps = newStamps;
        head = 0;
        tail = size;
    }
}
