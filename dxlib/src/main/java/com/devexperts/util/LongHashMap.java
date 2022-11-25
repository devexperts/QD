/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Set;

/**
 * Hashtable based implementation of the {@link LongMap} interface. This
 * implementation provides all of the optional map operations, and permits
 * <tt>null</tt> values. This class makes no guarantees as to
 * the order of the map; in particular, it does not guarantee that the order
 * will remain constant over time.<p>
 *
 * <b>Note that this implementation is not synchronized.</b> If multiple
 * threads access this map concurrently, and at least one of the threads
 * modifies the map structurally, it <i>must</i> be synchronized externally.
 * (A structural modification is any operation that adds or deletes one or
 * more mappings; merely changing the value associated with a key that an
 * instance already contains is not a structural modification.)  This is
 * typically accomplished by synchronizing on some object that naturally
 * encapsulates the map.  If no such object exists, the map should be
 * "wrapped" using the {@link Collections#synchronizedMap} method.
 * This is best done at creation time, to prevent accidental unsynchronized
 * access to the map.<p>
 *
 * The iterators returned by all of this class's "collection view methods" are
 * <i>fail-fast</i>: if the map is structurally modified at any time after the
 * iterator is created, in any way except through the iterator's own
 * <tt>remove</tt> or <tt>add</tt> methods, the iterator will throw a
 * {@link ConcurrentModificationException}. Thus, in the face of
 * concurrent modification, the iterator fails quickly and cleanly, rather
 * than risking arbitrary, non-deterministic behavior at an undetermined time
 * in the future.
 */
public final class LongHashMap<V> extends AbstractLongMap<V> implements Cloneable, Serializable {
    private static final long serialVersionUID = -6950829448098599560L;

    // Constants

    /**
     * The magic number that is used to compute hashcode.
     */
    private static final long MAGIC = 7046029254386354333L;

    /**
     * The initial power of the {@link #table} length.
     * @see #INITIAL_LENGTH
     */
    private static final int INITIAL_POWER = 3;

    /**
     * The initial length of the {@link #table}. This value
     * is equal to <code>1 << {@link #INITIAL_POWER}</code>.
     */
    static final int INITIAL_LENGTH = 1 << INITIAL_POWER;

    /**
     * The initial shift for hash function. This value
     * is equal to <code>64 - {@link #INITIAL_POWER}</code>.
     */
    private static final int INITIAL_SHIFT = 64 - INITIAL_POWER;

    /**
     * Maximal supported capacity of this hashtable.
     */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE / 3;

    // Fields

    /**
     * Total number of elements in the hashtable.
     */
    private transient int count;

    /**
     * The threshold for the number of elements in the hashtable. The hashtable
     * grows when {@code count > threshold} is true before the attempt
     * to add an element. The value of the threshold is always equal to
     * <code>(table.length << 1) / 3<code> if {@link #table} is not
     * {@code null} or 0 otherwise.
     */
    private transient int threshold;

    /**
     * First key in the hashtable. This variable is always defined when
     * {@code count > 0}. This place is reserved for 0 value, because
     * it cannot be stored in {@link #table}, but if the table does not
     * contain 0, then an arbitrary key is placed here.
     */
    private transient long first_key;

    /**
     * The value for the {@link #first_key}.
     */
    private transient V first_val;

    /**
     * The hashtable array. <code>count - 1<code> key values are recorded
     * in this array, i.e. everything except for the {@link #first_key}.
     * Zeroes denote unused entries in the table. The length of this array
     * is always a power of 2 and is at least {@link #INITIAL_LENGTH}.
     * It is {@code null} until it is not allocated.
     */
    private transient long[] table;

    /**
     * The values for the corresponding entries from {@link #table}.
     * The memory for this object is allocated only when any non-null mapping
     * is added to hashtable. When {@code table_val == null} all
     * mappings are treated as being mapped to {@code null}.
     */
    private transient V[] table_val;

    /**
     * The shift that is used to compute hashcode.
     */
    private transient int shift;

