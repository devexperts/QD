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
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.LegacyAdapter;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import static com.devexperts.qd.impl.matrix.Distribution.DEC_PENDING_COUNT_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.HAD_SNAPSHOT_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.SEND_SNAPSHOT_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.TX_END_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.TX_SWEEP_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.UPDATED_RECORD_DIST_FLAG;
import static com.devexperts.qd.impl.matrix.Distribution.UPDATED_SNIP_DIST_FLAG;

/**
 * The <code>History</code> is a matrix-based implementation of {@link QDHistory}.
 * This class is public for the purpose of testing only.
 */
public class History extends Collector implements QDHistory {
    static final int RETRIEVE_BATCH_SIZE = SystemProperties.getIntProperty(
        History.class, "retrieveBatchSize", 100, 1, Integer.MAX_VALUE);
    static final int SNAPSHOT_BATCH_SIZE = SystemProperties.getIntProperty(
        History.class, "snapshotBatchSize", 10000, 1, Integer.MAX_VALUE);
    static final long STATE_KEEP_TIME = TimePeriod.valueOf(SystemProperties.getProperty(
        History.class, "stateKeepTime", "60s")).getTime();

    //FIXME Abstraction leak here: History should not know about dxscheme and unconflated Order.
    // Flag specifying "conflated" or "unconflated" mode for History (dxscheme.fob=false or true respectively).
    static final boolean FOB_FLAG = SystemProperties.getBooleanProperty("dxscheme.fob", false);
    // By default, all records except Order are conflated.
    static final String CONFLATE_FILTER = SystemProperties.getProperty(History.class, "conflateFilter",
        FOB_FLAG ? "!(:Order*,:OptionSale)" : "!:OptionSale");

    /*
     * Event flags.
     */

    static final int TX_PENDING = EventFlag.TX_PENDING.flag();
    static final int REMOVE_EVENT = EventFlag.REMOVE_EVENT.flag();
    static final int SNAPSHOT_BEGIN = EventFlag.SNAPSHOT_BEGIN.flag();
    static final int SNAPSHOT_END = EventFlag.SNAPSHOT_END.flag();
    static final int SNAPSHOT_SNIP = EventFlag.SNAPSHOT_SNIP.flag();
    static final int SNAPSHOT_MODE = EventFlag.SNAPSHOT_MODE.flag();

    // NEXT_AGENT value to support storeEverything in total sub
    static final int NO_NEXT_AGENT_BUT_STORE_HB = -1;

    // -----------------------------------------------------------------------------------------------------------

    /* -----------------------------------------------------------------------------------------------------
     * HISTORY_SUB_FLAGS values.
     *
     * History sub flags has the following bit fields:
     *   SNIP_TIME_SUB_FLAG (1 most sign. bit), PENDING_COUNT (13 bits), and PROCESS_VERSION (18 least sign. bits).
     *
     * HISTORY_SUB_FLAGS bits layout:
     *   31   30   ...   18   17    ...     0
     * +----+---------------+-----------------+
     * | SS | PENDING_COUNT | PROCESS_VERSION |
     * +----+---------------+-----------------+
     *
     * Their modification policy:
     *  - SNIP_TIME_SUB_FLAG is modified only under local+global locks.
     *
     *  - process version is set to non-zero under global lock in processRecordSourceGLocked and pending count
     *    is incremented to count the number of data records to be processes in the 2nd phase.
     *
     *  - pending count is decremented and process version is set to zero under local lock in processAgentDataUpdate.
     *
     *   /----------------------------- processData ------------------------------\
     *     [ processRecordSourceGLocked ]             [ processAgentDataUpdate ]
     *              \----------------------------------------/
     *                     here processVersion != 0
     *
     *   This way, if another processData jumps in between processData phases, it is going to discover
     *   that process version is non zero and wait until the second phase of the first processData finishes,
     *   thus guaranteeing that processData invocations logically follow each other and cannot be accidentally nested.
     *
     *   If retrieveData jumps in between processData phases, it is going to discover that there is a different non-zero
     *   version and figure out that the data item is still going to be processed in the 2nd phase, avoiding the loss
     *   of TX_PENDING flag (see HistoryTxTest.testLostTxPending1)
     *
     *   If processData crashes, then finally section removes the current version from the set of in process versions
     *   (see Distribution.done). The non-zero values for process version and pending count in HISTORY_SUB_FLAGS remain,
     *   but the next time when this value is discovered in HISTORY_SUB_FLAGS value, the invocation to
     *   ProcessVersionTracker.waitWhileInProcess will find out that the corresponding version is no longer actually
     *   in process, and will replace it with the current process version.
     *
     *   Unsubscribe/subscribe (if that happens in between processData phases) clears processes version
     *   (which may be replaced with a different one) and causes all events pending to be processed ignored.
     *
     * -----------------------------------------------------------------------------------------------------
     */

    // number of bits of process version
    static final int PROCESS_VERSION_BITS = 18; // up to ~262K versions, then versions roll
    static final int PENDING_COUNT_BITS = 31 - PROCESS_VERSION_BITS; // the rest, but the last bit = up to 8192 records

    // flags for HISTORY_SUB_FLAGS
    static final int SNIP_TIME_SUB_FLAG = 1 << 31; // most significant bit
    static final int PENDING_COUNT_MASK = ((1 << PENDING_COUNT_BITS) - 1) << PROCESS_VERSION_BITS; // middle bits
    static final int PENDING_COUNT_INC = (1 << PROCESS_VERSION_BITS);
    static final int PROCESS_VERSION_MASK = ((1 << PROCESS_VERSION_BITS) - 1); // least significant bits

    // -----------------------------------------------------------------------------------------------------------

    /*
     * TX_DIRTY agent invariant:
     *   Conceptually, agent is TX_DIRTY when there was transaction that spans both global & local buffers or the
     *   data was retrieved from an otherwise inconsistent HB.
     *
     *   It stays dirty until it can be assured that the agent has snapshot-consistent view of data.
     *
     *   Agent becomes TX_DIRTY in one of the following cases:
     *     1) At any time during an active TX_PENDING transaction in HB it becomes true during retrieve from HB
     *        even if this global retrieve was complete.
     *     2) Whenever an event that is a part of transaction is queued to the local buffer
     *        and agent's knownTime > subTime, e.g. there is a potential that parts of transaction may
     *        end up in the global buffer.
     *     3) Whenever an event that is a part of an implicit SnapshotUpdate transaction is queued to the local buffer,
     *        even if knowTime == subTime.
     *
     *   When agent becomes TX_DIRTY, all its subsequent retrieve from HB produce TX_PENDING records
     *   and all records in local buffer are flagged with TX_PENDING, too, so that regardless of which retrieve
     *   is invoked, only TX_PENDING records are retrieved.
     *
     *   Agent becomes non dirty when ALL of the following is true
     *   (NOTE: global+local locks are needed to check that all of those conditions at the same time)
     *     1) It had retrieved everything from History Buffer (timeKnown <= timeSub).
     *     2) It had retrieved everything from agent buffer.
     *     3) There are no pending records to process in the 2nd phase of processData.
     *     4) HB is not in explicit or implicit transaction up to the timeSub (!HistoryBuffer.isTx())
     */

    // LAST_RECORD bit to indicate agent in "Dirty Transaction" (that spans both global & local buffers)
    static final long TX_DIRTY_LAST_RECORD_BIT = 1L << 63; // highest bit

    // time used for "virtual events" that should not have been processed but are need to remove TX_PENDING flag
    static final long VIRTUAL_TIME = Long.MAX_VALUE;

    //======================================= instance fields =======================================

    private final HistorySubscriptionFilter historyFilter;

    // Filter specifying records to conflate in "unconflated" mode
    private final QDFilter conflateFilter;

    private ProcessVersionTracker processVersion = new ProcessVersionTracker();

    //======================================= constructor =======================================

    protected History(Builder<?> builder) {
        this(builder, null);
    }

    protected History(Builder<?> builder, RecordOnlyFilter conflateFilter) {
        super(builder, true, true, true);
        HistorySubscriptionFilter historyFilter = builder.getHistoryFilter();
        // If history filter is not specified in builder get it as service.
        if (historyFilter == null)
            historyFilter = builder.getScheme().getService(HistorySubscriptionFilter.class);
        this.historyFilter = historyFilter;
        if (conflateFilter == null)
            conflateFilter = RecordOnlyFilter.valueOf(CONFLATE_FILTER, builder.getScheme());
        this.conflateFilter = conflateFilter;
    }

    //======================================= methods =======================================

    @Override
    Agent createAgentInternal(int number, QDAgent.Builder builder, QDStats stats) {
        Agent agent = new Agent(this, number, builder, stats);
        agent.sub = new SubMatrix(mapper, HISTORY_AGENT_STEP,
            builder.getAttachmentStrategy() == null ? AGENT_OBJ_STEP : AGENT_ATTACHMENT_OBJ_STEP,
            PREV_AGENT, 0, 0, Hashing.MAX_SHIFT, stats.create(QDStats.SType.AGENT_SUB));
        return agent;
    }

