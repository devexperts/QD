/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.glossary.test;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;

import com.dxfeed.glossary.PriceIncrements;
import junit.framework.TestCase;

/**
 * Unit test for {@link PriceIncrements} class.
 */
public class PriceIncrementsTest extends TestCase {

    private static final double EPS = 1e-10;

    public PriceIncrementsTest(String s) {
        super(s);
    }

    public void testPrecision() {
        PriceIncrements pi;
        pi = PriceIncrements.valueOf("0.00000001");
        assertEquals(pi.incrementPrice(0.60618514, 1), 0.60618515);
        Random r = new Random(3);
        for (int i = 1; i < 100000; i++) {
            int x = r.nextInt(100000000);
            double price0 = (double) x / 100000000;
            double price1 = (double) (x + 1) / 100000000;
            assertEquals(pi.incrementPrice(price0, 1), price1);
        }
    }

    public void testLogic() {
        PriceIncrements pi;

        pi = PriceIncrements.valueOf("0.01");
        assertEquals(pi.getText(), "0.01");
        assertTrue(Arrays.equals(pi.getPriceIncrements(), new double[] {0.01}));
        assertEquals(pi.getText(), PriceIncrements.valueOf(0.01).getText());
        assertEquals(pi.getText(), PriceIncrements.valueOf(new double[] {0.01}).getText());
        assertEquals(pi.getPriceIncrement(), 0.01);
        assertEquals(pi.getPriceIncrement(Double.NaN), 0.01);
        assertEquals(pi.getPriceIncrement(0.1234), 0.01);
        assertEquals(pi.getPriceIncrement(-0.1234), 0.01);
        assertEquals(pi.getPriceIncrement(1234), 0.01);
        assertEquals(pi.getPriceIncrement(-1234), 0.01);
        assertEquals(pi.getPricePrecision(), 2);
        assertEquals(pi.getPricePrecision(Double.NaN), 2);
        assertEquals(pi.getPricePrecision(0.1234), 2);
        assertEquals(pi.getPricePrecision(-0.1234), 2);
        assertEquals(pi.getPricePrecision(1234), 2);
        assertEquals(pi.getPricePrecision(-1234), 2);
        assertEquals(pi.roundPrice(0.1234), 0.12, EPS);
        assertEquals(pi.roundPrice(0.1234, 1), 0.13, EPS);
        assertEquals(pi.roundPrice(0.1234, -1), 0.12, EPS);
        assertEquals(pi.roundPrice(-0.1234), -0.12, EPS);
        assertEquals(pi.roundPrice(-0.1234, 1), -0.12, EPS);
        assertEquals(pi.roundPrice(-0.1234, -1), -0.13, EPS);
        assertEquals(pi.incrementPrice(0.1234, 1), 0.13, EPS);
        assertEquals(pi.incrementPrice(0.1234, -1), 0.11, EPS);
        assertEquals(pi.incrementPrice(-0.1234, 1), -0.11, EPS);
        assertEquals(pi.incrementPrice(-0.1234, -1), -0.13, EPS);

        pi = PriceIncrements.valueOf("0.0001 1; 0.01");
        assertEquals(pi.getText(), "0.0001 1; 0.01");
        assertTrue(Arrays.equals(pi.getPriceIncrements(), new double[] {0.0001, 1, 0.01}));
        assertEquals(pi.getText(), PriceIncrements.valueOf(new double[] {0.0001, 1, 0.01}).getText());
        assertEquals(pi.getPriceIncrement(), 0.0001);
        assertEquals(pi.getPriceIncrement(Double.NaN), 0.0001);
        assertEquals(pi.getPriceIncrement(0.1234), 0.0001);
        assertEquals(pi.getPriceIncrement(-0.1234), 0.0001);
        assertEquals(pi.getPriceIncrement(1, -1), 0.0001);
        assertEquals(pi.getPriceIncrement(1, 1), 0.01);
        assertEquals(pi.getPriceIncrement(1234), 0.01);
        assertEquals(pi.getPriceIncrement(-1234), 0.01);
        assertEquals(pi.getPricePrecision(), 4);
        assertEquals(pi.getPricePrecision(Double.NaN), 4);
        assertEquals(pi.getPricePrecision(0.1234), 4);
        assertEquals(pi.getPricePrecision(-0.1234), 4);
        assertEquals(pi.getPricePrecision(1), 4);
        assertEquals(pi.getPricePrecision(1234), 2);
        assertEquals(pi.getPricePrecision(-1234), 2);
        assertEquals(pi.roundPrice(0.1234), 0.1234, EPS);
        assertEquals(pi.roundPrice(0.123456), 0.1235, EPS);
        assertEquals(pi.roundPrice(0.123456, 1), 0.1235, EPS);
        assertEquals(pi.roundPrice(0.123456, -1), 0.1234, EPS);
        assertEquals(pi.roundPrice(-0.1234), -0.1234, EPS);
        assertEquals(pi.roundPrice(-0.123456), -0.1235, EPS);
        assertEquals(pi.roundPrice(-0.123456, 1), -0.1234, EPS);
        assertEquals(pi.roundPrice(-0.123456, -1), -0.1235, EPS);
        assertEquals(pi.roundPrice(7.1234), 7.12, EPS);
        assertEquals(pi.roundPrice(7.123456), 7.12, EPS);
        assertEquals(pi.roundPrice(7.123456, 1), 7.13, EPS);
        assertEquals(pi.roundPrice(7.123456, -1), 7.12, EPS);
        assertEquals(pi.roundPrice(-7.1234), -7.12, EPS);
        assertEquals(pi.roundPrice(-7.123456), -7.12, EPS);
        assertEquals(pi.roundPrice(-7.123456, 1), -7.12, EPS);
        assertEquals(pi.roundPrice(-7.123456, -1), -7.13, EPS);
        assertEquals(pi.incrementPrice(0.1234, 1), 0.1235, EPS);
        assertEquals(pi.incrementPrice(0.1234, -1), 0.1233, EPS);
        assertEquals(pi.incrementPrice(0.123456, 1), 0.1236, EPS);
        assertEquals(pi.incrementPrice(0.123456, -1), 0.1234, EPS);
        assertEquals(pi.incrementPrice(7.123456, 1), 7.13, EPS);
        assertEquals(pi.incrementPrice(7.123456, -1), 7.11, EPS);
        assertEquals(pi.incrementPrice(0.123456, 1, 7), 7.12, EPS);
        assertEquals(pi.incrementPrice(0.123456, -1, 7), -6.88, EPS);
        assertEquals(pi.incrementPrice(-7.123456, 1, 7), -0.1235, EPS);
        assertEquals(pi.incrementPrice(7.123456, -1, 7), 0.1235, EPS);
    }

