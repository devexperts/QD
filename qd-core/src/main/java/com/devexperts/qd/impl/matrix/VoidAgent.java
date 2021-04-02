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
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.AbstractAgent;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

class VoidAgent extends AbstractAgent implements RecordsContainer {
    private static final int STEP = 2;
    private static final int HISTORY_STEP = 4;

    private static final int VOID_TIME_SUB = 2;
    private static final int ATTACHMENT = 0;

    private final QDContract contract;
    private final DataScheme scheme;
    private final QDFilter filter;
    private volatile PayloadBitsSubMatrix sub;

    VoidAgent(VoidAgentBuilder builder) {
        super(builder.contract, builder);
        this.contract = builder.contract;
        this.scheme = builder.scheme;
        this.filter = builder.getFilter();
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        // nothing to do
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        return false; // no data here
    }

    @Override
    public synchronized void addSubscription(RecordSource source) {
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (EventFlag.REMOVE_SYMBOL.in(cur.getEventFlags())) {
                // remove
                removeSubInternal(cur);
            } else {
                // add
                if (filter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                    addSubInternal(cur);
            }
        }
    }

    @Override
    public synchronized void removeSubscription(RecordSource source) {
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            removeSubInternal(cur);
        }
    }

    @Override
    public synchronized void setSubscription(RecordSource source) {
        sub = null; // drop old subscription to create it from scratch
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            addSubInternal(cur);
        }
    }

    @Override
    public void close() {
        // nothing to do here
    }

    @Override
    public void closeAndExamineDataBySubscription(RecordSink sink) {
        // nothing to do here
    }

    @Override
    public QDStats getStats() {
        return QDStats.VOID; // never keeps stats
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        SubMatrix sub = this.sub;
        if (sub == null)
            return false;
        int key = getKey(sub, cipher, symbol);
        int rid = record.getId();
        int index = sub.getVolatileIndex(key, rid, 0);
        /*
           Potential word-tearing in getLong if subscription time is changed concurrently with this isSub
           invocation, but we cannot really do anything about it.
         */
        return sub.isPayload(index) && (contract != QDContract.HISTORY ||
            time >= sub.getLong(index + VOID_TIME_SUB));
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        SubMatrix sub = this.sub;
        return sub != null &&
            new SubSnapshot(sub, VOID_TIME_SUB, contract, QDFilter.ANYTHING, this).retrieveSubscription(sink);
    }

    @Override
    public synchronized int getSubscriptionSize() {
        SubMatrix sub = this.sub;
        return sub == null ? 0 : sub.payloadSize;
    }

    @Override
    public DataRecord getRecord(int rid) {
        return scheme.getRecord(rid);
    }

    private int getRid(DataRecord record) {
        if (record.getScheme() != scheme)
            throw new IllegalArgumentException("Wrong record scheme: " + record);
        return record.getId();
    }

    private int addKey(SubMatrix sub, int cipher, String symbol) {
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher");
            return sub.mapper.addKey(symbol);
        }
        return cipher;
    }

    private int getKey(SubMatrix sub, int cipher, String symbol) {
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher");
            return sub.mapper.getMapping().getKey(symbol);
        }
        return cipher;
    }

    private void addSubInternal(RecordCursor cur) {
        PayloadBitsSubMatrix sub = getOrCreateSub();
        if (needsRehash(sub))
            sub = rehash(sub);
        int key = addKey(sub, cur.getCipher(), cur.getSymbol());
        int rid = getRid(cur.getRecord());
        int index = sub.addIndex(key, rid);
        sub.markPayload(index);
        if (hasAttachmentStrategy())
            sub.setObj(index, ATTACHMENT,
                updateAttachment(sub.getObj(index, ATTACHMENT), cur, false));
        if (contract == QDContract.HISTORY)
            sub.setLong(index + VOID_TIME_SUB, cur.getTime());
    }

    private void removeSubInternal(RecordCursor cur) {
        PayloadBitsSubMatrix sub = getOrCreateSub();
        int key = getKey(sub, cur.getCipher(), cur.getSymbol());
        int rid = getRid(cur.getRecord());
        int index = sub.getIndex(key, rid, 0);
        if (index == 0 || !sub.isPayload(index))
            return; // not subscribed
        if (hasAttachmentStrategy()) {
            Object attachment = updateAttachment(sub.getObj(index, ATTACHMENT), cur, true);
            sub.setObj(index, ATTACHMENT, attachment);
            if (attachment != null) // don't actually remove, but adjust attachment and return
                return;
        }
        sub.clearPayload(index);
    }

    private boolean needsRehash(PayloadBitsSubMatrix sub) {
        return Hashing.needRehash(sub.shift, sub.overallSize, sub.payloadSize, Hashing.MAX_SHIFT);
    }

    private PayloadBitsSubMatrix rehash(PayloadBitsSubMatrix sub) {
        return this.sub = sub.rehash(Hashing.MAX_SHIFT);
    }

    private PayloadBitsSubMatrix getOrCreateSub() {
        PayloadBitsSubMatrix sub = this.sub;
        if (sub != null)
            return sub;
        Mapper mapper = new Mapper(this);
        mapper.incMaxCounter(scheme.getRecordCount()); // at most one per record forever
        sub = new PayloadBitsSubMatrix(mapper,
            contract == QDContract.HISTORY ? HISTORY_STEP : STEP,
            hasAttachmentStrategy() ? 0 : 1,
            0, 0, Hashing.MAX_SHIFT);
        this.sub = sub;
        return sub;
    }

    private static class PayloadBitsSubMatrix extends SubMatrix {
        private final PayloadBits payloadBits;

        private PayloadBitsSubMatrix(Mapper mapper, int step, int objStep, int capacity,
            int prevMagic, int maxShift)
        {
            // pass payloadOffset == Integer.MAX_VALUE. It should not be used (isPayload is overridden!) and must fail if used
            super(mapper, step, objStep, Integer.MAX_VALUE, capacity, prevMagic, maxShift, QDStats.VOID);
            payloadBits = new PayloadBits(matrix.length, step);
        }

        @Override
        PayloadBitsSubMatrix rehash(int maxShift) {
            PayloadBitsSubMatrix dest = new PayloadBitsSubMatrix(mapper, step, obj_step, payloadSize, magic, maxShift);
            rehashTo(dest);
            return dest;
        }

        @Override
        boolean isPayload(int index) {
            return payloadBits.isPayload(index);
        }

        @Override
        void markPayload(int index) {
            if (payloadBits.markPayload(index))
                updateAddedPayload();
        }

        void clearPayload(int index) {
            if (payloadBits.clearPayload(index)) {
                updateRemovedPayload();
                clearIndexData(index, 2);
            }
        }
    }
}
