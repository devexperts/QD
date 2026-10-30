/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import com.devexperts.util.LockFreePool;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks {@link JavaSerializationWriter} and {@link JavaSerializationReader} against the forms
 * that a new {@link ObjectOutputStream} writes per session, and checks the pooled instances of
 * {@link ObjectSerializer} and {@link ObjectDeserializer} that hold them.
 */
public class JavaSerializationStreamsTest {

    // stands for the declared types signature and the compact values that precede a session in the typed form
    private static final int PREFIX = 0xE8123456;

    private final SerialClassContext serialContext =
        SerialClassContext.createSerialClassContext(JavaSerializationStreamsTest.class.getClassLoader(), null, null);

    @Test
    public void testWriterSessionEqualsFreshStream() throws IOException {
        ByteArrayOutput out = new ByteArrayOutput();
        JavaSerializationWriter writer = new JavaSerializationWriter(out);
        List<String> list = new ArrayList<>(Arrays.asList("first", "second"));
        out.writeInt(PREFIX);
        writer.start();
        writer.write(list);
        writer.write(list);
        writer.flush();
        assertArrayEquals(prefixed(freshBytes(list, list)), out.toByteArray());
    }

    @Test
    public void testWriterSessionAfterResetEqualsFreshStream() throws IOException {
        ByteArrayOutput out = new ByteArrayOutput();
        JavaSerializationWriter writer = new JavaSerializationWriter(out);
        List<String> list = new ArrayList<>(Arrays.asList("first", "second"));
        writer.start();
        writer.write(list);
        writer.flush();
        writer.reset();
        out.clear();
        // the list of the first session is written in full again only if the reset forgot it
        out.writeInt(PREFIX);
        writer.start();
        writer.write(list);
        writer.write(list);
        writer.flush();
        assertArrayEquals(prefixed(freshBytes(list, list)), out.toByteArray());
    }

    @Test
    public void testReaderSessionThenResetReturnsTrue() throws IOException {
        ByteArrayInput in = new ByteArrayInput();
        JavaSerializationReader reader = new JavaSerializationReader(in);
        // the back-references of the second session resolve to its own objects only if the reset forgot the first one
        for (String name : new String[] {"first", "second"}) {
            List<String> list = new ArrayList<>(Arrays.asList(name, name + " element"));
            in.setBuffer(freshBytes(list, list));
            reader.start(serialContext);
            Object object = reader.read();
            assertEquals(list, object);
            assertSame(object, reader.read());
            assertEquals(in.getLimit(), in.getPosition());
            assertTrue(reader.reset());
        }
    }

    @Test
    public void testStartRejectsInvalidStreamHeader() throws IOException {
        ByteArrayInput in = new ByteArrayInput();
        JavaSerializationReader reader = new JavaSerializationReader(in);
        byte[] form = freshBytes("text");
        form[ObjectIOImplUtil.STREAM_HEADER.length - 1]++;
        in.setBuffer(form);
        StreamCorruptedException e = assertThrows(StreamCorruptedException.class, () -> reader.start(serialContext));
        assertEquals("invalid stream header", e.getMessage());
    }

    @Test
    public void testReadOfMissingClassThrowsInvalidClassException() throws IOException {
        String presentName = Present.class.getName();
        String missingName = presentName.replace("Present", "Missing");
        ByteArrayInput in = new ByteArrayInput();
        JavaSerializationReader reader = new JavaSerializationReader(in);
        in.setBuffer(replaceName(freshBytes(new Present()), presentName, missingName));
        reader.start(serialContext);
        InvalidClassException e = assertThrows(InvalidClassException.class, reader::read);
        assertEquals("Class not found: " + missingName, e.getMessage());
        assertTrue(e.getCause() instanceof ClassNotFoundException);
    }

    @Test
    public void testOneObjectReadsKeepClassNotFoundExceptionAsCause() throws IOException {
        String presentName = Present.class.getName();
        byte[] form = replaceName(freshBytes(new Present()), presentName, presentName.replace("Present", "Missing"));
        assertCausedByMissingClass(assertThrows(InvalidClassException.class, () -> IOUtil.bytesToObject(form)));
        ByteArrayOutput withLength = new ByteArrayOutput();
        withLength.writeCompactInt(form.length);
        withLength.write(form);
        assertCausedByMissingClass(assertThrows(InvalidClassException.class,
            () -> IOUtil.readObject(new ByteArrayInput(withLength.toByteArray()))));
        assertCausedByMissingClass(
            assertThrows(MarshallingException.class, () -> Marshalled.forBytes(form).getObject()).getCause());
    }

