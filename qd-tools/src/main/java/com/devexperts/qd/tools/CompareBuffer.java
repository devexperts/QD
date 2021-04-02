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
package com.devexperts.qd.tools;

import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.util.ArrayUtil;

import java.util.Arrays;

final class CompareBuffer {
    private static final int[] EMPTY_INTS = new int[0];
    private static final Object[] EMPTY_OBJS = new Object[0];

    private static final int I_TIME_HI = 0;
    private static final int I_TIME_LO = 1;
    private static final int I_FLDS = 2;

    private final RecordFields rf;
    private final int ineed;
    private final int oneed;

    private int[] ints = EMPTY_INTS;
    private Object[] objs = EMPTY_OBJS;
    private int size;
    private int isize; // == size * ineed;
    private int osize; // == size * oneed;

    public static final CompareBuffer EMPTY = new CompareBuffer(null); // you cannot add there

    public CompareBuffer(RecordFields rf) {
        this.rf = rf;
        this.ineed = rf == null ? 0 : I_FLDS + rf.getIntFieldCount();
        this.oneed = rf == null ? 0 : rf.getObjFieldCount();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public synchronized void add(long timestamp, RecordCursor cursor) {
        if (cursor.getRecord() != rf.getRecord())
            throw new IllegalArgumentException();
        if (ints.length < isize + ineed)
            ints = ArrayUtil.grow(ints, isize + ineed);
        if (objs.length < osize + oneed)
            objs = ArrayUtil.grow(objs, osize + oneed);
        ints[isize++] = (int) (timestamp >>> 32);
        ints[isize++] = (int) timestamp;
        for (int k = 0; k < rf.getIntFieldCount(); k++)
            ints[isize++] = cursor.getInt(rf.getIntIndex(k));
        for (int k = 0; k < rf.getObjFieldCount(); k++)
            objs[osize++] = cursor.getObj(rf.getObjIndex(k));
        size++;
    }

    public void clear() {
        Arrays.fill(objs, 0, osize, null);
        size = 0;
        isize = 0;
        osize = 0;
    }

    public void clearFirst(int n) {
        System.arraycopy(ints, n * ineed, ints, 0, (size - n) * ineed);
        System.arraycopy(objs, n * oneed, objs, 0, (size - n) * oneed);
        Arrays.fill(objs, (size - n) * oneed, osize, null);
        size -= n;
        isize = size * ineed;
        osize = size * oneed;
    }

    public boolean matches(CompareBuffer other, int i, int j) {
        for (int k = 0; k < rf.getIntFieldCount(); k++)
            if (ints[i * ineed + I_FLDS + k] != other.ints[j * ineed + I_FLDS + k])
                return false;
        for (int k = 0; k < rf.getObjFieldCount(); k++)
            if (!compareObjs(objs[i * oneed + k], other.objs[j * oneed + k]))
                return false;
        return true;
    }

    private static boolean compareObjs(Object o1, Object o2) {
        if (o1 == null)
            return o2 == null;
        if (o2 == null)
            return false;
        if (o1 instanceof byte[])
            return (o2 instanceof byte[]) && Arrays.equals((byte[]) o1, (byte[]) o2);
        return o1.equals(o2);
    }

    public long getTimestamp(int i) {
        int base = i * ineed;
        //noinspection PointlessArithmeticExpression
        return ((long) ints[base + I_TIME_HI] << 32) | ints[base + I_TIME_LO];
    }
}
