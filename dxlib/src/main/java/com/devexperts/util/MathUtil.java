/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.util;

/**
 * A collection of non-trivial mathematical utility methods.
 */
public class MathUtil {
    private MathUtil() {} // to prevent accidental initialization

    private static final int MAX_DECIMAL_DIGITS = 14;
    private static final long[] POW10 = new long[MAX_DECIMAL_DIGITS + 1];

    static {
        POW10[0] = 1;
        for (int i = 1; i < POW10.length; i++)
            POW10[i] = POW10[i - 1] * 10;
    }

    /**
     * Rounds a specified double number to a decimal number with at most
     * 14 significant digits and at most 14 digits after decimal point.
     * When x is integer, NaN or infinity, then x is returned
     * (this method does not round big integers to powers of 10).
     *
     * <p>For example, suppose you have 1 dollar and 10 cents and you pay 20 cent.
     * You should keep 90 cents. However, the following expression is <b>false</b> in Java:
     * <pre>1.1 - 0.2 == 0.9</pre>
     * because both 1.1 and 0.2 do not have precise representations in {@code double}.
     * To make this comparison work, you have to use {@code roundDecimal} method:
     * <pre>roundDecimal(1.1 - 0.2) == 0.9</pre>
     *
     * <p>As a general rule, you should use {@code roundDecimal} after any operation
     * (addition, subtraction, multiplication, division) on two decimal numbers if you
     * know that the result is a decimal with at most 14 significant digits and at most
     * 14 digits after decimal point.
     */
    public static double roundDecimal(double x) {
        if (Double.isNaN(x) || x == Math.floor(x))
            return x; // integer, NaN, or +/- inf
        double signum = Math.signum(x);
        double abs = Math.abs(x);
        int pow = Math.min(MAX_DECIMAL_DIGITS, MAX_DECIMAL_DIGITS - 1 - (int) (Math.floor(Math.log10(abs))));
        for (int i = pow; i >= 0; i--) {
            long mantissa = (long) (POW10[i] * abs + 0.5);
            if (mantissa < POW10[MAX_DECIMAL_DIGITS])
                return signum * mantissa / POW10[i];
        }
        // Mantissa >= 10^14 with fractions -- just round
        return Math.round(x);
    }

    /**
     * Returns quotient according to number theory - i.e. when remainder is zero or positive.
     *
     * @param a dividend
     * @param b divisor
     * @return quotient according to number theory
     */
    public static int div(int a, int b) {
        return a >= 0 ? a / b : b >= 0 ? (a + 1) / b - 1 : (a + 1) / b + 1;
    }

    /**
     * Returns quotient according to number theory - i.e. when remainder is zero or positive.
     *
     * @param a dividend
     * @param b divisor
     * @return quotient according to number theory
     */
    public static long div(long a, long b) {
        return a >= 0 ? a / b : b >= 0 ? (a + 1) / b - 1 : (a + 1) / b + 1;
    }

    /**
     * Returns remainder according to number theory - i.e. when remainder is zero or positive.
     *
     * @param a dividend
     * @param b divisor
     * @return remainder according to number theory
     */
    public static int rem(int a, int b) {
        int r = a % b;
        return r >= 0 ? r : b >= 0 ? r + b : r - b;
    }

    /**
     * Returns remainder according to number theory - i.e. when remainder is zero or positive.
     *
     * @param a dividend
     * @param b divisor
     * @return remainder according to number theory
     */
    public static long rem(long a, long b) {
        long r = a % b;
        return r >= 0 ? r : b >= 0 ? r + b : r - b;
    }
}
