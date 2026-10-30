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
package com.devexperts.io.test;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import com.devexperts.io.MarshallingException;
import com.devexperts.io.SerialClassContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Compares what the pooled Java streams behind {@link IOUtil} and {@link Marshaller} write and read
 * with a new {@link ObjectOutputStream} or {@link ObjectInputStream} per object or per session,
 * and pins the messages of the exceptions they throw.
 */
public class PooledJavaStreamTest {

    private boolean savedCompressionEnabled;

    @Before
    public void setUp() {
        savedCompressionEnabled = IOUtil.isCompressionEnabled();
        IOUtil.setCompressionEnabled(false);
    }

    @After
    public void tearDown() {
        IOUtil.setCompressionEnabled(savedCompressionEnabled);
    }

    @Test
    public void testObjectsWrittenInSequenceEqualFreshStreamBytes() throws IOException {
        List<Object> objects = variedObjects();
        for (int i = 0; i < objects.size(); i++) {
            assertArrayEquals("object " + i, freshBytes(objects.get(i)), IOUtil.objectToBytes(objects.get(i)));
        }
    }

    @Test
    public void testSameObjectWrittenTwiceGivesIdenticalBytes() throws IOException {
        Node node = Node.withSharedChild("twice");
        byte[] fresh = freshBytes(node);
        assertArrayEquals(fresh, IOUtil.objectToBytes(node));
        assertArrayEquals(fresh, IOUtil.objectToBytes(node));
    }

    @Test
    public void testTypedFormKeepsBackReferencesWithinSession() throws IOException {
        Node node = Node.withSharedChild("typed");
        Marshaller<Object[]> marshaller = Marshaller.forClasses(Node.class, Node.class);
        ByteArrayOutput expected = new ByteArrayOutput();
        expected.writeInt(declaredTypesSignature(Node.class, Node.class));
        expected.write(freshBytes(node, node));
        // the second pass reuses the pooled instances that the first pass released
        for (int pass = 0; pass < 2; pass++) {
            byte[] bytes = Marshalled.forObject(new Object[] {node, node}, marshaller).getBytes();
            assertArrayEquals(expected.toByteArray(), bytes);
            Object[] objects = Marshalled.forBytes(bytes, marshaller).getObject();
            assertEquals(node, objects[0]);
            assertSame(objects[0], objects[1]);
        }
    }

    @Test
    public void testFormsReadInSequenceEqualFreshStreamResults() throws Exception {
        List<byte[]> forms = new ArrayList<>();
        for (Object object : variedObjects()) {
            forms.add(freshBytes(object));
        }
        for (byte[] form : forms) {
            ObjectUtilTest.assertDeepEquals(freshRead(form), IOUtil.bytesToObject(form));
        }
    }

    @Test
    public void testWriteObjectWritesCompactLengthAndFreshStreamBytes() throws IOException {
        List<Object> objects = variedObjects();
        ByteArrayOutput out = new ByteArrayOutput();
        ByteArrayOutput expected = new ByteArrayOutput();
        for (Object object : objects) {
            IOUtil.writeObject(out, object);
            byte[] fresh = freshBytes(object);
            expected.writeCompactInt(fresh.length);
            expected.write(fresh);
        }
        assertArrayEquals(expected.toByteArray(), out.toByteArray());

        ByteArrayInput in = new ByteArrayInput(out.toByteArray());
        for (Object object : objects) {
            ObjectUtilTest.assertDeepEquals(object, IOUtil.readObject(in));
        }
        assertEquals(in.getLimit(), in.getPosition());
    }

