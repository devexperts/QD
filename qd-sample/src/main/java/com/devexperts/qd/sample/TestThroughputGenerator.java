/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;

class TestThroughputGenerator {
    private final int index;
    private final TestThroughputContext ctx;
    private final TestThroughputConfig config;
    private final int[][] sequence;

    private int initCount; // number of data to be initially filled
    private int rnd;

    final long start;
    final int n;

    TestThroughputGenerator(int index, TestThroughputContext ctx) {
        this.index = index;
        this.ctx = ctx;
        config = ctx.config;
        sequence = new int[ctx.config.records][ctx.config.symbols];
        start = ctx.config.distsplit == 0 ? 0 :
            (long) ctx.size() * ctx.config.distsplit * index / ctx.config.dists;
        long stop = ctx.config.distsplit == 0 ? ctx.size() :
            (long) ctx.size() * ctx.config.distsplit * (index + 1) / ctx.config.dists;
        n = (int) (stop - start);
        initCount = n * config.records;
    }

    void retrieveRecordBuffer(RecordBuffer buf) {
        if (initCount > 0) {
            initialFill(buf);
            return;
        }
        int rofs = (rnd & 0x7fffffff) % config.records;
        int sid = 0;
        int rid = 0;
        for (int i = 0; i < config.distbuf; i++) {
            if (!config.symbatch || i == 0) {
                rnd = rnd * 134775813 + 1;
                sid = (int) ((start + (rnd & 0x7fffffff) % n) % config.symbols);
                rid = (i + rofs) % config.records;
            }
            RecordCursor cur = buf.add(ctx.scheme.getRecord(rid), ctx.getCipher(sid), ctx.getSymbol(sid));
            fillData(cur, sid, rid);
        }
    }

    /**
     * Fill buffer with initial data: sequentially covers all symbols/records in generator's range.
     */
    void initialFill(RecordBuffer buf) {
        for (int i = 0; i < config.distbuf && initCount > 0; i++) {
            initCount--;
            int sid = (int) ((start + initCount % n) % config.symbols);
            int rid = (initCount / n) % config.records;

            RecordCursor cur = buf.add(ctx.scheme.getRecord(rid), ctx.getCipher(sid), ctx.getSymbol(sid));
            fillData(cur, sid, rid);
        }
    }

    private void fillData(RecordCursor cur, int sid, int rid) {
        int sequence = --this.sequence[rid][sid];
        for (int k = 0; k < config.ifields; k++) {
            cur.setInt(k, sequence & ctx.ifldmask[k]);
        }
        for (int k = 0; k < config.ofields; k++) {
            cur.setObj(k, sequence & config.ovalmask);
        }
    }
}
