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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Class for fast double processing such as parse from string presentation to double,
 * format double value to string with defined precision and rounding double values
 * <p>
 * Parser - Works with correct rounding as a jdk in range between 1E-324 to 1E+308
 * Used some ideas of <a href="https://github.com/fastfloat/fast_float">Daniel Lemire's algorithm</a>
 * <p>
 * Formatter - Base on ideas of <a href="https://github.com/ulfjack/ryu">Ryu</a> and additionally
 * can set the format accuracy.
 * <p>
 * Round - Works same as {@linkplain java.math.BigDecimal} round with modes {@linkplain RoundingMode}.
 * For modes (HALF_UP, HALF_DOWN, HALF_EVEN) - match the behavior with {@linkplain java.math.BigDecimal} round.
 * For modes (UP, DOWN, CEILING, FLOOR) and value more than ±1E17 in rare case possible to have different
 *  rounding than {@linkplain java.math.BigDecimal} for unrepresented double values.
 */
class FastDoubleUtil {

    private FastDoubleUtil() {}

    private static final int MAX_POWER = 308;
    private static final int MIN_POWER = -325;

    private static final int MANTISSA_BITS = 52;
    private static final int EXPONENT_BITS = 11;
    private static final int EXPONENT_BIAS = 1023;

    private static final long POW52 = 1L << MANTISSA_BITS;                                  // 0x10000000000000L
    private static final long POW53 = 1L << (MANTISSA_BITS + 1);                            // 0x20000000000000L
    private static final long MANTISSA_MASK = POW52 - 1;                                    // 0xFFFFFFFFFFFFFL
    private static final long MAX_MANTISSA = POW53 - 1;                                     // 0x1FFFFFFFFFFFFFL
    private static final long EXPONENT_MASK = ((1L << EXPONENT_BITS) - 1) << MANTISSA_BITS; // 0x7FF0000000000000L

    private static final int POS_TABLE_SIZE = 326;
    private static final int NEG_TABLE_SIZE = 342;
    private static final int POW5_BIT_COUNT = 125;
    private static final int POW5_INV_BIT_COUNT = 125;

    private static final int MAX_SIGNIFICANT_DIGITS = 18;
    private static final int ROUND_SIGNIFICANT_DIGITS = MAX_SIGNIFICANT_DIGITS - 2; // 16
    private static final int MAX_STRICT_EXPONENT = 22;

    private static final int MAX_CHARS = 26; // max chars in string double/long presentation
    private static final char[] ARRAY_INFINITY = "Infinity".toCharArray();
    private static final char[] ARRAY_NAN = "NaN".toCharArray();

    // The mantissas of powers of ten from -308 to 308, extended out to 64 bits.
    // The array contains the powers of ten approximated as a 64-bit mantissa. It goes from 10^-325 to
    // 10^308 (inclusively). The mantissa is truncated, and never rounded up.
    private static final long[] MANTISSA_64 = generateTableMantissa64();

    private static final long[][] DOUBLE_POW5_SPLIT = generateDoublePow5Table();
    private static final long[][] DOUBLE_POW5_INV_SPLIT = generateDoublePow5InvTable();

    private static final char[][] STRING_ZEROS = IntStream.rangeClosed(0, 15)
         .mapToObj(n -> Stream.generate(() -> "0")
             .limit(n)
             .collect(Collectors.joining())
             .toCharArray())
         .toArray(char[][]::new);

    private static final long[] DECIMAL_POWER_OF_TEN = LongStream.iterate(1, value -> value * 10)
        .limit(MAX_SIGNIFICANT_DIGITS)
        .toArray();

    private static final double[] POSITIVE_POWERS_OF_TEN = IntStream.rangeClosed(0, MAX_POWER)
        .mapToDouble(exp -> Double.parseDouble("1E" + exp))
        .toArray();

    private static final double[] NEGATIVE_POWERS_OF_TEN = IntStream.rangeClosed(0, -MIN_POWER)
        .mapToDouble(exp -> Double.parseDouble("1E-" + exp))
        .toArray();

    /**
     * Extract exponent of double value with fast approximation. Used for positive values only.
     * @param value double value
     * @return exponent in decimal system
     */
    static int extractExponent(double value) {
        return extractExponent(value, Double.doubleToRawLongBits(value));
    }