    /**
     * The number of times this hash has been modified. This field is used
     * to make iterators on the hash fail-fast.
     * @see ConcurrentModificationException
     */
    private transient int mod_count;

    // Constructors

    /**
     * Constructs a new, empty map.
     */
    public LongHashMap() {}

    /**
     * Constructs a new map containing all mappings from the specified map.
     */
    public LongHashMap(Map<? extends Long, ? extends V> m) {
        ensureCapacity(m.size());
        putAll(m);
    }

    /**
     * Constructs a new map with a specified capacity.
     */
    public LongHashMap(int capacity) {
        ensureCapacity(capacity);
    }

    // Map implementation

    // Implements Map#size()
    @Override
    public int size() {
        return count;
    }

    // Implements Map#isEmpty
    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    // Implements Map#clear()
    @Override
    public void clear() {
        if (count > 1) { // Clear table
            for (int h = table.length; --h >= 0; )
                table[h] = 0;
            table_val = null;
        }
        if (count > 0) { // Reset count and increment mod_count
            first_val = null;
            count = 0;
            mod_count++;
        }
    }

    // Implements Map#putAll
    @Override
    public void putAll(Map<? extends Long, ? extends V> t) {
        if (t instanceof LongHashMap) {
            // special purpose (fast) implementation when two LongHashMaps are involved
            LongHashMap<V> lhm = (LongHashMap<V>) t;
            if (lhm.count == 0)
                return; // nothing to do
            put(lhm.first_key, lhm.first_val); // treat first value
            long[] lhm_table = lhm.table;
            if (lhm_table == null)
                return; // nothing more to do
            V[] lhm_table_val = lhm.table_val;
            if (lhm_table_val == null) {
                // everything maps to null
                for (int i = lhm_table.length; --i >= 0;)
                    if (lhm_table[i] != 0)
                        put(lhm_table[i], null);
            } else {
                // everything maps to some values
                for (int i = lhm_table.length; --i >= 0;)
                    if (lhm_table[i] != 0)
                        put(lhm_table[i], lhm_table_val[i]);
            }
        } else
            super.putAll(t);
    }

    // Custom methods (work with 'long' type)

    // Implements LongMap#containsKey(long)
    @Override
    public boolean containsKey(long key) {
        if (count == 0) // Quick check for empty set
            return false;
        if (key == first_key) // Check first_key
            return true;
        if (count == 1) // Single element is in first_key always
            return false;
        int h = (int) ((key * MAGIC) >>> shift); // compute hashcode
        long k; // will store current key from table here
        while ((k = table[h]) != 0) { // It is critical to check for 0 first (key may be 0)
            if (k == key)
                return true; // we have found it!
            if (h == 0)
                h = table.length;
            h--;
        }
        return false;
    }

    // Implements LongMap#get(long)
    @Override
    public V get(long key) {
        V result = getExplicitly(key);
        return (result == NOT_FOUND) ? null : result;
    }

    // Implements LongMap#put(long, Object)
    @Override
    public V put(long key, V value) {
        V result = putExplicitly(key, value);
        return (result == NOT_FOUND) ? null : result;
    }

    // Implements LongMap#remove(long)
    @Override
    public V remove(long key) {
        V result = removeExplicitly(key, null, false);
        return (result == NOT_FOUND) ? null : result;
    }

    // Implements LongMap#getOrDefault(long, Object)
    @Override
    public V getOrDefault(long key, V defaultValue) {
        V result = getExplicitly(key);
        return (result == NOT_FOUND) ? defaultValue : result;
    }

    // Implements LongMap#remove(long, Object)
    @Override
    public boolean remove(long key, Object value) {
        return removeExplicitly(key, value, true) != NOT_FOUND;
    }

    // Explicit (internal) operations

    /**
     * This values is returned by {@link #getExplicitly(long)} and
     * {@link #removeExplicitly(long, Object, boolean)} as an indication that the mapping
     * was not found.
     */
    static final Object NOT_FOUND = new Object();

