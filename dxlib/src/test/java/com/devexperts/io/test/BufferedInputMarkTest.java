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

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedInputPart;
import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkedInput;
import com.devexperts.io.StreamInput;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class BufferedInputMarkTest {
    private static final int SIZE = 17;

    private ByteArrayInput createByteArrayInput() {
        byte[] bytes = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) i;
        }
        ByteArrayInput in = new ByteArrayInput(bytes);
        return in;
    }

    @Test
    public void testByteArrayInput() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        check(in);
    }

    @Test
    public void testByteArrayInputPart() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        in.mark();
        BufferedInputPart part = new BufferedInputPart();
        part.setInput(in, SIZE);
        check(part);
    }

    @Test
    public void testByteArrayInputPart2() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        in.mark();
        BufferedInputPart part = new BufferedInputPart();
        part.setInput(in, SIZE);
        part.mark();
        BufferedInputPart part2 = new BufferedInputPart();
        part2.setInput(part, SIZE);
        check(part2);
    }

    @Test
    public void testStreamInput() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        StreamInput sin = new StreamInput(in);
        check(sin);
    }

    @Test
    public void testStreamInputPart() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        StreamInput sin = new StreamInput(in);
        sin.mark();
        BufferedInputPart part = new BufferedInputPart();
        part.setInput(sin, SIZE);
        check(part);
    }

    @Test
    public void testChunkedInputPart() throws IOException {
        Random r = new Random(20100213);
        for (int repeat = 0; repeat < 20; repeat++) {
            // create test data array with different pre/post data parts
            int pre = r.nextInt(6);
            int post = r.nextInt(6);
            byte[] bytes = new byte[pre + SIZE + post];
            for (int i = 0; i < SIZE; i++) {
                bytes[i + pre] = (byte) i;
            }
            // strip into small chuncks
            int offset = 0;
            ChunkedInput in = new ChunkedInput();
            while (offset < bytes.length) {
                int length = Math.min(r.nextInt(3) + 1, bytes.length - offset);
                Chunk chunk = Chunk.wrap(bytes, offset, length, this);
                in.addToInput(chunk, this);
                offset += length;
            }
            // skip pre
            assertEquals(pre, in.skip(pre));
            // create part & test it
            in.mark();
            BufferedInputPart part = new BufferedInputPart();
            part.setInput(in, SIZE);
            check(part);
        }
    }

    private void check(BufferedInput in) throws IOException {
        in.mark();
        assertEquals(0, in.read());
        assertEquals(1, in.read());
        assertEquals(2, in.read());
        in.reset();
        assertEquals(0, in.read());
        assertEquals(1, in.read());
        assertEquals(2, in.read());
        in.mark();
        assertEquals(3, in.read());
        assertEquals(4, in.read());
        assertEquals(5, in.read());
        in.rewind(2);
        assertEquals(4, in.read());
        assertEquals(5, in.read());
        assertEquals(6, in.read());
        assertEquals(7, in.read());
        in.reset();
        assertEquals(3, in.read());
        assertEquals(4, in.read());
        assertEquals(5, in.read());
        in.mark();
        assertEquals(6, in.read());
        assertEquals(7, in.read());
        assertEquals(8, in.read());
        assertEquals(9, in.read());
        in.rewind(4);
        assertEquals(6, in.read());
        assertEquals(7, in.read());
        assertEquals(8, in.read());
        in.rewind(1);
        assertEquals(8, in.read());
        assertEquals(9, in.read());
        in.unmark();
        assertEquals(10, in.read());
        assertEquals(11, in.read());
        in.mark();
        assertEquals(12, in.read());
        assertEquals(13, in.read());
        in.mark();
        assertEquals(14, in.read());
        assertEquals(15, in.read());
        in.reset();
        assertEquals(14, in.read());
        assertEquals(15, in.read());
        assertEquals(16, in.read());
    }
}
