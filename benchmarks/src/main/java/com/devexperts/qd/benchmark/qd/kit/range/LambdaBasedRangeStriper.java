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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.RangeUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;
import static com.devexperts.qd.kit.RangeUtil.compareByCode;
import static com.devexperts.qd.kit.RangeUtil.compareByString;
import static com.devexperts.qd.kit.RangeUtil.encodeSymbol;
import static com.devexperts.qd.kit.RangeUtil.skipPrefix;

// An attempt to show that capturing lambda is as performant as the custom code.
public abstract class LambdaBasedRangeStriper implements SymbolStriper {

    public static final String RANGE_STRIPER_PREFIX = "byrange";
    protected static final int RANGE_PREFIX_LENGTH = RANGE_STRIPER_PREFIX.length();

    private final String name;
    protected final DataScheme scheme;
    protected final SymbolCodec codec;
    protected final char delimiter;
    protected final int wildcard;

    protected final int stripeCount;
    protected final String[] ranges;

    // Cached filters
    private final LongCodePrefixRangeFilter[] filters;

    public static LambdaBasedRangeStriper valueOf(DataScheme scheme, String spec) {
        if (!spec.startsWith(RANGE_STRIPER_PREFIX) || spec.length() < RANGE_PREFIX_LENGTH + 3)
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        char delimiter = spec.charAt(RANGE_PREFIX_LENGTH);
        if (!RANGE_CHARS[delimiter])
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        String[] ranges = spec.substring(RANGE_PREFIX_LENGTH + 1).split("" + delimiter, -1);
        if (ranges.length < 2 || !ranges[ranges.length - 1].isEmpty())
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        String[] validRanges = Arrays.copyOf(ranges, ranges.length - 1);

        int codeLength = Arrays.stream(ranges).mapToInt(String::length).max().orElse(0);
        return (codeLength <= CODE_LENGTH) ?
            new CodeRangeStriper(scheme, spec, validRanges, delimiter, codeLength) :
        new StringRangeStriper(scheme, spec, validRanges, delimiter);
    }

    public static SymbolStriper valueOf(DataScheme scheme, String... ranges) {
        if (ranges == null || ranges.length == 0)
            throw new IllegalArgumentException("Null or empty ranges");

        char delimiter = calculateDelimiter(ranges);
        int codeLength = Arrays.stream(ranges).mapToInt(String::length).max().orElse(0);
        return (codeLength <= CODE_LENGTH) ?
            new CodeRangeStriper(scheme, null, ranges, delimiter, codeLength) :
            new StringRangeStriper(scheme, null, ranges, delimiter);
    }

