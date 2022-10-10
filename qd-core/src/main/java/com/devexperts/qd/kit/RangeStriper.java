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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;
import static com.devexperts.qd.kit.RangeUtil.calculateDelimiter;
import static com.devexperts.qd.kit.RangeUtil.compareByCode;
import static com.devexperts.qd.kit.RangeUtil.compareByString;
import static com.devexperts.qd.kit.RangeUtil.encodeSymbol;
import static com.devexperts.qd.kit.RangeUtil.isValidRangeChar;
import static com.devexperts.qd.kit.RangeUtil.skipPrefix;

/**
 * Strategy that splits symbol universe into stripes by specified ranges.
 * <p>Example: striper {@code byrange_E_M_} splits the symbol universe into 3 stripes described by filters:
 * {@code range__E_}, {@code range_E_M_}, {@code range_M__}.
 * <p>Instead of '_' delimiter any character from [_a-zA-Z0-9] can be used as long as it is not present
 * in the range patterns.
 *
 * @see RangeFilter
 */
public abstract class RangeStriper implements SymbolStriper {

    public static final String RANGE_STRIPER_PREFIX = "byrange";
    protected static final int PREFIX_LENGTH = RANGE_STRIPER_PREFIX.length();

    private final String name;
    protected final DataScheme scheme;
    protected final SymbolCodec codec;
    protected final char delimiter;
    protected final int wildcard;

    protected final int stripeCount;
    protected final String[] ranges;

    // Cached filters
    private final RangeFilter[] filters;

    public static RangeStriper valueOf(DataScheme scheme, String spec) {
        if (!spec.startsWith(RANGE_STRIPER_PREFIX) || spec.length() < PREFIX_LENGTH + 3)
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        char delimiter = spec.charAt(PREFIX_LENGTH);
        if (!isValidRangeChar(delimiter))
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        String[] ranges = spec.substring(PREFIX_LENGTH + 1).split("" + delimiter, -1);
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
            return MonoStriper.INSTANCE;

        char delimiter = calculateDelimiter(ranges);
        int codeLength = Arrays.stream(ranges).mapToInt(String::length).max().orElse(0);
        return (codeLength <= CODE_LENGTH) ?
            new CodeRangeStriper(scheme, null, ranges, delimiter, codeLength) :
            new StringRangeStriper(scheme, null, ranges, delimiter);
    }

    protected RangeStriper(DataScheme scheme, String spec, String[] ranges, char delimiter) {
        Objects.requireNonNull(scheme, "scheme");
        validateRanges(ranges, delimiter);

        // Constructor invariant: spec must be valid
        assert(spec == null || spec.equals(calculateSpec(ranges, delimiter)));
        this.name = (spec != null) ? spec : calculateSpec(ranges, delimiter);
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.delimiter = delimiter;
        this.wildcard = scheme.getCodec().getWildcardCipher();

        this.stripeCount = ranges.length + 1;
        this.ranges = ranges;
        this.filters = new RangeFilter[stripeCount];
    }

    @Override
    public String getName() {
        return name;
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

            filters[stripeIndex] = new RangeFilter(scheme, spec, left, right);
        }
        return filters[stripeIndex];
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    protected static class CodeRangeStriper extends RangeStriper {
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
            if (symbol != null)
                return getStripeIndex(symbol);

            // Working with cipher
            long code = scheme.getCodec().decodeToLong(cipher);
            if ((code >>> 56) == '=') {
                // Use ineffective route for spread ciphers since they should never happen,
                // e.g. PentaCodec can only encode cipher for 4-letter spread only ("=A+B")
                return getStripeIndex(scheme.getCodec().decode(cipher));
            }

            long keyCode = skipPrefix(code);
            int index = Arrays.binarySearch(rangeCodes, keyCode);
            return (index < 0) ? (-index - 1) : (index + 1);
        }

        @Override
        public int getStripeIndex(String symbol) {
            // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

            int length = symbol.length();
            int from = skipPrefix(symbol, length);
            int to = Math.min(from + codeLength, length);

            long keyCode = encodeSymbol(symbol, from, to);
            int index = Arrays.binarySearch(rangeCodes, keyCode);
            return (index < 0) ? (-index - 1) : (index + 1);
        }
    }

    protected static class StringRangeStriper extends RangeStriper {
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
            if (symbol != null)
                return getStripeIndex(symbol);

            long code = scheme.getCodec().decodeToLong(cipher);
            if ((code >>> 56) == '=') {
                // Use ineffective route for spread ciphers since they should never happen,
                // e.g. PentaCodec can only encode cipher for 4-letter spread only ("=A+B")
                return getStripeIndex(scheme.getCodec().decode(cipher));
            }

            long keyCode = skipPrefix(code);
            int index = binarySearchByCode(rangeChars, keyCode);
            return (index < 0) ? (-index - 1) : (index + 1);
        }

        @Override
        public int getStripeIndex(String symbol) {
            // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

            int length = symbol.length();
            int from = skipPrefix(symbol, length);

            int index = binarySearchByString(rangeChars, symbol, from);
            return (index < 0) ? (-index - 1) : (index + 1);
        }

        private static int binarySearchByString(char[][] ranges, String symbol, int from) {
            int low = 0;
            int high = ranges.length - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                char[] midVal = ranges[mid];
                int cmp = compareByString(midVal, symbol, from);
                if (cmp < 0)
                    low = mid + 1;
                else if (cmp > 0)
                    high = mid - 1;
                else
                    return mid; // key found
            }
            return -(low + 1);  // key not found.
        }

        private static int binarySearchByCode(char[][] ranges, long symbolCode) {
            int low = 0;
            int high = ranges.length - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                char[] midVal = ranges[mid];
                int cmp = compareByCode(midVal, symbolCode);
                if (cmp < 0)
                    low = mid + 1;
                else if (cmp > 0)
                    high = mid - 1;
                else
                    return mid; // key found
            }
            return -(low + 1);  // key not found.
        }
    }

    // Utility methods

    private static String calculateSpec(String[] ranges, char delimiter) {
        String d = String.valueOf(delimiter);
        return RANGE_STRIPER_PREFIX + d + String.join(d, ranges) + d;
    }

    protected static void validateRanges(String[] ranges, char delimiter) {
        if (ranges == null || ranges.length == 0)
            throw new IllegalArgumentException("Null or empty ranges");

        for (int i = 0; i < ranges.length; i++) {
            validateRange(i, ranges[i], delimiter);

            if (i > 0 && ranges[i - 1].compareTo(ranges[i]) >= 0) {
                throw new IllegalArgumentException("Illegal range " + i + ": " + ranges[i] + " <= " + ranges[i - 1]);
            }
        }
    }

    protected static void validateRange(int rangeIndex, String range, char delimiter) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Null or empty range " + rangeIndex);

        for (int i = 0; i < range.length(); i++) {
            char c = range.charAt(i);
            if (!isValidRangeChar(c)) {
                throw new IllegalArgumentException("Illegal range " + rangeIndex + ": " + range);
            } else if (c == delimiter) {
                throw new IllegalArgumentException("Illegal range " + rangeIndex + ": " + range +
                    " contains delimiter '" + delimiter + "'");
            }
        }
    }
}
