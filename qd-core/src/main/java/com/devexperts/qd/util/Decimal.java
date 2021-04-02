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
package com.devexperts.qd.util;

import com.devexperts.util.WideDecimal;

import java.util.Arrays;

/**
 * The <code>Decimal</code> class contains a set of methods to work with
 * floating-point numbers packed into <code>int</code> primitive type.
 * Also, it can be used to wrap a single value in an object with interface
 * like that of the {@link Number} class.
 * <p>
 * Unlike floating-point numbers represented by <code>double</code> and
 * <code>float</code> primitive types, the <code>Decimal</code> uses
 * exponent with base 10, allowing absolute precision for decimal fractions.
 * It has the standard precision of 8 decimal digits and is able to represent numbers
 * as large as 13,421,772,700,000,000 by absolute value or as precise as
 * 0.000,001 (if precision permits). It uses variable of type <code>int</code>
 * for representation, which is divided into two parts as follows:
 * <ul>
 * <li> The lowest 4 bits are used to represent power of the exponent.
 * The exact multipliers are (in order): Infinity
 * (see "extra precision formats and special cases" below),
 * 100000000, 10000000, 1000000,
 * 100000, 10000, 1000, 100, 10, 1, 0.1, 0.01, 0.001, 0.0001, 0.00001, 0.000001.
 * <li>The highest 28 bits are used to represent mantissa in a two's complement format.
 * The mantissa may be from -134217727 to +134217727 (the possible value of -134217728
 * is not used because it does not have corresponding positive value).
 * </ul>
 * <p>
 * To calculate described parts and the real value one may use following code:
 * <code>
 * <br>int mantissa = decimal &gt;&gt; 4;
 * <br>int power = decimal &amp; 0x0F;
 * <br>double real_value = (double) mantissa * MULTIPLIERS[power];
 * <br></code>
 * <p>
 * The real formula in {@link #toDouble toDouble} method is more complex, because
 * it guarantees that the resulting <code>double</code> is the value closest to the
 * true decimal number and it also supports extra precision formats.
 * <p>
 * All numbers have single canonical representation in this format which is
 * defined as a number with smallest possible power without loss of precision.
 * The <code>Decimal</code> supports Not-a-Number and Infinities in the same way
 * as standard floating-point numbers do; these numbers also have appropriate
 * canonical representations - see corresponding constants.
 * <p>
 * The <code>Decimal</code> format is so designed that canonical representation of
 * Not-a-Number is numerical value of zero. Thus uninitialized fields with default
 * numerical value of zero will be properly treated as Not-a-Numbers.
 *
 * <h3>Extra precision formats and special cases</h3>
 *
 * Zero in the lowest 4 bits indicates a special case, which includes NaN values, infinities, and
 * extra precision formats. The interpretation of the number depends on the value of the
 * lowest 7 bits. The highest 25 bits represent mantissa for extra precision formats.
 * <ul>
 * <li>0x00 - {@link #NaN Not-a-Number} (must have canonical representation).
 * <li>0x10 - {@link #POSITIVE_INFINITY Positive Infinity} (must have canonical representation).
 * <li>0x20 - Extra precision with multiplier of 0.0000001 (1/10^7).
 * <li>0x30 - Extra precision with multiplier of 0.00000001 (1/10^8).
 * <li>0x40 - Reserved.
 * <li>0x50 - Reserved.
 * <li>0x60 - Extra precision with multiplier of 0.0078125 (1/128).
 * <li>0x70 - {@link #NEGATIVE_INFINITY Negative Infinity} (must have canonical representation).
 * </ul>
 */
public class Decimal extends Number implements Comparable {
    private static final long serialVersionUID = 1418802680507197887L;

    // ========== Static API for Operations with External Data ==========

    public static final int NaN = 0;
    public static final int POSITIVE_INFINITY = 0x10;
    public static final int NEGATIVE_INFINITY = 0xFFFFFFF0;

    public static final int ZERO = 0x01;
    public static final int MAX_VALUE = 0x7FFFFFF1; // 13421772700000000
    public static final int MIN_VALUE = 0x80000011; // -13421772700000000

    public static final String NAN_STRING = "NaN";
    public static final String POSITIVE_INFINITY_STRING = "Infinity";
    public static final String NEGATIVE_INFINITY_STRING = "-Infinity";

    private static final char[] ZERO_CHARS = "0.000000000000000000".toCharArray();

    private static final int STD_SHIFT = 4;
    private static final int EXTRA_SHIFT = 7;

    private static final int STD_MASK = 0x0F;
    private static final int EXTRA_MASK = 0x7F;
    private static final int M128_FLAG = 0x60;

