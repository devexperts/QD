/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeneratorConfigTest {

    @Test
    public void testDefaultValues() {
        Instruments.GeneratorConfig config = new Instruments.GeneratorConfig("");
        assertEquals(-1, config.total);
        assertEquals(-1, config.min);
        assertEquals(-1, config.max);
    }

    @Test
    public void testOnlyTotal() {
        Instruments.GeneratorConfig config = new Instruments.GeneratorConfig("100");
        assertEquals(100, config.total);
        assertEquals(-1, config.min);
        assertEquals(-1, config.max);
    }

    @Test
    public void testTotalWithFixedLength() {
        Instruments.GeneratorConfig config = new Instruments.GeneratorConfig("100{4}");
        assertEquals(100, config.total);
        assertEquals(4, config.min);
        assertEquals(-1, config.max);
    }

    @Test
    public void testTotalWithLengthRange() {
        Instruments.GeneratorConfig config = new Instruments.GeneratorConfig("100{2,6}");
        assertEquals(100, config.total);
        assertEquals(2, config.min);
        assertEquals(6, config.max);
    }

    @Test
    public void testWithoutTotal() {
        Instruments.GeneratorConfig config = new Instruments.GeneratorConfig("{2,6}");
        assertEquals(-1, config.total);
        assertEquals(2, config.min);
        assertEquals(6, config.max);
    }

    @Test
    public void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> new Instruments.GeneratorConfig(" 1000 { 3 , 8 } "));
        assertThrows(IllegalArgumentException.class, () -> new Instruments.GeneratorConfig("100{-5,8}"));
        assertThrows(IllegalArgumentException.class, () -> new Instruments.GeneratorConfig("100{0,8}"));
        assertThrows(IllegalArgumentException.class, () -> new Instruments.GeneratorConfig("100{8,5}"));
        assertThrows(IllegalArgumentException.class, () -> new Instruments.GeneratorConfig("abc{2,6}"));
    }
}
