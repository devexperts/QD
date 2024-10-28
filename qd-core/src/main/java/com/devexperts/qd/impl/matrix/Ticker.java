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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.SystemProperties;

/**
 * The <code>Ticker</code> is a matrix-based implementation of {@link QDTicker}.
 */
class Ticker extends Collector implements QDTicker {

    private static final int RETRIEVE_BATCH_SIZE = SystemProperties.getIntProperty(
        Ticker.class, "retrieveBatchSize", 100, 1, Integer.MAX_VALUE);

    private final TickerStorage storage; // SYNC: global

    Ticker(Builder<?> builder) {
        super(builder, false, true, false);
        storage = new TickerStorage(scheme, mapper, statsStorage, builder.hasEventTimeSequence());
    }

    @Override
    Agent createAgentInternal(int number, QDAgent.Builder builder, QDStats stats) {
        Agent agent = new Agent(this, number, builder, stats);
        agent.sub = new SubMatrix(mapper, TICKER_AGENT_STEP,
            builder.getAttachmentStrategy() == null ? AGENT_OBJ_STEP : AGENT_ATTACHMENT_OBJ_STEP,
            PREV_AGENT, 0, 0, Hashing.MAX_SHIFT, stats.create(QDStats.SType.AGENT_SUB));
        return agent;
    }