    private static final int UNITY_POWER = 9;
    private static final int MAX_STD_POWER = 15;
    private static final int MAX_EXTRA_POWER = 17;

    private static final int MAX_STD_MANTISSA = 0x7FFFFFFF >> STD_SHIFT; // 134217727
    private static final int MAX_EXTRA_MANTISSA = 0x7FFFFFFF >> EXTRA_SHIFT; // 16777215

    private static final double MAX_M128_VALUE = (MAX_EXTRA_MANTISSA + 0.499) / 128.0; // 131071.9921875 + ~1/256
    private static final double ULP_128 = 5e-8; // ulp for error comparison between decimal and 128th representations

    private static final int INF_PRECISION = -19;
    private static final int MIN_PRECISION = 1 - UNITY_POWER;
    private static final int MAX_STD_PRECISION = MAX_STD_POWER - UNITY_POWER;
    private static final int MAX_EXTRA_PRECISION = MAX_EXTRA_POWER - UNITY_POWER;

    private static final long[] POWERS = new long[18 + 1]; // max power that fit into long is 1e18

    private static final double[] MULTIPLIERS = new double[MAX_EXTRA_POWER + 1];
    private static final double[] DIVISORS = new double[MAX_EXTRA_POWER + 1];
    private static final double[] MAX_VALUES = new double[MAX_EXTRA_POWER + 1];

    private static final int[] EXTRA_PRECISION = {
        INF_PRECISION, // for canonical NaN
        INF_PRECISION, // for canonical positive infinity
        7, // 10^7
        8, // 10^8
        INF_PRECISION, // reserved
        INF_PRECISION, // reserved
        7, // corresponds to 10^7 via P7_M128_CONVERTER
        INF_PRECISION, // for canonical negative infinity
    };

    private static final int[] EXTRA_DIVISORS = {
        -1, // for canonical NaN
        -1, // for canonical positive infinity
        10000000,  // 10^7
        100000000, // 10^8
        0, // reserved
        0, // reserved
        128,
        -1, // for canonical negative infinity
    };

    // internally mantissa can be long due to possible encoding of 128th
    private static final long MAX_INTERNAL_MANTISSA = 99999999999999L; // for toString operations
    private static final long P7_M128_CONVERTER = 10000000 / 128; // 78125
    private static final long[] MAX_M128_INTERNAL_MANTISSA = new long[UNITY_POWER + 8];

    private static final int[] SHIFT = new int[EXTRA_MASK + 1];
    private static final int[] PRECISION = new int[EXTRA_MASK + 1];

    static {
        POWERS[0] = 1;
        for (int i = 1; i < POWERS.length; i++)
            POWERS[i] = POWERS[i - 1] * 10;
        MULTIPLIERS[0] = Double.POSITIVE_INFINITY;
        DIVISORS[0] = 0;
        for (int i = 1; i <= UNITY_POWER; i++) {
            MULTIPLIERS[i] = POWERS[UNITY_POWER - i];
            DIVISORS[i] = 1.0 / POWERS[UNITY_POWER - i];
        }
        for (int i = UNITY_POWER + 1; i <= MAX_EXTRA_POWER; i++) {
            MULTIPLIERS[i] = 1.0 / POWERS[i - UNITY_POWER];
            DIVISORS[i] = POWERS[i - UNITY_POWER];
        }
        for (int i = 0; i <= MAX_EXTRA_POWER; i++)
            MAX_VALUES[i] = ((i <= MAX_STD_POWER ? MAX_STD_MANTISSA : MAX_EXTRA_MANTISSA) + 0.499) * MULTIPLIERS[i];
        for (int i = 0; i < SHIFT.length; i++) {
            int power = i & STD_MASK;
            SHIFT[i] = power != 0 || EXTRA_DIVISORS[i >> STD_SHIFT] < 0 ? STD_SHIFT : EXTRA_SHIFT;
            PRECISION[i] = power != 0 ? power - UNITY_POWER : EXTRA_PRECISION[i >> STD_SHIFT];
        }

        long cur = ((1L << (31 - EXTRA_SHIFT)) - 1) * P7_M128_CONVERTER;
        if (cur > MAX_INTERNAL_MANTISSA)
            throw new AssertionError("MAX_INTERNAL_MANTISSA is too small");
        Arrays.fill(MAX_M128_INTERNAL_MANTISSA, -1);
        for (int i = UNITY_POWER + 7; i >= 0 && cur > 0; i--) {
            MAX_M128_INTERNAL_MANTISSA[i] = cur;
            cur /= 10;
        }
    }

    public static boolean isNaN(int decimal) {
        return decimal == NaN;
    }

