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

import com.devexperts.io.BufferedInputPart;
import com.devexperts.io.ByteArrayInput;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BufferedInputPartTest {

    private static final int SIZE = 127;

    private ByteArrayInput createByteArrayInput() {
        byte[] bytes = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) i;
        }
        return new ByteArrayInput(bytes);
    }

    @Test
    public void testReadByteBufferLimitOverflow() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        BufferedInputPart bip = new BufferedInputPart();
        doTestReadByteOverflow(in, bip, 0); // check near start
        doTestReadByteOverflow(in, bip, SIZE / 2); // check in the middle
    }

    private void doTestReadByteOverflow(ByteArrayInput in, BufferedInputPart bip, int pos) throws IOException {
        in.setPosition(pos);
        in.mark();
        bip.setInput(in, 2);
        readBytesByOne(bip, pos, 2);
        assertThrows(IOException.class, () -> readBytesByOne(bip, pos + 1, 1));
    }

    @Test
    public void testReadBufferLimitOverflow() throws IOException {
        ByteArrayInput in = createByteArrayInput();
        BufferedInputPart bip = new BufferedInputPart();
        doTestReadOverflow(in, bip, 0); // check near start
        doTestReadOverflow(in, bip, SIZE / 2); // check in the middle
    }

    private void doTestReadOverflow(ByteArrayInput in, BufferedInputPart bip, int pos) throws IOException {
        in.setPosition(pos);
        in.mark();
        bip.setInput(in, 10);
        readBytes(bip, pos, 5, 5); // expect full read
        readBytes(bip, pos + 5, 10, 5); // expect half of array
        readBytes(bip, pos + 10, 10, -1); // expect -1 at the end
    }

    private void readBytesByOne(BufferedInputPart bip, int base, int count) throws IOException {
        for (int i = base; i < base + count; i++) {
            assertEquals((byte) i, bip.readByte());
        }
    }

    private void readBytes(BufferedInputPart bip, int base, int count, int expected) throws IOException {
        byte[] buf = new byte[count];
        int n = bip.read(buf);
        assertEquals(expected, n);
        for (int i = 0; i < n; i++) {
            assertEquals((byte) (i + base), buf[i]);
        }
    }
}
