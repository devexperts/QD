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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.PrimitiveIterator;

/**
 * This class provides a skeletal implementation of the {@link LongSet}
 * interface, to minimize the effort required to implement this interface.<p>
 *
 * @see AbstractSet
 */
public abstract class AbstractLongSet extends AbstractSet<Long> implements LongSet {
    // Abstract methods to be overriden
    @Override
    public abstract int size();
    @Override
    public abstract void clear();
    @Override
    public abstract boolean contains(long key);
    @Override
    public abstract boolean remove(long key);
    @Override
    public abstract LongIterator longIterator();

    /**
     * Returns <tt>true</tt> if this set contains all of the elements
     * in the specified collection.
     * @see LongSet#containsAll(Collection)
     */
    @Override
    public final boolean containsAll(Collection<?> c) {
        if (c instanceof LongCollection) {
            LongCollection lc = (LongCollection) c;
            PrimitiveIterator.OfLong e = lc.longIterator();
            while (e.hasNext())
                if (!contains(e.nextLong()))
                    return false;
            return true;
        } else
            return super.containsAll(c);
    }

    /**
     * Adds all of the elements in the specified collection to this set.
     * @see LongSet#addAll(Collection)
     */
    @Override
    public final boolean addAll(Collection<? extends Long> c) {
        if (c instanceof LongCollection) {
            LongCollection lc = (LongCollection) c;
            boolean modified = false;
            PrimitiveIterator.OfLong e = lc.longIterator();
            while (e.hasNext()) {
                if (add(e.nextLong()))
                    modified = true;
            }
            return modified;
        } else
            return super.addAll(c);
    }

    /**
     * Removes from this set all of its elements that are contained in
     * the specified collection.
     * @see LongSet#removeAll(Collection)
     */
    @Override
    public final boolean removeAll(Collection<?> c) {
        if (c instanceof LongCollection) {
            LongCollection lc = (LongCollection) c;
            boolean modified = false;
            if (size() > lc.size()) {
                for (PrimitiveIterator.OfLong i = lc.longIterator(); i.hasNext(); )
                    modified |= remove(i.nextLong());
            } else {
                for (PrimitiveIterator.OfLong i = longIterator(); i.hasNext(); ) {
                    if (lc.contains(i.nextLong())) {
                        i.remove();
                        modified = true;
                    }
                }
            }
            return modified;
        } else
            return super.removeAll(c);
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified collection.
     * @see LongSet#retainAll(Collection)
     */
    @Override
    public final boolean retainAll(Collection<?> c) {
        if (c instanceof LongCollection) {
            LongCollection lc = (LongCollection) c;
            boolean modified = false;
            PrimitiveIterator.OfLong e = longIterator();
            while (e.hasNext()) {
                if (!lc.contains(e.nextLong())) {
                    e.remove();
                    modified = true;
                }
            }
            return modified;
        } else
            return super.retainAll(c);
    }

    // Implements LongSet#add(long) -- not supported by AbstractLongSet
    @Override
    public boolean add(long key) {
        throw new UnsupportedOperationException();
    }

    // Implements Set#contains(Object)
    @Override
    public final boolean contains(Object o) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return (o instanceof Long) && contains((long) (Long) o);
    }

    // Implements Set#iterator()

    /**
     * Returns the same value as {@link #longIterator()} does.
     */
    @Override
    public final Iterator<Long> iterator() {
        return longIterator();
    }

    // Implements Set#add(Object)
    @Override
    public final boolean add(Long o) {
        return add((long) o);
    }

    // Implements Set#remove(Object)
    @Override
    public final boolean remove(Object o) {
        //inspection hidden due to IDEA bug
        //noinspection RedundantCast
        return o instanceof Long && remove((long) (Long) o);
    }

    // Implements LongSet#toLongArray()
    @Override
    public long[] toLongArray() {
        long[] result = new long[size()];
        PrimitiveIterator.OfLong e = longIterator();
        for (int i = 0; e.hasNext(); i++)
            result[i] = e.nextLong();
        return result;
    }
}