    public static boolean isInfinite(int decimal) {
        return decimal == POSITIVE_INFINITY || decimal == NEGATIVE_INFINITY;
    }

    public static int sign(int decimal) {
        int mantissa = decimal >> SHIFT[decimal & EXTRA_MASK];
        return mantissa > 0 ? 1 : mantissa < 0 ? -1 : 0;
    }

    public static int neg(int decimal) {
        int negator = 1 << SHIFT[decimal & EXTRA_MASK];
        return (decimal ^ -negator) + negator;
    }

    public static int abs(int decimal) {
        return decimal >= 0 ? decimal : neg(decimal);
    }

    /**
     * Returns <code>double</code> value of the corresponding decimal.
     * The result is the <code>double</code> value closest to the
     * true decimal number.
     */
    public static double toDouble(int decimal) {
        // Note: toDouble can be written like this, but this way it will be slower:
        //  long mantissa = getDecimalMantissa(decimal);
        //  int precision = getDecimalPrecision(decimal);
        //  return precision < 0 ? mantissa * DOUBLE_POWERS[-precision] : mantissa / DOUBLE_POWERS[precision];

        int power = decimal & STD_MASK;
        if (power == 0) {
            // extra precision and special cases
            int divisor = EXTRA_DIVISORS[(decimal >> STD_SHIFT) & 0x07];
            if (divisor >= 0) {
                // mantissa in highest 25 bits for supported extra precision formats
                return (double) (decimal >> EXTRA_SHIFT) / divisor;
            }
        }
        return toDoubleInternal(decimal >> STD_SHIFT, power);
    }

    private static double toDoubleInternal(int mantissa, int power) {
        return power <= UNITY_POWER ? mantissa * MULTIPLIERS[power] : mantissa / DIVISORS[power];
    }

    /**
     * Returns decimal mantissa of the decimal number.
     * @see #composeDecimal(long, int)
     */
    public static long getDecimalMantissa(int decimal) {
        int flag = decimal & EXTRA_MASK;
        if (flag == M128_FLAG)
            return (decimal >> EXTRA_SHIFT) * P7_M128_CONVERTER;
        return decimal >> SHIFT[flag];
    }

    /**
     * Returns decimal precision of the decimal number.
     * @see #composeDecimal(long, int)
     */
    public static int getDecimalPrecision(int decimal) {
        return PRECISION[decimal & EXTRA_MASK];
    }

    /**
     * Returns decimal value that corresponds to <code>mantissa / 10^precision</code>.
     * The result is {@link #POSITIVE_INFINITY positive} or {@link #NEGATIVE_INFINITY negative} infinity if
     * the value is too large by absolute value to represent. The result is {@link #ZERO zero} if value
     * is too small to represent with extra precision format of decimal.
     *
     * <p>Precision equal or smaller than <code>-19</code> is considered to produce infinitely large multiplier,
     * thus <code>composeDecimal(0, -19)</code> returns {@link #NaN}.
     *
     * <p>This method always returns decimal in a standard precision format if it can
     * represent the value exactly. Extra precision is used only if the standard one
     * does not have enough precision.
     *
     * <p>Mantissa and precision of the decimal number can be retrieved with
     * {@link #getDecimalMantissa(int)} and {@link #getDecimalPrecision(int)} methods.
     * Generally, for well-formed decimal number:
     * <pre>composeDecimal(getDecimalMantissa(decimal), getDecimalPrecision(decimal)) == decimal</pre>
     */
    public static int composeDecimal(long mantissa, int precision) {
        // Performance note: this method is heavily used in gates, so we arrange most often use-cases in a fast path.
        return mantissa >= 0 ? composeNonNegativeDecimal(mantissa, precision) :
            composeNegativeDecimal(mantissa, precision); // Slow path: for negative numbers.
    }

    // only works for negative mantissa
    private static int composeNegativeDecimal(long mantissa, int precision) {
        // Performance note: this use-case happens rarely, so it is extracted into separate method to help code inlining.
        return neg(composeNonNegativeDecimal(mantissa == Long.MIN_VALUE ? Long.MAX_VALUE : -mantissa, precision));
    }