    private static int extractExponent(double value, long bits) {
        if (value == 0) {
            return 0;
        }
        // exponent in format pow2(x - 1023) but we need pow10(y)
        // pow2(x) = pow10(y) => y = log10(pow2(x)) = x * log10(2) = 0.301029995663981 * x
        int exponent = (int) ((((bits & EXPONENT_MASK) >> MANTISSA_BITS) - EXPONENT_BIAS) * 0.301029995663981);

        double power = exponent > 0 ? POSITIVE_POWERS_OF_TEN[exponent] : NEGATIVE_POWERS_OF_TEN[-exponent];
        // process correction of exponent approximation of pow10
        if (value > power) {
            double tmp = exponent >= 0 ? POSITIVE_POWERS_OF_TEN[exponent + 1] : NEGATIVE_POWERS_OF_TEN[-exponent - 1];
            if (value >= tmp) {
                if (value != 1E23) { // exist one not unrepresentable strict in double power 10 value 1E23 (9.9..9E22)
                    exponent++;
                }
            }
        } else if (value < power) {
            exponent--;
            if (exponent <= -MAX_POWER) {
                // for cases exponent < -308
                while (value < NEGATIVE_POWERS_OF_TEN[-exponent]) {
                    exponent--;
                }
            }
        }
        return exponent;
    }

    static double parseDouble(CharSequence text) {
        return parseDouble(text, 0, text.length());
    }

    static double parseDouble(CharSequence text, int start, int end) {
        if (start >= end) {
            throw new NumberFormatException("Empty char sequence");
        }

        long mantissa = 0; // Accumulates mantissa omitting decimal dot.
        boolean floatingPoint = false;
        boolean hasDigit = false;
        int divider = 0;

        char ch = text.charAt(start);
        boolean negative = ch == '-';
        int i = negative || ch == '+' ? start + 1 : start;
        int remainDigits = MAX_SIGNIFICANT_DIGITS;

        for (; i < end; i++) {
            ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (remainDigits > 0) {
                    if (floatingPoint) {
                        divider++;
                    }
                    mantissa = mantissa * 10 + (ch - '0');
                    if (mantissa != 0) {
                        remainDigits--;
                    }
                } else if (!floatingPoint) {
                    divider--;
                }
                hasDigit = true;
            } else if (ch == '.') {
                if (floatingPoint) {
                    throw new NumberFormatException("Multiple points");
                }
                floatingPoint = true;
            } else {
                break;
            }
        }

        if (!hasDigit) {
            if (ch == 'N' && checkNonNumber(text, i + 1, end, ARRAY_NAN) && !floatingPoint) {
                return Double.NaN;
            } else if (ch == 'I' && checkNonNumber(text, i + 1, end, ARRAY_INFINITY) && !floatingPoint) {
                return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            throw new NumberFormatException("Invalid input char sequence");
        }

        int exponent = i >= end ? 0 : parseExponent(ch, text, i, end);

        return evaluateStrict(negative, mantissa, divider, exponent, text, start, end);
    }

    static String formatDoublePrecision(double value, int precision) {
        StringBuilder sb = new StringBuilder(MAX_CHARS);
        formatDoublePrecision(sb, value, precision);
        return sb.toString();
    }

    static void formatDoublePrecision(StringBuilder sb, double value, int precision) {
        if (precision < 0) {
            throw new IllegalArgumentException("Precision must be positive value");
        } else if (precision == 0) {
            precision = MAX_SIGNIFICANT_DIGITS;
        }

        if (value == 0) {
            sb.append(Double.doubleToRawLongBits(value) < 0 ? "-0" : '0');
            return;
        }

        // corner cases processing
        if (!Double.isFinite(value)) {
            if (Double.isNaN(value)) {
                sb.append(ARRAY_NAN);
            } else if (value == Double.POSITIVE_INFINITY) {
                sb.append(ARRAY_INFINITY);
            } else if (value == Double.NEGATIVE_INFINITY) {
                sb.append('-').append(ARRAY_INFINITY);
            }
            return;
        }

        // make positive values
        if (value < 0) {
            sb.append('-');
            value = -value;
        }

        // define offset for engineering format
        int offset = engineeringOffset(value);
        if (offset == Integer.MAX_VALUE) {
            scientificPrecisionFormat(sb, value, precision); // scientific format
        } else {
            engineeringPrecisionFormat(sb, value, precision, offset); // engineering format
        }
    }

    static String formatDoubleScale(double value, int scale) {
        StringBuilder sb = new StringBuilder(MAX_CHARS);
        formatDoubleScale(sb, value, scale);
        return sb.toString();
    }

