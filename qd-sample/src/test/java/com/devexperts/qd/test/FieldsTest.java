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
package com.devexperts.qd.test;

import com.devexperts.qd.kit.ByteArrayField;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FieldsTest {
    private final ByteArrayField baf = new ByteArrayField(0, "Test.Test");

    @Test
    public void testByteArray() {
        assertNull(baf.toString(null));
        assertEquals("0x", baf.toString(new byte[0]));
        assertEquals("0x1F", baf.toString(new byte[] { 0x1f }));
        assertEquals("0xABCDEF0123456789", baf.toString(new byte[] {
            (byte) 0xab, (byte) 0xcd, (byte) 0xef, 0x01, 0x23, 0x45, 0x67, (byte) 0x89 }));

        assertEBA(null, baf.parseString(null));
        assertEBA(new byte[0], baf.parseString("0x"));
        assertEBA(new byte[0], baf.parseString("0X"));
        assertEBA(new byte[] { 0x4f }, baf.parseString("0X4F"));
        assertEBA(new byte[] {
            (byte) 0xab, (byte) 0xcd, (byte) 0xef, 0x01, 0x23, 0x45, 0x67, (byte) 0x89 },
            baf.parseString("0xABCDEF0123456789"));

        assertFalse(baf.equals(null, new byte[0]));
        assertFalse(baf.equals(new byte[0], null));
        assertFalse(baf.equals(new byte[0], new byte[] { 1 }));
        assertFalse(baf.equals(new byte[] { 1, 2 }, new byte[] { 2, 3 }));

        assertTrue(baf.equals(new byte[0], ""));
        assertTrue(baf.equals(new byte[0], new char[0]));
        assertTrue(baf.equals("AB", new byte[] { (byte)'A', (byte)'B' }));
        assertTrue(baf.equals(new char[] { '0', '1' , '2'},
            new byte[] { (byte)'0', (byte)'1', (byte)'2' }));
    }

    private void assertEBA(byte[] a, Object o) {
        byte[] b = (byte[]) o;
        assertArrayEquals(a, b);
        assertTrue(baf.equals(a, o));
    }
}
