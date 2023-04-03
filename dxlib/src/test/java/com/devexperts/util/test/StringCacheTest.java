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
package com.devexperts.util.test;

import com.devexperts.util.StringCache;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@SuppressWarnings({"RedundantCast"})
public class StringCacheTest {

    @Test
    public void testSpecial() {
        StringCache sc = new StringCache();
        // certain methods (not all) support nulls
        assertNull(sc.get((String) null, false));
        assertNull(sc.get((String) null, true));
        assertNull(sc.get((CharSequence) null));
        assertNull(sc.get((char[]) null));
        assertNull(sc.getASCII((byte[]) null));
        assertNull(sc.getShortString(0));
        // empty string must be always interned, so strictly equal to constant ""
        assertSame("", sc.get(new String(), false));
        assertSame("", sc.get(new String(), true));
        assertSame("", sc.get(new String("01234567890"), 5, 0));
        assertSame("", sc.get(new StringBuilder()));
        assertSame("", sc.get(new StringBuilder("01234567890"), 5, 0));
        assertSame("", sc.get(new char[0]));
        assertSame("", sc.get(new char[10], 5, 0));
        assertSame("", sc.getASCII(new byte[0]));
        assertSame("", sc.getASCII(new byte[10], 5, 0));
        // copying vs non-copying require extra instances to avoid test artifacts
        assertSame("a", new StringCache().get("a", false));
        assertNotSame("a", new StringCache().get("a", true));
    }

    private static final String[] randoms = new String[10000];
    static {
        Random r = new Random(0);
        char[] c = new char[64];
        for (int i = 0; i < randoms.length; i++) {
            int length = r.nextInt(c.length);
            for (int j = 0; j < length; j++)
                c[j] = (char) r.nextInt(8 * 256); // frequent Unicode range
            randoms[i] = new String(c, 0, length);
        }
    }

    @Test
    public void testPerformance() {
        int count = 4000;
        int repeat = 10; // 1000 for real test
        performanceTest(1, 1, count, repeat);
        performanceTest(1, 2, count, repeat);
        performanceTest(1, 4, count, repeat);
        performanceTest(1, 8, count, repeat);
        performanceTest(997, 1, count, repeat);
        performanceTest(997, 2, count, repeat);
        performanceTest(997, 4, count, repeat);
        performanceTest(997, 8, count, repeat);
        performanceTest(997 * 2, 1, count, repeat);
        performanceTest(997 * 2, 2, count, repeat);
        performanceTest(997 * 2, 4, count, repeat);
        performanceTest(997 * 4, 1, count, repeat);
        performanceTest(997 * 4, 2, count, repeat);
        performanceTest(997 * 8, 1, count, repeat);
    }

    public void performanceTest(int number, int size, int count, int repeat) {
        count = Math.min(count, randoms.length);
        StringCache sc = new StringCache(number, size);
        long time = System.currentTimeMillis();
        for (int n = 0; n < repeat; n++) {
            for (int i = 0; i < count; i++) {
                assertEquals(sc.get(randoms[i], false), randoms[i]);
            }
        }
        System.out.println("Performance [" + number + ", " + size + ", " + count + ", " + repeat + "] = " +
            (count * repeat * 1000L / Math.max(System.currentTimeMillis() - time, 1)) + " ops in " +
            (System.currentTimeMillis() - time) + " ms = " + sc);
    }

    @Test
    public void testAccess() {
        accessTest(1, 1, 10000);
        accessTest(1, 4, 10000);
        accessTest(997, 1, 10000);
        accessTest(997, 4, 10000);
    }

    public void accessTest(int number, int size, int count) {
        count = Math.min(count, randoms.length);
        StringCache sc1 = new StringCache(number, size);
        StringCache sc2 = new StringCache(number, size);
        StringCache sc3 = new StringCache(number, size);
        StringCache sc4 = new StringCache(number, size);
        StringCache sc5 = new StringCache(number, size);
        Random r = new Random(0);
        byte[] b = new byte[100];
        char[] c = new char[b.length];
        for (int i = 0; i < count; i++) {
            String s = randoms[i];
            assertEquals(sc1.get(s, false), s);
            assertEquals(sc2.get(new StringBuilder(s)), s);
            int length = s.length();
            int offset = r.nextInt((b.length - length) / 2);
            s.getChars(0, length, c, offset);
            assertEquals(sc3.get(c, offset, length), s);
            for (int j = 0; j < length; j++) {
                b[offset + j] = (byte) c[offset + j];
                c[offset + j] &= 0x7F;
            }
            assertEquals(sc4.getASCII(b, offset, length), new String(c, offset, length));
            long code = 0;
            int k = 0;
            for (int j = 0; j < Math.min(length, 8); j++) {
                char cc = (char) (s.charAt(j) & 0xFF);
                code = (code << 8) | cc;
                if (cc != 0)
                    c[k++] = cc;
            }
            assertEquals(sc5.getShortString(code), k == 0 ? null : new String(c, 0, k));
        }
    }

    private static final String[] collisions = { // these strings has same hash value
        "aaaaaaaaaa", "aaaaaaaabB", "aaaaaaabBa", "aaaaaabBaa", "aaaaabBaaa",
        "aaaabBaaaa", "aaabBaaaaa", "aabBaaaaaa", "abBaaaaaaa", "bBaaaaaaaa",
    };

    @Test
    public void testCaching() {
        for (int k = 1; k < collisions.length; k++) {
            StringCache sc = new StringCache(997, k);
            // fill up bucket
            for (int i = 0; i < k; i++)
                assertSame(collisions[i], sc.get(collisions[i], false));
            // check that all items up to bucket size are cached
            for (int i = 0; i < 1000; i++)
                assertSame(collisions[i % k], sc.get(new String(collisions[i % k]), false));
        }
    }

    @Test
    public void testLeastRecentlyUsed() {
        for (int k = 1; k < collisions.length - 1; k++) {
            StringCache sc = new StringCache(997, k);
            // fill up bucket
            for (int i = 0; i < k; i++) {
                assertSame(collisions[i], sc.get(collisions[i], false));
            }
            // add extra item to push out LRU item
            assertSame(collisions[k], sc.get(collisions[k], false));
            // check that MRU items are still cached
            for (int i = 1; i < k; i++) {
                assertSame(collisions[i], sc.get(new String(collisions[i]), false));
            }
            // check that LRU item is not cached
            assertNotSame(collisions[0], sc.get(new String(collisions[0]), false));
        }
    }
}