    @SuppressWarnings("unchecked")
    private V notFound() {
        return (V) NOT_FOUND;
    }

    /**
     * Returns the value to which this map maps the specified key. Returns
     * {@link #NOT_FOUND} if the map contains no mapping for this key.
     * @see #get(long)
     */
    V getExplicitly(long key) {
        if (count == 0) // Quick check for empty set
            return notFound();
        if (key == first_key) // Check first_key
            return first_val;
        if (count == 1) // Single element is in first_key always
            return notFound();
        int h = (int) ((key * MAGIC) >>> shift); // compute hashcode
        long k; // will store current key from table here
        while ((k = table[h]) != 0) { // It is critical to check for 0 first (key may be 0)
            if (k == key)
                return (table_val == null) ? null : table_val[h]; // we have found it!
            if (h == 0)
                h = table.length;
            h--;
        }
        return notFound();
    }

    /**
     * Associates the specified value with the specified key in this map
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or
     *         {@link #NOT_FOUND} if there was no mapping for key.
     * @see #put(long, Object)
     */
    V putExplicitly(long key, V value) {
        if (count == 0) { // Quick add to empty set
            first_key = key;
            first_val = value;
            count++;
            mod_count++;
            return notFound();
        }
        if (key == first_key) { // Check first_key
            V old_val = first_val;
            first_val = value;
            return old_val;
        }
        if (table == null) { // we have to perform initial allocation of hashtable
            table = new long[INITIAL_LENGTH];
            shift = INITIAL_SHIFT;
            threshold = (INITIAL_LENGTH << 1) / 3;
        }
        // Actual insert
        boolean inserting_zero = key == 0;
        if (inserting_zero) { // handle zero
            key = first_key; // swap key <-> first_key (where key == 0)
            first_key = 0;
            V t_val = first_val; // swap first_val <-> value
            first_val = value;
            value = t_val;
        }
        // Here key != 0 (Wow!)
        int h = (int) ((key * MAGIC) >>> shift); // compute hashcode
        long k; // will store current key from table here
        while ((k = table[h]) != 0) {
            if (k == key) { // we have found it in hashtable
                V old_val = putValueAt(h, value);
                return inserting_zero ? notFound() : old_val;
            }
            if (h == 0)
                h = table.length;
            h--;
        }
        // This key was not found in the table
        table[h] = key; // store this key in the table
        putValueAt(h, value);
        count++;
        if (count > threshold) { // Too many keys there -- rehash it up (grow twice)
            shift--;
            rehashHelper(table.length << 1); // ... notice: it will increase mod_count
        } else
            mod_count++;
        return notFound();
    }

    /**
     * Puts value at the specified position in hashtable.
     * {@link #table_val} array is allocated if needed.
     * @return Old value from the specified position in hashtable.
     */
    @SuppressWarnings("unchecked")
    private V putValueAt(int index, V value) {
        V old_val;
        if (table_val == null) {
            old_val = null;
            if (value != null) {
                table_val = (V[]) new Object[table.length];
                table_val[index] = value;
            }
        } else {
            old_val = table_val[index];
            table_val[index] = value;
        }
        return old_val;
    }

    /**
     * Removes the mapping for this key from this map if present or if it is currently
     * mapped to the specified value.
     * @return previous value associated with specified key, or
     *         {@link #NOT_FOUND} if there was no mapping for key.
     * @see #remove(long)
     */
    V removeExplicitly(long key, Object value, boolean matchValue) {
        if (count == 0) // Quick check for empty set
            return notFound();
        if (key == first_key) { // Removing first_key
            V old_val = first_val;
            if (!matchValue || (old_val == value || (old_val != null && old_val.equals(value)))) {
                removeFirstKey();
                return old_val;
            } else {
                return notFound();
            }
        }
        if (table == null)
            return notFound(); // Nothing else
        // Removing from table
        int h = (int) ((key * MAGIC) >>> shift); // compute hashcode
        long k; // will store current key from table here
        while ((k = table[h]) != 0) { // It is critical to check for 0 first (key may be 0)
            if (k == key) { // we found the key and now we are to safely remote it
                V old_val = (table_val == null) ? null : table_val[h];
                if (!matchValue || (old_val == value || (old_val != null && old_val.equals(value)))) {
                    removeByIndex(h);
                    return old_val;
                } else {
                    return notFound();
                }
            }
            // Outer search loop continues...
            if (h == 0)
                h = table.length;
            h--;
        }
        // Failed to find anything
        return notFound();
    }