    public void testRound() {
        PriceIncrements pi = PriceIncrements.valueOf("0.01");

        roundNearest(pi, 0.12500005, 0.13);
        roundNearest(pi, 0.12500000005, 0.12);
        roundNearest(pi, 0.12499995, 0.12);
        roundNearest(pi, 0.12499999995, 0.12);
        roundNearest(pi, 0.11500005, 0.12);
        roundNearest(pi, 0.11500000005, 0.12);
        roundNearest(pi, 0.11499995, 0.11);
        roundNearest(pi, 0.11499999995, 0.12);

        roundDirection(pi, 0.12000005, 0.12, 0.12, 0.13);
        roundDirection(pi, 0.12000000005, 0.12, 0.12, 0.12);
        roundDirection(pi, 0.11999995, 0.11, 0.12, 0.12);
        roundDirection(pi, 0.11999999995, 0.12, 0.12, 0.12);
        roundDirection(pi, -0.12000005, -0.13, -0.12, -0.12);
        roundDirection(pi, -0.12000000005, -0.12, -0.12, -0.12);
        roundDirection(pi, -0.11999995, -0.12, -0.12, -0.11);
        roundDirection(pi, -0.11999999995, -0.12, -0.12, -0.12);

        roundMode(pi, 0.12000005, 0.13, 0.12, 0.13, 0.12, 0.12, 0.12, 0.12);
        roundMode(pi, 0.12000000005, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12);
        roundMode(pi, 0.11999995, 0.12, 0.11, 0.12, 0.11, 0.12, 0.12, 0.12);
        roundMode(pi, 0.11999999995, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12);
        roundMode(pi, -0.12000005, -0.13, -0.12, -0.12, -0.13, -0.12, -0.12, -0.12);
        roundMode(pi, -0.12000000005, -0.12, -0.12, -0.12, -0.12, -0.12, -0.12, -0.12);
        roundMode(pi, -0.11999995, -0.12, -0.11, -0.11, -0.12, -0.12, -0.12, -0.12);
        roundMode(pi, -0.11999999995, -0.12, -0.12, -0.12, -0.12, -0.12, -0.12, -0.12);

        roundMode(pi, 0.12500005, 0.13, 0.12, 0.13, 0.12, 0.13, 0.13, 0.13);
        roundMode(pi, 0.12500000005, 0.13, 0.12, 0.13, 0.12, 0.13, 0.12, 0.12);
        roundMode(pi, 0.12499995, 0.13, 0.12, 0.13, 0.12, 0.12, 0.12, 0.12);
        roundMode(pi, 0.12499999995, 0.13, 0.12, 0.13, 0.12, 0.13, 0.12, 0.12);
        roundMode(pi, 0.11500005, 0.12, 0.11, 0.12, 0.11, 0.12, 0.12, 0.12);
        roundMode(pi, 0.11500000005, 0.12, 0.11, 0.12, 0.11, 0.12, 0.11, 0.12);
        roundMode(pi, 0.11499995, 0.12, 0.11, 0.12, 0.11, 0.11, 0.11, 0.11);
        roundMode(pi, 0.11499999995, 0.12, 0.11, 0.12, 0.11, 0.12, 0.11, 0.12);
        roundMode(pi, -0.12500005, -0.13, -0.12, -0.12, -0.13, -0.13, -0.13, -0.13);
        roundMode(pi, -0.12500000005, -0.13, -0.12, -0.12, -0.13, -0.13, -0.12, -0.12);
        roundMode(pi, -0.12499995, -0.13, -0.12, -0.12, -0.13, -0.12, -0.12, -0.12);
        roundMode(pi, -0.12499999995, -0.13, -0.12, -0.12, -0.13, -0.13, -0.12, -0.12);
        roundMode(pi, -0.11500005, -0.12, -0.11, -0.11, -0.12, -0.12, -0.12, -0.12);
        roundMode(pi, -0.11500000005, -0.12, -0.11, -0.11, -0.12, -0.12, -0.11, -0.12);
        roundMode(pi, -0.11499995, -0.12, -0.11, -0.11, -0.12, -0.11, -0.11, -0.11);
        roundMode(pi, -0.11499999995, -0.12, -0.11, -0.11, -0.12, -0.12, -0.11, -0.12);

        assertEquals(0.12, pi.roundPrice(0.12, RoundingMode.UNNECESSARY), EPS);
        assertEquals(-0.12, pi.roundPrice(-0.12, RoundingMode.UNNECESSARY), EPS);
        try {
            pi.roundPrice(0.1234, RoundingMode.UNNECESSARY);
            fail();
        } catch (ArithmeticException e) {
            //ignore
        }
        try {
            pi.roundPrice(-0.1234, RoundingMode.UNNECESSARY);
            fail();
        } catch (ArithmeticException e) {
            //ignore
        }
    }

