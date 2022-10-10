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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RangeStriperTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    @Test
    public void testRangeStriperMono() {
        assertSame(MonoStriper.INSTANCE, RangeStriper.valueOf(SCHEME));
        assertSame(MonoStriper.INSTANCE, RangeStriper.valueOf(SCHEME, (String[]) null));
        assertEquals(MonoStriper.MONO_STRIPER_NAME, RangeStriper.valueOf(SCHEME).toString());
        //noinspection RedundantArrayCreation
        assertSame(MonoStriper.INSTANCE, RangeStriper.valueOf(SCHEME, new String[0]));
    }

    @Test
    public void testInvalidRangeStriper() {
        // Format
        assertInvalidStriper("byrange");
        assertInvalidStriper("byrange_");
        assertInvalidStriper("byrange__");
        assertInvalidStriper("byrangeABC");
        assertInvalidStriper("byrange_A_B");
        assertInvalidStriper("byrange_A_B__");
        assertInvalidStriper("byrangeA_A_");

        // Range order
        assertInvalidStriper("byrange_A_A_");
        assertInvalidStriper("byrange_B_A_");
        assertInvalidStriper("byrange_B_B_");
    }

    @Test
    public void testValidRangeStriper() {
        assertValidStriper("byrange_A_");
        assertValidStriper("byrange_A_B_");
        assertValidStriper("byrangeeAeBe");
        assertValidStriper("byrangexAxBx");
        assertValidStriper("byrangeABACA");
        assertValidStriper("byrange_AAA_AAAA_");
        assertValidStriper("byrange_AAAAAAAAA_AAAAAAAAB_");
    }

    @Test
    public void testCalculateDelimiter() {
        assertEquals('_', RangeUtil.calculateDelimiter(new String[] { "A", "B", "C" }));
        assertEquals('a', RangeUtil.calculateDelimiter(new String[] { "A", "B", "_" }));
        assertEquals('0', RangeUtil.calculateDelimiter(new String[] {
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "_abcdefghijklmnopqrstuvwxyz" }));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotCalculateDelimiter() {
        String[] ranges = {
            "0123456789",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "_abcdefghijklmnopqrstuvwxyz"
        };
        RangeUtil.calculateDelimiter(ranges);
    }

    @Test
    public void testValidFilters() {
        assertStriperFilter("byrange_A_", "__A_", "_A__");
        assertStriperFilter("byrange_A_B_", "__A_", "_A_B_", "_B__");
        assertStriperFilter("byrangeXAXBX", "XXAX", "XAXBX", "XBXX");
        assertStriperFilter("byrange_AAAAA_aaaaa_", "__AAAAA_", "_AAAAA_aaaaa_", "_aaaaa__");
    }

    @Test
    public void testShortRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_B_D_F_");
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
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_B_D_F_");
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
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_B0000000A_B0000000F_F_");
        assertStriper(striper, "A", 0);
        assertStriper(striper, "B", 0);
        assertStriper(striper, "C", 2);
        assertStriper(striper, "D", 2);
        assertStriper(striper, "F", 3);
        assertStriper(striper, "G", 3);
    }

    @Test
    public void testDenisRange() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_B1234567_");
        assertStriper(striper, "B12345669", 0);
        assertStriper(striper, "B1234567", 1);
        assertStriper(striper, "B1234568", 1);
        assertStriper(striper, "B12345679", 1);
    }

    @Test
    public void testLongRangeLongSymbol() {
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_B0000000A_B0000000C_B0000000F_F_");
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
        RangeStriper striper = RangeStriper.valueOf(SCHEME, "byrange_A_B_C_");
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
                RangeStriper.RANGE_STRIPER_PREFIX + filterSpec[i], s.getStripeFilter(i).toString());
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
