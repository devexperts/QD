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
package com.devexperts.util;

import java.util.Arrays;

/**
 * The <code>WideDecimal</code> class contains a set of methods to work with
 * floating-point numbers packed into <code>long</code> primitive type.
 * Also, it can be used to wrap a single value in an object with interface
 * like that of the {@link Number} class.
 */
public final class WideDecimal extends Number implements Comparable<WideDecimal> {
    private static final long serialVersionUID = 0;

    public static final long NaN = 0;
    public static final long POSITIVE_INFINITY = 0x100;
    public static final long NEGATIVE_INFINITY = 0xFFFFFFFFFFFFFF00L;

    public static final String NAN_STRING = "NaN";
    public static final String POSITIVE_INFINITY_STRING = "Infinity";
    public static final String NEGATIVE_INFINITY_STRING = "-Infinity";

    public static String toString(long wide) {
        long significand = wide >> 8;
        int rank = (int) wide & 0xFF;
        if (rank == 0)
            return NF_STRING[Long.signum(significand) & 3];
        if (significand == 0)
            return ZERO_STRING;
        // if value is an integer number - use faster formatting method
        long integerString = toIntegerString(significand, rank);
        if (integerString != 0)
            return Long.toString(integerString);
        return appendFractionalOrScientific(new StringBuilder(MAX_STRING_LENGTH), significand, rank).toString();
    }

    public static StringBuilder appendTo(StringBuilder sb, long wide) {
        long significand = wide >> 8;
        int rank = (int) wide & 0xFF;
        if (rank == 0)
            return sb.append(NF_STRING[Long.signum(significand) & 3]);
        if (significand == 0)
            return sb.append(ZERO_STRING);
        // if value is an integer number - use faster formatting method
        long integerString = toIntegerString(significand, rank);
        if (integerString != 0)
            return sb.append(integerString);
        return appendFractionalOrScientific(sb, significand, rank);
    }

    private static long toIntegerString(long significand, int rank) {
        assert significand != 0 && rank != 0;
        if (rank > BIAS) {
            // fractional number with possible zero fraction and more trailing zeroes
            if (rank - BIAS <= EXACT_LONG_POWERS) {
                long pow10 = LONG_POWERS[rank - BIAS];
                long result = significand / pow10;
                if (result * pow10 == significand && result % SCIENTIFIC_MODULO != 0)
                    return result;
            }
        } else {
            // integer number with possible trailing zeroes
            if (BIAS - rank <= EXACT_LONG_POWERS && BIAS - rank <= MAX_TRAILING_ZEROES) {
                long pow10 = LONG_POWERS[BIAS - rank];
                long result = significand * pow10;
                if (result / pow10 == significand && result % SCIENTIFIC_MODULO != 0)
                    return result;
            }
        }
        return 0;
    }

    private static StringBuilder appendFractionalOrScientific(StringBuilder sb, long significand, int rank) {
        assert significand != 0 && rank != 0;
        // remove trailing zeroes
        while (significand % 10 == 0) {
            significand /= 10;
            rank--;
        }
        // append significand and note position of first digit (after potential '-' sign)
        int firstDigit = sb.length() + (int) (significand >>> 63);
        sb.append(significand);
        // use plain decimal number notation unless scientific notation is triggered
        if (rank > BIAS) {
            // fractional number
            int dotPosition = sb.length() - (rank - BIAS);
            if (dotPosition > firstDigit)
                return sb.insert(dotPosition, '.');
            if (firstDigit - dotPosition <= MAX_LEADING_ZEROES)
                return sb.insert(firstDigit, ZERO_CHARS, 0, 2 + (firstDigit - dotPosition));
        } else {
            // integer number
            if (BIAS - rank <= MAX_TRAILING_ZEROES)
                return sb.append(ZERO_CHARS, 2, BIAS - rank);
        }
        // use scientific notation
        int digits = sb.length() - firstDigit;
        if (digits != 1)
            sb.insert(firstDigit + 1, '.');
        return sb.append(EXPONENT_CHAR).append(BIAS - rank + digits - 1);
    }

