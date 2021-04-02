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

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;

import static com.devexperts.qd.impl.matrix.History.ATTACHMENT;
import static com.devexperts.qd.impl.matrix.History.KEY;
import static com.devexperts.qd.impl.matrix.History.LAST_RECORD;
import static com.devexperts.qd.impl.matrix.History.QUEUE_BIT;
import static com.devexperts.qd.impl.matrix.History.RID;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_BEGIN;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_END;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_QUEUE;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_SNIP;
import static com.devexperts.qd.impl.matrix.History.TIME_KNOWN;
import static com.devexperts.qd.impl.matrix.History.TIME_SUB;
import static com.devexperts.qd.impl.matrix.History.TX_DIRTY_LAST_RECORD_BIT;
import static com.devexperts.qd.impl.matrix.History.TX_PENDING;
import static com.devexperts.qd.impl.matrix.History.UPDATE_QUEUE;
import static com.devexperts.qd.impl.matrix.History.isAgentSubProcessing;
import static com.devexperts.qd.impl.matrix.History.isAgentSubSnip;
import static com.devexperts.qd.impl.matrix.History.isAgentTxDirty;
import static com.devexperts.qd.impl.matrix.History.makeAgentNonTxDirty;
import static com.devexperts.qd.impl.matrix.History.makeAgentTxDirty;

/**
 * Used by Ticker (snapshotQueue & updateQueue) and History (snapshotQueue only)
 */
final class AgentQueue {
    private  static final Logging log = Logging.getLogging(AgentQueue.class);

    private static final int EOL = ~QUEUE_BIT;

    private int head = EOL;  // SYNC: rw(local).
    private int tail = EOL;  // SYNC: rw(local).

    // SYNC: local
    boolean isEmpty() {
        return head == EOL;
    }

    /**
     * Clears QUEUE_BIT at the specified item
     * @param queueOfs SNAPSHOT_QUEUE when working with snapshotQueue,
     *                 UPDATE_QUEUE when working with updateQueue.
     */
    // SYNC: local
    void resetQueueBit(Agent agent, int aindex, int queueOfs) {
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        SubMatrix asub = agent.sub;
        asub.setInt(aindex + queueOfs, asub.getInt(aindex + queueOfs) & ~QUEUE_BIT);
    }

    /**
     * Adds agent's index to queue.
     * Returns true if the first item was added to the queue and thus listeners shall be notified about data available.
     * QUEUE_BIT is always set when adding new item to the queue and on existing items it is set if
     * setQueueBitOnExisting is true.
     * @param queueOfs SNAPSHOT_QUEUE when working with snapshotQueue,
     *                 UPDATE_QUEUE when working with updateQueue.
     * @param setQueueBitOnExisting when true, then QUEUE_BIT is set on existing items in the queue.
     */
    // SYNC: local
    boolean linkToQueue(Agent agent, int aindex, int queueOfs, boolean setQueueBitOnExisting) {
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        SubMatrix asub = agent.sub;
        int val = asub.getInt(aindex + queueOfs);
        if ((val & ~QUEUE_BIT) != 0) {
            // this item is already in queue
            if (setQueueBitOnExisting)
                asub.setInt(aindex + queueOfs, val | QUEUE_BIT); // ensure that QUEUE_BIT is set
            return false;
        }
        boolean first = false;
        int tail = this.tail;
        if (tail == EOL) {
            head = aindex;
            first = true;
        } else
            asub.setInt(tail + queueOfs, aindex | (asub.getInt(tail + queueOfs) & QUEUE_BIT));
        asub.setInt(aindex + queueOfs, QUEUE_BIT | EOL); // at the tail now with QUEUE_BIT set
        this.tail = aindex;
        return first;
    }

    static final int RETRIEVE_NO_CAPACITY_BIT = 1 << 31; // highest bit

