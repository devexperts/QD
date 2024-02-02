/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.glossary;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.RoundingMode;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class PriceIncrementsTest {

    @Test
    public void testPrecision() {
        PriceIncrements pi;
        pi = PriceIncrements.valueOf("0.00000001");
        checkEqualsDoubles(pi.incrementPrice(0.60618514, 1), 0.60618515);
        Random r = new Random(3);
        for (int i = 1; i < 100000; i++) {
            int x = r.nextInt(100000000);
            double price0 = (double) x / 100000000;
            double price1 = (double) (x + 1) / 100000000;
            checkEqualsDoubles(pi.incrementPrice(price0, 1), price1);
        }
    }

    @Test
    public void testSerialization() {
        for (String text : new String[] {null, "", "0", "0.0e0", "0.01", "0.0001 1; 0.01"}) {
            PriceIncrements pi = PriceIncrements.valueOf(text);
            PriceIncrements result;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(out);
                output.writeObject(pi);
                output.close();

                ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
                ObjectInputStream input = new ObjectInputStream(in);
                result = (PriceIncrements) input.readObject();
                input.close();
            } catch (IOException | ClassNotFoundException e) {
                fail(e.getMessage());
                return;
            }
            assertEquals(pi.getText(), result.getText());
            assertArrayEquals(pi.getPriceIncrements(), result.getPriceIncrements(), 0.0);
        }
    }

    @Test
    public void testSmallPrecision() {
        PriceIncrements pi = PriceIncrements.valueOf("1E-18");

        checkEqualsDoubles(pi.incrementPrice(0.0001, 1), 1.00000000000001E-4);
        checkEqualsDoubles(pi.incrementPrice(1E-9, 1), 1.000000001E-9);
        checkEqualsDoubles(pi.incrementPrice(1E-18, 1), 2E-18);
        checkEqualsDoubles(pi.incrementPrice(1E-18, -1), 0);

        checkEqualsDoubles(pi.incrementPrice(1E-9, -1), 9.99999999E-10);
        checkEqualsDoubles(pi.incrementPrice(0, 1), 1E-18);
        checkEqualsDoubles(pi.incrementPrice(0, -1), -1E-18);

        checkEqualsDoubles(pi.incrementPrice(1E-15, -1), 9.99E-16);
        checkEqualsDoubles(pi.incrementPrice(1E-16, -1), 9.9E-17);
        checkEqualsDoubles(pi.incrementPrice(1E-17, -1), 9.0E-18);
    }

    @Test
    public void testOverPrecisions() {
        PriceIncrements pi = PriceIncrements.valueOf("1E-18");

        checkEqualsDoubles(pi.incrementPrice(1, 1), 1);
        checkEqualsDoubles(pi.incrementPrice(0.1, 1), 0.1);
        checkEqualsDoubles(pi.incrementPrice(0.01, 1), 0.01);
        checkEqualsDoubles(pi.incrementPrice(0.001, 1), 0.001);

        checkEqualsDoubles(pi.incrementPrice(10, 1), 10);
        checkEqualsDoubles(pi.incrementPrice(1E2, 1), 1E2);
        checkEqualsDoubles(pi.incrementPrice(1E3, 1), 1E3);
        checkEqualsDoubles(pi.incrementPrice(1E4, 1), 1E4);

        checkEqualsDoubles(pi.incrementPrice(1, -1), 1);
        checkEqualsDoubles(pi.incrementPrice(0.1, -1), 0.1);

        checkEqualsDoubles(pi.incrementPrice(10, -1), 10);
        checkEqualsDoubles(pi.incrementPrice(1E4, -1), 1E4);
    }

    @Test
    public void computePrecision() {
        assertEquals(0, checkPricePrecision(100000000.123456789));
        assertEquals(4, checkPricePrecision(100.12345678));
        assertEquals(7, checkPricePrecision(0.12345678912345678));
        assertEquals(9, checkPricePrecision(0.000100001));
        assertEquals(4,  checkPricePrecision(0.000100000000000001));

        PriceIncrements pi = PriceIncrements.valueOf("1E-10 1E-5; 0.01");
        assertEquals(2, pi.getPricePrecision(1.0000000000100001E-5));
        assertEquals(10, pi.getPricePrecision(1.00000000001E-5));

        // values from 1 to 1E-18
        IntStream.rangeClosed(0, 18).forEach(i -> assertEquals(i, checkPricePrecision(Math.pow(10, -i))));
    }

    @Test
    public void testCryptoPriceIncrements() {
        PriceIncrements pi = PriceIncrements.valueOf("1E-18");
        assertEquals(pi.getText(), "1E-18");
        assertArrayEquals(new double[] { 1E-18 }, pi.getPriceIncrements(), 0.0);

        assertEquals(pi.getText(), PriceIncrements.valueOf(1E-18).getText());
        assertEquals(pi.getText(), PriceIncrements.valueOf(new double[] { 1E-18 }).getText());

        assertEquals(pi.getPricePrecision(), 18);
        assertEquals(pi.getPricePrecision(0.12345), 18);

        checkEqualsDoubles(pi.getPriceIncrement(Double.NaN), 1E-18);

        checkEqualsDoubles(pi.getPriceIncrement(1E-10), 1E-18);
        checkEqualsDoubles(pi.getPriceIncrement(1E10), 1E-18);

        checkEqualsDoubles(pi.roundPrice(1.1E-15, 1), 1.1E-15);
        checkEqualsDoubles(pi.roundPrice(1.01E-15, 1), 1.01E-15);
        checkEqualsDoubles(pi.roundPrice(1.001E-15, 1), 1.001E-15);
        checkEqualsDoubles(pi.roundPrice(1.0001E-15, 1), 1.001E-15);
        checkEqualsDoubles(pi.roundPrice(1.00012345E-15, 1), 1.001E-15);

        checkEqualsDoubles(pi.roundPrice(1.1E-15, -1), 1.1E-15);
        checkEqualsDoubles(pi.roundPrice(1.01E-15, -1), 1.01E-15);
        checkEqualsDoubles(pi.roundPrice(1.001E-15, -1), 1.001E-15);
        checkEqualsDoubles(pi.roundPrice(1.0001E-15, -1), 1E-15);
        checkEqualsDoubles(pi.roundPrice(1.00012345E-15, -1), 1E-15);

        checkEqualsDoubles(pi.incrementPrice(1E-15, 1), 1.001E-15);
        checkEqualsDoubles(pi.incrementPrice(1E-15, -1), 9.99E-16);

        checkEqualsDoubles(pi.incrementPrice(1, 1), 1);
        checkEqualsDoubles(pi.incrementPrice(0.1, 1), 0.1);
        checkEqualsDoubles(pi.incrementPrice(0.01, 1), 0.01);
        checkEqualsDoubles(pi.incrementPrice(0.001, 1), 0.001);
        checkEqualsDoubles(pi.incrementPrice(1E-4, 1), 1.00000000000001E-4);
        checkEqualsDoubles(pi.incrementPrice(1E-5, 1), 1.0000000000001E-5);
        checkEqualsDoubles(pi.incrementPrice(1E-6, 1), 1.000000000001E-6);
        checkEqualsDoubles(pi.incrementPrice(1E-7, 1), 1.00000000001E-7);
        checkEqualsDoubles(pi.incrementPrice(1E-8, 1), 1.0000000001E-8);
        checkEqualsDoubles(pi.incrementPrice(1E-9, 1), 1.000000001E-9);
        checkEqualsDoubles(pi.incrementPrice(1E-10, 1), 1.00000001E-10);
        checkEqualsDoubles(pi.incrementPrice(1E-11, 1), 1.0000001E-11);
        checkEqualsDoubles(pi.incrementPrice(1E-12, 1), 1.000001E-12);
        checkEqualsDoubles(pi.incrementPrice(1E-13, 1), 1.00001E-13);
        checkEqualsDoubles(pi.incrementPrice(1E-14, 1), 1.0001E-14);
        checkEqualsDoubles(pi.incrementPrice(1E-15, 1), 1.001E-15);
        checkEqualsDoubles(pi.incrementPrice(1E-16, 1), 1.01E-16);
        checkEqualsDoubles(pi.incrementPrice(1E-17, 1), 1.1E-17);
        checkEqualsDoubles(pi.incrementPrice(1E-18, 1), 2E-18);

        checkEqualsDoubles(pi.incrementPrice(1.23456E-10, 1), 1.23456001E-10);
        checkEqualsDoubles(pi.incrementPrice(1.23456789E-15, 1), 1.236E-15);
        checkEqualsDoubles(pi.incrementPrice(1.23456788E-10, 1), 1.23456789E-10);
    }

    @Test
    public void testLogic() {
        PriceIncrements pi;

        pi = PriceIncrements.valueOf("0.01");
        assertEquals(pi.getText(), "0.01");
        assertArrayEquals(new double[] { 0.01 }, pi.getPriceIncrements(), 0.0);
        assertEquals(pi.getText(), PriceIncrements.valueOf(0.01).getText());
        assertEquals(pi.getText(), PriceIncrements.valueOf(new double[] {0.01}).getText());
        checkEqualsDoubles(pi.getPriceIncrement(), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(Double.NaN), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(0.1234), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(-0.1234), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(1234), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(-1234), 0.01);
        assertEquals(pi.getPricePrecision(), 2);
        assertEquals(pi.getPricePrecision(Double.NaN), 2);
        assertEquals(pi.getPricePrecision(0.1234), 2);
        assertEquals(pi.getPricePrecision(-0.1234), 2);
        assertEquals(pi.getPricePrecision(1234), 2);
        assertEquals(pi.getPricePrecision(-1234), 2);
        checkEqualsDoubles(pi.roundPrice(0.1234), 0.12);
        checkEqualsDoubles(pi.roundPrice(0.1234, 1), 0.13);
        checkEqualsDoubles(pi.roundPrice(0.1234, -1), 0.12);
        checkEqualsDoubles(pi.roundPrice(-0.1234), -0.12);
        checkEqualsDoubles(pi.roundPrice(-0.1234, 1), -0.12);
        checkEqualsDoubles(pi.roundPrice(-0.1234, -1), -0.13);
        checkEqualsDoubles(pi.incrementPrice(0.1234, 1), 0.13);
        checkEqualsDoubles(pi.incrementPrice(0.1234, -1), 0.11);
        checkEqualsDoubles(pi.incrementPrice(-0.1234, 1), -0.11);
        checkEqualsDoubles(pi.incrementPrice(-0.1234, -1), -0.13);

        pi = PriceIncrements.valueOf("0.0001 1; 0.01");
        assertEquals(pi.getText(), "0.0001 1; 0.01");
        assertArrayEquals(new double[] { 0.0001, 1, 0.01 }, pi.getPriceIncrements(), 0.0);
        assertEquals(pi.getText(), PriceIncrements.valueOf(new double[] { 0.0001, 1, 0.01 }).getText());
        checkEqualsDoubles(pi.getPriceIncrement(), 0.0001);
        checkEqualsDoubles(pi.getPriceIncrement(Double.NaN), 0.0001);
        checkEqualsDoubles(pi.getPriceIncrement(0.1234), 0.0001);
        checkEqualsDoubles(pi.getPriceIncrement(-0.1234), 0.0001);
        checkEqualsDoubles(pi.getPriceIncrement(1, -1), 0.0001);
        checkEqualsDoubles(pi.getPriceIncrement(1, 1), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(1234), 0.01);
        checkEqualsDoubles(pi.getPriceIncrement(-1234), 0.01);
        assertEquals(pi.getPricePrecision(), 4);
        assertEquals(pi.getPricePrecision(Double.NaN), 4);
        assertEquals(pi.getPricePrecision(0.1234), 4);
        assertEquals(pi.getPricePrecision(-0.1234), 4);
        assertEquals(pi.getPricePrecision(1), 4);
        assertEquals(pi.getPricePrecision(1234), 2);
        assertEquals(pi.getPricePrecision(-1234), 2);
        checkEqualsDoubles(pi.roundPrice(0.1234), 0.1234);
        checkEqualsDoubles(pi.roundPrice(0.123456), 0.1235);
        checkEqualsDoubles(pi.roundPrice(0.123456, 1), 0.1235);
        checkEqualsDoubles(pi.roundPrice(0.123456, -1), 0.1234);
        checkEqualsDoubles(pi.roundPrice(-0.1234), -0.1234);
        checkEqualsDoubles(pi.roundPrice(-0.123456), -0.1235);
        checkEqualsDoubles(pi.roundPrice(-0.123456, 1), -0.1234);
        checkEqualsDoubles(pi.roundPrice(-0.123456, -1), -0.1235);
        checkEqualsDoubles(pi.roundPrice(7.1234), 7.12);
        checkEqualsDoubles(pi.roundPrice(7.123456), 7.12);
        checkEqualsDoubles(pi.roundPrice(7.123456, 1), 7.13);
        checkEqualsDoubles(pi.roundPrice(7.123456, -1), 7.12);
        checkEqualsDoubles(pi.roundPrice(-7.1234), -7.12);
        checkEqualsDoubles(pi.roundPrice(-7.123456), -7.12);
        checkEqualsDoubles(pi.roundPrice(-7.123456, 1), -7.12);
        checkEqualsDoubles(pi.roundPrice(-7.123456, -1), -7.13);
        checkEqualsDoubles(pi.incrementPrice(0.1234, 1), 0.1235);
        checkEqualsDoubles(pi.incrementPrice(0.1234, -1), 0.1233);
        checkEqualsDoubles(pi.incrementPrice(0.123456, 1), 0.1236);
        checkEqualsDoubles(pi.incrementPrice(0.123456, -1), 0.1234);
        checkEqualsDoubles(pi.incrementPrice(7.123456, 1), 7.13);
        checkEqualsDoubles(pi.incrementPrice(7.123456, -1), 7.11);
        checkEqualsDoubles(pi.incrementPrice(0.123456, 1, 7), 7.12);
        checkEqualsDoubles(pi.incrementPrice(0.123456, -1, 7), -6.88);
        checkEqualsDoubles(pi.incrementPrice(-7.123456, 1, 7), -0.1235);
        checkEqualsDoubles(pi.incrementPrice(7.123456, -1, 7), 0.1235);
    }

    @Test
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

        checkEqualsDoubles(0.12, pi.roundPrice(0.12, RoundingMode.UNNECESSARY));
        checkEqualsDoubles(-0.12, pi.roundPrice(-0.12, RoundingMode.UNNECESSARY));
        assertThrows(ArithmeticException.class, () -> pi.roundPrice(0.1234, RoundingMode.UNNECESSARY));
        assertThrows(ArithmeticException.class, () -> pi.roundPrice(-0.1234, RoundingMode.UNNECESSARY));
    }

    private void roundNearest(PriceIncrements pi, double price, double nearest) {
        checkEqualsDoubles(nearest, pi.roundPrice(price));
        checkEqualsDoubles(-nearest, pi.roundPrice(-price));
    }

    private void roundDirection(PriceIncrements pi, double price, double decrease, double nearest, double increase) {
        checkEqualsDoubles(decrease, pi.roundPrice(price, -1));
        checkEqualsDoubles(nearest, pi.roundPrice(price, 0));
        checkEqualsDoubles(increase, pi.roundPrice(price, 1));
    }

    private void roundMode(PriceIncrements pi, double price, double up, double down, double ceiling, double floor,
        double halfUp, double halfDown, double halfEven)
    {
        checkEqualsDoubles(up, pi.roundPrice(price, RoundingMode.UP));
        checkEqualsDoubles(down, pi.roundPrice(price, RoundingMode.DOWN));
        checkEqualsDoubles(ceiling, pi.roundPrice(price, RoundingMode.CEILING));
        checkEqualsDoubles(floor, pi.roundPrice(price, RoundingMode.FLOOR));
        checkEqualsDoubles(halfUp, pi.roundPrice(price, RoundingMode.HALF_UP));
        checkEqualsDoubles(halfDown, pi.roundPrice(price, RoundingMode.HALF_DOWN));
        checkEqualsDoubles(halfEven, pi.roundPrice(price, RoundingMode.HALF_EVEN));
    }

    private static void checkEqualsDoubles(double expected, double actual) {
        assertEquals(expected, actual, 0.0);
    }

    private static int checkPricePrecision(double value) {
        PriceIncrements pi = PriceIncrements.valueOf(value);
        return pi.getPricePrecision();
    }
}