    // only works for non-negative mantissa
    private static int composeNonNegativeDecimal(long mantissa, int precision) {
        if (precision < MIN_PRECISION)
            return composeLargeDecimal(mantissa, precision); // Slow path: for extra large numbers.
        // Fast path: for standard decimals (most frequent) use integer-only arithmetics without rounding.
        if (mantissa == 0)
            return ZERO;
        // it is important that (mantissa != 0) to limit number of iterations in the loop below in case of extra large precision
        while ((mantissa & 1) == 0 && mantissa % 10 == 0 && precision > MIN_PRECISION) {
            mantissa /= 10;
            precision--;
        }
        if (mantissa <= MAX_STD_MANTISSA && precision <= MAX_STD_PRECISION)
            return ((int) mantissa << STD_SHIFT) | (precision + UNITY_POWER);
        if (mantissa <= MAX_EXTRA_MANTISSA && precision <= MAX_EXTRA_PRECISION)
            return ((int) mantissa << EXTRA_SHIFT) | ((precision + (-MAX_STD_PRECISION + 1)) << STD_SHIFT);
        // Slow path: for decimals that need rounding.
        return composeRoundDecimal(mantissa, precision);
    }

    // only works for non-negative mantissa and precision < MIN_PRECISION
    private static int composeLargeDecimal(long mantissa, int precision) {
        // Performance note: this use-case happens rarely, so it is extracted into separate method to help code inlining.
        if (precision <= INF_PRECISION)
            return mantissa == 0 ? NaN : POSITIVE_INFINITY;
        if (mantissa > MAX_STD_MANTISSA / 10) // protect from multiplication overflow below
            return POSITIVE_INFINITY;
        mantissa *= POWERS[MIN_PRECISION - precision];
        // expression below will produce ZERO decimal for case (mantissa == 0)
        return mantissa > MAX_STD_MANTISSA ? POSITIVE_INFINITY : ((int) mantissa << STD_SHIFT) | 1;
    }

    // only works for non-negative mantissa and precision >= MIN_PRECISION
    private static int composeRoundDecimal(long mantissa, int precision) {
        // Performance note: this use-case happens rarely, so it is extracted into separate method to help code inlining.
        if (precision > MAX_EXTRA_PRECISION + 1) {
            // scale down extra large precision
            // keep 1 extra digit beyond max extra precision for proper rounding later
            if (precision > MAX_EXTRA_PRECISION + 1 + 18)
                return ZERO;
            mantissa /= POWERS[precision - (MAX_EXTRA_PRECISION + 1)];
            precision = MAX_EXTRA_PRECISION + 1;
            if (mantissa < 5)
                return ZERO;
        }
        // now: MIN_PRECISION <= precision <= MAX_EXTRA_PRECISION + 1
        // scale down extra large mantissa
        // keep 1 extra digit beyond max representable mantissa for proper rounding later
        while (mantissa > MAX_EXTRA_MANTISSA * P7_M128_CONVERTER * 10) {
            mantissa /= 10;
            precision--;
        }
        if (precision < MIN_PRECISION)
            return POSITIVE_INFINITY;
        int targetPrecision = Math.min(precision, MAX_EXTRA_PRECISION);
        long targetScale = POWERS[precision - targetPrecision];
        while (mantissa >= (targetPrecision <= MAX_STD_PRECISION ? MAX_STD_MANTISSA : MAX_EXTRA_MANTISSA) * targetScale + (targetScale >> 1)) {
            targetPrecision--;
            targetScale *= 10;
        }
        if (targetPrecision < MIN_PRECISION)
            return POSITIVE_INFINITY;
        long targetMantissa = (mantissa + (targetScale >> 1)) / targetScale;
        if (targetMantissa == 0)
            return ZERO;
        // max mantissa: 134217727
        // max 128th:    131071.9921875
        // numbers that fit in 128th and have precision <= 3 can be represented more precisely as decimal
        // numbers that have target precision >= 7 can be represented more precisely as decimal
        if (precision > 3 && targetPrecision < 7) {
            long m128 = (mantissa * 128 + (POWERS[precision] >> 1)) / POWERS[precision];
            if (m128 <= MAX_EXTRA_MANTISSA) {
                long error = (targetMantissa * targetScale - mantissa) * 128;
                long e128 = m128 * POWERS[precision] - mantissa * 128;
                if (Math.abs(e128) < Math.abs(error))
                    return ((int) m128 << EXTRA_SHIFT) | M128_FLAG;
            }
        }
        return canonicalizeRegularDecimal((int) targetMantissa, targetPrecision + UNITY_POWER);
    }

    /**
     * Returns decimal value for the corresponding <code>double</code>.
     * The result is {@link #NaN} if the value is
     * {@link Double#isNaN(double) not-a-number}. The result is
     * {@link #POSITIVE_INFINITY positive} or {@link #NEGATIVE_INFINITY negative} infinity if
     * the value is too large by absolute value to represent. The result is {@link #ZERO} if value
     * is too small to represent with extra precision format of decimal.
     *
     * <p>This method always returns decimal in a standard precision format if it can
     * represent the value exactly. Extra precision is used only if the standard one
     * does not have enough precision.
     */
    public static int compose(double value) {
        return value >= 0 ? composeNonNegativeInternal(value) :
            value < 0 ? neg(composeNonNegativeInternal(-value)) :
            NaN;
    }

