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
package com.dxfeed.event.market;

import java.util.Arrays;

class Util {
    private Util() {}

    @SuppressWarnings("unchecked")
    static <T extends Enum> T[] buildEnumArrayByOrdinal(T def, int length) {
        // enums over bit mask shall cover the whole range
        if (Integer.bitCount(length) != 1)
            throw new IllegalArgumentException("length must be power of 2");
        T[] values = (T[]) def.getClass().getEnumConstants();
        T[] result = Arrays.copyOf(values, length); // just new instance with same class and specified length
        Arrays.fill(result, def);
        for (T v : values)
            result[v.ordinal()] = v;
        return result;
    }

    static String encodeChar(char c) {
        // For logging via toString() methods. The encoding is similar to QD QTP TextCoding class.
        // Probably shall be moved somewhere else.
        if (c >= 32 && c <= 126)
            return String.valueOf(c);
        if (c == 0)
            return "\\0";
        return "\\u" + Integer.toHexString(c + 65536).substring(1);
    }

    static void checkChar(char c, int mask, String name) {
        if ((c & ~mask) != 0)
            throwInvalidChar(c, name);
    }

    static void throwInvalidChar(char c, String name) {
        throw new IllegalArgumentException("Invalid " + name + ": " + encodeChar(c));
    }

    static int getBits(int flags, int mask, int shift) {
        return (flags >> shift) & mask;
    }

    static int setBits(int flags, int mask, int shift, int bits) {
        return (flags & ~(mask << shift)) | ((bits & mask) << shift);
    }

    static long getBits(long flags, long mask, int shift) {
        return (flags >> shift) & mask;
    }

    static long setBits(long flags, long mask, int shift, long bits) {
        return (flags & ~(mask << shift)) | ((bits & mask) << shift);
    }

    // This code was copied from com.devexperts.qd.util.ShortString.encode to avoid unnecessary dependency.
    static long encodeShortString(String str) {
        if (str == null)
            return 0;
        int length = str.length();
        if (length > 8)
            throw new IllegalArgumentException("string is too long");
        long code = 0;
        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            if (c > 0xFF)
                throw new IllegalArgumentException("string contains illegal character");
            if (c != 0)
                code = (code << 8) | c;
        }
        return code;
    }

    // This code was copied from com.devexperts.qd.util.ShortString.decode to avoid unnecessary dependency,
    // and does not implement caching.
    static String decodeShortString(long code) {
        if (code == 0)
            return null;
        long reverse = 0; // normalized code in reverse order with zero bytes removed
        int length = 0;
        do {
            byte c = (byte) code;
            if (c != 0) {
                reverse = (reverse << 8) | (c & 0xFF);
                length++;
            }
        } while ((code >>>= 8) != 0);
        char[] c = new char[length];
        for (int i = 0; i < length; i++)
            c[i] = (char) ((int) (reverse >>> (i << 3)) & 0xFF);
        return new String(c, 0, length);
    }
}
