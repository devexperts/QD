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
package com.devexperts.qd.impl.matrix.management;

import java.util.concurrent.atomic.AtomicLongArray;

public class RecordCounters {
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private int[] count = EMPTY_INT_ARRAY;
    private int[] next = EMPTY_INT_ARRAY;
    private int last = -1;

    public void prepare(int n) {
        count = resizeIfNeeded(count, n);
        next = resizeIfNeeded(next, n);
    }

    private int[] resizeIfNeeded(int[] a, int n) {
        if (a.length >= n)
            return a;
        int[] b = new int[n];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    public void count(int rid) {
        if (count[rid] == 0) {
            next[rid] = last;
            last = rid;
        }
        count[rid]++;
    }

    public boolean flushAndClear(AtomicLongArray arr) {
        int rid = last;
        if (rid < 0)
            return false;
        do {
            arr.addAndGet(rid, count[rid]);
            count[rid] = 0;
            rid = next[rid];
        } while (rid >= 0);
        last = -1;
        return true;
    }
}