    private void roundNearest(PriceIncrements pi, double price, double nearest) {
        assertEquals(nearest, pi.roundPrice(price), EPS);
        assertEquals(-nearest, pi.roundPrice(-price), EPS);
    }

    private void roundDirection(PriceIncrements pi, double price, double decrease, double nearest, double increase) {
        assertEquals(decrease, pi.roundPrice(price, -1), EPS);
        assertEquals(nearest, pi.roundPrice(price, 0), EPS);
        assertEquals(increase, pi.roundPrice(price, 1), EPS);
    }

    private void roundMode(PriceIncrements pi, double price, double up, double down, double ceiling, double floor, double halfUp, double halfDown, double halfEven) {
        assertEquals(up, pi.roundPrice(price, RoundingMode.UP), EPS);
        assertEquals(down, pi.roundPrice(price, RoundingMode.DOWN), EPS);
        assertEquals(ceiling, pi.roundPrice(price, RoundingMode.CEILING), EPS);
        assertEquals(floor, pi.roundPrice(price, RoundingMode.FLOOR), EPS);
        assertEquals(halfUp, pi.roundPrice(price, RoundingMode.HALF_UP), EPS);
        assertEquals(halfDown, pi.roundPrice(price, RoundingMode.HALF_DOWN), EPS);
        assertEquals(halfEven, pi.roundPrice(price, RoundingMode.HALF_EVEN), EPS);
    }
}
