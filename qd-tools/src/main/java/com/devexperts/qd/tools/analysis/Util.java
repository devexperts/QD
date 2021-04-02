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
package com.devexperts.qd.tools.analysis;

import java.io.PrintWriter;
import java.util.Locale;

class Util {
    static final char FIRST_ASCII_CHAR = 32;
    static final char LAST_ASCII_CHAR = 126;

    private Util() {} // do not construct utility class

    static void printHeader(String name, PrintWriter out) {
        out.println();
        out.printf("=== %s ===%n", name.toUpperCase(Locale.US));
    }

    static void printSpaces(int n, PrintWriter out) {
        for (int i = 0; i < n; i++)
            out.print(' ');
    }

    static String formatCount(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    static String formatAverage(double v) {
        return String.format(Locale.US, "%,.1f", v);
    }

    static String formatPercent(double v) {
        return String.format(Locale.US, "%.2f%%", v);
    }

    static boolean isAsciiSymbol(int cipher, String symbol) {
        if (cipher != 0)
            return true;
        if (symbol == null)
            return false;
        for (int i = 0; i < symbol.length(); i++) {
            char c = symbol.charAt(i);
            if (c < FIRST_ASCII_CHAR || c > LAST_ASCII_CHAR)
                return false;
        }
        return true;
    }
}
