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

public class StringUtil {
    private StringUtil() {} // utility only, do not create

    public static int hashCode(char[] chars, int offset, int length) {
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + chars[offset + i];
        return hash;
    }

    public static boolean equals(String str, char[] chars, int offset, int length, int hash) {
        if (str == null || str.hashCode() != hash)
            return false;
        return equals(str, chars, offset, length);
    }

    public static boolean equals(String str, char[] chars, int offset, int length) {
        if (str == null || str.length() != length)
            return false;
        for (int i = 0; i < length; i++)
            if (str.charAt(i) != chars[offset + i])
                return false;
        return true;
    }
}
