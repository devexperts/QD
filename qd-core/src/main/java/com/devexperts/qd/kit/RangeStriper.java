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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER;
import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER_CHAR;
import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;

/**
 * Strategy that splits symbol universe into stripes by range boundaries.
 *
 * <p>Example: striper {@code byrange-D-M-} splits the symbol universe into 3 stripes described by filters:
 * {@code range--D-}, {@code range-D-M-}, {@code range-M--}.
 *
 * <p>RangeStriper can be matched by the following regex: <b>{@code byrange-(?:([a-zA-Z0-9]{1,8})-)+}</b>,
 * where each group represents a stripe boundary. For performance reasons stripe boundaries must not be
 * greater than 8 characters long.
 *
 * @see RangeFilter
 */
public class RangeStriper implements SymbolStriper {

    public static final String RANGE_STRIPER_PREFIX = "byrange";
    protected static final int PREFIX_LENGTH = RANGE_STRIPER_PREFIX.length();

    protected final String name;
    protected final DataScheme scheme;
    protected final SymbolCodec codec;
    protected final int wildcard;

    protected final int stripeCount;
    protected final int codeLength;
    protected final String[] ranges;
    protected final long[] rangeCodes;

    // Cached filters
    protected final RangeFilter[] filters;

    /**
     * Parses a given specification as range striper for a given scheme.
     *
     * @param scheme the scheme.
     * @param spec the striper specification.
     * @return symbol striper.
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static RangeStriper valueOf(DataScheme scheme, String spec) {
        if (!spec.startsWith(RANGE_STRIPER_PREFIX) || spec.length() < PREFIX_LENGTH + 3)
            throw new FilterSyntaxException("Invalid range striper definition: " + spec);

        if (spec.charAt(PREFIX_LENGTH) != RANGE_DELIMITER_CHAR ||
            spec.charAt(spec.length() - 1) != RANGE_DELIMITER_CHAR)
        {
            throw new FilterSyntaxException("Invalid range striper definition: " + spec);
        }

        String[] ranges = spec.substring(PREFIX_LENGTH + 1, spec.length() - 1).split(RANGE_DELIMITER, -1);
        if (ranges.length < 1)
            throw new FilterSyntaxException("Invalid range striper definition: " + spec);

        return new RangeStriper(scheme, ranges, spec);
    }

    /**
     * Constructs a symbol striper for a given array of symbol ranges.
     * Note that if the array is empty then {@link MonoStriper#INSTANCE} striper will be returned.
     *
     * @param scheme the scheme.
     * @param ranges the list of range boundaries.
     * @return symbol striper.
     * @throws NullPointerException if spec is null.
     * @throws FilterSyntaxException if spec is invalid.
     */
    public static SymbolStriper valueOf(DataScheme scheme, List<String> ranges) {
        Objects.requireNonNull(ranges, "ranges");
        if (ranges.isEmpty())
            return MonoStriper.INSTANCE;

        // Defensive array copy
        String[] rangesArray = ranges.toArray(new String[0]);
        return new RangeStriper(scheme, rangesArray, null);
    }

    protected RangeStriper(DataScheme scheme, String[] ranges, String spec) {
        Objects.requireNonNull(scheme, "scheme");
        this.codeLength = validateRanges(ranges);

        // Constructor invariant: spec must be valid
        assert (spec == null || spec.equals(formatName(ranges)));
        this.name = (spec != null) ? spec : formatName(ranges);
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.wildcard = scheme.getCodec().getWildcardCipher();

        this.stripeCount = ranges.length + 1;
        this.ranges = ranges;
        this.rangeCodes = Stream.of(ranges).mapToLong(RangeUtil::encodeSymbol).toArray();
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
    public String toString() {
        return getName();
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
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

        long keyCode = RangeUtil.skipPrefix(code);
        int index = Arrays.binarySearch(rangeCodes, keyCode);
        return (index < 0) ? (-index - 1) : (index + 1);
    }

    @Override
    public int getStripeIndex(String symbol) {
        // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

        int length = symbol.length();
        int from = RangeUtil.skipPrefix(symbol, length);
        int to = Math.min(from + codeLength, length);

        long keyCode = RangeUtil.encodeSymbol(symbol, from, to);
        int index = Arrays.binarySearch(rangeCodes, keyCode);
        return (index < 0) ? (-index - 1) : (index + 1);
    }

    @Override
    public int getStripeIndex(char[] symbol, int offset, int length) {
        // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

        int from = RangeUtil.skipPrefix(symbol, offset, offset + length);
        int to = Math.min(from + codeLength, length);

        long keyCode = RangeUtil.encodeSymbol(symbol, from, to);
        int index = Arrays.binarySearch(rangeCodes, keyCode);
        return (index < 0) ? (-index - 1) : (index + 1);
    }

    @Override
    public BitSet getIntersectingStripes(QDFilter filter) {
        if (!(filter instanceof RangeFilter))
            return null;

        BitSet result = new BitSet(getStripeCount());
        RangeFilter rangeFilter = (RangeFilter) filter;

        // Index to mark stripes from (inclusive)
        int from = 0;
        if (rangeFilter.leftCode != 0) {
            int index = Arrays.binarySearch(rangeCodes, rangeFilter.leftCode);
            from = (index < 0) ? (-index - 1) : (index + 1);
        }
        // Index to mark stripes to (exclusive)
        int to = getStripeCount();
        if (rangeFilter.rightCode != Long.MAX_VALUE) {
            int index = Arrays.binarySearch(rangeCodes, rangeFilter.rightCode);
            // Right border is exclusive, do not decrease by 1 if not found
            to = (index < 0) ? (-index) : (index + 1);
        }

        result.set(from, to);
        return result;
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (filters[stripeIndex] == null) {
            String left = (stripeIndex == 0) ? "" : ranges[stripeIndex - 1];
            String right = (stripeIndex == stripeCount - 1) ? "" : ranges[stripeIndex];
            filters[stripeIndex] = new RangeFilter(scheme, left, right);
        }
        return filters[stripeIndex];
    }

    // Utility methods

    private static String formatName(String[] ranges) {
        return RANGE_STRIPER_PREFIX + RANGE_DELIMITER + String.join(RANGE_DELIMITER, ranges) + RANGE_DELIMITER;
    }

    protected static int validateRanges(String[] ranges) {
        if (ranges == null || ranges.length == 0)
            throw new FilterSyntaxException("Null or empty ranges");

        int length = 0;
        for (int i = 0; i < ranges.length; i++) {
            validateRange(i, ranges[i]);

            if (i > 0 && ranges[i - 1].compareTo(ranges[i]) >= 0) {
                throw new FilterSyntaxException("Illegal range " + i + ": " + ranges[i] + " <= " + ranges[i - 1]);
            }
            length = Math.max(length, ranges[i].length());
        }
        return length;
    }

    protected static void validateRange(int rangeIndex, String range) {
        if (range == null || range.isEmpty())
            throw new FilterSyntaxException("Null or empty range at index: " + rangeIndex);
        if (range.length() > CODE_LENGTH)
            throw new FilterSyntaxException("Range is too long at index " + rangeIndex + ": " + range);

        for (int i = 0; i < range.length(); i++) {
            char c = range.charAt(i);
            if (!RangeUtil.isValidRangeChar(c)) {
                throw new FilterSyntaxException("Illegal range at index " + rangeIndex + ": " + range);
            }
        }
    }
}
