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
package com.devexperts.util;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Typed thread-safe key-value map where different values have different types and are distinguished
 * by globally-unique keys that are instances of {@link TypedKey}.
 */
@SuppressWarnings({"unchecked"})
public class TypedMap {
    private final Map<TypedKey<?>, Object> values = new IdentityHashMap<>();

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     */
    public synchronized <T> T get(TypedKey<T> key) {
        return (T) values.get(key);
    }

    /**
     * Changes the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced by the specified value.
     */
    public synchronized <T> void set(TypedKey<T> key, T value) {
        values.put(key, value);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