    // only works for non-negative non-NaN numbers
    private static int composeNonNegativeInternal(double value) {
        int power = MAX_EXTRA_POWER;
        while (value > MAX_VALUES[power])
            power--;
        // note: if (value == POSITIVE_INFINITY) then power == 0, DIVISORS[0] == 0, INFINITY * 0 == NaN, (int)(NaN + 0.5) == 0
        int mantissa = (int) (value * DIVISORS[power] + 0.5);
        if (mantissa == 0)
            return power == 0 ? POSITIVE_INFINITY : ZERO;
        // check if this combination of mantissa & power represents the source number exactly
        double error = toDoubleInternal(mantissa, power) - value;
        if (error != 0 && value < MAX_M128_VALUE) {
            // not exactly -- see if we can better represent the number in 128th
            int m128 = (int) (value * 128 + 0.5);
            if (Math.abs(m128 / 128.0 - value) < Math.abs(error) - ULP_128)
                return (m128 << EXTRA_SHIFT) | M128_FLAG;
        }
        return canonicalizeRegularDecimal(mantissa, power);
    }

    // only works for non-zero non-overflow mantissa and power in [1..MAX_EXTRA_POWER]
    private static int canonicalizeRegularDecimal(int mantissa, int power) {
        // We might need to drop 0-8 trailing zeros from mantissa.
        // Here is binary drop method which works for 0-7 trailing zeros.
        // The possible 8th trailing zero is processed in last nested "if".
        // (Nesting this "if" gives 3.5 checks on average instead of 4.)
        if (mantissa % 10000 == 0 && power > 4) {
            mantissa /= 10000;
            power -= 4;
        }
        if (mantissa % 100 == 0 && power > 2) {
            mantissa /= 100;
            power -= 2;
        }
        if (mantissa % 10 == 0 && power > 1) {
            mantissa /= 10;
            power--;
            if (mantissa % 10 == 0 && power > 1) {
                mantissa /= 10;
                power--;
            }
        }
        if (power > MAX_STD_POWER)
            return (mantissa << EXTRA_SHIFT) | ((power - MAX_STD_POWER + 1) << STD_SHIFT);
        return (mantissa << STD_SHIFT) | power;
    }

    /**
     * Parses string into a packed decimal integer. It supports standard floating point representation and
     * can parse any string that was produced by {@link #toString(int)}.
     * <ul>
     * <li><code>NaN</code>
     * <li><code>Infinity</code>
     * <li><code>-Infinity</code>
     * <li>[+|-][0-9]+
     * <li>[+|-].[0-9]+
     * <li>[+|-][0-9]+.[0-9]+
     * </ul>
     * This method shall support everything that {@link Double#parseDouble} supports with the exception
     * of hexadecimal floating point notation and "f|d" suffixes, but currently there are certain
     * other limitations:<br>
     * <b>todo: support "e" notation, so that any string produced by {@link Double#toString} can be parsed.</b><br>
     *
     * <p><b>Note:</b>Use {@link #parseDecimal(char[],int,int)} if you are parsing buffer of incoming characters
     * and you want to avoid creation of new string objects.
     */
    public static int parseDecimal(String s) throws NumberFormatException {
        // NOTE: This method is cut-and-pasted into parseDecimal(char[], int, int)
        s = s.trim(); // trim whitespace & throw NPE if null
        int n = s.length();
        if (n == 0)
            throw new NumberFormatException("Empty string");
        int sign = 1;
        int i = 0;
        char c = s.charAt(0);
        switch (c) {
        case '-':
            sign = -1;
            // fallthough!
        case '+':
            i++;
            if (i >= n)
                throw new NumberFormatException("No digits in '" + s + "'");
            c = s.charAt(i);
        }
        switch (c) {
        case 'N':
        case 'I':
            // possible NaN or Infinity
            if (NAN_STRING.equals(s))
                return NaN;
            if (POSITIVE_INFINITY_STRING.equals(s))
                return POSITIVE_INFINITY;
            if (NEGATIVE_INFINITY_STRING.equals(s))
                return NEGATIVE_INFINITY;
        }
        long mantissa = 0;
        int precision = 0;
        boolean dotseen = false;
        // Parse characters
        // Invariant: current number is sign * mantissa * MULTIPLIERS[power]
        // Invariant: c == s.charAt(i) in the beginning of the loop
        while (true) {
            switch (c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                long newmantissa = 10 * mantissa + c - '0';
                if (newmantissa > MAX_INTERNAL_MANTISSA)
                    precision--;
                else
                    mantissa = newmantissa;
                if (dotseen)
                    precision++;
                break;
            case '.':
                if (dotseen)
                    throw new NumberFormatException("Second dot in '" + s + "'");
                dotseen = true;
                break;
            default:
                throw new NumberFormatException("Illegal character in '" + s + "'");
            }
            i++;
            if (i >= n)
                break;
            c = s.charAt(i);
        }
        int decimal = composeNonNegativeDecimal(mantissa, precision);
        return sign > 0 ? decimal : neg(decimal);
    }

