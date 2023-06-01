/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.RangeStriper;
import com.devexperts.qd.kit.RangeUtil;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER;
import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER_CHAR;
import static com.devexperts.qd.kit.RangeUtil.CODE_LENGTH;
import static com.devexperts.qd.kit.RangeUtil.compareByCode;
import static com.devexperts.qd.kit.RangeUtil.compareByString;
import static com.devexperts.qd.kit.RangeUtil.encodeSymbol;
import static com.devexperts.qd.kit.RangeUtil.skipPrefix;

// An attempt to show that capturing lambda is as performant as the custom code.
public abstract class LambdaBasedRangeStriper extends RangeStriper {

    public static final String RANGE_STRIPER_PREFIX = "byrange";
    protected static final int PREFIX_LENGTH = RANGE_STRIPER_PREFIX.length();

    // Cached filters
    private final LongCodePrefixRangeFilter[] filters;

    public static LambdaBasedRangeStriper valueOf(DataScheme scheme, String spec) {
        if (!spec.startsWith(RANGE_STRIPER_PREFIX) || spec.length() < PREFIX_LENGTH + 3)
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        if (spec.charAt(PREFIX_LENGTH) != RANGE_DELIMITER_CHAR ||
            spec.charAt(spec.length() - 1) != RANGE_DELIMITER_CHAR)
        {
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);
        }

        String[] ranges = spec.substring(PREFIX_LENGTH + 1, spec.length() - 1).split(RANGE_DELIMITER, -1);
        if (ranges.length < 1)
            throw new IllegalArgumentException("Invalid range striper definition: " + spec);

        int codeLength = Arrays.stream(ranges).mapToInt(String::length).max().orElse(0);
        return (codeLength <= CODE_LENGTH) ?
            new CodeRangeStriper(scheme, ranges, codeLength, spec) :
            new StringRangeStriper(scheme, spec, ranges);
    }

    public static SymbolStriper valueOf(DataScheme scheme, String... ranges) {
        if (ranges == null || ranges.length == 0)
            throw new IllegalArgumentException("Null or empty ranges");

        int codeLength = Arrays.stream(ranges).mapToInt(String::length).max().orElse(0);
        return (codeLength <= CODE_LENGTH) ?
            new CodeRangeStriper(scheme, ranges, codeLength, null) :
            new StringRangeStriper(scheme, null, ranges);
    }

    protected LambdaBasedRangeStriper(DataScheme scheme, String[] ranges, String spec) {
        super(scheme, ranges, spec);
        this.filters = new LongCodePrefixRangeFilter[stripeCount];
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (filters[stripeIndex] == null) {
            String left = (stripeIndex == 0) ? "" : ranges[stripeIndex - 1];
            String right = (stripeIndex == stripeCount - 1) ? "" : ranges[stripeIndex];
            filters[stripeIndex] = new LongCodePrefixRangeFilter(scheme, left, right, null);
        }
        return filters[stripeIndex];
    }

    protected static class CodeRangeStriper extends LambdaBasedRangeStriper {
        protected final int codeLength;
        protected final long[] rangeCodes;

        protected CodeRangeStriper(DataScheme scheme, String[] ranges, int codeLength, String spec) {
            super(scheme, ranges, spec);

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

        protected StringRangeStriper(DataScheme scheme, String spec, String[] ranges) {
            super(scheme, ranges, spec);

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
}
