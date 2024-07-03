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

import java.util.BitSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HashFilterStriperTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    // Striper test methods

    @Test(expected = IllegalArgumentException.class)
    public void testHashStriperInvalidCountNotPowerOfTwo() {
        HashStriper.valueOf(SCHEME, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHashStriperInvalidCountZero() {
        HashStriper.valueOf(SCHEME, 0);
    }

    @Test
    public void testHashStriperMono() {
        assertSame(MonoStriper.INSTANCE, HashStriper.valueOf(SCHEME, 1));
        assertEquals(MonoStriper.MONO_STRIPER_NAME, HashStriper.valueOf(SCHEME, 1).toString());
    }

    @Test
    public void testHashStriper() {
        SymbolStriper hash = HashStriper.valueOf(SCHEME, 16);
        assertNotNull(hash);
        assertEquals(16, hash.getStripeCount());
        assertEquals("byhash16", hash.getName());
    }

    @Test
    public void testHashStriperIndex() {
        SymbolStriper hash = HashStriper.valueOf(SCHEME, 16);
        assertNotNull(hash);
        assertEquals(14, hash.getStripeIndex(SCHEME.getCodec().encode("IBM"), null));
        assertEquals(14, hash.getStripeIndex(0, "IBM"));
    }

    @Test
    public void testHashStriperFilterCache() {
        SymbolStriper hash = HashStriper.valueOf(SCHEME, 16);
        assertNotNull(hash);
        assertSame(hash.getStripeFilter(4), hash.getStripeFilter(4));
    }

    // Filter test methods

    @Test(expected = IllegalArgumentException.class)
    public void testHashFilterInvalid() {
        HashFilter.valueOf(SCHEME, "hash");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHashFilterNotANumber() {
        HashFilter.valueOf(SCHEME, "hashAofB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHashFilterInvalidCount() {
        HashFilter.valueOf(SCHEME, "hash5of7");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHashFilterInvalidIndex() {
        HashFilter.valueOf(SCHEME, "hash16of16");
    }

    @Test
    public void testHashFilter() {
        SymbolStriper hash = HashStriper.valueOf(SCHEME, 16);
        QDFilter filter = hash.getStripeFilter(14);
        assertNotNull(filter);
        assertEquals("hash14of16", filter.toString());

        assertTrue(filter.accept(null, null, SCHEME.getCodec().encode("IBM"), null));
        assertTrue(filter.accept(null, null, 0, "IBM"));
    }

    @Test
    public void testHashFilterFormatting() {
        assertEquals("hash0001of1024", HashFilter.formatName(1, 1024));

        HashStriper striper = (HashStriper) HashStriper.valueOf(SCHEME, 1024);
        assertEquals("hash0001of1024", new HashFilter(striper, 1).toString());
    }

    @Test
    public void testHashDifferentFormatting() {
        HashFilter f1 = (HashFilter) HashFilter.valueOf(SCHEME, "hash0of1024");
        assertNotNull(f1);
        assertEquals("hash0of1024", f1.toString());

        HashFilter f2 = (HashFilter) HashFilter.valueOf(SCHEME, "hash00of1024");
        assertNotNull(f2);
        assertEquals("hash00of1024", f2.toString());

        HashFilter f3 = (HashFilter) HashFilter.valueOf(SCHEME, "hash000of1024");
        assertNotNull(f3);
        assertEquals("hash000of1024", f3.toString());
    }

    @Test
    public void testHashIntersectsFilter() {
        SymbolStriper s1 = HashStriper.valueOf(SCHEME, 8);
        SymbolStriper s2 = HashStriper.valueOf(SCHEME, 64);
        int ratio = s2.getStripeCount() / s1.getStripeCount();

        // Larger striper vs smaller
        for (int i = 0; i < s1.getStripeCount(); i++) {
            HashFilter f1 = (HashFilter) s1.getStripeFilter(i);
            BitSet set = s2.getIntersectingStripes(f1);

            for (int j = 0; j < s2.getStripeCount(); j++) {
                HashFilter f2 = (HashFilter) s2.getStripeFilter(j);
                if (j / ratio == i) {
                    assertTrue(f1 + " should intersect with " + f2, set.get(j));
                } else {
                    assertFalse(f1 + " should not intersect with " + f2, set.get(j));
                }
            }
        }

        // Smaller striper vs larger
        for (int j = 0; j < s2.getStripeCount(); j++) {
            HashFilter f2 = (HashFilter) s2.getStripeFilter(j);
            BitSet set = s1.getIntersectingStripes(f2);

            for (int i = 0; i < s1.getStripeCount(); i++) {
                HashFilter f1 = (HashFilter) s1.getStripeFilter(i);
                if (j / ratio == i) {
                    assertTrue(f1 + " should intersect with " + f2, set.get(i));
                } else {
                    assertFalse(f1 + " should not intersect with " + f2, set.get(i));
                }
            }
        }
    }

    @Test
    public void testHashIntersectsUnknownFilter() {
        SymbolStriper striper = HashStriper.valueOf(SCHEME, 8);
        assertNull(striper.getIntersectingStripes(QDFilter.ANYTHING));
        assertNull(striper.getIntersectingStripes(QDFilter.NOTHING));
        assertNull(striper.getIntersectingStripes(CompositeFilters.valueOf("A*", SCHEME)));
    }
}
