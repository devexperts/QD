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

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class AdditionalUnderlyingsTest {

    private static final int RANDOM_GENERATED_NUMBERS = 10_000;

    private static final String[] FULL_PRECISION_FORMATS = {"%.0f", "%.2f", "%.5f", "%.10f", "%.14f", "%.17f"};
    private static final String[] SHORT_PRECISION_FORMATS = {"%.0f", "%.2f", "%.5f", "%.10f"};
    private static final String[] TINY_PRECISION_FORMATS = {"%.0f", "%.2f", "%.5f", "%.7f"};

    @Test
    public void testParseFailCases() {
        assertTrue(compareDoubles(Double.NaN, parseDouble("-")));
        assertTrue(compareDoubles(Double.NaN, parseDouble(".")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("-.")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("+")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("Infinity")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("-Infinity")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("+Infinity")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("¯|_(ツ)_/¯")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("0000.0000.0000")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("0-.0")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("0+0")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("0+")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("E")));
        assertTrue(compareDoubles(Double.NaN, parseDouble("")));
    }

    @Test
    public void testParseSuccessCases() {
        assertTrue(compareDoubles(0.0, parseDouble("0")));
        assertTrue(compareDoubles(-0.0, parseDouble("-.0")));
        assertTrue(compareDoubles(0.0, parseDouble("+.0")));
        assertTrue(compareDoubles(0.0, parseDouble("0.0")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0.0")));
        assertTrue(compareDoubles(0.0, parseDouble("+0.0")));
        assertTrue(compareDoubles(0.0, parseDouble("0.")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0.")));
        assertTrue(compareDoubles(0.0, parseDouble("+0.")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0")));
        assertTrue(compareDoubles(0.0, parseDouble("+0")));
        assertTrue(compareDoubles(0.0, parseDouble("0000.0000")));
        assertTrue(compareDoubles(0.0, parseDouble("+0000.0000")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0000.0000")));
        assertTrue(compareDoubles(0.0, parseDouble("0.0e-10")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0.0E-3")));
        assertTrue(compareDoubles(0.0, parseDouble("0.0e15")));

        assertTrue(compareDoubles(0.5, parseDouble(".5e0")));
        assertTrue(compareDoubles(123.0, parseDouble("123.")));
        assertTrue(compareDoubles(Double.MAX_VALUE, parseDouble(Double.toString(Double.MAX_VALUE))));
        assertTrue(compareDoubles(0.0, parseDouble(Double.toString(Double.MIN_NORMAL))));
        assertTrue(compareDoubles(0.0, parseDouble(Double.toString(Double.MIN_VALUE))));

        assertTrue(compareDoubles(0.0, parseDouble("0.e1")));
        assertTrue(compareDoubles(-0.0, parseDouble("-0.E0")));
        assertTrue(compareDoubles(Double.POSITIVE_INFINITY, parseDouble("1e+310")));
        assertTrue(compareDoubles(Double.NEGATIVE_INFINITY, parseDouble("-1e+309")));
        assertTrue(compareDoubles(-0.0, parseDouble("-1e-325")));
        assertTrue(compareDoubles(0.0, parseDouble("1e-325")));

        assertTrue(compareDoubles(Double.NaN, parseDouble("NaN")));

        assertTrue(compareDoubles(.0000000000000000000001, parseDouble(".0000000000000000000001")));
        // after pow ±22 we can have rounding fails ¯\_(ツ)_/¯
        assertTrue(compareDoubles(1.0000000000000001E-23, parseDouble(".00000000000000000000001")));
    }

    @Test
    public void parseDoubleWithExponentApproximateTest() {
        generateValuesWithExponent(291, true, FULL_PRECISION_FORMATS).forEach(this::parseDoubleApproximateResult);
    }

    @Test
    public void parseDoubleWithExponentExactResultTest() {
        generateValuesWithExactExponent(18, "%.4f").forEach(this::consume);
        generateValuesWithExactExponent(15, "%.7f").forEach(this::consume);
        generateValuesWithExactExponent(11, "%.11f").forEach(this::consume);
        generateValuesWithExactExponent(9, "%.13f").forEach(this::consume);
        generateValuesWithExactExponent(7, "%.14f").forEach(this::consume);
    }

    @Test
    public void parseDoubleWithOutExponentExactResultTest() {
        generateRandomValuesWithoutExponent(0.0000001, 0.00001, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(0.000001, 0.0001, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(0.0001, 0.001, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(0.01, 0.1, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(1, 100, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(100, 10000, SHORT_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(10000, 1000000, TINY_PRECISION_FORMATS).forEach(this::consume);
        generateRandomValuesWithoutExponent(1000000, 100000000, TINY_PRECISION_FORMATS).forEach(this::consume);
    }

    private void consume(String str) {
        double jdk = Double.parseDouble(str);
        double add = AdditionalUnderlyings.parseDouble(str, 0, str.length());
        assertTrue(compareDoubles(jdk, add));
    }

    private double parseDouble(String str) {
        return AdditionalUnderlyings.parseDouble(str, 0, str.length());
    }

    private boolean compareDoubles(double d1, double d2) {
        return Double.doubleToLongBits(d1) == Double.doubleToLongBits(d2);
    }

    private Stream<String> generateValuesWithExactExponent(int exponent, String... precisionFormat) {
        return generateValuesWithExponent(exponent, false, precisionFormat);
    }

    private Stream<String> generateValuesWithExponent(int exponent, boolean randomExponent, String... precisionFormat) {
        // use different random numbers every launch
        Random rnd = new Random();
        return Arrays.stream(precisionFormat)
            .flatMap(precision -> rnd.doubles(RANDOM_GENERATED_NUMBERS, -10, 10)
                .mapToObj(value -> String.format(Locale.US, precision, value))
                .map(value -> value + "E" +
                    (rnd.nextBoolean() ? "-" : "") + (randomExponent ? rnd.nextInt(exponent + 1) : exponent)));
    }

    private Stream<String> generateRandomValuesWithoutExponent(double start, double end, String... precisionFormat) {
        // use different random numbers every launch
        Random rnd = new Random();
        return Arrays.stream(precisionFormat)
            .flatMap(precision -> rnd.doubles(RANDOM_GENERATED_NUMBERS, start, end)
                .mapToObj(value -> (rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, precision, value)));
    }

    private void parseDoubleApproximateResult(String str) {
        double jdk = Double.parseDouble(str);
        double fast = parseDouble(str);

        double diff = Math.abs(jdk - fast);
        double abs = Math.abs(jdk);

        if (jdk == 0) {
            assertTrue(compareDoubles(jdk, fast));
        } else {
            assertTrue(
                "Too large error, origin str: " + str + ", jdk: " + jdk + ", util: " + fast, diff / abs < 1e-15);
        }
    }
}
