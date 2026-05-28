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
package com.devexperts.qd.qtp;

import com.devexperts.annotation.Internal;
import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descriptor for QTP protocol message that is sent as a part of {@link ProtocolDescriptor}.
 */
@Internal
public final class MessageDescriptor {
    private static final int ID_UNKNOWN = -1;

    // Not final because parseFrom()/appendFromTextTokens() set it during deserialization,
    // but no public setter exists — id is effectively immutable after construction.
    private int id = ID_UNKNOWN;
    private String name;
    private final ProtocolDescriptor parent;

    final Map<String, String> properties = new LinkedHashMap<>();

    MessageDescriptor(ProtocolDescriptor parent) {
        this.parent = parent;
    }

    MessageDescriptor(ProtocolDescriptor parent, MessageType type) {
        this.parent = parent;
        this.id = type.getId();
        this.name = type.name();
    }

    /**
     * Creates a deep copy of the given descriptor under a new parent.
     * Properties are copied; the new descriptor is independent of the original.
     */
    MessageDescriptor(ProtocolDescriptor parent, MessageDescriptor other) {
        this.parent = parent;
        this.id = other.id;
        this.name = other.name;
        this.properties.putAll(other.properties);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MessageType getMessageType() {
        if (id == ID_UNKNOWN)
            return MessageType.findByName(name);
        MessageType type = MessageType.findById(id);
        return type == null || !type.name().equals(name) ? null : type;
    }

    public Map<String, String> getProperties() {
        Map<String, String> result = new LinkedHashMap<>(parent.getProperties());
        result.putAll(properties);
        return Collections.unmodifiableMap(result);
    }

    public String getProperty(String key) {
        String result = properties.get(key);
        return result == null ? parent.getProperty(key) : result;
    }

    public void setProperty(String key, String value) {
        properties.put(key, value == null ? "" : value);
    }

    void composeTo(BufferedOutput out) throws IOException {
        out.writeCompactInt(id);
        out.writeUTFString(name);
        out.writeProperties(properties);
    }

    void parseFrom(BufferedInput in) throws IOException {
        id = in.readCompactInt();
        name = in.readUTFString();
        if (id == ID_UNKNOWN) {
            MessageType messageType = MessageType.findByName(name);
            if (messageType != null)
                id = messageType.getId();
        }
        in.readProperties(properties);
    }

    void convertToTextTokens(List<String> tokens, String prefix) {
        tokens.add(prefix + name);
        ProtocolDescriptor.convertPropertiesToTextTokens(tokens, properties);
    }

    int appendFromTextTokens(List<String> tokens, String prefix, int i) {
        id = ID_UNKNOWN;
        name = tokens.get(i++).substring(prefix.length());
        MessageType messageType = MessageType.findByName(name);
        if (messageType != null)
            id = messageType.getId();
        return ProtocolDescriptor.appendPropertiesFromTextTokens(tokens, properties, i);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MessageDescriptor && id == ((MessageDescriptor) o).id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        if (properties.isEmpty())
            return name;
        return name + properties;
    }
}
