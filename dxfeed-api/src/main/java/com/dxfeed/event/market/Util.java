/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
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
}
