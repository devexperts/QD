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
package com.devexperts.qd.hub.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for configuration beans.
 */
final class ConfigHelper {
    private ConfigHelper() {
    }

    /**
     * Ensures that the given list is a mutable {@link ArrayList}.
     * If the list is null, it returns a new empty {@code ArrayList}.
     * If the list is already an {@code ArrayList}, it returns the same list.
     * Otherwise, it returns a new {@code ArrayList} containing elements from the original list.
     *
     * @param list original list
     * @param <T> element type
     * @return an {@code ArrayList} instance
     */
    static <T> List<T> ensureArrayList(List<T> list) {
        if (list instanceof ArrayList)
            return list;
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * Ensures that the given map is a mutable {@link LinkedHashMap}.
     * If the map is null, it returns a new empty {@code LinkedHashMap}.
     * If the map is already a {@code LinkedHashMap}, it returns the same map.
     * Otherwise, it returns a new {@code LinkedHashMap} containing entries from the original map.
     *
     * @param map original map
     * @param <K> key type
     * @param <V> value type
     * @return a {@code LinkedHashMap} instance
     */
    static <K, V> Map<K, V> ensureLinkedHashMap(Map<K, V> map) {
        if (map instanceof LinkedHashMap)
            return map;
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }
}