    /**
     * Retrieves ticker data using a given queue.
     * This method should be only called when queue is not empty.
     * @param sink the sink to retrieve data to.
     * @param nRetrieveLimit the maximal number of records to retrieve.
     * @param queueOfs SNAPSHOT_QUEUE when working with snapshotQueue,
     *                 UPDATE_QUEUE when working with updateQueue.
     * @return number of records retrieved and appended to sink.
     *         {@link #RETRIEVE_NO_CAPACITY_BIT} is set when sink ran out of capacity in process.
     */
    // SYNC: local
    int retrieveForTicker(Ticker ticker, Agent agent, RecordSink sink, int nRetrieveLimit, int queueOfs) {
        assert agent.collector == ticker;
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        int aindex = head;
        assert aindex != EOL; // should be only called when queue is not empty
        SubMatrix asub = agent.sub;
        int nRetrieved = 0;
        do {
            // UPDATE_QUEUE in ticker uses QUEUE_BIT to indicate whether item has data
            int update = asub.getInt(aindex + UPDATE_QUEUE);
            boolean retainSnapshotBit = queueOfs == SNAPSHOT_QUEUE && (update & QUEUE_BIT) == 0;
            if ((update & QUEUE_BIT) != 0) {
                if (!sink.hasCapacity())
                    return nRetrieved | RETRIEVE_NO_CAPACITY_BIT; // no more capacity
                int key = asub.getInt(aindex + KEY);
                int rid = asub.getInt(aindex + RID);
                // SNAPSHOT_QUEUE in ticker stores mark when QUEUE_BIT is not set and QUEUE_BIT in UPDATE_QUEUE is set
                int mark = asub.getInt(aindex + SNAPSHOT_QUEUE);
                if ((mark & QUEUE_BIT) != 0) {
                    // tag item as retrieved-at-least-once, but retain pointer in SNAPSHOT_QUEUE
                    asub.setInt(aindex + SNAPSHOT_QUEUE, mark & ~QUEUE_BIT);
                    mark = 0; // not a mark -- snapshot queue next is/will be stored here
                }
                // Attachment (if needed)
                Object attachment = agent.hasAttachmentStrategy() ? asub.getObj(aindex, ATTACHMENT) : null;
                // reset queue bit first (this marks item as retrieved)
                asub.setInt(aindex + UPDATE_QUEUE, update & ~QUEUE_BIT);
                // ... because the following line may throw exception and we don't want to retry it next time
                if (ticker.getRecordData(agent, sink, key, rid, mark, attachment))
                    nRetrieved++;
            }
            aindex = retrieveNextQueued(agent, aindex, queueOfs, retainSnapshotBit);
        } while (aindex != EOL && nRetrieved < nRetrieveLimit);
        return nRetrieved;
    }

