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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;

/**
 * Pools one record cursor for usage during distribution and retrieval of data.
 * <b>This class is not thread-safe.</b>
 */
final class RecordCursorKeeper {
    private final RecordCursor.Owner owner = RecordCursor.allocateOwner();
    private Object lastObject;

    RecordCursor.Owner getForTickerMatrix(TickerMatrix matrix, boolean readOnly) {
        if (lastObject != matrix) {
            lastObject = matrix;
            matrix.setupOwner(owner, readOnly);
        }
        return owner;
    }

    RecordCursor.Owner getForHistoryBufferReadOnly(HistoryBuffer hb, DataRecord record, int cipher, String symbol) {
        if (lastObject != hb) {
            lastObject = hb;
            hb.setupOwner(owner, record, cipher, symbol);
        }
        return owner;
    }

    // Automatically invoked from GlobalLock.unlock()
    void reset() {
        if (lastObject != null) {
            lastObject = null;
            owner.reset();
        }
    }
}
