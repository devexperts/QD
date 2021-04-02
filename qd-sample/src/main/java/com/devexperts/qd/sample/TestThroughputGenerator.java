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
package com.devexperts.qd.sample;

import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;

class TestThroughputGenerator {
    private final int index;
    private final TestThroughputContext ctx;
    private final TestThroughputConfig config;
    private int[][] sequence;
    private int rnd;

    long start;
    int n;

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

    }

    void retrieveRecordBuffer(RecordBuffer buf) {
        int iflds = config.ifields;
        int oflds = config.ofields;
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
            int sequence = --this.sequence[rid][sid];
            for (int k = 0; k < iflds; k++)
                cur.setInt(k, sequence & ctx.ifldmask[k]);
            for (int k = 0; k < oflds; k++)
                cur.setObj(k, sequence & config.ovalmask);
        }
    }
}