    @Test
    public void testFailedCallsLeaveLaterFormsEqualToFreshStreams() throws Exception {
        byte[] truncated = freshBytes(Node.withSharedChild("truncated"));
        assertThrows(IOException.class, () -> IOUtil.bytesToObject(Arrays.copyOf(truncated, truncated.length / 2)));
        assertThrows(NotSerializableException.class, () -> IOUtil.objectToBytes(new Object()));
        // a form with a back-reference comes first, so handles left by a failed call change the result
        Node node = Node.withSharedChild("after failures");
        byte[] fresh = freshBytes(node);
        assertArrayEquals(fresh, IOUtil.objectToBytes(node));
        assertEquals(node, IOUtil.bytesToObject(fresh));
        List<Object> objects = variedObjects();
        for (int i = 0; i < objects.size(); i++) {
            assertArrayEquals("object " + i, freshBytes(objects.get(i)), IOUtil.objectToBytes(objects.get(i)));
        }
        for (Object object : objects) {
            byte[] form = freshBytes(object);
            ObjectUtilTest.assertDeepEquals(freshRead(form), IOUtil.bytesToObject(form));
        }
    }

    @Test
    public void testCompactsOnlyTypedFormCarriesNoStreamHeader() throws Exception {
        Marshaller<Object[]> marshaller = Marshaller.forClasses(int.class, String.class);
        Object[] values = {42, "compact"};
        ByteArrayOutput expected = new ByteArrayOutput();
        expected.writeInt(declaredTypesSignature(int.class, String.class));
        expected.writeCompactInt(42);
        expected.writeUTFString("compact");
        Node node = Node.withSharedChild("after compacts");
        byte[] fresh = freshBytes(node);
        // each object call reuses the pooled instance that the typed call before it released
        for (int pass = 0; pass < 2; pass++) {
            byte[] bytes = Marshalled.forObject(values, marshaller).getBytes();
            assertArrayEquals(expected.toByteArray(), bytes);
            assertArrayEquals(fresh, IOUtil.objectToBytes(node));
            assertArrayEquals(values, Marshalled.forBytes(bytes, marshaller).getObject());
            assertEquals(node, IOUtil.bytesToObject(fresh));
        }
    }

    @Test
    public void testTypedFormWritesCompactValuesBeforeStreamHeader() throws Exception {
        Marshaller<Object[]> marshaller = Marshaller.forClasses(int.class, Node.class);
        Node node = Node.withSharedChild("after compact");
        ByteArrayOutput expected = new ByteArrayOutput();
        expected.writeInt(declaredTypesSignature(int.class, Node.class));
        expected.writeCompactInt(42);
        expected.write(freshBytes(node));
        byte[] bytes = Marshalled.forObject(new Object[] {42, node}, marshaller).getBytes();
        assertArrayEquals(expected.toByteArray(), bytes);
        assertArrayEquals(freshBytes(node), IOUtil.objectToBytes(node));
        Object[] objects = Marshalled.forBytes(bytes, marshaller).getObject();
        assertEquals(42, objects[0]);
        assertEquals(node, objects[1]);
    }

    @Test
    public void testSerializationMarshallerEqualsFreshStream() throws Exception {
        Node node = Node.withSharedChild("marshalled");
        byte[] fresh = freshBytes(node);
        for (int pass = 0; pass < 2; pass++) {
            assertArrayEquals(fresh, Marshalled.forObject(node).getBytes());
            assertEquals(node, Marshalled.forBytes(fresh).getObject());
        }
    }

    @Test
    public void testMarshalledOfSerializationIsWrittenAsItsBytes() throws Exception {
        Node node = Node.withSharedChild("pass-through");
        byte[] bytes = Marshalled.forObject(node).getBytes();
        assertArrayEquals(bytes, IOUtil.objectToBytes(Marshalled.forObject(node)));
        ByteArrayOutput out = new ByteArrayOutput();
        IOUtil.writeObject(out, Marshalled.forObject(node));
        ByteArrayOutput expected = new ByteArrayOutput();
        expected.writeCompactInt(bytes.length);
        expected.write(bytes);
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
        assertArrayEquals(bytes, Marshalled.forObject(Marshalled.forObject(node)).getBytes());
    }

