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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class provides a skeletal implementation of the {@link LongMap}
 * interface, to minimize the effort required to implement this interface.<p>
 *
 * @see AbstractMap
 */
public abstract class AbstractLongMap<V> extends AbstractMap<Long,V> implements LongMap<V> {
    // Abstract methods to be overriden
    @Override
    public abstract int size();
    @Override
    public abstract void clear();
    @Override
    public abstract boolean containsKey(long key);
    @Override
    public abstract V get(long key);
    @Override
    public abstract V put(long key, V value);
    @Override
    public abstract V remove(long key);

    @Override
    public abstract LongSet longKeySet();
    @Override
    public abstract Collection<V> values();
    @Override
    public abstract Set<Map.Entry<Long,V>> entrySet();

    // :TODO: Implement efficient bulk operations (xxxAll) between LongMaps

    // Implements Map#containsKey(Object)
    @Override
    public final boolean containsKey(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) && containsKey((long) (Long) key);
    }

    // Implements Map#get(Object)
    @Override
    public final V get(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) ? get((long) (Long) key) : null;
    }

    // Implements Map#put(Object, Object)
    @Override
    public final V put(Long key, V value) {
        return put((long) key, value);
    }

    // Implements Map#remove(Object)
    @Override
    public final V remove(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) ? remove((long) (Long) key) : null;
    }

    // Implements Map#getOrDefault(Object, Object)
    @Override
    public final V getOrDefault(Object key, V defaultValue) {
        return (key instanceof Long) ? getOrDefault((long) (Long) key, defaultValue) : defaultValue;
    }


    // Implements Map#putIfAbsent(Object, Object)
    @Override
    public final V putIfAbsent(Long key, V value) {
        return putIfAbsent((long) key, value);
    }

    // Implements Map#replace(Object, Object, Object)
    @Override
    public final boolean replace(Long key, V oldValue, V newValue) {
        return replace((long) key, oldValue, newValue);
    }

    // Implements Map#replace(Object, Object)
    @Override
    public final V replace(Long key, V value) {
        return replace((long) key, value);
    }

    // Implements Map#computeIfAbsent(Object, Function)
    @Override
    public final V computeIfAbsent(Long key, Function<? super Long, ? extends V> mappingFunction) {
        return computeIfAbsent((long) key, mappingFunction);
    }

    // Implements Map#computeIfPresent(Object, BiFunction)
    @Override
    public final V computeIfPresent(Long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return computeIfPresent((long) key, remappingFunction);
    }

    // Implements Map#compute(Object, BiFunction)
    @Override
    public final V compute(Long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return compute((long) key, remappingFunction);
    }

    // Implements Map#merge(Object, Object, BiFunction)
    @Override
    public final V merge(Long key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return merge((long) key, value, remappingFunction);
    }

    // Implements Map#remove(Object, Object)
    @Override
    public final boolean remove(Object key, Object value) {
        return (key instanceof Long) ? remove((long) (Long) key, value) : false;
    }

    // Implements Map#keySet()

    /**
     * Returns the same value as {@link #longKeySet()} method does.
     */
    @Override
    public final Set<Long> keySet() {
        return longKeySet();
    }
}

