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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.PrimitiveIterator;

/**
 * This class implements the {@link LongSet} interface, backed by a hash table
 * (actually a {@link LongHashMap} instance). It makes no guarantees as to the
 * iteration order of the set; in particular, it does not guarantee that the
 * order will remain constant over time.<p>
 *
 * <b>Note that this implementation is not synchronized.</b> If multiple
 * threads access a set concurrently, and at least one of the threads modifies
 * the set, it <i>must</i> be synchronized externally.  This is typically
 * accomplished by synchronizing on some object that naturally encapsulates
 * the set. If no such object exists, the set should be "wrapped" using the
 * {@link Collections#synchronizedSet} method.  This is best done
 * at creation time, to prevent accidental unsynchronized access to the set.<p>
 *
 * The iterators returned by this class's <tt>iterator</tt> method are
 * <i>fail-fast</i>: if the set is modified at any time after the iterator is
 * created, in any way except through the iterator's own <tt>remove</tt>
 * method, the Iterator throws a {@link ConcurrentModificationException}.
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 */
public final class LongHashSet extends AbstractLongSet implements Cloneable, Serializable {
    private static final long serialVersionUID = -1317101846278749919L;

    /**
     * Backing map.
     */
    private transient LongHashMap<?> map;

    /**
     * Constructs a new, empty set.
     */
    public LongHashSet() {
        map = new LongHashMap();
    }

    /**
     * Constructs a new set containing all elements from the specified
     * collection.
     */
    public LongHashSet(Collection<? extends Long> c) {
        map = new LongHashMap(c.size());
        addAll(c);
    }

    /**
     * Constructs a new set with a specified capacity.
     */
    public LongHashSet(int capacity) {
        map = new LongHashMap(capacity);
    }

    /**
     * Constructs a new set backed by a specified map.
     */
    LongHashSet(LongHashMap map) {
        this.map = map;
    }

    // Implements LongSet#longIterator()
    @Override
    public LongIterator longIterator() {
        return (LongIterator) map.newKeySetIterator();
    }

    // Implements Set#size()
    @Override
    public int size() {
        return map.size();
    }

    // Implements Set#isEmpty()
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    // Impelements LongSet#contains(long)
    @Override
    public boolean contains(long key) {
        return map.containsKey(key);
    }

    // Implements LongSet#add(long)
    @Override
    public boolean add(long key) {
        return map.putExplicitly(key, null) == LongHashMap.NOT_FOUND;
    }

    // Implements LongSet#remove(long)
    @Override
    public boolean remove(long key) {
        return map.removeExplicitly(key, null, false) != LongHashMap.NOT_FOUND;
    }

    // Implements Set#clear()
    @Override
    public void clear() {
        map.clear();
    }

    /**
     * Returns a shallow copy of this set: the elements themselves are
     * not cloned.
     */
    @Override
    public Object clone() {
        try {
            LongHashSet new_set = (LongHashSet) super.clone();
            new_set.map = (LongHashMap) map.clone();
            return new_set;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    /**
     * Makes sure that no rehashes or memory reallocations will be
     * needed until <code>size() &lt;= capacity</code>.
     * @see LongHashMap#ensureCapacity(int)
     */
    public void ensureCapacity(int capacity) {
        map.ensureCapacity(capacity);
    }

    /**
     * Compacts memory usage of this hashtable. The hashtable's capacity
     * is decreased to the minimum needed size.
     * @see LongHashMap#compact()
     */
    public void compact() {
        map.compact();
    }

    /**
     * Compacts memory usage of this hashtable, but requested capacity
     * is retained.
     * #see LongHashMap#compact(int)
     */
    public void compact(int capacity) {
        map.compact(capacity);
    }

    /**
     * Removes all elements and frees all memory.
     * @see LongHashMap#clearAndCompact()
     */
    public void clearAndCompact() {
        map.clearAndCompact();
    }

    /**
     * Removes all elements and compacts memory usage of this hashtable,
     * but requested capacity is retained.
     * @see LongHashMap#clearAndCompact(int)
     */
    public void clearAndCompact(int capacity) {
        map.clearAndCompact(capacity);
    }

    /**
     * Save the state of this set to a stream (that is, serialize this set).
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        //s.defaultWriteObject();
        s.writeInt(map.size());

        // Write out all elements
        for (PrimitiveIterator.OfLong it = map.newKeySetIterator(); it.hasNext(); )
            s.writeLong(it.nextLong());
    }

    /**
     * Reconstitute the set instance from a stream (that is, deserialize it).
     */
    private void readObject(ObjectInputStream s) throws IOException {
        //s.defaultReadObject();
        int size = s.readInt();
        map = new LongHashMap(size);

        // Read in all elements
        for (int i = 0; i < size; i++)
            map.put(s.readLong(), null);
    }
}
