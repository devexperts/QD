/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme.impl;

import com.devexperts.qd.SerialFieldType;
import com.dxfeed.scheme.EmbeddedTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link EmbeddedTypes}.
 * <p>
 * This implementation maps all types accessible in {@link SerialFieldType} to their lower-cased names and adds
 * several handy aliases:
 * <ul>
 *     <li>{@code char} for {@code utf_char}.
 *     <li>{@code flags} for {@code compact_int}.
 *     <li>{@code time_nano_part} for {@code compact_int}.
 *     <li>{@code exchange} for {@code utf_char}.
 * </ul>
 * Also, {@code decimal} is renamed to {@code tiny_decimal}.
 */
public class DefaultEmbeddedTypes implements EmbeddedTypes {
    private static final String TYPE_FLAGS = "flags";
    private static final String TYPE_TIME_NANO_PART = "time_nano_part";
    private static final String TYPE_EXCHANGE = "exchange";
    private static final String TYPE_CHAR = "char";

    private static final Map<String, String> TYPE_RENAMES;
    private static final Map<String, SerialFieldType> ALL_TYPES = new HashMap<>();

    static {
        final int fieldModifiers = Modifier.STATIC | Modifier.FINAL | Modifier.PUBLIC;
        final Pattern namePattern = Pattern.compile("[A-Z_]+");

        TYPE_RENAMES = new HashMap<>();
        TYPE_RENAMES.put("decimal", "tiny_decimal");

        // Use reflection to extract standard types
        Class<SerialFieldType> clazz = SerialFieldType.class;
        for (Field f : clazz.getDeclaredFields()) {
            // We need public static final fields
            if ((f.getModifiers() & fieldModifiers) != fieldModifiers) {
                continue;
            }
            // We need fields of type SerialFieldType
            if (f.getType() != clazz) {
                continue;
            }
            // We need fields with [A-Z_]+ names
            if (!namePattern.matcher(f.getName()).matches()) {
                continue;
            }
            // Ok, it is what we need
            try {
                String name = f.getName().toLowerCase();
                SerialFieldType value = (SerialFieldType) f.get(null);
                ALL_TYPES.put(TYPE_RENAMES.getOrDefault(name, name), value);
            } catch (IllegalAccessException ignored) {
            }
        }

        // Special "signal" types
        ALL_TYPES.put(TYPE_FLAGS, SerialFieldType.COMPACT_INT);
        ALL_TYPES.put(TYPE_TIME_NANO_PART, SerialFieldType.COMPACT_INT);
        ALL_TYPES.put(TYPE_EXCHANGE, SerialFieldType.UTF_CHAR);
        ALL_TYPES.put(TYPE_CHAR, SerialFieldType.UTF_CHAR);
    }

    @Override
    public SerialFieldType getSerialType(String type) {
        return ALL_TYPES.get(type);
    }

    @Override
    public boolean canHaveBitfields(String type) {
        return TYPE_FLAGS.equals(type);
    }
}