    /**
     * The same as {@link #parseDecimal(String)} but works directly with array of characters.
     * @param   chars    the character array.
     * @param   offset   the initial offset into the chars.
     * @param   count    the number of chars to parse.
     */
    public static int parseDecimal(char[] chars, int offset, int count) throws NumberFormatException {
        // NOTE: This method is cut-and-pasted into parseDecimal(String).

        // emulate action of 'trim'
        int i = offset;
        int last = offset + count;
        while ((i < last) && (chars[last - 1] <= ' '))
            last--;
        while ((i < last) && (chars[i] <= ' '))
            i++;

        // the rest of code
        if (i >= last)
            throw new NumberFormatException("Empty string");
        int sign = 1;
        char c = chars[i];
        switch (c) {
        case '-':
            sign = -1;
            // fallthough!
        case '+':
            i++;
            if (i >= last)
                throw new NumberFormatException("No digits in " + Arrays.toString(chars) + " in [" + offset + ", " + (offset + count - 1) + "]");
            c = chars[i];
        }
        switch (c) {
        case 'N':
        case 'I':
            // possible NaN or Infinity
            if (equalsString(NAN_STRING, chars, i, last - i))
                return NaN;
            if (equalsString(POSITIVE_INFINITY_STRING, chars, i, last - i))
                return sign > 0 ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
        }
        long mantissa = 0;
        int precision = 0;
        boolean dotseen = false;
        // Parse characters
        // Invariant: current number is sign * mantissa * MULTIPLIERS[power]
        // Invariant: c == s.charAt(i) in the beginning of the loop
        while (true) {
            switch (c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                long newmantissa = 10 * mantissa + c - '0';
                if (newmantissa > MAX_INTERNAL_MANTISSA)
                    precision--;
                else
                    mantissa = newmantissa;
                if (dotseen)
                    precision++;
                break;
            case '.':
                if (dotseen)
                    throw new NumberFormatException("Second dot in " + Arrays.toString(chars) + " in [" + offset + ", " + (offset + count - 1) + "]");
                dotseen = true;
                break;
            default:
                throw new NumberFormatException("Illegal character in " + Arrays.toString(chars) + " in [" + offset + ", " + (offset + count - 1) + "]");
            }
            i++;
            if (i >= last)
                break;
            c = chars[i];
        }
        int decimal = composeNonNegativeDecimal(mantissa, precision);
        return sign > 0 ? decimal : neg(decimal);
    }

    private static boolean equalsString(String s, char[] chars, int offset, int count) {
        int n = s.length();
        if (n != count)
            return false;
        for (int i = 0; i < n; i++)
            if (s.charAt(i) != chars[offset + i])
                return false;
        return true;
    }

    public static String toString(int decimal) {
        long mantissa = getDecimalMantissa(decimal);
        int precision = getDecimalPrecision(decimal);
        if (precision <= INF_PRECISION)
            return mantissa == 0 ? NAN_STRING : mantissa > 0 ? POSITIVE_INFINITY_STRING : NEGATIVE_INFINITY_STRING;
        if (mantissa == 0)
            return "0";
        // normalize
        while (precision > 0 && mantissa % 10 == 0) {
            mantissa /= 10;
            precision--;
        }
        if (precision <= 0) // It's an integer number.
            return Long.toString(mantissa * POWERS[-precision]);
        // It's not an integer number.
        StringBuilder sb = new StringBuilder(15);
        sb.append(mantissa);
        int first_digit = (int) (mantissa >>> 63); // 1 for negative, 0 otherwise
        int dot_position = sb.length() - precision;
        if (dot_position <= first_digit)
            sb.insert(first_digit, ZERO_CHARS, 0, first_digit - dot_position + 2);
        else
            sb.insert(dot_position, '.');
        return sb.toString();
    }

