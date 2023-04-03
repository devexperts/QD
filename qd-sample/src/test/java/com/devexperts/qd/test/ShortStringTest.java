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

import com.devexperts.qd.util.ShortString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShortStringTest {

    @Test
    public void testEncode() {
        assertEquals(0, ShortString.encode(null));
        assertEquals(0, ShortString.encode(""));
        assertEquals(0x41L, ShortString.encode("A"));
        assertEquals(0x4142L, ShortString.encode("AB"));
        assertEquals(0x414243L, ShortString.encode("ABC"));
        assertEquals(0x41424344L, ShortString.encode("ABCD"));
        assertEquals(0x4142434445L, ShortString.encode("ABCDE"));
        assertEquals(0x414243444546L, ShortString.encode("ABCDEF"));
        assertEquals(0x41424344454647L, ShortString.encode("ABCDEFG"));
        assertEquals(0x4142434445464748L, ShortString.encode("ABCDEFGH"));
    }

    @Test
    public void testDecode() {
        assertEquals(null, ShortString.decode(0));
        assertEquals("A", ShortString.decode(0x0000000000000041L));
        assertEquals("A", ShortString.decode(0x0000004100000000L));
        assertEquals("A", ShortString.decode(0x4100000000000000L));
        assertEquals("AB", ShortString.decode(0x4142L));
        assertEquals("ABC", ShortString.decode(0x414243L));
        assertEquals("ABCD", ShortString.decode(0x41424344L));
        assertEquals("ABCDE", ShortString.decode(0x4142434445L));
        assertEquals("ABCDEF", ShortString.decode(0x414243444546L));
        assertEquals("ABCDEFG", ShortString.decode(0x41424344454647L));
        assertEquals("ABCDEFG", ShortString.decode(0x4142434445464700L));
        assertEquals("ABCDEFGH", ShortString.decode(0x4142434445464748L));
    }

    @Test
    public void testDecodeCache() {
        String s = ShortString.decode(0x4142L);
        assertEquals("AB", s);
        checkDecodeCache(s, 0x4142L, 6);
        checkDecodeCache(s, 0x410042L, 5);
        checkDecodeCache(s, 0x41000042L, 4);
        checkDecodeCache(s, 0x4100000042L, 3);
        checkDecodeCache(s, 0x410000000042L, 2);
        checkDecodeCache(s, 0x41000000000042L, 1);
        checkDecodeCache(s, 0x4100000000000042L, 0);
    }

    @Test
    public void testInt() {
        for (int i = -12345; i <= 12345; i++) {
            long code = ShortString.encodeInt(i);
            int value = ShortString.decodeInt(code);
            assertEquals(code, ShortString.encode(Integer.toString(i)));
            assertEquals(value, i);
        }
    }

    private void checkDecodeCache(String s, long code, int k) {
        for (int i = 0; i <= k; i++)
            assertTrue(s == ShortString.decode(code << (k << 3)));
    }
}