    /**
     * Retrieves history data from its snapshot queue.
     * This method should be only called when snapshot queue is not empty.
     * NOTE: This method may actually retrieve up to 2*nRetrieveLimit.
     *
     *
     * @param sink the sink to retrieve data to.
     * @param nRetrieveLimit the maximal number of records to retrieve.
     * @return number of records retrieved and appended to sink.
     *         {@link #RETRIEVE_NO_CAPACITY_BIT} is set when sink ran out of capacity in process.
     */
    // SYNC: local+global
    int retrieveSnapshotForHistory(History history, Agent agent, RecordSink sink, int nRetrieveLimit) {
        assert agent.collector == history;
        assert this == agent.snapshotQueue;
        int aindex = head;
        assert aindex != EOL; // should be only called when queue is not empty
        SubMatrix asub = agent.sub;
        int nRetrieved = 0;
        do {
            long timeKnown = asub.getLong(aindex + TIME_KNOWN);
            long timeSub = asub.getLong(aindex + TIME_SUB);
            assert timeKnown >= timeSub; // this is the key invariant of a history subscription state
            if (timeKnown == timeSub) {
                // nothing more to retrieve here, get next and continue
                aindex = retrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
                continue;
            }
            HistoryBuffer hb = history.getHB(agent, aindex);
            if (hb == null) {
                // item has in snapshot queue, but there is no data actually (sub was removed ana restored)
                aindex = retrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
                continue;
            }
            // check toTime (and make sure we have to do anything in this retrieve)
            long toTime = Math.max(timeSub, hb.getSnapshotTime()); // limit at sub, snapshot and snip times
            if (timeKnown <= toTime) {
                // nothing to do. This can happen when history buffer was snipped or snapshot restarted
                // while this agent was queued
                aindex = retrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
                continue;
            }
            // get key and rid
            int key = asub.getInt(aindex + KEY);
            int rid = asub.getInt(aindex + RID);
            // Attachment (if needed)
            Object attachment = agent.hasAttachmentStrategy() ? asub.getObj(aindex, ATTACHMENT) : null;
            // Figure out how many records to retrieve in batch
            if ((asub.getInt(aindex + SNAPSHOT_QUEUE) & QUEUE_BIT) != 0) // queue bit is set, it means ...
                agent.nSnapshotHistoryRem = History.SNAPSHOT_BATCH_SIZE; // first take on this record -- full batch
            // Prepare to retrieve
            nRetrieveLimit = Math.min(nRetrieveLimit, agent.nSnapshotHistoryRem);
            int snapshotEndFlag = 0;
            // if retrieving to timeSub, then snapshotEnd flag is set based on whether the sub was snipped
            if (toTime == timeSub)
                snapshotEndFlag = isAgentSubSnip(asub, aindex) ? SNAPSHOT_SNIP : SNAPSHOT_END;
            else if (hb.isSnipToTime(toTime)) // if retrieving to HB snip time
                snapshotEndFlag = SNAPSHOT_SNIP;
            boolean txEnd = false;
            long lastRecordWithBit = asub.getLong(aindex + LAST_RECORD);
            // Collect correct event flags for agents that use history snapshot.
            int eventFlags = 0;
            /*
             * Make agent TX_DIRTY if we are about to retrieve data from HB that has a snapshot and has events
             * that are in process of being added to local buffer (between 1st and 2nd phases of processData).
             */
            if (agent.useHistorySnapshot() && !History.isAgentTxDirty(lastRecordWithBit) && hb.wasEverSnapshotMode() &&
                isAgentSubProcessing(asub, aindex))
            {
                if (Collector.TRACE_LOG)
                    log.trace("makeAgentTxDirty on isAgentSubProcessing in AgentQueue");
                lastRecordWithBit = History.makeAgentTxDirty(agent, asub, aindex, lastRecordWithBit);
            }
            // Process TX_DIRTY agents (implies that agent.useHistorySnapshot() is true)
            boolean noMoreDirtyOnFullRetrieve = false;
            if (History.isAgentTxDirty(lastRecordWithBit)) {
                eventFlags |= TX_PENDING; // TX_DIRTY agent marks all retrieved records as TX_PENDING
                /*
                 * We need to figure out if we need reset TX_PENDING on the last retrieved item or not.
                 * We should do this, if:
                 *   #1. No ongoing snapshot update that holds this agent (snapshotEnd := toTime == timeSub)
                 *   #2. There is no ongoing transaction  AND
                 *   #3. There is no data in process of being added to local buffer (between 1st and 2nd phases of processData) AND
                 *   #4. No data currently in local buffer
                 *
                 * If #1 is not the case (there is an ongoing snapshot update), then its snapshotEnd/snapshotSnip
                 * event will turn off dirty flag.
                 *
                 * If #2 is not the case (there is an ongoing transaction), then the end of this transaction
                 * (TX_END) will be processed to make sure that dirty flag and TX_PENDING are off.
                 *
                 * If #3 is not the case (there is a processData in process and this retrieve is happening between
                 * its 1st and 2nd phases), then the 2nd phase of process data will clear dirty flag.
                 *
                 * If #4 is not the case (there is data in the buffer), then the last event in the local
                 * buffer will have its TX_PENDING bit reset after the end of this global retrieve.
                 */
                noMoreDirtyOnFullRetrieve = snapshotEndFlag != 0 && // #1
                    !hb.isTx() && // #2
                    !History.isAgentSubProcessing(asub, aindex); // #3
                if (noMoreDirtyOnFullRetrieve && // #1-3
                    !agent.buffer.isInBuffer(lastRecordWithBit & ~TX_DIRTY_LAST_RECORD_BIT))  // #4
                {
                    txEnd = true; // end transaction at the end of retrieve
                }
            }
            if (timeKnown == Long.MAX_VALUE)
                eventFlags |= SNAPSHOT_BEGIN; // first retrieved record is marked with SNAPSHOT_BEGIN
            boolean hasMore = true; // has more if crashes while trying to append
            int nExamined;
            try {
                // examineData may abort due to exception in sink.append, so update state in finally
                hasMore = hb.examineDataRetrieve(history.records[rid], history.getCipher(key), history.getSymbol(key),
                    timeKnown, toTime, sink, history.keeper, attachment, agent.nSnapshotHistoryRem,
                    eventFlags, snapshotEndFlag, txEnd, agent.useHistorySnapshot());
            } finally {
                // examine might have crashed (failed to append), so update critical state in "finally"
                nExamined = hb.nExamined;
                if (nExamined > 0) {
                    // examined to snip time is equivalent to examined to sub time
                    // (SNAPSHOT_SNIP event takes the role of SNAPSHOT_END)
                    if (hb.isSnipToTime(hb.examinedTime))
                        hb.examinedTime = timeSub;
                    asub.setLong(aindex + TIME_KNOWN, hb.examinedTime);
                    nRetrieved += nExamined;
                }
                /*
                 * Make agent not dirty anymore if agent was dirty and all the following is true:
                 *
                 *   #1. No ongoing snapshot update that holds this agent (snapshotEnd := toTime == timeSub)
                 *   #2. There is no ongoing transaction that touches timeSub AND
                 *   #3. There is no data in process of being added to local buffer (between 1st and 2nd phases of processData) AND
                 *   #4. Had retrieved everything (no more data)
                 *
                 * If #3 is not the case (there is an ongoing transaction), then the end of this transaction
                 * (TX_END) will be processed to make sure that dirty flag and TX_PENDING are off.
                 */
                if (noMoreDirtyOnFullRetrieve && // #1-3
                    !hasMore) // #4
                {
                    if (Collector.TRACE_LOG)
                        log.trace("makeAgentNonTxDirty in AgentQueue");
                    lastRecordWithBit = makeAgentNonTxDirty(asub, aindex, lastRecordWithBit);
                    // and reset TX_PENDING on the last item in buffer, if we did not do it on the last
                    // item retrieved from the global buffer (that is, if had data in agent buffer)
                    if (!txEnd) {
                        RecordCursor writeCursor = agent.buffer.writeCursorAtPersistentPosition(lastRecordWithBit);
                        writeCursor.setEventFlags(writeCursor.getEventFlags() & ~TX_PENDING);
                    }
                } else {
                    /*
                     * If we had just examined data for agent that uses history snapshot and is not TX_DIRTY, and there
                     * Was an ongoing transaction in HB, then it means, that the agent shall become TX_DIRTY.
                     * Note, that HistoryBuffer.examineDataRetrieve was already marking the corresponding events
                     * with TX_PENDING flag.
                     */
                    if (agent.useHistorySnapshot() && !isAgentTxDirty(lastRecordWithBit)
                        && nExamined > 0 && hb.isTx())
                    {
                        if (Collector.TRACE_LOG)
                            log.trace("makeAgentTxDirty on isTx in AgentQueue");
                        makeAgentTxDirty(agent, asub, aindex, lastRecordWithBit);
                    }
                }
            }
            // Update queue state
            if (hasMore) {
                // More data from this history buffer to retrieve, let us see why it is so...
                agent.nSnapshotHistoryRem -= nExamined; // decrease remaining snapshot batch size for the next call
                if (nExamined < nRetrieveLimit) {
                    // This conditions means sink ran out of capacity.
                    resetQueueBit(agent, aindex, SNAPSHOT_QUEUE); // reset bit to take off next time from where we left
                    return nRetrieved | RETRIEVE_NO_CAPACITY_BIT; // signal no more capacity
                }
                // otherwise -- retrieve limit has been reached, let's see if this item has completed its snapshot batch
                if (agent.nSnapshotHistoryRem > 0) {
                    // Not yet! The remainder of the snapshot batch allowance is still positive
                    resetQueueBit(agent, aindex, SNAPSHOT_QUEUE); // reset bit to take off next time from where we left
                    return nRetrieved; // .. and will continue working on this item in the next call
                }
                // full snapshot batch for this item was retrieved. Put it to tail and go to next one.
                aindex = moveToTailAndRetrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
            } else {
                // done with this item (retrieved all from its HistoryBuffer). Go to next one.
                aindex = retrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
            }
        } while (aindex != EOL && nRetrieved < nRetrieveLimit);
        return nRetrieved;
    }

