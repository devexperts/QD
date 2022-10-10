/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
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
        assertInvalidFilter("range_");
        assertInvalidFilter("rangeABC");
        assertInvalidFilter("rangexAxB");
        assertInvalidFilter("range_A_B__");
        assertInvalidFilter("range_A__B_");
        assertInvalidFilter("rangexAxBxx");
        assertInvalidFilter("range_A_A_");
        assertInvalidFilter("rangexBxAx");
    }

    @Test
    public void testInvalidSymbolRangeFilter() {
        assertInvalidFilter("range_#_%_");
        assertInvalidFilter("range_\u1234_\u5678_");
        assertInvalidFilter("range_A_B_\n");
        assertInvalidFilter("range_A_B_123");
    }

    @Test
    public void testValidSymbolRangeFilter() {
        assertNotNull(filter("range___"));
        assertNotNull(filter("range_A__"));
        assertNotNull(filter("range__B_"));
        assertNotNull(filter("range_A_B_"));

        // Exotic valid filters
        assertNotNull(filter("range01234567890_________0"));
        assertNotNull(filter("rangeA_________AabcdefghiA"));
        assertNotNull(filter("rangeABCDEFGHIJAabcdefghiA"));
    }

    @Test
    public void testUniversalFilter() {
        assertSame(QDFilter.ANYTHING, filter("range___"));
    }

    @Test
    public void testSimpleRangeFilter() {
        QDFilter f = filter("range_A_B_");
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
        QDFilter f = filter("range_A_B_");
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
        QDFilter f = filter("range_12345678A_12345678B_");
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
        QDFilter f = filter("range__AAA_");
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
        QDFilter f = filter("range_AAA__");
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
        assertFilter(f, "____________", true);
        assertFilter(f, "aaa", true);
        assertFilter(f, "zzz", true);
    }

    @Test
    public void testAllRangeSymbol() {
        QDFilter f = filter("range___");
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

        QDFilter f = filter("range_AA_AB_");
        assertFilter(f, "AA", true);
        assertFilter(f, pseudoAA, false);
    }

    @Test
    public void testOutOfRangeSymbol() {
        QDFilter f = filter("range__A_");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "\u007F", true);
        assertFilter(f, "\u00FF", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeCipher() {
        QDFilter f = filter("range__A_");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeInvalidSymbol() {
        QDFilter f = filter("range_A_a_");
        // Use ASCII-code between A and a
        assertFilter(f, "[^]", false);
    }

    @Test
    public void testSimpleCode() {
        // Symbols with code larger than 255 should not be "truncated"
        QDFilter f = filter("range_A_B_");
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
        QDFilter f = filter("range_AAAA00000ZZZ_AABB0000ZZZ_");
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