    @Test
    public void testSerializerPoolTakesBackOnlyInstancesOfSmallCompletedWrites() throws Exception {
        LockFreePool<?> pool = emptiedPool(ObjectSerializer.POOL);
        // an oversized or failed call takes the instance that the small call before it returned, and drops it
        IOUtil.objectToBytes("small");
        assertEquals(1, pool.size());
        IOUtil.objectToBytes(oversizedString(ObjectSerializer.MAX_POOLED_BUFFER));
        assertEquals(0, pool.size());
        IOUtil.objectToBytes("small");
        assertEquals(1, pool.size());
        assertThrows(NotSerializableException.class, () -> IOUtil.objectToBytes(new Object()));
        assertEquals(0, pool.size());
    }

    @Test
    public void testDeserializerPoolTakesBackOnlyInstancesOfSmallCompletedReads() throws Exception {
        LockFreePool<?> pool = emptiedPool(ObjectDeserializer.POOL);
        byte[] small = freshBytes("small");
        // an oversized or failed call takes the instance that the small call before it returned, and drops it
        IOUtil.bytesToObject(small);
        assertEquals(1, pool.size());
        IOUtil.bytesToObject(freshBytes(oversizedString(ObjectDeserializer.MAX_POOLED_BUFFER)));
        assertEquals(0, pool.size());
        IOUtil.bytesToObject(small);
        assertEquals(1, pool.size());
        assertThrows(IOException.class, () -> IOUtil.bytesToObject(Arrays.copyOf(small, small.length - 1)));
        assertEquals(0, pool.size());
    }

    @Test
    public void testFormAfterOversizedFormEqualsFreshStream() throws Exception {
        String oversized = oversizedString(Math.max(ObjectSerializer.MAX_POOLED_BUFFER,
            ObjectDeserializer.MAX_POOLED_BUFFER));
        String shared = "shared";
        List<String> small = new ArrayList<>(Arrays.asList(shared, shared));
        byte[] oversizedForm = freshBytes(oversized);
        byte[] smallForm = freshBytes(small);

        assertArrayEquals(oversizedForm, IOUtil.objectToBytes(oversized));
        assertArrayEquals(smallForm, IOUtil.objectToBytes(small));

        assertEquals(oversized, IOUtil.bytesToObject(oversizedForm));
        List<?> read = (List<?>) IOUtil.bytesToObject(smallForm);
        assertEquals(small, read);
        assertSame(read.get(0), read.get(1));
    }

    private static void assertCausedByMissingClass(Throwable e) {
        assertTrue(e instanceof InvalidClassException);
        assertTrue(e.getCause() instanceof ClassNotFoundException);
    }

    private static LockFreePool<?> emptiedPool(LockFreePool<?> pool) {
        while (pool.poll() != null) {
            // the test counts the instances from an empty pool
        }
        return pool;
    }

    // the stream header and the string's own header put its serial form above the limit
    private static String oversizedString(int maxPooledBuffer) {
        char[] chars = new char[maxPooledBuffer];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    private static byte[] freshBytes(Object... session) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bytes);
        for (Object object : session) {
            oos.writeObject(object);
        }
        oos.flush();
        return bytes.toByteArray();
    }

    private static byte[] prefixed(byte[] session) throws IOException {
        ByteArrayOutput out = new ByteArrayOutput();
        out.writeInt(PREFIX);
        out.write(session);
        return out.toByteArray();
    }

    // the two names have the same length, so the length that precedes the name in the form stays valid
    private static byte[] replaceName(byte[] form, String name, String replacement) {
        byte[] target = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + target.length <= form.length; i++) {
            if (Arrays.equals(target, Arrays.copyOfRange(form, i, i + target.length))) {
                System.arraycopy(replacement.getBytes(StandardCharsets.UTF_8), 0, form, i, target.length);
                return form;
            }
        }
        throw new AssertionError(name + " is not in the form");
    }

    private static class Present implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