    /**
     * Removes absent items from the head of the ticker queues (based on UPDATE_QUEUE bit for presence).
     * @param queueOfs    SNAPSHOT_QUEUE when working with snapshotQueue,
     *                    UPDATE_QUEUE when working with updateQueue.
     */
    // SYNC: local
    void cleanupEmptyHeadForTicker(Agent agent, int aindex, int queueOfs) {
        assert agent.collector.getContract() == QDContract.TICKER;
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        if (aindex != head)
            return; // not head
        SubMatrix asub = agent.sub;
        do {
            // QUEUE_BIT at presenceOfs indicates whether item has data
            if ((asub.getInt(aindex + UPDATE_QUEUE) & QUEUE_BIT) != 0)
                break; // not empty (has data)
            aindex = retrieveNextQueued(agent, aindex, queueOfs);
        } while (aindex != EOL);
    }

    /**
     * Removes absent items from the head of History snapshot queue (based time_known > time_sub for presence).
     */
    // SYNC: local
    void cleanupEmptySnapshotHeadForHistory(Agent agent, SubMatrix asub, int aindex) {
        assert agent.collector.getContract() == QDContract.HISTORY;
        assert this == agent.snapshotQueue;
        if (aindex != head)
            return; // not head
        do {
            // timeKnown and timeSub relation indicates whether item has snapshot data
            // However, for legacy agents, when "useHistorySnapshot" is false,
            // timeKnown indicates that time of last retrieved item and generally
            // stays above timeSub as long as subscription exists
            // (on unsubscribed item timeKnown == timeSub == Long.MAX_VALUE)
            // In general case, retrieveSnapshotForHistory invokes retrieveNextQueued when there
            // is no more data in HistoryBuffer, thus removing item from the queue even when
            // it is still true that timeKnown > timeSub
            long timeKnown = asub.getLong(aindex + TIME_KNOWN);
            long timeSub = asub.getLong(aindex + TIME_SUB);
            assert timeKnown >= timeSub; // this is the key invariant of a history subscription state
            if (timeKnown > timeSub)
                break; // not empty (has more snapshot data to send)
            // timeKnown == timeSub -- nothing more to send here
            aindex = retrieveNextQueued(agent, aindex, SNAPSHOT_QUEUE);
        } while (aindex != EOL);
    }

