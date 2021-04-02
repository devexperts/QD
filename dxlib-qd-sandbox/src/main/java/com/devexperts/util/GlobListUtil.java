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
package com.devexperts.util;

import java.util.regex.Pattern;

/**
 * Utility methods to support simple glob-list patterns:
 * <ul>
 *     <li>Use '*' for any sequence of characters (regex equivalent is '.*')
 *     <li>Use ',' for a list of choices (regex equivalent is '|')
 * </ul>
 */
public class GlobListUtil {
    private GlobListUtil() {} // to prevent inheritance and construction of the class

    public static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
            case '*':
                regex.append(".*");
                break;
            case ',':
                regex.append('|');
                break;
            default:
                regex.append(Pattern.quote(glob.substring(i, i + 1)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
