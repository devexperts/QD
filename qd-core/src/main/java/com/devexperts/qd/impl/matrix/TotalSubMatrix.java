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

import com.devexperts.qd.stats.QDStats;

/**
 * The <code>TotalSubMatrix</code> is intended for total subscription.
 * It reflects the fact that total subscription uses modified markup of subscribed entries.
 */
class TotalSubMatrix extends SubMatrix {
    TotalSubMatrix(Mapper mapper, int step, int objStep, int payloadOffset, int capacity, int prevMagic, int maxShift,
        QDStats stats)
    {
        super(mapper, step, objStep, payloadOffset, capacity, prevMagic, maxShift, stats);
    }

    @Override
    boolean isSubscribed(int index) {
        return matrix[index + payloadOffset] > 0;
    }

    @Override
    SubMatrix rehash(int maxShift) {
        // preserve proper class for new matrix
        SubMatrix dest = new TotalSubMatrix(mapper, step, obj_step, payloadOffset, payloadSize, magic, maxShift, stats);
        rehashTo(dest);
        return dest;
    }
}