    // SYNC: none
    @Override
    boolean retrieveDataImpl(Agent agent, RecordSink sink, boolean snapshotOnly) {
        if (agent.isClosed())
            return false;
        agent.localLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            return retrieveDataLLocked(agent, sink, snapshotOnly);
        } finally {
            agent.localLock.unlock();
        }
    }

    // SYNC: local
    private boolean retrieveDataLLocked(Agent agent, RecordSink sink, boolean snapshotOnly) {
        if (agent.isClosed())
            return false;
        try {
            boolean moreSnapshot = !agent.snapshotQueue.isEmpty();
            boolean moreUpdate = !snapshotOnly && !agent.updateQueue.isEmpty();
            while (moreSnapshot || moreUpdate) {
                if (!moreUpdate)  // give balance to snapshot if there's nothing to retrieve from update anyway
                    agent.nSnapshotRetrieved = 0;
                if (agent.nSnapshotRetrieved < RETRIEVE_BATCH_SIZE && moreSnapshot) {
                    // retrieve more from snapshot
                    int nRetrieveLimit = RETRIEVE_BATCH_SIZE - agent.nSnapshotRetrieved;
                    int nRetrievedWithBit = agent.snapshotQueue.retrieveForTicker(this, agent, sink,
                        nRetrieveLimit, SNAPSHOT_QUEUE);
                    int nRetrieved = nRetrievedWithBit & ~AgentQueue.RETRIEVE_NO_CAPACITY_BIT;
                    agent.nSnapshotRetrieved += nRetrieved; // increase balance
                    agent.nRetrieved += nRetrieved; // for stats
                    if ((nRetrievedWithBit & AgentQueue.RETRIEVE_NO_CAPACITY_BIT) != 0)
                        return true; // no more capacity
                    if (agent.snapshotQueue.isEmpty())
                        moreSnapshot = false;
                }
                if (!moreSnapshot) // give balance to update if there's nothing to retrieve from snapshot anyway
                    agent.nSnapshotRetrieved = RETRIEVE_BATCH_SIZE;
                if (agent.nSnapshotRetrieved > 0 && moreUpdate) {
                    // positive balance -- retrieved from snapshot last time and there's something to update.
                    // Now retrieve matching number of records from update queue
                    int nRetrievedWithBit =
                        agent.updateQueue.retrieveForTicker(this, agent, sink, agent.nSnapshotRetrieved, UPDATE_QUEUE);
                    int nRetrieved = nRetrievedWithBit & ~AgentQueue.RETRIEVE_NO_CAPACITY_BIT;
                    agent.nSnapshotRetrieved -= nRetrieved; // decrease balance
                    agent.nRetrieved += nRetrieved; // for stats
                    if ((nRetrievedWithBit & AgentQueue.RETRIEVE_NO_CAPACITY_BIT) != 0)
                        return true; // no more capacity
                    if (agent.updateQueue.isEmpty())
                        moreUpdate = false;
                }
            }
            return false; // no more snapshot or update (but still had capacity last time we've checked)
        } finally {
            countRetrieval(agent);
        }
    }

    /**
     * Retrieves data from ticker storage and returns true if it was there.
     * If called for non-available record, will return false without appending record.
     * This method does not check if sink has capacity (shall be checked beforehand).
     */
    // SYNC: local (it is invoked from retrieve(Snapshot/Data)LLocked
    boolean getRecordData(Agent agent, RecordSink sink, int key, int rid, int mark, Object attachment) {
        return storage.getMatrix(rid).getRecordData(key, sink, agent.retrievalKeeper, mark, attachment);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    @Override
    boolean totalRecordRemoved(int key, int rid, SubMatrix tsub, int tindex) {
        super.totalRecordRemoved(key, rid, tsub, tindex);
        if (!shouldStoreEverything(key, rid))
            storage.removeRecord(key, rid);
        return true;
    }

    @Override
    void enqueueAddedRecord(Agent agent, SubMatrix asub, int aindex) {
        if (agent.hasVoidRecordListener())
            return; // we don't need to send all our data to void listener -- it consumes it automatically
        int snapshot = asub.getInt(aindex + SNAPSHOT_QUEUE);
        if ((snapshot & QUEUE_BIT) != 0)
            return; // it is already in snapshot queue or waiting for data to arrive
        if ((asub.getInt(aindex + UPDATE_QUEUE) & QUEUE_BIT) != 0) {
            snapshot = 0; // clear mark, we are going to send snapshot
        }
        // set QUEUE_BIT to indicate waiting for data to arrive/retrieve
        // it may be already in snapshot queue
        asub.setInt(aindex + SNAPSHOT_QUEUE, snapshot | QUEUE_BIT);
        // check data in storage
        int key = asub.getInt(aindex + KEY);
        int rid = asub.getInt(aindex + RID);
        if (!storage.getMatrix(rid).hasRecord(key))
            return;  // don't have any data in storage -- we'll be waiting
        if (agent.snapshotQueue.linkToQueue(agent, aindex, SNAPSHOT_QUEUE, true))
            subNotifyAccumulator |= NOTIFY_SUB_SNAPSHOT_AVAILABLE; // added first entry -- notify listener when done
        if (agent.updateQueue.linkToQueue(agent, aindex, UPDATE_QUEUE, true))
            subNotifyAccumulator |= NOTIFY_SUB_DATA_AVAILABLE; // added first entry -- notify listener when done
    }

    @Override
    void dequeueRemovedRecord(Agent agent, SubMatrix asub, int aindex) {
        // drop bit from SNAPSHOT_QUEUE and UPDATE_QUEUE to indicate that no more data update needed
        if ((asub.getInt(aindex + SNAPSHOT_QUEUE) & QUEUE_BIT) == 0 &&
            (asub.getInt(aindex + UPDATE_QUEUE) & QUEUE_BIT) != 0)
        {
            asub.setInt(aindex + SNAPSHOT_QUEUE, 0); // clear time mark
        }
        agent.snapshotQueue.resetQueueBit(agent, aindex, SNAPSHOT_QUEUE);
        agent.updateQueue.resetQueueBit(agent, aindex, UPDATE_QUEUE);
        agent.snapshotQueue.cleanupEmptyHeadForTicker(agent, aindex, SNAPSHOT_QUEUE);
        agent.updateQueue.cleanupEmptyHeadForTicker(agent, aindex, UPDATE_QUEUE);
    }

    // SYNC: local
    @Override
    int getNotificationBits(Agent agent) {
        // Snapshot queue is retrieved by any retrieve
        if (!agent.snapshotQueue.isEmpty()) {
            return Notification.SNAPSHOT_BIT | Notification.UPDATE_BIT;
        } else {
            // updateQueue contains only updates
            return !agent.updateQueue.isEmpty() ? Notification.UPDATE_BIT : 0;
        }
    }

    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: global
    @Override
    boolean processRecordSourceGLocked(Distributor distributor, Distribution dist, RecordSource source) {
        AgentProcessor processor = dist.getProcessor(management.getInterleave());
        SubMatrix tsub = total.sub;
        RecordCursor cursor;
        while ((cursor = source.next()) != null) {
            DataRecord record = cursor.getRecord();
            int rid = getRid(record);
            dist.countIncomingRecord(rid);
            int cipher = cursor.getCipher();
            String symbol = cursor.getSymbol();
            if (storeEverything && !distributor.filter.accept(contract, record, cipher, symbol))
                continue;
            int key = getKey(cipher, symbol);
            int tindex = tsub.getIndex(key, rid, 0);
            int nagent = tsub.getInt(tindex + NEXT_AGENT);
            // storeEverything support
            if (shouldStoreEverything(record, cipher, symbol)) {
                if (key == 0)
                    key = mapper.addKey(symbol);
            } else if (nagent <= 0)
                continue; // No subscription -- ignore incoming event (unless storing everything)
            if (!storage.putRecordCursor(key, rid, cursor, keeper))
                continue;
            processor.processAgentsList(nagent, tsub.getInt(tindex + NEXT_INDEX), cursor.getTimeMark(), rid);
            if (!dist.hasCapacity())
                break;
        }
        processor.flush();
        return cursor != null;
    }

    // SYNC: local
    @Override
    int processAgentDataUpdate(Distribution dist, RecordSource buffer, Agent agent) {
        boolean dirty = agent.subModCount != dist.getSubModCount(agent);
        SubMatrix asub = agent.sub;
        SubMatrix osub = dist.getSub(agent);
        for (int i = dist.firstIndex(agent); i > 0; i = dist.nextIndex(i)) {
            int aindex = dist.getPayload1(i);
            if (dirty) {
                aindex = asub.getIndex(osub.getInt(aindex + KEY), osub.getInt(aindex + RID), 0);
                if (asub.getInt(aindex + PREV_AGENT) == 0)
                    continue;
            }
            int mark = dist.getPayload2(i);
            // snapshot queue / mark
            int snapshot = asub.getInt(aindex + SNAPSHOT_QUEUE);
            if (snapshot == QUEUE_BIT) {
                // waiting for snapshot
                agent.snapshotQueue.linkToQueue(agent, aindex, SNAPSHOT_QUEUE, true);
            } else if (snapshot == 0) {
                // has no snapshot queue bit and is not in snapshot queue, so can record mark there
                asub.setInt(aindex + SNAPSHOT_QUEUE, mark & ~QUEUE_BIT);
            } else if ((asub.getInt(aindex + UPDATE_QUEUE) & QUEUE_BIT) == 0) {
                // was in snapshot queue, set QUEUE_BIT to remain in snapshot queue
                // WARNING: may cause spurious appear in snapshot queue of already processed (via update queue) event
                asub.setInt(aindex + SNAPSHOT_QUEUE, snapshot | QUEUE_BIT); // already linked
            }
            // update queue
            agent.updateQueue.linkToQueue(agent, aindex, UPDATE_QUEUE, true);
        }
        return 0;
    }

    @Override
    // SYNC: global+local
    void examineSubDataInternalByIndex(Agent agent, int aindex, RecordSink sink) {
        SubMatrix asub = agent.sub;
        int key = asub.getInt(aindex + KEY);
        int rid = asub.getInt(aindex + RID);
        Object attachment = agent.hasAttachmentStrategy() ? asub.getObj(aindex, ATTACHMENT) : null;
        storage.getMatrix(rid).examineDataAlways(key, getCipher(key), getSymbol(key), sink, keeper, attachment);
    }

    // ========== QDTicker Implementation ==========

    @Override
    public boolean isAvailable(DataRecord record, int cipher, String symbol) {
        return storage.getMatrix(getRid(record)).isAvailable(cipher, symbol);
    }

    @Override
    public void remove(RecordSource source) {
        globalLock.lock(CollectorOperation.REMOVE_DATA);
        try {
            removeGLocked(source);
        } finally {
            globalLock.unlock();
        }
    }

    private void removeGLocked(RecordSource source) {
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            int rid = getRid(cur.getRecord());
            int key = getKey(cur.getCipher(), cur.getSymbol());
            storage.removeRecord(key, rid);
        }
    }

    @Override
    public int getInt(DataIntField field, int cipher, String symbol) {
        return storage.getMatrix(getRid(field.getRecord())).getInt(cipher, symbol, field.getIndex());
    }

    @Override
    public Object getObj(DataObjField field, int cipher, String symbol) {
        return storage.getMatrix(getRid(field.getRecord())).getObj(cipher, symbol, field.getIndex());
    }

    @Override
    public void getData(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        storage.getMatrix(getRid(record)).getData(owner, cipher, symbol);
    }

    @Override
    public boolean getDataIfAvailable(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        return storage.getMatrix(getRid(record)).getDataIfAvailable(owner, cipher, symbol);
    }

    @Override
    public boolean getDataIfSubscribed(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        if (!total.isSubscribed(record, cipher, symbol, 0))
            return false;
        storage.getMatrix(getRid(record)).getData(owner, cipher, symbol);
        return true;
    }

    @Override
    public boolean examineData(RecordSink sink) {
        return storage.examineData(sink);
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        RecordCursor.Owner dataOwner = RecordCursor.allocateOwner();
        int nExaminedInBatch = 0;
        for (RecordCursor subCursor; (subCursor = sub.next()) != null;) {
            if (getDataIfAvailable(dataOwner, subCursor.getRecord(), subCursor.getCipher(), subCursor.getSymbol())) {
                if (!sink.hasCapacity()) {
                    if (nExaminedInBatch > 0)
                        sink.flush();
                    return true;
                }
                sink.append(dataOwner.cursor());
                nExaminedInBatch++;
                if (nExaminedInBatch >= EXAMINE_BATCH_SIZE) {
                    sink.flush();
                    nExaminedInBatch = 0;
                }
            }
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return false;
    }

    // ========== Debugging ==========

    @Override
    <T extends CollectorDebug.SymbolReferenceVisitor> T visitSymbols(T srv, CollectorDebug.RehashCrashInfo rci) {
        super.visitSymbols(srv, rci);
        storage.visitStorageSymbols(srv);
        return srv;
    }

}
