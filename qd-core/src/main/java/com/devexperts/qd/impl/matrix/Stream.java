/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

/**
 * The <code>Stream</code> is a matrix-based implementation of {@link QDStream}.
 */
class Stream extends Collector implements QDStream {

    private boolean enableWildcards;
    private final int wildcardCipher;

    Stream(Builder<?> builder) {
        super(builder, false, false, true);
        wildcardCipher = scheme.getCodec().getWildcardCipher();
    }

    @Override
    Agent createAgentInternal(int number, QDAgent.Builder builder, QDStats stats) {
        Agent agent = new Agent(this, number, builder, stats);
        agent.sub = new SubMatrix(mapper, STREAM_AGENT_STEP,
            builder.getAttachmentStrategy() == null ? AGENT_OBJ_STEP : AGENT_ATTACHMENT_OBJ_STEP,
            PREV_AGENT, 0, 0, Hashing.MAX_SHIFT, stats.create(QDStats.SType.AGENT_SUB));
        return agent;
    }

    @Override
    RecordMode getAgentBufferMode(Agent agent) {
        RecordMode mode = agent.getMode();
        if (hasEventTimeSequence())
            mode = mode.withEventTimeSequence();
        return mode;
    }

    // SYNC: global+local
    @Override
    boolean keepInStreamBufferOnRefilter(Agent agent, RecordCursor cur) {
        SubMatrix asub = agent.sub; // atomic read
        int index = asub.getIndex(getKey(cur.getCipher(), cur.getSymbol()), getRid(cur.getRecord()), 0);
        return asub.isPayload(index);
    }

    // SYNC: global+local
    @Override
    void refilterStreamBuffersAfterSubscriptionChange(Agent agent) {
        agent.buffer.compactAndRefilter();
    }

    // SYNC: none
    @Override
    boolean retrieveDataImpl(Agent agent, RecordSink sink, boolean snapshotOnly) {
        // Stream does not have snapshot, so snapshotOnly retrieve is always empty
        if (agent.isClosed() || snapshotOnly)
            return false;
        agent.localLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            return retrieveDataLLocked(agent, sink);
        } finally {
            agent.localLock.unlock();
        }
    }

    // SYNC: local
    private boolean retrieveDataLLocked(Agent agent, RecordSink sink) {
        if (agent.isClosed())
            return false;
        agent.nRetrieved += agent.buffer.retrieveData(sink, Integer.MAX_VALUE);
        countRetrieval(agent);
        if (agent.buffer.unblock())
            agent.localLock.signalAll();
        return agent.buffer.hasNext();
    }

    // SYNC: local
    @Override
    int getNotificationBits(Agent agent) {
        // Stream never has snapshot data
        return agent.buffer.hasNext() ? Notification.UPDATE_BIT : 0;
    }

    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: global
    @Override
    boolean processRecordSourceGLocked(Distributor distributor, Distribution dist, RecordSource source) {
        AgentIterator ait = dist.getAgentIterator();
        RecordCursor cursor;
        while ((cursor = source.next()) != null) {
            DataRecord record = cursor.getRecord();
            int rid = getRid(record);
            dist.countIncomingRecord(rid);
            int cipher = cursor.getCipher();
            String symbol = cursor.getSymbol();
            if (enableWildcards && !distributor.filter.accept(contract, record, cipher, symbol))
                continue;
            int key = getKey(cipher, symbol);
            long position = cursor.getPosition();

            int prevDistSize = dist.size();
            for (Agent agent = ait.start(this, key, rid); agent != null; agent = ait.next())
                dist.add(agent.number, position, 0, rid);
            boolean possibleDuplicate = dist.size() != prevDistSize;

            if (enableWildcards && key != wildcardCipher) {
                for (Agent agent = ait.start(this, wildcardCipher, rid); agent != null; agent = ait.next()) {
                    if (possibleDuplicate && dist.isDuplicate(agent.number, position))
                        continue;
                    if (agent.filter.getUpdatedFilter().accept(contract, record, cipher, symbol))
                        dist.add(agent.number, position, 0, rid);
                }
            }

            if (!dist.hasCapacity())
                break;
        }
        return cursor != null;
    }

    // SYNC: local
    @Override
    int processAgentDataUpdate(Distribution dist, RecordSource buffer, Agent agent) {
        for (int i = dist.firstIndex(agent); i > 0; i = dist.nextIndex(i)) {
            long position = dist.getPayloadLong(i);
            RecordCursor cursor = buffer.cursorAt(position);
            if (agent.buffer.blockNewRecord())
                return i;
            if (agent.buffer.dropNewRecord(cursor))
                continue;
            RecordCursor writeCursor = agent.buffer.addDataAndCompactIfNeeded(cursor);
            writeCursor.setEventFlags(cursor.getEventFlags()); // copy event flags (agent w/o them just ignores)
            writeCursor.setTimeMark(cursor.getTimeMark()); // copy time marks
            if (hasEventTimeSequence())
                writeCursor.setEventTimeSequence(cursor.getEventTimeSequence()); // copy time sequence
            if (agent.hasAttachmentStrategy()) {
                // find attachment if this item is still subscribed to
                Object attachment = null;
                int key = getKey(cursor.getCipher(), cursor.getSymbol());
                if (key != 0) {
                    int rid = getRid(cursor.getRecord());
                    SubMatrix asub = agent.sub;
                    int aindex = asub.getIndex(key, rid, 0);
                    if (asub.getInt(aindex + PREV_AGENT) != 0)
                        attachment = asub.getObj(aindex, ATTACHMENT);
                }
                writeCursor.setAttachment(attachment);
            }
        }
        agent.buffer.dropOldRecords();
        agent.buffer.logDrops(agent);
        return 0;
    }

    @Override
    public void setEnableWildcards(boolean enableWildcards) {
        this.enableWildcards = enableWildcards;
    }

    @Override
    public boolean getEnableWildcards() {
        return enableWildcards;
    }

    @Override
    protected boolean isSubAllowed(Agent agent, DataRecord record, int cipher, String symbol) {
        return super.isSubAllowed(agent, record, cipher, symbol) && (enableWildcards || cipher != wildcardCipher);
    }
}