    // SYNC: local
    private int retrieveNextQueued(Agent agent, int aindex, int queueOfs) {
        return retrieveNextQueued(agent, aindex, queueOfs, false);
    }

    // SYNC: local
    private int retrieveNextQueued(Agent agent, int aindex, int queueOfs, boolean retainQueueBit) {
        SubMatrix asub = agent.sub;
        int state = asub.getInt(aindex + queueOfs);
        int next = state & ~QUEUE_BIT;
        asub.setInt(aindex + queueOfs, retainQueueBit ? state & QUEUE_BIT : 0);
        head = next;
        if (next == EOL)
            tail = EOL;
        return next;
    }

    /**
     * Moves this agent's index (which must be head) to the tail of the queue.
     * QUEUE_BIT is always set when this method returns.
     * @param queueOfs SNAPSHOT_QUEUE when working with snapshotQueue,
     *                 UPDATE_QUEUE when working with updateQueue.
     */
    // SYNC: local
    private int moveToTailAndRetrieveNextQueued(Agent agent, int aindex, int queueOfs) {
        assert aindex == head;
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        SubMatrix asub = agent.sub;
        int val = asub.getInt(aindex + queueOfs);
        int next = val & ~QUEUE_BIT;
        if (next == EOL) {
            // It is already last item. Just raise its QUEUE_BIT
            asub.setInt(aindex + queueOfs, val | QUEUE_BIT);
            return aindex;
        }
        int tail = this.tail;
        asub.setInt(tail + queueOfs, aindex | (asub.getInt(tail + queueOfs) & QUEUE_BIT));
        asub.setInt(aindex + queueOfs, QUEUE_BIT | EOL); // at the tail now with QUEUE_BIT set
        this.tail = aindex;
        this.head = next;
        return next;
    }

    // SYNC: local
    int getHead() {
        return head;
    }

    /**
     * Used in rehashAgent for Ticker & History to fix queue after rehash.
     * This method rebuilds list while retaining QUEUE_BIT in each item.
     * @param queueOfs SNAPSHOT_QUEUE when working with snapshotQueue,
     *                 UPDATE_QUEUE when working with updateQueue.
     */
    // SYNC: global+local
    void fixQueue(Agent agent, int oindex, SubMatrix osub, int queueOfs) {
        assert (this == agent.snapshotQueue && queueOfs == SNAPSHOT_QUEUE) ||
               (this == agent.updateQueue && queueOfs == UPDATE_QUEUE);
        if (oindex == EOL)
            return;
        SubMatrix asub = agent.sub;
        int lindex = 0;
        do {
            int aindex = asub.getIndex(osub.getInt(oindex + KEY), osub.getInt(oindex + RID), 0);
            if (aindex > 0) {
                if (lindex == 0)
                    head = aindex;
                else
                    asub.setInt(lindex + queueOfs, aindex | (asub.getInt(lindex + queueOfs) & QUEUE_BIT));
                lindex = aindex;
            }
            oindex = osub.getInt(oindex + queueOfs) & ~QUEUE_BIT;
        } while (oindex != EOL);
        if (lindex == 0) {
            tail = EOL;
            head = EOL;
        } else {
            tail = lindex;
            agent.sub.setInt(lindex + queueOfs, EOL | (asub.getInt(lindex + queueOfs) & QUEUE_BIT));
        }
    }
}

