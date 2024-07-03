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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.benchmark.qd.kit.range.BenchmarkRangeUtil.CaptureLambda;
import com.devexperts.qd.benchmark.qd.kit.range.BenchmarkRangeUtil.Lambda;
import com.devexperts.qd.kit.RangeStriper;
import com.dxfeed.api.impl.DXFeedScheme;

import java.util.Arrays;

import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER;
import static com.devexperts.qd.kit.RangeFilter.RANGE_DELIMITER_CHAR;

/**
 * Research and comparison implementations of code-based {@link RangeStriper}.
 */
public abstract class BenchmarkRangeStriper extends RangeStriper {

    public static final DataScheme SCHEME = DXFeedScheme.getInstance();

    private static final int PREFIX_LENGTH = RANGE_STRIPER_PREFIX.length();

    private BenchmarkRangeStriper(DataScheme scheme, String[] ranges, String spec) {
        super(scheme, ranges, spec);
        throw new IllegalStateException("Inherits RangeStriper for inner class visibility only");
    }

    public static SymbolStriper createRangeStriper(String striperImpl, String striperSpec) {
        switch (striperImpl) {
            case "standard":
                return RangeStriper.valueOf(SCHEME, striperSpec);
            case "array":
                return createRangeStriper(striperSpec, ArrayBasedRangeStriper::new);
            case "string":
                return createRangeStriper(striperSpec, StringBasedRangeStriper::new);
            case "lambda":
                return createRangeStriper(striperSpec, LambdaRangeStriper::new);
            case "lambda2":
                return createRangeStriper(striperSpec, MegaMorphLambdaRangeStriper::new);
            case "capture":
                return createRangeStriper(striperSpec, CaptureLambdaRangeStriper::new);
            case "capture2":
                return createRangeStriper(striperSpec, MegaMorphCaptureLambdaRangeStriper::new);
            default:
                throw new IllegalArgumentException("Unknown striper implementation: " + striperImpl);
        }
    }

    @FunctionalInterface
    private interface RangeStriperFactory {
        public SymbolStriper createRangeStriper(DataScheme scheme, String[] ranges, String spec);
    }

    private static SymbolStriper createRangeStriper(String spec, RangeStriperFactory factory) {
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

        return factory.createRangeStriper(SCHEME, ranges, spec);
    }

    // "Array-first" striper where strings are converted to char array
    public static class ArrayBasedRangeStriper extends RangeStriper {
        public ArrayBasedRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            return getStripeIndex(symbol.toCharArray(), 0, symbol.length());
        }

        @Override
        public int getStripeIndex(String symbol) {
            return getStripeIndex(symbol.toCharArray(), 0, symbol.length());
        }
    }

    // "String-first" striper where char arrays are converted to strings
    public static class StringBasedRangeStriper extends RangeStriper {
        public StringBasedRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(char[] symbol, int offset, int length) {
            return super.getStripeIndex(new String(symbol, offset, length));
        }
    }

    // Strings and arrays are abstracted through non-capturing lambda
    public static class LambdaRangeStriper extends RangeStriper {
        public LambdaRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            return getStripeIndex(symbol, String::charAt, 0, symbol.length());
        }

        @Override
        public int getStripeIndex(String symbol) {
            return getStripeIndex(symbol, String::charAt, 0, symbol.length());
        }

        @Override
        public int getStripeIndex(char[] symbol, int offset, int length) {
            return getStripeIndex(symbol, (char[] object, int index) -> object[index], offset, length);
        }

        protected <T> int getStripeIndex(T o, Lambda<T> symbol, int offset, int length) {
            // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

            int from = BenchmarkRangeUtil.skipPrefix(o, symbol, offset, offset + length);
            int to = Math.min(from + codeLength, length);

            long keyCode = BenchmarkRangeUtil.encodeSymbol(o, symbol, from, to);
            int index = Arrays.binarySearch(rangeCodes, keyCode);
            return (index < 0) ? (-index - 1) : (index + 1);
        }
    }

    // Subclass of LambdaRangeStriper to cause troubles
    public static class MegaMorphLambdaRangeStriper extends LambdaRangeStriper {

        public MegaMorphLambdaRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            return getStripeIndex(symbol, (s, i) -> (char) (s.charAt(i) + 10), 0, symbol.length());
        }

        @Override
        public int getStripeIndex(String symbol) {
            return getStripeIndex(symbol, (s, i) -> (char) (s.charAt(i) + 10), 0, symbol.length());
        }

        @Override
        public int getStripeIndex(char[] symbol, int offset, int length) {
            return getStripeIndex(symbol, (arr, i) -> (char) (arr[i] + 10), offset, length);
        }
    }

    // Strings and arrays are abstracted through capturing lambda
    public static class CaptureLambdaRangeStriper extends RangeStriper {
        public CaptureLambdaRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            return getStripeIndex(symbol::charAt, 0, symbol.length());
        }

        @Override
        public int getStripeIndex(String symbol) {
            return getStripeIndex(symbol::charAt, 0, symbol.length());
        }

        @Override
        public int getStripeIndex(char[] symbol, int offset, int length) {
            return getStripeIndex(index -> symbol[index], offset, length);
        }

        protected int getStripeIndex(CaptureLambda symbol, int offset, int length) {
            // No special handling for wildcard, since "*" will be empty symbol after skipping unused prefix

            int from = BenchmarkRangeUtil.skipPrefix(symbol, offset, offset + length);
            int to = Math.min(from + codeLength, length);

            long keyCode = BenchmarkRangeUtil.encodeSymbol(symbol, from, to);
            int index = Arrays.binarySearch(rangeCodes, keyCode);
            return (index < 0) ? (-index - 1) : (index + 1);
        }
    }

    // Subclass of CaptureLambdaRangeStriper to cause troubles
    public static class MegaMorphCaptureLambdaRangeStriper extends CaptureLambdaRangeStriper {
        public MegaMorphCaptureLambdaRangeStriper(DataScheme scheme, String[] ranges, String spec) {
            super(scheme, ranges, spec);
        }

        @Override
        public int getStripeIndex(int cipher, String symbol) {
            return getStripeIndex(index -> (char) (symbol.charAt(index) + 10), 0, symbol.length());
        }

        @Override
        public int getStripeIndex(String symbol) {
            return getStripeIndex(index -> (char) (symbol.charAt(index) + 10), 0, symbol.length());
        }

        @Override
        public int getStripeIndex(char[] symbol, int offset, int length) {
            return getStripeIndex(index -> (char) (symbol[index] + 10), offset, length);
        }
    }
}
