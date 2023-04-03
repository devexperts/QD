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
package com.devexperts.qd.test;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.IOUtil;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class CompactCharTest {

    @Test
    public void testReadWriteChar() throws IOException {
        ByteArrayOutput out = new ByteArrayOutput();
        ByteArrayInput in = new ByteArrayInput();
        for (int i = 0; i <= 0xFFFF; i++) {
            out.setPosition(0);
            IOUtil.writeUTFChar(out, i);
            in.setBuffer(out.getBuffer());
            assertEquals(i, IOUtil.readUTFChar(in));
            assertEquals(out.getPosition(), in.getPosition());
            int p = out.getPosition();
            out.writeUTFChar(i);
            assertEquals(out.getPosition() - p, p);
            for (int j = 0; j < p; j++)
                assertEquals(out.getBuffer()[j], out.getBuffer()[p + j]);
            assertEquals(i, in.readUTFChar());
            assertEquals(out.getPosition(), in.getPosition());
        }
    }

    @Test
    public void testReadWriteAll() throws IOException {
        checkReadWriteAll(null);
        checkReadWriteAll("");
        checkReadWriteAll("Hello! :@)");
        checkReadWriteAll("\u1234\uFFFF\u0000\u007F\u0080\u07FF\u0800!");
        checkReadWriteAll(new String(new int[] {0x23456}, 0, 1));
    }

    private void checkReadWriteAll(String string) throws IOException {
        char[] chars = (string == null) ? null : string.toCharArray();

        ByteArrayOutput out = new ByteArrayOutput();
        ByteArrayInput in = new ByteArrayInput();

        out.setPosition(0);
        IOUtil.writeCharArray(out, chars);
        in.setBuffer(out.getBuffer());
        char[] chars2 = IOUtil.readCharArray(in);
        assertEquals(string, chars2 == null ? null : String.valueOf(chars2));
        assertEquals(out.getPosition(), in.getPosition());

        out.setPosition(0);
        IOUtil.writeCharArray(out, string);
        in.setBuffer(out.getBuffer());
        assertEquals(string, IOUtil.readCharArrayString(in));
        assertEquals(out.getPosition(), in.getPosition());

        out.setPosition(0);
        IOUtil.writeUTFString(out, string);
        in.setBuffer(out.getBuffer());
        assertEquals(string, IOUtil.readUTFString(in));
        assertEquals(out.getPosition(), in.getPosition());

        out.setPosition(0);
        out.writeUTFString(string);
        in.setBuffer(out.getBuffer());
        assertEquals(string, in.readUTFString());
        assertEquals(out.getPosition(), in.getPosition());
    }
}
