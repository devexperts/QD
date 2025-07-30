/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.annotation.Internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

@Internal
public class PropertiesUtil {

    // Class of map implementation used by Collections.singletonMap
    @SuppressWarnings("rawtypes")
    private static final Class<? extends Map> SINGLETON_MAP_CLASS = Collections.singletonMap("", "").getClass();

    private PropertiesUtil() {} // never instantiate

    /**
     * Converts provided map to an immutable representative with the same content.
     * May return the original map if detects that it's already immutable.
     *
     * @param map a map to be converted
     * @param <K> key type
     * @param <V> value type
     * @return immutable representation of the provided map
     */
    public static <K, V> Map<K, V> getImmutableMap(@Nullable Map<K, V> map) {
        if (map == null || map.isEmpty())
            return Collections.emptyMap();
        if (map.getClass() == SINGLETON_MAP_CLASS)
            return map;
        if (map.size() == 1) {
            Map.Entry<K, V> entry = map.entrySet().iterator().next();
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
