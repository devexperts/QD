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

import com.devexperts.annotation.Internal;
import com.devexperts.logging.Logging;
import com.devexperts.services.Services;
import com.devexperts.util.SystemProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The registered {@link ObjectSerializationReaderFactory reader factories} of a class path, indexed by marker,
 * the registered {@link ObjectSerializationWriterFactory writer factories}, indexed by name, and the writer factory
 * that writes.
 */
final class ObjectSerializationFactories {
    private static final Logging log = Logging.getLogging(ObjectSerializationFactories.class);

    private static final ObjectSerializationFactories INSTANCE = new ObjectSerializationFactories(
        Services.createServices(ObjectSerializationReaderFactory.class, null),
        Services.createServices(ObjectSerializationWriterFactory.class, null),
        SystemProperties.getProperty(IOUtil.OBJECT_SERIALIZATION_PROPERTY, null));

    private final ObjectSerializationReaderFactory[] readersByMarker = new ObjectSerializationReaderFactory[256];
    private final Map<String, ObjectSerializationWriterFactory> writersByName = new LinkedHashMap<>();
    private volatile ObjectSerializationWriterFactory defaultWriter; // SYNC: none

    // Reader and writer registration are independent: a duplicate marker does not prevent
    // writer registration, and a duplicate name does not prevent reader registration
    ObjectSerializationFactories(Iterable<ObjectSerializationReaderFactory> readerFactories,
        Iterable<ObjectSerializationWriterFactory> writerFactories, String defaultName)
    {
        for (ObjectSerializationReaderFactory factory : readerFactories) {
            registerReader(factory);
        }
        for (ObjectSerializationWriterFactory factory : writerFactories) {
            registerWriter(factory);
        }
        // JavaSerializationFactory is a listed service; this keeps it where the service lookup misses it
        JavaSerializationFactory java = null;
        if (forMarker(JavaSerializationFactory.MARKER) == null) {
            java = new JavaSerializationFactory();
            registerReader(java);
        }
        if (!writersByName.containsKey(JavaSerializationFactory.NAME))
            registerWriter(java != null ? java : new JavaSerializationFactory());
        ObjectSerializationWriterFactory writer =
            defaultName == null || defaultName.trim().isEmpty() ? firstWriter() : forName(defaultName);
        if (writer == null) {
            throw new IllegalArgumentException("Unknown object serialization '" + defaultName + "'; registered: " +
                writersByName.keySet());
        }
        this.defaultWriter = writer;
        List<String> markers = new ArrayList<>();
        for (int marker = 0; marker < readersByMarker.length; marker++) {
            if (readersByMarker[marker] != null)
                markers.add(hex((byte) marker));
        }
        if (markers.size() > 1 || writersByName.size() > 1) {
            log.info("Object serializations: readers " + markers + ", writers " + writersByName.keySet() +
                ", writer " + defaultWriter.getName());
        }
    }

    static ObjectSerializationFactories instance() {
        return INSTANCE;
    }

    /**
     * Returns the reader factory of the specified marker, or {@code null}.
     */
    ObjectSerializationReaderFactory forMarker(byte marker) {
        return readersByMarker[marker & 0xFF];
    }

    /**
     * Returns the writer factory of the specified name, or {@code null}.
     */
    ObjectSerializationWriterFactory forName(String name) {
        return writersByName.get(Objects.requireNonNull(name, "name"));
    }

    ObjectSerializationWriterFactory getDefault() {
        return defaultWriter;
    }

    // For testing purposes only!
    @Internal
    void setDefault(ObjectSerializationWriterFactory factory) {
        this.defaultWriter = Objects.requireNonNull(factory, "factory");
    }

    private void registerReader(ObjectSerializationReaderFactory factory) {
        byte marker = factory.getMarker();
        try {
            IOUtil.checkObjectSerializationMarker(marker);
        } catch (IllegalArgumentException e) {
            log.error(skipped("reader", factory, e.getMessage()));
            return;
        }
        ObjectSerializationReaderFactory holder = readersByMarker[marker & 0xFF];
        if (holder != null) {
            log.warn(skipped("reader", factory, "the marker " + hex(marker) + " is held by " +
                holder.getClass().getName()));
            return;
        }
        readersByMarker[marker & 0xFF] = factory;
    }

    private void registerWriter(ObjectSerializationWriterFactory factory) {
        String name = factory.getName();
        ObjectSerializationWriterFactory holder = writersByName.get(name);
        if (holder != null) {
            log.warn(skipped("writer", factory, "the name '" + name + "' is held by " + holder.getClass().getName()));
            return;
        }
        writersByName.put(name, factory);
    }

    private ObjectSerializationWriterFactory firstWriter() {
        return writersByName.values().iterator().next(); // a writer factory named DX.java at least is registered
    }

    private static String skipped(String kind, Object factory, String reason) {
        return "Object serialization " + kind + " factory " + factory.getClass().getName() + " is skipped: " + reason;
    }

    private static String hex(byte marker) {
        return String.format("0x%02X", marker & 0xFF);
    }
}
