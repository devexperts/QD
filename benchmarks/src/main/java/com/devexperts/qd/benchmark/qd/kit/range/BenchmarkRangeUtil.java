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

import com.devexperts.qd.kit.RangeFilter;
import com.devexperts.qd.kit.RangeStriper;

/**
 * Copy-and-paste of {@link com.devexperts.qd.kit.RangeUtil} with adjusted methods
 * for benchmark {@link RangeStriper} implementations.
 */
public class BenchmarkRangeUtil {

    public static final int CODE_LENGTH = 8;

    @FunctionalInterface
    public interface Lambda<T> {
        public char charAt(T object, int index);
    }

    @FunctionalInterface
    public interface CaptureLambda {
        public char charAt(int index);
    }

    private BenchmarkRangeUtil() {} // do not create

    private static final boolean[] RANGE_CHARS = new boolean[128];
    static {
        for (int c = 0; c < RANGE_CHARS.length; c++) {
            RANGE_CHARS[c] = String.valueOf((char) c).matches("[a-zA-Z0-9]");
        }
    }

    public static <T> long encodeSymbol(T object, Lambda<T> s, int from, int to) {
        if (from >= to)
            return 0;

        int length = Math.min(CODE_LENGTH, to - from);
        long code = 0;
        int shift = 64;
        for (int i = 0; i < length; i++) {
            // If character is outside the byte range replace it with the largest positive byte char
            long c = Math.min(0x7F, s.charAt(object, from + i));

            shift -= 8;
            code |= c << shift;
        }
        return code;
    }

    public static long encodeSymbol(CaptureLambda s, int from, int to) {
        if (from >= to)
            return 0;

        int length = Math.min(CODE_LENGTH, to - from);
        long code = 0;
        int shift = 64;
        for (int i = 0; i < length; i++) {
            // If character is outside the byte range replace it with the largest positive byte char
            long c = Math.min(0x7F, s.charAt(from + i));

            shift -= 8;
            code |= c << shift;
        }
        return code;
    }

    /**
     * Returns {@code true} if the given char is a valid range character
     * for the {@link RangeStriper} and {@link RangeFilter}.
     * 
     * @param c character
     * @return {@code true} if the given char is a valid range char
     */
    public static boolean isValidRangeChar(char c) {
        return (c < RANGE_CHARS.length && RANGE_CHARS[c]);
    }

    public static <T> int skipPrefix(T object, Lambda<T> symbol, int from, int to) {
        if (from >= to)
            return from;
        int nonSpreadIdx = (symbol.charAt(object, from) == '=') ? skipSpreadPrefix(object, symbol, from + 1, to) : from;
        return skipUnusedPrefix(object, symbol, nonSpreadIdx, to);
    }

    public static int skipPrefix(CaptureLambda symbol, int from, int to) {
        if (from >= to)
            return from;
        int nonSpreadIdx = (symbol.charAt(from) == '=') ? skipSpreadPrefix(symbol, from + 1, to) : from;
        return skipUnusedPrefix(symbol, nonSpreadIdx, to);
    }

    private static <T> int skipUnusedPrefix(T object, Lambda<T> symbol, int from, int to) {
        for (int i = from; i < to; i++) {
            if (isValidRangeChar(symbol.charAt(object, i)))
                return i;
        }
        return to;
    }

    private static int skipUnusedPrefix(CaptureLambda symbol, int from, int to) {
        for (int i = from; i < to; i++) {
            if (isValidRangeChar(symbol.charAt(i)))
                return i;
        }
        return to;
    }

    private static <T> int skipSpreadPrefix(T object, Lambda<T> symbol, int from, int to) {
        if (from >= to)
            return to;

        // Skip optional sign
        char sign = symbol.charAt(object, from);
        int idx = (sign == '+' || sign == '-') ? from + 1 : from;

        // Skip decimal number (if present)
        int numIdx = indexOfNumber(object, symbol, idx, to);

        // After the number there must be a '*'
        if (numIdx >= 0 && numIdx < to && symbol.charAt(object, numIdx) == '*') {
            idx = numIdx + 1;
        }
        return idx;
    }

    private static int skipSpreadPrefix(CaptureLambda symbol, int from, int to) {
        if (from >= to)
            return to;

        // Skip optional sign
        char sign = symbol.charAt(from);
        int idx = (sign == '+' || sign == '-') ? from + 1 : from;

        // Skip decimal number (if present)
        int numIdx = indexOfNumber(symbol, idx, to);

        // After the number there must be a '*'
        if (numIdx >= 0 && numIdx < to && symbol.charAt(numIdx) == '*') {
            idx = numIdx + 1;
        }
        return idx;
    }

    private static <T> int indexOfNumber(T object, Lambda<T> symbol, int from, int to) {
        boolean seenDigit = false;
        boolean seenDot = false;
        int i = from;

        while (i < to) {
            char c = symbol.charAt(object, i);
            if ('0' <= c && c <= '9') {
                seenDigit = true;
                i++;
            } else if (c == '.') {
                if (seenDot || !seenDigit) {
                    return -1;
                }
                seenDot = true;
                i++;
            } else {
                break;
            }
        }
        return seenDigit ? i : -1;
    }

    private static int indexOfNumber(CaptureLambda symbol, int from, int to) {
        boolean seenDigit = false;
        boolean seenDot = false;
        int i = from;

        while (i < to) {
            char c = symbol.charAt(i);
            if ('0' <= c && c <= '9') {
                seenDigit = true;
                i++;
            } else if (c == '.') {
                if (seenDot || !seenDigit) {
                    return -1;
                }
                seenDot = true;
                i++;
            } else {
                break;
            }
        }
        return seenDigit ? i : -1;
    }
}
