/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import java.util.Arrays;

import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import junit.framework.TestCase;

public class MarshalledTest extends TestCase {
    public void testNullObject() {
        assertTrue(Marshalled.NULL != null);
        assertTrue(Marshalled.NULL.getBytes() == null);
        assertTrue(Marshalled.NULL.getObject() == null);
        assertTrue(Marshalled.NULL == Marshalled.forObject(null));
        assertTrue(Marshalled.NULL == Marshalled.forObject(null, Marshaller.SERIALIZATION));
        assertTrue(Marshalled.NULL == Marshalled.forBytes(null));
        assertTrue(Marshalled.NULL == Marshalled.forBytes(null, Marshaller.SERIALIZATION));
    }

    public void testSerialization() {
        Marshalled<Integer> oneFromObject = Marshalled.forObject(1);
        assertEquals((Integer) 1, oneFromObject.getObject());
        byte[] oneBytes = oneFromObject.getBytes();
        Marshalled<Object> oneFromBytes = Marshalled.forBytes(oneBytes);
        assertEquals(1, oneFromBytes.getObject());
        assertTrue(Arrays.equals(oneBytes, oneFromBytes.getBytes()));
        assertTrue(oneFromBytes.equals(oneFromObject));
        assertEquals(oneFromBytes.hashCode(), oneFromObject.hashCode());
        assertEquals(oneFromBytes.toString(), oneFromObject.toString());
    }
}