    protected LambdaBasedRangeStriper(DataScheme scheme, String spec, String[] ranges, char delimiter) {
        Objects.requireNonNull(scheme, "scheme");
        if (ranges == null || ranges.length == 0)
            throw new IllegalArgumentException("Null or empty ranges");

        for (int i = 0; i < ranges.length; i++) {
            validateRange(i, ranges[i], delimiter);

            if (i > 0 && ranges[i - 1].compareTo(ranges[i]) >= 0) {
                throw new IllegalArgumentException("Illegal range " + i + ": " + ranges[i] + " <= " + ranges[i - 1]);
            }
        }

        // Constructor invariant: spec must be valid
        assert(spec == null || spec.equals(calculateSpec(ranges, delimiter)));
        this.name = (spec != null) ? spec : calculateSpec(ranges, delimiter);
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.delimiter = delimiter;
        this.wildcard = scheme.getCodec().getWildcardCipher();

        this.stripeCount = ranges.length + 1;
        this.ranges = ranges;
        this.filters = new LongCodePrefixRangeFilter[stripeCount];
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public int getStripeCount() {
        return stripeCount;
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (filters[stripeIndex] == null) {
            String left = (stripeIndex == 0) ? "" : ranges[stripeIndex - 1];
            String right = (stripeIndex == stripeCount - 1) ? "" : ranges[stripeIndex];
            String spec = RANGE_STRIPER_PREFIX + delimiter + left + delimiter + right + delimiter;

            filters[stripeIndex] = new LongCodePrefixRangeFilter(scheme, spec, left, right);
        }
        return filters[stripeIndex];
    }

    @Override
    public String toString() {
        return getName();
    }

    protected static class CodeRangeStriper extends LambdaBasedRangeStriper {
        protected final int codeLength;
        protected final long[] rangeCodes;

        protected CodeRangeStriper(DataScheme scheme, String spec, String[] ranges, char delimiter, int codeLength) {
            super(scheme, spec, ranges, delimiter);

            this.codeLength = codeLength;
            this.rangeCodes = Stream.of(ranges).mapToLong(RangeUtil::encodeSymbol).toArray();
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            if (cipher == wildcard)
                return 0;

            long code;
            if (symbol != null) {
                int length = symbol.length();
                int from = skipPrefix(symbol, length);
                int to = Math.min(from + codeLength, length);

                code = encodeSymbol(symbol, from, to);
            } else {
                code = scheme.getCodec().decodeToLong(cipher);
                if ((code >>> 56) == '=') {
                    // Use ineffective route for spread ciphers since they should never happen,
                    // e.g. PentaCodec can only encode cipher for 4-letter spread only ("=A+B")
                    return getStripeIndex(scheme.getCodec().decode(cipher));
                }
                code = skipPrefix(code);
            }

            int index = Arrays.binarySearch(rangeCodes, code);
            return (index < 0) ? (-index - 1) : (index + 1);
        }
    }

    protected static class StringRangeStriper extends LambdaBasedRangeStriper {
        protected char[][] rangeChars;

        protected StringRangeStriper(DataScheme scheme, String spec, String[] ranges, char delimiter) {
            super(scheme, spec, ranges, delimiter);

            this.rangeChars = new char[ranges.length][];
            for (int i = 0; i < ranges.length; i++)
                rangeChars[i] = ranges[i].toCharArray();
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            if (cipher == wildcard)
                return 0;

            int index;
            if (symbol != null) {
                int length = symbol.length();
                int from = skipPrefix(symbol, length);

                // Does capture makes lambda ineffective?
                index = Arrays.binarySearch(rangeChars, symbol,
                    (range, key) -> compareByString((char[]) range, (String) key, from));
            } else {
                long code = scheme.getCodec().decodeToLong(cipher);
                if ((code >>> 56) == '=') {
                    // Use ineffective route for spread ciphers since they should never happen,
                    // e.g. PentaCodec can only encode cipher for 4-letter spread only ("=A+B")
                    return getStripeIndex(scheme.getCodec().decode(cipher));
                }
                long keyCode = skipPrefix(code);

                // Does capture makes lambda ineffective?
                //noinspection ComparatorMethodParameterNotUsed
                index = Arrays.binarySearch(rangeChars, null,
                    (range, ignored) -> compareByCode(range, keyCode));
            }

            return (index < 0) ? (-index - 1) : (index + 1);
        }
    }

    // Utility methods

    private static String calculateSpec(String[] ranges, char delimiter) {
        String d = String.valueOf(delimiter);
        return RANGE_STRIPER_PREFIX + d + String.join(d, ranges) + d;
    }

    protected static void validateRange(int rangeIndex, String range, char delimiter) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Null or empty range " + rangeIndex);

        for (int i = 0; i < range.length(); i++) {
            char c = range.charAt(i);
            if (c >= 128 || !RANGE_CHARS[c]) {
                throw new IllegalArgumentException("Illegal range " + rangeIndex + ": " + range);
            } else if (c == delimiter) {
                throw new IllegalArgumentException("Illegal range " + rangeIndex + ": " + range +
                    " contains delimiter '" + delimiter + "'");
            }
        }
    }

    protected static char calculateDelimiter(String[] ranges) {
        boolean[] seen = new boolean[128];
        // Build histogram of occurrences
        for (String range : ranges) {
            for (int i = 0; i < range.length(); i++) {
                char c = range.charAt(i);
                if (c < 128 && RANGE_CHARS[c]) {
                    seen[c] = true;
                }
            }
        }
        // Find best delimiter using special ordering
        for (char c : DELIMITER_CHARS) {
            if (!seen[c]) {
                return c;
            }
        }
        throw new IllegalArgumentException("Range is too complex to find a delimiter!");
    }

    // Special ordering for delimiters (underscore, lower letters, capital letters, digits)
    protected static final char[] DELIMITER_CHARS =
        "_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    protected static final boolean[] RANGE_CHARS = new boolean[128];
    static {
        for (char c : DELIMITER_CHARS) {
            RANGE_CHARS[c] = true;
        }
    }
}
