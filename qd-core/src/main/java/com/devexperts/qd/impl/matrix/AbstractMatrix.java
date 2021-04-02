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

import com.devexperts.qd.SymbolCodec;
import com.devexperts.util.AtomicArrays;

abstract class AbstractMatrix {
    protected final Mapper mapper;
    protected volatile Mapping mapping; // not null if this matrix was rehashed and no longer "active"

    protected final int magic;
    protected final int shift;
    protected final int step;
    protected final int[] matrix;
    protected final int obj_step; // Used to convert: obj_index = (index / step) * obj_step;
    protected final Object[] obj_matrix;

    protected int overallSize;
    protected int payloadSize;

    // Specify 'mapper = null' to avoid automatic 'incCounter/decCounter'.
    AbstractMatrix(Mapper mapper, int step, int obj_step, int capacity, int prev_magic, int max_shift) {
        this.mapper = mapper;
        this.magic = Hashing.nextMagic(prev_magic, capacity);
        this.shift = Math.min(max_shift, Hashing.getShift(capacity));
        this.step = step;
        if (step >= Integer.MAX_VALUE >> (32 - shift))
            throw new IllegalArgumentException("Step and capacity are too large.");
        this.matrix = new int[step << (32 - shift)];
        this.obj_step = obj_step;
        if (obj_step >= Integer.MAX_VALUE >> (32 - shift))
            throw new IllegalArgumentException("Obj_step and capacity are too large.");
        this.obj_matrix = obj_step == 0 ? null : new Object[obj_step << (32 - shift)];
    }

    final Mapping getMapping() {
        // Note: order of getting "mapping" is IMPORTANT!
        Mapping mapping = mapper.getMapping();
        // we have to ask "are we rehashed?" as the last check, and take our
        // "mapping snapshot" in this case
        if (this.mapping != null)
            mapping = this.mapping;
        return mapping;
    }

    final boolean needRehash(int max_shift) {
        return Hashing.needRehash(shift, overallSize, payloadSize, max_shift);
    }

    final void startRehash() {
        if (mapping != null)
            throw new IllegalStateException("Repeated rehash.");
        if (mapper != null)
            mapping = mapper.getMapping();
    }

    final int getInt(int index) {
        return matrix[index];
    }

    final void setInt(int index, int value) {
        matrix[index] = value;
    }

    final int getVolatileInt(int index) {
        return AtomicArrays.INSTANCE.getVolatileInt(matrix, index);
    }

    final void setVolatileInt(int index, int value) {
        AtomicArrays.INSTANCE.setVolatileInt(matrix, index, value);
    }

    final boolean compareAndSetInt(int index, int expect, int update) {
        return AtomicArrays.INSTANCE.compareAndSetInt(matrix, index, expect, update);
    }

    final long getLong(int index) {
        return ((long) matrix[index] << 32) | ((long) matrix[index + 1] & 0xFFFFFFFFL);
    }

    final void setLong(int index, long value) {
        matrix[index] = (int) (value >>> 32);
        matrix[index + 1] = (int) value;
    }

    final Object getObj(int index, int offset) {
        return obj_matrix[(index / step) * obj_step + offset];
    }

    final void setObj(int index, int offset, Object value) {
        obj_matrix[(index / step) * obj_step + offset] = value;
    }

    final int getIndex(int key, int miss_mask) {
        int index = ((key * magic) >>> shift) * step;
        int test_key;
        while ((test_key = matrix[index]) != key) {
            if (test_key == 0) {
                if (index > 0)
                    return index & miss_mask;
                index = matrix.length;
            }
            index -= step;
        }
        return index;
    }

    /**
     * For unsynchronized read-only access. Returns 0 if not found.
     */
    final int getIndex(int cipher, String symbol) {
        int key = cipher;
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher.");
            key = getMapping().getKey(symbol);
        }
        return getIndex(key, 0);
    }

    /**
     * For unsynchronized read-only access. Returns 0 if not found.
     */
    final int getIndex(int cipher, char[] chars, int offset, int length) {
        int key = cipher;
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher.");
            key = getMapping().getKey(chars, offset, length);
        }
        return getIndex(key, 0);
    }

    final void clearIndexData(int index, int leaveInts) {
        for (int i = step; --i >= leaveInts;)
            matrix[index + i] = 0;
        if (obj_step != 0) {
            int obj_index = (index / step) * obj_step;
            for (int i = obj_step; --i >= 0;)
                obj_matrix[obj_index + i] = null;
        }
    }
}
