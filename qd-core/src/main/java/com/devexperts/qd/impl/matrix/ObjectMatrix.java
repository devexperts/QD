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
package com.devexperts.qd.impl.matrix;

/**
 * The <code>ObjectMatrix</code> maps object keys into object values.
 * It supports only non-null keys and only addition or retrieval of stored values.
 * It requires externally tracked and activated rehash as usual.
 * <p>
 * The <code>ObjectMatrix</code> requires synchronized write access, but it allows
 * unsynchronized read-only access at any time.
 */
final class ObjectMatrix {

    private final int magic;
    private final int shift;
    private final Object[] matrix;

    private int overall_size;

    ObjectMatrix(int capacity, int prev_magic) {
        magic = Hashing.nextMagic(prev_magic, capacity);
        shift = Hashing.getShift(capacity);
        if (2 >= Integer.MAX_VALUE >> (32 - shift))
            throw new IllegalArgumentException("Capacity is too large.");
        matrix = new Object[2 << (32 - shift)];
    }

    // ========== Internal ==========

    private final int getIndex(Object key, int miss_mask) {
        int index = ((key.hashCode() * magic) >>> shift) << 1;
        Object test_key;
        while (!key.equals(test_key = matrix[index])) {
            if (test_key == null) {
                if (index > 0)
                    return index & miss_mask;
                index = matrix.length;
            }
            index -= 2;
        }
        return index;
    }

    // ========== Public ==========

    /**
     * Returns stored value for specified key or null if not found.
     */
    final Object get(Object key) {
        return matrix[getIndex(key, 0) + 1];
    }

    /**
     * Puts specified value for specified key replacing previous value.
     */
    final void put(Object key, Object value) {
        int index = getIndex(key, -1);
        if (matrix[index] == null) {
            matrix[index] = key;
            overall_size++;
        }
        matrix[index + 1] = value;
    }

    // ========== Maintenance ==========

    final boolean needRehash() {
        return Hashing.needRehash(shift, overall_size, overall_size, Hashing.MAX_SHIFT);
    }

    final ObjectMatrix rehash() {
        ObjectMatrix dest = new ObjectMatrix(overall_size, magic);
        for (int index = matrix.length; (index -= 2) > 0;)
            if (matrix[index] != null)
                dest.put(matrix[index], matrix[index + 1]);
        if (dest.overall_size != overall_size)
            throw new IllegalStateException("Payload integrity corrupted.");
        return dest;
    }
}
