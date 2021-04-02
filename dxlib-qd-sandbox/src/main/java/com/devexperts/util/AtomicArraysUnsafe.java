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

import sun.misc.Unsafe;

/**
 * Provides operations on atomic arrays via {@link sun.misc.Unsafe}
 */
class AtomicArraysUnsafe extends AtomicArrays {
    private static final Unsafe unsafe = UnsafeHolder.UNSAFE;

    private static final int intBase;
    private static final int intScale;
    private static final int longBase;
    private static final int longScale;
    private static final int objBase;
    private static final int objScale;

    static {
        intBase = unsafe.arrayBaseOffset(int[].class);
        intScale = unsafe.arrayIndexScale(int[].class);
        longBase = unsafe.arrayBaseOffset(long[].class);
        longScale = unsafe.arrayIndexScale(long[].class);
        objBase = unsafe.arrayBaseOffset(Object[].class);
        objScale = unsafe.arrayIndexScale(Object[].class);
    }

    private static void throwIOOBE(int i, int length) {
            throw new IndexOutOfBoundsException("index=" + i + ", length=" + length);
    }

    private static long rawIntIndex(int[] a, int i) {
        if (i < 0 || i >= a.length)
            throwIOOBE(i, a.length);
        return intBase + (long) i * intScale;
    }

    private static long rawLongIndex(long[] a, int i) {
        if (i < 0 || i >= a.length)
            throwIOOBE(i, a.length);
        return longBase + (long) i * longScale;
    }

    private static long rawObjIndex(Object[] a, int i) {
        if (i < 0 || i >= a.length)
            throwIOOBE(i, a.length);
        return objBase + (long) i * objScale;
    }

    @Override
    public void setVolatileInt(int[] a, int i, int val) {
        unsafe.putIntVolatile(a, rawIntIndex(a, i), val);
    }

    @Override
    public int getVolatileInt(int[] a, int i) {
        return unsafe.getIntVolatile(a, rawIntIndex(a, i));
    }

    @Override
    public int addAndGetInt(int[] a, int i, int delta) {
        long index = rawIntIndex(a, i);
        int expect;
        int update;
        do {
            expect = unsafe.getInt(a, index);
            update = expect + delta;
        } while (!unsafe.compareAndSwapInt(a, index, expect, update));
        return update;
    }

    @Override
    public boolean compareAndSetInt(int[] a, int i, int expect, int update) {
        return unsafe.compareAndSwapInt(a, rawIntIndex(a, i), expect, update);
    }

    @Override
    public void setVolatileLong(long[] a, int i, long val) {
        unsafe.putLongVolatile(a, rawLongIndex(a, i), val);
    }

    @Override
    public long getVolatileLong(long[] a, int i) {
        return unsafe.getLongVolatile(a, rawLongIndex(a, i));
    }

    @Override
    public long addAndGetLong(long[] a, int i, long delta) {
        long index = rawLongIndex(a, i);
        long expect;
        long update;
        do {
            expect = unsafe.getLong(a, index);
            update = expect + delta;
        } while (!unsafe.compareAndSwapLong(a, index, expect, update));
        return update;
    }

    @Override
    public <T> void setVolatileObj(T[] a, int i, T val) {
        unsafe.putObjectVolatile(a, rawObjIndex(a, i), val);
    }

    @SuppressWarnings( {"unchecked"})
    @Override
    public <T> T getVolatileObj(T[] a, int i) {
        return (T) unsafe.getObjectVolatile(a, rawObjIndex(a, i));
    }
}
