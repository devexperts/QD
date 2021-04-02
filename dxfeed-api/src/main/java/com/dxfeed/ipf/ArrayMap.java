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
package com.dxfeed.ipf;

import java.lang.reflect.Array;

/**
 * Basic implementation of hash map in an array to be embedded into other object.
 * Intended use-case - when many small maps are needed and memory overhead is an issue.
 * <p>
 * Notes:
 * Not a thread-safe.
 * Fragile to outside modifications of an array (key misplacement).
 * Suboptimal performance especially in bad cases.
 */
final class ArrayMap {

    public static <T> int size(T[] a) {
        int result = 0;
        for (int i = a.length & ~1; (i -= 2) >= 0;)
            if (a[i] != null)
                result++;
        return result;
    }

    public static <T> boolean isEmpty(T[] a) {
        for (int i = a.length & ~1; (i -= 2) >= 0;)
            if (a[i] != null)
                return false;
        return true;
    }

    public static <T> boolean containsKey(T[] a, T key) {
        int index = initialIndex(key.hashCode(), a.length);
        for (int i = a.length & ~1; (i -= 2) >= 0;) {
            T k = a[index];
            if (k == null)
                return false;
            if (key.equals(k))
                return true;
            if ((index -= 2) < 0)
                index = (a.length & ~1) - 2;
        }
        return false;
    }

    public static <T> T get(T[] a, T key) {
        int index = initialIndex(key.hashCode(), a.length);
        for (int i = a.length & ~1; (i -= 2) >= 0;) {
            T k = a[index];
            if (k == null)
                return null;
            if (key.equals(k))
                return a[index + 1];
            if ((index -= 2) < 0)
                index = (a.length & ~1) - 2;
        }
        return null;
    }

    public static <T> T[] putIfKeyPresent(T[] a, T key, T value) {
        int index = initialIndex(key.hashCode(), a.length);
        for (int i = a.length & ~1; (i -= 2) >= 0;) {
            T k = a[index];
            if (k == null)
                return a;
            if (key.equals(k)) {
                a[index + 1] = value;
                return a;
            }
            if ((index -= 2) < 0)
                index = (a.length & ~1) - 2;
        }
        return a;
    }

    public static <T> T[] put(T[] a, T key, T value) {
        int result = putImpl(a, key, value);
        if (result < 0) {
            if (putImpl(a = rehash(a), key, value) < 0)
                throw new IllegalStateException("grow failure");
            return a;
        }
        if (result < a.length >> 1)
            return rehash(a);
        return a;
    }

    public static <T> T remove(T[] a, T key) {
        // Remove operation shall nullify key and reposition arbitrary number of entries.
        // Theoretically it can reposition all other entries several times while trying to finish.
        // Therefore it is generally simpler to rehash entire map anew upon removal.
        // Resume: do not implement it until really needed.
        throw new UnsupportedOperationException();
    }

    public static <T> void clear(T[] a) {
        for (int i = a.length & ~1; --i >= 0;)
            a[i] = null;
    }


    // ========== Internal Implementation ==========

    private static final int GOLDEN_RATIO = 0x9E3779B9;

    private static int initialIndex(int hash, int length) {
        return (int) ((((hash * GOLDEN_RATIO) & 0xFFFFFFFFL) * (length & ~1)) >>> 32) & ~1;
    }

    private static <T> int putImpl(T[] a, T key, T value) {
        int index = initialIndex(key.hashCode(), a.length);
        for (int i = a.length & ~1; (i -= 2) >= 0;) {
            T k = a[index];
            if (k == null) {
                a[index] = key;
                a[index + 1] = value;
                return i;
            }
            if (key.equals(k)) {
                a[index + 1] = value;
                return i;
            }
            if ((index -= 2) < 0)
                index = (a.length & ~1) - 2;
        }
        return -2;
    }

    private static <T> T[] rehash(T[] old) {
        //noinspection unchecked
        T[] a = (T[]) Array.newInstance(old.getClass().getComponentType(), Math.max((old.length & ~1) * 2, 4));
        for (int i = old.length & ~1; (i -= 2) >= 0;) {
            T k = old[i];
            if (k != null)
                if (putImpl(a, k, old[i + 1]) < 0)
                    throw new IllegalStateException("rehash failure");
        }
        return a;
    }

    private ArrayMap() {}
}
