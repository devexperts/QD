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
import com.devexperts.qd.SymbolStriper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
}
