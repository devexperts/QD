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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

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
    }

    @Test
    public void testValidRangeStriper() {
        assertValidStriper("byrange-A-");
        assertValidStriper("byrange-A-B-");
        assertValidStriper("byrange-AAA-AAAA-");
        assertValidStriper("byrange-AAAAAAAAA-AAAAAAAAB-");
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
    public void testLongRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B0000000A-B0000000F-F-");
        assertStriper(striper, "A", 0);
        assertStriper(striper, "B", 0);
        assertStriper(striper, "C", 2);
        assertStriper(striper, "D", 2);
        assertStriper(striper, "F", 3);
        assertStriper(striper, "G", 3);
    }

    @Test
    public void testDenisRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B1234567-");
        assertStriper(striper, "B12345669", 0);
        assertStriper(striper, "B1234567", 1);
        assertStriper(striper, "B1234568", 1);
        assertStriper(striper, "B12345679", 1);
    }

    @Test
    public void testLongRangeLongSymbol() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-B0000000A-B0000000C-B0000000F-F-");
        assertStriper(striper, "AAAAAAAAA", 0);
        assertStriper(striper, "B00000000", 0);
        assertStriper(striper, "B0000000A", 1);
        assertStriper(striper, "B0000000B", 1);
        assertStriper(striper, "B0000000C", 2);
        assertStriper(striper, "B0000000F", 3);
        assertStriper(striper, "BBBBBBBBB", 3);
        assertStriper(striper, "EEEEEEEEE", 3);
        assertStriper(striper, "FFFFFFFFF", 4);
    }

    @Test
    public void testRangeStriperFilterCache() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange-A-B-C-");
        assertNotNull(striper);
        assertSame(striper.getStripeFilter(2), striper.getStripeFilter(2));
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
    }
}
