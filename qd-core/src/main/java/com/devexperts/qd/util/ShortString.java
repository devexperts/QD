/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.util;

import com.devexperts.util.StringCache;

/**
 * Auxiliary class for converting short (up to 8 characters) strings into primitive long values and backwards.
 * The conversion always skips zero characters in the input and never produces them in the output.
 */
public class ShortString {
    /**
     * Encodes string up to 8 characters as a long.
     * Zero characters (code point 0) are skipped, characters larger than 0xFF are prohibited.
     * All other characters are concatenated together to form the code.
     * The first character of string becomes the most significant byte of the resulting code.
     * For example, {@code "A"} encodes as {@code 0x41L}, {@code "AB"} encodes as {@code 0x4142L}, etc.
     * {@code null} and {@code ""} both encode as {@code 0}.
     * @param str the string.
     * @return the code.
     * @throws IllegalArgumentException if length is greater than 8 or string contains character larger than 0xFF
     */
    public static long encode(String str) {
        return str == null ? 0 : encode(str, 0, str.length());
    }

    /**
     * Encodes a portion of a string up to 8 character to a long.
     * This method is equivalent to expression <code>{@link #encode(String) encode}(str.{@link String#substring(int, int) substring}(offset, offset + length))</code>.
     * @param str the string.
     * @param offset the initial offset of the substring.
     * @param length the length of the substring to encode.
     * @return the code.
     * @throws NullPointerException if {@code str} is null.
     * @throws IllegalArgumentException if length is greater than 8 or string contains character larger than 0xFF
     * @see #encode(String)
     */
    public static long encode(String str, int offset, int length) {
        if (length > 8)
            throw new IllegalArgumentException("string is too long");
        long code = 0;
        for (int i = 0; i < length; i++) {
            char c = str.charAt(offset + i);
            if (c > 0xFF)
                throw new IllegalArgumentException("string contains illegal character");
            if (c != 0)
                code = (code << 8) | c;
        }
        return code;
    }

    /**
     * Encodes a portion of a char array up to 8 character to a long.
     * This method is equivalent to expression <code>{@link #encode(String) encode}({@link String#String(char[], int, int) new String}(data, offset, length))</code>.
     * @param data the char array.
     * @param offset the initial offset of the substring.
     * @param length the length of the substring to encode.
     * @return the code.
     * @throws NullPointerException if {@code str} is null.
     * @throws IllegalArgumentException if length is greater than 8 or string contains character larger than 0xFF
     * @see #encode(String)
     */
    public static long encode(char[] data, int offset, int length) {
        if (length > 8)
            throw new IllegalArgumentException("string is too long");
        long code = 0;
        for (int i = 0; i < length; i++) {
            char c = data[offset + i];
            if (c > 0xFF)
                throw new IllegalArgumentException("string contains illegal character");
            if (c != 0)
                code = (code << 8) | c;
        }
        return code;
    }

    /**
     * Encodes a portion of a byte array up to 8 character to a long.
     * This method is equivalent to expression <code>{@link #encode(String) encode}({@link String#String(byte[], int, int, int) new String}(data, 0, offset, length))</code>.
     * @param data the byte array.
     * @param offset the initial offset of the substring.
     * @param length the length of the substring to encode.
     * @return the code.
     * @throws NullPointerException if {@code str} is null.
     * @throws IllegalArgumentException if length is greater than 8
     * @see #encode(String)
     */
    public static long encode(byte[] data, int offset, int length) {
        if (length > 8)
            throw new IllegalArgumentException("string is too long");
        long code = 0;
        for (int i = 0; i < length; i++) {
            char c = (char) (data[offset + i] & 0xFF);
            if (c != 0)
                code = (code << 8) | c;
        }
        return code;
    }

    /**
     * Decodes long code into string.
     * This method skips zeros bytes in the given code and converts remaining bytes
     * into characters from the most-significant byte to the least-significant one.
     * Thus, all of {@code 0x0000000000000041L},
     * {@code 0x0000004100000000L}, and {@code 0x4100000000000000L} decode as {@code "A"}.
     * {@code 0x4142L} decodes as {@code "AB"}, etc.
     * {@code 0} decodes as {@code null}.
     * @param code the code.
     * @return the string.
     */
    public static String decode(long code) {
        return CACHE.getShortString(code);
    }

    private static final int CODE_00 = ('0' << 8) + '0';
    private static final int CODE_99 = ('9' << 8) + '9';

    /**
     * Encodes integer number as a short string - up to 8 characters represented as a long.
     * This method is equivalent to expression <code>{@link #encode(String) encode}({@link Integer#toString(int) Integer.toString}(value))</code>.
     * @param value the number.
     * @return the code.
     * @throws IllegalArgumentException if number is lesser than -9999999 or larger than 99999999
     */
    public static long encodeInt(int value) {
        if (value >= 0 && value <= 9)
            return '0' + value;
        if (value >= 10 && value <= 99)
            return (('0' + value / 10) << 8) + ('0' + value % 10);
        if (value >= -9999999 && value <= 99999999)
            return encode(Integer.toString(value));
        throw new IllegalArgumentException("number is out of range");
    }

    /**
     * Decodes short string into integer.
     * This method is equivalent to expression <code>{@link Integer#parseInt(String) Integer.parseInt}({@link #decode(long) decode}(code))</code>.
     * @param code the code.
     * @return the number.
     * @throws NumberFormatException if the code does not contain a parsable integer
     */
    public static int decodeInt(long code) {
        if (code >= '0' && code <= '9')
            return (int) code - '0';
        if (code >= CODE_00 && code <= CODE_99) {
            int lastChar = (int) code & 0xFF;
            if (lastChar >= '0' && lastChar <= '9')
                return ((int) (code >> 8) - '0') * 10 + (lastChar - '0');
            throw new NumberFormatException(decode(code));
        }
        return Integer.parseInt(decode(code));
    }

    private static final StringCache CACHE = new StringCache(239, 4);

    private ShortString() {}
}