    static void formatDoubleScale(StringBuilder sb, double value, int scale) {
        if (value == 0) {
            sb.append('0');
            return;
        }

        // corner cases processing
        if (!Double.isFinite(value)) {
            if (Double.isNaN(value)) {
                sb.append(ARRAY_NAN);
            } else if (value == Double.POSITIVE_INFINITY) {
                sb.append(ARRAY_INFINITY);
            } else if (value == Double.NEGATIVE_INFINITY) {
                sb.append('-').append(ARRAY_INFINITY);
            }
            return;
        }

        // make positive values
        boolean negative = value < 0;
        if (negative) {
            value = -value;
        }

        // define offset for engineering format
        int offset = engineeringOffset(value);
        if (offset == Integer.MAX_VALUE) {
            scientificScaleFormat(sb, negative, value, scale); // scientific format
        } else {
            engineeringScaleFormat(sb, negative, value, scale, offset); // engineering format
        }
    }

    private static void scientificScaleFormat(StringBuilder sb, boolean negative, double value, int scale) {
        long bits = Double.doubleToRawLongBits(value);
        int exponent = extractExponent(value, bits);

        long mantissa = extractMantissa(bits);
        int length = decimalLength(mantissa);
        if (exponent < length || scale < 0) {
            if (exponent <= 0 && scale < -exponent - 1) {
                // scale less than exponent
                sb.append('0');
                return;
            } else if (scale < length - exponent - 1) {
                // rounding
                int precision = exponent + scale + 1;
                mantissa = roundMantissa(mantissa, length, precision);
                if (mantissa == 0) {
                    sb.append('0');
                    return;
                }

                length = decimalLength(mantissa);
                if (length > precision) {
                    exponent++;
                }
            }
        }

        appendMantissaBuilder(negative ? sb.append('-') : sb, mantissa, 1, length).append('E').append(exponent);
    }

    private static void engineeringScaleFormat(
        StringBuilder sb, boolean negative, double value, int scale, int offset)
    {
        // check is value simple decimal number
        if (value == Math.rint(value) && scale >= 0) {
            // the faster way to use jdk Integer converter
            (negative ? sb.append('-') : sb).append((long) value);
            return;
        }

        long mantissa = extractMantissa(Double.doubleToRawLongBits(value));

        int length = decimalLength(mantissa);
        if (offset < length || scale < 0) {
            if (offset < 0 && scale < -offset) {
                // scale less than exponent
                sb.append('0');
                return;
            } else if (scale < length - offset) {
                // rounding
                int precision = offset + scale;
                mantissa = roundMantissa(mantissa, length, precision);
                if (mantissa == 0) {
                    sb.append('0');
                    return;
                }

                length = decimalLength(mantissa);
                if (length > precision) {
                    offset++;
                }
            }
        }

        appendMantissaBuilder(negative ? sb.append("-") : sb, mantissa, offset, length);
    }

    private static void scientificPrecisionFormat(StringBuilder sb, double value, int precision) {
        long bits = Double.doubleToRawLongBits(value);
        int exponent = extractExponent(value, bits);

        long mantissa = extractMantissa(bits);

        int length = decimalLength(mantissa);
        if (length > precision) {
            mantissa = roundMantissa(mantissa, length, precision);

            length = decimalLength(mantissa);
            if (length > precision) {
                exponent++;
            }
        }

        appendMantissaBuilder(sb, mantissa, 1, length).append('E').append(exponent);
    }

    private static void engineeringPrecisionFormat(StringBuilder sb, double value, int precision, int offset) {
        // check is value simple decimal number
        if (value == Math.rint(value)) {
            // the faster way to use jdk Integer converter
            sb.append(roundDecimal((long) value, precision));
            return;
        }

        long mantissa = extractMantissa(Double.doubleToRawLongBits(value));

        int length = decimalLength(mantissa);
        if (length > precision) {
            mantissa = roundMantissa(mantissa, length, precision);

            length = decimalLength(mantissa);
            if (length > precision) {
                offset++;
            }
        }

        appendMantissaBuilder(sb, mantissa, offset, length);
    }

