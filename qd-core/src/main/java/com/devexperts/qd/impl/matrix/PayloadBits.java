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

final class PayloadBits {
    private final int payloadShift; // Used to convert: bit = index >> payload_shift;
    private final int[] payloadBits; // Check (payload_bits[bit >> 5] & (1 <<  bit)) != 0;

    PayloadBits(int matrixLength, int step) {
        int n = 0;
        while ((step >> ++n) != 0) {
            // nothing to do -- see loop expression
        }
        payloadShift = n - 1;
        payloadBits = new int[((matrixLength >> payloadShift) + 31) >> 5];
    }

    boolean isPayload(int index) {
        int bit = index >> payloadShift;
        return (payloadBits[bit >> 5] & (1 << bit)) != 0;
    }

    boolean markPayload(int index) {
        int bit = index >> payloadShift;
        int oldBits = payloadBits[bit >> 5];
        int newBits = oldBits | (1 << bit);
        if (newBits == oldBits)
            return false;
        payloadBits[bit >> 5] = newBits;
        return true;
    }

    boolean clearPayload(int index) {
        int bit = index >> payloadShift;
        int oldBits = payloadBits[bit >> 5];
        int newBits = oldBits & ~(1 << bit);
        if (newBits == oldBits)
            return false;
        payloadBits[bit >> 5] = newBits;
        return true;
    }
}