    /**
     * Removes the mapping for the {@link #first_key} from this map
     * assuming that it is present. An arbitrary replacement
     * for the first_key if fetched from the hashtable.
     */
    private void removeFirstKey() {
        count--;
        mod_count++;
        if (count == 0) { // Cool - we have nothing more to do
            first_val = null;
            return;
        }
        // Let's find some replacement for first_key
        int h = (int) ((first_key * MAGIC) >>> shift); // compute hashcode
        while (table[h] == 0) { // scan down for something non-empty
            if (h == 0)
                h = table.length;
            h--;
        }
        while (table[h] != 0) { // search for the end of sequence (for quick remove)
            if (h == 0)
                h = table.length;
            h--;
        }
        h++;
        if (h == table.length)
            h = 0;
        // HERE: table[h] != 0 && table[h-1**] == 0 and we can safely remove it
        first_key = table[h];
        table[h] = 0;
        if (table_val != null) {
            first_val = table_val[h];
            table_val[h] = null;
        } else
            first_val = null;
    }

    /**
     * Removes the mapping for the {@code table[index]} from
     * this map assuming that it is present.
     */
    private void removeByIndex(int index) {
        count--;
        mod_count++;
        int i = index;
        for (;;) {
            // INVARIANT: table[index] shall be removed
            if (i == 0)
                i = table.length;
            i--;
            long k;
            if ((k = table[i]) == 0) { // We don't mess with anything and can remove table[index] safely
                table[index] = 0;
                if (table_val != null)
                    table_val[index] = null;
                return;
            }
            // Below we are trying to check if k's link is broken or not...
            int hh = (int) ((k * MAGIC) >>> shift); // compute hashcode for 'k'
            while (hh != index && hh != i) {
                if (hh == 0)
                    hh = table.length;
                hh--;
            }
            if (hh == index) { // k's link is broken -- let's move k to table[index]
                table[index] = k;
                if (table_val != null)
                    table_val[index] = table_val[i];
                index = i;
            }
        }
    }

    // Stores different views of this map
    private transient volatile LongSet key_set;
    private transient Collection<V> values;
    private transient Set<Map.Entry<Long, V>> entry_set;

    // Implements LongSet#longKeySet()

    /**
     * Returns a set view of the keys contained in this map. The set is
     * backed by the map, so changes to the map are reflected in the set, and
     * vice-versa.  The set supports element removal, which removes the
     * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
     * <tt>clear</tt> operations. It also supports the <tt>add</tt> and
     * <tt>addAll</tt> operations -- the mappings to <tt>null</tt> are
     * added for the corresponding keys. This set is actually implemented
     * via {@link LongHashSet}.
     *
     * @return a set view of the keys contained in this map.
     */
    @Override
    public LongSet longKeySet() {
        LongSet ks = this.key_set;
        if (ks == null) {
            ks = new LongHashSet(this);
            this.key_set = ks;
        }
        return ks;
    }

    /**
     * Returns a collection view of the values contained in this map.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from this map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the values contained in this map.
     */
    @Override
    public Collection<V> values() {
        Collection<V> vals = this.values;
        if (vals == null) {
            vals = new AbstractCollection<V>() {
                @Override
                public int size() {
                    return count;
                }

                @Override
                public boolean isEmpty() {
                    return count == 0;
                }

                @Override
                public void clear() {
                    LongHashMap.this.clear();
                }

                @Override
                public Iterator<V> iterator() {
                    return new ValuesIterator();
                }
            };
            this.values = vals;
        }
        return vals;
    }

