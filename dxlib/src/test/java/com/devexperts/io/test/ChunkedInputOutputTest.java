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

import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.ChunkedOutput;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ChunkedInputOutputTest {
    private static final int MAGIC = (int) Math.round(((Math.sqrt(5) - 1) / 2) * (1L << 32));

    public static final int CHUNK_SIZE = 8192; // default

    private static final int ITERATIONS = 1000;

    @Test
    public void testInputBytes() throws IOException {
        ChunkedInput in = new ChunkedInput();
        Random r0 = new Random(20121226);
        Random r1 = new Random(20121226);
        Random r2 = new Random(20121226);
        int total = 0;
        int checked = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            Chunk chunk = ChunkPool.DEFAULT.getChunk(this);
            byte[] bytes = chunk.getBytes();
            assertEquals(0, chunk.getOffset());
            assertEquals(CHUNK_SIZE, chunk.getLength());
            assertEquals(CHUNK_SIZE, bytes.length);
            int ofs = r0.nextInt(CHUNK_SIZE - 2);
            int len = r0.nextInt(CHUNK_SIZE - ofs - 1) + 1;
            for (int j = 0; j < len; j++)
                bytes[ofs + j] = (byte) r1.nextInt(256);
            chunk.setRange(ofs, len, this);
            in.addToInput(chunk, this);
            total += len;
            assertEquals(checked, in.totalPosition());
            assertEquals(total - checked, in.available());
            assertEquals(total - checked != 0, in.hasAvailable());
            int checkLen = i == ITERATIONS - 1? total - checked : r0.nextInt(total - checked + 1);
            for (int j = 0; j < checkLen; j++)
                assertEquals(r2.nextInt(256), in.read());
            checked += checkLen;
            assertEquals(checked, in.totalPosition());
            assertEquals(total - checked, in.available());
            assertEquals(total - checked != 0, in.hasAvailable());
        }
    }

    @Test
    public void testInputMark() throws IOException {
        ChunkedInput in = new ChunkedInput();
        // add 3.5 chunks worth of random bytes
        int size1 = (int) (3.5 * CHUNK_SIZE) & ~1; // make sure it is even
        in.addToInput(fill(new byte[size1], 0), 0, size1);
        assertEquals(size1, in.available());
        assertEquals(0, in.totalPosition());
        // mark & read them
        in.mark();
        byte[] buf1 = new byte[size1];
        in.readFully(buf1);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf1, 0);
        // reset & read again
        in.reset();
        assertEquals(size1, in.available());
        assertEquals(0, in.totalPosition());
        in.readFully(buf1);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf1, 0);
        // reset again & read again
        in.reset();
        assertEquals(size1, in.available());
        assertEquals(0, in.totalPosition());
        in.readFully(buf1);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf1, 0);
        // reset & read only half
        int size2 = size1 / 2;
        byte[] buf2 = new byte[size2];
        in.reset();
        assertEquals(size1, in.available());
        assertEquals(0, in.totalPosition());
        in.readFully(buf2);
        assertEquals(size2, in.available());
        assertEquals(size2, in.totalPosition());
        verifyFillAndClear(buf2, 0);
        // remark and read last half
        in.mark();
        assertEquals(size2, in.available());
        assertEquals(size2, in.totalPosition());
        in.readFully(buf2);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf2, size2);
        // reset & read again last half
        in.reset();
        assertEquals(size2, in.available());
        assertEquals(size2, in.totalPosition());
        in.readFully(buf2);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf2, size2);
        // reset & read again last half
        in.reset();
        assertEquals(size2, in.available());
        assertEquals(size2, in.totalPosition());
        in.readFully(buf2);
        assertEquals(0, in.available());
        assertEquals(size1, in.totalPosition());
        verifyFillAndClear(buf2, size2);
    }

    @Test
    public void testWriteChunks() throws IOException {
        ChunkedOutput out1 = new ChunkedOutput();
        ChunkedOutput out2 = new ChunkedOutput();
        // out1: write 3.5 chunks worth of random bytes
        int size1 = (int) (3.5 * CHUNK_SIZE);
        out1.write(fill(new byte[size1], 0));
        assertEquals(size1, out1.totalPosition());
        // out2: write 2.3 chunks worth of random bytes
        int size2 = (int) (2.3 * CHUNK_SIZE);
        out2.write(fill(new byte[size2], size1));
        assertEquals(size2, out2.totalPosition());
        // write chunks from out2 to out1
        ChunkList chunks = out2.getOutput(this);
        assertEquals(size2, chunks.getTotalLength());
        out1.writeAllFromChunkList(chunks, this);
        assertEquals(size1 + size2, out1.totalPosition());
        assertEquals(size2, out2.totalPosition());
        // out1: write 2.1 chunks worth of random bytes
        int size3 = (int) (2.1 * CHUNK_SIZE);
        out1.write(fill(new byte[size3], size1 + size2));
        assertEquals(size1 + size2 + size3, out1.totalPosition());
        // transfer chunks from ou1 to input
        ChunkedInput in = new ChunkedInput();
        chunks = out1.getOutput(this);
        assertEquals(size1 + size2 + size3, chunks.getTotalLength());
        in.addAllToInput(chunks, this);
        assertEquals(size1 + size2 + size3, out1.totalPosition());
        assertEquals(size1 + size2 + size3, in.available());
        // read & verify all
        byte[] buf = new byte[size1 + size2 + size3];
        in.readFully(buf);
        verifyFillAndClear(buf, 0);
    }

    private byte[] fill(byte[] buf, int offset) {
        for (int i = 0; i < buf.length; i++)
            buf[i] = rndByteAtOffset(i + offset);
        return buf;
    }

    private void verifyFillAndClear(byte[] buf, int offset) {
        for (int i = 0; i < buf.length; i++) {
            byte b = buf[i];
            byte x = rndByteAtOffset(i + offset);
            if (b != x)
                fail("buf[" + i + "]: " + b + " != " + x);
            buf[i] = 0;
        }
    }

    private static byte rndByteAtOffset(int offset) {
        return (byte) ((offset * MAGIC) >>> (32 - 8));
    }
}
