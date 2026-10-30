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

import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

// changes the writer of the JVM-wide registry, so it runs in a class loader of its own
@RunWith(IsolatedRunner.class)
@Isolated({"com.devexperts.io"})
public class ObjectSerializationSpiTest {

    private static final ObjectSerializationFactories FACTORIES = ObjectSerializationFactories.instance();
    private static final ObjectSerializationWriterFactory TEST = FACTORIES.forName("test");
    private static final ObjectSerializationWriterFactory JAVA = FACTORIES.forName("DX.java");

    private static final Class<?>[] TYPES = {String.class, int[].class, Payload.class};

    private final ObjectSerializationWriterFactory writing = FACTORIES.getDefault();
    private final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    @Before
    public void setUp() {
        // reading resolves classes through the context class loader, and Payload is defined by the isolated one
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(contextLoader);
        FACTORIES.setDefault(writing);
        IOUtil.setCompressionEnabled(false);
        TestSerializationFactory.failReset = false;
        TestSerializationFactory.throwOnReset = false;
    }

    @Test
    public void testSerialFormStartsWithTheMarkerOfTheFactoryThatWrote() {
        FACTORIES.setDefault(JAVA);
        assertEquals((byte) 0xAC, Marshalled.forObject(new Payload("a", 1)).getBytes()[0]);

        FACTORIES.setDefault(TEST);
        assertEquals(TestSerializationFactory.MARKER, Marshalled.forObject(new Payload("a", 1)).getBytes()[0]);
    }

    @Test
    public void testRoundTripWithEachRegisteredFactory() {
        for (ObjectSerializationWriterFactory factory : Arrays.asList(JAVA, TEST)) {
            FACTORIES.setDefault(factory);
            Payload payload = new Payload("round trip", 42);
            Marshalled<Object> marshalled = Marshalled.forBytes(Marshalled.forObject(payload).getBytes());
            assertEquals(factory.getName(), payload, marshalled.getObject());
        }
    }

    @Test
    public void testEveryRegisteredFormIsReadWhateverTheFactoryThatWrites() {
        Payload payload = new Payload("written once", 7);
        for (ObjectSerializationWriterFactory writer : Arrays.asList(JAVA, TEST)) {
            FACTORIES.setDefault(writer);
            byte[] bytes = Marshalled.forObject(payload).getBytes();
            for (ObjectSerializationWriterFactory defaultWriter : Arrays.asList(JAVA, TEST)) {
                FACTORIES.setDefault(defaultWriter);
                assertEquals(writer.getName() + " form read while " + defaultWriter.getName() + " writes",
                    payload, Marshalled.forBytes(bytes).getObject());
            }
        }
    }

    @Test
    public void testPooledSerializerReusesTheWriterOfItsFactory() throws IOException {
        FACTORIES.setDefault(TEST);
        assertEquals(new Payload("a", 1), IOUtil.bytesToObject(IOUtil.objectToBytes(new Payload("a", 1))));
        int created = TestSerializationFactory.WRITERS_CREATED.get();

        // the pool hands the serializer of the first call to the next one, which reuses its writer
        assertEquals(new Payload("b", 2), IOUtil.bytesToObject(IOUtil.objectToBytes(new Payload("b", 2))));
        assertEquals(created, TestSerializationFactory.WRITERS_CREATED.get());
    }

    @Test
    public void testObjectApiOfIOUtilUsesTheFactoryInEffect() throws IOException {
        FACTORIES.setDefault(TEST);
        Payload payload = new Payload("io util", 3);

        byte[] bytes = IOUtil.objectToBytes(payload);
        assertEquals(TestSerializationFactory.MARKER, bytes[0]);
        assertEquals(payload, IOUtil.bytesToObject(bytes));

        ByteArrayOutput out = new ByteArrayOutput();
        out.writeObject(payload);
        assertEquals(payload, new ByteArrayInput(out.toByteArray()).readObject());
    }

    @Test
    public void testSerialFormOfAnUnregisteredMarkerIsAnUnknownStreamHeader() {
        byte[] bytes = {(byte) 0xFE, 1, 2, 3};
        MarshallingException e = assertThrows(MarshallingException.class,
            () -> Marshalled.forBytes(bytes).getObject());
        assertTrue(e.getCause() instanceof StreamCorruptedException);
        assertEquals("Unknown stream header 0xFE", e.getCause().getMessage());
    }