    public static long parseWide(String s) throws NumberFormatException {
        s = s.trim(); // trim whitespace and throw NPE if null
        int n = s.length();
        if (n == 0)
            throw new NumberFormatException("Empty string");
        boolean negative = false;
        // Invariant: c == s.charAt(i)
        int i = 0;
        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            negative = c == '-';
            i++;
            if (i >= n)
                throw new NumberFormatException("No digits in '" + s + "'");
            c = s.charAt(i);
        }
        if (c == 'N' || c == 'I')
            return parseLiteral(s);
        // Invariant: current number is significand / 10^scale
        long significand = 0;
        int scale = 0;
        int dotSeen = 0;
        int precisionLimitReached = 0;
        while (true) {
            if (c >= '0' && c <= '9') {
                if (precisionLimitReached == 0) {
                    long newSignificand = significand * 10 + c - '0';
                    // TODO support MAX_SIGNIFICAND+1 for negative numbers - in all compose and in parse
                    if (newSignificand > MAX_SIGNIFICAND) {
                        precisionLimitReached = 1;
                        // TODO: add proper rounding for MAX_SIGNIFICAND
                        if (c >= '5' && significand < MAX_SIGNIFICAND)
                            significand++;
                    } else {
                        significand = newSignificand;
                    }
                }
                scale += dotSeen - precisionLimitReached;
            } else if (c == '.') {
                if (dotSeen != 0)
                    throw new NumberFormatException("Second dot in '" + s + "'");
                dotSeen = 1;
            } else if (c == 'e' || c == 'E') {
                scale -= parseExponent(s, i + 1, n);
                break;
            } else {
                throw new NumberFormatException("Illegal character in '" + s + "'");
            }
            i++;
            if (i >= n)
                break;
            c = s.charAt(i);
        }
        if (significand == 0)
            return ZERO;
        while (scale <= -BIAS && significand <= MAX_SIGNIFICAND / 10) {
            significand *= 10;
            scale++;
        }
        long wide = composeNonNegative(significand, scale);
        return negative ? neg(wide) : wide;
    }

    private static long parseLiteral(String s) {
        if (NAN_STRING.equals(s))
            return NaN;
        if (POSITIVE_INFINITY_STRING.equals(s))
            return POSITIVE_INFINITY;
        if (NEGATIVE_INFINITY_STRING.equals(s))
            return NEGATIVE_INFINITY;
        throw new NumberFormatException("Illegal literal in '" + s + "'");
    }

    private static int parseExponent(String s, int i, int n) {
        if (i >= n)
            throw new NumberFormatException("Empty exponent in '" + s + "'");
        boolean negative = false;
        // Invariant: c == s.charAt(i)
        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            negative = c == '-';
            i++;
            if (i >= n)
                throw new NumberFormatException("No digits in exponent in '" + s + "'");
            c = s.charAt(i);
        }
        int exponent = 0;
        while (true) {
            if (c >= '0' && c <= '9') {
                exponent = Math.min(exponent * 10 + c - '0', 1000);
            } else {
                throw new NumberFormatException("Illegal character in exponent in '" + s + "'");
            }
            i++;
            if (i >= n)
                break;
            c = s.charAt(i);
        }
        return negative ? -exponent : exponent;
    }

    public static double toDouble(long wide) {
        long significand = wide >> 8;
        int rank = (int) wide & 0xFF;
        // if both significand and rank coefficient can be exactly represented as double values -
        // perform a single double multiply or divide to compute properly rounded result
        if (significand <= MAX_DOUBLE_SIGNIFICAND && -significand <= MAX_DOUBLE_SIGNIFICAND) {
            if (rank > BIAS) {
                if (rank <= BIAS + EXACT_DOUBLE_POWERS)
                    return significand / DIVISORS[rank];
            } else {
                if (rank >= BIAS - EXACT_DOUBLE_POWERS)
                    return significand * MULTIPLIERS[rank];
                if (rank == 0)
                    return NF_DOUBLE[Long.signum(significand) & 3];
            }
        }
        // some component can't be exactly represented as double value -
        // extended precision computations with proper rounding are required;
        // avoid them with somewhat inefficient but guaranteed to work approach
        return Double.parseDouble(toString(wide));
    }

    public static long composeWide(double value) {
        return value >= 0 ? composeNonNegative(value) :
            value < 0 ? neg(composeNonNegative(-value)) :
            NaN;
    }

    private static long composeNonNegative(double value) {
        assert value >= 0;
        int rank = findRank(value);
        if (rank <= 0)
            return POSITIVE_INFINITY;
        long significand = (long) (value * DIVISORS[rank] + 0.5);
        return composeFittingNonNegative(significand, rank);
    }

    private static int findRank(double value) {
        int rank = value > MAX_DOUBLES[128] ? 0 : 128;
        rank += value > MAX_DOUBLES[rank + 64] ? 0 : 64;
        rank += value > MAX_DOUBLES[rank + 32] ? 0 : 32;
        rank += value > MAX_DOUBLES[rank + 16] ? 0 : 16;
        rank += value > MAX_DOUBLES[rank + 8] ? 0 : 8;
        rank += value > MAX_DOUBLES[rank + 4] ? 0 : 4;
        rank += value > MAX_DOUBLES[rank + 2] ? 0 : 2;
        rank += value > MAX_DOUBLES[rank + 1] ? 0 : 1;
        return rank;
    }

    public static long composeWide(long significand, int scale) {
        // the (-significand - (-significand >>> 63)) expression protects from overflow -
        // it converts Long.MIN_VALUE into Long.MAX_VALUE instead of Long.MIN_VALUE
        return significand >= 0 ? composeNonNegative(significand, scale) :
            neg(composeNonNegative(-significand - (-significand >>> 63), scale));
    }

    private static long composeNonNegative(long significand, int scale) {
        assert significand >= 0;
        // check exceedingly large scales to avoid rank overflows
        if (scale > 255 + EXACT_LONG_POWERS - BIAS)
            return ZERO;
        int rank = scale + BIAS;
        // check for special rank to provide symmetrical conversion of special values
        if (rank <= 0)
            return significand == 0 ? NaN : POSITIVE_INFINITY;
        // TODO support MAX_SIGNIFICAND+1 for negative numbers - in all compose and in parse
        if (significand <= MAX_SIGNIFICAND && rank <= 255)
            return composeFittingNonNegative(significand, rank);
        return composeNonFittingNonNegative(significand, rank);
    }

    private static long composeNonFittingNonNegative(long significand, int rank) {
        assert significand >= 0 && 0 < rank && rank <= 255 + EXACT_LONG_POWERS;
        // reduce significand to fit both significand and rank into supported ranges
        int reduction;
        if (significand <= MAX_SIGNIFICAND)
            reduction = 0;
        else if (significand < MAX_SIGNIFICAND * 10 + 5)
            reduction = 1;
        else if (significand < MAX_SIGNIFICAND * 100 + 50)
            reduction = 2;
        else
            reduction = 3;
        if (rank <= reduction)
            return POSITIVE_INFINITY;
        reduction = Math.max(reduction, rank - 255);
        assert reduction > 0;
        // divide by half of target divisor and then divide by 2 with proper rounding
        // this way we avoid overflows when rounding near Long.MAX_VALUE
        // it works properly because divisor is even and at least 10, and integer division truncates remainder
        significand = (significand / (LONG_POWERS[reduction] >> 1) + 1) >> 1;
        rank -= reduction;
        return composeFittingNonNegative(significand, rank);
    }

    private static long composeFittingNonNegative(long significand, int rank) {
        assert 0 <= significand && significand <= MAX_SIGNIFICAND && 0 < rank && rank <= 255;
        // check for ZERO to avoid looping and getting de-normalized ZERO
        if (significand == 0)
            return ZERO;
        while (rank > 1 && significand % 10 == 0) {
            significand /= 10;
            rank--;
        }
        return (significand << 8) | rank;
    }

    public static long getSignificand(long wide) {
        return wide >> 8;
    }

    public static int getScale(long wide) {
        return ((int) wide & 0xFF) - BIAS;
    }

    public static boolean isNaN(long wide) {
        return wide == 0;
    }

    public static boolean isInfinite(long wide) {
        return (wide & 0xFF) == 0 && wide != 0;
    }

    public static boolean isFinite(long wide) {
        return (wide & 0xFF) != 0;
    }

    public static int signum(long wide) {
        return Long.signum(wide >> 8);
    }

    public static long neg(long wide) {
        return (wide ^ -0x100) + 0x100;
    }

    public static long abs(int wide) {
        return wide >= 0 ? wide : neg(wide);
    }

    public static long sum(long w1, long w2) {
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (r1 == r2)
                return composeWide(s1 + s2, r1 - BIAS);
            if (r1 > r2) {
                if (r1 - r2 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r1 - r2];
                    long scaled = s2 * pow10;
                    if (scaled / pow10 == s2)
                        return composeWide(s1 + scaled, r1 - BIAS);
                }
            } else {
                if (r2 - r1 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r2 - r1];
                    long scaled = s1 * pow10;
                    if (scaled / pow10 == s1)
                        return composeWide(scaled + s2, r2 - BIAS);
                }
            }
        }
        if (isNaN(w1) || isNaN(w2))
            return NaN;
        // slow path: for non-standard decimals (specials and extra precision) use floating arithmetic
        return composeWide(toDouble(w1) + toDouble(w2));
    }

    public static long max(long w1, long w2) {
        return compare(w1, w2) >= 0 ? w1 : w2;
    }

    public static long min(long w1, long w2) {
        return compare(w1, w2) <= 0 ? w1 : w2;
    }

    public static int compare(long w1, long w2) {
        if (w1 == w2)
            return 0;
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            if (r1 == r2)
                return w1 > w2 ? 1 : -1; // note: exact decimal equality is checked above
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (r1 > r2) {
                if (r1 - r2 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r1 - r2];
                    long scaled = s2 * pow10;
                    if (scaled / pow10 == s2)
                        return Long.compare(s1, scaled);
                }
            } else {
                if (r2 - r1 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r2 - r1];
                    long scaled = s1 * pow10;
                    if (scaled / pow10 == s1)
                        return Long.compare(scaled, s2);
                }
            }
        }
        if (isNaN(w1))
            return 1;
        if (isNaN(w2))
            return -1;
        if ((w1 ^ w2) < 0)
            return w1 > w2 ? 1 : -1; // different signs and both not NaN
        // slow path: for non-standard decimals (specials and extra precision) use floating arithmetic
        return Double.compare(toDouble(w1), toDouble(w2));
    }

    // ========== Number Extension ==========

    private final long wide;

    public WideDecimal(long wide) {
        this.wide = wide;
    }

    public WideDecimal(String s) throws NumberFormatException {
        this.wide = parseWide(s);
    }

    public boolean isNaN() {
        return isNaN(wide);
    }

    public boolean isInfinite() {
        return isInfinite(wide);
    }

    public byte byteValue() {
        return (byte) toDouble(wide);
    }

    public short shortValue() {
        return (short) toDouble(wide);
    }

    public int intValue() {
        return (int) toDouble(wide);
    }

    public long longValue() {
        return (long) toDouble(wide);
    }

    public float floatValue() {
        return (float) toDouble(wide);
    }

    public double doubleValue() {
        return toDouble(wide);
    }

    public String toString() {
        return toString(wide);
    }

    public int hashCode() {
        return Long.hashCode(wide);
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof WideDecimal && wide == ((WideDecimal) obj).wide;
    }

    // ========== Comparable Implementation ==========

    public int compareTo(WideDecimal other) {
        return compare(wide, other.wide);
    }

    // ========== Implementation Details ==========

    private static final long MAX_DOUBLE_SIGNIFICAND = (1L << 53) - 1;
    // TODO: check what MAX_DOUBLE multiplier shall be actually used
    private static final long MAX_DOUBLE_VALUE = 1L << 51;

    private static final long MAX_SIGNIFICAND = Long.MAX_VALUE >> 8;
    private static final int BIAS = 128;

    private static final long ZERO = BIAS; // canonical zero for scale==0

    private static final String ZERO_STRING = "0";
    private static final char EXPONENT_CHAR = 'E';
    private static final int MAX_LEADING_ZEROES = 6; // valid range: [0, 127]
    private static final int MAX_TRAILING_ZEROES = 6; // valid range: [0, 143]
    private static final long SCIENTIFIC_MODULO; // LONG_POWERS[MAX_TRAILING_ZEROES + 1] or Long.MAX_VALUE
    private static final int MAX_STRING_LENGTH = 18 + Math.max(6, Math.max(2 + MAX_LEADING_ZEROES, MAX_TRAILING_ZEROES));
    private static final char[] ZERO_CHARS = new char[130]; // aka "0.000000(0)"

    private static final int EXACT_LONG_POWERS = 18; // max power of 10 that fit into long exactly
    private static final int EXACT_DOUBLE_POWERS = 22; // max power of 10 that fit into double exactly
    private static final long[] LONG_POWERS = new long[EXACT_LONG_POWERS + 1];

    // MULTIPLIERS and DIVISORS are indexed by [rank] to compliment toDouble computation
    // [rank]        [0]        [1]     ...  [127]   [128]   [129]  ...  [255]
    // MULTIPLIERS   infinity   1e127        1e1     1e0     1e-1        1e-127
    // DIVISORS      0          1e-127       1e-1    1e0     1e1         1e127
    private static final double[] MULTIPLIERS = new double[256];
    private static final double[] DIVISORS = new double[256];

    private static final double[] MAX_DOUBLES = new double[256];

    static {
        LONG_POWERS[0] = 1;
        for (int i = 1; i < LONG_POWERS.length; i++)
            LONG_POWERS[i] = LONG_POWERS[i - 1] * 10;
        //noinspection ConstantConditions
        SCIENTIFIC_MODULO = MAX_TRAILING_ZEROES + 1 <= EXACT_LONG_POWERS ? LONG_POWERS[MAX_TRAILING_ZEROES + 1] : Long.MAX_VALUE;

        MULTIPLIERS[0] = Double.POSITIVE_INFINITY;
        for (int rank = 1; rank < MULTIPLIERS.length; rank++)
            MULTIPLIERS[rank] = Double.parseDouble("1E" + (BIAS - rank));

        DIVISORS[0] = 0;
        for (int rank = 1; rank < DIVISORS.length; rank++)
            DIVISORS[rank] = Double.parseDouble("1E" + (rank - BIAS));

        for (int rank = 0; rank < MAX_DOUBLES.length; rank++)
            MAX_DOUBLES[rank] = MAX_DOUBLE_VALUE * MULTIPLIERS[rank];

        Arrays.fill(ZERO_CHARS, '0');
        ZERO_CHARS[1] = '.';
    }

    private static final double[] NF_DOUBLE = {Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY};
    private static final String[] NF_STRING = {NAN_STRING, POSITIVE_INFINITY_STRING, NAN_STRING, NEGATIVE_INFINITY_STRING};


    // TODO: reorganize into unit-test
    public static void main(String[] args) {
        for (int rank = 0; rank <= 255; rank++) {
            int r0 = findRank(MAX_DOUBLES[rank] * 0.99);
            int r1 = findRank(MAX_DOUBLES[rank]);
            int r2 = findRank(MAX_DOUBLES[rank] * 1.01);
            System.out.println((r0 == rank && r1 == rank && r2 == rank - 1) + "  " + rank + "  " + r0 + "  " + r1 + "  " + r2 + "  " + MAX_DOUBLES[rank]);
        }

        a("NaN", 0, 128);
        a("Infinity", 1, 128);
        a("Infinity", 123456, 128);
        a("-Infinity", -1, 128);
        a("-Infinity", -123456, 128);
        a("0", 0, 0);
        a("0", 0, -10);
        a("0", 0, 10);
        a("1", 1, 0);
        a("1", 1000, -3);
        a("1000000", 1000, 3);
        a("1000000", 1000000, 0);
        a("1000000", 1000000000, -3);
        a("1E7", 10000, 3);
        a("1E7", 10000000, 0);
        a("1E7", 10000000000L, -3);
        a("123.456", 123456, -3);
        a("0.123456", 123456, -6);
        a("0.000123456", 123456, -9);
        a("0.000000123456", 123456, -12);
        a("1.23456E-8", 123456, -13);
        a("1.23456E-9", 123456, -14);
        a("1.23456E-10", 123456, -15);


        for (int i = 0; i < 1000_000; i++) {
            int pa = (int) (Math.random() * 12);
            int pb = (int) (Math.random() * 12);
            double a = Math.floor(Math.random() * LONG_POWERS[pa]) / LONG_POWERS[pa];
            double b = Math.floor(Math.random() * LONG_POWERS[pb]) / LONG_POWERS[pb];
            long wa = composeWide(a);
            long wb = composeWide(b);
            if (a != toDouble(wa))
                System.out.println(a + " -> " + toDouble(wa));
            if (b != toDouble(wb))
                System.out.println(b + " -> " + toDouble(wb));
            if (compare(wa, wb) != Double.compare(a, b))
                System.out.println(a + ", " + b + " -> " + compare(wa, wb));
            if (Math.abs(toDouble(sum(wa, wb)) - (a + b)) > 1e-14)
                System.out.println(a + " + " + b + " = " + toString(sum(wa, wb)));
        }
    }

    private static void a(String expected, long significand, int exponent) {
        double expectedDouble = Double.parseDouble(expected);
        long rawWide = (significand << 8) | ((BIAS - exponent) & 0xFF);
        String rawString = toString(rawWide);
        double rawDouble = toDouble(rawWide);
        String rawDoubleString = Double.toString(rawDouble);
        long theWide = composeWide(significand, -exponent);
        String theString = toString(theWide);
        double theDouble = toDouble(theWide);
        String theDoubleString = Double.toString(theDouble);
        System.out.println("" +
            (rawDouble == expectedDouble ? "1" : "0") +
            (rawString.equals(expected) ? "1" : "0") +
            (rawString.equals(rawDoubleString) ? "1" : "0") +
            (theDouble == expectedDouble ? "1" : "0") +
            (theString.equals(expected) ? "1" : "0") +
            (theString.equals(theDoubleString) ? "1" : "0") +
            "   " + expected +
            "     " + rawString + "     " + rawDoubleString +
            "     " + theString + "     " + theDoubleString +
            "     " + significand + EXPONENT_CHAR + exponent);
    }
}
