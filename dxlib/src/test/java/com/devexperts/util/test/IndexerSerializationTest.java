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
package com.devexperts.util.test;

import com.devexperts.util.IndexedSet;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IndexerSerializationTest {

    private static final String INDEXED_OBJECTS_FILE = "indexed-objects.bin";
    private static final String DEFAULT_INDEXER_FUNCTION_CNAME =
        "com.devexperts.util.IndexerFunction$DefaultIndexerFunction";

    public static void main(String[] args) throws IOException {
        try (
            FileOutputStream os = new FileOutputStream("./dxlib/src/test/resources/" + INDEXED_OBJECTS_FILE);
            ObjectOutputStream oos = new ObjectOutputStream(os)
        ) {
            serializeObjects(oos);
        }
    }

    @Test
    public void testOldDeserialization() throws IOException, ClassNotFoundException {

        try (InputStream is = this.getClass().getResourceAsStream("/" + INDEXED_OBJECTS_FILE);
            ObjectInputStream ois = new ObjectInputStream(is)
        ) {
            deserializeAndCheckObjects(ois);
        }

    }

    @Test
    public void testNewSerialization() throws IOException, ClassNotFoundException {
        byte[] bytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                serializeObjects(oos);
            }
            bytes = baos.toByteArray();
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    if (desc.getName().equals(DEFAULT_INDEXER_FUNCTION_CNAME))
                        fail(DEFAULT_INDEXER_FUNCTION_CNAME + " encountered in serialized data");
                    return super.resolveClass(desc);
                }
            }
        ) {
            deserializeAndCheckObjects(ois);
        }
    }

    private void deserializeAndCheckObjects(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        Object obj = ois.readObject();
        assertTrue(obj instanceof IndexedSet);
        IndexedSet<String, String> set = (IndexedSet<String, String>) obj;
        assertEquals(2, set.size());
        assertEquals("xxx", set.getByKey("xxx"));
        assertEquals("yyy", set.getByKey("yyy"));
        assertEquals("com.devexperts.util.Indexer$DefaultIndexer", set.getIndexerFunction().getClass().getName());
    }

    private static void serializeObjects(ObjectOutputStream oos) throws IOException {
        IndexedSet<String, String> set = new IndexedSet<>();
        set.add("xxx");
        set.add("yyy");
        oos.writeObject(set);
    }
}
