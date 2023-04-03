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

import com.devexperts.qd.util.Decimal;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DecimalParseTest {

    @Test
    public void testSpecialCases() {
        // large numbers (infinity)
        assertEquals(Decimal.POSITIVE_INFINITY, Decimal.parseDecimal("123456789123456789"));
        assertEquals(Decimal.NEGATIVE_INFINITY, Decimal.parseDecimal("-987654321987654321"));
        // underflow to zero
        assertEquals(Decimal.ZERO, Decimal.parseDecimal("0.000000001"));
        assertEquals(Decimal.ZERO, Decimal.parseDecimal("-0.000000001"));
    }

    @Test
    public void testParseDecimalListed() {
        assertEquals(Decimal.parseDecimal("  1234  ".toCharArray(), 0, 8), Decimal.compose(1234));
        Random r = new Random(1);
        check(0, r);
        check(1, r);
        check(10, r);
        check(0.1, r);
        check(12345678, r);
        check(98765432, r);
        check(1234.5678, r);
        check(9876.5432, r);
        check(-132807800, r);
        check(2283111300.0, r);
        // boundary decimals
        check(Decimal.toDouble(0xffffffff), r);
        check(Decimal.toDouble(0x7fffffff), r);
        check(Decimal.toDouble(0xfffffff1), r);
        check(Decimal.toDouble(0x7ffffff1), r);
        // infinities and NaNs shall work
        check(Double.POSITIVE_INFINITY, r);
        check(Double.NEGATIVE_INFINITY, r);
        check(Double.NaN, r);
    }

    @Test
    public void testParseDecimalRnd() {
        double[] pow10 = new double[6];
        pow10[0] = 1;
        for (int i = 1; i < 6; i++) {
            pow10[i] = 10 * pow10[i - 1];
        }
        // makes sure that correct 8-digit double survives toString/parseDouble
        Random r = new Random(2);
        for (int cnt = 0; cnt < 100000; cnt++) {
            int i = r.nextInt(100000000);
            double p = pow10[r.nextInt(6)];
            double d = r.nextBoolean() ? i * p : i / p;
            check(d, r);
        }
    }

    private void check(double d, Random r) {
        int composed = Decimal.compose(d);
        String s = Decimal.toString(composed);
        assertEquals("toString != appendTo", s, Decimal.appendTo(new StringBuilder(), composed).toString());
        // check string parse
        int parsed1 = Decimal.parseDecimal(s);
        if (parsed1 != composed)
            fail("Decimal was lost: " + Decimal.toString(parsed1) + " != " + Decimal.toString(composed));
        // check char[] parse
        int offset = r.nextInt(3);
        int follow = r.nextInt(3);
        int length = s.length();
        int n = length + offset + follow;
        char[] chars = new char[n];
        for (int i = 0; i < offset; i++)
            chars[i] = (char) r.nextInt(256);
        for (int i = 0; i < follow; i++)
            chars[n - i - 1] = (char) r.nextInt(256);
        s.getChars(0, length, chars, offset);
        int parsed2 = Decimal.parseDecimal(chars, offset, length);
        if (parsed2 != composed)
            fail("Decimal was lost: " + Decimal.toString(parsed2) + " != " + Decimal.toString(composed));
    }
}
