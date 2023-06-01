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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RangeFilterTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    @Test
    public void testInvalidRangeFilter() {
        assertInvalidFilter("range");
        assertInvalidFilter("range-");
        assertInvalidFilter("rangeABC");
        assertInvalidFilter("rangexAxB");
        assertInvalidFilter("range-A-B--");
        assertInvalidFilter("range-A--B-");
        assertInvalidFilter("rangexAxBxx");
        assertInvalidFilter("range-A-A-");
        assertInvalidFilter("rangexBxAx");
    }

    @Test
    public void testInvalidSymbolRangeFilter() {
        assertInvalidFilter("range-#-%-");
        assertInvalidFilter("range-\u1234-\u5678-");
        assertInvalidFilter("range-A-B-\n");
        assertInvalidFilter("range-A-B-123");
    }

    @Test
    public void testValidSymbolRangeFilter() {
        assertNotNull(filter("range---"));
        assertNotNull(filter("range-A--"));
        assertNotNull(filter("range--B-"));
        assertNotNull(filter("range-A-B-"));
    }

    @Test
    public void testUniversalFilter() {
        assertSame(QDFilter.ANYTHING, filter("range---"));
    }

    @Test
    public void testSimpleRangeFilter() {
        QDFilter f = filter("range-A-B-");
        assertFilter(f, "0", false);

        assertFilter(f, "A", true);
        assertFilter(f, "ABCD", true);
        assertFilter(f, ".A", true);
        assertFilter(f, "./A", true);

        assertFilter(f, "B", false);
        assertFilter(f, "C", false);
    }

    @Test
    public void testSpreadSymbol() {
        QDFilter f = filter("range-A-B-");
        assertFilter(f, "=A", true);
        assertFilter(f, "=-A", true);
        assertFilter(f, "=-2*A", true);
        assertFilter(f, "=-2.2*A", true);
        assertFilter(f, "=-2.2*.A", true);
        assertFilter(f, "=-2.2*./A", true);
        assertFilter(f, "=-2.2*.AC-100.0", true);
        assertFilter(f, "=-2.2*.AP-100.0", true);
    }

    @Test
    public void testLongSymbol() {
        QDFilter f = filter("range-12345678A-12345678B-");
        assertFilter(f, "1234567", false);
        assertFilter(f, "12345678", false);

        assertFilter(f, "12345678A", true);
        assertFilter(f, "12345678AA", true);
        assertFilter(f, ".12345678A", true);
        assertFilter(f, "/12345678A", true);
        assertFilter(f, "=-1.23*12345678A", true);
        assertFilter(f, "12345678AB", true);
        assertFilter(f, "12345678AAA", true);
        assertFilter(f, "12345678AAB", true);

        assertFilter(f, "12345678B", false);
        assertFilter(f, "12345678BA", false);
    }

    @Test
    public void testLeftRangeSymbol() {
        QDFilter f = filter("range--AAA-");
        assertFilter(f, "", true);
        assertFilter(f, "0", true);
        assertFilter(f, "000000000000", true);
        assertFilter(f, "A", true);
        assertFilter(f, "AA", true);
        assertFilter(f, "AA0", true);
        assertFilter(f, "AA0000000000", true);
        assertFilter(f, "AA1", true);
    }

    @Test
    public void testRightRangeSymbol() {
        QDFilter f = filter("range-AAA--");
        assertFilter(f, "AAA", true);
        assertFilter(f, ".AAA", true);
        assertFilter(f, "/AAA", true);
        assertFilter(f, "=AAA", true);
        assertFilter(f, "AAAA", true);
        assertFilter(f, "AAA000000000", true);
        assertFilter(f, ".AAA00000000", true);
        assertFilter(f, "/AAA00000000", true);
        assertFilter(f, "=-2.*AAA00000000", true);
        assertFilter(f, "ZZZZZZZZZZZZ", true);
        assertFilter(f, "aaa", true);
        assertFilter(f, "zzz", true);
    }

    @Test
    public void testAllRangeSymbol() {
        QDFilter f = filter("range---");
        assertFilter(f, "AAA", true);
        assertFilter(f, "@", true);
        assertFilter(f, "{}", true);
        assertFilter(f, "\uFFFF", true);
    }

    @Test
    public void testEncodingSymbol() {
        // Symbols with code larger than 255 should not be "truncated"
        String pseudoAA = "A" + (char) ('A' + 256);

        assertNotEquals(RangeUtil.encodeSymbol("AA"), RangeUtil.encodeSymbol(pseudoAA));

        QDFilter f = filter("range-AA-AB-");
        assertFilter(f, "AA", true);
        assertFilter(f, pseudoAA, false);
    }

    @Test
    public void testOutOfRangeSymbol() {
        QDFilter f = filter("range--A-");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "\u007F", true);
        assertFilter(f, "\u00FF", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeCipher() {
        QDFilter f = filter("range--A-");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeInvalidSymbol() {
        QDFilter f = filter("range-A-a-");
        // Use ASCII-code between A and a
        assertFilter(f, "[^]", false);
    }

    @Test
    public void testSimpleCode() {
        // Symbols with code larger than 255 should not be "truncated"
        QDFilter f = filter("range-A-B-");
        assertFilter(f, "0", false);

        assertFilter(f, "A", true);
        assertFilter(f, "ABCD", true);
        assertFilter(f, ".A", true);
        assertFilter(f, "./A", true);
        assertFilter(f, "=A-B", true);

        assertFilter(f, "B", false);
        assertFilter(f, "C", false);
    }

    @Test
    public void testLongSymbolCode() {
        QDFilter f = filter("range-AAAA00000ZZZ-AABB0000ZZZ-");
        assertFilter(f, "AAAA", false);

        assertFilter(f, "AABB", true);

        assertFilter(f, "AABBB", false);
    }

    // Utility methods

    private static QDFilter filter(String spec) {
        return RangeFilter.valueOf(SCHEME, spec);
    }

    private static void assertFilter(QDFilter filter, String s, boolean condition) {
        int cipher = SCHEME.getCodec().encode(s);
        if (cipher != 0) {
            assertEquals("Symbol " + s + " with filter " + filter, condition, filter.accept(null, null, cipher, null));
        }
        assertEquals("Symbol " + s + " with filter " + filter, condition, filter.accept(null, null, 0, s));
    }

    private static void assertInvalidFilter(String spec) {
        assertThrows(IllegalArgumentException.class, () -> filter(spec));
    }
}
