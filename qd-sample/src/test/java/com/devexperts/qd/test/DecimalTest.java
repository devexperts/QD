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

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link Decimal} class.
 */
public class DecimalTest {
    private static final long[] POWERS = new long[19];
    static {
        POWERS[0] = 1;
        for (int i = 1; i < POWERS.length; i++)
            POWERS[i] = POWERS[i - 1] * 10;
    }

    private static double randomStdRangedDouble(Random r) {
        double d = r.nextInt(1000000000) % POWERS[r.nextInt(8) + 1]; // ~ 1-8 digits of 9 random digits
        int precision = r.nextInt(15) - 8; // from -8 to +6
        d = precision >= 0 ? d / POWERS[precision] : d * POWERS[-precision];
        return r.nextBoolean() ? d : -d;
    }

    private static boolean equals(double d1, double d2) {
        return Double.isNaN(d1) ? Double.isNaN(d2) : d1 == d2;
    }

    private static String str(int decimal) {
        int shift = (decimal & 0x0F) != 0 || (((decimal >> 4) + 1) & 7) <= 2 ? 4 : 7;
        return "0x" + Integer.toHexString(decimal) +
            "(" + (decimal >> shift) + ":" + (decimal & ((1 << shift) - 1)) + "=" + Decimal.toString(decimal) + ")";
    }

    @Test
    public void testCoversions() {
        Random r = new Random(20070412);
        for (int i = 0; i < 100000; i++) {
            double d = randomStdRangedDouble(r);

            int decimal = Decimal.compose(d);
            assertEquals(decimal, Decimal.wideToTiny(Decimal.tinyToWide(decimal)));
            String s = Decimal.toString(decimal);
            assertEquals("toString != appendTo", s, Decimal.appendTo(new StringBuilder(), decimal).toString());
            assertTrue(d == Decimal.toDouble(decimal));
            assertTrue(decimal == Decimal.parseDecimal(s));
            assertTrue(d == Double.parseDouble(s));
            assertTrue(-d == Decimal.toDouble(Decimal.neg(decimal)));
            assertTrue(Math.abs(d) == Decimal.toDouble(Decimal.abs(decimal)));
        }
    }

    @Test
    public void testRandomCompose() {
        Random r = new Random(20120903);
        for (int i = 0; i < 100000; i++) {
            long mantissa = r.nextLong();
            int precision = r.nextInt(37) - 18; // from -18 to +18
            double d = precision >= 0 ? (double) mantissa / POWERS[precision] : (double) mantissa * POWERS[-precision];

            int d1 = Decimal.compose(d);
            int d2 = Decimal.composeDecimal(mantissa, precision);
            if (d1 != d2) {
                fail("compose(" + d + ") = " + str(d1) +
                    ", composeDecimal(" + mantissa + ", " + precision + ") = " + str(d2));
            }
            assertEquals(d1, Decimal.wideToTiny(Decimal.tinyToWide(d1)));
        }
    }

