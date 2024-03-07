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

import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FastDoubleUtilTest {

    private static final int RANDOM_GENERATED_NUMBERS = 1000;
    private static final String[] FULL_PRECISION_FORMATS = {"%.0f", "%.2f", "%.5f", "%.10f", "%.14f", "%.17f"};

    @Test
    public void parseDoubleSpecialCasesTest() {
        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("0.0")));
        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-0.0")));

        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-.0")));
        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("0.")));

        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("+0")));
        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-0")));

        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("+0.0e-10")));
        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-0.0E-3")));
        assertTrue(compare(+0.0, FastDoubleUtil.parseDouble("+0.0e15")));

        assertTrue(compare(0.5, FastDoubleUtil.parseDouble(".5e0")));
        assertTrue(compare(123.0, FastDoubleUtil.parseDouble("123.")));

        assertTrue(compare(Double.NaN, FastDoubleUtil.parseDouble("NaN")));
        assertTrue(compare(Double.NEGATIVE_INFINITY, FastDoubleUtil.parseDouble("-Infinity")));
        assertTrue(compare(Double.POSITIVE_INFINITY, FastDoubleUtil.parseDouble("+Infinity")));
        assertTrue(compare(Double.POSITIVE_INFINITY, FastDoubleUtil.parseDouble("Infinity")));

        assertTrue(compare(Double.MAX_VALUE, FastDoubleUtil.parseDouble(Double.toString(Double.MAX_VALUE))));
        assertTrue(compare(Double.MIN_NORMAL, FastDoubleUtil.parseDouble(Double.toString(Double.MIN_NORMAL))));
        assertTrue(compare(Double.MIN_VALUE, FastDoubleUtil.parseDouble(Double.toString(Double.MIN_VALUE))));

        assertTrue(compare(Double.POSITIVE_INFINITY, FastDoubleUtil.parseDouble("1e+310")));
        assertTrue(compare(Double.NEGATIVE_INFINITY, FastDoubleUtil.parseDouble("-1e+309")));
        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-1e-325")));
        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("1e-325")));

        assertTrue(compare(-0.0, FastDoubleUtil.parseDouble("-1e-333")));
        assertTrue(compare(0.0, FastDoubleUtil.parseDouble("1e-333")));

        assertTrue(compare(Double.NEGATIVE_INFINITY, FastDoubleUtil.parseDouble("-1E10000000000000000000000")));
        assertTrue(compare(Double.POSITIVE_INFINITY, FastDoubleUtil.parseDouble("1E100000000000000000000000")));

        String strPi = "3.14159265358979323846264338327950288"; // π number
        assertTrue(compare(Double.parseDouble(strPi), FastDoubleUtil.parseDouble(strPi)));
        String strE = "2.7182818284590452353602874713527"; // e number
        assertTrue(compare(Double.parseDouble(strE), FastDoubleUtil.parseDouble(strE)));
        String strSqrt2 = "1.41421356237309504880168872420969808"; // √2
        assertTrue(compare(Double.parseDouble(strSqrt2), FastDoubleUtil.parseDouble(strSqrt2)));
        String strFi = "1.61803398874989484820458683436563812"; // Golden ratio (φ)
        assertTrue(compare(Double.parseDouble(strFi), FastDoubleUtil.parseDouble(strFi)));
        String strH = "0.00000000000000000000000000000000062607015"; // Planck's constant h
        assertTrue(compare(Double.parseDouble(strH), FastDoubleUtil.parseDouble(strH)));
        String strNa = "602214085774000000000000"; // Avogadro's constant Na
        assertTrue(compare(Double.parseDouble(strNa), FastDoubleUtil.parseDouble(strNa)));
    }

    @Test
    public void parseDoubleSpecialExceptions() {
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble(""));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("-"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("-."));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("."));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble(".e1"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("-E1"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("¯|_(ツ)_/¯"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("1.2.3"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("1.E2Y3"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("1234567¯|_(ツ)_/¯"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("INF¯|_(ツ)_/¯"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("N¯|_(ツ)_/¯"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("NXX"));
        assertThrows(NumberFormatException.class, () -> FastDoubleUtil.parseDouble("1E10000X"));
    }

    @Test
    public void parseDoubleWithExponentTest() {
        generateValuesWithExponent().forEach(this::parseDoubles);
    }

    @Test
    public void parseDoubleWithoutExponentTest() {
        generateValuesWithoutExponent(0.0000001, 0.00001).forEach(this::parseDoubles);
        generateValuesWithoutExponent(0.000001, 0.0001).forEach(this::parseDoubles);
        generateValuesWithoutExponent(0.0001, 0.001).forEach(this::parseDoubles);
        generateValuesWithoutExponent(0.01, 0.1).forEach(this::parseDoubles);
        generateValuesWithoutExponent(1, 100).forEach(this::parseDoubles);
        generateValuesWithoutExponent(100, 10000).forEach(this::parseDoubles);
        generateValuesWithoutExponent(10000, 1000000).forEach(this::parseDoubles);
        generateValuesWithoutExponent(1000000, 100000000).forEach(this::parseDoubles);
    }

    @Test
    public void formatDoubleMaxPrecisionTest() {
        assertEquals("-0", formatPrecision(-0.0));
        assertEquals("0", formatPrecision(0.0));

        assertEquals("NaN", formatPrecision(Double.NaN));
        assertEquals("-Infinity", formatPrecision(Double.NEGATIVE_INFINITY));
        assertEquals("Infinity", formatPrecision(Double.POSITIVE_INFINITY));
        assertEquals("Infinity", formatPrecision(Double.POSITIVE_INFINITY));

        assertEquals("9.999999999999999E22", formatPrecision(1E23));
        assertEquals("9.999999999999999E22", formatPrecision(9.999999999999999E22));

        assertEquals("0.000000001", formatPrecision(1E-9));
        assertEquals("1000000000", formatPrecision(1E9));

        assertEquals("0.00000000123456789",  formatPrecision(1.23456789E-9));
        assertEquals("1234567890", formatPrecision(1.23456789E9));

        assertEquals("9999999999", formatPrecision(9999999999L));
        assertEquals("9.9999999E-10", formatPrecision(9.9999999E-10));

        assertEquals("9.99999999999999E16", formatPrecision(9.99999999999999e16));

        assertEquals("1E-10", formatPrecision(1E-10));
        assertEquals("1E10", formatPrecision(1E10));

        assertEquals(Double.toString(Double.MAX_VALUE), formatPrecision(Double.MAX_VALUE));
        assertEquals(Double.toString(Double.MIN_NORMAL), formatPrecision(Double.MIN_NORMAL));
        assertEquals(Double.toString(Double.MIN_VALUE), formatPrecision(Double.MIN_VALUE));

        assertThrows(IllegalArgumentException.class, () -> FastDoubleUtil.formatDoublePrecision(1E10, -1));
    }

    @Test
    public void formatDoublePrecisionTest() {
        assertEquals("0.00000000123",  formatPrecision(1.23456789E-9, 3));
        assertEquals("1235000000", formatPrecision(1.23456789E9, 4));

        assertEquals("10000000000", formatPrecision(9999999999L, 3));
        assertEquals("9.9999999E-10", formatPrecision(9.9999999E-10));

        assertEquals("1E23", formatPrecision(1E23, 15));
        assertEquals("1E23", formatPrecision(9.999999999999999E22, 15));

        assertEquals("1E17", formatPrecision(9.999999999999999E16, 15));
        assertEquals("0.00001", formatPrecision(9.999999999999999E-6, 15));
        assertEquals("0.3", formatPrecision(0.2 + 0.1, 15));
    }

    @Test
    public void formatDoubleNotationFormatTest() {
        assertEquals("0.00000000123",  formatPrecision(1.23456789E-9, 3));
        assertEquals("1235000000", formatPrecision(1.23456789E9, 4));

        assertEquals("10000000000", formatPrecision(9999999999L, 3));
        assertEquals("9.9999999E-10", formatPrecision(9.9999999E-10, 15));
        assertEquals("1E-9", formatPrecision(9.9999999E-10, 7));

        assertEquals("1E17", formatPrecision(9.999999999999999E16, 15));
        assertEquals("0.00001", formatPrecision(9.999999999999999E-6, 15));
        assertEquals("0.3", formatPrecision(0.2 + 0.1, 15));
    }

    @Test
    public void formatEngineeringDouble() {
        assertEquals("0",  formatPrecision(0));
        assertEquals("0.1",  formatPrecision(0.1));
        assertEquals("0.01",  formatPrecision(0.01));
        assertEquals("0.001",  formatPrecision(0.001));
        assertEquals("0.0001",  formatPrecision(0.0001));
        assertEquals("0.00001",  formatPrecision(0.00001));
        assertEquals("0.000001",  formatPrecision(0.000001));
        assertEquals("0.0000001",  formatPrecision(0.0000001));
        assertEquals("0.00000001",  formatPrecision(0.00000001));
        assertEquals("0.000000001",  formatPrecision(0.000000001));
        assertEquals("1E-10",  formatPrecision(0.0000000001));
    }

    @Test
    public void formatScientificDouble() {
        assertEquals("1",  formatPrecision(1L));
        assertEquals("10",  formatPrecision(10L));
        assertEquals("100",  formatPrecision(100L));
        assertEquals("1000",  formatPrecision(1000L));
        assertEquals("10000",  formatPrecision(10000L));
        assertEquals("100000",  formatPrecision(100000L));
        assertEquals("1000000",  formatPrecision(1000000L));
        assertEquals("10000000",  formatPrecision(10000000L));
        assertEquals("100000000",  formatPrecision(100000000L));
        assertEquals("1000000000",  formatPrecision(1000000000L));
        assertEquals("1E10",  formatPrecision(10000000000L));
    }

    @Test
    public void formatDoubleScaleSpecialCasesTest() {
        assertEquals("0", formatScale(-0.0, 0));
        assertEquals("0", formatScale(0.0, 0));

        assertEquals("NaN", formatScale(Double.NaN, 0));
        assertEquals("-Infinity", formatScale(Double.NEGATIVE_INFINITY, 0));
        assertEquals("Infinity", formatScale(Double.POSITIVE_INFINITY, 0));

        assertEquals("9.999999999999999E22", formatScale(1E23, 0));
        assertEquals("9.999999999999999E22", formatScale(9.999999999999999E22, 0));

        assertEquals("0.000000001", formatScale(1E-9, 9));
        assertEquals("1000000000", formatScale(1E9, 0));

        assertEquals("0.00000000123456789",  formatScale(1.23456789E-9, 17));
        assertEquals("1234567890", formatScale(1.23456789E9, 10));

        assertEquals("0.3", formatScale(0.2 + 0.1, 16));
        assertEquals("0.30000000000000004", formatScale(0.2 + 0.1, 17));

        assertEquals("1E-14", formatScale(6.0E-15, 14));
        assertEquals("0", formatScale(-0.3, 0));

        assertEquals(Double.toString(Double.MAX_VALUE), formatScale(Double.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Double.toString(Double.MIN_NORMAL), formatScale(Double.MIN_NORMAL, Integer.MAX_VALUE));
        assertEquals(Double.toString(Double.MIN_VALUE), formatScale(Double.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    public void formatDoubleNegativeScaleTest() {
        // same behavior with negative scale as BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
        assertEquals("123456789", formatScale(123456789.12345,  0));
        assertEquals("123456790", formatScale(123456789.12345, -1));
        assertEquals("123456800", formatScale(123456789.12345, -2));
        assertEquals("123457000", formatScale(123456789.12345, -3));
        assertEquals("123460000", formatScale(123456789.12345, -4));
        assertEquals("123500000", formatScale(123456789.12345, -5));
        assertEquals("123000000", formatScale(123456789.12345, -6));
        assertEquals("120000000", formatScale(123456789.12345, -7));
        assertEquals("100000000", formatScale(123456789.12345, -8));
        assertEquals("0",         formatScale(123456789.12345, -9));
        assertEquals("0",         formatScale(123456789.12345, -100));

        assertEquals("1.23456789E42", formatScale(1.23456789E42,    0));
        assertEquals("1.23456789E42", formatScale(1.23456789E42,   -1));
        assertEquals("1.2345679E42",  formatScale(1.23456789E42,  -35));
        assertEquals("1.234568E42",   formatScale(1.23456789E42,  -36));
        assertEquals("1.23457E42",    formatScale(1.23456789E42,  -37));
        assertEquals("1.2346E42",     formatScale(1.23456789E42,  -38));
        assertEquals("1.235E42",      formatScale(1.23456789E42,  -39));
        assertEquals("1.23E42",       formatScale(1.23456789E42,  -40));
        assertEquals("1.2E42",        formatScale(1.23456789E42,  -41));
        assertEquals("1E42",          formatScale(1.23456789E42,  -42));
        assertEquals("0",             formatScale(1.23456789E42,  -43));
    }

    @Test
    public void formatDoubleScaleFractionTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(0, 42).forEach(scale -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" : "-") + rnd.nextInt(42))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        String strScale = formatScale(value, scale);
                        String strBigDecimal = BigDecimal.valueOf(value)
                            .setScale(scale, RoundingMode.HALF_UP)
                            .toString();

                        double scaleDouble = Double.parseDouble(strScale);
                        double bigDecimalDouble = Double.parseDouble(strBigDecimal);

                        if (!compare(scaleDouble, bigDecimalDouble)) {
                            fail("Incorrect scale: " + value + ", result: " +
                                scaleDouble + " jdk: " + bigDecimalDouble + ", scale: " + scale);
                        }
                    });
            }
        });
    }

    @Test
    public void formatDoubleScaleIntegerTest() {
        Random rnd = new Random();
        IntStream.range(-18, 0).forEach(scale -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E+" + rnd.nextInt(16))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        String strScale = formatScale(value, scale);
                        String strBigDecimal = BigDecimal.valueOf(value)
                            .setScale(scale, RoundingMode.HALF_UP)
                            .toString();

                        double scaleDouble = Double.parseDouble(strScale);
                        double bigDecimalDouble = Double.parseDouble(strBigDecimal);

                        if (!compare(scaleDouble, bigDecimalDouble)) {
                            fail("Incorrect scale: " + value + ", result: " + scaleDouble +
                                ", jdk: " + bigDecimalDouble + ", scale: " + scale);
                        }
                    });
            }
        });
    }

    @Test
    public void formatDoubleScaleApproximateIntegerTest() {
        Random rnd = new Random();
        IntStream.range(-20, 0).forEach(scale -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E+" + rnd.nextInt(308))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        String strScale = formatScale(value, scale);
                        BigDecimal decimal = BigDecimal.valueOf(value);

                        String strBigDecimalUp = decimal.setScale(scale, RoundingMode.UP).toString();
                        String strBigDecimalDown = decimal.setScale(scale, RoundingMode.DOWN).toString();

                        double scaleDouble = Double.parseDouble(strScale);
                        double bigDecimalDoubleUp = Double.parseDouble(strBigDecimalUp);
                        double bigDecimalDoubleDown = Double.parseDouble(strBigDecimalDown);

                        if (!compare(scaleDouble, bigDecimalDoubleUp) && !compare(scaleDouble, bigDecimalDoubleDown)) {
                            fail("Incorrect scale: " + value + ", result: " + scaleDouble +
                                ", bigDecimalDoubleUp: " + bigDecimalDoubleUp +
                                ", bigDecimalDoubleDown: " + bigDecimalDoubleDown + ", scale: " + scale);
                        }
                    });
            }
        });
    }

    @Test
    public void formatDoubleStrictRoundingTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(1, 16).forEach(round -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" + rnd.nextInt(16) : "-" + rnd.nextInt(308)))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        String fast = formatPrecision(value, round);
                        String jdk =
                            BigDecimal.valueOf(value).round(new MathContext(round, RoundingMode.HALF_UP)).toString();

                        double fastDouble = Double.parseDouble(fast);
                        double jdkDouble = Double.parseDouble(jdk);

                        if (!compare(fastDouble, jdkDouble)) {
                            fail("Incorrect format:" + value + ", util: " + fastDouble +
                                ", jdk: " + jdkDouble + ", round: " + round);
                        }
                    });
            }
        });
    }

    @Test
    public void formatDoubleApproximationRoundingTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(1, 16).forEach(round -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" + rnd.nextInt(307) : "-" + rnd.nextInt(320)))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        String fast = formatPrecision(value, round);
                        String jdkUp =
                            BigDecimal.valueOf(value).round(new MathContext(round, RoundingMode.UP)).toString();
                        String jdkDown =
                            BigDecimal.valueOf(value).round(new MathContext(round, RoundingMode.DOWN)).toString();

                        double fastDouble = Double.parseDouble(fast);
                        double jdkDoubleUp = Double.parseDouble(jdkUp);
                        double jdkDoubleDown = Double.parseDouble(jdkDown);

                        if (!compare(fastDouble, jdkDoubleUp) && !compare(fastDouble, jdkDoubleDown)) {
                            fail("Incorrect format:" + value + ", util: " + fastDouble +
                                ", jdk: " + jdkDoubleUp + "/" + jdkDoubleDown + ", round: " + round);
                        }
                    });
            }
        });
    }

    @Test
    public void roundScaleSpecialCasesTest() {
        assertTrue(compare(0, roundScale(-0.0, 0, RoundingMode.HALF_UP)));
        assertTrue(compare(0, roundScale(0.0, 0, RoundingMode.CEILING)));

        assertTrue(Double.isNaN(roundScale(Double.NaN, 0, RoundingMode.DOWN)));
        assertTrue(compare(Double.NEGATIVE_INFINITY, roundScale(Double.NEGATIVE_INFINITY, 0, RoundingMode.UP)));
        assertTrue(compare(Double.POSITIVE_INFINITY, roundScale(Double.POSITIVE_INFINITY, 0, RoundingMode.UP)));

        assertTrue(compare(1E23, roundScale(1E23, 0, RoundingMode.HALF_UP)));

        assertTrue(compare(0.000000001234568,  roundScale(1.2345675E-9, 15, RoundingMode.HALF_UP)));
        assertTrue(compare(0.000000001234567,  roundScale(1.2345675E-9, 15, RoundingMode.HALF_DOWN)));
        assertTrue(compare(0.000000001234568,  roundScale(1.2345675E-9, 15, RoundingMode.HALF_EVEN)));

        assertTrue(compare(1.2345679E9, roundScale(1.23456789E9, -2, RoundingMode.HALF_UP)));
        assertTrue(compare(1.2345678E9, roundScale(1.23456789E9, -2, RoundingMode.DOWN)));

        assertTrue(compare(0.3, roundScale(0.2 + 0.1, 16, RoundingMode.HALF_UP)));
        assertTrue(compare(0.3, roundScale(0.2 + 0.1, 16, RoundingMode.DOWN)));

        assertTrue(compare(0.30000000000000004, roundScale(0.2 + 0.1, 17, RoundingMode.HALF_UP)));
        assertTrue(compare(0.3000000000000001, roundScale(0.2 + 0.1, 16, RoundingMode.UP)));
        assertTrue(compare(0.3, roundScale(0.2 + 0.1, 16, RoundingMode.DOWN)));

        assertTrue(compare(0.1, roundScale(0.06, 1, RoundingMode.HALF_UP)));
        assertTrue(compare(0, roundScale(0.06, 1, RoundingMode.DOWN)));

        assertTrue(compare(1235.0, roundScale(1.2345E3, 0, RoundingMode.HALF_UP)));
        assertTrue(compare(1.3E-5, roundScale(1.2345E-5, 6, RoundingMode.UP)));

        assertTrue(compare(-10000, roundScale(-100.0, -4, RoundingMode.UP)));
        assertTrue(compare(1E20, roundScale(123456789, -20, RoundingMode.CEILING)));
    }

    @Test
    public void roundNegativeScaleTest() {
        assertTrue(compare(123456789, roundScale(123456789.12345,  0, RoundingMode.HALF_UP)));
        assertTrue(compare(123456789, roundScale(123456789.12345,  0, RoundingMode.DOWN)));
        assertTrue(compare(123456790, roundScale(123456789.12345,  0, RoundingMode.UP)));

        assertTrue(compare(123456790, roundScale(123456789.12345, -1, RoundingMode.HALF_UP)));
        assertTrue(compare(123456780, roundScale(123456789.12345, -1, RoundingMode.DOWN)));
        assertTrue(compare(123456790, roundScale(123456789.12345, -1, RoundingMode.UP)));

        assertTrue(compare(123456800, roundScale(123456789.12345, -2, RoundingMode.HALF_UP)));
        assertTrue(compare(123456700, roundScale(123456789.12345, -2, RoundingMode.DOWN)));
        assertTrue(compare(123456800, roundScale(123456789.12345, -2, RoundingMode.UP)));

        assertTrue(compare(123457000, roundScale(123456789.12345, -3, RoundingMode.HALF_UP)));
        assertTrue(compare(123456000, roundScale(123456789.12345, -3, RoundingMode.DOWN)));
        assertTrue(compare(123457000, roundScale(123456789.12345, -3, RoundingMode.UP)));

        assertTrue(compare(123460000, roundScale(123456789.12345, -4, RoundingMode.HALF_UP)));
        assertTrue(compare(123450000, roundScale(123456789.12345, -4, RoundingMode.DOWN)));
        assertTrue(compare(123460000, roundScale(123456789.12345, -4, RoundingMode.UP)));

        assertTrue(compare(123500000, roundScale(123456789.12345, -5, RoundingMode.HALF_UP)));
        assertTrue(compare(123400000, roundScale(123456789.12345, -5, RoundingMode.DOWN)));
        assertTrue(compare(123500000, roundScale(123456789.12345, -5, RoundingMode.UP)));

        assertTrue(compare(123000000, roundScale(123456789.12345, -6, RoundingMode.HALF_UP)));
        assertTrue(compare(123000000, roundScale(123456789.12345, -6, RoundingMode.DOWN)));
        assertTrue(compare(124000000, roundScale(123456789.12345, -6, RoundingMode.UP)));

        assertTrue(compare(120000000, roundScale(123456789.12345, -7, RoundingMode.HALF_UP)));
        assertTrue(compare(120000000, roundScale(123456789.12345, -7, RoundingMode.DOWN)));
        assertTrue(compare(130000000, roundScale(123456789.12345, -7, RoundingMode.UP)));

        assertTrue(compare(100000000, roundScale(123456789.12345, -8, RoundingMode.HALF_UP)));
        assertTrue(compare(100000000, roundScale(123456789.12345, -8, RoundingMode.DOWN)));
        assertTrue(compare(200000000, roundScale(123456789.12345, -8, RoundingMode.UP)));

        assertTrue(compare(0,         roundScale(123456789.12345, -9, RoundingMode.HALF_UP)));
        assertTrue(compare(0,         roundScale(123456789.12345, -9, RoundingMode.DOWN)));
        assertTrue(compare(1E9,       roundScale(123456789.12345, -9, RoundingMode.UP)));

        assertTrue(compare(0,         roundScale(123456789.12345, -100, RoundingMode.HALF_UP)));
        assertTrue(compare(0,         roundScale(123456789.12345, -100, RoundingMode.DOWN)));
        assertTrue(compare(1E100,     roundScale(123456789.12345, -100, RoundingMode.UP)));
    }

    @Test
    public void roundScaleFractionTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(0, 42).forEach(scale -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" : "-") + rnd.nextInt(42))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        double halfUp = roundScale(value, scale, RoundingMode.HALF_UP);
                        double halfDown = roundScale(value, scale, RoundingMode.HALF_DOWN);
                        double halfEven = roundScale(value, scale, RoundingMode.HALF_EVEN);
                        double ceiling = roundScale(value, scale, RoundingMode.CEILING);
                        double floor = roundScale(value, scale, RoundingMode.FLOOR);
                        double up = roundScale(value, scale, RoundingMode.UP);
                        double down = roundScale(value, scale, RoundingMode.DOWN);

                        BigDecimal decimal = BigDecimal.valueOf(value);

                        double jdkHalfUp = decimal.setScale(scale, RoundingMode.HALF_UP).doubleValue();
                        double jdkHalfDown = decimal.setScale(scale, RoundingMode.HALF_DOWN).doubleValue();
                        double jdkHalfEven = decimal.setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
                        double jdkCeiling = decimal.setScale(scale, RoundingMode.CEILING).doubleValue();
                        double jdkFloor = decimal.setScale(scale, RoundingMode.FLOOR).doubleValue();
                        double jdkUp = decimal.setScale(scale, RoundingMode.UP).doubleValue();
                        double jdkDown = decimal.setScale(scale, RoundingMode.DOWN).doubleValue();

                        if (!compare(halfUp, jdkHalfUp)) {
                            fail("Incorrect halfUp: " + value + ", result: " + halfUp +
                                ", jdkHalfUp: " + jdkHalfUp + ", scale: " + scale);
                        }
                        if (!compare(halfDown, jdkHalfDown)) {
                            fail("Incorrect halfDown: " + value + ", result: " + halfDown +
                                ", jdkHalfDown: " + jdkHalfDown + ", scale: " + scale);
                        }
                        if (!compare(halfEven, jdkHalfEven)) {
                            fail("Incorrect halfEven: " + value + ", result: " + halfEven +
                                ", jdkHalfEven: " + jdkHalfEven + ", scale: " + scale);
                        }
                        if (!compare(ceiling, jdkCeiling)) {
                            fail("Incorrect ceiling: " + value + ", result: " + ceiling +
                                ", jdkCeiling: " + jdkCeiling + ", scale: " + scale);
                        }
                        if (!compare(floor, jdkFloor)) {
                            fail("Incorrect floor: " + value + ", result: " + floor +
                                ", jdkFloor: " + jdkFloor + ", scale: " + scale);
                        }
                        if (!compare(up, jdkUp)) {
                            fail("Incorrect up: " + value + ", result: " + up +
                                ", jdkUp: " + jdkUp + ", scale: " + scale);
                        }
                        if (!compare(down, jdkDown)) {
                            fail("Incorrect down: " + value + ", result: " + down +
                                ", jdkDown: " + jdkDown + ", scale: " + scale);
                        }
                    });
            }
        });
    }

    @Test
    public void roundScaleIntegerTest() {
        Random rnd = new Random();
        IntStream.range(-18, 0).forEach(scale -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E+" + rnd.nextInt(16))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        double halfUp = roundScale(value, scale, RoundingMode.HALF_UP);
                        double halfDown = roundScale(value, scale, RoundingMode.HALF_DOWN);
                        double halfEven = roundScale(value, scale, RoundingMode.HALF_EVEN);
                        double ceiling = roundScale(value, scale, RoundingMode.CEILING);
                        double floor = roundScale(value, scale, RoundingMode.FLOOR);
                        double up = roundScale(value, scale, RoundingMode.UP);
                        double down = roundScale(value, scale, RoundingMode.DOWN);

                        BigDecimal decimal = BigDecimal.valueOf(value);

                        double jdkHalfUp = decimal.setScale(scale, RoundingMode.HALF_UP).doubleValue();
                        double jdkHalfDown = decimal.setScale(scale, RoundingMode.HALF_DOWN).doubleValue();
                        double jdkHalfEven = decimal.setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
                        double jdkCeiling = decimal.setScale(scale, RoundingMode.CEILING).doubleValue();
                        double jdkFloor = decimal.setScale(scale, RoundingMode.FLOOR).doubleValue();
                        double jdkUp = decimal.setScale(scale, RoundingMode.UP).doubleValue();
                        double jdkDown = decimal.setScale(scale, RoundingMode.DOWN).doubleValue();

                        if (!compare(halfUp, jdkHalfUp)) {
                            fail("Incorrect halfUp: " + value + ", result: " + halfUp +
                                ", jdkHalfUp: " + jdkHalfUp + ", scale: " + scale);
                        }
                        if (!compare(halfDown, jdkHalfDown)) {
                            fail("Incorrect halfDown: " + value + ", result: " + halfDown +
                                ", jdkHalfDown: " + jdkHalfDown + ", scale: " + scale);
                        }
                        if (!compare(halfEven, jdkHalfEven)) {
                            fail("Incorrect halfEven: " + value + ", result: " + halfEven +
                                ", jdkHalfEven: " + jdkHalfEven + ", scale: " + scale);
                        }
                        if (!compare(ceiling, jdkCeiling)) {
                            fail("Incorrect ceiling: " + value + ", result: " + ceiling +
                                ", jdkCeiling: " + jdkCeiling + ", scale: " + scale);
                        }
                        if (!compare(floor, jdkFloor)) {
                            fail("Incorrect floor: " + value + ", result: " + floor +
                                ", jdkFloor: " + jdkFloor + ", scale: " + scale);
                        }
                        if (!compare(up, jdkUp)) {
                            fail("Incorrect up: " + value + ", result: " + up +
                                ", jdkUp: " + jdkUp + ", scale: " + scale);
                        }
                        if (!compare(down, jdkDown)) {
                            fail("Incorrect down: " + value + ", result: " + down +
                                ", jdkDown: " + jdkDown + ", scale: " + scale);
                        }
                    });
            }
        });
    }

    @Test
    public void roundPrecisionSpecificCases() {
        // without rounding for round == 0 or round > 17
        assertTrue(compare(Double.MAX_VALUE,
            roundPrecision(Double.MAX_VALUE, 0, RoundingMode.HALF_UP)));
        assertTrue(compare(Double.MAX_VALUE,
            roundPrecision(Double.MAX_VALUE, 17, RoundingMode.HALF_UP)));

        // not a finite values
        assertTrue(compare(Double.POSITIVE_INFINITY,
            roundPrecision(Double.POSITIVE_INFINITY, 5, RoundingMode.HALF_UP)));
        assertTrue(compare(Double.NEGATIVE_INFINITY,
            roundPrecision(Double.NEGATIVE_INFINITY, 5, RoundingMode.HALF_UP)));

        // simple 0.0 rounding
        IntStream.rangeClosed(1, 17).forEach(round -> {
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.HALF_UP)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.HALF_DOWN)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.HALF_EVEN)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.UP)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.DOWN)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.CEILING)));
            assertTrue(compare(0.0, roundPrecision(0.0, round, RoundingMode.FLOOR)));
        });

        // exceptional cases
        assertThrows(IllegalArgumentException.class, () -> roundPrecision(1.0, -1, RoundingMode.HALF_UP));
        assertThrows(IllegalArgumentException.class, () -> roundPrecision(1.0, -5, RoundingMode.CEILING));
    }

    @Test
    public void roundPrecisionHalfUpDownEvenStrictTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(1, 15).forEach(round -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" : "-") + rnd.nextInt(308))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        double halfUp = roundPrecision(value, round, RoundingMode.HALF_UP);
                        double halfDown = roundPrecision(value, round, RoundingMode.HALF_DOWN);
                        double halfEven = roundPrecision(value, round, RoundingMode.HALF_EVEN);

                        BigDecimal decimal = BigDecimal.valueOf(value);
                        double jdkUp = decimal.round(new MathContext(round, RoundingMode.HALF_UP)).doubleValue();
                        double jdkDown = decimal.round(new MathContext(round, RoundingMode.HALF_DOWN)).doubleValue();
                        double jdkEven = decimal.round(new MathContext(round, RoundingMode.HALF_EVEN)).doubleValue();

                        if (!compare(halfUp, jdkUp)) {
                            fail("Incorrect halfUp: " + value + ", result: " + halfUp +
                                ", jdkUp: " + jdkUp + ", round: " + round);
                        }
                        if (!compare(halfDown, jdkDown)) {
                            fail("Incorrect halfDown: " + value + ", result: " + halfDown +
                                ", jdkDown: " + jdkDown +  ", round: " + round);
                        }
                        if (!compare(halfEven, jdkEven)) {
                            fail("Incorrect halfEven: " + value + ", result: " + halfEven +
                                ", jdkEven: " + jdkEven + ", round: " + round);
                        }
                    });
            }
        });
    }

    @Test
    public void roundPrecisionUpDownCeilFloorStrictTest() {
        Random rnd = new Random();
        IntStream.rangeClosed(1, 16).forEach(round -> {
            for (String precision : FULL_PRECISION_FORMATS) {
                rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                    .mapToObj(value -> String.format(Locale.US, precision, value))
                    .map(value -> value + "E" + (rnd.nextBoolean() ? "+" + rnd.nextInt(17) : "-" + rnd.nextInt(308)))
                    .map(Double::parseDouble)
                    .filter(value -> value != 0)
                    .forEach(value -> {
                        double ceiling = roundPrecision(value, round, RoundingMode.CEILING);
                        double floor = roundPrecision(value, round, RoundingMode.FLOOR);
                        double up = roundPrecision(value, round, RoundingMode.UP);
                        double down = roundPrecision(value, round, RoundingMode.DOWN);

                        BigDecimal decimal = BigDecimal.valueOf(value);
                        double jdkCeiling = decimal.round(new MathContext(round, RoundingMode.CEILING)).doubleValue();
                        double jdkFloor = decimal.round(new MathContext(round, RoundingMode.FLOOR)).doubleValue();
                        double jdkUp = decimal.round(new MathContext(round, RoundingMode.UP)).doubleValue();
                        double jdkDown = decimal.round(new MathContext(round, RoundingMode.DOWN)).doubleValue();

                        if (!compare(ceiling, jdkCeiling)) {
                            fail("Incorrect ceiling: " + value + ", result: " + ceiling +
                                ", jdkCeiling: " + jdkCeiling + ", round: " + round);
                        }
                        if (!compare(floor, jdkFloor)) {
                            fail("Incorrect floor: " + value + ", result: " + floor +
                                ", jdkFloor: " + jdkFloor + ", round: " + round);
                        }
                        if (!compare(up, jdkUp)) {
                            fail("Incorrect up: " + value + ", result: " + up +
                                ", jdkUp: " + jdkUp + ", round: " + round);
                        }
                        if (!compare(down, jdkDown)) {
                            fail("Incorrect down: " + value + ", result: " + down +
                                ", jdkDown: " + jdkDown + ", round: " + round);
                        }
                    });
            }
        });
    }

    @Test
    public void roundPrecisionHalfUpDownEvenRoughTest() {
        int round = 16;
        Random rnd = new Random();
        for (String precision : FULL_PRECISION_FORMATS) {
            rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                .mapToObj(value -> String.format(Locale.US, precision, value))
                .map(value -> value + "E" + (rnd.nextBoolean() ? "+" : "-") + rnd.nextInt(308))
                .map(Double::parseDouble)
                .filter(value -> value != 0)
                .forEach(value -> {
                    double halfUp = roundPrecision(value, round, RoundingMode.HALF_UP);
                    double halfDown = roundPrecision(value, round, RoundingMode.HALF_DOWN);
                    double halfEven = roundPrecision(value, round, RoundingMode.HALF_EVEN);

                    BigDecimal decimal = BigDecimal.valueOf(value);
                    double jdkUp = decimal.round(new MathContext(round, RoundingMode.UP)).doubleValue();
                    double jdkDown = decimal.round(new MathContext(round, RoundingMode.DOWN)).doubleValue();

                    if (!compare(halfUp, jdkUp) && !compare(halfUp, jdkDown)) {
                        fail("Incorrect round 16, halfUp: " + value + ", result: " + halfUp);
                    }
                    if (!compare(halfDown, jdkUp) && !compare(halfDown, jdkDown)) {
                        fail("Incorrect round 16, halfDown: " + value + ", result: " + halfDown);
                    }
                    if (!compare(halfEven, jdkUp) && !compare(halfEven, jdkDown)) {
                        fail("Incorrect round 16, halfEven: " + value + ", result: " + halfDown);
                    }
                });
        }
    }

    private double roundScale(double value, int precision, RoundingMode mode) {
        return FastDoubleUtil.roundScale(value, precision, mode);
    }

    private double roundPrecision(double value, int precision, RoundingMode mode) {
        return FastDoubleUtil.roundPrecision(value, precision, mode);
    }

    private static String formatScale(double value, int scale) {
        return FastDoubleUtil.formatDoubleScale(value, scale);
    }

    private static String formatPrecision(double value) {
        return FastDoubleUtil.formatDoublePrecision(value, 0);
    }

    private static String formatPrecision(double value, int precision) {
        return FastDoubleUtil.formatDoublePrecision(value, precision);
    }

    private void parseDoubles(String str) {
        parseResult(str, FastDoubleUtil::parseDouble);
    }

    private void parseResult(String str, ToDoubleFunction<String> function) {
        double jdk = Double.parseDouble(str);
        double fast = function.applyAsDouble(str);
        if (!compare(jdk, fast)) {
            fail("Not strict parsing str: " + str + ", jdk: " + jdk + ", fast: " + fast);
        }
    }

    private boolean compare(double d1, double d2) {
        return Double.doubleToLongBits(d1) == Double.doubleToLongBits(d2);
    }

    private Stream<String> generateValuesWithExponent() {
        // use different random numbers every launch
        Random rnd = new Random();
        return Arrays.stream(FULL_PRECISION_FORMATS)
            .flatMap(precision -> rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                .mapToObj(value -> String.format(Locale.US, precision, value))
                .map(value -> value + "E" + (rnd.nextBoolean() ? "-" : "") + (rnd.nextInt(309))));
    }

    private Stream<String> generateValuesWithoutExponent(double start, double end) {
        // use different random numbers every launch
        Random rnd = new Random();
        return Arrays.stream(FULL_PRECISION_FORMATS)
            .flatMap(precision -> rnd.doubles(RANDOM_GENERATED_NUMBERS, start, end)
                .mapToObj(value -> (rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, precision, value)));
    }
}
