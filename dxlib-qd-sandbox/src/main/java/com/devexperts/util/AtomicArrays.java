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

import com.devexperts.logging.Logging;

/**
 * This class provides access to volatile read and write operations for array elements.
 * This class contains mock implementation that works via synchronized section and
 * {@link AtomicArraysUnsafe} provides implementation via {@link sun.misc.Unsafe}.
 */
public class AtomicArrays {
    /**
     * The instance of this utility class.
     */
    public static final AtomicArrays INSTANCE = createInstance();

    private static AtomicArrays createInstance() {
        try {
            return new AtomicArraysUnsafe();
        } catch (Throwable t) {
            try {
                Logging.getLogging(AtomicArrays.class).warn("Cannot access sun.misc.Unsafe, fall back to SLOW MODE: " + t);
            } catch (Throwable ignore) {
            }
        }
        return new AtomicArrays();
    }

    public void setVolatileInt(int[] a, int i, int val) {
        synchronized (a) {
            a[i] = val;
        }
    }

    public int getVolatileInt(int[] a, int i) {
        synchronized (a) {
            return a[i];
        }
    }

    public int addAndGetInt(int[] a, int i, int delta) {
        synchronized (a) {
            return a[i] += delta;
        }
    }

    public boolean compareAndSetInt(int[] a, int i, int expect, int update) {
        synchronized (a) {
            if (a[i] == expect) {
                a[i] = update;
                return true;
            } else
                return false;
        }
    }

    public void setVolatileLong(long[] a, int i, long val) {
        synchronized (a) {
            a[i] = val;
        }
    }

    public long getVolatileLong(long[] a, int i) {
        synchronized (a) {
            return a[i];
        }
    }

    public long addAndGetLong(long[] a, int i, long delta) {
        synchronized (a) {
            return a[i] += delta;
        }
    }

    public <T> void setVolatileObj(T[] a, int i, T val) {
        synchronized (a) {
            a[i] = val;
        }
    }

    public <T> T getVolatileObj(T[] a, int i) {
        synchronized (a) {
            return a[i];
        }
    }
}
