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

/**
 * Utility class to strip symbols' prefixes for {@link RangeStriper} and {@link RangeFilter}.
 * @see RangeFilter#SYMBOL_PATTERN
 */
public class RangeUtil {

    public static final int CODE_LENGTH = 8;

    private RangeUtil() {} // do not create

    private static final int[] RANGE_INTS = new int[128 / 32];

    static {
        for (int c = 0; c < 128; c++) {
            if (String.valueOf((char) c).matches("[a-zA-Z0-9]")) {
                RANGE_INTS[c >> 5] |= (Integer.MIN_VALUE >>> (c & 31));
            }
        }
    }

    /**
     * Encode the given symbol into the primitive long value.
     * Only first {@link #CODE_LENGTH} characters will be encoded.
     * @param s symbol
     * @return encoded long value
     * @see #encodeSymbol(String, int, int)
     */
    public static long encodeSymbol(String s) {
        return encodeSymbol(s, 0, s.length());
    }

    /**
     * Encode the given symbol into the primitive long value.
     * Only first {@link #CODE_LENGTH} characters within the given bounds will be encoded.
     * @param s symbol
     * @param from the beginning index, inclusive
     * @param to the ending index, exclusive
     * @return encoded long value
     * @see com.devexperts.qd.SymbolCodec#decodeToLong(int)
     */
    public static long encodeSymbol(String s, int from, int to) {
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

    public static long encodeSymbol(char[] s, int from, int to) {
        if (from >= to)
            return 0;

        int length = Math.min(CODE_LENGTH, to - from);
        long code = 0;
        int shift = 64;
        for (int i = 0; i < length; i++) {
            // If character is outside the byte range replace it with the largest positive byte char
            long c = Math.min(0x7F, s[from + i]);

            shift -= 8;
            code |= c << shift;
        }
        return code;
    }

    /**
     * Utility method to compare two strings: one as a char array and another one as a substring.
     *
     * @param s1 string represented as a char array
     * @param s2 string to get a substring from
     * @param s2from index to get a substring from
     * @return the sign of the comparison
     * @see String#compareTo(String)
     */
    public static int compareByString(char[] s1, String s2, int s2from) {
        for (int i = 0, j = s2from; i < s1.length && j < s2.length(); i++, j++) {
            char c1 = s1[i];
            char c2 = s2.charAt(j);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return s1.length - (s2.length() - s2from);
    }

    /**
     * Returns {@code true} if the given char is a valid range character
     * for the {@link RangeStriper} and {@link RangeFilter}.
     * 
     * @param c character
     * @return {@code true} if the given char is a valid range char
     */
    public static boolean isValidRangeChar(char c) {
        return ((c - 128) & (RANGE_INTS[(c >> 5) & 3] << c)) < 0;
    }

    /**
     * Returns encoded string value stripped from unused prefix.
     *
     * @param symbolCode symbol encoded as a long value
     * @return stripped string encoded as a long value
     * @see RangeFilter#SYMBOL_PATTERN
     */
    public static long skipPrefix(long symbolCode) {
        while (symbolCode != 0 && !isValidRangeChar((char) (symbolCode >>> 56))) {
            symbolCode <<= 8;
        }
        return symbolCode;
    }

    /**
     * Returns index in the string of the symbol stripped from unused prefix.
     *
     * @param symbol symbol string
     * @return stripped string encoded as a long value
     * @see RangeFilter#SYMBOL_PATTERN
     */
    public static int skipPrefix(String symbol) {
        return skipPrefix(symbol, symbol.length());
    }

    /**
     * Returns index in the string of the symbol stripped from unused prefix.
     *
     * @param symbol symbol string
     * @param length symbol length
     * @return stripped string encoded as a long value
     * @see RangeFilter#SYMBOL_PATTERN
     */
    public static int skipPrefix(String symbol, int length) {
        if (length == 0)
            return 0;
        int nonSpreadIdx = (symbol.charAt(0) == '=') ? skipSpreadPrefix(symbol, 1, length) : 0;
        return skipUnusedPrefix(symbol, nonSpreadIdx, length);
    }

    public static int skipPrefix(char[] symbol, int from, int to) {
        if (from >= to)
            return from;
        int nonSpreadIdx = (symbol[from] == '=') ? skipSpreadPrefix(symbol, from + 1, to) : from;
        return skipUnusedPrefix(symbol, nonSpreadIdx, to);
    }

    private static int skipUnusedPrefix(String symbol, int from, int to) {
        for (int i = from; i < to; i++) {
            if (isValidRangeChar(symbol.charAt(i)))
                return i;
        }
        return to;
    }

    private static int skipUnusedPrefix(char[] symbol, int from, int to) {
        for (int i = from; i < to; i++) {
            if (isValidRangeChar(symbol[i]))
                return i;
        }
        return to;
    }

    private static int skipSpreadPrefix(String symbol, int from, int to) {
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

    private static int skipSpreadPrefix(char[] symbol, int from, int to) {
        if (from >= to)
            return to;

        // Skip optional sign
        char sign = symbol[from];
        int idx = (sign == '+' || sign == '-') ? from + 1 : from;

        // Skip decimal number (if present)
        int numIdx = indexOfNumber(symbol, idx, to);

        // After the number there must be a '*'
        if (numIdx >= 0 && numIdx < to && symbol[numIdx] == '*') {
            idx = numIdx + 1;
        }
        return idx;
    }

    private static int indexOfNumber(String symbol, int from, int to) {
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

    private static int indexOfNumber(char[] symbol, int from, int to) {
        boolean seenDigit = false;
        boolean seenDot = false;
        int i = from;

        while (i < to) {
            char c = symbol[i];
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