    @Test
    public void testTypedMarshallerRoundTripWithEachRegisteredFactory() {
        for (ObjectSerializationWriterFactory factory : Arrays.asList(JAVA, TEST)) {
            FACTORIES.setDefault(factory);
            Marshaller.Typed<Object[]> marshaller = Marshaller.forClasses(String.class, int[].class, Payload.class);
            Object[] parameters = {"text", new int[] {1, 2, 3}, new Payload("typed", 5)};

            Object[] restored = Marshalled.forBytes(
                Marshalled.forObject(parameters, marshaller).getBytes(), marshaller).getObject();

            assertEquals(factory.getName(), "text", restored[0]);
            assertArrayEquals(factory.getName(), new int[] {1, 2, 3}, (int[]) restored[1]);
            assertEquals(factory.getName(), new Payload("typed", 5), restored[2]);
        }
    }

    @Test
    public void testTypedFormOfOneFactoryIsReadWhileWritingWithAnother() {
        Marshaller.Typed<Object[]> marshaller = Marshaller.forClasses(TYPES);
        FACTORIES.setDefault(TEST);
        Object[] parameters = {"text", new int[] {4}, new Payload("typed test", 9)};
        byte[] bytes = Marshalled.forObject(parameters, marshaller).getBytes();

        FACTORIES.setDefault(JAVA);
        Object[] restored = Marshalled.forBytes(bytes, marshaller).getObject();
        assertEquals("text", restored[0]);
        assertArrayEquals(new int[] {4}, (int[]) restored[1]);
        assertEquals(new Payload("typed test", 9), restored[2]);
    }

    @Test
    public void testTypedMarshallerRejectsTheSignatureOfOtherTypes() {
        for (ObjectSerializationWriterFactory factory : Arrays.asList(JAVA, TEST)) {
            FACTORIES.setDefault(factory);
            byte[] bytes = Marshalled.forObject(new Object[] {"text"}, Marshaller.forClasses(String.class)).getBytes();
            assertThrows(factory.getName(), MarshallingException.class,
                () -> Marshalled.forBytes(bytes, Marshaller.forClasses(Integer.class)).getObject());
        }
    }

    @Test
    public void testTypedFormKeepsTheSignatureAndTheCompactValuesUnderAnotherFactory() throws IOException {
        Marshaller.Typed<Object[]> marshaller = Marshaller.forClasses(int.class, Payload.class);
        Object[] parameters = {1234, new Payload("after the compact value", 6)};
        FACTORIES.setDefault(JAVA);
        byte[] javaForm = Marshalled.forObject(parameters, marshaller).getBytes();
        FACTORIES.setDefault(TEST);
        byte[] testForm = Marshalled.forObject(parameters, marshaller).getBytes();

        // the declared types signature, then the compact value, then the object part of the factory
        ByteArrayOutput compacts = new ByteArrayOutput();
        compacts.writeCompactInt(1234);
        int objectsAt = 4 + compacts.getPosition();
        assertEquals((byte) 0xE8, testForm[0]);
        assertArrayEquals(Arrays.copyOf(javaForm, objectsAt), Arrays.copyOf(testForm, objectsAt));
        assertEquals(TestSerializationFactory.MARKER, testForm[objectsAt]);
        assertArrayEquals(Arrays.copyOfRange(javaForm, objectsAt, javaForm.length),
            Arrays.copyOfRange(testForm, objectsAt + 1, testForm.length));

        Object[] restored = Marshalled.forBytes(testForm, marshaller).getObject();
        assertEquals(1234, restored[0]);
        assertEquals(new Payload("after the compact value", 6), restored[1]);
    }

    @Test
    public void testCompressedFormRoundTripWithEachRegisteredFactory() {
        char[] filler = new char[64 * 1024];
        Arrays.fill(filler, 'x');
        Payload payload = new Payload(new String(filler), 11);
        for (ObjectSerializationWriterFactory factory : Arrays.asList(JAVA, TEST)) {
            FACTORIES.setDefault(factory);
            IOUtil.setCompressionEnabled(true);
            byte[] bytes = Marshalled.forObject(payload).getBytes();
            assertTrue(factory.getName(), bytes.length < filler.length);
            assertEquals(factory.getName(), payload, Marshalled.forBytes(bytes).getObject());
        }
    }