    // Implements Set#entrySet()

    /**
     * Returns a collection view of the mappings contained in this map. Each
     * element in the returned collection is a {@link LongMap.Entry}. The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the mappings contained in this map.
     * @see LongMap.Entry
     */
    @Override
    public Set<Map.Entry<Long,V>> entrySet() {
        Set<Map.Entry<Long, V>> es = this.entry_set;
        if (es == null) {
            es = new AbstractSet<Map.Entry<Long,V>>() {
                @Override
                public int size() {
                    return count;
                }

                @Override
                public boolean isEmpty() {
                    return count == 0;
                }

                @Override
                public void clear() {
                    LongHashMap.this.clear();
                }

                @Override
                public boolean contains(Object o) {
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry entry = (Map.Entry) o;
                    long key = (entry instanceof LongMap.Entry) ?
                        ((LongMap.Entry) entry).getLongKey() :
                        (Long) entry.getKey();
                    Object val = getExplicitly(key);
                    Object e_val = entry.getValue();
                    return (val == e_val) || (val != null && val.equals(e_val));
                }

                @Override
                public boolean remove(Object o) {
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry entry = (Map.Entry) o;
                    long key = (entry instanceof LongMap.Entry) ?
                        ((LongMap.Entry) entry).getLongKey() :
                        (Long) entry.getKey();
                    Object val = getExplicitly(key);
                    Object e_val = entry.getValue();
                    if (val == e_val || (val != null && val.equals(e_val))) {
                        LongHashMap.this.remove(key);
                        return true;
                    } else
                        return false;
                }

                @Override
                public Iterator<Map.Entry<Long,V>> iterator() {
                    return new EntrySetIterator();
                }
            };
            this.entry_set = es;
        }
        return es;
    }

    // Internal rehashing methods

    /**
     * Rehashes internal structure of this map so that
     * {@code table.length = (1 << new_power)}. It is
     * an error to call this method with
     * {@code new_power < INITIAL_POWER}.
     */
    private void rehash(int new_power) {
        int new_length = 1 << new_power;
        if (table != null && table.length == new_length)
            return; // Nothing to do
        shift = 64 - new_power;
        rehashHelper(new_length);
    }

    /**
     * Performs actual unconditional rehashing assuming that {@link #shift}
     * if already assigned to a correct value and {@code new_length}
     * if a correct power of 2.
     */
    @SuppressWarnings("unchecked")
    private void rehashHelper(int new_length) {
        threshold = (new_length << 1) / 3; // threshold = 2/3 * length
        mod_count++;
        if (count <= 1) { // fresh allocation -- don't need to scan anything
            table = new long[new_length];
            table_val = null;
        } else { // actual rehash -- because count > 1 we know that table != null
            long[] t = new long[new_length]; // new table
            V[] tv = null; // new table_val -- we will allocate it if needed
            for (int i = table.length; --i >= 0; ) { // scan entire old hashtable
                long k = table[i];
                if (k != 0) { // Non-empty value -- place it into new table 't'
                    int h = (int) ((k * MAGIC) >>> shift); // compute hashcode
                    while (t[h] != 0) {
                        if (h == 0)
                            h = t.length;
                        h--;
                    }
                    t[h] = k;
                    // Copy value if needed
                    if (table_val != null && table_val[i] != null) {
                        if (tv == null) // Allocate new table_val 'tv' if needed
                            tv = (V[]) new Object[new_length];
                        tv[h] = table_val[i];
                    }
                }
            }
            // Rehash done -- store everything we've created and let GC take care about old ones
            table = t;
            table_val = tv;
        }
    }

    /**
     * Returns minimal power that is needed for given capacity.
     * The returned power is always at least {@link #INITIAL_POWER}.
     */
    private static int getPower(int capacity) {
        // power   length   threshold
        //   3        8         5
        //   4       16        10
        //   5       32        21
        //   6       64        42
        //  ...     ....      ....
        // Compute length we are to have so, that threshold <= capacity and it's power
        int power = INITIAL_POWER;
        capacity = (capacity * 3) >> (INITIAL_POWER + 1);
        while (capacity != 0) {
            power++;
            capacity = capacity >> 1;
        }
        return power;
    }

