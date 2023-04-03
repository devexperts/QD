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
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.util.ShortString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PentaCodecTest {
    private static final PentaCodec CODEC = PentaCodec.INSTANCE;

    private static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ./$".toCharArray();

    private ByteArrayInput in;
    private ByteArrayOutput out;

    @Before
    public void setUp() {
        out = new ByteArrayOutput(1024);
        in = new ByteArrayInput(out.getBuffer());
    }

    @After
    public void tearDown() {
        in = null;
        out = null;
    }

    @Test
    public void testNull() {
        assertEquals(0, CODEC.encode(null));
        assertEquals(null, CODEC.decode(0));
        assertEquals(null, CODEC.decode(0, null));
        assertEquals(0, CODEC.decodeToLong(0));
        assertEquals(-1, CODEC.decodeCharAt(0, 0));
        assertEquals(0, CODEC.hashCode(0));

        assertEquals(CODEC.encode("*"), CODEC.getWildcardCipher());
    }

    @Test
    public void testPentas() {
        helpTestPentas("");
        char[] c = new char[12];
        for (char d = 0; d < 256; d++) {
            for (int i = 0; i < c.length; i++) {
                c[i] = d;
                helpTestPentas(new String(c, 0, i + 1));
            }
            helpTestPentas(".DWNL" + d);
        }
        Random r = new Random(1);
        for (int i = 0; i < 100; i++) {
            for (int j = 1; j < c.length; j++)
                c[j] = CHARS[r.nextInt(CHARS.length)];
            for (int b = 0; b < c.length; b++) {
                for (c[b] = 0; c[b] < 256; c[b]++) {
                    for (int len = Math.max(2, b + 1); len < c.length; len++)
                        helpTestPentas(new String(c, 0, len));
                }
                c[b] = CHARS[r.nextInt(CHARS.length)];
            }
        }
    }

    private void helpTestPentas(String s) {
        assert s != null;
        try {
            int cipher = CODEC.encode(s);
            assertEquals(cipher, CODEC.encode(s.toCharArray(), 0, s.length()));
            if (cipher != 0)
                assertEquals(s, CODEC.decode(cipher));
            String symbol = cipher == 0 ? s : null;
            assertEquals(s, CODEC.decode(cipher, symbol));
            if (s.isEmpty())
                assertEquals(0, CODEC.decodeToLong(cipher));
            else
                assertEquals(CODEC.decode(cipher), ShortString.decode(CODEC.decodeToLong(cipher)));
            if (cipher != 0) {
                long code = CODEC.decodeToLong(cipher);
                for (int i = 0; i < s.length(); i++) {
                    int sChar = s.charAt(i);
                    int cipherChar = CODEC.decodeCharAt(cipher, i);
                    int codeChar = (int) ((code >>> ((7 - i) << 3)) & 0xff);
                    assertEquals(sChar, cipherChar);
                    assertEquals(sChar, codeChar);
                }
                assertEquals(s.hashCode(), CODEC.hashCode(cipher));
            }

            out.setPosition(0);
            CODEC.createWriter().writeSymbol(out, cipher, symbol, 0);
            in.setLimit(out.getPosition());

            in.setPosition(0);
            SymbolCodec.Reader reader = CODEC.createReader();
            reader.readSymbol(in, null);
            assertEquals(out.getPosition(), in.getPosition());
            assertEquals(symbol, reader.getSymbol());
            assertEquals(cipher, reader.getCipher());
        } catch (IOException e) {
            fail(e.toString());
        }
    }
}
