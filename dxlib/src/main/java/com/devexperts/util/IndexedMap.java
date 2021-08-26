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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collector;
import javax.annotation.Nonnull;

/**
 * A wrapper around {@link IndexedSet} which implements {@link Map Map} interface.
 *
 * <p>The <b>IndexedMap</b> does not support <b>null</b> values, but it supports <b>null</b> keys
 * if they are supported by corresponding {@link IndexerFunction}. The <b>IndexedMap</b> is serializable.
 */
public class IndexedMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    private static final long serialVersionUID = 0L;

    private IndexedSet<K, V> set;

    private transient KeySet<K, V> keySet;
    private transient EntrySet<K, V> entrySet;

    // ========== static factory methods ===========

    /**
     * Creates new empty map with default indexer {@link IndexerFunction#DEFAULT}.
     */
    public static <V> IndexedMap<V, V> create() {
        return new IndexedMap<>();
    }

    /**
     * Creates new empty map with default identity indexer.
     */
    @SuppressWarnings("unchecked")
    public static <V> IndexedMap<V, V> createIdentity() {
        return new IndexedMap<>((IndexerFunction.IdentityKey<V, V>) IndexerFunction.DEFAULT_IDENTITY_KEY);
    }

    /**
     * Creates new empty map with specified indexer.
     */
    public static <K, V> IndexedMap<K, V> create(IndexerFunction<K, ? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified identity indexer.
     */
    public static <K, V> IndexedMap<K, V> createIdentity(IndexerFunction.IdentityKey<K, ? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified int indexer.
     */
    public static <V> IndexedMap<Integer, V> createInt(IndexerFunction.IntKey<? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified long indexer.
     */
    public static <V> IndexedMap<Long, V> createLong(IndexerFunction.LongKey<? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified indexer.
     *
     * @deprecated Use {@link #createInt(IndexerFunction.IntKey) createInt(indexer)}
     */
    @Deprecated
    public static <V> IndexedMap<Integer, V> create(IndexerFunction.IntKey<? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified indexer.
     *
     * @deprecated Use {@link #createLong(IndexerFunction.LongKey) createLong(indexer)}
     */
    @Deprecated
    public static <V> IndexedMap<Long, V> create(IndexerFunction.LongKey<? super V> indexer) {
        return new IndexedMap<>(indexer);
    }

    /**
     * Creates new empty map with specified indexer and specified initial capacity.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withCapacity(int) withCapacity(initialCapacity)}
     */
    @Deprecated
    public static <K, V> IndexedMap<K, V> create(IndexerFunction<K, ? super V> indexer, int initialCapacity) {
        return new IndexedMap<>(indexer, initialCapacity);
    }

    /**
     * Creates new empty map with specified indexer and specified initial capacity.
     *
     * @deprecated Use {@link #createInt(IndexerFunction.IntKey) createInt(indexer)}.{@link #withCapacity(int) withCapacity(initialCapacity)}
     */
    @Deprecated
    public static <V> IndexedMap<Integer, V> create(IndexerFunction.IntKey<? super V> indexer, int initialCapacity) {
        return new IndexedMap<>(indexer, initialCapacity);
    }

    /**
     * Creates new empty map with specified indexer and specified initial capacity.
     *
     * @deprecated Use {@link #createLong(IndexerFunction.LongKey) createLong(indexer)}.{@link #withCapacity(int) withCapacity(initialCapacity)}
     */
    @Deprecated
    public static <V> IndexedMap<Long, V> create(IndexerFunction.LongKey<? super V> indexer, int initialCapacity) {
        return new IndexedMap<>(indexer, initialCapacity);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified collection.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withElements(Collection) withElements(c)}
     */
    @Deprecated
    public static <K, V> IndexedMap<K, V> create(IndexerFunction<K, ? super V> indexer, Collection<? extends V> c) {
        return new IndexedMap<>(indexer, c);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified collection.
     *
     * @deprecated Use {@link #createInt(IndexerFunction.IntKey) createInt(indexer)}.{@link #withElements(Collection) withElements(c)}
     */
    @Deprecated
    public static <V> IndexedMap<Integer, V> create(IndexerFunction.IntKey<? super V> indexer, Collection<? extends V> c) {
        return new IndexedMap<>(indexer, c);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified collection.
     *
     * @deprecated Use {@link #createLong(IndexerFunction.LongKey) createLong(indexer)}.{@link #withElements(Collection) withElements(c)}
     */
    @Deprecated
    public static <V> IndexedMap<Long, V> create(IndexerFunction.LongKey<? super V> indexer, Collection<? extends V> c) {
        return new IndexedMap<>(indexer, c);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified map.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withElements(Map) withElements(map)}
     */
    @Deprecated
    public static <K, V> IndexedMap<K, V> create(IndexerFunction<K, ? super V> indexer, Map<? extends K, ? extends V> map) {
        return new IndexedMap<>(indexer, map);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified map.
     *
     * @deprecated Use {@link #createInt(IndexerFunction.IntKey) createInt(indexer)}.{@link #withElements(Map) withElements(map)}
     */
    @Deprecated
    public static <V> IndexedMap<Integer, V> create(IndexerFunction.IntKey<? super V> indexer, Map<Integer, ? extends V> map) {
        return new IndexedMap<>(indexer, map);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified map.
     *
     * @deprecated Use {@link #createLong(IndexerFunction.LongKey) createLong(indexer)}.{@link #withElements(Map) withElements(map)}
     */
    @Deprecated
    public static <V> IndexedMap<Long, V> create(IndexerFunction.LongKey<? super V> indexer, Map<Long, ? extends V> map) {
        return new IndexedMap<>(indexer, map);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with default indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    @SuppressWarnings("unchecked")
    public static <V> Collector<V, ?, ? extends IndexedMap<V, V>> collector() {
        return collector((IndexerFunction<V, ? super V>) IndexerFunction.DEFAULT);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with default identity indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    @SuppressWarnings("unchecked")
    public static <V> Collector<V, ?, ? extends IndexedMap<V, V>> collectorIdentity() {
        return collector((IndexerFunction.IdentityKey<V, V>) IndexerFunction.DEFAULT_IDENTITY_KEY);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    public static <K, V> Collector<V, ?, ? extends IndexedMap<K, V>> collector(IndexerFunction<K, ? super V> indexer) {
        return Collector.of(() -> create(indexer), IndexedMap::put,
            (left, right) -> { left.putAll(right); return left; },
            Collector.Characteristics.UNORDERED, Collector.Characteristics.IDENTITY_FINISH);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified identity indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    public static <K, V> Collector<V, ?, ? extends IndexedMap<K, V>> collectorIdentity(IndexerFunction.IdentityKey<K, ? super V> indexer) {
        return collector((IndexerFunction<K, ? super V>) indexer);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified int indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    public static <V> Collector<V, ?, ? extends IndexedMap<Integer, V>> collectorInt(IndexerFunction.IntKey<? super V> indexer) {
        return collector((IndexerFunction<Integer, ? super V>) indexer);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified long indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     */
    public static <V> Collector<V, ?, ? extends IndexedMap<Long, V>> collectorLong(IndexerFunction.LongKey<? super V> indexer) {
        return collector((IndexerFunction<Long, ? super V>) indexer);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * @deprecated Use {@link #collectorInt(IndexerFunction.IntKey) collectorInt(indexer)}
     */
    @Deprecated
    public static <V> Collector<V, ?, ? extends IndexedMap<Integer, V>> collector(IndexerFunction.IntKey<? super V> indexer) {
        return collector((IndexerFunction<Integer, ? super V>) indexer);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new {@code IndexedMap} with specified indexer.
     * This is an {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * @deprecated Use {@link #collectorLong(IndexerFunction.LongKey) collectorLong(indexer)}
     */
    @Deprecated
    public static <V> Collector<V, ?, ? extends IndexedMap<Long, V>> collector(IndexerFunction.LongKey<? super V> indexer) {
        return collector((IndexerFunction<Long, ? super V>) indexer);
    }

    // ========== Construction and Sizing Operations ==========

    /**
     * Creates new empty map with default indexer {@link IndexerFunction#DEFAULT}.
     */
    public IndexedMap() {
        this(0);
    }

    /**
     * Creates new empty map with default indexer {@link IndexerFunction#DEFAULT} and specified initial capacity.
     */
    @SuppressWarnings("unchecked")
    public IndexedMap(int initialCapacity) {
        this(IndexerFunction.DEFAULT, initialCapacity);
    }

    /**
     * Creates new empty map with specified indexer.
     */
    protected IndexedMap(IndexerFunction<K, ? super V> indexer) {
        this(indexer, 0);
    }

    /**
     * Creates new empty map with specified indexer.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}
     */
    @Deprecated
    public IndexedMap(Indexer<K, ? super V> indexer) {
        this((IndexerFunction<K, ? super V>) indexer);
    }

    /**
     * Creates new empty map with specified indexer and specified initial capacity.
     */
    protected IndexedMap(IndexerFunction<K, ? super V> indexer, int initialCapacity) {
        set = new IndexedSet<>(indexer, initialCapacity);
    }

    /**
     * Creates new empty map with specified indexer and specified initial capacity.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withCapacity(int) withCapacity(initialCapacity)}
     */
    @Deprecated
    public IndexedMap(Indexer<K, ? super V> indexer, int initialCapacity) {
        this((IndexerFunction<K, ? super V>) indexer, initialCapacity);
    }

    /**
     * Creates a new map containing the elements in the specified collection.
     * If specified collection is an {@link IndexedSet}, then new indexed map uses same indexer,
     * otherwise it uses default indexer {@link IndexerFunction#DEFAULT}.
     */
    @SuppressWarnings("unchecked")
    public IndexedMap(Collection<V> c) {
        this(c instanceof IndexedSet ? ((IndexedSet<K, V>) c).getIndexerFunction() : IndexerFunction.DEFAULT, c);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified collection.
     */
    protected IndexedMap(IndexerFunction<K, ? super V> indexer, Collection<? extends V> c) {
        this(indexer, c.size());
        putAll(c);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified collection.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withElements(Collection) withElements(c)}
     */
    @Deprecated
    public IndexedMap(Indexer<K, ? super V> indexer, Collection<? extends V> c) {
        this((IndexerFunction<K, ? super V>) indexer, c);
    }

    /**
     * Creates a new map containing the elements in the specified map.
     * If specified collection is an {@link IndexedMap}, then new indexed map uses same indexer,
     * otherwise it uses default indexer {@link IndexerFunction#DEFAULT}.
     */
    @SuppressWarnings("unchecked")
    public IndexedMap(Map<K, V> map) {
        this(map instanceof IndexedMap ? ((IndexedMap<K, V>) map).getIndexerFunction() : IndexerFunction.DEFAULT, map);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified map.
     */
    protected IndexedMap(IndexerFunction<K, ? super V> indexer, Map<? extends K, ? extends V> map) {
        this(indexer, map.size());
        putAll(map);
    }

    /**
     * Creates a new map with specified indexer containing the elements in the specified map.
     *
     * @deprecated Use {@link #create(IndexerFunction) create(indexer)}.{@link #withElements(Map) withElements(map)}
     */
    @Deprecated
    public IndexedMap(Indexer<K, ? super V> indexer, Map<? extends K, ? extends V> map) {
        this((IndexerFunction<K, ? super V>) indexer, map);
    }

    /**
     * Creates a new map which wraps specified indexed set and provides a {@link Map Map} view for it.
     * The wrapping works only if <b>wrap</b> parameter is <b>true</b>. If <b>wrap</b> parameter is
     * <b>false</b> then new independent map is created containing the elements in the specified set -
     * see {@link #IndexedMap(Collection) IndexedMap(Collection)} constructor.
     */
    public IndexedMap(IndexedSet<K, V> set, boolean wrap) {
        if (set == null)
            throw new NullPointerException("Set is null.");
        this.set = wrap ? set : new IndexedSet<>(set.getIndexerFunction(), set);
    }

    /**
     * Returns a shallow copy of this map - the keys and values themselves are not cloned.
     */
    @SuppressWarnings("unchecked")
    @Override
    public IndexedMap<K, V> clone() {
        try {
            IndexedMap<K, V> result = (IndexedMap<K, V>) super.clone();
            result.set = result.set.clone();
            result.keySet = null;
            result.entrySet = null;
            return result;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    /**
     * Increases the capacity of this map instance, if necessary, to ensure that it
     * can hold at least the number of elements specified by the capacity argument.
     * <p>
     * Returns <b>this</b> map instance for convenience.
     */
    public IndexedMap<K, V> withCapacity(int capacity) {
        ensureCapacity(capacity);
        return this;
    }

    /**
     * Puts all of the elements in the specified collection into this map.
     * <p>
     * Returns <b>this</b> map instance for convenience.
     */
    public IndexedMap<K, V> withElements(Collection<? extends V> c) {
        ensureCapacity(c.size());
        putAll(c);
        return this;
    }

    /**
     * Puts all of the elements in the specified map into this map.
     * <p>
     * Returns <b>this</b> map instance for convenience.
     */
    public IndexedMap<K, V> withElements(Map<K, ? extends V> map) {
        ensureCapacity(map.size());
        putAll(map);
        return this;
    }

    /**
     * Increases the capacity of this map instance, if necessary, to ensure that it
     * can hold at least the number of elements specified by the capacity argument.
     */
    public void ensureCapacity(int capacity) {
        set.ensureCapacity(capacity);
    }

    /**
     * Trims the capacity of this map instance to be the map's current size.
     * An application can use this operation to minimize the storage of this map instance.
     */
    public void trimToSize() {
        set.trimToSize();
    }

    /**
     * Removes all elements from this map.
     */
    @Override
    public void clear() {
        set.clear();
    }

    // ========== Query Operations ==========

    /**
     * Returns indexer used to distinguish and identify elements in this map.
     *
     * @deprecated Use {@link #getIndexerFunction()}
     */
    @Deprecated
    public Indexer<K, ? super V> getIndexer() {
        return set.getIndexer();
    }

    /**
     * Returns indexer function used to distinguish and identify elements in this map.
     */
    public IndexerFunction<K, ? super V> getIndexerFunction() {
        return set.getIndexerFunction();
    }

    /**
     * Returns indexed set used by this map for actual data storage.
     */
    public IndexedSet<K, V> getIndexedSet() {
        return set;
    }

    /**
     * Returns a collection view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are reflected
     * in the collection, and vice-versa. The view supports all operations.
     */
    @Nonnull
    @Override
    public Collection<V> values() {
        return set;
    }

    /**
     * Returns a set view of the keys contained in this map. The set is backed by the map,
     * so changes to the map are reflected in the set, and vice-versa.
     * The view supports all operations except operations which add new elements.
     */
    @Nonnull
    @Override
    public Set<K> keySet() {
        if (keySet == null)
            keySet = new KeySet<>(set);
        return keySet;
    }

    /**
     * Returns a set view of the mapping contained in this map. Each element in the returned set
     * is a {@link Map.Entry Map.Entry}. The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa. The view supports all operations.
     */
    @Nonnull
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        if (entrySet == null)
            entrySet = new EntrySet<>(set);
        return entrySet;
    }

    /**
     * Returns the number of elements in this map.
     */
    @Override
    public int size() {
        return set.size();
    }

    /**
     * Tests if this map has no elements.
     */
    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    /**
     * Returns the element from this map which matches specified key or <b>null</b> if none were found.
     * <p>
     * Note, that unlike {@link HashMap#get},
     * this method might throw {@link ClassCastException} if key is of the wrong class.
     *
     * @deprecated Use {@link #getByKey} to be explicit about type and intent.
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        return set.getByKey((K) key);
    }

    /**
     * Returns the element from this map which matches specified value or <b>null</b> if none were found.
     */
    public V getByValue(V value) {
        return set.getByValue(value);
    }

    /**
     * Returns the element from this map which matches specified key or <b>null</b> if none were found.
     */
    public V getByKey(K key) {
        return set.getByKey(key);
    }

    /**
     * Returns the element from this map which matches specified key or <b>null</b> if none were found.
     */
    public V getByKey(long key) {
        return set.getByKey(key);
    }

    /**
     * Returns <b>true</b> if this map contains element which matches specified value.
     * <p>
     * Note, that unlike {@link HashMap#containsValue},
     * this method might throw {@link ClassCastException} if value is of the wrong class.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean containsValue(Object value) {
        return set.containsValue((V) value);
    }

    /**
     * Returns <b>true</b> if this map contains element which matches specified key.
     * <p>
     * Note, that unlike {@link HashMap#containsKey},
     * this method might throw {@link ClassCastException} if key is of the wrong class.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        return set.containsKey((K) key);
    }

    /**
     * Returns <b>true</b> if this map contains element which matches specified key.
     */
    public boolean containsKey(long key) {
        return set.containsKey(key);
    }

    // ========== Modification Operations ==========

    /**
     * Puts specified element into this map and returns previous element that matches specified one.
     */
    public V put(V value) {
        return set.put(value);
    }

    /**
     * Puts specified element into this map and returns previous element that matches specified one.
     *
     * @throws IllegalArgumentException if specified key does not match specified value.
     */
    @Override
    public V put(K key, V value) {
        if (!set.getIndexerFunction().matchesByKey(key, value))
            throw new IllegalArgumentException("Key does not match value.");
        return set.put(value);
    }

    /**
     * Removes the element from this map which matches specified key if it is present
     * and returns removed element or <b>null</b> if none were found.
     * <p>
     * Note, that unlike {@link HashMap#remove},
     * this method might throw {@link ClassCastException} if key is of the wrong class.
     *
     * @deprecated Use {@link #removeKey} to be explicit about type and intent.
     */
    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        return set.removeKey((K) key);
    }

    /**
     * Removes the element from this map which matches specified value if it is present
     * and returns removed element or <b>null</b> if none were found.
     */
    public V removeValue(V value) {
        return set.removeValue(value);
    }

    /**
     * Removes the element from this map which matches specified key if it is present
     * and returns removed element or <b>null</b> if none were found.
     */
    public V removeKey(K key) {
        return set.removeKey(key);
    }

    /**
     * Removes the element from this map which matches specified key if it is present
     * and returns removed element or <b>null</b> if none were found.
     */
    public V removeKey(long key) {
        return set.removeKey(key);
    }

    // ========== Bulk Operations ==========

    /**
     * Puts all of the elements in the specified collection into this map.
     */
    public void putAll(Collection<? extends V> c) {
        set.addAll(c);
    }

    /**
     * Puts all of the elements in the specified map into this map.
     *
     * @throws IllegalArgumentException if specified keys do not match specified values.
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        if (!(map instanceof IndexedMap))
            for (Entry<? extends K, ? extends V> e : map.entrySet()) {
                if (!set.getIndexerFunction().matchesByKey(e.getKey(), e.getValue()))
                    throw new IllegalArgumentException("Key does not match value.");
            }
        set.addAll(map.values());
    }

    // ========== Comparison and Hashing ==========

    /**
     * Compares the specified object with this map for equality.
     * Obeys the general contract of the {@link Map#equals(Object)} method.
     */
    @SuppressWarnings("rawtypes")
    public boolean equals(Object o) {
        return o == this || o instanceof Map && entrySet().equals(((Map) o).entrySet());
    }

    /**
     * Returns the hash code value for this map.
     * Obeys the general contract of the {@link Map#hashCode()} method.
     */
    public int hashCode() {
        return entrySet().hashCode();
    }

    // ========== String Conversion ==========

    /**
     * Returns a string representation of this map.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(set.size() * 5 + 10);
        sb.append("{");
        String separator = "";
        for (V value : set) {
            sb.append(separator);
            sb.append(set.getIndexerFunction().getObjectKey(value));
            sb.append("=");
            sb.append(value);
            separator = ", ";
        }
        sb.append("}");
        return sb.toString();
    }

    // ========== Internal Implementation - Views ==========

    private static final class KeySet<K, V> extends AbstractConcurrentSet<K> implements Serializable {
        private static final long serialVersionUID = 0;

        private final IndexedSet<K, V> set;

        KeySet(IndexedSet<K, V> set) {
            this.set = set;
        }

        @Override
        public void clear() {
            set.clear();
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(Object o) {
            return set.containsKey((K) o);
        }

        @Nonnull
        @Override
        public Iterator<K> iterator() {
            return set.keyIterator();
        }

        @Override
        public boolean add(K o) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(Object o) {
            return set.removeKey((K) o) != null;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return set.removeAllKeys(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return set.retainAllKeys(c);
        }

        @Override
        public boolean removeIf(Predicate<? super K> filter) {
            return set.removeKeyIf(filter);
        }
    }

    private static final class EntrySet<K, V> extends AbstractConcurrentSet<Map.Entry<K, V>> implements Serializable {
        private static final long serialVersionUID = 0;

        private final IndexedSet<K, V> set;

        EntrySet(IndexedSet<K, V> set) {
            this.set = set;
        }

        @Override
        public void clear() {
            set.clear();
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            return set.getIndexerFunction().matchesByKey(e.getKey(), e.getValue()) && set.containsValue(e.getValue());
        }

        @Nonnull
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return set.entryIterator();
        }

        @Override
        public boolean add(Map.Entry<K, V> e) {
            if (!set.getIndexerFunction().matchesByKey(e.getKey(), e.getValue()))
                throw new IllegalArgumentException("Key does not match value.");
            return set.add(e.getValue());
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            return set.getIndexerFunction().matchesByKey(e.getKey(), e.getValue()) && set.remove(e.getValue());
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return set.removeAllEntries(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return set.retainAllEntries(c);
        }

        @Override
        public boolean removeIf(Predicate<? super Entry<K, V>> filter) {
            return set.removeEntryIf(filter);
        }
    }
}
