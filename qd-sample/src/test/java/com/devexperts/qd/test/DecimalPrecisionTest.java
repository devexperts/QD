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

import static org.junit.Assert.fail;

public class DecimalPrecisionTest {

    @Test
    public void testDecimalPrecisionListed() {
        check(0);
        check(1);
        check(10);
        check(0.1);
        check(12345678);
        check(98765432);
        check(1234.5678);
        check(9876.5432);
        check(-132807800);
        check(2283111300.0);
        // boundary decimals
        check(Decimal.toDouble(0xffffffff));
        check(Decimal.toDouble(0x7fffffff));
        check(Decimal.toDouble(0xfffffff1));
        check(Decimal.toDouble(0x7ffffff1));
        // infinities and NaNs shall work
        check(Double.POSITIVE_INFINITY);
        check(Double.NEGATIVE_INFINITY);
        check(Double.NaN);
    }

    @Test
    public void testDecimalPrecisionRnd() {
        double[] pow10 = new double[6];
        pow10[0] = 1;
        for (int i = 1; i < 6; i++) {
            pow10[i] = 10 * pow10[i - 1];
        }
        // makes sure that correct 8-digit double survives compose/toDouble
        Random r = new Random(1);
        for (int cnt = 0; cnt < 100000; cnt++) {
            int i = r.nextInt(100000000);
            double p = pow10[r.nextInt(6)];
            double d = r.nextBoolean() ? i * p : i / p;
            check(d);
        }
    }

    private void check(double d) {
        double e = Decimal.toDouble(Decimal.compose(d));
        if (Double.doubleToLongBits(d) != Double.doubleToLongBits(e))
            fail("Binary representation was lost: " + d + " != " + e);
    }
}
