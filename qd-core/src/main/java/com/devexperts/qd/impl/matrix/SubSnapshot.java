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
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordSink;

/**
 * Represents weakly-consistent snapshot of subscription for non-globally-synchronized retrieval.
 */
final class SubSnapshot {
    private final SubMatrix sub;
    private final int timeOffset;
    private final QDContract contract;
    private final QDFilter filter; // @NotNull
    private final RecordsContainer records;

    private int index; // current state

    /**
     * Constructs snapshot for an agent, including total agent.
     */
    // SYNC: none
    SubSnapshot(Agent agent, QDFilter filter) {
        this(agent.sub, agent == agent.collector.total ? Collector.TIME_TOTAL : Collector.TIME_SUB,
            agent.collector.getContract(), filter, agent.collector);
    }

    // SYNC: none
    SubSnapshot(SubMatrix sub, int timeOffset, QDContract contract, QDFilter filter, RecordsContainer records) {
        this.sub = sub;
        this.timeOffset = timeOffset;
        this.contract = contract;
        this.filter = filter;
        this.records = records;
        index = sub.matrix.length;
    }

    /**
     * Retrieves a portion of subscription snapshot into the specified subscription visitor.
     * Returns true if some subscription still remains in this snapshot
     * or false if everything was retrieved.
     */
    // SYNC: none
    synchronized boolean retrieveSubscription(RecordSink sink) {
        if (index <= 0)
            return false;
        int nExaminedInBatch = 0;
        while ((index -= sub.step) > 0) {
            /*
               Payload check is racy, but it is ok for our weak-consistency guarantees.
               There is a volatile read of sub in SubSnapshot constructor, thus
               any index that was payload before payload was constructed and remained payload
               during retrieval will be detected as payload here. If the index is in/out of
               payload during retrieval we don't really care what this check returns.
             */
            if (!sub.isSubscribed(index))
                continue;
            /*
               Volatile read of KEY guarantees that (key,rid,time) tuple will be
               read consistently for newly added items, since KEY is initialized via
               setVolatileInt at the end of a new subscription item creation.
             */
            int key = sub.getVolatileInt(index + Collector.KEY);
            if (key == 0)
                continue; // this can happen due to a race in isPayload check
            int rid = sub.getInt(index + Collector.RID);
            /*
               Time can change during subscription snapshot retrieval, so there is
               a potential of word-tearing for getLong operation. We cannot do anything
               about it. The only consolation is that time is word-teared if and only if
               subscription is changed after the snapshot is taken, thus distributor will
               record a proper time in its added_subscription set.
             */
            long time = contract == QDContract.HISTORY ? sub.getLong(index + timeOffset) : 0;
            int cipher = key;
            String symbol = null;
            if ((key & SymbolCodec.VALID_CIPHER) == 0) {
                cipher = 0;
                symbol = sub.getMapping().getSymbolIfPresent(key); // do not cache mapping to see concurrent mapping rehash
                if (symbol == null)
                    continue;  // not found -- was just added, but we don't "see" its mapping (mapping was rehashed, or...)
            }
            DataRecord record = records.getRecord(rid);
            if (!filter.accept(contract, record, cipher, symbol))
                continue;
            if (!sink.hasCapacity()) {
                index += sub.step; // step back so that we re-visit this record again later
                break;
            }
            sink.visitRecord(record, cipher, symbol, time);
            nExaminedInBatch++;
            if (nExaminedInBatch >= Collector.EXAMINE_BATCH_SIZE) {
                sink.flush();
                nExaminedInBatch = 0;
            }
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return index > 0;
    }
}
