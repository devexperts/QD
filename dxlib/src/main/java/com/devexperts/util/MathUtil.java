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
package com.devexperts.util;

import java.math.RoundingMode;
import java.util.stream.LongStream;

/**
 * A collection of non-trivial mathematical utility methods.
 */
public class MathUtil {
    private MathUtil() {} // to prevent accidental initialization

    private static final int MAX_DECIMAL_DIGITS = 14;
    private static final long[] POW10 = LongStream.iterate(1L, v -> v * 10).limit(MAX_DECIMAL_DIGITS + 1).toArray();

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
        int pow = Math.min(MAX_DECIMAL_DIGITS, MAX_DECIMAL_DIGITS - 1 - FastDoubleUtil.extractExponent(abs));
        for (int i = pow; i >= 0; i--) {
            long mantissa = (long) (POW10[i] * abs + 0.5);
            if (mantissa < POW10[MAX_DECIMAL_DIGITS])
                return signum * mantissa / POW10[i];
        }
        // Mantissa >= 10^14 with fractions -- just round
        return Math.round(x);
    }

    /**
     * Returns a double value scaled according to the {@linkplain RoundingMode} and scale value.
     * This is a fast implementation compared to {@linkplain java.math.BigDecimal#setScale}.
     * Returns similar result as BigDecimal setScale for RoundingMode (HALF_UP, HALF_DOWN, HALF_EVEN)
     * <p>
     * For Rounding Mode (UP, DOWN, CEILING, FLOOR) and values larger than 1E16 by absolute value it is possible
     * to have different result than {@linkplain java.math.BigDecimal} in very rare case.
     * The positive <var>scale</var> parameter defines a number of fraction digits in the double value.
     * The negative <var>scale</var> parameter defines a number of integer digits that will be assigned to 0.
     *
     * @param value double for round
     * @param scale the number of fractional digits for the positive scale
     *              and the number of integer digits to be assigned the value 0 for negative scale
     * @param mode {@linkplain java.math.RoundingMode}
     * @return scaled double value
     * @throws ArithmeticException if the rounding mode is {@code UNNECESSARY} and the operation would require rounding.
     */
    public static double roundDecimal(double value, int scale, RoundingMode mode) {
        return FastDoubleUtil.roundScale(value, scale, mode);
    }

    /**
     * Fast implementation of double parser. This method works only with decimal representation of double values and is
     * intolerant to leading or trailing spaces in CharSequence.
     *
     * @param text with double value
     * @return double presentation
     * @throws NullPointerException  if the {@code text} is null
     * @throws NumberFormatException if the {@code text} does not contain valid double value
     */
    public static double parseDouble(CharSequence text) throws NumberFormatException {
        return FastDoubleUtil.parseDouble(text);
    }

    /**
     * Fast implementation of double parser. This method works only with decimal representation of double values
     * and is intolerant to leading or trailing spaces in CharSequence range from start to end positions.
     *
     * @param text with double value
     * @param start start position of value
     * @param end end position of value, exclusive
     * @return double presentation
     * @throws NullPointerException  if the {@code text} is null
     * @throws NumberFormatException if the {@code text} does not contain valid double value
     */
    public static double parseDouble(CharSequence text, int start, int end) throws NumberFormatException {
        return FastDoubleUtil.parseDouble(text, start, end);
    }

    /**
     * Returns a string representation of the double argument with specified precision.
     * Produced string representation matches the syntax of {@link Double#toString} but conforms to the following rules:
     * <ul>
     * <li>A wider range is used for engineering format:
     * Engineering notation is used for numbers in range from 0.000&nbsp;000&nbsp;001 to 1&nbsp;000&nbsp;000&nbsp;000,
     * otherwise scientific notation is used.
     * <li>In the scientific notation additional {@code '.0'} is omitted
     * (Example: {@code '1E10'} instead of {@code '1.0E10'} for {@linkplain Double#toString})
     * <li>
     * The <var>precision</var> parameter defines a number of significant digits in the result string.
     * If <var>precision</var> is zero the formatting is performed without rounding like {@link Double#toString}
     * <p>
     * For a number with absolute value greater than 1E16, in very rare cases it's possible to have rounding not
     * exactly the same as
     * {@code BigDecimal.valueOf(value).round(new MathContext(precision, RoundingMode.HALF_UP))},
     * but anyway the value will match the result with {@linkplain RoundingMode#UP} or {@linkplain RoundingMode#DOWN}
     * </ul>
     *
     * @param value double
     * @param precision number of significant digits, effective rounding for [1,16]
     * @return a string representation of the double argument with specified precision
     * @throws IllegalArgumentException if precision less than 0
     */
    public static String formatDoublePrecision(double value, int precision) {
        return FastDoubleUtil.formatDoublePrecision(value, precision);
    }

    /**
     * Returns a string representation of the double argument with specified precision.
     * Produced string representation matches the syntax of {@link Double#toString} but conforms to the following rules:
     * <ul>
     * <li>A wider range is used for engineering format:
     * Engineering notation is used for numbers in range from 0.000&nbsp;000&nbsp;001 to 1&nbsp;000&nbsp;000&nbsp;000,
     * otherwise scientific notation is used.
     * <li>In the scientific notation additional {@code '.0'} is omitted
     * (Example: {@code '1E10'} instead of {@code '1.0E10'} for {@linkplain Double#toString})
     * <li>
     * The <var>precision</var> parameter defines a number of significant digits in the result string.
     * If <var>precision</var> is zero the formatting is performed without rounding like {@link Double#toString}
     * <p>
     * For a number with absolute value greater than 1E16, in very rare cases it's possible to have rounding not
     * exactly the same as
     * {@code BigDecimal.valueOf(value).round(new MathContext(precision, RoundingMode.HALF_UP))},
     * but anyway the value will match the result with {@linkplain RoundingMode#UP} or {@linkplain RoundingMode#DOWN}
     * </ul>
     *
     * @param sb StringBuilder with formatted result
     * @param value double
     * @param precision number of significant digits, effective rounding for [1,16]
     * @throws NullPointerException  if the {@code sb} is null
     * @throws IllegalArgumentException for precision less than 0
     */
    public static void formatDoublePrecision(StringBuilder sb, double value, int precision) {
        FastDoubleUtil.formatDoublePrecision(sb, value, precision);
    }

    /**
     * Returns a string representation of the double argument whose scale is the specified value.
     * Produced string representation matches the syntax of {@link Double#toString} but conforms to the following rules:
     * <ul>
     * <li>A wider range is used for engineering format:
     * Engineering notation is used for numbers in range from 0.000&nbsp;000&nbsp;001 to 1&nbsp;000&nbsp;000&nbsp;000,
     * otherwise scientific notation is used.
     * <li>In the scientific notation additional {@code '.0'} is omitted
     * (Example: {@code '1E10'} instead of {@code '1.0E10'} for {@linkplain Double#toString})
     * <li>
     * The positive <var>scale</var> parameter defines a number of fraction digits in the result string.
     * The negative <var>scale</var> parameter defines a number of integer digits that will be assigned to 0
     * with defined round mode. This approach similar as in {@linkplain java.math.BigDecimal#setScale}.
     * For a number with absolute value greater than 1E16, in very rare cases it's possible to have rounding not
     * exactly the same as
     * {@code BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP))},
     * but anyway the value will match the result with {@linkplain RoundingMode#UP} or {@linkplain RoundingMode#DOWN}
     * </ul>
     *
     * @param value double
     * @param scale the number of fractional digits for the positive scale
     *              and the number of integer digits to be assigned the value 0 for negative scale
     * @return a string representation of the double argument with specified scale
     */
    public static String formatDouble(double value, int scale) {
        return FastDoubleUtil.formatDoubleScale(value, scale);
    }

    /**
     * Returns a string representation of the double argument whose scale is the specified value.
     * Produced string representation matches the syntax of {@link Double#toString} but conforms to the following rules:
     * <ul>
     * <li>A wider range is used for engineering format:
     * Engineering notation is used for numbers in range from 0.000&nbsp;000&nbsp;001 to 1&nbsp;000&nbsp;000&nbsp;000,
     * otherwise scientific notation is used.
     * <li>In the scientific notation additional {@code '.0'} is omitted
     * (Example: {@code '1E10'} instead of {@code '1.0E10'} for {@linkplain Double#toString})
     * <li>
     * The positive <var>scale</var> parameter defines a number of fraction digits in the result string.
     * The negative <var>scale</var> parameter defines a number of integer digits that will be assigned to 0
     * with defined round mode. This approach similar as in {@linkplain java.math.BigDecimal#setScale}.
     * For a number with absolute value greater than 1E16, in very rare cases it's possible to have rounding not
     * exactly the same as
     * {@code BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP))},
     * but anyway the value will match the result with {@linkplain RoundingMode#UP} or {@linkplain RoundingMode#DOWN}
     * </ul>
     *
     * @param sb StringBuilder with formatted result with specified scale
     * @param value double
     * @param scale the number of fractional digits for the positive scale
     *              and the number of integer digits to be assigned the value 0 for negative scale
     */
    public static void formatDouble(StringBuilder sb, double value, int scale) {
        FastDoubleUtil.formatDoubleScale(sb, value, scale);
    }

    /**
     * Returns a double value rounded according to the {@linkplain RoundingMode} and round precision.
     * If the round is 0 or more than 16 then no rounding takes place.
     * This is a fast implementation compared to {@linkplain java.math.BigDecimal} round.
     * Returns similar result as BigDecimal round for RoundingMode (HALF_UP, HALF_DOWN, HALF_EVEN)
     * <p>
     * For Rounding Mode (UP, DOWN, CEILING, FLOOR) and values larger than 1E16 by absolute value it is possible
     * to have different result than {@linkplain java.math.BigDecimal} in very rare case.
     *
     * @param value double for round
     * @param precision number of significant digits, effective rounding for [1,16]
     * @param mode {@linkplain java.math.RoundingMode}
     * @return rounded double value
     * @throws ArithmeticException if the rounding mode is {@code UNNECESSARY} and the operation would require rounding.
     */
    public static double roundPrecision(double value, int precision, RoundingMode mode) {
        return FastDoubleUtil.roundPrecision(value, precision, mode);
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
