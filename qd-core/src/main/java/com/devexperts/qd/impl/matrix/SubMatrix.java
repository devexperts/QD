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
import com.devexperts.qd.stats.QDStats;

/**
 * The <code>SubMatrix</code> uses dual key consisting of standard 'key' and 'rid'.
 * It is used when separate per-record storage in a common matrix is required,
 * usually for subscription storage (hence the name).
 * <p>
 * The <code>SubMatrix</code> stores 'key' at offset 0 and 'rid' at offset 1, thus:
 * <pre>
 * getInt(index + 0) = 'key'
 * getInt(index + 1) = 'rid'
 * getInt(index + step) = 'key' of next entry
 * getInt(index + step + 1) = 'rid' of next entry
 * </pre>
 * The <code>SubMatrix</code> supports {@link Mapper} with automatic usage counting.
 * It does not allow unsynchronized reads as there are no corresponding use-case exist.
 * It uses one of stored values as a payload indication (index + payload_offset).
 */
class SubMatrix extends AbstractMatrix {
    static final int KEY = 0;
    static final int RID = 1;

    private static final int MAGIC_RID = 0x5F3AC769; // Magic number used in hashing for rid.

    protected final int payloadOffset;
    protected final QDStats stats;

    // Specify 'mapper = null' to avoid automatic 'incCounter/decCounter'.
    // Specify 'payload_offset = 0' for automatic 'incPayload' by key presence.
    SubMatrix(Mapper mapper, int step, int obj_step, int payloadOffset, int capacity, int prev_magic, int max_shift,
        QDStats stats)
    {
        super(mapper, step, obj_step, capacity, prev_magic, max_shift);
        this.payloadOffset = payloadOffset;
        this.stats = stats;
    }

    void closeStats() {
        stats.close();
    }

    // SYNC: none
    int getVolatileIndex(int key, int rid, int miss_mask) {
        int index = (((key + rid * MAGIC_RID) * magic) >>> shift) * step;
        int test_key;
        // Volatile read of key here, to get get a consistently initialized rid if this
        // method is invoked without synchronization concurrently with addIndex
        while ((test_key = getVolatileInt(index + KEY)) != key || matrix[index + RID] != rid) {
            if (test_key == 0) {
                if (index > 0)
                    return index & miss_mask;
                index = matrix.length;
            }
            index -= step;
        }
        return index;
    }

    // SYNC: global or local
    int getIndex(int key, int rid, int miss_mask) {
        int index = (((key + rid * MAGIC_RID) * magic) >>> shift) * step;
        int test_key;
        while ((test_key = matrix[index + KEY]) != key || matrix[index + RID] != rid) {
            if (test_key == 0) {
                if (index > 0)
                    return index & miss_mask;
                index = matrix.length;
            }
            index -= step;
        }
        return index;
    }

    // SYNC: global+local
    int addIndex(int key, int rid) {
        int index = addIndexBegin(key, rid);
        addIndexComplete(index, key, rid);
        return index;
    }

    // SYNC: global+local
    int addIndexBegin(int key, int rid) {
        int index = getIndex(key, rid, -1);
        if (matrix[index + KEY] != 0)
            return index; // this key is already initialized
        if ((key & SymbolCodec.VALID_CIPHER) == 0 && mapper != null)
            mapper.incCounter(key);
        overallSize++;
        if (payloadOffset == KEY)
            updateAddedPayload();
        matrix[index + RID] = rid;
        return index;
    }

    // SYNC: global+local
    void addIndexComplete(int index, int key, int rid) {
        if (matrix[index + KEY] != 0)
            return; // this key is already initialized
        /*
           Set key as a last volatile operation, to make sure that if concurrent SubSnapshot
           sees a non-zero key, then all the previous values were already flushed to memory.
         */
        setVolatileInt(index + KEY, key);
    }

    boolean isSubscribed(int index) {
        return isPayload(index);
    }

    // SYNC: global or local
    boolean isPayload(int index) {
        return matrix[index + payloadOffset] != 0;
    }

    void markPayload(int index) {
        updateAddedPayload();
    }

    // SYNC: global+local
    void updateAddedPayload(int rid) {
        stats.updateAdded(rid);
        updateAddedPayload();
    }

    void updateAddedPayload() {
        if (++payloadSize > overallSize)
            throw new IllegalStateException("Payload size overflow");
    }

    // SYNC: local
    void updateRemovedPayload(int rid) {
        stats.updateRemoved(rid);
        updateRemovedPayload();
    }

    void updateRemovedPayload() {
        if (--payloadSize < 0)
            throw new IllegalStateException("Payload size underflow");
    }

    // clear hash-table "removed" elements sequence ending with an empty cell
    // SYNC: global+local
    void clearRemovedCellsTrail(int index, int occupiedOffset) {
        int prevIndex = index - step;
        if (prevIndex == 0)
            prevIndex = matrix.length - step;
        if (cellIsEmpty(prevIndex)) {
            while (cellIsRemoved(index, occupiedOffset)) {
                int key = getInt(index + KEY);
                if ((key & SymbolCodec.VALID_CIPHER) == 0)
                    mapper.decCounter(key);
                clearIndexData(index, 0);
                overallSize--;
                index += step;
                if (index == matrix.length)
                    index = step;
            }
        }
    }

    // cell is removed from QUEUE but still occupies hash-table cell
    private boolean cellIsRemoved(int index, int occupiedOffset) {
        return !cellIsEmpty(index) && !isPayload(index) && getInt(index + occupiedOffset) == 0;
    }

    // cell is completely empty
    private boolean cellIsEmpty(int index) {
        return getInt(index + KEY) == 0;
    }


    // ========== Maintenance ==========

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    SubMatrix rehash(int maxShift) {
        // allocate memory first -- leave structure untouched if crashes due to OOM
        SubMatrix dest = new SubMatrix(mapper, step, obj_step, payloadOffset, payloadSize, magic, maxShift, stats);
        rehashTo(dest);
        return dest;
    }

    void rehashTo(SubMatrix dest) {
        startRehash();
        if (overallSize == 0)
            return;
        dest.overallSize = payloadSize;
        for (int index = matrix.length; (index -= step) > 0;) {
            int key = matrix[index + KEY];
            if (key == 0)
                continue;
            // Note, that HistorySubMatrix can keep sub items that are being processed even if subscription had disappeared
            if (isPayload(index)) {
                int rid = matrix[index + RID];
                int destIndex = dest.getIndex(key, rid, -1);
                if (dest.matrix[destIndex] != 0)
                    throw new IllegalStateException("Repeated key detected");
                dest.matrix[destIndex + KEY] = key;
                dest.matrix[destIndex + RID] = rid;
                for (int i = step; --i > RID;)
                    dest.matrix[destIndex + i] = matrix[index + i];
                if (obj_step != 0) {
                    int obj_index = (index / step) * obj_step;
                    int dest_obj_index = (destIndex / step) * obj_step;
                    for (int i = obj_step; --i >= 0;)
                        dest.obj_matrix[dest_obj_index + i] = obj_matrix[obj_index + i];
                }
                dest.markPayload(destIndex);
            } else if ((key & SymbolCodec.VALID_CIPHER) == 0)
                mapper.decCounter(key);
        }
        if (dest.payloadSize != payloadSize)
            throw new IllegalStateException("Payload integrity corrupted");
    }

    // SYNC: global
    void close() {
        for (int index = matrix.length; (index -= step) > 0;) {
            int key = matrix[index + KEY];
            if (key != 0 && (key & SymbolCodec.VALID_CIPHER) == 0)
                mapper.decCounter(key);
        }
    }
}
