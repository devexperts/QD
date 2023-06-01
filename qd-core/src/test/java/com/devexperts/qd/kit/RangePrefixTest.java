/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RangePrefixTest {

    static Matcher matcher = Pattern.compile(RangeFilter.SYMBOL_PATTERN).matcher("");

    @Test
    public void testSymbolPrefix() {
        assertPrefix(".|A");
        assertPrefix("./|A");
        assertPrefix("$|A");
        assertPrefix("?^%#$^@|A");
        assertPrefix("[]{}()$|A");

        // Tokyo Stock Exchange uses 4-digit numbers as stock symbols
        assertPrefix("|1234");
    }

    @Test
    public void testSymbolNoPrefix() {
        assertPrefix("?^%#$^@|");
        assertPrefix("\u1234\u4321|");
    }

    @Test
    public void testSpreadPrefix() {
        // Valid spreads
        assertPrefix("=|A");
        assertPrefix("=-|A");
        assertPrefix("=+|A");
        assertPrefix("=2*|A");
        assertPrefix("=+2.*|A");
        assertPrefix("=0.2*|A");
        assertPrefix("=-0.2*|A");
        assertPrefix("=-2*|A");
        assertPrefix("=-2.2*|A");
        assertPrefix("=-2.2*|1234");
        assertPrefix("=-2.*|1234");

        // Valid spread with empty symbol
        assertPrefix("=2*|");
        assertPrefix("=-2.5*|");

        // Invalid spreads
        assertPrefix("=-|2.2.2*IBM+MSFT");
        assertPrefix("=|2.2.2*IBM+MSFT");
        assertPrefix("=|2@2*IBM+MSFT");
        assertPrefix("=|2IBM+2*MSFT");
        assertPrefix("=+|2IBM+2*MSFT");
        assertPrefix("=|2.5IBM+2*MSFT");
        assertPrefix("=-2.5*-|MSFT");

        // Japan symbols are numbers
        assertPrefix("=|1234+2345");
        assertPrefix("=|1234-2345");
        assertPrefix("=-|1234-2345");

        assertPrefix("=-1.*|A");
        assertPrefix("=-0.1*|A");
        assertPrefix("=.|1234");
        assertPrefix("=0.12*.|12");
    }

    private static void assertPrefix(String s) {
        int expected = s.indexOf('|');
        if (expected < 0)
            fail("Illegal test string: " + s);

        String clean = s.replaceAll("\\|", "");
        int actual = RangeUtil.skipPrefix(clean, clean.length());
        assertEquals("Invalid prefix skip for " + clean, expected, actual);

        matcher.reset(clean);
        assertTrue(matcher.matches());
        assertEquals("Invalid prefix skip for " + clean, matcher.start(1), actual);
    }
}