    private static StringBuilder appendMantissaBuilder(StringBuilder sb, long mantissa, int decimalOffset, int length) {
        while (length > 0 && mantissa % 10 == 0) {
            mantissa /= 10;
            length--;
        }

        if (decimalOffset <= 0) {
            // for numbers less than 1 add forward 0
            sb.append("0.");
            if (decimalOffset != 0) {
                sb.append(STRING_ZEROS[-decimalOffset]);
            }
            sb.append(mantissa);
        } else if (length >= decimalOffset) {
            // for numbers with . inside mantissa
            long delimiter = DECIMAL_POWER_OF_TEN[length - decimalOffset];

            long whole = mantissa / delimiter;
            long fraction = mantissa % delimiter;

            sb.append(whole);
            if (fraction != 0) {
                sb.append('.');
                int zeros = length - decimalOffset - decimalLength(fraction);
                if (zeros > 0) {
                    sb.append(STRING_ZEROS[zeros]);
                }
                sb.append(fraction);
            }
        } else {
            // for numbers with trailing 0
            sb.append(mantissa).append(STRING_ZEROS[decimalOffset - length]);
        }
        return sb;
    }

    static double roundScale(double value, int scale, RoundingMode mode) {
        // check is integer value, don't need rounding
        if (!Double.isFinite(value)) {
            return value;
        }

        boolean negative = value < 0;
        double abs = Math.abs(value);

        long bits = Double.doubleToRawLongBits(abs);
        int exponent = extractExponent(abs, bits);

        long mantissa = (exponent >= 0 && exponent <= ROUND_SIGNIFICANT_DIGITS && abs == Math.rint(abs)) ?
            (long) abs :
            extractMantissa(bits);

        int length = decimalLength(mantissa);
        if (exponent < length || scale < 0) {
            if (scale < -exponent - 1) {
                // scale less than exponent
                int absScale;
                double[] array;
                if (scale < 0) {
                    absScale = -scale;
                    array = POSITIVE_POWERS_OF_TEN;
                } else {
                    absScale = scale;
                    array = NEGATIVE_POWERS_OF_TEN;
                }
                switch (mode) {
                    case UP:
                        return negative ? -array[absScale] : array[absScale];
                    case FLOOR:
                        return negative ? -array[absScale] : 0.0;
                    case CEILING:
                        return negative ? 0.0 : array[absScale];
                    default:
                        return 0.0;
                }
            } else if (scale < length - exponent - 1) {
                // rounding
                int precision = exponent + scale + 1;
                mantissa = roundSignificant(negative, mantissa, length, length - precision, mode);
                if (mantissa == 0) {
                    return BigDecimal.valueOf(value).setScale(scale, mode).doubleValue();
                }
                length = decimalLength(mantissa);
                if (length > precision) {
                    exponent++;
                }
            }
        }
        exponent = exponent - length + 1;

        double result;
        if (-MAX_STRICT_EXPONENT <= exponent && exponent <= MAX_STRICT_EXPONENT &&
            mantissa < MAX_MANTISSA)
        {
            result = addExponent(mantissa, exponent);
        } else {
            result = combine(mantissa, exponent);
            if (Double.isNaN(result)) {
                StringBuilder sb = new StringBuilder(MAX_CHARS);
                (negative ? sb.append('-') : sb).append(mantissa).append('E').append(exponent);
                return parseDouble(sb);
            }
        }
        return negative ? -result : result;
    }

    static double roundPrecision(double value, int precision, RoundingMode mode) {
        if (precision < 0) {
            throw new IllegalArgumentException("Precision must be positive value");
        }

        // check is integer value, don't need rounding
        if (!Double.isFinite(value) || precision > ROUND_SIGNIFICANT_DIGITS || precision == 0) {
            return value;
        }

        boolean negative = value < 0;
        double abs = Math.abs(value);

        long bits = Double.doubleToRawLongBits(abs);
        int exponent = extractExponent(abs, bits);

        long mantissa = (exponent >= 0 && exponent <= ROUND_SIGNIFICANT_DIGITS && abs == Math.rint(abs)) ?
            (long) abs :
            extractMantissa(bits);
        int length = decimalLength(mantissa);

        if (length > precision) {
            int index = length - precision;
            mantissa = roundSignificant(negative, mantissa, length, index, mode);
            if (mantissa == 0) {
                return BigDecimal.valueOf(value).round(new MathContext(precision, mode)).doubleValue();
            }
        } else {
            return value;
        }
        int exponentAdjust = exponent - precision + 1;

        double result;
        if (-MAX_STRICT_EXPONENT <= exponentAdjust && exponentAdjust <= MAX_STRICT_EXPONENT &&
            mantissa < MAX_MANTISSA)
        {
            result = addExponent(mantissa, exponentAdjust);
        } else {
            result = combine(mantissa, exponentAdjust);
            if (Double.isNaN(result)) {
                StringBuilder sb = new StringBuilder(MAX_CHARS);
                (negative ? sb.append('-') : sb).append(mantissa).append('E').append(exponentAdjust);
                return parseDouble(sb);
            }
        }
        return negative ? -result : result;
    }