    // Additional public utility methods

    /**
     * Makes sure that no rehashes or memory reallocations will be
     * needed until {@code size() <= capacity}.
     * @throws IllegalArgumentException when {@code capacity > MAX_CAPACITY}.
     */
    public void ensureCapacity(int capacity) {
        if (capacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Invalid capacity");
        if (capacity <= threshold)
            return; // Nothing to do -- already have enough capacity
        rehash(getPower(capacity));
    }

    /**
     * Compacts memory usage of this hashtable. The hashtable's capacity
     * is decreased to the minimum needed size.
     */
    public void compact() {
        if (count <= 1) { // Free all data structures to hold no more that 1 mapping
            mod_count++;
            table = null;
            table_val = null;
            threshold = 0;
        } else
            rehash(getPower(count));
    }

    /**
     * Compacts memory usage of this hashtable, but requested capacity
     * is retained. This method is identical to the sequence of
     * {@link #compact()} and {@link #ensureCapacity(int)} calls,
     * but is faster, because it does not produce excessive garbage.
     * When capacity is less or equal than 1, this method is equivalent
     * to {@link #compact()}.
     * @throws IllegalArgumentException when {@code capacity > MAX_CAPACITY}.
     */
    public void compact(int capacity) {
        if (capacity <= 1 || capacity <= count) { // Simply compact such cases
            compact();
            return;
        }
        // Here capacity > count
        if (capacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Invalid capacity");
        rehash(getPower(capacity));
    }

    /**
     * Removes all mappings and frees all memory. This method is identical
     * to the sequence of {@link #clear()} and {@link #compact()} calls,
     * but is faster.
     */
    public void clearAndCompact() {
        if (count > 0 || table != null) {
            count = 0;
            mod_count++;
            first_val = null;
            table = null;
            table_val = null;
            threshold = 0;
        }
    }

    /**
     * Removes all mappings and compacts memory usage of this hashtable,
     * but requested capacity is retained. This method is identical
     * to the sequence of {@link #clear()} and {@link #compact(int)} calls,
     * but is faster. When capacity is less or equal than 1, this method is
     * equivalent to {@link #clearAndCompact()}.
     * @throws IllegalArgumentException when {@code capacity > MAX_CAPACITY}.
     */
    public void clearAndCompact(int capacity) {
        if (capacity <= 1) { // they don't ask for big capacity
            clearAndCompact(); // just clear and compact
            return;
        }
        if (capacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Invalid capacity");
        int new_power = getPower(capacity);
        int new_length = 1 << new_power;
        if (table != null && table.length == new_length) { // we have the memory we need
            clear(); // just clear it
            return;
        }
        // Reallocate memory
        count = 0;
        mod_count++;
        first_val = null;
        table = new long[new_length];
        table_val = null;
        shift = 64 - new_power;
        threshold = (new_length << 1) / 3;
    }

    /**
     * Returns a shallow copy of this {@link LongHashMap} instance:
     * the values themselves are not cloned.
     */
    @Override
    public Object clone() {
        try {
            LongHashMap m = (LongHashMap) super.clone();
            if (table != null)
                m.table = table.clone();
            if (table_val != null)
                m.table_val = table_val.clone();
            // Cleanup collection-views of the cloned map
            m.key_set = null;
            m.values = null;
            m.entry_set = null;
            m.mod_count = 0; // And clear mod_count, too
            return m;
        } catch (CloneNotSupportedException ex) {
            throw new InternalError();
        }
    }

    // Serializable implementation

    /**
     * Save the state of this map to a stream (that is, serialize this map).
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        //s.defaultWriteObject();
        s.writeInt(count);
        if (count == 0)
            return;
        s.writeLong(first_key);
        s.writeObject(first_val);
        if (count == 1)
            return;
        for (int h = table.length; --h >= 0; ) {
            long k = table[h];
            if (k != 0) {
                s.writeLong(k);
                s.writeObject((table_val == null) ? null : table_val[h]);
            }
        }
    }

    /**
     * Reconstitute the map instance from a stream (that is, deserialize it).
     */
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        //s.defaultReadObject();
        int cnt = s.readInt();
        if (cnt == 0)
            return;
        first_key = s.readLong();
        first_val = (V) s.readObject();
        count = 1;
        if (cnt == 1)
            return;
        ensureCapacity(cnt); // allocate memory...
        for (int i = 1; i < cnt; i++)
            put(s.readLong(), (V) s.readObject());
    }

    /**
     * Returns a new instance for the iterator over {@link #keySet()}
     * of this map.
     */
    PrimitiveIterator.OfLong newKeySetIterator() {
        return new KeySetIterator();
    }

    /**
     * Abstract base for inner iterator classes. This iterator always
     * returns elements from the {@link #table} first, and returns
     * {@link LongHashMap#first_key} in the last place. The table is iterated
     * upwards (via index increment) to ensure that {@link #remove} will
     * not harm anything that is not processed yet
     * (that is also why {@link LongHashMap#first_key} is returned last).
     */
    private abstract class AbstractIterator<T> implements Iterator<T> {
        /**
         * This is a table reference copy for performance.
         */
        protected long[] table = LongHashMap.this.table;

        /**
         * This index points to the element in hashtable on which
         * we shall stop iteration. It points to an
         * empty slot in the {@link #table}, i.e. {@code table[last_index] == 0}
         * and is defined when {@code index >= 0}.
         */
        private int last_index;

        /**
         * This index points to the element in hashtable we are about
         * to return on the call to {@link #next()}.
         * The value of -1 indicates that no values were returned yet.
         * The value that is equal to {@link #last_index} indicates that we
         * are about to return {@link LongHashMap#first_key}. The value of -2 indicates
         * that all values were already returned.
         */
        private int index = -1;

        /**
         * The last returned value by the {@link #nextIndex()} method.
         * The value of -1 indicates that the value was not returned yet
         * (or the value returned was already removed). The value of -2
         * indicates that first_key was returned. Any non-negative value
         * indicates that the corresponding entry from the table was
         * returned.
         */
        protected int last_returned = -1;

        /**
         * This value stores original modification count for fail-fast
         * iterator implementation.
         */
        private int expected_mod_count = mod_count;

        protected AbstractIterator() {}

        // Implements Iterator#hasNext()
        @Override
        public boolean hasNext() {
            if (index == -1) // Yes, we have a lots of elements ahead, if the set is not empty
                return count > 0;
            return index != -2; // We have more elements if we're not done yet
        }

        /**
         * Computes index for the next value that shall be returned and
         * stores this index in {@link #last_returned}.
         * @see #last_returned
         */
        public void nextIndex() throws NoSuchElementException {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            if (count == 0 || index == -2) // Quick check for empty set or the case when iteration is over
                throw new NoSuchElementException();
            if (count == 1) { // Quick check for single-element set
                index = last_returned = -2;
                return;
            }
            // HERE: count > 1
            if (index == -1) { // We're for a first time here - init everything
                while (table[last_index] != 0) // Init last_index so that table[last_index] == 0
                    last_index++;
                index = last_index + 1; // Init index
                // We can prove that index < table.length here
            }
            // Now advance index in a forward direction until it points to something non-zero
            while (table[index] == 0 && index != last_index) {
                index++;
                if (index >= table.length)
                    index = 0;
            }
            if (index == last_index) { // We shall return first_key
                index = last_returned = -2;
                return;
            }
            // Return whatever index points to
            last_returned = index;
            index++; // Advance index to return something else next time...
            if (index >= table.length)
                index = 0;
        }

        // Implements Iterator#remove()
        @Override
        public void remove() {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            if (last_returned == -1) // complain if we have nothing to remove
                throw new IllegalStateException();
            // The call below shall properly remove element without any harm to iterator
            // ... moreover, remove must always be successful ...
            if (last_returned >= 0)
                removeByIndex(last_returned);
            else
                removeFirstKey();
            expected_mod_count++;
            last_returned = -1;
        }

        // Abstract method Iterator#next() to be overridden
        @Override
        @SuppressWarnings({"IteratorNextCanNotThrowNoSuchElementException"})
        public abstract T next();
    }

    /**
     * Inner iterator class for {@link LongHashMap#longKeySet()}.
     */
    private final class KeySetIterator extends AbstractIterator<Long> implements LongIterator {
        KeySetIterator() {}

        // Implements LongIterator#nextLong()
        @Override
        public long nextLong() throws NoSuchElementException {
            nextIndex();
            return (last_returned >= 0) ? this.table[last_returned] : first_key;
        }

        // Implements Iterator#next()
        @Override
        public Long next() {
            return nextLong();
        }
    }

    /**
     * Inner iterator class for {@link LongHashMap#values()}.
     */
    private final class ValuesIterator extends AbstractIterator<V> {
        ValuesIterator() {}

        // Implements Iterator#next()
        @Override
        public V next() {
            nextIndex();
            return (last_returned >= 0) ?
                ((table_val == null) ? null : table_val[last_returned]) :
                first_val;
        }
    }

    /**
     * Inner iterator class for {@link LongHashMap#entrySet()}.
     */
    private final class EntrySetIterator extends AbstractIterator<Map.Entry<Long,V>> {
        EntrySetIterator() {}

        // Implements Iterator#next()
        @Override
        public Map.Entry<Long,V> next() {
            nextIndex();
            return (last_returned >= 0) ?
                new IndexEntry(last_returned) :
                new FirstKeyEntry();
        }
    }

    /**
     * Abstract entry implementation that is consistent with the
     * {@code java.util.HashMap.Entry}.
     */
    private abstract class AbstractEntry implements LongMap.Entry<V> {
        protected final int expected_mod_count = mod_count;
        protected final long key;

        protected AbstractEntry(long key) {
            this.key = key;
        }

        @Override
        public long getLongKey() {
            return key;
        }

        @Override
        public Long getKey() {
            return key;
        }

        @Override
        public abstract V getValue();
        @Override
        public abstract V setValue(V value);

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry e = (Map.Entry) o;
            // Check key equality
            if (e instanceof LongMap.Entry) {
                if (((LongMap.Entry) e).getLongKey() != key)
                    return false;
            } else {
                Object e_key = e.getKey();
                if (!(e_key instanceof Long))
                    return false;
                if ((Long) e_key != key)
                    return false;
            }
            // Check value equality
            Object value = getValue();
            return (value == null ? e.getValue() == null : value.equals(e.getValue()));
        }

        @Override
        public int hashCode() {
            Object value = getValue();
            int hash = (int) (key ^ (key >> 32)); // !!! This is hashcode of java.lang.Long
            return hash ^ ((value == null) ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + "=" + getValue();
        }
    }

    /**
     * Entry implementation for {@link LongHashMap#first_key} mapping.
     */
    private final class FirstKeyEntry extends AbstractEntry {
        FirstKeyEntry() {
            super(LongHashMap.this.first_key);
        }

        @Override
        public V getValue() {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            return first_val;
        }

        @Override
        public V setValue(V value) {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            V old_val = first_val;
            first_val = value;
            return old_val;
        }
    }

    /**
     * Entry implementation for {@link LongHashMap#table} mappings.
     */
    private final class IndexEntry extends AbstractEntry {
        private final int index;

        IndexEntry(int index) {
            super(LongHashMap.this.table[index]);
            this.index = index;
        }

        @Override
        public V getValue() {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            return (table_val == null) ? null : table_val[index];
        }

        @Override
        public V setValue(V value) {
            if (mod_count != expected_mod_count)
                throw new ConcurrentModificationException();
            return putValueAt(index, value);
        }
    }
}