    @Test
    public void testMarshalledOfAnotherMarshallerIsNotSerializable() throws Exception {
        Marshalled<Object[]> typed = Marshalled.forObject(new Object[] {"text"}, Marshaller.forClasses(String.class));
        String message = Marshalled.class.getName();
        NotSerializableException bytesError =
            assertThrows(NotSerializableException.class, () -> IOUtil.objectToBytes(typed));
        assertEquals(message, bytesError.getMessage());
        NotSerializableException objectError =
            assertThrows(NotSerializableException.class, () -> IOUtil.writeObject(new ByteArrayOutput(), typed));
        assertEquals(message, objectError.getMessage());
        MarshallingException marshalledError =
            assertThrows(MarshallingException.class, () -> Marshalled.forObject(typed).getBytes());
        assertTrue(marshalledError.getCause() instanceof NotSerializableException);
        assertEquals(message, marshalledError.getCause().getMessage());
    }

    @Test
    public void testEachReadChecksClassesWithItsOwnContext() throws Exception {
        Node node = Node.withSharedChild("context");
        byte[] form = freshBytes(node);
        SerialClassContext javaOnly = SerialClassContext.createSerialClassContext(
            PooledJavaStreamTest.class.getClassLoader(), Collections.singletonList("java.*"), null);
        // the second read reuses the pooled instance that the first read released
        assertEquals(node, IOUtil.bytesToObject(form));
        InvalidClassException e =
            assertThrows(InvalidClassException.class, () -> IOUtil.bytesToObject(form, javaOnly));
        assertEquals("Class " + Node.class.getName() + " is not in the whitelist.", e.getMessage());
        assertEquals(node, IOUtil.bytesToObject(form));
    }

