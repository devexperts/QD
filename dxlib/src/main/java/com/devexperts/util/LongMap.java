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

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class extends {@link Map} with methods that are specific
 * for <code>long</code> values.
 */
public interface LongMap<V> extends Map<Long, V> {
    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.
     *
     * @see #containsKey(Object)
     */
    public boolean containsKey(long key);

    /**
     * Returns the value to which this map maps the specified key.  Returns
     * <tt>null</tt> if the map contains no mapping for this key.
     *
     * @see #get(Object)
     */
    public V get(long key);

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key,
     * the old value is replaced.
     *
     * @see #put(Object, Object)
     */
    public V put(long key, V value);

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @see #remove(Object)
     */
    public V remove(long key);

    /**
     * Returns a set view of the keys contained in this map.
     *
     * @see #keySet()
     */
    public LongSet longKeySet();

    /**
     * Returns the value to which this map maps the specified key.  Returns
     * {@code defaultValue} if the map contains no mapping for this key.
     *
     * @see #getOrDefault(Object, Object)
     */
    default V getOrDefault(long key, V defaultValue) {
        V v;
        return (((v = get(key)) != null) || containsKey(key))
            ? v
            : defaultValue;
    }

    /**
     * If the specified key is not already associated with a value (or is mapped
     * to {@code null}) associates it with the given value and returns
     * {@code null}, else returns the current value.
     *
     * @see #putIfAbsent(Object, Object)
     */
    default V putIfAbsent(long key, V value) {
        V result = get(key);
        if (result == null) {
            result = put(key, value);
        }
        return result;
    }

    /**
     * Replaces the entry for the specified key only if currently
     * mapped to the specified value.
     *
     * @see #replace(Object, Object, Object)
     */
    default boolean replace(long key, V oldValue, V newValue) {
        V curValue = get(key);
        if (!Objects.equals(curValue, oldValue) || (curValue == null && !containsKey(key))) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    /**
     * Replaces the entry for the specified key only if it is
     * currently mapped to some value.
     *
     * @see #replace(Object, Object)
     */
    default V replace(long key, V value) {
        V curValue;
        if (((curValue = get(key)) != null) || containsKey(key)) {
            curValue = put(key, value);
        }
        return curValue;
    }

    /**
     * If the specified key is not already associated with a value (or is mapped
     * to {@code null}), attempts to compute its value using the given mapping
     * function and enters it into this map unless {@code null}.
     *
     * @see #computeIfAbsent(Object, Function)
     */
    default V computeIfAbsent(long key, Function<? super Long, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }
        return v;
    }

    /**
     * If the value for the specified key is present and non-null, attempts to
     * compute a new mapping given the key and its current mapped value.
     *
     * @see #computeIfPresent(Object, BiFunction)
     */
    default V computeIfPresent(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        if ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                put(key, newValue);
                return newValue;
            } else {
                remove(key);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Attempts to compute a mapping for the specified key and its current
     * mapped value (or {@code null} if there is no current mapping).
     *
     * @see #compute(Object, BiFunction)
     */
    default V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);
        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            if (oldValue != null || containsKey(key)) {
                remove(key);
                return null;
            } else {
                return null;
            }
        } else {
            put(key, newValue);
            return newValue;
        }
    }

    /**
     * If the specified key is not already associated with a value or is
     * associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is {@code null}. This
     * method may be of use when combining multiple mapped values for a key.
     *
     * @see #merge(Object, Object, BiFunction)
     */
    default V merge(long key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
        if (newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
    }

    /**
     * Removes the entry for the specified key only if it is currently
     * mapped to the specified value.
     *
     * @see #remove(Object, Object)
     */
    default boolean remove(long key, Object value) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, value) || (curValue == null && !containsKey(key))) {
            return false;
        }
        remove(key);
        return true;
    }

    /**
     * A map entry (key-value pair).
     *
     * @see Map.Entry
     */
    public interface Entry<V> extends Map.Entry<Long, V> {
        /**
         * Returns the key corresponding to this entry.
         *
         * @see Map.Entry#getKey()
         */
        public long getLongKey();
    }
}
