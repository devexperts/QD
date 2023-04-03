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
package com.devexperts.io.test;

import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import com.devexperts.io.MarshallingException;
import org.junit.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MarshalledTest {

    private static final byte[] SMALL_BYTE_ARRAY = "Some random bytes here".getBytes();
    private static final byte[] LARGE_BYTE_ARRAY = new byte[1500];
    private static final Class<?>[] TYPES = {Void.class, Integer.class, int[].class, Object.class};

    static {
        for (int i = 0; i < LARGE_BYTE_ARRAY.length; i++)
            LARGE_BYTE_ARRAY[i] = SMALL_BYTE_ARRAY[i % SMALL_BYTE_ARRAY.length];
    }

    @Test
    public void testMarshalledError() {
        assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(SMALL_BYTE_ARRAY, Marshaller.forClasses(TYPES)).getObject());
        assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(LARGE_BYTE_ARRAY, Marshaller.forClasses(TYPES)).getObject());
        assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(SMALL_BYTE_ARRAY).getObject());
        assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(LARGE_BYTE_ARRAY).getObject());
    }

    @Test
    public void testNullObject() {
        assertNotNull(Marshalled.NULL);
        assertNull(Marshalled.NULL.getBytes());
        assertNull(Marshalled.NULL.getObject());
        assertSame(Marshalled.NULL, Marshalled.forObject(null));
        assertSame(Marshalled.NULL, Marshalled.forObject(null, Marshaller.SERIALIZATION));
        assertSame(Marshalled.NULL, Marshalled.forBytes(null));
        assertSame(Marshalled.NULL, Marshalled.forBytes(null, Marshaller.SERIALIZATION));
    }

    @Test
    public void testSerialization() {
        Marshalled<Integer> oneFromObject = Marshalled.forObject(1);
        assertEquals((Integer) 1, oneFromObject.getObject());
        byte[] oneBytes = oneFromObject.getBytes();
        Marshalled<Object> oneFromBytes = Marshalled.forBytes(oneBytes);
        assertEquals(1, oneFromBytes.getObject());
        assertArrayEquals(oneBytes, oneFromBytes.getBytes());
        assertEquals(oneFromBytes, oneFromObject);
        assertEquals(oneFromBytes.hashCode(), oneFromObject.hashCode());
        assertEquals(oneFromBytes.toString(), oneFromObject.toString());
    }

    public static final int BIG_SIZE = 1000_000;

    public static class BigObject implements Serializable {
        private static final long serialVersionUID = 0;

        public transient long used;

        private void writeObject(ObjectOutputStream out) throws IOException {
            for (int i = 0; i < BIG_SIZE / 4; i++)
                out.writeInt(i);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            used = getUsed();
            for (int i = 0; i < BIG_SIZE / 4; i++)
                assertEquals(i, in.readInt());
        }
    }

    public static long getUsed() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    @Test
    public void testMemoryDuplication() {
        /*
         * This test attempts to catch duplicate memory allocation during deserialization.
         * Unfortunately there is no easy way to test memory allocation directly.
         * Therefore this test makes several attempts and checks median value of used memory in hope
         * to avoid both garbage collections and concurrent memory allocation by other processes.
         * Nevertheless, this test could be highly unstable. Disable it in this case.
         */
        long[] allocations = new long[20];
        Marshalled original = Marshalled.forObject(new BigObject());
        for (int i = 0; i < allocations.length; i++) {
            long used = getUsed();
            long time = System.currentTimeMillis();
            BigObject result = (BigObject) Marshalled.forBytes(original.getBytes()).getObject();
            allocations[i] = result.used - used;
            //System.out.println(result.used - used + " in " + (System.currentTimeMillis() - time));
        }
        Arrays.sort(allocations);
        assertTrue("deserialization allocates too much memory", allocations[allocations.length / 2] < BIG_SIZE);
    }
}