    @Override
    RecordMode getAgentBufferMode(Agent agent) {
        RecordMode mode = agent.getMode().withLink();
        if (hasEventTimeSequence())
            mode = mode.withEventTimeSequence();
        return mode;
    }

    @Override
    protected long trimSubTime(RecordCursor cur) {
        long time = cur.getTime();
        return historyFilter == null ? time :
            Math.max(time, historyFilter.getMinHistoryTime(cur.getRecord(), cur.getCipher(), cur.getSymbol()));
    }

    @Override
    protected boolean isSubAllowed(Agent agent, DataRecord record, int cipher, String symbol) {
        if (!record.hasTime())
            throw new IllegalArgumentException("Record does not contain time");
        return super.isSubAllowed(agent, record, cipher, symbol);
    }

    // SYNC: global+local
    private HistoryBuffer getHB(int key, int rid) {
        int tindex = total.sub.getIndex(key, rid, 0);
        if (tindex == 0)
            throw new IllegalStateException("Total entry missed");
        return (HistoryBuffer) total.sub.getObj(tindex, HISTORY_BUFFER);
    }

    // SYNC: global+local
    HistoryBuffer getHB(Agent agent, int aindex) {
        return getHB(agent.sub.getInt(aindex + KEY), agent.sub.getInt(aindex + RID));
    }

