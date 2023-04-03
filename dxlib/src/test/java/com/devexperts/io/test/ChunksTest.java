/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChunksTest {

    private Random rnd;

    private final TrackingChunkPool pool = new TrackingChunkPool();

    @Before
    public void setUp() {
        rnd = new Random(12345);
        pool.checkAndClearCounters(0, 0, 0, 0);
    }

    @Test
    public void testChunk1() {
        Object owner = new Object();
        byte[] bytes = randomByteArray(100);

        Chunk chunk = Chunk.wrap(bytes, owner);
        assertEquals(0, chunk.getOffset());
        assertEquals(100, chunk.getLength());
        assertEquals(bytes, chunk.getBytes());
        assertFalse(chunk.isReadOnly());
        chunk.setLength(0, owner);
        assertEquals(0, chunk.getLength());
        chunk.setRange(10, 80, owner);
        assertEquals(10, chunk.getOffset());
        assertEquals(80, chunk.getLength());

        // index checks
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.setLength(91, owner));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.setLength(-1, owner));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.setRange(2000000000, 2000000000, owner));

        Object newOwner = new Object();
        chunk.handOver(owner, newOwner);

        // owner checks
        assertThrows(IllegalStateException.class, () -> chunk.setLength(10, owner));
        assertThrows(IllegalStateException.class, () -> chunk.setRange(10, 10, null));
        assertThrows(IllegalStateException.class, () -> chunk.markReadOnly(owner));
        assertThrows(IllegalStateException.class, () -> chunk.recycle(owner));
        assertThrows(IllegalStateException.class, () -> chunk.handOver(owner, newOwner));

        chunk.recycle(newOwner);
        assertThrows(IllegalStateException.class, () -> chunk.recycle(newOwner));
        assertThrows(IllegalStateException.class, () -> chunk.handOver(newOwner, this));
        assertThrows(IllegalStateException.class, () -> chunk.setLength(10, newOwner));
    }

    @Test
    public void testChunk2() {
        Object owner = new Object();
        byte[] bytes = randomByteArray(100);
        Chunk chunk = Chunk.wrap(bytes, 10, 20, owner);
        assertFalse(chunk.isReadOnly());
        chunk.markReadOnly(owner);
        assertTrue(chunk.isReadOnly());

        chunk.getBytes();
        assertThrows(IllegalStateException.class, () -> chunk.setLength(42, owner));
        assertThrows(IllegalStateException.class, () -> chunk.setRange(1, 2, owner));

        chunk.markReadOnly(this);
        chunk.recycle("Now everyone owns this chunk,");
        chunk.handOver("even these two strings.", 12345);
    }

    private byte[] randomByteArray(int n) {
        byte[] result = new byte[n];
        rnd.nextBytes(result);
        return result;
    }

    @Test
    public void testChunkList() {
        Object owner = new Object();
        ChunkList list = new ChunkList(owner);
        assertTrue(list.isEmpty());
        list.addAll(ChunkList.wrap(randomByteArray(10), owner), owner);
        assertEquals(1, list.size());
        assertEquals(10, list.getTotalLength());

        list.addAll(new ChunkList(owner), owner);
        assertEquals(1, list.size());
        assertEquals(10, list.getTotalLength());

        try {
            list.add(null, owner);
            fail();
        } catch (NullPointerException ignored) {}
        try {
            list.addAll(null, owner);
            fail();
        } catch (NullPointerException ignored) {}
        try {
            list.addAll(list, owner);
            fail();
        } catch (IllegalArgumentException ignored) {}

        try {
            list.get(1);
            fail();
        } catch (IndexOutOfBoundsException ignored) {}

        // owner checks
        try {
            list.handOver(this, 123);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.recycle(new Object());
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.markReadOnly(null);
            fail();
        } catch (IllegalStateException ignored) {}
        Object anotherOwner = new Object();
        Chunk anothersChunk = randomChunk(anotherOwner);
        pool.checkAndClearCounters(1, 0, 0, 0);
        try {
            list.add(anothersChunk, owner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.add(anothersChunk, anotherOwner);
            fail();
        } catch (IllegalStateException ignored) {}

        list.recycle(owner);
        try {
            list.recycle(owner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.handOver(owner, anotherOwner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.markReadOnly(owner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.add(randomChunk(owner), owner);
            fail();
        } catch (IllegalStateException ignored) {}
        pool.checkAndClearCounters(1, 0, 0, 0);
        try {
            list.addAll(new ChunkList(owner), owner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.poll(owner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.pollLast(owner);
            fail();
        } catch (IllegalStateException ignored) {}

        list = new ChunkList(owner); // not from pool
        Chunk chunk = randomChunk(owner);
        pool.checkAndClearCounters(1, 0, 0, 0);
        list.add(chunk, owner);
        int l = chunk.getLength();
        assertEquals(l, list.getTotalLength());
        final int N = 10000;
        list.addAll(pool.copyToChunkList(randomByteArray(N), 0, N, owner), owner);
        int k = pool.gc;
        pool.checkAndClearCounters(-1, 0, 1, 1);
        assertEquals(k + 1, list.size());
        assertSame(chunk, list.get(0));
        try {
            chunk.recycle(owner);
            fail();
        } catch (IllegalStateException ignored) {}
        pool.checkAndClearCounters(0, 1, 0, 0); // the chunk wasn't recycled however an attempt was made
        list.poll(owner);
        assertEquals(k, list.size());
        chunk.recycle(owner);
        pool.checkAndClearCounters(0, 1, 0, 0);

        list.handOver(owner, anotherOwner);
        assertFalse(list.isReadOnly());
        list.markReadOnly(anotherOwner);
        assertTrue(list.isReadOnly());
        for (Chunk c : list)
            assertTrue(c.isReadOnly());
        list.recycle("someone");
        list.markReadOnly("someone else");
        list.handOver("yet someone else", 555);
        pool.checkAndClearCounters(0, 0, 0, 0);
        try {
            list.add(Chunk.wrap(new byte[0], anotherOwner), anotherOwner);
            fail();
        } catch (IllegalStateException ignored) {}
        try {
            list.poll(owner);
            fail();
        } catch (IllegalStateException ignored) {}

        ChunkList anotherList = pool.getChunkList(owner);
        pool.checkAndClearCounters(0, 0, 1, 0);
        anotherList.addAll(list, owner);
        assertEquals(k, list.size()); // didn't changed
        assertEquals(k, anotherList.size());
        for (Chunk c : anotherList)
            assertTrue(c.isReadOnly());
        assertFalse(anotherList.isReadOnly());
        pool.checkAndClearCounters(0, 0, 0, 0);
        anotherList.recycle(owner);
        pool.checkAndClearCounters(0, 0, 0, 1);
    }

    @Test
    public void testChunkListAddPoll() {
        final int N = 1000;
        Object owner = new Object();
        ChunkList list = pool.getChunkList(owner);
        pool.checkAndClearCounters(0, 0, 1, 0);
        LinkedList<Chunk> etalon = new LinkedList<Chunk>();
        int totalLen = 0;
        for (int counter = 0; counter < N; counter++) {
            switch (rnd.nextInt(4)) {
                case 0: { // poll first
                    Chunk c = list.poll(owner);
                    if (c == null) {
                        assertTrue(etalon.isEmpty());
                        break;
                    }
                    assertSame(etalon.removeFirst(), c);
                    totalLen -= c.getLength();
                    c.recycle(owner);
                    pool.checkAndClearCounters(0, 1, 0, 0);
                    break;
                }
                case 1: { // poll last
                    Chunk c = list.pollLast(owner);
                    if (c == null) {
                        assertTrue(etalon.isEmpty());
                        break;
                    }
                    assertSame(etalon.removeLast(), c);
                    totalLen -= c.getLength();
                    c.recycle(owner);
                    pool.checkAndClearCounters(0, 1, 0, 0);
                    break;
                }
                default: { // add
                    Chunk c = randomChunk(owner);
                    list.add(c, owner);
                    etalon.add(c);
                    totalLen += c.getLength();
                    pool.checkAndClearCounters(1, 0, 0, 0);
                    break;
                }
            }
            assertEquals(etalon.size(), list.size());
            assertEquals(totalLen, list.getTotalLength());
            assertHaveSameContents(etalon.iterator(), list.iterator());
        }

        Object anotherOwner = new Object();
        ChunkList anotherList = new ChunkList(anotherOwner);
        list.handOver(owner, anotherOwner);
        anotherList.addAll(list, anotherOwner);
        pool.checkAndClearCounters(0, 0, 0, 1);
        assertEquals(totalLen, anotherList.getTotalLength());
        assertHaveSameContents(etalon.iterator(), anotherList.iterator());

        anotherList.recycle(anotherOwner);
        pool.checkAndClearCounters(0, etalon.size(), 0, 0);
    }

    private static void assertHaveSameContents(Iterator<Chunk> i1, Iterator<Chunk> i2) {
        while (i1.hasNext())
            assertSame(i1.next(), i2.next());
        assertFalse(i2.hasNext());
    }

    private Chunk randomChunk(Object owner) {
        Chunk result = pool.getChunk(owner);
        int len = rnd.nextInt(result.getLength() + 1);
        int off = rnd.nextInt(result.getLength() + 1 - len);
        rnd.nextBytes(result.getBytes());
        result.setRange(off, len, owner);
        return result;
    }

    @Test
    public void testChunkedInputOutput() throws IOException {
        final int N = 1000000;
        final int K = 30;
        final int M = 2 * K; // for marks;

        // phase 1: writing into chunked output
        ChunkedOutput out = new ChunkedOutput(pool);
        byte[] data = new byte[N + K];
        ArrayList<ChunkList> chunkLists = new ArrayList<ChunkList>();
        int curLen = 0;
        int totalLen = 0;
        while (totalLen + curLen < N) {
            int op = rnd.nextInt(7);
            switch (op) {
                case 0: // write from byte array
                case 1: // write from byte buffer
                case 2: // write from data input
                case 3: // write from input stream
                {
                    int l = rnd.nextInt(K + 1);
                    for (int i = 0; i < l; i++)
                        data[totalLen + curLen + i] = (byte) rnd.nextInt(256);
                    switch (op) {
                    case 0:
                        out.write(data, totalLen + curLen, l);
                        break;
                    case 1:
                        out.writeFromByteBuffer(ByteBuffer.wrap(data, totalLen + curLen, l));
                        break;
                    case 2:
                        out.writeFromDataInput(new ByteArrayInput(data, totalLen + curLen, l), l);
                        break;
                    case 3:
                        // one of the following two fields may randomly exceed l, but not both of them at once
                        int decision = rnd.nextInt(3);
                        int baiLen = decision == 0 ? l + 1 : l;
                        int writeLim = decision == 1 ? l + 1 : l;
                        assertEquals(l, out.writeFromInputStream(
                            new ByteArrayInput(data, totalLen + curLen, baiLen), writeLim));
                        break;
                    }
                    curLen += l;
                    break;
                }
                case 4: // discard
                {
                    long l = rnd.nextInt(K + 1);
                    long res = out.discard(l);
                    assertEquals(Math.min(l, curLen), res);
                    curLen -= res;
                    break;
                }
                case 5: // getOutput
                    ChunkList output = out.getOutput(this);
                    if (output == null) {
                        assertEquals(0, curLen);
                    } else {
                        chunkLists.add(output);
                        totalLen += curLen;
                        curLen = 0;
                    }
                    break;
                case 6: // clear();
                    out.clear();
                    curLen = 0;
                    break;
                default:
                    throw new AssertionError("fix nextInt argument");
            }
        }
        ChunkList output = out.getOutput(this);
        if (output == null) {
            assertEquals(0, curLen);
        } else {
            chunkLists.add(output);
            totalLen += curLen;
        }

        // hacking obtained chunks a little
        Collections.reverse(chunkLists);
        Chunk emptyChunk = pool.getChunk(this);
        emptyChunk.setRange(10, 0, this);
        chunkLists.get(chunkLists.size() / 2).add(emptyChunk, this);
        int roc = 0;
        int rol = 0;
        for (ChunkList chunks : chunkLists) {
            if (rnd.nextBoolean()) {
                chunks.markReadOnly(this);
                roc += chunks.size();
                rol++;
            }
        }

        // phase 2: reading from chunked input
        ChunkedInput in = new ChunkedInput();
        assertTrue(in.markSupported());
        byte[] d = new byte[K];
        ByteBuffer bb = ByteBuffer.wrap(d);
        ByteArrayOutput bao = new ByteArrayOutput(0);
        bao.setBuffer(d);
        int position = 0;
        int limit = 0;
        boolean markState = false;
        int markPoint = 0;
        while (position < totalLen) {
            int op = rnd.nextInt(10);
            switch (op) {
                case 0: // read to byte array
                case 1: // read to byte buffer
                case 2: // read to data input
                case 3: // read to input stream
                {
                    int l = rnd.nextInt(K + 1);
                    int res;
                    switch (op) {
                        case 0:
                            res = in.read(d, 0, l);
                            if (res == -1) res = 0;
                            break;
                        case 1:
                            bb.clear();
                            bb.limit(l);
                            in.readToByteBuffer(bb);
                            res = bb.position();
                            break;
                        case 2:
                            bao.clear();
                            bao.setLimit(l);
                            res = (int) in.readToDataOutput(bao, l);
                            break;
                        case 3:
                            bao.clear();
                            bao.setLimit(l);
                            res = (int) in.readToOutputStream(bao, l);
                            break;
                        default:
                            throw new AssertionError();
                    }

                    if (Math.min(l, limit - position) != res)
                        fail(makeDebugStr(op, position, limit, markState, markPoint, l, res));
                    for (int i = 0; i < res; i++)
                        if (data[position + i] != d[i])
                            fail(makeDebugStr(op, position, limit, markState, markPoint, l, res));
                    position += res;
                    break;
                }
                case 4: // skip
                {
                    int l = rnd.nextInt(K + 1);
                    int res = (int) in.skip(l);
                    if (Math.min(l, limit - position) != res)
                        fail(makeDebugStr(op, position, limit, markState, markPoint, l, res));
                    position += res;
                    break;
                }
                case 5: // rewind
                {
                    int l = rnd.nextInt(K + 1);
                    try {
                        in.rewind(l);
                        assertTrue(markState && position - l >= markPoint);
                        position -= l;
                    } catch (IllegalStateException e) {
                        assertTrue(!markState || position - l < markPoint);
                    }
                    break;
                }
                case 6: // mark/unmark
                {
                    if (markState) {
                        in.unmark();
                        markState = false;
                        markPoint = -1;
                    } else {
                        in.mark();
                        markState = true;
                        markPoint = position;
                    }
                    break;
                }
                case 7: // add to input
                    if (chunkLists.isEmpty())
                        break;
                    ChunkList chunks = chunkLists.remove(chunkLists.size() - 1);
                    limit += chunks.getTotalLength();
                    in.addAllToInput(chunks, this);
                    break;
                case 8: // clear
                    in.clear();
                    position = limit;
                    markState = false;
                    break;
                case 9: // try to reset
                    try {
                        in.reset();
                        assertTrue(markState);
                        position = markPoint;
                    } catch (IllegalStateException e) {
                        assertFalse(markState);
                    }
                    break;
                default:
                    throw new AssertionError("fix nextInt argument");
            }
            if (position > limit)
                fail(makeDebugStr(op, position, limit, markState, markPoint, 0, 0));
            if (in.available() != limit - position)
                fail(makeDebugStr(op, position, limit, markState, markPoint, 0, 0));
            if ((limit > position) != in.hasAvailable())
                fail(makeDebugStr(op, position, limit, markState, markPoint, 0, 0));
        }
        if (markState)
            in.unmark();
        assertEquals(pool.gc, pool.rc + roc);
        assertEquals(pool.gl, pool.rl + rol);
        pool.clearCounters();
    }

    private static String makeDebugStr(int op, int position, int limit, boolean markState, int markPoint,
        int l, int res)
    {
        return "op=" + op + " pos=" + position + " lim=" + limit + " ms=" + markState + " mp=" + markPoint +
            " l=" + l + " res=" + res;
    }
}
