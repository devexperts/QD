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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

/**
 * The <code>TickerStorage</code> stores current values of all data records.
 * It maintains a set of {@link TickerMatrix} - one for each data record -
 * to actually store current values of corresponding data records.
 *
 * <p><b>TickerStorage is modified only under global lock.</b>
 */
final class TickerStorage {

    private final TickerMatrix[] matrices;
    private final QDStats stats;

    TickerStorage(DataScheme scheme, Mapper mapper, QDStats stats, boolean withEventTimeSequence) {
        mapper.incMaxCounter(scheme.getRecordCount());
        matrices = new TickerMatrix[scheme.getRecordCount()];
        for (int i = matrices.length; --i >= 0;)
            matrices[i] = new TickerMatrix(scheme.getRecord(i), mapper, 0, 0, withEventTimeSequence);
        this.stats = stats;
    }

    TickerMatrix getMatrix(int rid) {
        return matrices[rid];
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    private TickerMatrix rehashMatrixIfNeeded(int rid) {
        TickerMatrix matrix = matrices[rid];
        if (Hashing.needRehash(matrix.shift, matrix.overallSize, matrix.payloadSize, Hashing.MAX_SHIFT))
            matrix = matrices[rid] = matrix.rehash();
        return matrix;
    }

    // NOTE: There is a similar method called "putRecord"
    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    boolean putRecordCursor(int key, int rid, RecordCursor cursor, RecordCursorKeeper keeper) {
        return rehashMatrixIfNeeded(rid).putRecordCursor(key, rid, cursor, keeper, stats);
    }

    void removeRecord(int key, int rid) {
        // remove will not rehash to reduce memory, to allow clear close of agents w/o allocating more memory.
        // allocated memory will linger after remove until new data comes in and added.
        matrices[rid].removeRecord(key, rid, stats);
    }

    boolean examineData(RecordSink sink) {
        RecordCursorKeeper keeper = new RecordCursorKeeper();
        int nExaminedInBatch = 0;
        for (TickerMatrix matrix : matrices) {
            nExaminedInBatch = matrix.examineData(sink, keeper, nExaminedInBatch);
            if (nExaminedInBatch < 0)
                return true;
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return false;
    }

    // ========== Debugging ==========

    void visitStorageSymbols(CollectorDebug.SymbolReferenceVisitor srv) {
        CollectorDebug.SymbolReferenceLocation srl = new CollectorDebug.SymbolReferenceLocation();
        srl.storage = true;
        for (TickerMatrix tm : matrices)
            tm.visitTickerMatrixSymbols(srv, srl);
    }
}
