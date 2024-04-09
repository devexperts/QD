/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.util.Arrays;

/**
 * Helper methods to work with O(1) array-based data structures.
 *
 * <p>To implement O(1) array of indexed elements use the following pattern of code:
 * <pre><tt>
 * private static final int MIN_ELEMENT_INDEX = 0; // use 1 to leave elements[0] null
 * private Element[] elements = new Element[ArrayUtil.MIN_CAPACITY];
 * private int lastElementIndex = MIN_ELEMENT_INDEX;
 *
 * void addElement(Element element) {
 *     lastElementIndex = ArrayUtil.findFreeIndex(elements, lastElementIndex, MIN_ELEMENT_INDEX);
 *     if (lastElementIndex &gt;= elements.length)
 *         elements = grow(elements, 0);
 *     elements[lastElementIndex] = element;
 * }
 * </tt></pre>
 */
public class ArrayUtil {
    /**
     * This is a recommended minimal capacity for arrays.
     */
    public static final int MIN_CAPACITY = 8;

    /**
     * Max allowed array size. For safety, it is equal to {@link Integer#MAX_VALUE}
     * minus one million.
     */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE - 1000000;

    private ArrayUtil() {}

    /**
     * Finds free (non-occupied) index in an array in such a way, that amortized time to
     * allocate an index is O(1). This implementation looks for an index {@code i}
     * such that {@code a[i] == null} starting from the given {@code lastFreeIndex} and
     * then cycles to the beginning of array where it starts from {@code minIndex}.
     * While iterating it counts the number of free indices in array. If less than a quarter
     * of indices a free then {@code a.length} is returned to indicate that array shall
     * be reallocated to a larger size.
     *
     * @param a              the array.
     * @param lastFoundIndex last result of this method.
     *                       On the first invocation is must be equal to {@code minIndex}.
     * @param minIndex       Minimal allowed index.
     * @return an index {@code i}, such that {@code i >= minIndex && a[i] == null || i == a.length}.
     *         The result of {@code a.length} indicates that array shall be reallocated with
     *         {@link #grow(Object[], int)} method.
     */
    public static int findFreeIndex(Object[] a, int lastFoundIndex, int minIndex) {
        for (int i = lastFoundIndex; i < a.length; i++)
            if (a[i] == null)
                return i;
        int freeCount = 0;
        int firstFreeIndex = 0;
        for (int i = lastFoundIndex; --i >= minIndex;)
            if (a[i] == null) {
                freeCount++;
                firstFreeIndex = i;
            }
        return freeCount <= a.length >> 2 ? a.length : firstFreeIndex;
    }

    private static int growSize(int n, int minCapacity) {
        if (n >= MAX_CAPACITY || minCapacity > MAX_CAPACITY)
            throw new IllegalArgumentException("Array size is too large");
        return n <= (MAX_CAPACITY >> 1) ?
            // if size less than 1/2 of possible max, then do normal x2 growth
            Math.max(Math.max(minCapacity, MIN_CAPACITY), n << 1) :
            // otherwise grow to maximal possible size
            MAX_CAPACITY;
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @param <T>          Type of the array components.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static <T> T[] grow(T[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static boolean[] grow(boolean[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static byte[] grow(byte[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static char[] grow(char[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static int[] grow(int[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }

    /**
     * Allocates new array with at least double the size of an existing one,
     * preserving all data from an existing array.
     * @param a            the array.
     * @param minCapacity  minimal size of the resulting array. Pass 0 if not needed.
     * @return             New array that is at least twice as large as {@code a},
     *                     has at least {@link #MIN_CAPACITY} elements, and at least
     *                     {@code minCapacity} elements, with all old elements in their old places.
     */
    public static long[] grow(long[] a, int minCapacity) {
        return Arrays.copyOf(a, growSize(a.length, minCapacity));
    }
}