    @Test
    public void testCompose() {
        assertEquals(Decimal.compose(10000000000000000.0), 1600000001);
        assertEquals(Decimal.compose(1000000000000000.0), 160000001);
        assertEquals(Decimal.compose(100000000000000.0), 16000001);
        assertEquals(Decimal.compose(10000000000000.0), 1600001);
        assertEquals(Decimal.compose(1000000000000.0), 160001);
        assertEquals(Decimal.compose(100000000000.0), 16001);
        assertEquals(Decimal.compose(10000000000.0), 1601);
        assertEquals(Decimal.compose(1000000000.0), 161);
        assertEquals(Decimal.compose(100000000.0), 0x11);
        assertEquals(Decimal.compose(10000000.0), 0x12);
        assertEquals(Decimal.compose(1000000.0), 0x13);
        assertEquals(Decimal.compose(100000.0), 0x14);
        assertEquals(Decimal.compose(10000.0), 0x15);
        assertEquals(Decimal.compose(1000.0), 0x16);
        assertEquals(Decimal.compose(100.0), 0x17);
        assertEquals(Decimal.compose(10.0), 0x18);
        assertEquals(Decimal.compose(1.0), 0x19);
        assertEquals(Decimal.compose(0.1), 0x1A);
        assertEquals(Decimal.compose(0.01), 0x1B);
        assertEquals(Decimal.compose(0.001), 0x1C);
        assertEquals(Decimal.compose(0.0001), 0x1D);
        assertEquals(Decimal.compose(0.00001), 0x1E);
        assertEquals(Decimal.compose(0.000001), 0x1F);
        assertEquals(Decimal.compose(0.0000001), 0xA0);
        assertEquals(Decimal.compose(0.00000001), 0xB0);

        // Long.MAX_VALUE =  9223372036854775807
        // Long.MIN_VALUE = -9223372036854775808
        checkCompose(Long.MAX_VALUE, 9, 9223372000.0);
        checkCompose(-Long.MAX_VALUE, 9, -9223372000.0);
        checkCompose(Long.MIN_VALUE, 9, -9223372000.0);
        checkCompose(Long.MAX_VALUE, 15, 9223.372);
        checkCompose(-Long.MAX_VALUE, 15, -9223.372);
        checkCompose(Long.MIN_VALUE, 15, -9223.372);

        for (int precision = -1000; precision <= 1000; precision++) {
            checkCompose(0, precision, precision <= -19 ? Double.NaN : 0);
            for (int m = 1; m <= 9; m++)
                for (int k = 0; k < POWERS.length; k++) {
                    // Note: 1e18 is max decimal power that fits into long,
                    // 9e18 is max 1-digit mantissa that fits into long.
                    int p = k - precision;
                    double value = p >= POWERS.length ? Double.POSITIVE_INFINITY : p <= -POWERS.length ? 0 :
                        p >= 0 ? (double) m * POWERS[p] : (double) m / POWERS[-p];
                    if (value > 13421772700000000.0)
                        value = Double.POSITIVE_INFINITY;
                    else if (value < 0.499e-8)
                        value = 0;
                    else if (value < 1e-8)
                        value = 1e-8;
                    checkCompose(m * POWERS[k], precision, value);
                    checkCompose(-m * POWERS[k], precision, -value);
                }
        }

        for (int m = 0; m <= 128; m++) {
            checkCompose(m * 10000000 / 128, 7, m / 128.0);
            checkCompose(-m * 10000000 / 128, 7, -m / 128.0);
        }
        for (int k = 1; k <= 7; k++)
            for (int precision = k; precision <= 13; precision++)
                checkCompose(131071 * POWERS[precision] + (POWERS[precision] >> k), precision, 131071 + 1.0 / (1 << k));

        assertEquals(Decimal.toDouble(800000029), 5000.0001, 0.0);
        assertEquals(Decimal.compose(5000.00005), 800000029, 0.0);
        assertEquals(Decimal.composeDecimal(500000005, 5), 800000029, 0.0);
        checkCompose(1342177276, 1, 134217730);
        checkCompose(1342177275, -7, Double.POSITIVE_INFINITY);
    }

    private void checkCompose(long mantissa, int precision, double value) {
        int d1 = Decimal.composeDecimal(mantissa, precision);
        int d2 = Decimal.compose(value);
        double v1 = Decimal.toDouble(d1);
        double v2 = Decimal.toDouble(d2);
        if (d1 != d2 || !equals(v1, v2) || !equals(v1, value))
            fail("checkCompose(" + mantissa + ", " + precision + ", " + value + ") composed " + str(d1) + " vs " + str(d2));
        assertEquals(d1, Decimal.wideToTiny(Decimal.tinyToWide(d1)));
    }