    @Test
    public void testMissingClassKeepsItsMessageAtEveryEntryPoint() throws Exception {
        String missingName = Present.class.getName().replace("Present", "Missing");
        byte[] form = replaceName(freshBytes(new Present()), Present.class.getName(), missingName);
        String message = "Class not found: " + missingName;

        InvalidClassException bytesError =
            assertThrows(InvalidClassException.class, () -> IOUtil.bytesToObject(form));
        assertEquals(message, bytesError.getMessage());

        ByteArrayOutput withLength = new ByteArrayOutput();
        withLength.writeCompactInt(form.length);
        withLength.write(form);
        InvalidClassException objectError = assertThrows(InvalidClassException.class,
            () -> IOUtil.readObject(new ByteArrayInput(withLength.toByteArray())));
        assertEquals(message, objectError.getMessage());

        MarshallingException marshalledError =
            assertThrows(MarshallingException.class, () -> Marshalled.forBytes(form).getObject());
        assertEquals(message, marshalledError.getCause().getMessage());

        ByteArrayOutput typed = new ByteArrayOutput();
        typed.writeInt(declaredTypesSignature(Present.class));
        typed.write(form);
        Marshaller<Object[]> marshaller = Marshaller.forClasses(Present.class);
        MarshallingException typedError = assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(typed.toByteArray(), marshaller).getObject());
        assertEquals(message, typedError.getCause().getMessage());
        assertTrue(typedError.getCause().getCause() instanceof ClassNotFoundException);
    }

    @Test
    public void testMalformedFormsKeepTheirMessages() throws Exception {
        Node node = Node.withSharedChild("malformed");
        byte[] form = freshBytes(node);

        EOFException emptyError = assertThrows(EOFException.class, () -> IOUtil.bytesToObject(new byte[0]));
        assertEquals("End of buffer.", emptyError.getMessage());

        byte[] wrongFirstByte = form.clone();
        wrongFirstByte[0] = 0x01; // the first byte of the stream header
        StreamCorruptedException firstByteError =
            assertThrows(StreamCorruptedException.class, () -> IOUtil.bytesToObject(wrongFirstByte));
        assertEquals("Unknown stream header 0x01", firstByteError.getMessage());

        byte[] wrongHeader = form.clone();
        wrongHeader[3]++; // the last byte of the stream header
        StreamCorruptedException headerError =
            assertThrows(StreamCorruptedException.class, () -> IOUtil.bytesToObject(wrongHeader));
        assertEquals("invalid stream header", headerError.getMessage());

        byte[] longer = Arrays.copyOf(form, form.length + 1);
        IOException unreadError = assertThrows(IOException.class, () -> IOUtil.bytesToObject(longer));
        assertEquals("Some input data was not deserialized", unreadError.getMessage());

        Marshaller<Object[]> nodeMarshaller = Marshaller.forClasses(Node.class);
        MarshallingException untypedError =
            assertThrows(MarshallingException.class, () -> Marshalled.forBytes(form, nodeMarshaller).getObject());
        assertEquals("Not a declared types stream.", untypedError.getCause().getMessage());

        byte[] typed = Marshalled.forObject(new Object[] {node}, nodeMarshaller).getBytes();
        Marshaller<Object[]> objectMarshaller = Marshaller.forClasses(Object.class);
        MarshallingException signatureError =
            assertThrows(MarshallingException.class, () -> Marshalled.forBytes(typed, objectMarshaller).getObject());
        assertEquals("Wrong declared types signature, expected: " +
            Integer.toHexString(declaredTypesSignature(Object.class)) + ", got " +
            Integer.toHexString(declaredTypesSignature(Node.class)), signatureError.getCause().getMessage());
    }

    @Test
    public void testMismatchedTypedValuesKeepTheirMessages() throws Exception {
        Marshaller<Object[]> nodeMarshaller = Marshaller.forClasses(Node.class);

        MarshallingException writeError = assertThrows(MarshallingException.class,
            () -> Marshalled.forObject(new Object[] {"text"}, nodeMarshaller).getBytes());
        assertEquals("Invalid type " + String.class.getName() + ", expected " + Node.class,
            writeError.getCause().getMessage());

        ByteArrayOutput typed = new ByteArrayOutput();
        typed.writeInt(declaredTypesSignature(Node.class));
        typed.write(freshBytes("text"));
        MarshallingException readError = assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(typed.toByteArray(), nodeMarshaller).getObject());
        assertEquals("Invalid type " + String.class.getName() + ", expected " + Node.class.getName(),
            readError.getCause().getMessage());

        Node node = Node.withSharedChild("count");
        MarshallingException countError = assertThrows(MarshallingException.class,
            () -> Marshalled.forObject(new Object[] {node, node}, nodeMarshaller).getBytes());
        assertEquals("Number of objects does not match number of types", countError.getCause().getMessage());
    }

    // Every kind recurs five times, and forms with shared references sit between the others,
    // so handles that a stream keeps from an earlier form change the bytes written or the objects read.
    private static List<Object> variedObjects() {
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String shared = "shared " + i;
            Map<String, Object> map = new HashMap<>();
            map.put("long", (long) i << 40);
            map.put("node", Node.withSharedChild("map node " + i));
            objects.add("string " + i);
            objects.add(i * 1_000_003);
            objects.add((long) i << 40);
            objects.add(i / 7.0);
            objects.add(new int[] {i, -i, Integer.MIN_VALUE});
            objects.add(new String[] {shared, null, shared});
            objects.add(new ArrayList<Object>(Arrays.asList(i, "element " + i, null, (long) i)));
            objects.add(map);
            objects.add(Node.withSharedChild("node " + i));
            objects.add(new Object[] {shared, i, shared});
        }
        return objects;
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

    private static Object freshRead(byte[] form) throws IOException, ClassNotFoundException {
        return new ObjectInputStream(new ByteArrayInputStream(form)).readObject();
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

    // the wire value of ObjectIOImplUtil#getDeclaredTypesSignature
    private static int declaredTypesSignature(Class<?>... types) {
        int hash = 0;
        for (Class<?> type : types) {
            hash = hash * 239 + type.getName().hashCode();
        }
        return 0xE8000000 | hash & 0x00FFFFFF;
    }

    private static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final Node left;
        private final Node right;

        Node(String name, Node left, Node right) {
            this.name = name;
            this.left = left;
            this.right = right;
        }

        static Node withSharedChild(String name) {
            Node child = new Node(name + " child", null, null);
            return new Node(name, child, child);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Node))
                return false;
            Node node = (Node) o;
            return name.equals(node.name) && Objects.equals(left, node.left) && Objects.equals(right, node.right) &&
                (left == right) == (node.left == node.right);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            if (left == null && right == null)
                return name;
            return name + (left == right ? " {shared " + left + "}" : " {" + left + ", " + right + "}");
        }
    }

    private static final class Present implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
