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

import com.devexperts.util.MathUtil;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Represents a set of additional underlyings for a given option. Each additional underlying
 * has assiciated parameter called SPC (shares per contract) that specifies how many shares
 * of additional underlying are delivered during settlement of the option. In cases when
 * option delivers additional cash the SPC specifies an amount of how much cash is delivered.
 * <p>
 * See {@link #getText} and {@link #getMap} for details about used formats and representations.
 */
public class AdditionalUnderlyings implements Serializable {
    private static final long serialVersionUID = 0;

    // ========== Static API ==========

    /**
     * Empty additional underlyings - it has empty text and empty map.
     */
    public static final AdditionalUnderlyings EMPTY = new AdditionalUnderlyings("");

    private static final AdditionalUnderlyings[] cache = new AdditionalUnderlyings[239];

    /**
     * Returns an instance of additional underlyings for specified textual representation.
     * See {@link #getText} for format specification.
     *
     * @throws IllegalArgumentException if text uses wrong format or contains invalid values
     */
    public static AdditionalUnderlyings valueOf(String text) {
        if (text == null || text.isEmpty())
            return EMPTY;
        int h = Math.abs(text.hashCode() % cache.length);
        AdditionalUnderlyings au = cache[h]; // Atomic read.
        if (au == null || !text.equals(au.text))
            cache[h] = au = new AdditionalUnderlyings(text);
        return au;
    }

    /**
     * Returns an instance of additional underlyings for specified internal representation.
     * See {@link #getMap} for details about internal representation.
     *
     * @throws IllegalArgumentException if data contains invalid values
     */
    public static AdditionalUnderlyings valueOf(Map<String, Double> map) {
        return valueOf(format(map));
    }

    /**
     * Returns SPC for specified underlying symbol or 0 is specified symbol is not found.
     * This method is equivalent to expression "valueOf(text).getSPC(symbol)"
     * except it does not check correctness of format.
     */
    public static double getSPC(String text, String symbol) {
        // This is a garbage-free retrieval of SPC for specified symbol.
        if (text == null || text.isEmpty() || symbol == null || symbol.isEmpty())
            return 0;
        for (int n = text.length(); n > 0; n = text.lastIndexOf(';', n - 1)) {
            int k = text.lastIndexOf(' ', n - 1);
            if (k >= n - 1)
                continue;
            int j = text.lastIndexOf(' ', k - 1) + 1;
            if (k - j != symbol.length() || !text.regionMatches(j, symbol, 0, symbol.length()))
                continue;
            double spc = parseDouble(text, k + 1, n);
            if (!Double.isNaN(spc))
                return spc;
        }
        return 0;
    }

    // ========== Instance API ==========

    private final String text;
    private transient volatile Map<String, Double> map;

    private AdditionalUnderlyings(String text) {
        this.text = text;
        this.map = parse(text);
    }

    /**
     * Returns textual representation of additional underlyings in the format:
     * <pre>
     * TEXT ::= "" | LIST
     * LIST ::= AU | AU "; " LIST
     * AU ::= UNDERLYING " " SPC
     * </pre>
     * Where UNDERLYING is a symbol of underlying instrument and SPC is a number of shares per contract of that underlying.
     * All additional underlyings are listed in the alphabetical order of underlying symbol. In cases when option settles
     * with additional cash the underlying symbol will specify cash symbol and SPC will specify amount of cash.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns internal representation of additional underlyings as a map from underlying symbol to its SPC.
     */
    public Map<String, Double> getMap() {
        Map<String, Double> map = this.map; // Atomic read.
        if (map == null)
            this.map = map = parse(text);
        return map;
    }

    /**
     * Returns SPC for specified underlying symbol or 0 is specified symbol is not found.
     */
    public double getSPC(String symbol) {
        Double spc = getMap().get(symbol);
        return spc == null ? 0 : spc;
    }

    public int hashCode() {
        return text.hashCode();
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof AdditionalUnderlyings && text.equals(((AdditionalUnderlyings) obj).text);
    }

    public String toString() {
        return text;
    }

    // ========== Internal Implementation ==========

    private static String format(Map<String, Double> map) {
        if (map == null || map.isEmpty())
            return "";
        String[] symbols = map.keySet().toArray(new String[0]);
        Arrays.sort(symbols);
        StringBuilder sb = new StringBuilder();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isEmpty())
                throw new IllegalArgumentException("symbol is empty");
            if (symbol.indexOf(' ') >= 0 || symbol.indexOf(';') >= 0)
                throw new IllegalArgumentException("symbol contains prohibited character");
            Double spc = map.get(symbol);
            if (spc == null || Double.isNaN(spc) || Double.isInfinite(spc))
                throw new IllegalArgumentException("SPC is missing or infinite");
            if (sb.length() > 0)
                sb.append("; ");
            sb.append(symbol).append(" ");
            formatDouble(sb, spc);
        }
        return sb.toString();
    }

    private static Map<String, Double> parse(String text) {
        if (text == null || text.isEmpty())
            return Collections.emptyMap();
        Map<String, Double> map = new HashMap<>();
        for (int n = text.length(); n > 0; n = text.lastIndexOf(';', n - 1)) {
            if (n == text.length() - 1 || n < text.length() - 1 && text.charAt(n + 1) != ' ')
                throw new IllegalArgumentException("inappropriate use of separators");
            int k = text.lastIndexOf(' ', n - 1);
            if (k >= n - 1)
                throw new IllegalArgumentException("inappropriate use of separators");
            int j = text.lastIndexOf(' ', k - 1);
            if (j >= k - 1 || j == 0 || j > 0 && text.charAt(j - 1) != ';')
                throw new IllegalArgumentException("inappropriate use of separators");
            if (text.lastIndexOf(';', n - 1) > j)
                throw new IllegalArgumentException("inappropriate use of separators");
            double spc = parseDouble(text, k + 1, n);
            if (Double.isNaN(spc))
                throw new IllegalArgumentException("SPC is missing or infinite");
            map.put(text.substring(j + 1, k), spc);
        }
        return Collections.unmodifiableMap(map);
    }

    static String formatDouble(double value) {
        return MathUtil.formatDoublePrecision(value, MAX_FORMAT_PRECISION);
    }

    static void formatDouble(StringBuilder sb, double value) {
        MathUtil.formatDoublePrecision(sb, value, MAX_FORMAT_PRECISION);
    }

    // ========== Implementation Details of double parsing ==========
    private static final int MAX_POWER = 308;
    private static final int MAX_FORMAT_PRECISION = 15;
    private static final double[] POSITIVE_POWERS_OF_TEN = IntStream.rangeClosed(0, MAX_POWER)
        .mapToDouble(exp -> Double.parseDouble("1E" + exp))
        .toArray();

    /**
     * This is a special implementation that is compatible with the previously used format. Special features:
     * - doesn't throw exceptions
     * - returns Double.NaN for any invalid inputs
     * - acceptable range of exponent [1E-308,1E+308]. All values more or less will be interpreted as 0 or ± infinity
     */
    static double parseDouble(String text, int start, int end) {
        if (start >= end) {
            return Double.NaN;
        }

        double mantissa = 0; // Accumulates mantissa omitting decimal dot.
        boolean floatingPoint = false;
        boolean hasDigit = false;
        int divider = 0;

        char ch = text.charAt(start);
        boolean negative = ch == '-';
        int i = negative || ch == '+' ? start + 1 : start;

        for (; i < end; i++) {
            ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (floatingPoint) {
                    divider++;
                }
                mantissa = mantissa * 10 + (ch - '0');
                hasDigit = true;
            } else if (ch == '.') {
                if (floatingPoint) {
                    return Double.NaN; // Second dot detected.
                }
                floatingPoint = true;
            } else {
                break;
            }
        }

        if (!hasDigit) {
            return Double.NaN;
        }

        int exponent = 0;
        if (i < end) {
            exponent = parseExponent(ch, text, i, end);
            if (exponent == Integer.MAX_VALUE) {
                return Double.NaN;
            }
        }

        return evaluate(negative, mantissa, divider, exponent);
    }

    private static int parseExponent(char ch, CharSequence text, int start, int end) {
        int i = start + 1;
        if ((ch != 'E' && ch != 'e') || i >= end) {
            return Integer.MAX_VALUE; // use for all invalid strings
        }

        int exp = 0;
        ch = text.charAt(i);
        boolean negativeExp = ch == '-';
        if (negativeExp || ch ==  '+') {
            i++;
        }

        for (; i < end; i++) {
            ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                return Integer.MAX_VALUE;
            }
            exp = exp * 10 + (ch - '0');
        }
        return negativeExp ? -exp : exp;
    }

    private static double evaluate(boolean negative, double mantissa, int divider, int exponent) {
        int adjustedExponent = exponent - divider;
        if (adjustedExponent > MAX_POWER || adjustedExponent < -MAX_POWER) {
            if (mantissa == 0 || adjustedExponent < -MAX_POWER) {
                return negative ? -0.0 : +0.0;
            } else {
                return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
        }
        double value = adjustedExponent >= 0 ?
            mantissa * POSITIVE_POWERS_OF_TEN[adjustedExponent] :
            mantissa / POSITIVE_POWERS_OF_TEN[-adjustedExponent];
        return negative ? -value : value;
    }
}