    private static long roundSignificant(boolean negative, long mantissa, int length, int index, RoundingMode mode) {
        long divider = DECIMAL_POWER_OF_TEN[index];
        long rem = mantissa % divider;
        long rounded = mantissa / divider;
        if (rem == 0) {
            return rounded;
        }

        if (mode == RoundingMode.HALF_EVEN) {
            mode = rounded % 2 == 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP;
        }

        switch (mode) {
            case UP: return slowPath(length, rem, divider) ? 0 : rounded + 1;
            case DOWN: return slowPath(length, rem, divider) ? 0 : rounded;
            case FLOOR: return slowPath(length, rem, divider) ? 0 : negative ? rounded + 1 : rounded;
            case CEILING: return slowPath(length, rem, divider) ? 0 : negative ? rounded : rounded + 1;
            case HALF_UP:
            case HALF_DOWN: {
                long halfRem = 5 * DECIMAL_POWER_OF_TEN[index - 1];
                return rem == halfRem ? 0 : rem > halfRem ? rounded + 1 : rounded;
            }
            default: throw new ArithmeticException("Rounding necessary");
        }
    }

    private static boolean slowPath(int mantissaSize, long rem, long divider) {
        // for some exceptional cases we should use
        return mantissaSize > 15 && rem + 3 >= divider;
    }