    // SYNC: global+local
    @Override
    void prepareTotalSubForRehash() {
        if (STATE_KEEP_TIME <= 0)
            return;
        /*
         * We might have some expired HistoryBuffer held to keep their state. Here is the time and place
         * to actually check and remove them. We do so by full scan and proper release and unmark as payload.
         * As a result they will not be rehashed by ongoing rehash. Might produce matrix with capacity larger
         * than expected - let it be.
         */
        long currentTime = System.currentTimeMillis();
        SubMatrix tsub = total.sub;
        for (int tindex = tsub.matrix.length; (tindex -= tsub.step) >= 0; ) {
            // save time by looking only into entries which might contain expired buffer
            if (tsub.getInt(tindex + NEXT_AGENT) != NO_NEXT_AGENT_BUT_STORE_HB)
                continue;
            HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
            if (hb == null || hb.expirationTime > currentTime)
                continue;
            // the buffer is here and it is expired but we might need it because of storeEverything
            int key = tsub.getInt(tindex + KEY);
            int rid = tsub.getInt(tindex + RID);
            if (shouldStoreEverything(key, rid)) {
                // it's storeEverything (but was not before) - mark it accordingly to not check again
                hb.expirationTime = Long.MAX_VALUE;
                continue;
            }
            // remove buffer with all due bookkeeping and state change
            removeHistoryBufferAt(rid, tsub, tindex);
            tsub.setInt(tindex + NEXT_AGENT, 0);
            tsub.updateRemovedPayload(rid);
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    @Override
    void totalRecordAdded(int key, int rid, SubMatrix tsub, int tindex, long time) {
        if (TRACE_LOG)
            log.trace("totalRecordAdded time=" + time);
        super.totalRecordAdded(key, rid, tsub, tindex, time);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb != null && !shouldStoreEverything(key, rid)) {
            hb.expirationTime = Long.MAX_VALUE;
            // of any change in sub time - remove old records
            hb.removeOldRecords(time, statsStorage, rid);
            /*
             * Mark HB as no longer having a complete snapshot if subscribing to time below previous snapshot and
             * the snapshot was not snipped at previous snapshot time
             * (must wait until next consistent snapshot is received from upstream data source).
             */
            if (time < hb.getSnapshotTime() && !hb.isSnipToTime(hb.getSnapshotTime()))
                hb.resetSnapshot();
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    @Override
    boolean totalRecordRemoved(int key, int rid, SubMatrix tsub, int tindex) {
        if (TRACE_LOG)
            log.trace("totalRecordRemoved");
        super.totalRecordRemoved(key, rid, tsub, tindex);
        if (shouldStoreEverything(key, rid)) {
            // force total sub item to be kept as payload
            tsub.setInt(tindex + NEXT_AGENT, NO_NEXT_AGENT_BUT_STORE_HB);
            return false;
        }
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb != null) {
            if (STATE_KEEP_TIME > 0) {
                // remove all data but keep state info for some time in case subscription is created anew
                // also update snapshot times as if subscription changed to Long.MAX_VALUE (empty sub range)
                hb.clearAllRecords(statsStorage, rid);
                hb.expirationTime = System.currentTimeMillis() + STATE_KEEP_TIME;
                // force total sub item to be kept as payload
                tsub.setInt(tindex + NEXT_AGENT, NO_NEXT_AGENT_BUT_STORE_HB);
                return false;
            }
        }
        removeHistoryBufferAt(rid, tsub, tindex);
        return true;
    }

    private void removeHistoryBufferAt(int rid, SubMatrix tsub, int tindex) {
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb != null) {
            statsStorage.updateRemoved(rid, hb.size());
            tsub.setObj(tindex, HISTORY_BUFFER, null); // Let GC clean up HistoryBuffer
        }
    }

    // SYNC: global+local
    @Override
    void enqueueAddedRecord(Agent agent, SubMatrix asub, int aindex) {
        if (TRACE_LOG)
            log.trace("enqueueAddedRecord time_sub=" + asub.getLong(aindex + TIME_SUB) + " for " + agent);
        // Resend all the data from scratch on repeated addSubscription or updates via setSubscription (like ticker)
        unlinkFromAgentBufferAndClearTxDirty(agent, asub, aindex);
        // handle timeSub / timeKnown
        long timeSub = asub.getLong(aindex + TIME_SUB);
        if (agent.hasVoidRecordListener()) {
            // reset time known to time sub, because we consider this data to be consumed by VOID listener
            asub.setLong(aindex + TIME_KNOWN, timeSub);
            return;
        }
        // Set time known to MAX value -- we're going to start sending everything from scratch
        asub.setLong(aindex + TIME_KNOWN, Long.MAX_VALUE);
        HistoryBuffer hb = getHB(agent, aindex);
        if (hb == null)
            return; // nothing here yet
        // Only records above snapshot time are visible to the agents
        int nAvailable = hb.getAvailableCount(Math.max(timeSub, hb.getSnapshotTime()), Long.MAX_VALUE);
        // When no records are available, then agent that uses history snapshot needs to generate
        // virtual "end snapshot" event when HB snapshot time is already below its sub time.
        if (nAvailable > 0 || agent.useHistorySnapshot() && hb.getSnapshotTime() <= timeSub) {
            // link to snapshot queue
            if (agent.snapshotQueue.linkToQueue(agent, aindex, SNAPSHOT_QUEUE, false)) {
                // added first entry -- notify listeners when done
                subNotifyAccumulator |= NOTIFY_SUB_SNAPSHOT_AVAILABLE;
                // if there were not data queued, then it is also new data notification
                if (!agent.buffer.hasNext())
                    subNotifyAccumulator |= NOTIFY_SUB_DATA_AVAILABLE;
            }
        }
    }

    // SYNC: global+local
    @Override
    void dequeueRemovedRecord(Agent agent, SubMatrix asub, int aindex) {
        if (TRACE_LOG)
            log.trace("dequeueRemovedRecord for " + agent);
        // Drop everything queued in agent buffer
        unlinkFromAgentBufferAndClearTxDirty(agent, asub, aindex);
        // Remove from snapshot queue (will work, because TIME_SUB was already set to Long.MAX_VALUE)
        agent.snapshotQueue.cleanupEmptySnapshotHeadForHistory(agent, asub, aindex);
    }

    // SYNC: local
    private void unlinkFromAgentBufferAndClearTxDirty(Agent agent, SubMatrix asub, int aindex) {
        long lastRecordWithBit = asub.getLong(aindex + LAST_RECORD);
        long position = lastRecordWithBit & ~TX_DIRTY_LAST_RECORD_BIT;
        if (agent.buffer.isInBuffer(position))
            agent.buffer.unlinkFromPersistentPosition(position);
        // there's nothing more in buffer and agent is not TX_DIRTY anymore
        asub.setLong(aindex + LAST_RECORD, 0);
    }

    // SYNC: local
    private void rebaseIfNeeded(Agent agent) {
        if (agent.buffer.needsRebase())
            rebuildLastRecordAndRebase(agent);
    }

    // SYNC: local
    void rebuildLastRecordAndRebase(Agent agent) {
        if (TRACE_LOG)
            log.trace("rebuildLastRecordAndRebase for " + agent);
        // Compact buffer first (!!!)
        agent.buffer.compact();
        // clear LAST_RECORD in all sub-d items -- set it to 0, but keep TX_DIRTY bit
        SubMatrix asub = agent.sub;
        for (int aindex = 0; aindex < asub.matrix.length; aindex += asub.step) {
            if (asub.isPayload(aindex)) {
                long lastRecordWithBit = asub.getLong(aindex + LAST_RECORD);
                asub.setLong(aindex + LAST_RECORD, lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT);
            }
        }
        // scan buffer and set correct LAST_RECORD
        RecordCursor cursor;
        while ((cursor = agent.buffer.next()) != null) {
            if (cursor.isUnlinked())
                continue;
            int key = getKey(cursor.getCipher(), cursor.getSymbol());
            int rid = getRid(cursor.getRecord());
            int aindex = asub.getIndex(key, rid, 0);
            if (!asub.isPayload(aindex))
                continue;
            // Remember cursor.getPosition() + AgentBuffer.BASE in LAST_RECORD, because we will call "rebasePosition"
            // and thus agent.buffer.getPositionBase() that is used as an offset will become equal to BASE.
            long lastRecordWithBit = asub.getLong(aindex + LAST_RECORD);
            asub.setLong(aindex + LAST_RECORD, (cursor.getPosition() + AgentBuffer.BASE) |
                (lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT));
        }
        agent.buffer.rewindAndRebasePosition();
    }

    // SYNC: local
    @Override
    int getNotificationBits(Agent agent) {
        // Snapshot queue is retrieved by any retrieve
        if (!agent.snapshotQueue.isEmpty()) {
            return Notification.SNAPSHOT_BIT | Notification.UPDATE_BIT;
        } else {
            // Buffer contains only updates
            return agent.buffer.hasNext() ? Notification.UPDATE_BIT : 0;
        }
    }

    private static final int RETRIEVE_NOTHING_ELSE = 0;
    private static final int RETRIEVE_SNAPSHOT = 1;
    private static final int RETRIEVE_UPDATE = 2;
    private static final int RETRIEVE_NO_CAPACITY = 3;

    // returns true if some records still remains in the agent, false if all accumulated records were retrieved.
    @Override
    boolean retrieveDataImpl(Agent agent, RecordSink sink, boolean snapshotOnly) {
        if (agent.isClosed())
            return false;
        // Always start retrieve under local lock and escalate to global lock only when really needed
        int retrieveStatus;
        agent.localLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            retrieveStatus = retrieveDataUpdateLLocked(agent, sink, snapshotOnly);
        } finally {
            agent.localLock.unlock();
        }
        // Check if the status indicates that retrieve is over
        switch (retrieveStatus) {
            case RETRIEVE_NOTHING_ELSE:
                return false;
            case RETRIEVE_NO_CAPACITY:
                return true;
        }
        assert retrieveStatus == RETRIEVE_SNAPSHOT;
        // Take global lock and retrieve under it (use global lock with priority)
        globalLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            return retrieveDataGLocked(agent, sink, snapshotOnly);
        } finally {
            globalLock.unlock();
        }
    }

    /**
     * Retrieve data under local lock only.
     * Returns RETRIEVE_NO_CAPACITY when sink has no capacity or the result of checkRetrieveStatus.
     * This method never returns RETRIEVE_UPDATE.
     */
    // SYNC: local
    int retrieveDataUpdateLLocked(Agent agent, RecordSink sink, boolean snapshotOnly) {
        if (agent.isClosed())
            return RETRIEVE_NOTHING_ELSE;
        int result;
        while ((result = checkRetrieveStatus(agent, snapshotOnly)) == RETRIEVE_UPDATE) {
            if (retrieveUpdateBatchFromLocalAgentBuffer(agent, sink)) {
                result = RETRIEVE_NO_CAPACITY;
                break;
            }
        }
        if (result != RETRIEVE_SNAPSHOT)
            countRetrieval(agent); // unless we need global locks, this is our chance to count this retrieval
        return result;
    }

    // returns true if some records still remains in the agent, false if all accumulated records were retrieved.
    private boolean retrieveDataGLocked(Agent agent, RecordSink sink, boolean snapshotOnly) {
        if (agent.isClosed())
            return false;
        agent.localLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            return retrieveDataGLLocked(agent, sink, snapshotOnly);
        } finally {
            agent.localLock.unlock();
        }
    }

    // returns true if some records still remains in the agent, false if all accumulated records were retrieved.
    private boolean retrieveDataGLLocked(Agent agent, RecordSink sink, boolean snapshotOnly) {
        // Retrieve from global and local buffers while sink has capacity
        int result;
    main_loop:
        while (true) {
            result = checkRetrieveStatus(agent, snapshotOnly);
            switch (result) {
                case RETRIEVE_NOTHING_ELSE:
                    break main_loop; // that's it -- no more snapshots and updates
                case RETRIEVE_SNAPSHOT:
                    if (retrieveSnapshotBatchFromGlobalHistoryBuffer(agent, sink)) {
                        result = RETRIEVE_NO_CAPACITY; // no more capacity
                        break main_loop;
                    }
                    break; // continue main loop
                case RETRIEVE_UPDATE:
                    if (retrieveUpdateBatchFromLocalAgentBuffer(agent, sink)) {
                        result = RETRIEVE_NO_CAPACITY; // no more capacity
                        break main_loop;
                    }
                    break; // continue main loop
            }
        }
        countRetrieval(agent); // must count retrieval before releasing locks
        return result == RETRIEVE_NO_CAPACITY; // true when ran out of capacity
    }

    /**
     * Checks agent variable and returns:
     * <pre>
     *    RETRIEVE_SNAPSHOT      when it is turn to retrieve item from the head of snapshotQueue,
     *    RETRIEVE_UPDATE        when it is turn to retrieve updates from non-empty local buffer,
     *    RETRIEVE_NOTHING_ELSE  when queue is empty or agent is closed.
     * </pre>
     * This method performs fair balancing between global/local retrieval to avoid starvation.
     * <p>
     * This method can be overridden in test code to force RETRIEVE_SNAPSHOT or RETRIEVE_UPDATE.
     */
    // SYNC: local
    protected int checkRetrieveStatus(Agent agent, boolean snapshotOnly) {
        if (agent.isClosed())
            return RETRIEVE_NOTHING_ELSE;
        boolean moreSnapshot = !agent.snapshotQueue.isEmpty();
        boolean moreUpdate = !snapshotOnly && agent.buffer.hasNext();
        if (!moreUpdate) {
            // give balance to snapshot if there's nothing to retrieve from update anyway
            agent.nSnapshotRetrieved = 0;
            if (!moreSnapshot)
                return RETRIEVE_NOTHING_ELSE; // and leave it balanced to snapshot next time anything appears
        }
        if (agent.nSnapshotRetrieved < RETRIEVE_BATCH_SIZE && moreSnapshot &&
            (!shallForceRetrieveUpdate() || !moreUpdate))
        {
            // retrieve more from snapshot
            return RETRIEVE_SNAPSHOT;
        }
        // give balance to update if there's nothing to retrieve from snapshot anyway or forced (for test purpuses)
        if (!moreSnapshot || shallForceRetrieveUpdate())
            agent.nSnapshotRetrieved = RETRIEVE_BATCH_SIZE;
        assert agent.nSnapshotRetrieved > 0 && moreUpdate;
        return RETRIEVE_UPDATE; // Now retrieve updates from queue
    }

    /**
     * Retrieves from local buffer.
     * It is invoked when checkRetrieveStatus returned RETRIEVE_UPDATE.
     *
     * @return true when there's more data available but sink has ran out of capacity.
     */
    // SYNC: local
    private boolean retrieveUpdateBatchFromLocalAgentBuffer(Agent agent, RecordSink sink) {
        assert agent.nSnapshotRetrieved > 0; // ensured by checkRetrieveStatus == RETRIEVE_UPDATE
        int nRetrieved = agent.buffer.retrieveData(sink, agent.nSnapshotRetrieved);
        boolean retrievedLess = nRetrieved < agent.nSnapshotRetrieved;
        agent.nSnapshotRetrieved -= nRetrieved; // decrease balance
        agent.nRetrieved += nRetrieved; // for stats
        rebaseIfNeeded(agent);
        if (agent.buffer.unblock())
            agent.localLock.signalAll();
        return agent.buffer.hasNext() && retrievedLess;
    }

    /**
     * Retrieves from global queued item at the top of the queue.
     * It is invoked when checkRetrieveStatus returned RETRIEVE_SNAPSHOT.
     *
     * @return true when there's more data available but sink has ran out of capacity.
     */
    // SYNC: global+local
    private boolean retrieveSnapshotBatchFromGlobalHistoryBuffer(Agent agent, RecordSink sink) {
        assert agent.nSnapshotRetrieved < RETRIEVE_BATCH_SIZE; // ensured by checkRetrieveStatus == RETRIEVE_SNAPSHOT
        // Note that we still request full batch of retrieval (even when already nSnapshotRetrieved > 0)
        int nRetrievedWithBit = agent.snapshotQueue.retrieveSnapshotForHistory(this, agent, sink, RETRIEVE_BATCH_SIZE);
        int nRetrieved = nRetrievedWithBit & ~AgentQueue.RETRIEVE_NO_CAPACITY_BIT;
        agent.nSnapshotRetrieved += nRetrieved; // increase balance
        agent.nRetrieved += nRetrieved; // for stats
        return (nRetrievedWithBit & AgentQueue.RETRIEVE_NO_CAPACITY_BIT) != 0;
    }

    /* -----------------------------------------------------------------------------------------------------
     * History times and locks
     *
     *   Time                Stored in      Guarded by
     *   ------------        ---------      ----------------------
     *   timeSub             SubMatrix      global+local
     *   timeKnown           SubMatrix      local
     *   *snapshotTime       HistoryBuffer  global
     *
     * Invariants:
     *   timeKnown    >= timeSub // guarded by local lock
     *   snapshotTime >= everSnapshotTime >= snipSnapshotTime // guarded by global lock
     *
     * -----------------------------------------------------------------------------------------------------
     */

    // DISTRIBUTION Phase 1: process input, build distribution.
    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: global
    @Override
    boolean processRecordSourceGLocked(Distributor distributor, Distribution dist, RecordSource source) {
        // Under global lock assign a "processVersion" to this operation
        dist.trackProcessVersion(processVersion);
        // Capture commonly used variables into locals
        AgentIterator ait = dist.getAgentIterator();
        RecordBuffer removeBuffer = dist.getRemoveBuffer();
        SubMatrix tsub = total.sub;
        /*
         * Process each incoming event, figure out which agents they are interesting for, and
         * remember this information in Distribution data structure for the next phase.
         */
        RecordCursor cursor;
        while ((cursor = source.next()) != null) {
            DataRecord record = cursor.getRecord();

            int rid = getRid(record);
            dist.countIncomingRecord(rid);
            if (!record.hasTime())
                continue;

            int cipher = cursor.getCipher();
            String symbol = cursor.getSymbol();
            if (storeEverything && !distributor.filter.accept(contract, record, cipher, symbol))
                continue;

            int key = getKey(cipher, symbol);
            int tindex = tsub.getIndex(key, rid, 0);
            // storeEverything support
            boolean shouldStoreEverything = shouldStoreEverything(record, cipher, symbol);
            if (shouldStoreEverything) {
                if (key == 0)
                    key = mapper.addKey(symbol);
                if (tindex == 0) {
                    // no entry in total sub yet -- create a dummy one
                    rehashAgentIfNeeded(total); // rehash before adding, if needed
                    tsub = total.sub;
                    tindex = createDummyTotalSubEntry(tsub, rid, key);
                }
            } else if (tsub.getInt(tindex + NEXT_AGENT) <= 0)
                continue; // No subscription -- ignore incoming event (unless storing everything)
            /*
             * time == Long.MAX_VALUE is not supported, because it is used as initial value for KNOWN_TIME.
             * For potential future backwards compatibility, REMOVE_TIME on time == Long.MAX_VALUE is
             * supported (TX can begin/end with this event).
             */
            long time = cursor.getTime();
            int eventFlags = cursor.getEventFlags();
            if (time == VIRTUAL_TIME && (eventFlags & REMOVE_EVENT) == 0)
                throw new IllegalArgumentException("History does not support actual records with time == Long.MAX_VALUE");
            /*
             * Get or create history buffer.
             */
            HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
            if (hb == null) { // no history buffer for this symbol+record yet -- create one
                hb = new HistoryBuffer(record, hasEventTimeSequence());
                tsub.setObj(tindex, HISTORY_BUFFER, hb);
            }

            long timeTotal = tsub.getLong(tindex + TIME_TOTAL);

            if (TRACE_LOG)
                log.trace("processRecordSourceGLocked " + historyCursorString(cursor) +
                    " timeTotal=" + timeTotal +
                    " state before=" + hb);

            int distFlags = 0;
            /*
             * (0) Turn on snapshot mode when we see SNAPSHOT_BEGIN/MODE flag (per QD-895).
             */
            if ((eventFlags & (SNAPSHOT_BEGIN | SNAPSHOT_MODE)) != 0) {
                if (hb.enterSnapshotModeFirstTime()) {
                    // mark event that turned on snapshot mode (it will have to be processed by agents)
                    distFlags |= SEND_SNAPSHOT_DIST_FLAG;
                } else if (!conflateFilter.accept(cursor)) {
                    // We need to transparently retransmit the snapshot for unconflated events.
                    hb.enterSnapshotModeForUnconflated();
                    // mark event that triggered snapshot retransmission (it will have to be processed by agents)
                    distFlags |= SEND_SNAPSHOT_DIST_FLAG;
                }
            }
            /*
             * (1) Process transaction TX_PENDING logic. This logic is invoked even if subscription has changed and this
             * event is outside snapshot time, because the transaction may span across the border and TX_END
             * must be delivered regardless.
             */
            if (hb.updateExplicitTx((eventFlags & TX_PENDING) != 0)) {
                // mark event that ends transaction (it will have to be processed by agents)
                distFlags |= TX_END_DIST_FLAG;
            }
            /*
             * (2) Process SNAPSHOT_BEGIN logic.
             */
            if ((eventFlags & SNAPSHOT_BEGIN) != 0) {
                hb.snapshotBegin();
            }
            /*
             * (3) Process SNAPSHOT_SNIP logic.
             */
            long snipRemovePosition = removeBuffer.getLimit();
            if ((eventFlags & SNAPSHOT_SNIP) != 0 &&
                hb.snapshotSnipAndRemove(time, record, cipher, symbol, removeBuffer, statsStorage, rid))
            {
                // mark event that updates snip time (it will have to be processed by agents)
                distFlags |= UPDATED_SNIP_DIST_FLAG;
            }
            /*
             * Ignore all events that are being received below everSnapshotTime while waiting for snapshot to begin.
             * This prevents HB from entering inconsistent state (with holes in data) when subscription was changed and
             * events below everSnapshotTime were consequently lost from HB. Also, don't process any events below timeTotal,
             * unless storing everything.
             */
            long trimToTime = hb.isWaitingForSnapshotBegin() ? hb.getEverSnapshotTime() :
                (shouldStoreEverything ? Long.MIN_VALUE : timeTotal);

            /*
             * (4) Update snapshot time and do sweeping remove from HB when snapshot time goes down.
             * We DO NOT do this for records that are outside our current timeTotal.
             * Adds to removeBuffer anything that was removed in the process.
             */
            long sweepRemovePosition = removeBuffer.getLimit();
            long prevSnapshotTime = hb.getSnapshotTime();
            assert hb.validTimes() && hb.getEverSnapshotTime() >= trimToTime; // this invariant is always maintained when updating timeTotal
            boolean updatedEverSnapshotTime = // limit sweep to trimToTime (that is, while waiting for snapshot to begin)
                hb.updateSnapshotTimeAndSweepRemove(time, trimToTime, record, cipher, symbol, removeBuffer, statsStorage, rid);
            /*
             * (5) Update or remove existing record in HB. We do this only if it falls inside our timeTotal and
             * consider "nothing updated" otherwise (it might have still updated snapshot time and removed something
             * from HB in process).
             */
            if (time >= trimToTime &&
                hb.putRecord(time, cursor, (eventFlags & REMOVE_EVENT) != 0, statsStorage, rid))
            {
                if (TRACE_LOG)
                    log.trace("updatedRecord");
                // mark event that updated record in HB (it will have to be processed by agents)
                distFlags |= UPDATED_RECORD_DIST_FLAG;
            }
            /*
             * (6) Turn on implicit sweep update transaction when anything is sweep-removed by this event
             * or updated record in the previous snapshot (as opposed to extending snapshot).
             * The latter case is turned on only inside subscription range (of totalTime) and is skipped
             * when changes are being performed in "storeEverything" mode below totalTime, because
             * there is no mechanism to turn this transaction off.
             */
            long endRemovePosition = removeBuffer.getLimit();
            if (endRemovePosition != sweepRemovePosition ||
                ((distFlags & UPDATED_RECORD_DIST_FLAG) != 0 &&
                    time < prevSnapshotTime && time > timeTotal && !updatedEverSnapshotTime))
            {
                hb.updateSweepTxOn();
            }
            /*
             * (7) Process snapshot end logic. SNAPSHOT_END flag is IMPLICIT on any event below total subscription time.
             */
            boolean receivedSnapshotEnd = (eventFlags & SNAPSHOT_SNIP) != 0 || time <= timeTotal;
            if (receivedSnapshotEnd) {
                hb.snapshotEnd();
            }

            /*
             * (8) Turn off implicit sweep update transaction when either SNAPSHOT_SNIP or an actual snapshot end
             * is received.
             */
            if (receivedSnapshotEnd && hb.updateSweepTxOff()) {
                // mark event that ends transaction (it will have to be processed by agents)
                distFlags |= TX_END_DIST_FLAG;
            }
            /*
             * Bailout if there are no changes (nothing removed or updated by this event),
             * including no change to snapshot time, and it is not otherwise specially marked with distFlags.
             */
            // Here we have invariant: snipRemovePosition <= sweepRemovePosition <= endRemovePosition
            if (distFlags == 0 && snipRemovePosition == endRemovePosition && hb.getSnapshotTime() == prevSnapshotTime)
                continue;
            /*
             * (9) Enforce limits on max HB size when new record is added or updated.
             */
            if (historyFilter != null && (eventFlags & REMOVE_EVENT) == 0 && (distFlags & UPDATED_RECORD_DIST_FLAG) != 0)
                hb.enforceMaxRecordCount(historyFilter.getMaxRecordCount(record, cipher, symbol), statsStorage, rid);
            /*
             * Mark all events that are part of the implicit sweep transaction.
             */
            if (hb.isSweepTx())
                distFlags |= TX_SWEEP_DIST_FLAG;
            /*
             * Mark all events on HB with the snapshot.
             */
            if (hb.wasEverSnapshotMode())
                distFlags |= HAD_SNAPSHOT_DIST_FLAG;
            /*
             * Process events for all subscribed agents.
             */
            long position = cursor.getPosition();
            boolean shallTerminateBatch = false; // when pending process counter of any agent reaches max value
            for (Agent agent = ait.start(this, tindex); agent != null; agent = ait.next()) {
                if (agent.hasVoidRecordListener())
                    continue;
                SubMatrix nsub = agent.sub;
                int nagent = agent.number;
                int nindex = ait.currentIndex();
                // now we'll need time sub
                long timeSub = nsub.getLong(nindex + TIME_SUB);
                int oldDistSize = dist.size(); // used to track if anything was added to dist for this agent
                /*
                 * Process events removed during snapshot sweep.
                 * Here's an optimization. If this event just "beganSnapshot" and agent support history snapshot,
                 * then there's no need to remove events one-by-one, because BEGIN_SNAPSHOT will be delivered to
                 * this agent.
                 */
                if (sweepRemovePosition != endRemovePosition &&
                    (!agent.useHistorySnapshot() || (distFlags & SEND_SNAPSHOT_DIST_FLAG) == 0))
                {
                    /*
                     * Submit snapshot-removed records (if subscribed). Note, that they constitute a part of
                     * an implicit sweep transaction.
                     */
                    addRemovedToDist(removeBuffer, sweepRemovePosition, endRemovePosition, dist,
                        (UPDATED_RECORD_DIST_FLAG | TX_SWEEP_DIST_FLAG) | (distFlags & HAD_SNAPSHOT_DIST_FLAG),
                        nagent, rid, timeSub);
                }
                /*
                 * Submit event to the agent if one of the following is true
                 * 1. event that is in subTime range of this agent:
                 *   a) the record in HB was actually updated
                 *       => the agent needs to add this event to update or snapshot queue
                 *   b) snapshot was snipped inside the subscription range
                 *       => need to  deliver SNAPSHOT_SNIP to the agent (if uses history snapshot) or update its known time anyway
                 * 2. agent uses history snapshot and the event
                 *   a) has ended a transaction
                 *       => the agent must process it to end its transaction.
                 *   b) had began snapshot in HB (for a first time snapshot was seen)
                         => the agent needs to add this event to reset its knowTime and start sending snapshot
                 *   c) had updated snapshot time above sub time of this agent in non-legacy mode
                 *       => the agent needs to send more events from HB (including snapshot end if needed)
                 *
                 * NOTE: THIS METHOD CANNOT RELY ON CHECKS OF TIME_KNOWN, BECAUSE BETWEEN THE CALL OF THIS
                 * METHOD processRecordSourceGLocked AND THE NEXT METHOD processAgentDataUpdate GLOBAL LOCK IS
                 * RELEASED AND retrieveData MAY HAPPEN AND UPDATE TIME_KNOWN. TIME_SUB MAY ALSO UPDATE, BUT
                 * WHEN TIME_SUB UPDATES, THEN TIME_KNOWN IS SET TO LONG.MAX_VALUE.
                 */
                if (time >= timeSub && // (1)
                    ((distFlags & (UPDATED_RECORD_DIST_FLAG | UPDATED_SNIP_DIST_FLAG)) != 0) || // (1a, 1b)
                    agent.useHistorySnapshot() && (
                        ((distFlags & (TX_END_DIST_FLAG | SEND_SNAPSHOT_DIST_FLAG)) != 0) || // (2a, 2b)
                        (time < prevSnapshotTime && prevSnapshotTime > timeSub && hb.wasEverSnapshotMode()) // (2c)
                    ))
                {
                    dist.add(nagent, position, distFlags, rid);
                }
                /*
                 * Process events removed during snapshot snip.
                 * This is only needed for legacy agents, since agents using history snapshot receive
                 * snapshotSnip event and remove the corresponding events themselves.
                 */
                if (snipRemovePosition != sweepRemovePosition && !agent.useHistorySnapshot()) {
                    addRemovedToDist(removeBuffer, snipRemovePosition, sweepRemovePosition, dist,
                        UPDATED_RECORD_DIST_FLAG, nagent, rid, timeSub);
                }
                /*
                 * We increment pending count if and only if anything was added to dist.
                 * We do one increment per the whole batch of events makes sure that counter does not overflow,
                 * that is counter does not exceed the number of records that are processed in a batch.
                 */
                if (dist.size() > oldDistSize && incAgentSubProcessPendingCountAndMark(dist, nsub, nindex))
                    shallTerminateBatch = true;
            }
            if (shallTerminateBatch || !dist.hasCapacity())
                break;
        }
        return cursor != null;
    }

    private int createDummyTotalSubEntry(SubMatrix tsub, int rid, int key) {
        int tindex = tsub.addIndexBegin(key, rid);
        tsub.setInt(tindex + NEXT_AGENT, NO_NEXT_AGENT_BUT_STORE_HB); // dummy next agent
        tsub.setLong(tindex + TIME_TOTAL, Long.MAX_VALUE); // dummy total sub time
        tsub.addIndexComplete(tindex, key, rid);
        tsub.updateAddedPayload(rid);
        return tindex;
    }

    private void addRemovedToDist(RecordBuffer removeBuffer, long position, long limit,
        Distribution dist, int distFlags, int nagent, int rid, long timeSub)
    {
        assert position != limit; // called only when there is anything to do
        removeBuffer.setPosition(position);
        do {
            RecordCursor removeCursor = removeBuffer.next();
            long removeCursorTime = removeCursor.getTime();
            if (removeCursorTime < timeSub)
                break; // not subscribed to this and subsequent removed records (they are in decreasing time)
            // negated position is used to indicate a position from removeBuffer,
            // removed records implicitly updated record and a part of implicit sweep update
            dist.add(nagent, ~removeCursor.getPosition(), distFlags, rid);
        } while (removeBuffer.getPosition() != limit);
    }

    // DISTRIBUTION Phase 2: update and notify agents.
    // SYNC: local
    @Override
    int processAgentDataUpdate(Distribution dist, RecordSource buffer, Agent agent) {
        /*
         * Process each event record for this agent and update agent's state correspondingly.
         */
        for (int i = dist.firstIndex(agent); i > 0; i = dist.nextIndex(i)) {
            long position = dist.getPayloadLong(i);
            /*
             * Parse distribution payload (figure out what is this event).
             * removeBuffer is true when the record was snapshot-removed from HB.
             * Negated (negative!) position is used to indicate a position from removeBuffer.
             */
            boolean removeBuffer = position < 0;
            RecordCursor cursor = removeBuffer ?
                dist.getRemoveBuffer().cursorAt(~position) :
                buffer.cursorAt(position);
            int key = getKey(cursor.getCipher(), cursor.getSymbol());
            if (key == 0)
                continue; // no longer subscribed to this symbol at all -- bailout
            int rid = getRid(cursor.getRecord());
            SubMatrix asub = agent.sub;
            int aindex = asub.getIndex(key, rid, 0);
            /*
             * Check that there is still current subscription to the corresponding symbol and the subscription
             * has not changed between phased. Unsubscription always resets process version to zero and
             * if subscription appears and new processing starts on then, then it will have a different process
             * version.
             */
            if ((asub.getInt(aindex + HISTORY_SUB_FLAGS) & PROCESS_VERSION_MASK) != dist.getCurProcessVersion())
                continue; // subscription was changed between phases -- bailout
            /*
             * Check for blocking mode before trying to process this event and update any state.
             */
            if (agent.buffer.blockNewRecord())
                return i;
            /*
             * Gather information about event.
             */
            int distFlags = dist.getFlags(i);
            long time = cursor.getTime();
            long timeSub = asub.getLong(aindex + TIME_SUB);
            long timeKnown = asub.getLong(aindex + TIME_KNOWN);

            if (TRACE_LOG)
                log.trace("processAgentDataUpdate " + historyCursorString(cursor) + ", timeSub=" + timeSub +
                    ", timeKnown=" + timeKnown + " for " + agent);

            assert timeKnown >= timeSub; // this is the key invariant of a history subscription state (NOTE: timeKnown can grow)
            long lastRecordWithBit = asub.getLong(aindex + LAST_RECORD);
            int eventFlags = removeBuffer ? REMOVE_EVENT : // implicit flag on record from removeBuffer
                cursor.getEventFlags() & (TX_PENDING | REMOVE_EVENT | SNAPSHOT_SNIP); // only these original flags are used here
            /*
             * Convert distFlag TX_SWEEP_DIST_FLAG into eventFlag TX_PENDING
             */
            if ((distFlags & TX_SWEEP_DIST_FLAG) != 0 && agent.useHistorySnapshot())
                eventFlags |= TX_PENDING;
            /*
             * Decrement pending count and clear processing version to mark the end of 2nd phase for this item.
             */
            boolean noMorePending = false;
            if ((distFlags & DEC_PENDING_COUNT_DIST_FLAG) != 0)
                noMorePending = decAgentSubProcessPendingCountAndClear(dist, asub, aindex, rid);
            /*
             * If agent supports history snapshot protocol and the snapshot had just began in buffer (beganSnapshot),
             * then reset agent's known time, so that it starts delivering snapshot from HB and clear everything from
             * the local buffer (which might break snapshot consistency)
             */
            if (agent.useHistorySnapshot() && (distFlags & SEND_SNAPSHOT_DIST_FLAG) != 0) {
                timeKnown = Long.MAX_VALUE;
                asub.setLong(aindex + TIME_KNOWN, timeKnown);
                // Drop everything queued in agent buffer
                unlinkFromAgentBufferAndClearTxDirty(agent, asub, aindex);
                lastRecordWithBit = 0;
            }
            /*
             * Snapshot snip to time >= timeKnown must advance timeKnow to timeSub.
             */
            if ((eventFlags & SNAPSHOT_SNIP) != 0 && time >= timeKnown) {
                timeKnown = timeSub;
                asub.setLong(aindex + TIME_KNOWN, timeKnown);
            }
            /*
             * This is the logic to turn off TX_DIRTY flag on agents that supports history snapshot when all
             * of the following is true:
             *   1. event is not a part of any kind of transaction (implicit tx was already converted into TX_PENDING flag) AND
             *   2. we have a completely-retrieved agent (timeKnown == timeSub) AND
             *   3. no more pending records to be processed in this batch.
             *
             * Note that the following was the case before this event was received:
             *   a) Retrieval from global buffer was continuing to report TX_PENDING (because of TX_DIRTY flag).
             *   b) Retrieve from local buffer was continuing to report TX_PENDING,
             *      because all events in local buffer were marked TX_PENDING in makeAgentTxDirty,
             *      and all newly added events for TX_DIRTY agent were also marked TX_PENDING.
             */
            if (agent.useHistorySnapshot() && isAgentTxDirty(lastRecordWithBit)
                && (eventFlags & TX_PENDING) == 0 // (1)
                && timeKnown == timeSub  // (2)
                && noMorePending) // (3)
            {
                if (TRACE_LOG)
                    log.trace("makeAgentNonTxDirty in History");
                lastRecordWithBit = makeAgentNonTxDirty(asub, aindex, lastRecordWithBit);
                distFlags |= TX_END_DIST_FLAG; // pretend this is the end of transaction for the logic below
            }
            /*
             * TX_END event has to be forced to be processed in the agent queue(!) when we already
             * have a completely-retrieved agent (timeKnown == timeSub), because agent does not have a chance
             * to send a snapshot end anymore during retrieve from HB to turn off TX_PENDING,
             * so we have to handle a case of such even when time < timeSub specially here.
             */
            long processTime = time;
            boolean virtualTime = false;
            if ((distFlags & TX_END_DIST_FLAG) != 0
                && timeKnown == timeSub
                && time < timeSub)
            {
                /*
                 * Pretend that event's time is at timeSub for the logic below, but mark the event with
                 * REMOVED_EVENT flag. This way the agent user does not have to validate events against their
                 * subscription time to get a consistent view. Previous event flags dropped in order to prevent
                 * snapshot removal
                 */
                processTime = timeSub;
                virtualTime = true;
                eventFlags = REMOVE_EVENT; // QD-1098
            }
            /*
             * Classification of incoming event according to its role in the snapshot sync algorithm:
             *
             *   removeEvent = (eventFlags & REMOVE_EVENT) != 0
             *   updatedRecord = (distFlags & Distribution.UPDATED_RECORD_DIST_FLAG) != 0
             *
             *   RB  - removeBuffer                NHB - new/updated record in HB added or exposed moving snapshotTime
             *   RE  - removeEvent                       Add it to global queue when time < timeKnown
             *   UR  - updatedRecord               GSE - may need to generate snapshot end event when useHistorySnapshot
             *                                     ABU - need to add update to agent buffer when time >= timeKnown
             *   ----------------       ---------------
             *    RB |  RE |  UR        NHB | GSE | ABU   Coverage by HistorySnapshotTest
             *   --- | --- | ---        --- | --- | ---
             *     T |  T  |  T          F  |  F  |  T    PHASE 2 removed record (swept past it)
             *     F |  T  |  T          F  |  T  |  T    PHASE 4 REMOVE_TIME was received and removed from HB
             *     F |  T  |  F          F  |  T  |  F    PHASE 4+ REMOVE_TIME confirms missing record, moves snapshot
             *     F |  F  |  T          T  |  T  |  T    PHASE 1,3 added/restored previously removed
             *     F |  F  |  F          T  |  T  |  F    PHASE 2,3,4+ confirmed that record still there
             *
             * Note, that is timeSub could have changed in between global-locked phase 1 of distribution and
             * this local-locked phase 2 of distribution. In this case timeKnown was reset to Long.MAX_VALUE and
             * agent buffer was cleared, however retrieveData may have happened between the phases, too,
             * and might have already retrieves some data from HB and updated timeKnow in turn.
             * We need to keep this in mind and recheck event time's relation to timeSub as appropriate.
             * Extra add of symbol to snapshot queue in the code below is not a big deal in this case, though.
             * It only schedules retrieveData which will figure everything out.
             *
             * First, check if event is below timeKnown and shall be retrieved from the global snapshot queue (if ever).
             */
            if (processTime < timeKnown) {
                /*
                 * Event is actually retrieved from snapshot queue if one of the following is true:
                 * 1. It is an update or confirmed record above timeSub
                 *    => It will need to be delivered from global queue
                 * 2. It is an agent that uses history snapshot and agent's timeSub < timeKnown
                 *    => It will need to generate virtual SnapshotEnd (or SnapshotSnip) event
                 */
                if ((eventFlags & REMOVE_EVENT) == 0 && processTime >= timeSub ||
                    agent.useHistorySnapshot() && !removeBuffer && timeSub < timeKnown)
                {
                    // Add to snapshotQueue for retrieveSnapshotBatchFromGlobalHistoryBuffer
                    agent.snapshotQueue.linkToQueue(agent, aindex, SNAPSHOT_QUEUE, false);
                }
                continue;
            }
            // ------------------------------------ time >= timeKnown ------------------------------------
            /*
             * Figure out if the corresponding record is already in buffer.
             */
            long lastRecord = lastRecordWithBit & ~TX_DIRTY_LAST_RECORD_BIT;
            boolean lastRecordInBuffer = agent.buffer.isInBuffer(lastRecord);
            /*
             * Only the following types of events are added to the agent buffer:
             *  1) Events that updated record in HB (including events from removeBuffer).
             *  2) Events that updated snapshot snip flag for agents that support history snapshot.
             *     legacy agents do not add these to agent buffer (they remove individual snipped events instead).
             * However, even if the event does not fall into any of the above categories, there is a special treatment
             * for TX_END events.
             *
             * There is also an invariant that timeKnown >= timeSub, so hereafter we have:
             *   time >= timeKnown >= timeSub
             */
            if ((distFlags & UPDATED_RECORD_DIST_FLAG) == 0 && // not (1)
                ((distFlags & UPDATED_SNIP_DIST_FLAG) == 0 || !agent.useHistorySnapshot())) // not (2)
            {
                if ((distFlags & TX_END_DIST_FLAG) == 0)
                    continue; // nothing interesting (not update, not TX_END)
                if (isAgentTxDirty(lastRecordWithBit))
                    continue; // agent is dirty, so we don't need to deliver non-updating event (there is no tx to end!)
                /*
                 * Special code to process non-updating TX_END event here when agent is not dirty.
                 * If there is another record in buffer, then we make optimization by using it to end transaction
                 * (remove TX_PENDING bit on it). Otherwise, we'll need to deliver this event even if it
                 * does not actually update anything -- just to end tx.
                 */
                if (lastRecordInBuffer) {
                    RecordCursor writeCursor = agent.buffer.writeCursorAtPersistentPosition(lastRecord);
                    writeCursor.setEventFlags(writeCursor.getEventFlags() & ~TX_PENDING);
                    continue;
                }
            }
            /*
             * If agent uses history snapshot and is not TX_DIRTY, then it becomes dirty when:
             *   1) agent is not completely retrieved (timeKnown > timeSub) AND
             *   2) event was received by history buffer in snapshot state
             *
             * This is so, because here we have an event that updates already retrieved part of the snapshot
             * (time >= timeKnown) which may have happened after an update to the part of the snapshot that is yet
             * to be retrieved, so the resulting snapshot may end up being inconsistent
             * (see HistoryTxTest.testPartialRetrieveUpdateInconsistency)
             *
             * The second check is important to ensure that with legacy data sources (that do not send SNAPSHOT_BEGIN)
             * we never get to deal with any transactions.
             */
            if (agent.useHistorySnapshot() && !isAgentTxDirty(lastRecordWithBit)
                && timeKnown > timeSub // (1)
                && (distFlags & HAD_SNAPSHOT_DIST_FLAG) != 0) // (2)
            {
                if (TRACE_LOG)
                    log.trace("makeAgentTxDirty on timeKnown > timeSub in History");
                lastRecordWithBit = makeAgentTxDirty(agent, asub, aindex, lastRecordWithBit);
            }
            /*
             * Make sure that records added to TX_DIRTY agent are marked with TX_PENDING flag.
             * This works when we had just marked agent TX_DIRTY in the above code, too.
             */
            if (isAgentTxDirty(lastRecordWithBit))
                eventFlags |= TX_PENDING;
            /*
             * Figure if last record in buffer needs to be conflated.
             */
            if (lastRecordInBuffer) {
                RecordCursor writeCursor = agent.buffer.writeCursorAtPersistentPosition(lastRecord);
                if (writeCursor.getTime() == (virtualTime ? VIRTUAL_TIME : time) &&
                    conflateFilter.accept(writeCursor))
                {
                    // Conflate update to the most recently updated item and that's it
                    conflateLastRecord(cursor, writeCursor, virtualTime, eventFlags);
                    continue;
                }
            }
            /*
             * Check for buffer overflow: Just drop new record that we are trying to append if buffer overflows.
             * There is no attempt to recover state. Dropped records may mean dropped transactions.
             * Dropping records is a stop-gap mechanism.
             */
            if (agent.buffer.dropNewRecord(cursor))
                continue;
            /*
             * Add new record to the buffer; pay attention to keep TX_DIRTY flag.
             */
            lastRecordWithBit = agent.buffer.getLastPersistentPosition() | (lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT);
            asub.setLong(aindex + LAST_RECORD, lastRecordWithBit);
            RecordCursor writeCursor = agent.buffer.addDataAndCompactIfNeeded(cursor);
            if (virtualTime) {
                writeCursor.clearDataButTime();
                writeCursor.setTime(VIRTUAL_TIME);
            }
            writeCursor.setEventFlags(eventFlags);  // always set flags, agent that does not useHS, has no flags
            if (hasEventTimeSequence())
                writeCursor.setEventTimeSequence(cursor.getEventTimeSequence());
            writeCursor.setTimeMark(cursor.getTimeMark());
            if (lastRecordInBuffer)
                writeCursor.setLinkTo(lastRecord + agent.buffer.getPositionBase());
            if (agent.hasAttachmentStrategy())
                writeCursor.setAttachment(asub.getObj(aindex, ATTACHMENT));
        }
        agent.buffer.dropOldRecords();
        agent.buffer.logDrops(agent);
        rebaseIfNeeded(agent);
        return 0;
    }

    // SYNC: global (invoked during 1st phase)
    // returns true when counter has the maximal possible value (processing batch must terminate)
    private boolean incAgentSubProcessPendingCountAndMark(Distribution dist, SubMatrix sub, int index) {
        int historySubFlags = sub.getInt(index + HISTORY_SUB_FLAGS);
        int prevProcessVersion = historySubFlags & PROCESS_VERSION_MASK;
        int curProcessVersion = dist.getCurProcessVersion();
        if (prevProcessVersion != curProcessVersion) {
            if (prevProcessVersion != 0) {
                /*
                 * It is being processed by different thread, so wait until that other thread is over with processing.
                 * This should be an extremely rare corner-case occurrence, so we don't really mind this wait.
                 */
                processVersion.waitWhileInProcess(prevProcessVersion);
                /*
                 * We don't recheck the state here. PROCESS_VERSION should be zero after processing is over, but there
                 * is a rare potential case when 2nd phase of processing terminates with exception. The corresponding
                 * processing is marked as "done" in ProcessVersionTracker in using "finally" block, but
                 * HISTORY_SUB_FLAGS may be left with stale values.
                 * Thus, we always just rewrite HISTORY_SUB_FLAGS with curProcessVersion.
                 */
            }
            // initialize with current version and count of 1
            historySubFlags =
                (historySubFlags & ~(PROCESS_VERSION_MASK | PENDING_COUNT_MASK)) | curProcessVersion | PENDING_COUNT_INC;
        } else {
            // already processing -- just increment count
            if ((historySubFlags & PENDING_COUNT_MASK) == PENDING_COUNT_MASK) {
                // this should never happen, since we terminate batch when this counter value is reached
                throw FatalError.fatal(this, "PENDING_COUNT overflow");
            }
            historySubFlags += PENDING_COUNT_INC;
        }
        // now set to our version #
        sub.setInt(index + HISTORY_SUB_FLAGS, historySubFlags);
        // now adjust dist flags of last added dist item
        dist.addFlagsToLastAdded(DEC_PENDING_COUNT_DIST_FLAG);
        return (historySubFlags & PENDING_COUNT_MASK) == PENDING_COUNT_MASK;
    }

    // SYNC: local (invoked during 2nd phase)
    // returns true when no more pending records left to process
    private boolean decAgentSubProcessPendingCountAndClear(Distribution dist, SubMatrix sub, int index, int rid) {
        int historySubFlags = sub.getInt(index + HISTORY_SUB_FLAGS);
        if ((historySubFlags & PROCESS_VERSION_MASK) != dist.getCurProcessVersion())
            throw FatalError.fatal(this, "PROCESS_VERSION is invalid " +
                (historySubFlags & PROCESS_VERSION_MASK) + " != " + dist.getCurProcessVersion());
        if ((historySubFlags & PENDING_COUNT_MASK) == 0)
            throw FatalError.fatal(this, "PENDING_COUNT is zero");
        historySubFlags -= PENDING_COUNT_INC;
        boolean noMorePending = (historySubFlags & PENDING_COUNT_MASK) == 0;
        if (noMorePending) {
            // last item was processed -- clear process version
            historySubFlags &= ~PROCESS_VERSION_MASK;
        }
        sub.setInt(index + HISTORY_SUB_FLAGS, historySubFlags);
        if (!sub.isPayload(index))
            sub.updateRemovedPayload(rid);
        return noMorePending;
    }

    static boolean isAgentSubProcessing(SubMatrix sub, int index) {
        return (sub.getInt(index + HISTORY_SUB_FLAGS) & PROCESS_VERSION_MASK) != 0;
    }

    static boolean isAgentSubSnip(SubMatrix sub, int index) {
        return (sub.getInt(index + HISTORY_SUB_FLAGS) & SNIP_TIME_SUB_FLAG) != 0;
    }

    // check lastRecordWithBit
    static boolean isAgentTxDirty(long lastRecordWithBit) {
        return (lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT) != 0;
    }

    // returns updated lastRecordWithBit
    // SYNC: local
    static long makeAgentTxDirty(Agent agent, SubMatrix asub, int aindex, long lastRecordWithBit) {
        assert !isAgentTxDirty(lastRecordWithBit);
        long lastRecord = lastRecordWithBit & ~TX_DIRTY_LAST_RECORD_BIT;
        if (agent.buffer.isInBuffer(lastRecord))
            agent.buffer.flagFromPersistentPosition(lastRecord, TX_PENDING);
        // Set TX_DIRTY flag
        lastRecordWithBit |= TX_DIRTY_LAST_RECORD_BIT;
        asub.setLong(aindex + LAST_RECORD, lastRecordWithBit);
        return lastRecordWithBit;
    }

    // returns updated lastRecordWithBit
    static long makeAgentNonTxDirty(SubMatrix asub, int aindex, long lastRecordWithBit) {
        assert isAgentTxDirty(lastRecordWithBit);
        lastRecordWithBit &= ~TX_DIRTY_LAST_RECORD_BIT; // turn off TX_DIRTY flag
        asub.setLong(aindex + LAST_RECORD, lastRecordWithBit);
        return lastRecordWithBit;
    }

    private void conflateLastRecord(RecordCursor cursor, RecordCursor writeCursor, boolean virtualTime, int eventFlags) {
        writeCursor.setEventFlags(eventFlags); // always overwrite flags, agent that does not useHS, has no flags
        if ((eventFlags & REMOVE_EVENT) != 0) {
            // on REMOVE_TIME event clear data and mark
            writeCursor.clearDataButTime();
            writeCursor.setTimeMark(0);
        } else {
            assert !virtualTime; // all VIRTUAL_TIME events have REMOVE_EVENT flag
            // on update data event copy data only, keep previous mark (if it was defined)
            writeCursor.copyDataFrom(cursor);
            if (writeCursor.getTimeMark() == 0)
                writeCursor.setTimeMark(cursor.getTimeMark());
        }
    }

    // ========== QDHistory Implementation ==========

    @Override
    public long getMinAvailableTime(DataRecord record, int cipher, String symbol) {
        if (!record.hasTime())
            throw new IllegalArgumentException("Record does not contain time.");
        globalLock.lock(CollectorOperation.MIN_TIME);
        try {
            return getMinAvailableTimeGLocked(record, cipher, symbol);
        } finally {
            globalLock.unlock();
        }
    }

    // SYNC: global
    private long getMinAvailableTimeGLocked(DataRecord record, int cipher, String symbol) {
        int key = getKey(cipher, symbol);
        int rid = getRid(record);
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(key, rid, 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb == null)
            return 0;
        return hb.getMinAvailableTime();
    }

    @Override
    public long getMaxAvailableTime(DataRecord record, int cipher, String symbol) {
        if (!record.hasTime())
            throw new IllegalArgumentException("Record does not contain time.");
        globalLock.lock(CollectorOperation.MAX_TIME);
        try {
            return getMaxAvailableTimeGLocked(record, cipher, symbol);
        } finally {
            globalLock.unlock();
        }
    }

    // SYNC: global
    private long getMaxAvailableTimeGLocked(DataRecord record, int cipher, String symbol) {
        int key = getKey(cipher, symbol);
        int rid = getRid(record);
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(key, rid, 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb == null)
            return 0;
        return hb.getMaxAvailableTime();
    }

    @Override
    public int getAvailableCount(DataRecord record, int cipher, String symbol, long startTime, long endTime) {
        if (!record.hasTime())
            throw new IllegalArgumentException("Record does not contain time.");
        globalLock.lock(CollectorOperation.COUNT_DATA);
        try {
            return getAvailableCountGLocked(record, cipher, symbol, startTime, endTime);
        } finally {
            globalLock.unlock();
        }
    }

    // SYNC: global
    private int getAvailableCountGLocked(DataRecord record, int cipher, String symbol, long start_time, long end_time) {
        int key = getKey(cipher, symbol);
        int rid = getRid(record);
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(key, rid, 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb == null)
            return 0;
        return hb.getAvailableCount(start_time, end_time);
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime,
        DataVisitor visitor)
    {
        return examineData(record, cipher, symbol, startTime, endTime, LegacyAdapter.of(visitor));
    }

    @Override
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime,
        RecordSink sink)
    {
        globalLock.lock(CollectorOperation.EXAMINE_DATA);
        try {
            return examineDataRangeGLocked(record, cipher, symbol, startTime, endTime, sink);
        } finally {
            globalLock.unlock();
            sink.flush();
        }
    }

    // SYNC: global
    private boolean examineDataRangeGLocked(DataRecord record, int cipher, String symbol, long startTime, long endTime,
        RecordSink sink)
    {
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(getKey(cipher, symbol), getRid(record), 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        return hb != null &&
            (startTime > endTime ?
                hb.examineDataRangeRTL(record, cipher, symbol, startTime, endTime, sink, keeper, null) :
                hb.examineDataRangeLTR(record, cipher, symbol, startTime, endTime, sink, keeper, null));
    }

    // SYNC: global
    @Override
    void examineSubDataInternalByIndex(Agent agent, int aindex, RecordSink sink) {
        SubMatrix asub = agent.sub;
        int key = asub.getInt(aindex + KEY);
        int rid = asub.getInt(aindex + RID);
        long time = hasTime ? asub.getLong(aindex + TIME_SUB) : 0;
        Object attachment = agent.hasAttachmentStrategy() ? asub.getObj(aindex, ATTACHMENT) : null;
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(key, rid, 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb != null)
            hb.examineDataSnapshot(records[rid], getCipher(key), getSymbol(key), time, sink, keeper, attachment);
    }

    @Override
    public boolean examineData(RecordSink sink) {
        SubMatrix tsub = total.sub;
        int nExaminedInBatch = 0;
        // iterate over matrix
        for (int tindex = tsub.matrix.length; (tindex -= tsub.step) >= 0; ) {
            if (tsub.isPayload(tindex)) {
                HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
                if (hb == null)
                    continue;
                int nExamined;
                globalLock.lock(CollectorOperation.EXAMINE_DATA);
                try {
                    nExamined = examineDataSnapshotGLocked(sink, tsub, tindex, hb);
                } finally {
                    globalLock.unlock();
                }
                if (nExamined < 0) {
                    if (nExaminedInBatch - nExamined - 1 > 0)
                        sink.flush();
                    return true;
                }
                nExaminedInBatch += nExamined;
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

    // SYNC: global
    // returns (-1 - nExamined) when sink has no more capacity or number of records examined so far
    private int examineDataSnapshotGLocked(RecordSink sink, SubMatrix tsub, int tindex, HistoryBuffer hb) {
        // recheck under global lock that iterated entry is still good to examine
        // if anything looks suspicious from concurrency point of view -- skip it
        if (hb != tsub.getObj(tindex, HISTORY_BUFFER))
            return 0;
        int key = tsub.getInt(tindex + KEY);
        int rid = tsub.getInt(tindex + RID);
        int cipher = key;
        String symbol = null;
        if ((key & SymbolCodec.VALID_CIPHER) == 0) {
            cipher = 0;
            symbol = tsub.getMapping().getSymbolIfPresent(key); // do not cache mapping to see concurrent mapping rehash
            if (symbol == null)
                return 0;
        }
        // DebugDumpReader support
        if (sink instanceof HistoryBufferDebugSink)
            ((HistoryBufferDebugSink) sink).visitHistoryBuffer(records[rid], cipher, symbol,
                tsub.getLong(tindex + TIME_TOTAL), hb);
        // Note, toTime is set to Long.MIN_VALUE, so HB uses "everSnapshotTime" that tracks minimum snapshot
        // time that was ever received and was not subsequently lost due to unsubscribe
        int examineMethodResult;
        if (hb.examineDataSnapshot(records[rid], cipher, symbol, Long.MIN_VALUE, sink, keeper, null))
            examineMethodResult = -1 - hb.nExamined;
        else
            examineMethodResult = hb.nExamined;
        // DebugDumpReader support
        if (sink instanceof HistoryBufferDebugSink)
            ((HistoryBufferDebugSink) sink).visitDone(records[rid], cipher, symbol, examineMethodResult);
        return examineMethodResult;
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        int nExaminedInBatch = 0;
        for (RecordCursor subCursor; (subCursor = sub.next()) != null; ) {
            DataRecord record = subCursor.getRecord();
            int cipher = subCursor.getCipher();
            String symbol = subCursor.getSymbol();
            long toTime = subCursor.getTime();
            int nExamined;
            globalLock.lock(CollectorOperation.EXAMINE_DATA);
            try {
                nExamined = examineDataBySubscriptionGLocked(record, cipher, symbol, toTime, sink);
            } finally {
                globalLock.unlock();
            }
            if (nExamined < 0) {
                if (nExaminedInBatch - nExamined - 1 > 0)
                    sink.flush();
                return true;
            }
            nExaminedInBatch += nExamined;
            if (nExaminedInBatch >= EXAMINE_BATCH_SIZE) {
                sink.flush();
                nExaminedInBatch = 0;
            }
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return false;
    }

    // SYNC: global
    // returns (-1 - nExamined) when sink has no more capacity or number of records examined so far
    private int examineDataBySubscriptionGLocked(DataRecord record, int cipher, String symbol, long toTime, RecordSink sink) {
        SubMatrix tsub = total.sub;
        int tindex = tsub.getIndex(getKey(cipher, symbol), getRid(record), 0);
        HistoryBuffer hb = (HistoryBuffer) tsub.getObj(tindex, HISTORY_BUFFER);
        if (hb == null)
            return 0;
        if (hb.examineDataSnapshot(record, cipher, symbol, toTime, sink, keeper, null))
            return -1 - hb.nExamined;
        return hb.nExamined;
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
        SubMatrix tsub = total.sub;
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            int rid = getRid(cur.getRecord());
            int key = getKey(cur.getCipher(), cur.getSymbol());
            int tindex = tsub.getIndex(key, rid, 0);
            if (tindex == 0)
                continue;
            removeHistoryBufferAt(rid, tsub, tindex);
            if (tsub.getInt(tindex + NEXT_AGENT) == NO_NEXT_AGENT_BUT_STORE_HB) {
                tsub.setInt(tindex + NEXT_AGENT, 0);
                tsub.updateRemovedPayload(rid);
            }
        }
    }

    // ============================ Test support methods  ============================

    // THIS METHOD IS FOR TESTING OF REBASE FUNCTIONALITY (ONLY)
    public void forceRebase(QDAgent qdAgent) {
        Agent agent = (Agent) qdAgent;
        agent.localLock.lock(CollectorOperation.RETRIEVE_DATA);
        try {
            rebuildLastRecordAndRebase(agent);
        } finally {
            agent.localLock.unlock();
        }
    }

    // THIS METHOD IS OVERRIDDEN FOR TESTING PURPOSES (ONLY)
    protected boolean shallForceRetrieveUpdate() {
        return false;
    }

    private static String historyCursorString(RecordCursor cursor) {
        StringBuilder sb = new StringBuilder();
        sb.append(cursor.getDecodedSymbol());
        sb.append('@').append(cursor.getTime());
        for (int i = 2; i < cursor.getIntCount(); i++) {
            sb.append(',').append(cursor.getInt(i));
        }
        String flags = EventFlag.formatEventFlags(cursor.getEventFlags(), MessageType.HISTORY_DATA);
        if (!flags.isEmpty())
            sb.append(',').append(flags);
        return sb.toString();
    }
}