    @Test
    public void testSerializerWhoseWriterFailsToResetIsDropped() throws IOException {
        FACTORIES.setDefault(TEST);
        Payload payload = new Payload("shared", 12);
        List<Payload> list = new ArrayList<>(Arrays.asList(payload, payload));

        TestSerializationFactory.failReset = true;
        byte[] first = IOUtil.objectToBytes(list);
        TestSerializationFactory.failReset = false;
        // a writer that kept the first session writes the list as a reference to it
        byte[] second = IOUtil.objectToBytes(list);

        assertArrayEquals(markedFreshBytes(list), first);
        assertArrayEquals(markedFreshBytes(list), second);
    }

    @Test
    public void testDeserializerWhoseReaderFailsToResetIsDropped() throws IOException {
        FACTORIES.setDefault(TEST);
        byte[] first = IOUtil.objectToBytes(sharedPair("first"));
        byte[] second = IOUtil.objectToBytes(sharedPair("second"));

        TestSerializationFactory.failReset = true;
        assertEquals(sharedPair("first"), IOUtil.bytesToObject(first));
        TestSerializationFactory.failReset = false;
        // a reader that kept the first session resolves the back-reference of the second form into the first one
        List<?> restored = (List<?>) IOUtil.bytesToObject(second);

        assertEquals(sharedPair("second"), restored);
        assertSame(restored.get(0), restored.get(1));
    }

    @Test
    public void testSerializerWhoseWriterThrowsOnResetIsDropped() throws IOException {
        FACTORIES.setDefault(TEST);
        List<Payload> list = sharedPair("thrown on write");

        TestSerializationFactory.throwOnReset = true;
        byte[] first = IOUtil.objectToBytes(list);
        TestSerializationFactory.throwOnReset = false;
        // a writer that kept the first session writes the list as a reference to it
        byte[] second = IOUtil.objectToBytes(list);

        assertArrayEquals(markedFreshBytes(list), first);
        assertArrayEquals(markedFreshBytes(list), second);
    }

    @Test
    public void testDeserializerWhoseReaderThrowsOnResetIsDropped() throws IOException {
        FACTORIES.setDefault(TEST);
        byte[] first = IOUtil.objectToBytes(sharedPair("first"));
        byte[] second = IOUtil.objectToBytes(sharedPair("second"));

        TestSerializationFactory.throwOnReset = true;
        assertEquals(sharedPair("first"), IOUtil.bytesToObject(first));
        TestSerializationFactory.throwOnReset = false;
        // a reader that kept the first session resolves the back-reference of the second form into the first one
        List<?> restored = (List<?>) IOUtil.bytesToObject(second);

        assertEquals(sharedPair("second"), restored);
        assertSame(restored.get(0), restored.get(1));
    }

    @Test
    public void testTypedReadOfCompactValuesAloneResetsNoReader() throws IOException {
        FACTORIES.setDefault(TEST);
        byte[] objectForm = IOUtil.objectToBytes(new Payload("before", 15));
        Marshaller.Typed<Object[]> marshaller = Marshaller.forClasses(int.class, String.class);
        byte[] compactsForm = Marshalled.forObject(new Object[] {42, "compact"}, marshaller).getBytes();
        int resets = TestSerializationFactory.READER_RESETS.get();
        // the pool hands the deserializer this call releases, with its reader of the test factory, to the next call
        assertEquals(new Payload("before", 15), IOUtil.bytesToObject(objectForm));
        assertEquals(resets + 1, TestSerializationFactory.READER_RESETS.get());

        assertArrayEquals(new Object[] {42, "compact"}, Marshalled.forBytes(compactsForm, marshaller).getObject());
        assertEquals(resets + 1, TestSerializationFactory.READER_RESETS.get());
    }

    private static List<Payload> sharedPair(String name) {
        Payload payload = new Payload(name, 13);
        return new ArrayList<>(Arrays.asList(payload, payload));
    }

    private static byte[] markedFreshBytes(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(TestSerializationFactory.MARKER);
        ObjectOutputStream oos = new ObjectOutputStream(bytes);
        oos.writeObject(object);
        oos.flush();
        return bytes.toByteArray();
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 0;

        private final String name;
        private final int value;

        public Payload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Payload && value == ((Payload) o).value && Objects.equals(name, ((Payload) o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return "Payload{" + name + ", " + value + "}";
        }
    }
}
