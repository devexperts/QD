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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StampedChunksQueueTest {

    public StampedChunksQueue chunksQueue;
    public ArrayDeque<ChunkList> chunks;
    public ArrayDeque<Long> stamps;
    private long baseTime;

    @Before
    public void setUp() throws Exception {
        chunksQueue = new StampedChunksQueue();
        chunks = new ArrayDeque<>();
        stamps = new ArrayDeque<>();
        baseTime = 10_000;
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGrowThenShrinkSmall() {
        doTestGrowThenShrink(8);
    }

    @Test
    public void testGrowThenShrinkLarge() {
        doTestGrowThenShrink(1000);
    }

    private void doTestGrowThenShrink(int n) {
        addAndCheck(n);
        removeAndCheck(n);
        assertTrue(chunks.isEmpty());
        assertTrue(chunksQueue.isEmpty());
    }

    @Test
    public void testGrowThenSliding() {
        addAndCheck(1000);
        for (int i = 0; i < 5000; i++) {
            addAndCheck(1);
            removeAndCheck(1);
        }
        removeAndCheck(1000);
        assertTrue(chunks.isEmpty());
        assertTrue(chunksQueue.isEmpty());
    }

    @Test(expected = NoSuchElementException.class)
    public void testPeakFirstTimestampOnEmpty() {
        addAndCheck(8);
        removeAndCheck(8);
        chunksQueue.getFirstTimestamp();
    }

    @Test
    public void testHasChunkToSend() {
        long time = baseTime;
        assertFalse("empty queue", chunksQueue.hasChunkToSend(baseTime));
        addAndCheck(8);
        assertEquals(time + 8, baseTime);
        assertTrue(chunksQueue.hasChunkToSend(time)); // first shall match
        removeAndCheck(4);
        assertFalse(chunksQueue.hasChunkToSend(time));
        assertTrue(chunksQueue.hasChunkToSend(time + 4));
    }

    @Test
    public void testClearAndRecycle() {
        addAndCheck(100);
        removeAndCheck(50);
        chunksQueue.clearAndRecycle(this);
        assertTrue(chunksQueue.isEmpty());
        chunks.forEach(chunkList -> {
            assertTrue(((MockChunkList) chunkList).recycled);
        });
    }

    @Test
    public void testTotalSize() {
        assertEquals(0, chunksQueue.getTotalSize());
        addAndCheck(100);
        assertEquals(chunks.stream().mapToLong(ChunkList::getTotalLength).sum(), chunksQueue.getTotalSize());
        removeAndCheck(50);
        assertEquals(chunks.stream().mapToLong(ChunkList::getTotalLength).sum(), chunksQueue.getTotalSize());
        removeAndCheck(50);
        assertEquals(0, chunksQueue.getTotalSize());
    }

    @Test
    public void testAdjustTimeStamps() {
        long time = baseTime;
        chunksQueue.adjustTimeStamps(10); // check on empty queue
        addAndCheck(100);
        removeAndCheck(50);
        assertFalse(chunksQueue.hasChunkToSend(time + 49));
        assertTrue(chunksQueue.hasChunkToSend(time + 50));
        assertFalse(chunksQueue.hasChunkToSend(time));
        chunksQueue.adjustTimeStamps(-50);
        assertTrue(chunksQueue.hasChunkToSend(time));
        for (int i = 0; i < 50; i++) {
            assertEquals(time + i, chunksQueue.getFirstTimestamp());
            chunksQueue.remove();
        }
        assertTrue(chunksQueue.isEmpty());
    }

    @Test(expected = NoSuchElementException.class)
    public void testRemoveOnEmpty() {
        chunksQueue.remove();
    }

    @Test(expected = NoSuchElementException.class)
    public void testRemoveOnEmptyAfterChange() {
        addAndCheck(5);
        removeAndCheck(5);
        chunksQueue.remove();
    }


    private void removeAndCheck(int n) {
        for (int i = 0; i < n; i++) {
            assertEquals(stamps.remove().longValue(), chunksQueue.getFirstTimestamp());
            assertSame(chunks.remove(), chunksQueue.remove());
            assertEquals(chunks.size(), chunksQueue.size());
        }
    }

    private void addAndCheck(int n) {
        for (int i = 0; i < n; i++) {
            ChunkList cl = new MockChunkList(this, ThreadLocalRandom.current().nextLong(1000));
            mirrorAdd(cl, baseTime++);
        }
        assertEquals(chunks.size(), chunksQueue.size());
    }

    private void mirrorAdd(ChunkList cl, long stamp) {
        chunksQueue.add(cl, stamp);
        chunks.add(cl);
        stamps.add(stamp);
    }

    static class MockChunkList extends ChunkList {
        private final long length;
        public boolean recycled;

        public MockChunkList(Object owner, long length) {
            super(owner);
            this.length = length;
        }

        @Override
        public void recycle(Object owner) {
            super.recycle(owner);
            recycled = true;
        }

        @Override
        public long getTotalLength() {
            return length;
        }
    }
}
