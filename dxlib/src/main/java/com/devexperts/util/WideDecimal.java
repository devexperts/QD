/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
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
    public static final long NEGATIVE_INFINITY = -0x100;
    public static final long ZERO; // canonical zero with zero scale

    public static final String NAN_STRING = "NaN";
    public static final String POSITIVE_INFINITY_STRING = "Infinity";
    public static final String NEGATIVE_INFINITY_STRING = "-Infinity";
    public static final String ZERO_STRING = "0";

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
        } else if (rank == BIAS) {
            return (significand % SCIENTIFIC_MODULO == 0) ? 0 : significand;
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
        return parseToScale(s, BIAS);
    }

    public static long parseToScale(String s, int targetScale) throws NumberFormatException {
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
        while (true) {
            if (c >= '0' && c <= '9') {
                if (significand <= Long.MAX_VALUE / 10 - 1) {
                    significand = significand * 10 + c - '0';
                    scale += dotSeen;
                } else {
                    scale += dotSeen - 1;
                }
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
            return targetRank(targetScale);
        while (scale <= -BIAS && significand <= Long.MAX_VALUE / 10) {
            significand *= 10;
            scale++;
        }
        return composeToScale(negative ? -significand : significand, scale, targetScale);
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
            } else if (rank == BIAS) {
                return significand;
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

    public static long toLong(long wide) {
        long significand = wide >> 8;
        int rank = (int) wide & 0xFF;
        // if rank coefficient can be exactly represented as long value -
        // use integer-only arithmetic with proper rounding
        if (rank > BIAS) {
            if (rank <= BIAS + EXACT_LONG_POWERS)
                return div10(significand, LONG_DIVISORS[rank]);
            // divisor is too big - therefore the result is too small
            return 0;
        } else if (rank == BIAS) {
            return significand;
        } else {
            if (rank >= BIAS - EXACT_LONG_POWERS) {
                long pow10 = LONG_MULTIPLIERS[rank];
                long result = significand * pow10;
                if (result / pow10 == significand)
                    return result;
            }
            // non-finite number, multiplier is too big, or result overflows 64 bit precision -
            // therefore the result is 0 or Infinity, the below code nicely catches all these cases
            return NF_LONG[Long.signum(significand) & 3];
        }
    }

    public static long composeWide(double value) {
        if (value == (long) value)
            return composeWide((long) value);
        return value >= 0 ? composeNonNegative(value, BIAS) :
            value < 0 ? neg(composeNonNegative(-value, BIAS)) :
            NaN;
    }

    public static long composeToScale(double value, int targetScale) {
        int targetRank = targetRank(targetScale);
        return value >= 0 ? composeNonNegative(value, targetRank) :
            value < 0 ? neg(composeNonNegative(-value, targetRank)) :
            NaN;
    }

    private static long composeNonNegative(double value, int targetRank) {
        assert value >= 0;
        int rank = findRank(value);
        if (rank <= 0)
            return POSITIVE_INFINITY;
        long significand = (long) (value * DIVISORS[rank] + 0.5);
        return composeFitting(significand, rank, targetRank);
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

    public static long composeWide(long value) {
        return MIN_SIGNIFICAND <= value && value <= MAX_SIGNIFICAND ?
            (value << 8) | BIAS :
            composeNonFitting(value, BIAS, BIAS);
    }

    public static long composeToScale(long value, int targetScale) {
        int targetRank = targetRank(targetScale);
        return MIN_SIGNIFICAND <= value && value <= MAX_SIGNIFICAND ?
            composeFitting(value, BIAS, targetRank) :
            composeNonFitting(value, BIAS, targetRank);
    }

    public static long composeWide(long significand, int scale) {
        // check exceedingly large scales to avoid rank overflows
        if (scale > 255 + EXACT_LONG_POWERS - BIAS)
            return BIAS;
        return composeNonFitting(significand, scale + BIAS, BIAS);
    }

    public static long composeToScale(long significand, int scale, int targetScale) {
        int targetRank = targetRank(targetScale);
        // check exceedingly large scales to avoid rank overflows
        if (scale > 255 + EXACT_LONG_POWERS - BIAS)
            return targetRank;
        return composeNonFitting(significand, scale + BIAS, targetRank);
    }

    private static long composeNonFitting(long significand, int rank, int targetRank) {
        assert 1 <= targetRank && targetRank <= 255;
        // check for special rank to provide symmetrical conversion of special values
        if (rank <= 0)
            return NF_WIDE[Long.signum(significand) & 3];
        if (rank > 255 + EXACT_LONG_POWERS)
            return targetRank;
        // reduce significand to fit both significand and rank into supported ranges
        int reduction;
        if (MIN_SIGNIFICAND <= significand && significand <= MAX_SIGNIFICAND)
            reduction = 0;
        else if (MIN_SIGNIFICAND * 10 - 5 <= significand && significand < MAX_SIGNIFICAND * 10 + 5)
            reduction = 1;
        else if (MIN_SIGNIFICAND * 100 - 50 <= significand && significand < MAX_SIGNIFICAND * 100 + 50)
            reduction = 2;
        else
            reduction = 3;
        if (rank <= reduction)
            return NF_WIDE[Long.signum(significand) & 3];
        reduction = Math.max(reduction, rank - 255);
        if (reduction > 0) {
            significand = div10(significand, LONG_POWERS[reduction]);
            rank -= reduction;
        }
        return composeFitting(significand, rank, targetRank);
    }

    private static long composeFitting(long significand, int rank, int targetRank) {
        assert MIN_SIGNIFICAND <= significand && significand <= MAX_SIGNIFICAND &&
            1 <= rank && rank <= 255 && 1 <= targetRank && targetRank <= 255;
        // check for ZERO to avoid looping and getting de-normalized ZERO
        if (significand == 0)
            return targetRank;
        while (rank > targetRank && significand % 10 == 0) {
            significand /= 10;
            rank--;
        }
        while (rank < targetRank && MIN_SIGNIFICAND / 10 <= significand && significand <= MAX_SIGNIFICAND / 10) {
            significand *= 10;
            rank++;
        }
        if (rank == BIAS + 1 && targetRank == BIAS && MIN_SIGNIFICAND / 10 <= significand && significand <= MAX_SIGNIFICAND / 10) {
            significand *= 10;
            rank++;
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

    public static boolean isDefined(long wide) {
        return wide != 0;
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

    public static long abs(long wide) {
        return wide >= 0 ? wide : neg(wide);
    }

    public static long round(long wide) {
        return round(wide, 0);
    }

    public static long round(long wide, int scale) {
        int rank = (int) wide & 0xFF;
        if (rank == 0 || rank - BIAS <= scale)
            return wide;
        if (rank - BIAS - EXACT_LONG_POWERS > scale)
            return rank;
        // the amount of rounding is in [1, EXACT_LONG_POWERS]
        long pow10 = LONG_POWERS[rank - BIAS - scale];
        long significand = wide >> 8;
        significand = div10(significand, pow10) * pow10;
        return (significand << 8) | rank;
    }

    public static long toScale(long wide, int targetScale) {
        int rank = (int) wide & 0xFF;
        if (rank == 0)
            return wide;
        long significand = wide >> 8;
        int targetRank = targetRank(targetScale);
        return composeFitting(significand, rank, targetRank);
    }

    public static long zeroToScale(int targetScale) {
        return targetRank(targetScale);
    }

    public static long selectDefined(long w1, long w2) {
        return isNaN(w1) ? w2 : w1;
    }

    public static long sum(long w1, long w2) {
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (s1 == 0)
                return w2;
            if (s2 == 0)
                return w1;
            if (r1 == r2)
                return composeNonFitting(s1 + s2, r1, r1);
            if (r1 > r2) {
                if (r1 - r2 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r1 - r2];
                    long scaled = s2 * pow10;
                    long result = s1 + scaled;
                    if (scaled / pow10 == s2 && ((s1 ^ result) & (scaled ^ result)) >= 0)
                        return composeNonFitting(result, r1, r1);
                }
            } else {
                if (r2 - r1 <= EXACT_LONG_POWERS) {
                    long pow10 = LONG_POWERS[r2 - r1];
                    long scaled = s1 * pow10;
                    long result = scaled + s2;
                    if (scaled / pow10 == s1 && ((scaled ^ result) & (s2 ^ result)) >= 0)
                        return composeNonFitting(result, r2, r2);
                }
            }
        }
        if (isNaN(w1) || isNaN(w2))
            return NaN;
        // slow path: for non-standard decimals (specials and extra precision) use floating arithmetic
        return composeWide(toDouble(w1) + toDouble(w2));
    }

    public static long sumDefined(long w1, long w2) {
        return isNaN(w1) ? w2 : isNaN(w2) ? w1 : sum(w1, w2);
    }

    public static long subtract(long w1, long w2) {
        return sum(w1, neg(w2));
    }

    public static long subtractDefined(long w1, long w2) {
        return sumDefined(w1, neg(w2));
    }

    public static long average(long w1, long w2) {
        long wide = sum(w1, w2);
        long significand = wide >> 8;
        int rank = (int) wide & 0xFF;
        if (significand == 0 || rank == 0)
            return wide;
        return composeNonFitting(significand * 5, rank + 1, rank);
    }

    public static long averageDefined(long w1, long w2) {
        return isNaN(w1) ? w2 : isNaN(w2) ? w1 : average(w1, w2);
    }

    public static long multiply(long w1, long w2) {
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            int targetRank = Math.max(r1, r2);
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (s1 == 0 || s2 == 0)
                return targetRank;
            int rank = r1 + r2 - BIAS;
            while (s1 % 10 == 0) {
                s1 /= 10;
                rank--;
            }
            while (s2 % 10 == 0) {
                s2 /= 10;
                rank--;
            }
            long result = s1 * s2;
            if (result / s2 == s1) {
                while (rank < 1 && Long.MIN_VALUE / 10 <= result && result <= Long.MAX_VALUE / 10) {
                    result *= 10;
                    rank++;
                }
                return composeNonFitting(result, rank, targetRank);
            }
        }
        if (isNaN(w1) || isNaN(w2))
            return NaN;
        // slow path: for non-standard decimals (specials and extra precision) use floating arithmetic
        return composeWide(toDouble(w1) * toDouble(w2));
    }

    public static long divide(long w1, long w2) {
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (s2 == 0)
                return NF_WIDE[Long.signum(s1) & 3];
            if (s1 == 0)
                return r1;
            int rank = r1 - r2 + BIAS;
            while (Long.MIN_VALUE / 10 <= s1 && s1 <= Long.MAX_VALUE / 10) {
                s1 *= 10;
                rank++;
            }
            while (s2 % 10 == 0) {
                s2 /= 10;
                rank++;
            }
            long result = s1 / s2;
            if (result * s2 == s1) {
                while (rank < 1 && Long.MIN_VALUE / 10 <= result && result <= Long.MAX_VALUE / 10) {
                    result *= 10;
                    rank++;
                }
                return composeNonFitting(result, rank, r1);
            }
        }
        if (isNaN(w1) || isNaN(w2))
            return NaN;
        // slow path: for non-standard decimals (specials and extra precision) use floating arithmetic
        return composeWide(toDouble(w1) / toDouble(w2));
    }

    public static long max(long w1, long w2) {
        return compare(w1, w2) >= 0 ? w1 : w2;
    }

    public static long maxDefined(long w1, long w2) {
        return isNaN(w1) ? w2 : isNaN(w2) ? w1 : max(w1, w2);
    }

    public static long min(long w1, long w2) {
        return compare(w1, w2) <= 0 ? w1 : w2;
    }

    public static long minDefined(long w1, long w2) {
        return isNaN(w1) ? w2 : isNaN(w2) ? w1 : min(w1, w2);
    }

    public static int compare(long w1, long w2) {
        if (w1 == w2)
            return 0;
        if ((w1 ^ w2) < 0)
            return w1 > w2 ? 1 : -1; // different signs, non-negative (including NaN) is greater
        int r1 = (int) w1 & 0xFF;
        int r2 = (int) w2 & 0xFF;
        if (r1 > 0 && r2 > 0) {
            // fast path: for standard decimals (most frequent) use integer-only arithmetic
            if (r1 == r2)
                return w1 > w2 ? 1 : -1; // note: exact decimal equality is checked above
            long s1 = w1 >> 8;
            long s2 = w2 >> 8;
            if (s1 == 0)
                return Long.signum(-s2);
            if (s2 == 0)
                return Long.signum(s1);
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
        return (byte) toLong(wide);
    }

    public short shortValue() {
        return (short) toLong(wide);
    }

    public int intValue() {
        return (int) toLong(wide);
    }

    public long longValue() {
        return toLong(wide);
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
    private static final long MIN_SIGNIFICAND = Long.MIN_VALUE >> 8;
    private static final int BIAS = 128;

    private static final char EXPONENT_CHAR = 'E';
    private static final int MAX_LEADING_ZEROES = 6; // valid range: [0, 127]
    private static final int MAX_TRAILING_ZEROES = 6; // valid range: [0, 143]
    private static final int MAX_STRING_LENGTH = 18 + Math.max(6, Math.max(2 + MAX_LEADING_ZEROES, MAX_TRAILING_ZEROES));
    private static final long SCIENTIFIC_MODULO; // LONG_POWERS[MAX_TRAILING_ZEROES + 1] or Long.MAX_VALUE
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

    // LONG_MULTIPLIERS and LONG_DIVISORS are indexed by [rank] to compliment toLong computation
    // They mimic MULTIPLIERS and DIVISORS but are defined only for EXACT_LONG_POWERS
    private static final long[] LONG_MULTIPLIERS = new long[256];
    private static final long[] LONG_DIVISORS = new long[256];

    private static final double[] MAX_DOUBLES = new double[256];

    private static final String[] NF_STRING = {NAN_STRING, POSITIVE_INFINITY_STRING, NAN_STRING, NEGATIVE_INFINITY_STRING};
    private static final double[] NF_DOUBLE = {Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY};
    private static final long[] NF_LONG = {0, Long.MAX_VALUE, 0, Long.MIN_VALUE};
    private static final long[] NF_WIDE = {NaN, POSITIVE_INFINITY, NaN, NEGATIVE_INFINITY};

    static {
        ZERO = BIAS;

        long pow10 = 1;
        for (int i = 0; i <= EXACT_LONG_POWERS; i++) {
            LONG_POWERS[i] = pow10;
            LONG_MULTIPLIERS[BIAS - i] = pow10;
            LONG_DIVISORS[BIAS + i] = pow10;
            pow10 *= 10;
        }

        //noinspection ConstantConditions
        SCIENTIFIC_MODULO = MAX_TRAILING_ZEROES + 1 <= EXACT_LONG_POWERS ? LONG_POWERS[MAX_TRAILING_ZEROES + 1] : Long.MAX_VALUE;
        Arrays.fill(ZERO_CHARS, '0');
        ZERO_CHARS[1] = '.';

        MULTIPLIERS[0] = Double.POSITIVE_INFINITY;
        for (int rank = 1; rank < MULTIPLIERS.length; rank++)
            MULTIPLIERS[rank] = Double.parseDouble("1E" + (BIAS - rank));

        DIVISORS[0] = 0;
        for (int rank = 1; rank < DIVISORS.length; rank++)
            DIVISORS[rank] = Double.parseDouble("1E" + (rank - BIAS));

        for (int rank = 0; rank < MAX_DOUBLES.length; rank++)
            MAX_DOUBLES[rank] = MAX_DOUBLE_VALUE * MULTIPLIERS[rank];
    }

    private static long div10(long significand, long pow10) {
        // divides significand by a power of 10 (10+) with proper rounding
        // divide by half of target divisor and then divide by 2 with proper rounding
        // this way we avoid overflows when rounding near Long.MAX_VALUE or Long.MIN_VALUE
        // it works properly because divisor is even (at least 10) and integer division truncates remainder toward 0
        // special handling of negative significand is needed to perform floor division (see MathUtil)
        return significand >= 0 ?
            (significand / (pow10 >> 1) + 1) >> 1 :
            ((significand + 1) / (pow10 >> 1)) >> 1;
    }

    private static int targetRank(int targetScale) {
        return BIAS + Math.min(Math.max(targetScale, -127), 127);
    }
}