    public static StringBuilder appendTo(StringBuilder sb, int decimal) {
        long mantissa = getDecimalMantissa(decimal);
        int precision = getDecimalPrecision(decimal);
        if (precision <= INF_PRECISION)
            return sb.append(mantissa == 0 ? NAN_STRING : mantissa > 0 ? POSITIVE_INFINITY_STRING : NEGATIVE_INFINITY_STRING);
        if (mantissa == 0)
            return sb.append("0");
        // normalize
        while (precision > 0 && mantissa % 10 == 0) {
            mantissa /= 10;
            precision--;
        }
        if (precision <= 0) // It's an integer number.
            return sb.append(mantissa * POWERS[-precision]);
        // It's not an integer number.
        int first_digit = sb.length() + (int) (mantissa >>> 63); // +1 for negative (sign '-'), 0 otherwise
        sb.append(mantissa);
        int dot_position = sb.length() - precision;
        if (dot_position <= first_digit)
            sb.insert(first_digit, ZERO_CHARS, 0, first_digit - dot_position + 2);
        else
            sb.insert(dot_position, '.');
        return sb;
    }

    private static final long[] COMPARE_POWERS = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000, 1000000000, 1000000000, 1000000000, 1000000000, 1000000000, 1000000000};

    /**
     * Compares two specified decimals for order. Returns a negative integer, zero, or positive integer
     * as the first decimal is less than, equal to, or greater than the second decimal.
     *
     * <p><b>Note:</b> this method treats {@link #NaN} as a largest value (even larger than {@link #POSITIVE_INFINITY}) that is equal to itself.
     */
    public static int compare(int d1, int d2) {
        if (d1 == d2)
            return 0;
        int p1 = d1 & STD_MASK;
        int p2 = d2 & STD_MASK;
        if (p1 > 0 && p2 > 0) {
            // Fast path: for standard decimals (most frequent) use integer-only arithmetics.
            if (p1 == p2)
                return d1 > d2 ? 1 : -1; // note: exact decimal equality is checked above
            int m1 = d1 >> STD_SHIFT;
            int m2 = d2 >> STD_SHIFT;
            long delta = p1 > p2 ? m1 - m2 * COMPARE_POWERS[p1 - p2] : m1 * COMPARE_POWERS[p2 - p1] - m2;
            return delta > 0 ? 1 : delta < 0 ? -1 : 0;
        }
        if (isNaN(d1))
            return 1;
        if (isNaN(d2))
            return -1;
        // Slow path: for non-standard decimals (specials and extra precision) use floating arithmetics.
        double v1 = toDouble(d1);
        double v2 = toDouble(d2);
        if (v1 > v2)
            return 1;
        if (v1 < v2)
            return -1;
        return 0;
    }

    /**
     * Subtracts the second specified decimal from the first specified decimal and returns result as decimal.
     */
    public static int subtract(int d1, int d2) {
        // Performance note: this method is heavily used to compute NetChange, so we check certain cases for fast path.
        int p1 = d1 & STD_MASK;
        int p2 = d2 & STD_MASK;
        if (p1 > 0 && p2 > 0) {
            // Fast path: for standard decimals (most frequent) use integer-only arithmetics.
            if (d1 == d2)
                return ZERO;
            int m1 = d1 >> STD_SHIFT;
            if (m1 == 0)
                return neg(d2);
            int m2 = d2 >> STD_SHIFT;
            if (m2 == 0)
                return d1;
            // (MAX_STD_MANTISSA * 10^10) fits into 'long' and is computed directly, otherwise escape to 'double'
            int p;
            long m;
            if (p1 > p2) {
                p = p1;
                m = p1 - p2 <= 10 ? m1 - m2 * POWERS[p1 - p2] : Long.MAX_VALUE;
            } else if (p1 < p2) {
                p = p2;
                m = p2 - p1 <= 10 ? m1 * POWERS[p2 - p1] - m2 : Long.MAX_VALUE;
            } else {
                p = p1;
                m = m1 - m2;
            }
            if (m == 0)
                return ZERO;
            if (m >= -MAX_STD_MANTISSA && m <= MAX_STD_MANTISSA)
                return canonicalizeRegularDecimal((int) m, p);
            if (m != Long.MAX_VALUE)
                return composeDecimal(m, p - UNITY_POWER);
        }
        if (isNaN(d1) || isNaN(d2))
            return NaN;
        // Slow path: for non-standard decimals (specials and extra precision) use floating arithmetics.
        return compose(toDouble(d1) - toDouble(d2));
    }

    /**
     * Adds two specified decimals and returns result as decimal.
     */
    public static int add(int d1, int d2) {
        int p1 = d1 & STD_MASK;
        int p2 = d2 & STD_MASK;
        if (p1 > 0 && p2 > 0) {
            // Fast path: for standard decimals (most frequent) use integer-only arithmetics.
            int m1 = d1 >> STD_SHIFT;
            if (m1 == 0)
                return d2;
            int m2 = d2 >> STD_SHIFT;
            if (m2 == 0)
                return d1;
            // (MAX_STD_MANTISSA * 10^10) fits into 'long' and is computed directly, otherwise escape to 'double'
            int p;
            long m;
            if (p1 > p2) {
                p = p1;
                m = p1 - p2 <= 10 ? m1 + m2 * POWERS[p1 - p2] : Long.MAX_VALUE;
            } else if (p1 < p2) {
                p = p2;
                m = p2 - p1 <= 10 ? m1 * POWERS[p2 - p1] + m2 : Long.MAX_VALUE;
            } else {
                p = p1;
                m = m1 + m2;
            }
            if (m == 0)
                return ZERO;
            if (m >= -MAX_STD_MANTISSA && m <= MAX_STD_MANTISSA)
                return canonicalizeRegularDecimal((int) m, p);
            if (m != Long.MAX_VALUE)
                return composeDecimal(m, p - UNITY_POWER);
        }
        if (isNaN(d1) || isNaN(d2))
            return NaN;
        // Slow path: for non-standard decimals (specials and extra precision) use floating arithmetics.
        return compose(toDouble(d1) + toDouble(d2));
    }

    /**
     * Averages two specified decimals and returns result as decimal.
     */
    public static int average(int d1, int d2) {
        int p1 = d1 & STD_MASK;
        int p2 = d2 & STD_MASK;
        if (p1 > 0 && p2 > 0) {
            // Fast path: for standard decimals (most frequent) use integer-only arithmetics.
            int m1 = d1 >> STD_SHIFT;
            int m2 = d2 >> STD_SHIFT;
            // (MAX_STD_MANTISSA * 10^10) fits into 'long' and is computed directly, otherwise escape to 'double'
            int p;
            long m;
            if (p1 > p2) {
                p = p1;
                m = p1 - p2 <= 10 ? m1 + m2 * POWERS[p1 - p2] : Long.MAX_VALUE;
            } else if (p1 < p2) {
                p = p2;
                m = p2 - p1 <= 10 ? m1 * POWERS[p2 - p1] + m2 : Long.MAX_VALUE;
            } else {
                p = p1;
                m = m1 + m2;
            }
            if (m == 0)
                return ZERO;
            if (m != Long.MAX_VALUE) {
                // (MAX_STD_MANTISSA * 10^10 * 5) fits into 'long' and is computed directly
                if ((m & 1) != 0) {
                    m *= 5;
                    p++;
                } else
                    m >>= 1;
                if (m >= -MAX_STD_MANTISSA && m <= MAX_STD_MANTISSA && p <= MAX_STD_POWER)
                    return canonicalizeRegularDecimal((int) m, p);
                return composeDecimal(m, p - UNITY_POWER);
            }
        }
        if (isNaN(d1) || isNaN(d2))
            return NaN;
        // Slow path: for non-standard decimals (specials and extra precision) use floating arithmetics.
        return compose((toDouble(d1) + toDouble(d2)) * 0.5);
    }

    public static int wideToTiny(long wide) {
        return Decimal.composeDecimal(WideDecimal.getSignificand(wide), WideDecimal.getScale(wide));
    }

    public static long tinyToWide(int tiny) {
        int scale = Decimal.getDecimalPrecision(tiny);
        if (scale <= -19)
            scale = -128;
        return WideDecimal.composeWide(Decimal.getDecimalMantissa(tiny), scale);
    }

    // ========== Number Extension ==========

    protected final int decimal;

    public Decimal(int decimal) {
        this.decimal = decimal;
    }

    public Decimal(String s) throws NumberFormatException {
        this.decimal = parseDecimal(s);
    }

    public byte byteValue() {
        return (byte) toDouble(decimal);
    }

    public short shortValue() {
        return (short) toDouble(decimal);
    }

    public int intValue() {
        return (int) toDouble(decimal);
    }

    public long longValue() {
        return (long) toDouble(decimal);
    }

    public float floatValue() {
        return (float) toDouble(decimal);
    }

    public double doubleValue() {
        return toDouble(decimal);
    }

    public String toString() {
        return toString(decimal);
    }

    public int hashCode() {
        return decimal;
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof Decimal && decimal == ((Decimal) obj).decimal;
    }

    // ========== Comparable Implementation ==========

    public int compareTo(Object obj) {
        return obj == this ? 0 : compare(decimal, ((Decimal) obj).decimal);
    }
}
