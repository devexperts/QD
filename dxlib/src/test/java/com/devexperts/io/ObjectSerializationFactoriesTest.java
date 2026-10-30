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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectSerializationFactoriesTest {

    private static final byte JAVA_MARKER = ObjectIOImplUtil.STREAM_HEADER[0];

    @Test
    public void testFirstWriterIsSelectedAndJavaFactoryIsAdded() {
        Fake one = new Fake("one", (byte) 0xA1);
        ObjectSerializationFactories factories = factoriesOf(one);

        assertSame(one, factories.forMarker((byte) 0xA1));
        assertSame(one, factories.forName("one"));
        assertTrue(factories.forMarker(JAVA_MARKER) instanceof JavaSerializationFactory);
        assertTrue(factories.forName("DX.java") instanceof JavaSerializationFactory);
        assertSame(one, factories.getDefault());
    }

    @Test
    public void testJavaFactoryAloneReadsAndWrites() {
        ObjectSerializationFactories factories = factoriesOf();

        assertTrue(factories.getDefault() instanceof JavaSerializationFactory);
        assertSame(factories.getDefault(), factories.forName("DX.java"));
        assertSame(factories.getDefault(), factories.forMarker(JAVA_MARKER));
    }

    @Test
    public void testReaderFactoryIsFoundByItsMarker() {
        Fake one = new Fake("one", (byte) 0xA1);
        ObjectSerializationFactories factories = factoriesOf(one);

        assertSame(one, factories.forMarker((byte) 0xA1));
        assertTrue(factories.forMarker(JAVA_MARKER) instanceof JavaSerializationFactory);
        assertNull(factories.forMarker((byte) 0xA2));
    }

    @Test
    public void testLaterFactoryClaimingATakenNameIsSkippedAsAWriterOnly() {
        Fake first = new Fake("same", (byte) 0xA1);
        Fake second = new Fake("same", (byte) 0xA2);
        ObjectSerializationFactories factories = factoriesOf(first, second);

        assertSame(first, factories.forName("same"));
        assertSame(first, factories.forMarker((byte) 0xA1));
        assertSame(second, factories.forMarker((byte) 0xA2));
    }

    @Test
    public void testLaterFactoryClaimingATakenMarkerIsSkippedAsAReaderOnly() {
        Fake first = new Fake("first", (byte) 0xA1);
        Fake second = new Fake("second", (byte) 0xA1);
        ObjectSerializationFactories factories = factoriesOf(first, second);

        assertSame(first, factories.forMarker((byte) 0xA1));
        assertSame(first, factories.forName("first"));
        assertSame(second, factories.forName("second"));
    }

    @Test
    public void testFactoryNamedJavaAheadOfTheJavaFactoryReplacesItsWritingAlone() {
        FakeWriter faster = new FakeWriter("DX.java");
        JavaSerializationFactory java = new JavaSerializationFactory();
        ObjectSerializationFactories factories =
            new ObjectSerializationFactories(Arrays.asList(java), Arrays.asList(faster, java), null);

        assertSame(faster, factories.forName("DX.java"));
        assertSame(faster, factories.getDefault());
        assertSame(java, factories.forMarker(JAVA_MARKER));
    }

    @Test
    public void testFactoryWithTheMarkerOfJavaAheadOfTheJavaFactoryReplacesItsReadingAlone() {
        Fake faster = new Fake("faster", JAVA_MARKER);
        JavaSerializationFactory java = new JavaSerializationFactory();
        ObjectSerializationFactories factories =
            new ObjectSerializationFactories(Arrays.asList(faster, java), Arrays.asList(faster, java), null);

        assertSame(faster, factories.forMarker(JAVA_MARKER));
        assertSame(faster, factories.forName("faster"));
        assertSame(java, factories.forName("DX.java"));
    }

    @Test
    public void testReaderFactoryWithAZlibOrSignatureMarkerIsSkipped() {
        ObjectSerializationFactories factories = new ObjectSerializationFactories(
            Arrays.asList(new FakeReader((byte) 0x08), new FakeReader((byte) 0x78), new FakeReader((byte) 0xE8)),
            Collections.emptyList(), null);

        assertNull(factories.forMarker((byte) 0x08));
        assertNull(factories.forMarker((byte) 0x78));
        assertNull(factories.forMarker((byte) 0xE8));
        assertTrue(factories.forMarker(JAVA_MARKER) instanceof JavaSerializationFactory);
    }

    @Test
    public void testReaderFactoryWithAMarkerBelow0x80IsRegistered() {
        FakeReader json = new FakeReader((byte) '{');
        ObjectSerializationFactories factories =
            new ObjectSerializationFactories(Arrays.asList(json), Collections.emptyList(), null);

        assertSame(json, factories.forMarker((byte) '{'));
    }

    @Test
    public void testUnknownNameReturnsNull() {
        ObjectSerializationFactories factories = factoriesOf(new Fake("one", (byte) 0xA1));

        assertNull(factories.forName("absent"));
    }

    @Test
    public void testSetDefaultSetsTheWriter() {
        ObjectSerializationFactories factories = factoriesOf(new Fake("one", (byte) 0xA1));
        ObjectSerializationWriterFactory java = factories.forName("DX.java");

        factories.setDefault(java);
        assertSame(java, factories.getDefault());
    }

    @Test
    public void testNameOfNoRegisteredWriterIsRejectedWithTheRegisteredNames() {
        List<ObjectSerializationReaderFactory> readers = Collections.emptyList();
        List<ObjectSerializationWriterFactory> writers = Collections.singletonList(new FakeWriter("one"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new ObjectSerializationFactories(readers, writers, "absent"));
        assertEquals("Unknown object serialization 'absent'; registered: [one, DX.java]", e.getMessage());
    }

    @Test
    public void testEmptyOrMissingNameSelectsTheFirstWriter() {
        FakeWriter one = new FakeWriter("one");
        FakeWriter two = new FakeWriter("two");
        for (String name : Arrays.asList(null, "", "  ")) {
            ObjectSerializationFactories factories =
                new ObjectSerializationFactories(Collections.emptyList(), Arrays.asList(one, two), name);
            assertSame(name, one, factories.getDefault());
        }
    }

    @Test
    public void testNameSelectsItsWriter() {
        FakeWriter one = new FakeWriter("one");
        FakeWriter two = new FakeWriter("two");
        ObjectSerializationFactories factories =
            new ObjectSerializationFactories(Collections.emptyList(), Arrays.asList(one, two), "two");

        assertSame(two, factories.getDefault());
    }

    @Test
    public void testRegisteredFactoriesOfThisClassPath() {
        ObjectSerializationFactories factories = ObjectSerializationFactories.instance();

        // the test factory takes an order after Java
        assertTrue(factories.forMarker(JAVA_MARKER) instanceof JavaSerializationFactory);
        assertTrue(factories.forMarker(TestSerializationFactory.MARKER) instanceof TestSerializationFactory);
        assertTrue(factories.forName("DX.java") instanceof JavaSerializationFactory);
        assertTrue(factories.forName("test") instanceof TestSerializationFactory);
        assertTrue(factories.getDefault() instanceof JavaSerializationFactory);
    }

    // lists every factory as a reader factory and as a writer factory, in service order
    private static ObjectSerializationFactories factoriesOf(Fake... factories) {
        return new ObjectSerializationFactories(Arrays.asList(factories), Arrays.asList(factories), null);
    }

    private static class FakeReader implements ObjectSerializationReaderFactory {
        private final byte marker;

        FakeReader(byte marker) {
            this.marker = marker;
        }

        @Override
        public byte getMarker() {
            return marker;
        }

        @Override
        public ObjectSerializationReader createReader(ByteArrayInput in) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeWriter implements ObjectSerializationWriterFactory {
        private final String name;

        FakeWriter(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ObjectSerializationWriter createWriter(ByteArrayOutput out) {
            throw new UnsupportedOperationException();
        }
    }

    private static class Fake implements ObjectSerializationReaderFactory, ObjectSerializationWriterFactory {
        private final String name;
        private final byte marker;

        Fake(String name, byte marker) {
            this.name = name;
            this.marker = marker;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public byte getMarker() {
            return marker;
        }

        @Override
        public ObjectSerializationWriter createWriter(ByteArrayOutput out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectSerializationReader createReader(ByteArrayInput in) {
            throw new UnsupportedOperationException();
        }
    }
}
