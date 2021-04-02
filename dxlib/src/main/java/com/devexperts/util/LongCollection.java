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

import java.util.Collection;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * This class extends {@link Collection} with methods that are specific
 * for <code>long</code> values.
 */
public interface LongCollection extends Collection<Long> {
    /**
     * Returns <tt>true</tt> if this collection contains the specified element.
     * @see #contains(Object)
     */
    public boolean contains(long key);

    /**
     * Ensures that this collection contains the specified element.
     * @see #add(Object)
     */
    public boolean add(long key);

    /**
     * Removes a single instance of the specified element from this
     * collection, if it is present.
     * @see #remove(Object)
     */
    public boolean remove(long key);

    /**
     * Returns an array containing all of the elements in this collection.
     * @see #toArray()
     */
    public long[] toLongArray();

    /**
     * Returns an iterator over the elements in this collection.
     * @see #iterator()
     */
    public LongIterator longIterator();

    @Override
    default Spliterator.OfLong spliterator() {
        return spliterator(0);
    }

    default Spliterator.OfLong spliterator(int characteristics) {
        return Spliterators.spliteratorUnknownSize(longIterator(), characteristics);
    }

    default LongStream longStream() {
        return StreamSupport.longStream(spliterator(), false);
    }

    default LongStream parallelLongStream() {
        return StreamSupport.longStream(spliterator(), true);
    }
}
