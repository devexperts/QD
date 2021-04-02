/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import com.devexperts.util.WideDecimal;
import junit.framework.TestCase;

import static com.devexperts.util.WideDecimal.abs;
import static com.devexperts.util.WideDecimal.compare;
import static com.devexperts.util.WideDecimal.composeWide;
import static com.devexperts.util.WideDecimal.neg;
import static com.devexperts.util.WideDecimal.sum;
import static com.devexperts.util.WideDecimal.toDouble;

public class WideDecimalTest extends TestCase {
    private static final long MAX_SIGNIFICAND = Long.MAX_VALUE >> 8;
    private static final int MAX_RANK = 255;

    public void testSigns() {
        for (long significand = 0; significand <= MAX_SIGNIFICAND; significand += significand / 2 + 1) {
            for (int rank = 0; rank <= MAX_RANK; rank += 16) {
                long rawWide = (significand << 8) | (rank & 0xFF);
                long rawNeg = (-significand << 8) | (rank & 0xFF);
                assertEquals(rawWide, abs(rawWide));
                assertEquals(rawNeg, neg(rawWide));
            }
        }
    }

    public void testWide() {
        checkWide("NaN", 0, 128);
        checkWide("Infinity", 1, 128);
        checkWide("Infinity", 123456, 128);
        checkWide("-Infinity", -1, 128);
        checkWide("-Infinity", -123456, 128);
        checkWide("0", 0, 0);
        checkWide("0", 0, -10);
        checkWide("0", 0, 10);
        checkWide("1", 1, 0);
        checkWide("1", 1000, -3);
        checkWide("1000000", 1000, 3);
        checkWide("1000000", 1000000, 0);
        checkWide("1000000", 1000000000, -3);
        checkWide("1E7", 10000, 3);
        checkWide("1E7", 10000000, 0);
        checkWide("1E7", 10000000000L, -3);
        checkWide("123.456", 123456, -3);
        checkWide("0.123456", 123456, -6);
        checkWide("0.000123456", 123456, -9);
        checkWide("0.000000123456", 123456, -12);
        checkWide("1.23456E-8", 123456, -13);
        checkWide("1.23456E-9", 123456, -14);
        checkWide("1.23456E-10", 123456, -15);
    }

    private void checkWide(String expected, long significand, int exponent) {
        double expectedDouble = Double.parseDouble(expected);
        long rawWide = (significand << 8) | ((128 - exponent) & 0xFF);
        long theWide = composeWide(significand, -exponent);
        assertEquals(0, compare(rawWide, theWide));
        assertEquals(expectedDouble, toDouble(rawWide));
        assertEquals(expectedDouble, toDouble(theWide));
        assertEquals(expected, toString(rawWide));
        assertEquals(expected, toString(theWide));
        assertEquals(expected, toStringSB(rawWide));
        assertEquals(expected, toStringSB(theWide));
    }

    public static void testRandom() {
        for (int i = 0; i < 1000; i++) {
            long pa = (long) Math.pow(10, (int) (Math.random() * 12));
            long pb = (long) Math.pow(10, (int) (Math.random() * 12));
            double a = Math.floor(Math.random() * pa) / pa;
            double b = Math.floor(Math.random() * pb) / pb;
            long wa = composeWide(a);
            long wb = composeWide(b);
            assertEquals(a, toDouble(wa));
            assertEquals(b, toDouble(wb));
            assertEquals(Double.compare(a, b), compare(wa, wb));
            assertEquals(a + b, toDouble(sum(wa, wb)), 1e-15);
        }
    }

    private static String toString(long wide) {
        return WideDecimal.toString(wide);
    }

    private static String toStringSB(long wide) {
        return WideDecimal.appendTo(new StringBuilder(), wide).toString();
    }

    private static String toString(double d) {
        return Double.toString(d);
    }
}
