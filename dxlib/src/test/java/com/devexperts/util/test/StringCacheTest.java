/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.util.test;

import java.util.Random;

import com.devexperts.util.StringCache;
import junit.framework.TestCase;

@SuppressWarnings({"RedundantCast", "RedundantStringConstructorCall"})
public class StringCacheTest  extends TestCase {

    public StringCacheTest(String s) {
        super(s);
    }

    public void testSpecial() {
        StringCache sc = new StringCache();
        // certain methods (not all) support nulls
        assertTrue(sc.get((String) null, false) == null);
        assertTrue(sc.get((String) null, true) == null);
        assertTrue(sc.get((CharSequence) null) == null);
        assertTrue(sc.get((char[]) null) == null);
        assertTrue(sc.getASCII((byte[]) null) == null);
        assertTrue(sc.getShortString(0) == null);
        // empty string must be always interned, so strictly equal to constant ""
        assertTrue(sc.get(new String(), false) == "");
        assertTrue(sc.get(new String(), true) == "");
        assertTrue(sc.get(new String("01234567890"), 5, 0) == "");
        assertTrue(sc.get(new StringBuilder()) == "");
        assertTrue(sc.get(new StringBuilder("01234567890"), 5, 0) == "");
        assertTrue(sc.get(new char[0]) == "");
        assertTrue(sc.get(new char[10], 5, 0) == "");
        assertTrue(sc.getASCII(new byte[0]) == "");
        assertTrue(sc.getASCII(new byte[10], 5, 0) == "");
        // copying vs non-copying require extra instances to avoid test artifacts
        assertTrue(new StringCache().get("a", false) == "a");
        assertTrue(new StringCache().get("a", true) != "a");
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
        for (int n = 0; n < repeat; n++)
            for (int i = 0; i < count; i++)
                assertEquals(sc.get(randoms[i], false), randoms[i]);
        System.out.println("Performance [" + number + ", " + size + ", " + count + ", " + repeat + "] = " +
            (count * repeat * 1000L / Math.max(System.currentTimeMillis() - time, 1)) + " ops in " +
            (System.currentTimeMillis() - time) + " ms = " + sc);
    }

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

    public void testCaching() {
        for (int k = 1; k < collisions.length; k++) {
            StringCache sc = new StringCache(997, k);
            // fill up bucket
            for (int i = 0; i < k; i++)
                assertTrue(sc.get(collisions[i], false) == collisions[i]);
            // check that all items up to bucket size are cached
            for (int i = 0; i < 1000; i++)
                assertTrue(sc.get(new String(collisions[i % k]), false) == collisions[i % k]);
        }
    }

    public void testLRU() {
        for (int k = 1; k < collisions.length - 1; k++) {
            StringCache sc = new StringCache(997, k);
            // fill up bucket
            for (int i = 0; i < k; i++)
                assertTrue(sc.get(collisions[i], false) == collisions[i]);
            // add extra item to push out LRU item
            assertTrue(sc.get(collisions[k], false) == collisions[k]);
            // check that MRU items are still cached
            for (int i = 1; i < k; i++)
                assertTrue(sc.get(new String(collisions[i]), false) == collisions[i]);
            // check that LRU item is not cached
            assertTrue(sc.get(new String(collisions[0]), false) != collisions[0]);
        }
    }
}
