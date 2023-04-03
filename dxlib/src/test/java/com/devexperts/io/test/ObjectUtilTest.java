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

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.IOUtil;
import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import com.devexperts.io.MarshallingException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ObjectUtilTest {

    @Test
    public void testReadWrite() throws IOException {
        checkReadWrite(null);
        checkReadWrite(1.0);
        checkReadWrite("test");
        checkReadWrite(bigObject(1));
        checkReadWrite(bigObject(2));
    }

    @Test
    public void testToFromBytes() throws IOException {
        checkToFromBytes(null);
        checkToFromBytes(1.0);
        checkToFromBytes("test");
        checkToFromBytes(bigObject(1));
        checkToFromBytes(bigObject(2));
    }

    private static Object bigObject(long seed) {
        Random r = new Random(seed);
        int n = r.nextInt(10000) + 10000;
        ArrayList<Object> result = new ArrayList<Object>(n);
        for (int i = 0; i < n; i++)
            switch (r.nextInt(3)) {
            case 0:
                result.add(r.nextInt(1000));
                break;
            case 1:
                result.add((long) r.nextInt(1000));
                break;
            case 2:
                result.add(String.valueOf(r.nextInt(1000)));
                break;
            }
        return result;
    }

    private static void checkReadWrite(Object value) throws IOException {
        byte[] bytes = writeToBytes(value);
        Object result = readFromBytes(bytes);
        assertEquals(value, result);
    }

    private static void checkToFromBytes(Object value) throws IOException {
        byte[] bytes = IOUtil.objectToBytes(value);
        Object result = IOUtil.bytesToObject(bytes);
        assertEquals(value, result);
    }

    private static Object readFromBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        return IOUtil.readObject(dis);
    }

    private static byte[] writeToBytes(Object value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        IOUtil.writeObject(dos, value);
        return baos.toByteArray();
    }

    // ============================================================

    // makes sure that stream header is written, but ignore version (-1s)
    static final int[] INT1234_BYTES = new int[] {
        0x80, 0x51, 0xac, 0xed, -1, -1, 0x73, 0x72, 0x0, 0x11, 0x6a, 0x61, 0x76, 0x61, 0x2e, 0x6c, 0x61, 0x6e, 0x67,
        0x2e, 0x49, 0x6e, 0x74, 0x65, 0x67, 0x65, 0x72, 0x12, 0xe2, 0xa0, 0xa4, 0xf7, 0x81, 0x87, 0x38, 0x2, 0x0,
        0x1, 0x49, 0x0, 0x5, 0x76, 0x61, 0x6c, 0x75, 0x65, 0x78, 0x72, 0x0, 0x10, 0x6a, 0x61, 0x76, 0x61, 0x2e, 0x6c,
        0x61, 0x6e, 0x67, 0x2e, 0x4e, 0x75, 0x6d, 0x62, 0x65, 0x72, 0x86, 0xac, 0x95, 0x1d, 0xb, 0x94, 0xe0, 0x8b,
        0x2, 0x0, 0x0, 0x78, 0x70, 0x0, 0x0, 0x4, 0xd2
    };
    static final int INT1234_LEN = INT1234_BYTES.length;
    static final int DATA_OFFSET = 2;

    @Test
    public void testCompatibleWrite() throws IOException {
        byte[] bytes = writeToBytes(1234);
        int n = bytes.length;
        assertEquals(n, INT1234_LEN);
        for (int i = 0; i < n; i++) {
            if (INT1234_BYTES[i] >= 0)
                assertEquals(INT1234_BYTES[i], 0xff & bytes[i]);
        }
    }

    @Test
    public void testCompatibleToBytes() throws IOException {
        byte[] bytes = IOUtil.objectToBytes(1234);
        int n = bytes.length;
        assertEquals(n, INT1234_LEN - DATA_OFFSET);
        for (int i = 0; i < n; i++) {
            if (INT1234_BYTES[i + DATA_OFFSET] >= 0)
                assertEquals(INT1234_BYTES[i + DATA_OFFSET], 0xff & bytes[i]);
        }
    }

    // ============================================================

    @Test
    public void testObjectsToFromBytes() throws IOException {
        checkObjectsToFromBytes(new Class[] {void.class}, new Object[] {null});
        checkObjectsToFromBytes(new Class[0], new Object[0]);

        List<Integer> list = new ArrayList<Integer>();
        list.add(-1);
        list.add(null);
        list.add(Integer.MAX_VALUE);
        checkObjectsToFromBytes(new Class[] {
            int.class, List.class, byte[][].class, String[].class, String.class, Long.class, Date.class,
            Boolean[][][].class
        }, new Object[] {
            123, list, new byte[][] {new byte[] {123, 45}, null, new byte[0]}, new String[] {"abc", null, "def"},
            null, 1234567890123456789L, new Date(564623246),
            new Boolean[][][] {null, new Boolean[][] {null, new Boolean[] {true, false, null}}}
        });

        checkObjectsToFromBytes(new Class[] {int.class, String.class, boolean.class},
            new Object[] {1234567890, "Hello! =)", true}); // only "primitives"
        checkObjectsToFromBytes(new Class[] {Integer.class, File.class, Date.class, Object.class},
            new Object[] {1234567890, new File("some_file.dat"), new Date(7777777), list}); // only objects
    }

    @Test
    public void testObjectsToFromBytesFailures() {
        checkToFromTypesMismatch(new Class[] {Integer.class}, new Class[] {int.class}, new Object[] {123});
        checkToFromTypesMismatch(new Class[] {long.class}, new Class[] {int.class}, new Object[] {12345678901234567L});
        checkToFromTypesMismatch(new Class[] {boolean[][].class}, new Class[] {Object[].class},
            new Object[] {new boolean[0][0]});
        checkToFromTypesMismatch(new Class[] {Object.class}, new Class[] {String.class}, new Object[] {"hello"});
        checkIllegalObjectsToBytesArguments(new Class[] {String.class}, new Object[] {5});
        checkIllegalObjectsToBytesArguments(new Class[] {Object[][].class}, new Object[] {new Object[2]});
        checkIllegalObjectsToBytesArguments(new Class[] {int.class, int.class}, new Object[] {1, 2, 3});
        checkIllegalObjectsToBytesArguments(new Class[] {int.class, Date.class}, new Object[] {new Date(123), 123});
    }

    private static void checkIllegalObjectsToBytesArguments(Class[] types, Object[] objects) {
        assertThrows(MarshallingException.class,
            () -> Marshalled.forObject(objects, Marshaller.forClasses(types)).getBytes());
    }

    private static void checkToFromTypesMismatch(Class[] writeTypes, Class[] readTypes, Object[] objects) {
        assertThrows(MarshallingException.class, () -> {
            byte[] bytes = Marshalled.forObject(objects, Marshaller.forClasses(writeTypes)).getBytes();
            Marshalled.forBytes(bytes, Marshaller.forClasses(readTypes)).getObject();
        });
    }

    private static void checkObjectsToFromBytes(Class[] types, Object[] objects) {
        assertEquals(types.length, objects.length);
        byte[] bytes = Marshalled.forObject(objects, Marshaller.forClasses(types)).getBytes();
        Object[] results = (Object[]) Marshalled.forBytes(bytes, Marshaller.forClasses(types)).getObject();
        for (int i = 0; i < types.length; i++) {
            assertDeepEquals(objects[i], results[i]);
        }
    }

    public static void assertDeepEquals(Object expected, Object actual) {
        if (expected == actual)
            return;
        Class<?> ec = expected.getClass();
        Class<?> ac = actual.getClass();
        if (ec.isArray()) {
            if (!ac.isArray())
                fail("Array expected");
            int n = Array.getLength(expected);
            if (n != Array.getLength(actual))
                fail("Array length mismatch");
            for (int i = 0; i < n; i++)
                assertDeepEquals(Array.get(expected, i), Array.get(actual, i));
        } else {
            assertEquals(expected, actual);
        }
    }

    // ============================================================

    @Test
    public void testCompression() throws Exception {
        compressionTest(false);
        compressionTest(true);
    }

    private void compressionTest(boolean compression) throws Exception {
        boolean oldCompression = IOUtil.isCompressionEnabled();
        IOUtil.setCompressionEnabled(compression);
        System.out.println("compression = " + compression + ", old = " + oldCompression);

        HashMap<Object, Object> original = new HashMap<Object, Object>(System.getProperties());
        for (int i = 0; i < 5000; i++)
            original.put(i, i);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.flush();
        byte[] bytes = baos.toByteArray();
        byte[] ioBytes = IOUtil.objectToBytes(original);
        print(bytes, "bytes");
        print(ioBytes, "ioBytes");
        if (!compression)
            assertArrayEquals(bytes, ioBytes);
        assertEquals(original, IOUtil.bytesToObject(bytes));
        assertEquals(original, IOUtil.bytesToObject(ioBytes));

        Class[] types = new Class[] {int.class, original.getClass()};
        Object[] params = new Object[] {0x12345678, original};
        byte[] declared = Marshalled.forObject(params, Marshaller.forClasses(types)).getBytes();
        byte[] declared2 = IOUtil.deflate(declared, 1);
        print(declared, "declared");
        print(declared2, "declared2");
        assertDeepEquals(params,
            Marshalled.forBytes(declared, Marshaller.forClasses(int.class, original.getClass())).getObject());
        assertDeepEquals(params,
            Marshalled.forBytes(declared2, Marshaller.forClasses(int.class, original.getClass())).getObject());

        baos = new ByteArrayOutputStream();
        IOUtil.writeObject(new DataOutputStream(baos), original);
        byte[] written = baos.toByteArray();
        baos = new ByteArrayOutputStream();
        IOUtil.writeByteArray(new DataOutputStream(baos), ioBytes);
        assertTrue(Arrays.equals(baos.toByteArray(), written));

        byte[] deflated = IOUtil.deflate(bytes, 1);
        byte[] deflated2 = IOUtil.deflate(deflated, 1);
        print(deflated, "deflated");
        print(deflated2, "deflated2");
        assertFalse(Arrays.equals(bytes, deflated));
        assertFalse(Arrays.equals(bytes, deflated2));
        assertFalse(Arrays.equals(deflated, deflated2));
        assertTrue(Arrays.equals(bytes, IOUtil.inflate(deflated)));
        assertTrue(Arrays.equals(deflated, IOUtil.inflate(deflated2)));
        assertTrue(Arrays.equals(bytes, IOUtil.decompress(deflated)));
        assertTrue(Arrays.equals(bytes, IOUtil.decompress(deflated2)));

        byte[] compressed = IOUtil.compress(bytes);
        byte[] compressed2 = IOUtil.compress(compressed);
        print(compressed, "compressed");
        print(compressed2, "compressed2");
        assertTrue(bytes == compressed != compression);
        assertTrue(Arrays.equals(bytes, compressed) != compression);
        assertTrue(compressed == compressed2);
        assertTrue(Arrays.equals(bytes, IOUtil.decompress(compressed)));
        assertTrue(Arrays.equals(bytes, IOUtil.decompress(compressed2)));

        IOUtil.setCompressionEnabled(oldCompression);
    }

    private void print(byte[] b, String m) throws IOException {
        ByteArrayInput bai = new ByteArrayInput(b);
        System.out.println(m + " = " + b + " [" + b.length + "] = " +
            Integer.toHexString(bai.readInt()) + ", " + Integer.toHexString(bai.readInt()) + ", " +
            Integer.toHexString(bai.readInt()) + ", " + Integer.toHexString(bai.readInt()) + ", " +
            Integer.toHexString(bai.readInt()) + ", " + Integer.toHexString(bai.readInt()) + ", " +
            Integer.toHexString(bai.readInt()) + ", " + Integer.toHexString(bai.readInt()));
    }
}
