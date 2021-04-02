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

/**
 * Encapsulates a small thread-local pool of objects and a global {@link LockFreePool}.
 * This class is primarily designed for pooling of moderately-sized objects that may be needed so often,
 * as to raise concern about contention in {@link LockFreePool} implementation and memory locality concerns
 * on object reuse.
 *
 * <p>This class is a thread-safe.
 *
 * @param <E> type of elements kept in this pool.
 */
public class ThreadLocalPool<E> {

    /**
     * Maximal supported thread local capacity.
     */
    public static final int MAX_THREAD_LOCAL_CAPACITY = 1024; // to preserve sanity and allow future mods

    private final int threadLocalCapacity;
    private final ThreadLocal<Entry<E>> local = new ThreadLocal<Entry<E>>();
    private final LockFreePool<E> global;

    /**
     * Creates a <tt>ThreadLocalPool</tt> with the specified (fixed) capacities. Using a fixed capacity in production
     * code is a bad practise. Use a convenience constructor
     * {@link #ThreadLocalPool(String, int, int) ThreadLocalPool(poolName, defaultThreadLocalCapacity, defaultPoolCapacity)}
     * that enables an override of pool's default capacities via JVM system properties if needed.
     *
     * @param threadLocalCapacity the capacity of thread-local pool, e.g. how many objects are pooled per thread.
     * @param poolCapacity the capacity of the global {@link LockFreePool}, e.g. how many objects are pooled globally in addition to local ones.
     * @throws IllegalArgumentException if thread local capacity is less than 1 or greater than {@link #MAX_THREAD_LOCAL_CAPACITY} or
     *                                  pool capacity is less than 1 or greater than {@link LockFreePool#MAX_CAPACITY}.
     */
    public ThreadLocalPool(int threadLocalCapacity, int poolCapacity) {
        if (threadLocalCapacity < 1 || threadLocalCapacity > MAX_THREAD_LOCAL_CAPACITY)
            throw new IllegalArgumentException("Thread local capacity is out of range");
        this.threadLocalCapacity = threadLocalCapacity;
        this.global = new LockFreePool<E>(poolCapacity);
    }

    /**
     * A convenience constructor that creates a <tt>ThreadLocalPool</tt> with a the specified default capacities,
     * which can be overriden by a JVM system properties.
     * The corresponding JVM property name for thread local capacity is {@code poolName + ".threadLocalCapacity"},
     * and for the global {@link LockFreePool} is {@code poolName + ".poolCapacity"}.
     *
     * @param poolName the base name of the pool.
     * @param defaultThreadLocalCapacity the default capacity of thread-local pool, e.g. how many objects are pooled per thread.
     * @param defaultPoolCapacity the default capacity of the global {@link LockFreePool}, e.g. how many objects are pooled globally in addition to local ones.
     * @throws IllegalArgumentException if thread local capacity is less than 1 or greater than {@link #MAX_THREAD_LOCAL_CAPACITY} or
     *                                  pool capacity is less than 1 or greater than {@link LockFreePool#MAX_CAPACITY}.
     */
    public ThreadLocalPool(String poolName, int defaultThreadLocalCapacity, int defaultPoolCapacity) {
        this(SystemProperties.getIntProperty(poolName + ".threadLocalCapacity", defaultThreadLocalCapacity),
            SystemProperties.getIntProperty(poolName + ".poolCapacity", defaultPoolCapacity));
    }

    /**
     * Returns the number of elements in this pool that are available to the current thread.
     *
     * @return the number of elements in this pool that are available to the current thread.
     */
    public int size() {
        Entry<E> entry = local.get();
        return entry == null ? global.size() : global.size() + entry.top;
    }

    /**
     * Retrieves and removes the element from this pool, or <tt>null</tt> if this pool is empty.
     *
     * @return the element from this pool, or <tt>null</tt> if this pool is empty.
     */
    public E poll() {
        Entry<E> entry = local.get();
        if (entry != null && entry.top > 0)
            return entry.pop();
        return global.poll();
    }

    /**
     * Inserts the specified element into this pool. This method always stores the element into the thread local pool,
     * to make sure that thread local pool contains the most "fresh" instance. If thread local pool has no capacity,
     * then its oldest element is moved into the global {@link LockFreePool}.
     *
     * @param o the element to insert.
     * @return Always returns {@code true}.
     * @throws NullPointerException if element is <tt>null</tt>.
     * @throws IllegalStateException if element is already in the pool (best effort check only).
     */
    public boolean offer(E o) {
        if (o == null)
            throw new NullPointerException();
        Entry<E> entry = local.get();
        if (entry == null)
            local.set(entry = new Entry<E>(threadLocalCapacity));
        o = entry.push(o);
        if (o != null)
            global.offer(o);
        return true;
    }

    private static class Entry<E> {
        final E[] stack;
        int top;

        @SuppressWarnings("unchecked")
        Entry(int threadLocalCapacity) {
            stack = (E[]) new Object[threadLocalCapacity];
        }

        E pop() {
            return stack[--top];
        }

        E push(E o) {
            if (top < stack.length) {
                stack[top++] = o;
                return null;
            }
            E result = stack[0];
            System.arraycopy(stack, 1, stack, 0, stack.length - 1);
            stack[stack.length - 1] = o;
            return result;
        }
    }
}

