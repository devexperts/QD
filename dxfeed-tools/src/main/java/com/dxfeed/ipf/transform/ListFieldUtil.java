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
package com.dxfeed.ipf.transform;

/**
 * Contains utility methods for list base fields (fields containing multiple semicolon separated items).
 * For example 'EXCHANGES' field in ipf with value: 'ARCX;BATS;XNAS'.
 */
class ListFieldUtil {
    private static final int LIST_FIELD_SEPARATOR = ';';
    static final String LIST_FIELD_SEPARATOR_STRING = String.valueOf((char) LIST_FIELD_SEPARATOR);

    private ListFieldUtil() {
    }

    static boolean hasItem(String list, String item) {
        if (item.indexOf(LIST_FIELD_SEPARATOR) >= 0)
            throw new IllegalArgumentException("Item expression expected");
        if (list.length() < item.length() || item.isEmpty())
            return false;

        for (int end = list.length(); end > 0; ) {
            int start = list.lastIndexOf(LIST_FIELD_SEPARATOR, end - 1);
            if (end - start == item.length() + 1 && list.regionMatches(start + 1, item, 0, item.length()))
                return true;
            end = start;
        }
        return false;
    }
}
