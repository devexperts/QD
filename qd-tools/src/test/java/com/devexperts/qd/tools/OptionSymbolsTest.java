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
import org.junit.Before;
import static org.junit.Assert.*;


public class OptionSymbolsTest {
    private NetTest.OptionSymbols symbols;

    @Before
    public void setUp() {
        // Create a new instance before each test
        symbols = new NetTest.OptionSymbols();
    }

    @Test
    public void testBasicValue() {
        // Test parsing a basic value "1000"
        try {
            symbols.parseValue("1000");
            assertEquals(1000, symbols.getTotal());
            assertEquals(1000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull(symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testFixedLength() {
        // Test parsing with fixed symbol length "1000{8}"
        try {
            symbols.parseValue("1000{8}");
            assertEquals(1000, symbols.getTotal());
            assertEquals(1000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(8, symbols.getMinLength());
            assertEquals(8, symbols.getMaxLength());
            assertNull(symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testLengthRange() {
        // Test parsing with symbol length range "1000{3,6}"
        try {
            symbols.parseValue("1000{3,6}");
            assertEquals(1000, symbols.getTotal());
            assertEquals(1000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(3, symbols.getMinLength());
            assertEquals(6, symbols.getMaxLength());
            assertNull( symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testRandomSelectionShortForm() {
        // Test parsing with random selection short form "1000/r10"
        try {
            symbols.parseValue("1000/r10");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull( symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testRandomSelectionLongForm() {
        // Test parsing with random selection long form "1000/random10"
        try {
            symbols.parseValue("1000/random10");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull( symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testSliceSelectionShortForm() {
        // Test parsing with slice selection short form "1000/s10"
        try {
            symbols.parseValue("1000/s10");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertTrue(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull( symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testSliceSelectionLongForm() {
        // Test parsing with slice selection long form "1000/slice10"
        try {
            symbols.parseValue("1000/slice10");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertTrue(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull( symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testIpfPath() {
        // Test parsing with IPF file path "1000@symbols.ipf"
        try {
            symbols.parseValue("1000@symbols.ipf");
            assertEquals(1000, symbols.getTotal());
            assertEquals(1000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertEquals("symbols.ipf", symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testSlashInIpfPath() {
        try {
            symbols.parseValue("1000@./ipfs/my.ipf");
            assertEquals(1000, symbols.getTotal());
            assertEquals(1000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertEquals("./ipfs/my.ipf", symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testPerEntityAndSlashInIpfPath() {
        try {
            symbols.parseValue("1000/10@./ipfs/my.ipf");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertEquals("./ipfs/my.ipf", symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testCombinedOptionsWithSliceAndLength() {
        // Test combined options with slice selection and length range "1000/s10{3,5}"
        try {
            symbols.parseValue("1000/s10{3,5}");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertTrue(symbols.isSliceSelection());
            assertEquals(3, symbols.getMinLength());
            assertEquals(5, symbols.getMaxLength());
            assertNull(symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testCombinedOptionsWithDefaultAndLength() {
        // Test combined options with default selection and length range "1000/10{3,5}"
        try {
            symbols.parseValue("1000/10{3,5}");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(3, symbols.getMinLength());
            assertEquals(5, symbols.getMaxLength());
            assertNull(symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testCombinedOptionsWithRandomAndIpf() {
        // Test combined options with random selection and IPF file "1000/r10@symbols.ipf"
        try {
            symbols.parseValue("1000/r10@symbols.ipf");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertEquals("symbols.ipf", symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyValue() {
        // Test parsing an empty string should keep default values
        try {
            symbols.parseValue("");
            // Should retain default values
            assertEquals(100000, symbols.getTotal());
            assertEquals(100000, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection());
            assertEquals(-1, symbols.getMinLength());
            assertEquals(-1, symbols.getMaxLength());
            assertNull(symbols.getIpfPath());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test(expected = OptionParseException.class)
    public void testNegativeTotalValue() throws OptionParseException {
        // Test negative total value should throw exception
        symbols.parseValue("-1000");
    }

    @Test(expected = OptionParseException.class)
    public void testNegativePerEntityValue() throws OptionParseException {
        // Test negative per-entity value should throw exception
        symbols.parseValue("1000/r-10");
    }

    @Test
    public void testInvalidFormat() {
        assertThrows("InvalidMode", OptionParseException.class, () -> symbols.parseValue("1000/re10"));
        assertThrows("InvalidLengthRange", OptionParseException.class, () -> symbols.parseValue("1000{5,3}"));
        assertThrows("NegativeLength", OptionParseException.class, () -> symbols.parseValue("1000{-5}"));
        assertThrows("PerEntityGreaterThanTotal", OptionParseException.class, () -> symbols.parseValue("1000/2000"));
        assertThrows("InvalidFormat", OptionParseException.class, () -> symbols.parseValue("1000/{10}"));
        assertThrows("SimultaneousLengthAndIpf", OptionParseException.class, () -> symbols.parseValue("1000{5}@symbols.ipf"));
        assertThrows("SlashWithoutPerEntity", OptionParseException.class, () -> symbols.parseValue("1000/"));
        assertThrows("Space", OptionParseException.class, () -> symbols.parseValue(" "));
    }

    @Test
    public void testParseMethodIntegration() {
        // Test integration through the parse method
        String[] args = {"-S", "1000/s10{3,5}"};

        try {
            symbols.parse(0, args);
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertTrue(symbols.isSliceSelection());
            assertEquals(3, symbols.getMinLength());
            assertEquals(5, symbols.getMaxLength());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testBoundaryValues() {
        // Test boundary values
        try {
            // Test with minimum values
            symbols.parseValue("1/1");
            assertEquals(1, symbols.getTotal());
            assertEquals(1, symbols.getPerEntity());

            // Reset for next test
            symbols = new NetTest.OptionSymbols();

            // Test with equal min/max length
            symbols.parseValue("1000{1,1}");
            assertEquals(1, symbols.getMinLength());
            assertEquals(1, symbols.getMaxLength());
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }

    @Test
    public void testDefaultSelectionMode() {
        // Test that default selection mode is random when not specified
        try {
            symbols.parseValue("1000/10");
            assertEquals(1000, symbols.getTotal());
            assertEquals(10, symbols.getPerEntity());
            assertFalse(symbols.isSliceSelection()); // Default is random (false)
        } catch (OptionParseException e) {
            fail("Parsing should succeed: " + e.getMessage());
        }
    }
}
