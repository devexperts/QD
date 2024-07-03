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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolStriper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RangeStriperTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    @Test
    public void testRangeStriperMono() {
        assertThrows(NullPointerException.class, () -> RangeStriper.valueOf(SCHEME, (String) null));
        assertThrows(NullPointerException.class, () -> RangeStriper.valueOf(SCHEME, (List<String>) null));
        assertSame(MonoStriper.INSTANCE, RangeStriper.valueOf(SCHEME, new ArrayList<>()));
    }

    @Test
    public void testInvalidRangeStriper() {
        // Format
        assertInvalidStriper("byrange");
        assertInvalidStriper("byrange-");
        assertInvalidStriper("byrange--");
        assertInvalidStriper("byrangeABC");
        assertInvalidStriper("byrange-A-B");
        assertInvalidStriper("byrangeA-B-C-");
        assertInvalidStriper("byrangeAB-C-");
        assertInvalidStriper("byrange-A-B--");
        assertInvalidStriper("byrangeA-A-");

        // Range order
        assertInvalidStriper("byrange-A-A-");
        assertInvalidStriper("byrange-B-A-");
        assertInvalidStriper("byrange-B-B-");

        // Length
        assertInvalidStriper("byrange-A12456678-B12345678-");
    }

    @Test
    public void testValidRangeStriper() {
        assertValidStriper("byrange-A-");
        assertValidStriper("byrange-A-B-");
        assertValidStriper("byrange-AAA-AAAA-");
        assertValidStriper("byrange-AAAAAAAA-AAAAAAAB-");
    }

    @Test
    public void testValidFilters() {
        assertStriperFilter("byrange-A-", "--A-", "-A--");
        assertStriperFilter("byrange-A-B-", "--A-", "-A-B-", "-B--");
        assertStriperFilter("byrange-AAAAA-aaaaa-", "--AAAAA-", "-AAAAA-aaaaa-", "-aaaaa--");
    }

    @Test
    public void testShortRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B-D-F-");
        assertStriper(striper, "A", 0);
        assertStriper(striper, "B", 1);
        assertStriper(striper, "C", 1);
        assertStriper(striper, "D", 2);
        assertStriper(striper, "E", 2);
        assertStriper(striper, "F", 3);
        assertStriper(striper, "G", 3);
    }

    @Test
    public void testShortRangeLongSymbol() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B-D-F-");
        assertStriper(striper, "AAAAAAAAAA", 0);
        assertStriper(striper, "BBBBBBBBBB", 1);
        assertStriper(striper, "CCCCCCCCCC", 1);
        assertStriper(striper, "DDDDDDDDDD", 2);
        assertStriper(striper, "EEEEEEEEEE", 2);
        assertStriper(striper, "FFFFFFFFFF", 3);
        assertStriper(striper, "GGGGGGGGGG", 3);
    }

    @Test
    public void testCustomRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B1234567-");
        assertStriper(striper, "B12345669", 0);
        assertStriper(striper, "B1234567", 1);
        assertStriper(striper, "B1234568", 1);
        assertStriper(striper, "B12345679", 1);
    }

    @Test
    public void testRangeStriperFilterCache() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-A-B-C-");
        assertNotNull(striper);
        assertSame(striper.getStripeFilter(2), striper.getStripeFilter(2));
    }

    @Test
    public void testChar() {
        RangeStriper striper1 = RangeStriper.valueOf(SCHEME, "byrange-S-");
        RangeStriper striper2 = RangeStriper.valueOf(SCHEME, "byrange-K-S-");
        RangeStriper striper3 = RangeStriper.valueOf(SCHEME, "byrange-K-S-U-");

        assertStriper(striper1, "IBM", 0);
        assertStriper(striper1, "EUD/USD", 0);
        assertStriper(striper2, "IBM", 0);
        assertStriper(striper2, "EUD/USD", 0);
        assertStriper(striper3, "IBM", 0);
        assertStriper(striper3, "EUD/USD", 0);
    }

    @Test
    public void testIntersects() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-C-K-P-");

        // Single stripe intersect
        assertIntersect(striper, "range--A-", "1000");
        assertIntersect(striper, "range--C-", "1000");
        assertIntersect(striper, "range-A-B-", "1000");

        assertIntersect(striper, "range-C-K-", "0100");
        assertIntersect(striper, "range-D-E-", "0100");

        assertIntersect(striper, "range-K-P-", "0010");
        assertIntersect(striper, "range-M-O-", "0010");

        assertIntersect(striper, "range-P--", "0001");
        assertIntersect(striper, "range-T--", "0001");
        assertIntersect(striper, "range-T-V-", "0001");

        // Double stripe intersect
        assertIntersect(striper, "range--K-", "1100");
        assertIntersect(striper, "range--E-", "1100");
        assertIntersect(striper, "range-B-E-", "1100");

        assertIntersect(striper, "range-C-M-", "0110");
        assertIntersect(striper, "range-E-M-", "0110");
        assertIntersect(striper, "range-C-P-", "0110");

        assertIntersect(striper, "range-K--", "0011");
        assertIntersect(striper, "range-M--", "0011");
        assertIntersect(striper, "range-M-T-", "0011");

        // Triple stripe intersect
        assertIntersect(striper, "range--P-", "1110");
        assertIntersect(striper, "range--M-", "1110");
        assertIntersect(striper, "range-A-M-", "1110");
        assertIntersect(striper, "range-C--", "0111");
        assertIntersect(striper, "range-D--", "0111");
        assertIntersect(striper, "range-C-T-", "0111");
        assertIntersect(striper, "range-D-T-", "0111");

        // All stripes intersect
        assertIntersect(striper, "range-B--", "1111");
        assertIntersect(striper, "range-A-T-", "1111");
        assertIntersect(striper, "range--T-", "1111");
    }

    @Test
    public void testIntersectsUnknownFilter() {
        SymbolStriper striper = RangeStriper.valueOf(SCHEME, "byrange-C-K-P-");
        assertNull(striper.getIntersectingStripes(QDFilter.ANYTHING));
        assertNull(striper.getIntersectingStripes(QDFilter.NOTHING));
        //TODO Possible place for improvement for pattern filters with fixed prefix
        assertNull(striper.getIntersectingStripes(CompositeFilters.valueOf("A*", SCHEME)));
    }

    // Utility methods

    private static void assertInvalidStriper(String spec) {
        assertThrows(IllegalArgumentException.class, () -> RangeStriper.valueOf(SCHEME, spec));
    }

    private static void assertValidStriper(String spec) {
        RangeStriper rangeStriper = RangeStriper.valueOf(SCHEME, spec);
        assertEquals(spec, rangeStriper.toString());
    }

    private static void assertStriperFilter(String spec, String... filterSpec) {
        RangeStriper s = RangeStriper.valueOf(SCHEME, spec);

        assertEquals(filterSpec.length, s.getStripeCount());
        for (int i = 0; i < filterSpec.length; i++) {
            assertEquals("Invalid filter " + i + " of " + s,
                RangeFilter.RANGE_FILTER_PREFIX + filterSpec[i], s.getStripeFilter(i).toString());
        }
    }

    private static void assertStriper(RangeStriper striper, String s, int index) {
        int cipher = SCHEME.getCodec().encode(s);
        if (cipher != 0) {
            assertEquals("Index for symbol " + s + " in " + striper, index, striper.getStripeIndex(cipher, null));
        }
        assertEquals("Index for symbol " + s + " in " + striper, index, striper.getStripeIndex(0, s));
        assertEquals("Index for symbol " + s + " in " + striper, index,
            striper.getStripeIndex(s.toCharArray(), 0, s.length()));
        for (int i = 0; i < striper.getStripeCount(); i++) {
            assertEquals(i == index, striper.getStripeFilter(i).accept(null, null, cipher, s));
        }
    }

    private static void assertIntersect(RangeStriper striper, String filter, String stripes) {
        BitSet intersect = striper.getIntersectingStripes(RangeFilter.valueOf(SCHEME, filter));

        if (stripes.length() != striper.getStripeCount())
            throw new IllegalArgumentException();

        for (int i = 0; i < stripes.length(); i++) {
            if (stripes.charAt(i) == '1') {
                assertTrue(filter + " should intersect with " + striper.getStripeFilter(i), intersect.get(i));
            } else {
                assertFalse(filter + " should not intersect with " + striper.getStripeFilter(i), intersect.get(i));
            }
        }
    }
}
