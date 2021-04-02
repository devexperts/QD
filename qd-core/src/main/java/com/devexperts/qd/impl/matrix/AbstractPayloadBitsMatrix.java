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

abstract class AbstractPayloadBitsMatrix extends AbstractMatrix {
    private final PayloadBits payloadBits;

    AbstractPayloadBitsMatrix(Mapper mapper, int step, int obj_step, int capacity, int prev_magic) {
        super(mapper, step, obj_step, capacity, prev_magic, Hashing.MAX_SHIFT);
        payloadBits = new PayloadBits(matrix.length, step);
    }

    final boolean isPayload(int index) {
        return payloadBits.isPayload(index);
    }

    final void markPayload(int index) {
        if (payloadBits.markPayload(index))
            updateAddedPayload();
    }

    final boolean clearPayload(int index) {
        if (payloadBits.clearPayload(index)) {
            updateRemovedPayload(index);
            return true;
        }
        return false;
    }

    final void rehashTo(AbstractPayloadBitsMatrix dest) {
        startRehash();
        for (int index = matrix.length; (index -= step) > 0;) {
            int key = matrix[index];
            if (key == 0)
                continue;
            if (isPayload(index)) {
                int destIndex = dest.getIndex(key, -1);
                if (dest.matrix[destIndex] != 0)
                    throw new IllegalStateException("Repeated key.");
                dest.matrix[destIndex] = key;
                dest.overallSize++;
                for (int i = step; --i > 0;)
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
        if (dest.overallSize != payloadSize || dest.payloadSize != payloadSize)
            throw new IllegalStateException("Payload integrity corrupted.");
    }

    private void updateAddedPayload() {
        if (++payloadSize > overallSize)
            throw new IllegalStateException("Payload size overflow.");
    }

    private void updateRemovedPayload(int index) {
        if (--payloadSize < 0)
            throw new IllegalStateException("Payload size underflow.");
        clearIndexData(index, 1);
    }
}