    private static int parseExponent(char ch, CharSequence text, int start, int end) {
        int i = start + 1;
        if ((ch != 'E' && ch != 'e') || i >= end) {
            throw new NumberFormatException("Invalid format of exponent"); // use for all invalid strings
        }

        int tmp;
        int exp = 0;
        ch = text.charAt(i);
        boolean negativeExp = ch == '-';
        if (negativeExp || ch == '+') {
            i++;
        }
        for (; i < end; i++) {
            ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new NumberFormatException("Invalid format of exponent");
            }
            tmp = exp * 10 + (ch - '0');
            // prevent overflow of integer presentation of exponent
            if (tmp > 0) {
                exp = tmp;
            }
        }
        return negativeExp ? -exp : exp;
    }

    private static boolean checkNonNumber(CharSequence text, int k, int n, char[] pattern) {
        if (n - k != pattern.length - 1) {
            return false;
        }
        // first letter was checked before
        for (int i = 1; i < pattern.length; i++) {
            if (text.charAt(k++) != pattern[i])
                return false;
        }
        return true;
    }

    private static double evaluateStrict(
        boolean negative, long mantissa, int divider, int exp, CharSequence text, int start, int end)
    {
        double value;
        int exponent = exp - divider;
        if (-MAX_STRICT_EXPONENT <= exponent && exponent <= MAX_STRICT_EXPONENT && mantissa < MAX_MANTISSA) {
            value = addExponent(mantissa, exponent);
        } else {
            if (mantissa == 0.0) {
                return negative ? -0.0 : +0.0;
            }
            if (exponent > MAX_POWER) {
                return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            if (exponent < MIN_POWER) {
                return negative ? -0.0 : +0.0;
            }
            value = combine(mantissa, exponent);
            if (Double.isNaN(value)) {
                // fallback to jdk parser in rare cases
                return Double.parseDouble(text.subSequence(start, end).toString());
            }
        }
        return negative ? -value : value;
    }

    private static double addExponent(double mantissa, int exponent) {
        return exponent >= 0 ?
            mantissa * POSITIVE_POWERS_OF_TEN[exponent] :
            mantissa / POSITIVE_POWERS_OF_TEN[-exponent];
    }

    private static long roundDecimal(long value, int precision) {
        int length = decimalLength(value);
        if (length > precision) {
            int index = length - precision;
            long divider = DECIMAL_POWER_OF_TEN[index];
            value = value % divider >= 5 * DECIMAL_POWER_OF_TEN[index - 1] ? (value / divider) + 1 : value / divider;
            value *= divider;
        }
        return value;
    }

    private static long roundMantissa(long value, int length, int precision) {
        int index = length - precision;
        if (index >= MAX_SIGNIFICANT_DIGITS) {
            return 0;
        }
        long divider = DECIMAL_POWER_OF_TEN[index];
        return value % divider >= 5 * DECIMAL_POWER_OF_TEN[index - 1] ? (value / divider) + 1 : value / divider;
    }

    /**
     * Evaluate how many decimal digits in a number
     * @param value long
     * @return decimal numbers
     */
    private static int decimalLength(long value) {
        if (value < 10000000000L) {
            if (value < 10L) {
                return 1;
            } else if (value < 100L) {
                return 2;
            } else if (value < 1000L) {
                return 3;
            } else if (value < 10000L) {
                return 4;
            } else if (value < 100000L) {
                return 5;
            } else if (value < 1000000L) {
                return 6;
            } else if (value < 10000000L) {
                return 7;
            } else if (value < 100000000L) {
                return 8;
            } else if (value < 1000000000L) {
                return 9;
            }
            return 10;
        } else {
            if (value < 100000000000L) {
                return 11;
            } else if (value < 1000000000000L) {
                return 12;
            } else if (value < 10000000000000L) {
                return 13;
            } else if (value < 100000000000000L) {
                return 14;
            } else if (value < 1000000000000000L) {
                return 15;
            } else if (value < 10000000000000000L) {
                return 16;
            } else if (value < 100000000000000000L) {
                return 17;
            } else if (value < 1000000000000000000L) {
                return 18;
            }
            return 19;
        }
    }

    /**
     * Engineering format for double presentation. Range of values [1e-9 ... 1e10). Compatible with dxFeed agreements.
     *
     * @param value double value
     * @return alignment offset for engineering presentation or marker value Integer.MAX_VALUE for scientificFormat
     */
    private static int engineeringOffset(double value) {
        if (value < 1) {
            if (value >= 0.1) {
                return 0;
            } else if (value >= 0.01) {
                return -1;
            } else if (value >= 0.001) {
                return -2;
            } else if (value >= 0.0001) {
                return -3;
            } else if (value >= 0.00001) {
                return -4;
            } else if (value >= 0.000001) {
                return -5;
            } else if (value >= 0.0000001) {
                return -6;
            } else if (value >= 0.00000001) {
                return -7;
            } else if (value >= 0.000000001) {
                return -8;
            }
        } else {
            if (value < 10) {
                return 1;
            } else if (value < 100L) {
                return 2;
            } else if (value < 1000L) {
                return 3;
            } else if (value < 10000L) {
                return 4;
            } else if (value < 100000L) {
                return 5;
            } else if (value < 1000000L) {
                return 6;
            } else if (value < 10000000L) {
                return 7;
            } else if (value < 100000000L) {
                return 8;
            } else if (value < 1000000000L) {
                return 9;
            } else if (value < 10000000000L) {
                return 10;
            }
        }
        // marker for scientific format, invalid value for engineering format
        return Integer.MAX_VALUE;
    }

    /**
     * Tricky way to combine mantissa and exponent into double with correct rounding.
     * Implementation of <a href="https://github.com/fastfloat/fast_float">Daniel Lemire's algorithm</a>
     * @param mantissa decimal number
     * @param exponent decimal power
     * @return combined double value
     */
    private static double combine(long mantissa, int exponent) {
        long factorMantissa = MANTISSA_64[exponent - MIN_POWER];

        long binaryExponent = (((152170L + 65536L) * exponent) >> 16) + EXPONENT_BIAS + 64;

        int lz = Long.numberOfLeadingZeros(mantissa);
        long shiftedSignificand = mantissa << lz;

        long upper = unsignedMultiplyHigh(shiftedSignificand, factorMantissa);

        long upperbit = upper >>> 63;
        long binaryMantissa = upper >>> (upperbit + 9);
        lz += (int) (1 ^ upperbit);

        if (((upper & 0x1FF) == 0x1FF) || ((upper & 0x1FF) == 0) && (binaryMantissa & 3) == 1) {
            return Double.NaN;
        }

        binaryMantissa += 1;
        binaryMantissa >>>= 1;

        if (binaryMantissa >= POW53) {
            binaryMantissa = POW52;
            lz--;
        }

        binaryMantissa &= ~POW52;

        long realExponent = binaryExponent - lz;
        if ((realExponent < 1) || (realExponent > 2 * EXPONENT_BIAS)) {
            return Double.NaN;
        }

        return Double.longBitsToDouble(binaryMantissa | realExponent << MANTISSA_BITS);
    }

    /**
     * Implementation of Ryu approach to extracting the exact mantissa.
     * see <a href="https://github.com/ulfjack/ryu">Ryu</a> for more information
     * @param bits binary format of double
     * @return mantissa with all significant digits
     */
    private static long extractMantissa(long bits) {
        // Extract the mantissa and exponent bits
        int ieeeExponent = (int) ((bits & EXPONENT_MASK) >>> MANTISSA_BITS);
        long ieeeMantissa = bits & MANTISSA_MASK;
        long m2;
        int e2 = -EXPONENT_BIAS - MANTISSA_BITS - 2;
        if (ieeeExponent == 0) {
            e2 += 1;
            m2 = ieeeMantissa;
        } else {
            e2 += ieeeExponent;
            m2 = ieeeMantissa | (1L << MANTISSA_BITS);
        }

        // Determine the interval of legal decimal representations
        long mv = 4 * m2;
        long mp = 4 * m2 + 2;
        int mmShift = (ieeeMantissa != 0 || ieeeExponent <= 1) ? 1 : 0;
        long mm = 4 * m2 - 1 - mmShift;

        // Convert to a decimal power base using 128-bit arithmetic
        long vr;
        long vp;
        long vm;
        boolean dvIsTrailingZeros = false;
        if (e2 >= 0) {
            int q = log10Pow2(e2) - (e2 > 3 ? 1 : 0);
            int k = POW5_INV_BIT_COUNT + pow5bits(q) - 1;
            int i = -e2 + q + k;
            long[] pow5 = DOUBLE_POW5_INV_SPLIT[q];

            vr = multiplyAndShift(mv, pow5, i);
            vp = multiplyAndShift(mp, pow5, i);
            vm = multiplyAndShift(mm, pow5, i);

            if (q <= 21) {
                if (mv % 5 == 0) {
                    dvIsTrailingZeros = isMoreMultipleOfPower5(mv, q);
                } else if (isMoreMultipleOfPower5(mp, q)) {
                    vp--;
                }
            }
        } else {
            int q = log10Pow5(-e2) - (-e2 > 1 ? 1 : 0);
            int i = -e2 - q;
            int k = pow5bits(i) - POW5_BIT_COUNT;
            int j = q - k;
            long[] pow5 = DOUBLE_POW5_SPLIT[i];

            vr = multiplyAndShift(mv, pow5, j);
            vp = multiplyAndShift(mp, pow5, j);
            vm = multiplyAndShift(mm, pow5, j);

            if (q <= 1) {
                dvIsTrailingZeros = true;
                vp--;
            } else if (q < 63) {
                dvIsTrailingZeros = (mv & ((1L << (q - 1)) - 1)) == 0;
            }
        }

        // Find the shortest decimal representation
        int lastRemovedDigit = 0;
        if (dvIsTrailingZeros) {
            while (vp / 10 > vm / 10) {
                dvIsTrailingZeros &= lastRemovedDigit == 0;
                lastRemovedDigit = (int) (vr % 10);
                vp /= 10;
                vr /= 10;
                vm /= 10;
            }
            if (dvIsTrailingZeros && (lastRemovedDigit == 5) && (vr % 2 == 0)) {
                // Round even if the exact numbers is .....50..0.
                lastRemovedDigit = 4;
            }
        } else {
            while (vp / 10 > vm / 10) {
                if ((vp < 100) && e2 < -322) {
                    // some special cases with exponent less -322
                    break;
                }
                lastRemovedDigit = (int) (vr % 10);
                vp /= 10;
                vr /= 10;
                vm /= 10;
            }
        }
        return vr + (vr == vm || (lastRemovedDigit >= 5) ? 1 : 0);
    }

    /**
     * Math.multiplyHigh available in jdk from java 18
     */
    private static long multiplyHigh(long x, long y) {
        long x1 = x >> 32;
        long x2 = x & 0xFFFFFFFFL;
        long y1 = y >> 32;
        long y2 = y & 0xFFFFFFFFL;

        long z2 = x2 * y2;
        long t = x1 * y2 + (z2 >>> 32);
        long z1 = t & 0xFFFFFFFFL;
        long z0 = t >> 32;
        z1 += x2 * y1;

        return x1 * y1 + z0 + (z1 >> 32);
    }

    /**
     * Math.unsignedMultiplyHigh available in jdk from java 18
     */
    private static long unsignedMultiplyHigh(long x, long y) {
        long result = multiplyHigh(x, y);
        result += (y & (x >> 63));
        result += (x & (y >> 63));
        return result;
    }

    private static int pow5bits(int e) {
        return ((e * 1217359) >>> 19) + 1;
    }

    private static int log10Pow2(int e) {
        return (e * 78913) >>> 18;
    }

    private static int log10Pow5(int e) {
        return (e * 732923) >>> 20;
    }

    private static boolean isMoreMultipleOfPower5(long value, int q) {
        // find the largest power of 5 that divides value.
        int power5 = 0;
        for (; ; power5++) {
            if (value % 5 != 0) {
                break; // no more dividers
            }
            value = value / 5;
        }
        return power5 >= q;
    }

    private static long multiplyAndShift(long m, long[] value64, int shift) {
        // b0 = m * value64[0];
        // b2 = m * value64[1];
        // (((b0 >> 64) + b2) >> (shift - 64))
        long hi = value64[0];
        long lo = value64[1];

        // hi part of multiplication of unsigned 64 bit values with m
        // b0 = m * value128[0]; b0 >> 64
        long hiPart = unsignedMultiplyHigh(m, hi);

        // low part multiplication, full multiplication of unsigned 64 bit values
        // b2 = m * lo_64
        long loMultiply = m * lo;
        long hiMultiply = multiplyHigh(m, lo);
        if (m < 0) {
            hiMultiply += lo;
        }

        // add unsigned 64 bit values
        loMultiply += hiPart;
        if (loMultiply + Long.MIN_VALUE < hiPart + Long.MIN_VALUE) {
            hiMultiply++;
        }

        // shift to right to get 64 bit value
        int bits = shift - 64;
        return bits < 64 ? (loMultiply >>> bits) | (hiMultiply << (64 - bits)) : hiMultiply >>> (bits - 64);
    }

    private static long[][] generateDoublePow5Table() {
        BigInteger value5 = BigInteger.valueOf(5);
        BigInteger mask64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

        return IntStream.range(0, POS_TABLE_SIZE).mapToObj(q -> {
            BigInteger power5 = value5.pow(q);
            int pow5len = power5.bitLength();
            // [5^i / 2^i] = [5^i / 2^(ceil(log_2(5^i)) - POW5_BIT_COUNT)]
            BigInteger pow5DivPow2 = power5.shiftRight(pow5len - POW5_BIT_COUNT);
            return new long[] {pow5DivPow2.and(mask64).longValue(), pow5DivPow2.shiftRight(64).longValue()};
        }).toArray(long[][]::new);
    }

    private static long[][] generateDoublePow5InvTable() {
        BigInteger value5 = BigInteger.valueOf(5);
        BigInteger mask64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

        return IntStream.range(0, NEG_TABLE_SIZE).mapToObj(q -> {
            BigInteger power5 = value5.pow(q);
            int pow5len = power5.bitLength();
            // [2^i / 5^i] + 1 = [2^(floor(log_2(5^i)) + POW5_INV_BITCOUNT) / 5^i] + 1
            BigInteger inv =
                BigInteger.ONE.shiftLeft(pow5len - 1 + POW5_INV_BIT_COUNT).divide(power5).add(BigInteger.ONE);
            return new long[] {inv.and(mask64).longValue(), inv.shiftRight(64).longValue()};
        }).toArray(long[][]::new);
    }

    private static long[] generateTableMantissa64() {
        BigInteger value2 = BigInteger.valueOf(2);
        BigInteger value5 = BigInteger.valueOf(5);
        BigInteger shift64 = BigInteger.ONE.shiftLeft(64);
        BigInteger shift127 = BigInteger.ONE.shiftLeft(127);
        BigInteger shift128 = BigInteger.ONE.shiftLeft(128);

        LongStream lowPart = IntStream.range(MIN_POWER, 0)
            .mapToLong(q -> {
                int i = 0;
                BigInteger value;
                BigInteger power5 = value5.pow(-q);
                for (BigInteger power2 = BigInteger.ONE; power5.compareTo(power2.shiftLeft(i)) > 0; i++) {}
                if (q >= -27) {
                    value = value2.pow(i + 127).divide(power5).add(BigInteger.ONE);
                } else {
                    value = value2.pow(2 * i + 128).divide(power5).add(BigInteger.ONE);
                    for (; value.compareTo(shift128) >= 0; value = value.divide(value2)) {}
                }
                return value.divide(shift64).longValue();
            });

        LongStream highPart = IntStream.rangeClosed(0, MAX_POWER)
            .mapToLong(q -> {
                BigInteger power5 = value5.pow(q);
                for (; power5.compareTo(shift127) < 0; power5 = power5.multiply(value2)) {}
                for (; power5.compareTo(shift128) >= 0; power5 = power5.divide(value2)) {}
                return power5.divide(shift64).longValue();
            });

        return LongStream.concat(lowPart, highPart).toArray();
    }
}
