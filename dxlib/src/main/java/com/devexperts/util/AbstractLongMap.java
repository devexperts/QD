/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.util.*;

/**
 * This class provides a skeletal implementation of the {@link LongMap}
 * interface, to minimize the effort required to implement this interface.<p>
 *
 * @see AbstractMap
 */
public abstract class AbstractLongMap<V> extends AbstractMap<Long,V> implements LongMap<V> {
    // Abstract methods to be overriden
    public abstract int size();
    public abstract void clear();
    public abstract boolean containsKey(long key);
    public abstract V get(long key);
    public abstract V put(long key, V value);
    public abstract V remove(long key);
    public abstract LongSet longKeySet();
    public abstract Collection<V> values();
    public abstract Set<Map.Entry<Long,V>> entrySet();

    // :TODO: Implement efficient bulk operations (xxxAll) between LongMaps

    // Implements Map#containsKey(Object)
    public final boolean containsKey(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) && containsKey((long) (Long) key);
    }

    // Implements Map#get(Object)
    public final V get(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) ? get((long) (Long) key) : null;
    }

    // Implements Map#put(Object, Object)
    public final V put(Long key, V value) {
        return put((long) key, value);
    }

    // Implements Map#remove(Object)
    public final V remove(Object key) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (key instanceof Long) ? remove((long) (Long) key) : null;
    }

    // Implemenets Map#keySet()

    /**
     * Returns the same value as {@link #longKeySet()} method does.
     */
    public final Set<Long> keySet() {
        return longKeySet();
    }
}

