/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.glossary;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        return spc == null ? 0 : spc.doubleValue();
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
        String[] symbols = map.keySet().toArray(new String[map.size()]);
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
            sb.append(symbol).append(" ").append(formatDouble(spc));
        }
        return sb.toString();
    }

    private static Map<String, Double> parse(String text) {
        if (text == null || text.isEmpty())
            return Collections.emptyMap();
        Map<String, Double> map = new HashMap<String, Double>();
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
            map.put(text.substring(j + 1, k), Double.valueOf(spc));
        }
        return Collections.unmodifiableMap(map);
    }

    static double parseDouble(String text, int k, int n) {
        if (k >= n)
            return Double.NaN;
        boolean negative = false;
        double amount = 0; // Accumulates mantissa omitting decimal dot.
        double divisor = 0; // Power of 10 for each digit after decimal dot.
        while (k < n) {
            char c = text.charAt(k++);
            if (c == '-') {
                if (negative || amount > 0) // Minus sign is not first character.
                    return Double.NaN;
                negative = true;
                continue;
            }
            if (c == '.') {
                if (divisor > 0) // Second dot detected.
                    return Double.NaN;
                divisor = 1; // Start to compute power of 10.
                continue;
            }
            if (c < '0' || c > '9')
                return Double.NaN;
            amount = amount * 10 + (c - '0');
            divisor = divisor * 10;
        }
        if (divisor > 1)
            amount = amount / divisor;
        if (negative)
            amount = -amount;
        return amount;
    }

    static String formatDouble(double d) {
        if (d == (double) (long) d)
            return Long.toString((long) d);
        if (d >= 0.01 && d < 1000000)
            return Double.toString(d);
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(20);
        nf.setGroupingUsed(false);
        return nf.format(d);
    }
}
