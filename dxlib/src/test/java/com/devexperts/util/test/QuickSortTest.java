/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import com.devexperts.util.IntComparator;
import com.devexperts.util.LongComparator;
import com.devexperts.util.QuickSort;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuickSortTest {
    private static final int REPEAT = 20;
    private static final int SIZE = 500;

    @Test
    public void testInteger() {
        unstable = 0;
        for (int i = 0; i < 3; i++) {
            Comparator<Integer> comparator = (i == 0) ? null :
                (i == 1) ? Comparator.naturalOrder() : Comparator.comparing(Integer::intValue);
            checkWhole(comparator, this::toIntegerList, (a, c) -> QuickSort.sort(a));
            checkRange(comparator, this::toIntegerList, (a, f, t, c) -> QuickSort.sort(a, f, t));
            checkWhole(comparator, this::toIntegerList, QuickSort::sort);
            checkRange(comparator, this::toIntegerList, QuickSort::sort);
            checkWhole(comparator, this::toIntegerArray, (a, c) -> QuickSort.sort(a));
            checkRange(comparator, this::toIntegerArray, (a, f, t, c) -> QuickSort.sort(a, f, t));
            checkWhole(comparator, this::toIntegerArray, QuickSort::sort);
            checkRange(comparator, this::toIntegerArray, QuickSort::sort);
        }
    }

    @Test
    public void testValue() {
        for (unstable = 0; unstable < 5; unstable++) {
            checkWhole((Comparator<Value>) this::compareValue, this::toValueList, QuickSort::sort);
            checkRange((Comparator<Value>) this::compareValue, this::toValueList, QuickSort::sort);
            checkWhole((Comparator<Value>) this::compareValue, this::toValueArray, QuickSort::sort);
            checkRange((Comparator<Value>) this::compareValue, this::toValueArray, QuickSort::sort);
        }
    }

    @Test
    public void testInt() {
        for (unstable = 0; unstable < 5; unstable++) {
            IntComparator comparator = this::compareInt;
            checkWhole(comparator, this::toIntArray, QuickSort::sort);
            checkRange(comparator, this::toIntArray, QuickSort::sort);
            comparator = IntComparator.comparingInt(i -> -i).reversed();
            checkWhole(comparator, this::toIntArray, QuickSort::sort);
            checkRange(comparator, this::toIntArray, QuickSort::sort);
            comparator = IntComparator.comparing(Value::new, this::compareValue);
            checkWhole(comparator, this::toIntArray, QuickSort::sort);
            checkRange(comparator, this::toIntArray, QuickSort::sort);
        }
    }

    @Test
    public void testLong() {
        for (unstable = 0; unstable < 5; unstable++) {
            LongComparator comparator = this::compareLong;
            checkWhole(comparator, this::toLongArray, QuickSort::sort);
            checkRange(comparator, this::toLongArray, QuickSort::sort);
            comparator = LongComparator.comparingLong(i -> -i).reversed();
            checkWhole(comparator, this::toLongArray, QuickSort::sort);
            checkRange(comparator, this::toLongArray, QuickSort::sort);
            comparator = LongComparator.comparing(i -> new Value((int) i), this::compareValue);
            checkWhole(comparator, this::toLongArray, QuickSort::sort);
            checkRange(comparator, this::toLongArray, QuickSort::sort);
        }
    }

    // ========== Converting ==========

    private List<Integer> toIntegerList(int[] x) {
        return new ArrayList<>(Arrays.asList(toIntegerArray(x)));
    }

    private List<Value> toValueList(int[] x) {
        return new ArrayList<>(Arrays.asList(toValueArray(x)));
    }

    private Integer[] toIntegerArray(int[] x) {
        Integer[] r = new Integer[x.length];
        for (int i = 0; i < x.length; i++)
            r[i] = x[i];
        return r;
    }

    private Value[] toValueArray(int[] x) {
        Value[] r = new Value[x.length];
        for (int i = 0; i < x.length; i++)
            r[i] = new Value(x[i]);
        return r;
    }

    private int[] toIntArray(int[] x) {
        return x.clone();
    }

    private long[] toLongArray(int[] x) {
        long[] r = new long[x.length];
        for (int i = 0; i < x.length; i++)
            r[i] = x[i];
        return r;
    }

    // ========== Generating and Comparing ==========

    private final Random random = new Random(20160915);
    private int unstable;

    private int[] generate() {
        int[] x = new int[SIZE];
        for (int i = 0; i < SIZE; i++)
            x[i] = random.nextInt(SIZE * 9 / 10); // ~10% duplicates/triplicates/etc
        return x;
    }

    private int compareValue(Value v1, Value v2) {
        return compareLong(v1.value, v2.value);
    }

    private int compareInt(int v1, int v2) {
        return compareLong(v1, v2);
    }

    private int compareLong(long v1, long v2) {
        switch (unstable) {
            case 0: return Long.compare(v1, v2);
            case 1: return -1;
            case 2: return 0;
            case 3: return 1;
            case 4: return random.nextInt(3) - 1;
            default: throw new IllegalArgumentException();
        }
    }

    // ========== Sorting and Checking ==========

    private static interface WholeSorter<A, C> {
        public void sort(A a, C c);
    }

    private static interface RangeSorter<A, C> {
        public void sort(A a, int fromIndex, int toIndex, C c);
    }

    private <A, C> void checkWhole(C c, Function<int[], A> f, WholeSorter<A, C> s) {
        for (int rep = 0; rep < REPEAT; rep++) {
            int[] x = generate();
            A actual = f.apply(x);
            s.sort(actual, c);
            if (unstable != 0)
                sort(actual, 0, x.length);
            Arrays.sort(x);
            A expected = f.apply(x);
            checkEquals(expected, actual);
        }
    }

    private <A, C> void checkRange(C c, Function<int[], A> f, RangeSorter<A, C> s) {
        for (int rep = 0; rep < REPEAT; rep++) {
            int fromIndex = random.nextInt(SIZE);
            int toIndex = random.nextInt(SIZE - fromIndex) + fromIndex;
            int[] x = generate();
            A actual = f.apply(x);
            s.sort(actual, fromIndex, toIndex, c);
            if (unstable != 0)
                sort(actual, fromIndex, toIndex);
            Arrays.sort(x, fromIndex, toIndex);
            A expected = f.apply(x);
            checkEquals(expected, actual);
        }
    }

    private <A> void sort(A a, int fromIndex, int toIndex) {
        if (a instanceof List) {
            ((List) a).subList(fromIndex, toIndex).sort(Comparator.comparing(Number::intValue));
        } else if (a instanceof Number[]) {
            Arrays.sort((Number[]) a, fromIndex, toIndex, Comparator.comparing(Number::intValue));
        } else if (a instanceof int[]) {
            Arrays.sort((int[]) a, fromIndex, toIndex);
        } else if(a instanceof long[]) {
            Arrays.sort((long[])a,fromIndex,toIndex);
        } else {
            fail("unknown container type");
        }
    }

    private <A> void checkEquals(A expected, A actual) {
        if (expected instanceof List) {
            assertEquals(expected, actual);
        } else if (expected instanceof Number[]) {
            assertArrayEquals((Number[]) expected, (Number[]) actual);
        } else if (expected instanceof int[]) {
            assertArrayEquals((int[]) expected, (int[]) actual);
        } else if (expected instanceof long[]) {
            assertArrayEquals((long[]) expected, (long[]) actual);
        } else {
            fail("unknown container type");
        }
    }

    private static class Value extends Number { // unlike Integer this class is NOT Comparable
        final int value;

        Value(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }

        @Override
        public long longValue() {
            return value;
        }

        @Override
        public float floatValue() {
            return value;
        }

        @Override
        public double doubleValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Value && ((Value) obj).value == value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
