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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A collection designed for pooling elements so that they can be reused later.
 * This class is primarily designed for pooling of very, very large objects that occupy a lot of memory and
 * are extremely expensive to create, to make sure that as few as possible of those objects are allocated
 * and are kept in the pool.
 *
 * <p>The <tt>LockFreePool</tt> is a fixed-capacity (or &quot;bounded&quot;)
 * stack as it orders elements in a LIFO (last-in-first-out) manner.
 * This class is a thread-safe and provides lock-free access.
 *
 * <p>If you need a pool of moderately-sized objects that may be needed so often, as to raise concern about
 * contention on this {@code LockFreePool} implementation and memory locality concerns on object reuse,
 * then use {@link ThreadLocalPool} class and keeps an additional per-thread pool of objects.
 *
 * @param <E> type of elements kept in this pool.
 */
public class LockFreePool<E> {
    /*
        64-bits in state are allocated this way:
        +-----------+--------+---------+
        |  VERSION  |  SIZE  |  INDEX  |
        +-----------+--------+---------+
        Where size occupies SIZE_BITS and index occupies INDEX_BITS.
     */

    private static final int SIZE_BITS = 24;
    private static final int INDEX_BITS = SIZE_BITS + 1; // we allocate x2 storage to have 50% fill-factor

    static {
        assert 64 - SIZE_BITS - INDEX_BITS >= 12; // make sure we have enough bits for version left
    }

    private static final long SIZE_INC = 1L << INDEX_BITS;
    private static final long VERSION_INC = 1L << (SIZE_BITS + INDEX_BITS);
    private static final long INDEX_MASK = SIZE_INC - 1;
    private static final long SIZE_MASK = VERSION_INC - SIZE_INC;

    /**
     * Maximal supported pool capacity.
     */
    public static final int MAX_CAPACITY = (1 << SIZE_BITS) - 1;

    private final int capacity;
    private final int lengthMask;
    private final AtomicReferenceArray<E> objects;
    private final int[] next;
    private final AtomicLong state = new AtomicLong();

    /**
     * Creates a <tt>LockFreePool</tt> with the specified (fixed) capacity. Using a fixed capacity in production
     * code is a bad practise. Use a convenience constructor
     * {@link #LockFreePool(String, int) LockFreePool(poolName, defaultCapacity)} that enables an override of
     * pool's default capacity via JVM system property if needed.
     *
     * @param poolCapacity the capacity of this pool.
     * @throws IllegalArgumentException if pool capacity is less than 1 or greater than {@link #MAX_CAPACITY}.
     */
    public LockFreePool(int poolCapacity) {
        if (poolCapacity < 1 || poolCapacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Pool capacity is out of range");
        this.capacity = poolCapacity;
        // internal array length is a power of 2 between 2*capacity and 4*capacity
        // small capacities use extra space to avoid cache line collisions
        lengthMask = (Integer.highestOneBit(Math.max(poolCapacity, 16)) << 2) - 1;
        objects = new AtomicReferenceArray<E>(lengthMask + 1);
        next = new int[lengthMask + 1];
    }

    /**
     * A convenience constructor that creates a <tt>LockFreePool</tt> with a the specified default capacity,
     * which can be overriden by a JVM system property.
     * The corresponding JVM property name is {@code poolName + ".poolCapacity"}.
     *
     * @param poolName the base name of the pool.
     * @param defaultPoolCapacity the default capacity of this pool.
     * @throws IllegalArgumentException if pool capacity is less than 1 or greater than {@link #MAX_CAPACITY}.
     */
    public LockFreePool(String poolName, int defaultPoolCapacity) {
        this(SystemProperties.getIntProperty(poolName + ".poolCapacity", defaultPoolCapacity));
    }

    /**
     * Returns the number of elements in this pool.
     *
     * @return the number of elements in this pool.
     */
    public int size() {
        return (int) ((state.get() & SIZE_MASK) >>> INDEX_BITS);
    }

    /**
     * Retrieves and removes the element from this pool, or <tt>null</tt> if this pool is empty.
     *
     * @return the element from this pool, or <tt>null</tt> if this pool is empty.
     */
    public E poll() {
        while (true) {
            long st = state.get();
            if ((st & SIZE_MASK) == 0)
                return null; // pool is empty
            int index = (int) (st & INDEX_MASK);
            // increment version, decrement size, and replace index to the top of stack
            if (state.compareAndSet(st, (st + (VERSION_INC - SIZE_INC)) & ~INDEX_MASK | next[index])) {
                E result = objects.get(index);
                objects.set(index, null); // mark lot as free
                return result;
            }
        }
    }

    /**
     * Inserts the specified element into this pool, if possible.
     *
     * @param o the element to insert.
     * @return <tt>true</tt> if element was inserted into this pool, else <tt>false</tt>.
     * @throws NullPointerException if element is <tt>null</tt>.
     * @throws IllegalStateException if element is already in the pool (best effort check only).
     */
    public boolean offer(E o) {
        if (o == null)
            throw new NullPointerException("Element is null");
        // phase 1: find free lot and occupy it atomically
        int index = hash(o) & lengthMask; // random guess of free lot
        int attempts = 0;
        while (true) {
            E old = objects.get(index);
            if (old == null && objects.compareAndSet(index, null, o))
                break;
            if (old == o)
                throw new IllegalStateException("Element is already in the pool");
            index = (index + 1) & lengthMask;
            // avoid running for too long, but do not check shared state too often
            if (++attempts >= lengthMask || (attempts & 3) == 0 && size() >= capacity)
                return false; // too many attempts or pool is full
        }
        // phase 2: update pool state atomically or rollback if pool is full
        while (true) {
            long st = state.get();
            if ((st & SIZE_MASK) >>> INDEX_BITS >= capacity) { // pool is full
                objects.set(index, null); // mark recently occupied lot as free
                return false;
            }
            next[index] = (int) (st & INDEX_MASK);
            // increment version, increment size, and replace index to the top of stack
            if (state.compareAndSet(st, (st + (VERSION_INC + SIZE_INC)) & ~INDEX_MASK | index))
                return true;
        }
    }

    private static int hash(Object o) {
        // Hash function copied from HashMap.
        int h = System.identityHashCode(o);
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }
}