    @Test
    public void testRoundingOnes() {
        // too big -> infinity
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 11111111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 1111111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 11111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 1111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 11111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 1111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 11111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 1111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 11111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 1111111111111111111.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 111111111111111111.0);
        // standard precision range
        checkRoundingBothSigns(11111111100000000.0, 11111111111111111.1);
        checkRoundingBothSigns(1111111110000000.0, 1111111111111111.11);
        checkRoundingBothSigns(111111111000000.0, 111111111111111.111);
        checkRoundingBothSigns(11111111100000.0, 11111111111111.1111);
        checkRoundingBothSigns(1111111110000.0, 1111111111111.11111);
        checkRoundingBothSigns(111111111000.0, 111111111111.111111);
        checkRoundingBothSigns(11111111100.0, 11111111111.1111111);
        checkRoundingBothSigns(1111111110.0, 1111111111.11111111);
        checkRoundingBothSigns(111111111.0, 111111111.111111111);
        checkRoundingBothSigns(11111111.1, 11111111.1111111111);
        checkRoundingBothSigns(1111111.11, 1111111.11111111111);
        checkRoundingBothSigns(111111.111, 111111.111111111111);
        checkRoundingBothSigns(11111.1111, 11111.1111111111111);
        checkRoundingBothSigns(1111.11111, 1111.11111111111111);
        checkRoundingBothSigns(111.111111, 111.111111111111111);
        checkRoundingBothSigns(11.111111, 11.1111111111111111);
        // extra precision range
        checkRoundingBothSigns(1.1111111, 1.11111111111111111);
        checkRoundingBothSigns(0.11111111, 0.111111111111111111);
        checkRoundingBothSigns(0.01111111, 0.0111111111111111111);
        checkRoundingBothSigns(0.00111111, 0.00111111111111111111);
        checkRoundingBothSigns(0.00011111, 0.000111111111111111111);
        checkRoundingBothSigns(0.00001111, 0.0000111111111111111111);
        checkRoundingBothSigns(0.00000111, 0.00000111111111111111111);
        checkRoundingBothSigns(0.00000011, 0.000000111111111111111111);
        checkRoundingBothSigns(0.00000001, 0.0000000111111111111111111);
        // too small -> zero
        checkRoundingBothSigns(0.0, 0.00000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.0000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.00000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.0000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.00000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.000000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.0000000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.00000000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.000000000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.0000000000000000000111111111111111111);
        checkRoundingBothSigns(0.0, 0.00000000000000000000111111111111111111);
    }

    @Test
    public void testRoundingFours() {
        // too big -> infinity
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 4444444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 4444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 4444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 4444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 4444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 444444444444444444.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 44444444444444444.0);
        // standard precision range
        checkRoundingBothSigns(4444444400000000.0, 4444444444444444.44);
        checkRoundingBothSigns(444444440000000.0, 444444444444444.444);
        checkRoundingBothSigns(44444444000000.0, 44444444444444.4444);
        checkRoundingBothSigns(4444444400000.0, 4444444444444.44444);
        checkRoundingBothSigns(444444440000.0, 444444444444.444444);
        checkRoundingBothSigns(44444444000.0, 44444444444.4444444);
        checkRoundingBothSigns(4444444400.0, 4444444444.44444444);
        checkRoundingBothSigns(444444440.0, 444444444.444444444);
        checkRoundingBothSigns(44444444.0, 44444444.4444444444);
        checkRoundingBothSigns(4444444.4, 4444444.44444444444);
        checkRoundingBothSigns(444444.44, 444444.444444444444);
        checkRoundingBothSigns(44444.444, 44444.4444444444444);
        checkRoundingBothSigns(4444.4444, 4444.44444444444444);
        checkRoundingBothSigns(444.44444, 444.444444444444444);
        checkRoundingBothSigns(44.444444, 44.4444444444444444);
        checkRoundingBothSigns(4.444444, 4.44444444444444444);
        // extra precision range
        checkRoundingBothSigns(0.4444444, 0.444444444444444444);
        checkRoundingBothSigns(0.04444444, 0.0444444444444444444);
        checkRoundingBothSigns(0.00444444, 0.00444444444444444444);
        checkRoundingBothSigns(0.00044444, 0.000444444444444444444);
        checkRoundingBothSigns(0.00004444, 0.0000444444444444444444);
        checkRoundingBothSigns(0.00000444, 0.00000444444444444444444);
        checkRoundingBothSigns(0.00000044, 0.000000444444444444444444);
        checkRoundingBothSigns(0.00000004, 0.0000000444444444444444444);
        // too small -> zero
        checkRoundingBothSigns(0.0, 0.00000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.0000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.00000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.0000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.00000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.000000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.0000000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.00000000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.000000000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.0000000000000000000444444444444444444);
        checkRoundingBothSigns(0.0, 0.00000000000000000000444444444444444444);
    }

    @Test
    public void testRoundingFives() {
        // too big -> infinity
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 5555555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 5555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 5555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 5555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 5555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 555555555555555555.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 55555555555555555.0);
        // standard precision range
        checkRoundingBothSigns(5555555600000000.0, 5555555555555555.55);
        checkRoundingBothSigns(555555560000000.0, 555555555555555.555);
        checkRoundingBothSigns(55555556000000.0, 55555555555555.5555);
        checkRoundingBothSigns(5555555600000.0, 5555555555555.55555);
        checkRoundingBothSigns(555555560000.0, 555555555555.555555);
        checkRoundingBothSigns(55555556000.0, 55555555555.5555555);
        checkRoundingBothSigns(5555555600.0, 5555555555.55555555);
        checkRoundingBothSigns(555555560.0, 555555555.555555555);
        checkRoundingBothSigns(55555556.0, 55555555.5555555555);
        checkRoundingBothSigns(5555555.6, 5555555.55555555555);
        checkRoundingBothSigns(555555.56, 555555.555555555555);
        checkRoundingBothSigns(55555.556, 55555.5555555555555);
        checkRoundingBothSigns(5555.5556, 5555.55555555555555);
        checkRoundingBothSigns(555.55556, 555.555555555555555);
        checkRoundingBothSigns(55.555556, 55.5555555555555555);
        checkRoundingBothSigns(5.555556, 5.55555555555555555);
        // extra precision range
        checkRoundingBothSigns(0.5555556, 0.555555555555555555);
        checkRoundingBothSigns(0.05555556, 0.0555555555555555555);
        checkRoundingBothSigns(0.00555556, 0.00555555555555555555);
        checkRoundingBothSigns(0.00055556, 0.000555555555555555555);
        checkRoundingBothSigns(0.00005556, 0.0000555555555555555555);
        checkRoundingBothSigns(0.00000556, 0.00000555555555555555555);
        checkRoundingBothSigns(0.00000056, 0.000000555555555555555555);
        checkRoundingBothSigns(0.00000006, 0.0000000555555555555555555);
        checkRoundingBothSigns(0.00000001, 0.00000000555555555555555555);
        // too small -> zero
        checkRoundingBothSigns(0.0, 0.000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.0000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.00000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.0000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.00000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.000000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.0000000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.00000000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.000000000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.0000000000000000000555555555555555555);
        checkRoundingBothSigns(0.0, 0.00000000000000000000555555555555555555);
    }

    @Test
    public void testRoundingSixs() {
        // too big -> infinity
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 6666666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 6666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 6666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 6666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 6666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 666666666666666666.0);
        checkRoundingBothSigns(Double.POSITIVE_INFINITY, 66666666666666666.0);
        // standard precision range
        checkRoundingBothSigns(6666666700000000.0, 6666666666666666.66);
        checkRoundingBothSigns(666666670000000.0, 666666666666666.666);
        checkRoundingBothSigns(66666667000000.0, 66666666666666.6666);
        checkRoundingBothSigns(6666666700000.0, 6666666666666.66666);
        checkRoundingBothSigns(666666670000.0, 666666666666.666666);
        checkRoundingBothSigns(66666667000.0, 66666666666.6666666);
        checkRoundingBothSigns(6666666700.0, 6666666666.66666666);
        checkRoundingBothSigns(666666670.0, 666666666.666666666);
        checkRoundingBothSigns(66666667.0, 66666666.6666666666);
        checkRoundingBothSigns(6666666.7, 6666666.66666666666);
        checkRoundingBothSigns(666666.67, 666666.666666666666);
        checkRoundingBothSigns(66666.667, 66666.6666666666666);
        checkRoundingBothSigns(6666.6667, 6666.66666666666666);
        checkRoundingBothSigns(666.66667, 666.666666666666666);
        checkRoundingBothSigns(66.666667, 66.6666666666666666);
        checkRoundingBothSigns(6.666667, 6.66666666666666666);
        // extra precision range
        checkRoundingBothSigns(0.6666667, 0.666666666666666666);
        checkRoundingBothSigns(0.06666667, 0.0666666666666666666);
        checkRoundingBothSigns(0.00666667, 0.00666666666666666666);
        checkRoundingBothSigns(0.00066667, 0.000666666666666666666);
        checkRoundingBothSigns(0.00006667, 0.0000666666666666666666);
        checkRoundingBothSigns(0.00000667, 0.00000666666666666666666);
        checkRoundingBothSigns(0.00000067, 0.000000666666666666666666);
        checkRoundingBothSigns(0.00000007, 0.0000000666666666666666666);
        checkRoundingBothSigns(0.00000001, 0.00000000666666666666666666);
        // too small -> zero
        checkRoundingBothSigns(0.0, 0.000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.0000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.00000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.0000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.00000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.000000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.0000000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.00000000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.000000000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.0000000000000000000666666666666666666);
        checkRoundingBothSigns(0.0, 0.00000000000000000000666666666666666666);
    }

    private void checkRoundingBothSigns(double expect, double src) {
        checkRounding(expect, src);
        checkRounding(expect == 0 ? 0 : -expect, -src);
    }

    private void checkRounding(double expect, double src) {
        int decimal = Decimal.compose(src);
        assertEquals(decimal, Decimal.wideToTiny(Decimal.tinyToWide(decimal)));
        checkToDoubleAndCanonical(expect, decimal);
        // check strings
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        fmt.setGroupingUsed(false);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(30);
        checkStr(expect, fmt.format(src)); // regular string
        fmt.setMinimumFractionDigits(30);
        checkStr(expect, fmt.format(src)); // overlong string
    }

    private void checkStr(double expect, String str) {
        checkToDoubleAndCanonical(expect, Decimal.parseDecimal(str));
        char[] chars = str.toCharArray();
        checkToDoubleAndCanonical(expect, Decimal.parseDecimal(chars, 0, chars.length));
    }

    private void checkToDoubleAndCanonical(double expect, int decimal) {
        assertEquals(expect, Decimal.toDouble(decimal), 0.0);
        if (Double.isInfinite(expect))
            assertEquals(decimal, expect > 0 ? Decimal.POSITIVE_INFINITY : Decimal.NEGATIVE_INFINITY);
        if (expect == 0)
            assertEquals(decimal, Decimal.ZERO);
    }

    @Test
    public void testPrecision() {
        // standard precision range
        checkPreciseBothSigns(13421772700000000.0, true); // max representable number with 10^8 multiplier
        checkPreciseBothSigns(9999999900000000.0, true);
        checkPreciseBothSigns(999999990000000.0, true);
        checkPreciseBothSigns(99999999000000.0, true);
        checkPreciseBothSigns(9999999900000.0, true);
        checkPreciseBothSigns(999999990000.0, true);
        checkPreciseBothSigns(99999999000.0, true);
        checkPreciseBothSigns(9999999900.0, true);
        checkPreciseBothSigns(999999990.0, true);
        checkPreciseBothSigns(99999999.0, true);
        checkPreciseBothSigns(9999999.9, true);
        checkPreciseBothSigns(999999.99, true);
        checkPreciseBothSigns(99999.999, true);
        checkPreciseBothSigns(9999.9999, true);
        checkPreciseBothSigns(999.99999, true);
        checkPreciseBothSigns(99.999999, true);
        // extra precision range
        checkPreciseBothSigns(0.9999999, false);
        checkPreciseBothSigns(1.6777215, false); // max representable number with 10^-7 multiplier
        checkPreciseBothSigns(0.09999999, false);
        checkPreciseBothSigns(0.16777215, false); // max representable number with 10^-8 multiplier
        checkPreciseBothSigns(0.00000001, false); // smallest representable number
        // 128th
        checkPreciseBothSigns(78124.9921875, false); // it is 9999999 / 128
        checkPreciseBothSigns(999 + 127.0/128, false);
        checkPreciseBothSigns(131071.9921875, false); // max number representable in 128th
    }

    private void checkPreciseBothSigns(double v, boolean std) {
        checkPrecise(v, std);
        checkPrecise(-v, std);
    }

    private void checkPrecise(double v, boolean std) {
        int decimal = Decimal.compose(v);
        assertEquals(decimal, Decimal.wideToTiny(Decimal.tinyToWide(decimal)));
        checkToDoubleAndCanonical(v, decimal);
        assertEquals(std, (decimal & 0x0F) != 0);
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        fmt.setGroupingUsed(false);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(8);
        String str = fmt.format(v);
        assertEquals(str, Decimal.toString(decimal));
        assertEquals("toString != appendTo", str, Decimal.appendTo(new StringBuilder(), decimal).toString());
        assertEquals(decimal, Decimal.parseDecimal(str));
        char[] chars = str.toCharArray();
        assertEquals(decimal, Decimal.parseDecimal(chars, 0, chars.length));
        // also check sanity of Double.parseDouble (just in case...)
        assertEquals(v, Double.parseDouble(str), 0.0);
    }

    @Test
    public void testDecimals() {
        testStringAndDouble("NaN", Double.NaN);
        testStringAndDouble("Infinity", Double.POSITIVE_INFINITY);
        testStringAndDouble("-Infinity", Double.NEGATIVE_INFINITY);
        testStringAndDouble("0", 0);
        testStringAndDouble("1", 1);
        testStringAndDouble("-1", -1);
        testStringAndDouble("100", 100);
        testStringAndDouble("0.1", 0.1);
        testStringAndDouble("0.01", 0.01);
        testStringAndDouble("0.125", 0.125);
        testStringAndDouble("5240643", 5240643);
        testStringAndDouble("453.628", 453.628);
        testStringAndDouble("-45.6262", -45.6262);
        testStringAndDouble("0.00123", 0.00123);
        testStringAndDouble("-0.00099", -0.00099);
    }

    private void testStringAndDouble(String str, double dbl) {
        // Should be internally consistent
        int decimal = Decimal.compose(dbl);
        assertEquals(decimal, Decimal.wideToTiny(Decimal.tinyToWide(decimal)));
        assertEquals(str, Decimal.toString(decimal));
        assertEquals("toString != appendTo", str, Decimal.appendTo(new StringBuilder(), decimal).toString());
        if (Double.isNaN(dbl))
            assertTrue("should be NaN", Double.isNaN(Decimal.toDouble(decimal)));
        else
            assertEquals(dbl, Decimal.toDouble(decimal), 0.0);

        // Should be consistent with Double.toString on NaNs and infinities
        if (Double.isNaN(dbl) || Double.isInfinite(dbl))
            assertEquals(str, Double.toString(dbl));

        // Should be consistent with Double.parse on exceptions
        try {
            assertEquals(decimal, Decimal.parseDecimal(str));
            try {
                Double.parseDouble(str);
            } catch (NumberFormatException bad) {
                fail("Decimal.parse must have thrown exception");
            }
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(str);
                fail("Decimal.parse must not have thrown exception");
            } catch (NumberFormatException ok) {}
        }

        // Shall be consistent on isNaN and isInfinite
        assertEquals("isNaN", Double.isNaN(dbl), Decimal.isNaN(decimal));
        assertEquals("isInfinite", Double.isInfinite(dbl), Decimal.isInfinite(decimal));

        // Shall have consistent abs and neg behavior with sign
        switch (Decimal.sign(decimal)) {
        case 0:
            if (Decimal.isNaN(decimal)) {
                assertEquals(Decimal.NaN, decimal);
                assertEquals(Decimal.NaN, Decimal.abs(decimal));
                assertEquals(Decimal.NaN, Decimal.neg(decimal));
            } else {
                assertEquals(Decimal.ZERO, decimal);
                assertEquals(Decimal.ZERO, Decimal.abs(decimal));
                assertEquals(Decimal.ZERO, Decimal.neg(decimal));
            }
            break;
        case -1:
            assertEquals(Decimal.neg(decimal), Decimal.abs(decimal));
            break;
        case 1:
            assertEquals(decimal, Decimal.abs(decimal));
            break;
        default:
            fail("wrong sign value");
        }
    }

    private static Map<Integer, Double> createSpecialDecimals(Map<Integer, Double> map) {
        // Create canonical decimal representations for special decimals.
        map.put(Decimal.NaN, Double.NaN);
        map.put(Decimal.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        map.put(Decimal.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        map.put(Decimal.ZERO, 0.0);
        for (int i = 1; i <= 15; i++) {
            map.put(0x7FFFFFF0 + i, 13421772700000000.0 / POWERS[i - 1]);
            map.put(0x80000010 + i, -13421772700000000.0 / POWERS[i - 1]);
        }
        return map;
    }

    private static Map<Integer, Double> createMalformedSpecialDecimals(Map<Integer, Double> map) {
        // Create malformed (non-canonical) decimal representations for special decimals.
        for (int i = 7; i <= 30; i++) {
            map.put(Decimal.POSITIVE_INFINITY + (1 << i), Double.POSITIVE_INFINITY);
            map.put(Decimal.NEGATIVE_INFINITY - (1 << i), Double.NEGATIVE_INFINITY);
            map.put(0x00000000 + (1 << i), Double.POSITIVE_INFINITY); // pseudo NaN turned to infinity
            map.put(0xFFFFFF80 - (1 << i), Double.NEGATIVE_INFINITY); // pseudo NaN turned to -infinity
        }
        for (int i = 2; i <= 15; i++)
            map.put(i, 0.0); // zero with non-canonical power
        map.put(0x20, 0.0); // zero with 10^-7 multiplier
        map.put(0x30, 0.0); // zero with 10^-8 multiplier
        map.put(0x60, 0.0); // zero with 1/128 multiplier
        return map;
    }

    private static Map<Integer, Double> createOneDecimals(Map<Integer, Double> map) {
        // Create canonical decimal representations for +/- 1 for every multiplier.
        for (int i = 1; i <= 15; i++) {
            double d = i >= 9 ? 1.0 / POWERS[i - 9] : 1.0 * POWERS[9 - i];
            map.put(0x10 + i, d);
            map.put(0xFFFFFFF0 + i, -d);
        }
        map.put(0xA0, 1e-7);
        map.put(0xFFFFFFA0, -1e-7);
        map.put(0xB0, 1e-8);
        map.put(0xFFFFFFB0, -1e-8);
        map.put(0xE0, 1 / 128.0);
        map.put(0xFFFFFFE0, -1 / 128.0);
        return map;
    }

    private static Map<Integer, Double> createMalformedOneDecimals(Map<Integer, Double> map) {
        // Create malformed (non-canonical) decimal representations for +/- 1 for every multiplier.
        // This method also creates some canonical decimal representations for large mantissa.
        for (int m = 10; m <= 100000000; m *= 10)
            for (int i = 1; i <= 15; i++) {
                double d = i >= 9 ? (double) m / POWERS[i - 9] : (double) m * POWERS[9 - i];
                map.put((m << 4) + i, d);
                map.put((-m << 4) + i, -d);
            }
        for (int m = 10; m <= 10000000; m *= 10) {
            map.put((m << 7) + 0x20, m / 1e7);
            map.put((-m << 7) + 0x20, -m / 1e7);
            map.put((m << 7) + 0x30, m / 1e8);
            map.put((-m << 7) + 0x30, -m / 1e8);
            map.put((m << 7) + 0x60, m / 128.0);
            map.put((-m << 7) + 0x60, -m / 128.0);
        }
        return map;
    }

    private static Map<Integer, Double> createDigitDecimals(Map<Integer, Double> map) {
        // Create canonical and non-canonical decimal representations for single-digit decimals.
        for (int m = 2; m <= 10; m++) {
            for (int i = 1; i <= 15; i++) {
                double d = i >= 9 ? (double) m / POWERS[i - 9] : (double) m * POWERS[9 - i];
                map.put((m << 4) + i, d);
                map.put((-m << 4) + i, -d);
            }
            map.put((m << 7) + 0x20, m / 1e7);
            map.put((-m << 7) + 0x20, -m / 1e7);
            map.put((m << 7) + 0x30, m / 1e8);
            map.put((-m << 7) + 0x30, -m / 1e8);
            map.put((m << 7) + 0x60, m / 128.0);
            map.put((-m << 7) + 0x60, -m / 128.0);
        }
        return map;
    }

    private static Map<Integer, Double> createFractionalDecimals(Map<Integer, Double> map) {
        // Create canonical and non-canonical decimal representations of 1/128 fractions.
        // This method also creates some canonical decimal representations for large mantissa.
        int correct = 10000000 / 128;
        for (int m = 1; m <= 128; m++) {
            if (m % 2 == 0) {
                map.put(((m * correct / 10) << 4) + 0x0F, m / 128.0);
                map.put(((-m * correct / 10) << 4) + 0x0F, -m / 128.0);
            }
            map.put(((m * correct) << 7) + 0x20, m / 128.0);
            map.put(((-m * correct) << 7) + 0x20, -m / 128.0);
            if (m * 10 * correct < 0xFFFFFF) {
                map.put(((m * 10 * correct) << 7) + 0x30, m / 128.0);
                map.put(((-m * 10 * correct) << 7) + 0x30, -m / 128.0);
            }
            map.put((m << 7) + 0x60, m / 128.0);
            map.put((-m << 7) + 0x60, -m / 128.0);
        }
        return map;
    }

    @Test
    public void testCreatedDecimals() {
        checkCreatedDecimals(createSpecialDecimals(new HashMap<>()));
        checkCreatedDecimals(createMalformedSpecialDecimals(new HashMap<>()));
        checkCreatedDecimals(createOneDecimals(new HashMap<>()));
        checkCreatedDecimals(createMalformedOneDecimals(new HashMap<>()));
        checkCreatedDecimals(createDigitDecimals(new HashMap<>()));
        checkCreatedDecimals(createFractionalDecimals(new HashMap<>()));
    }

    private void checkCreatedDecimals(Map<Integer, Double> map) {
        for (Map.Entry<Integer, Double> e : map.entrySet()) {
            assertEquals(Decimal.toDouble(e.getKey()), e.getValue(), 0.0);
            assertEquals(Decimal.toString(e.getKey()), Decimal.toString(Decimal.compose(e.getValue())));
            assertEquals(
                Decimal.composeDecimal(Decimal.getDecimalMantissa(e.getKey()), Decimal.getDecimalPrecision(e.getKey())),
                Decimal.compose(e.getValue()));
        }
    }

    @Test
    public void testMath() {
        Map<Integer, Double> map = new HashMap<Integer, Double>();
        createSpecialDecimals(map);
        createMalformedSpecialDecimals(map);
        createOneDecimals(map);
        createMalformedOneDecimals(map);
        createDigitDecimals(map);
        createFractionalDecimals(map);
        for (Map.Entry<Integer, Double> e1 : map.entrySet())
            for (Map.Entry<Integer, Double> e2 : map.entrySet()) {
                int c0 = Double.compare(e1.getValue(), e2.getValue());
                int c1 = Decimal.compare(e1.getKey(), e2.getKey());
                if (c0 != c1) {
                    fail("compare(" + str(e1.getKey()) + " aka " + e1.getValue() + ", " +
                        str(e2.getKey()) + " aka " + e2.getValue() + "): " + c0 + " vs " + c1);
                }
                checkMath("subtract", e1, e2, e1.getValue() - e2.getValue(), Decimal.subtract(e1.getKey(), e2.getKey()));
                checkMath("add", e1, e2, e1.getValue() + e2.getValue(), Decimal.add(e1.getKey(), e2.getKey()));
                checkMath("average", e1, e2, (e1.getValue() + e2.getValue()) / 2, Decimal.average(e1.getKey(), e2.getKey()));
            }
    }

    private void checkMath(String name, Map.Entry<Integer, Double> e1, Map.Entry<Integer, Double> e2,
        double expect, int result)
    {
        // recompose expected result via decimal to enforce proper precision loss
        double ulp = 2 * Math.ulp(expect);
        int d1 = Decimal.compose(expect - ulp);
        int d2 = Decimal.compose(expect + ulp);
        double v1 = Decimal.toDouble(d1);
        double v2 = Decimal.toDouble(d2);
        double vr = Decimal.toDouble(result);
        if (!equals(vr, v1) && !equals(vr, v2)) {
            fail(name + "(" + str(e1.getKey()) + " aka " + e1.getValue() + ", " +
                str(e2.getKey()) + " aka " + e2.getValue() + "): " +
                str(result) + " while expected " + expect + " composed as " + str(d1) + " or " + str(d2));
        }
    }
}
