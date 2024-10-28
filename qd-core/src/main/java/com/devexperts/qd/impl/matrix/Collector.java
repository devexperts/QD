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

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.devexperts.qd.impl.matrix.History.TX_DIRTY_LAST_RECORD_BIT;

/**
 * The <code>Collector</code> is a matrix-based implementation of {@link QDCollector}.
 * <p>
 * The <code>Collector</code> maintains whole subscription structure of a single
 * {@link QDCollector}, including individual agent's subscription and total
 * subscription. The individual subscriptions are stored in corresponding
 * {@link QDAgent}, but are maintained by collector. Total subscription is
 * stored in a special instance of {@link QDAgent} - {@link #total} to simplify
 * structural maintenance.
 * <p>
 * Each subscription entry corresponds to a certain data record and is
 * identified by 'key' and 'rid' (record id) (stored using {@link SubMatrix}).
 * Active subscription entries for the same data record are linked across all
 * interested agents into double-linked list with head stored in 'total' agent.
 * This list is asymmetrical - forward link identifies both next agent (NEXT_AGENT)
 * and index in that agent (NEXT_INDEX), while backward link identifies only
 * previous agent (PREV_AGENT). The reasons for this asymmetry are need for
 * fast forward iteration during distribution and desire to save resources
 * for backward links. Both links use 0 to indicate that there is no link in
 * that direction.
 */
public abstract class Collector extends AbstractCollector implements RecordsContainer {
    // It is used to debug problems in conjunction with TraceRunner & HistorySnapshotMTStressTest
    public static final boolean TRACE_LOG = Collector.class.desiredAssertionStatus();

    protected final Logging log = Logging.getLogging(getClass());

    static final int EXAMINE_BATCH_SIZE = SystemProperties.getIntProperty(
        Collector.class, "examineBatchSize", 10000, 1, Integer.MAX_VALUE);

    private static final int INITIAL_AGENTS_SIZE = 8;

    // For all agents:
    static final int KEY = SubMatrix.KEY; // Key offset in SubMatrix
    static final int RID = SubMatrix.RID; // RecordId offset in SubMatrix
    static final int NEXT_AGENT = 2; // Offset of agent.number of next agent.
    static final int NEXT_INDEX = 3; // Offset of index in next agent.

    // That's it for ticker & stream total agents
    static final int TOTAL_AGENT_STEP = 4;

    // For 'total' agents:
    static final int TIME_TOTAL = 4;
    static final int TIME_TOTAL_X = 5;

    // That's it for history total agents
    static final int TOTAL_HISTORY_AGENT_STEP = 6;

    // For clients' agents:
    static final int PREV_AGENT = 4; // Offset of agent.number of prev agent, see PREV_AGENT_xxx constants

    // That's it for Stream agents
    static final int STREAM_AGENT_STEP = 5;

    // For ticker & history agents -- snapshot queue
    static final int SNAPSHOT_QUEUE = 5;

    // For ticker agents -- update queue
    static final int UPDATE_QUEUE = 6;

    // That's it for Ticker agents
    static final int TICKER_AGENT_STEP = 7;

    // For history agents -- cleared in addSubInternal
    static final int HISTORY_SUB_FLAGS = 6;
    static final int TIME_SUB = 7;
    static final int TIME_SUB_X = 8;
    static final int TIME_KNOWN = 9;
    static final int TIME_KNOWN_X = 10;
    static final int LAST_RECORD = 11; // 0 means not in buffer, > 0 index in buffer
    static final int LAST_RECORD_X = 12;

    // That's it for History agents
    static final int HISTORY_AGENT_STEP = 13;

    /*--------------------------------------------------------------------------------------------------------------
     * FULL AGENT SUB MATRIX LAYOUT FOR DIFFERENT COLLECTORS:
     *     Total[Hist]    Ticker          Stream      History
     *     -------------- --------------- ----------- ----------------
     *  0   KEY            KEY             KEY         KEY
     *  1   RID            RID             RID         RID
     *  2   NEXT_AGENT     NEXT_AGENT      NEXT_AGENT  NEXT_AGENT      <-- Payload indicator for total sub (!=0 for sub items), -1 to keep without actual sub
     *  3   NEXT_INDEX     NEXT_INDEX      NEXT_INDEX  NEXT_INDEX
     *  4  [TIME_TOTAL  ]  PREV_AGENT      PREV_AGENT  PREV_AGENT      <-- Payload indicator for agent sub (!=0 for sub item)
     *  5  [TIME_TOTAL_X]  SNAPSHOT_QUEUE              SNAPSHOT_QUEUE
     *  6                  UPDATE_QUEUE                HISTORY_SUB_FLAGS <- SNIP_TIME_SUB_FLAG, pending count, process version, sync(global OR local)
     *  7                                              TIME_SUB       \ time_sub = Long.MAX_VALUE when not sub-d
     *  8                                              TIME_SUB_X     /
     *  9                                              TIME_KNOWN     \ time_known > time_sub for snapshot-queued
     * 10                                              TIME_KNOWN_X   / time_known := Long.MAX_VALUE on enqueue
     * 11                                              LAST_RECORD    \ 0 when not in buffer, or persistent position in
     * 12                                              LAST_RECORD_X  / AgentBuffer, may have TX_DIRTY_LAST_RECORD_BIT
     *--------------------------------------------------------------------------------------------------------------*/

    // For objects in 'total' agents:
    static final int HISTORY_BUFFER = 0;

    // That's it for objects in 'total' agents
    static final int TOTAL_OBJ_STEP = 0;
    static final int TOTAL_HISTORY_OBJ_STEP = 1;

    // For objects in clients' agents:
    static final int ATTACHMENT = 0;

    // That's it for objects in clients' agents
    static final int AGENT_OBJ_STEP = 0;
    static final int AGENT_ATTACHMENT_OBJ_STEP = 1;

    /*--------------------------------------------------------------------------------------------------------------*/

    // Flags for notification
    static final int NOTIFY_SUB_TOTAL_ADDED = 1;
    static final int NOTIFY_SUB_TOTAL_REMOVED = 2;
    static final int NOTIFY_SUB_SNAPSHOT_AVAILABLE = 4;
    static final int NOTIFY_SUB_DATA_AVAILABLE = 8;
    static final int NOTIFY_SUB_HAS_MORE = 16;
    static final int NOTIFY_SUB_PHASE2 = 32; // used by setSubscription to signal 2nd phase

    // Index of the total agent in the agents array
    static final int TOTAL_AGENT_INDEX = 1;
    static final int MIN_AGENT_INDEX = 2; // min index of a regular agent
    static final int MIN_DISTRIBUTOR_INDEX = 1;

    // PREV_AGENT values
    static final int PREV_AGENT_MASK = 0x7fffffff; // mask for PREV_AGENT field
    static final int PREV_AGENT_SET = 0x80000000; // bit for sub entries that were added by setSubscription

    // SNAPSHOT_QUEUE & UPDATE_QUEUE state mark bits
    static final int QUEUE_BIT = 1 << 31; // highest bit

    /*--------------------------------------------------------------------------------------------------------------
     * TICKER [SNAPSHOT_QUEUE]  ,       [UPDATE_QUEUE] combinations:
     *               0 | 0      ,            0 | 0       empty
     *       QUEUE_BIT | 0      ,            0 | 0       just subscribed to the item, no data yet
     *       QUEUE_BIT | <next> ,    QUEUE_BIT | <next>  data arrived, added to both snapshot & update queues (see NB)
     *               0 | <next> ,            0 | 0       data retrieved via update queue, still in snapshot queue
     *               0 | 0      ,            0 | <next>  data retrieved via snapshot queue, still in update queue
     *               0 | <mark> ,    QUEUE_BIT | <next>  more data arrived, queue mark recorded
     *               0 | <next> ,            0 | <next>  data removed via unsubscribe
     *       QUEUE_BIT | *      ,            0 | *       resubscribe before queues where fully retrieved
     *
     * NB:   QUEUE_BIT | <next> ,    QUEUE_BIT | <next>  this state may be entered spuriously
     *                                                   when new event arrives while <next> in snapshot queue is set
     *                                                   and event is already retrieved via update queue
     *
     * <next> == EOL (special non-zero value) when the item is last in the queue
     *
     * The only possible state, which contains <mark> is *NO* QUEUE_BIT in SNAPSHOT_QUEUE and QUEUE_BIT in UPDATE_QUEUE
     *
     * QUEUE_BIT in SNAPSHOT_QUEUE means -- this item waits for/has snapshot
     *                                      sometimes this bit can be set even if event is already processed
     *                absence of the bit -- queue mark              if QUEUE_BIT in UPDATE_QUEUE
     *                                      next in snapshot queue  otherwise
     * QUEUE_BIT in UPDATE_QUEUE means   -- this item has data to be sent
     *                absence of the bit -- this item has no data to be sent
     *--------------------------------------------------------------------------------------------------------------*/

    /*--------------------------------------------------------------------------------------------------------------
     * HISTORY [SNAPSHOT_QUEUE]
     *                0 | 0                               empty
     *        QUEUE_BIT | <next>                          data arrived, needs to retrieve from HistoryBuffer
     *                0 | 0                               data retrieved from snapshot completely
     *                0 | <next>                          data retrieved, but batch was over (no more capacity)
     *                                                    ^ will continue next time with agent.nSnapshotHistoryRem
     *
     * <next> == EOL (special non-zero value) when the item is last in the queue
     *--------------------------------------------------------------------------------------------------------------*/

    //======================================= instance fields =======================================

    final CollectorManagement management;
    final CollectorCounters counters;
    CollectorCounters snapshotCounters; // may be null, SYNC(counters)

    final RecordCursorKeeper keeper = new RecordCursorKeeper(); // SYNC: global, Ticker&History, auto-clear on globalLock.unlock()
    final GlobalLock globalLock; // Protects all structural modifications and almost all read/write.

    final Mapper mapper;
    final boolean hasTime;
    final DataRecord[] records;

    final QDStats stats;
    final QDStats statsStorage;
    final QDStats statsDropped;

    final Agent total; // The special agent which contains total subscription and list heads.

    Agent[] agents; // [agent.number] -> agent; SYNC: write(global), read(none)
    private int lastAgentIndex = MIN_AGENT_INDEX;

    final DistributorsList distributors = new DistributorsList();

    volatile QDErrorHandler errorHandler; // SYNC: none

    /**
     * A Queue that contains all agents that are currently being closed.
     * Under global lock {@link #helpClose} methods takes agents from this queue
     * and makes steps with {@link Agent#performCloseSteps} method to complete close operations.
     */
    private final ClosingAgentsQueue closingAgentsQueue = new ClosingAgentsQueue();

    int subNotifyAccumulator; // Current notification flags (NOTIFY_XXX); SYNC: r/w(global)

    /**
     * Number of remaining steps for subscription changes in the current subscription bucket.
     * Each add or remove of an item from the subscription is considered to be one step.
     * The number of steps per one acquire of global lock is controlled via
     * {@link CollectorManagement#getSubscriptionBucket()}
     */
    int subStepsRemaining; // SYNC: r/w(global)

    final LockBoundTaskQueue lockBoundTaskQueue = new LockBoundTaskQueue();

    //======================================= constructor =======================================

    Collector(Builder<?> builder, boolean hasTime, boolean has_storage, boolean has_dropped) {
        super(builder);
        this.management = CollectorManagement.getInstance(builder.getScheme(), getContract(), builder.getStats().getFullKeyProperties());
        this.counters = management.createCounters();

        this.globalLock = new GlobalLock(management, counters, keeper);
        this.mapper = new Mapper(this);
        this.hasTime = hasTime;
        this.records = new DataRecord[scheme.getRecordCount()];
        for (int i = records.length; --i >= 0;)
            records[i] = scheme.getRecord(i);

        this.stats = builder.getStats();
        this.statsStorage = has_storage ? stats.create(QDStats.SType.STORAGE_DATA) : null;
        this.statsDropped = has_dropped ? stats.create(QDStats.SType.DROPPED_DATA) : null;

        mapper.incMaxCounter(scheme.getRecordCount());
        QDStats unique_sub_stats = stats.create(QDStats.SType.UNIQUE_SUB);
        total = new Agent(this, TOTAL_AGENT_INDEX, agentBuilder(), unique_sub_stats);
        total.sub = new TotalSubMatrix(mapper,
            hasTime ? TOTAL_HISTORY_AGENT_STEP : TOTAL_AGENT_STEP,
            hasTime ? TOTAL_HISTORY_OBJ_STEP : TOTAL_OBJ_STEP,
            NEXT_AGENT, 0, 0, Hashing.MAX_SHIFT, unique_sub_stats);

        agents = new Agent[INITIAL_AGENTS_SIZE];
        agents[total.number] = total;

        errorHandler = scheme.getService(QDErrorHandler.class);
        if (errorHandler == null)
            errorHandler = QDErrorHandler.DEFAULT;

        management.addCollector(this);
    }

    //======================================= methods =======================================

    // Code is specific for Ticker, Stream, and History
    abstract Agent createAgentInternal(int number, QDAgent.Builder builder, QDStats stats);

    // tests override
    AgentBuffer createAgentBuffer(Agent agent) {
        return new AgentBuffer(agent);
    }

    // Stream and History override
    RecordMode getAgentBufferMode(Agent agent) {
        throw new UnsupportedOperationException();
    }

    public CollectorManagement getManagement() {
        return management;
    }

    public CollectorCounters getCountersSinceStart() {
        return counters.snapshot();
    }

    public CollectorCounters getCountersSinceSnapshot() {
        synchronized (counters) {
            return counters.since(snapshotCounters);
        }
    }

    public void snapshotCounters() {
        synchronized (counters) {
            snapshotCounters = counters.snapshot();
        }
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    public void dumpSubscription(SubscriptionDumpVisitor visitor) throws IOException {
        // variables
        Map<Agent, Integer> aid_map = new HashMap<>();
        SubMatrix tsub = total.sub; // Atomic read.

        // visit collector
        visitor.visitCollector(System.identityHashCode(this), stats.getFullKeyProperties(), contract.toString(), hasTime);

        // Implementation note: this method is full of "atomic read" operations, both explicit and implicit.
        // They are intended to prevent exceptions due to concurrent update of subscription matrix.
        // If such update is detected during iteration of subscription chain - broken chain is ignored.
        // :todo: ensure that all "atomic reads" are volatile reads
        for (int tindex = tsub.matrix.length; (tindex -= tsub.step) > 0;) {
            int nagent = tsub.getInt(tindex + NEXT_AGENT);
            if (nagent <= 0)
                continue;
            int nindex = tsub.getInt(tindex + NEXT_INDEX);
            int key = tsub.getVolatileInt(tindex); // ensure that the following rid read is correct
            if (key == 0)
                continue;

            // retrieve symbol is possible
            int cipher = getCipher(key);
            String symbol = null;
            if (cipher == 0) {
                symbol = mapper.getMapping().getSymbolIfPresent(key);
                if (symbol == null) // Concurrent subscription update - ignore
                    continue;
            }

            // visit record
            int rid = tsub.getInt(tindex + 1);
            visitor.visitRecord(records[rid]);

            // visit symbol
            visitor.visitSymbol(cipher, symbol);

            Agent[] agents = this.agents; // Atomic read.
            while (nagent > 0) {
                if (nagent >= agents.length)
                    break; // Concurrent subscription update - ignore broken chain.
                Agent agent = agents[nagent]; // Atomic read.
                if (agent == null)
                    break; // Concurrent subscription update - ignore broken chain.
                SubMatrix nsub = agent.sub; // Atomic read.
                if (nindex >= nsub.matrix.length || key != nsub.getInt(nindex) || rid != nsub.getInt(nindex + 1))
                    break; // Concurrent subscription update - ignore broken chain.

                // visit agent
                Integer aid_ext = aid_map.get(agent);
                if (aid_ext != null)
                    visitor.visitAgentAgain(aid_ext);
                else {
                    aid_ext = aid_map.size();
                    aid_map.put(agent, aid_ext);
                    visitor.visitAgentNew(aid_ext, agent.getStats().getKeyProperties());
                }

                // visit time
                if (hasTime)
                    visitor.visitTime(nsub.getInt(nindex + TIME_SUB), nsub.getInt(nindex + TIME_SUB_X));

                nagent = nsub.getInt(nindex + NEXT_AGENT);
                nindex = nsub.getInt(nindex + NEXT_INDEX);
            }
            visitor.visitEndOfChain(); // End-of-chain marker.
        }

        visitor.visitEndOfCollector();
    }

    // ========== Agent Delegation ==========

    // returns true if some records still remains in the agent, false if all accumulated records were retrieved.
    boolean retrieveData(Agent agent, RecordSink sink, boolean snapshotOnly) {
        try {
            return retrieveDataImpl(agent, sink, snapshotOnly);
        } catch (final Throwable error) {
            management.setFatalError(error);
            throw error;
        }
    }

    // returns true if some records still remains in the agent, false if all accumulated records were retrieved.
    // Ticker, Stream & History override with concrete implementations
    abstract boolean retrieveDataImpl(Agent agent, RecordSink sink, boolean snapshotOnly);

    // SYNC: global
    private void startSubChangeBatch(int notify) {
        subStepsRemaining = management.getSubscriptionBucket();
        subNotifyAccumulator = notify & ~NOTIFY_SUB_HAS_MORE;
    }

    // SYNC: global
    private int doneSubChangeBatch() {
        if (subStepsRemaining == 0) // no more steps in a batch
            subNotifyAccumulator |= NOTIFY_SUB_HAS_MORE; // means we have to do more
        return subNotifyAccumulator;
    }

    // SYNC: global+local
    private void subscriptionChangeComplete(Agent agent) {
        if (agent.reducedSub){
            rehashAgentIfNeeded(agent);
            rehashAgentIfNeeded(total);
            refilterStreamBuffersAfterSubscriptionChange(agent);
            mapper.rehashIfNeeded();
            agent.reducedSub = false;
        }
    }

    // SYNC: none
    private void notifySubChange(int notify, Agent agent) {
        if (TRACE_LOG)
            log.trace("notifySubChange" +
                ((notify & NOTIFY_SUB_TOTAL_ADDED) != 0 ? " TOTAL_ADDED" : "") +
                ((notify & NOTIFY_SUB_TOTAL_REMOVED) != 0 ? " TOTAL_REMOVED" : "") +
                ((notify & NOTIFY_SUB_SNAPSHOT_AVAILABLE) != 0 ? " SNAPSHOT_AVAILABLE" : "") +
                ((notify & NOTIFY_SUB_DATA_AVAILABLE) != 0 ? " DATA_AVAILABLE" : "") +
                " for " + agent);
        if ((notify & NOTIFY_SUB_TOTAL_ADDED) != 0)
            distributors.notifyAdded();
        if ((notify & NOTIFY_SUB_TOTAL_REMOVED) != 0)
            distributors.notifyRemoved();
        if ((notify & NOTIFY_SUB_SNAPSHOT_AVAILABLE) != 0)
            agent.notifySnapshotListener();
        if ((notify & NOTIFY_SUB_DATA_AVAILABLE) != 0)
            agent.notifyDataListener();
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: none
    int addSubscriptionPart(Agent agent, RecordSource source, int notify) {
        try {
            return addSubscriptionPartImpl(agent, source, notify);
        } catch (final Throwable error) {
            management.setFatalError(error);
            throw error;
        }
    }

    int addSubscriptionPartImpl(Agent agent, RecordSource source, int notify) {
        if (agent.isClosed())
            return 0; // bail out w/o waiting for lock if already closed
        globalLock.lock(CollectorOperation.ADD_SUBSCRIPTION);
        try {
            startSubChangeBatch(notify);
            addSubscriptionGLocked(agent, source);
            notify = doneSubChangeBatch();
        } finally {
            globalLock.unlock();
        }
        if ((notify & NOTIFY_SUB_HAS_MORE) != 0)
            return notify; // return non-zero result to indicate there's more to do
        notifySubChange(notify, agent);
        return 0;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    private void addSubscriptionGLocked(Agent agent, RecordSource source) {
        if (helpClose() || agent.isClosed())
            return;
        agent.localLock.lock(CollectorOperation.ADD_SUBSCRIPTION);
        try {
            addSubscriptionGLLocked(agent, source);
        } finally {
            agent.localLock.unlock();
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // returns true if there is more subscription in iterator
    // SYNC: global+local
    private void addSubscriptionGLLocked(Agent agent, RecordSource source) {
        // pre-increment sub_mod_count even if there are no subscription items in iterator to retain structure
        // consistency if this method crashes due to OOM in the middle of operation.
        agent.subModCount++;
        if (agent.performSetterCleanupSteps())
            return; // bail out for next batch, as no steps left here
        // addSubscription itself
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (EventFlag.REMOVE_SYMBOL.in(cur.getEventFlags())) {
                // remove
                removeSubInternal(agent, cur);
            } else {
                // add
                if (isSubAllowed(agent, cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                    addSubInternal(agent, cur, false);
            }
            if (--subStepsRemaining <= 0)
                return; // bail out for next batch, as no steps left here
        }
        // we are here if subscription change batch was fully completed
        subscriptionChangeComplete(agent);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: none
    int removeSubscriptionPart(Agent agent, RecordSource source, int notify) {
        try {
            return removeSubscriptionPartImpl(agent, source, notify);
        } catch (final Throwable error) {
            management.setFatalError(error);
            throw error;
        }
    }

    int removeSubscriptionPartImpl(Agent agent, RecordSource source, int notify) {
        if (agent.isClosed())
            return 0; // bail out w/o waiting for lock if already closed
        globalLock.lock(CollectorOperation.REMOVE_SUBSCRIPTION);
        try {
            startSubChangeBatch(notify);
            removeSubscriptionGLocked(agent, source);
            notify = doneSubChangeBatch();
        } finally {
            globalLock.unlock();
        }
        if ((notify & NOTIFY_SUB_HAS_MORE) != 0)
            return notify; // return non-zero result to indicate there's more to do
        notifySubChange(notify, agent);
        return 0;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    private void removeSubscriptionGLocked(Agent agent, RecordSource source) {
        if (helpClose() || agent.isClosed())
            return;
        agent.localLock.lock(CollectorOperation.REMOVE_SUBSCRIPTION);
        try {
            removeSubscriptionGLLocked(agent, source);
        } finally {
            agent.localLock.unlock();
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // returns true if there is more subscription in iterator
    private void removeSubscriptionGLLocked(Agent agent, RecordSource source) {
        // pre-increment sub_mod_count even if there are no subscription items in iterator to retain structure
        // consistency if this method crashes due to OOM in the middle of operation.
        agent.subModCount++;
        if (agent.performSetterCleanupSteps())
            return; // bail out for next batch, as no steps left here
        // removeSubscription itself
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            removeSubInternal(agent, cur);
            if (--subStepsRemaining <= 0)
                return; // bail out for next batch, as no steps left here
        }
        // we are here if subscription change batch was fully completed
        subscriptionChangeComplete(agent);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: none
    int setSubscriptionPart(Agent agent, RecordSource source, int notify) {
        try {
            return setSubscriptionPartImpl(agent, source, notify);
        } catch (final Throwable error) {
            management.setFatalError(error);
            throw error;
        }
    }

    int setSubscriptionPartImpl(Agent agent, RecordSource source, int notify) {
        if (agent.isClosed())
            return 0; // bail out w/o waiting for lock if already closed
        globalLock.lock(CollectorOperation.SET_SUBSCRIPTION);
        try {
            startSubChangeBatch(notify);
            setSubscriptionGLocked(agent, source);
            notify = doneSubChangeBatch();
        } finally {
            globalLock.unlock();
        }
        if ((notify & NOTIFY_SUB_HAS_MORE) != 0)
            return notify; // return non-zero result to indicate there's more to do
        notifySubChange(notify, agent);
        return 0;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    private void setSubscriptionGLocked(Agent agent, RecordSource source) {
        if (helpClose() || agent.isClosed())
            return;
        agent.localLock.lock(CollectorOperation.SET_SUBSCRIPTION);
        try {
            setSubscriptionGLLocked(agent, source);
        } finally {
            agent.localLock.unlock();
        }
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    private void setSubscriptionGLLocked(Agent agent, RecordSource source) {
        // pre-increment sub_mod_count even if there are no subscription items in iterator to retain structure
        // consistency if this method crashes due to OOM in the middle of operation.
        agent.subModCount++;
        // complete previously working setter first, before starting 1st phase of ours
        if (agent.performSetterCleanupSteps())
            return; // bail out for next batch, as no steps left here
        if ((subNotifyAccumulator & NOTIFY_SUB_PHASE2) == 0) {
            // setSubscription itself -- addSubscription (1st) phase
            RecordCursor cur;
            while ((cur = source.next()) != null) {
                if (isSubAllowed(agent, cur.getRecord(), cur.getCipher(), cur.getSymbol())) {
                    addSubInternal(agent, cur, true);
                    if (--subStepsRemaining <= 0)
                        return; // bail out for next batch, as no steps left here
                }
            }
            // addSubscription (1st) phase fully completes, now start cleanup (2nd) phase
            subNotifyAccumulator |= NOTIFY_SUB_PHASE2;
            agent.startSetterCleanup();
            if (agent.performSetterCleanupSteps())
                return; // bail out for next batch, as no steps left here
        }
        // we are here if subscription change batch was fully completed
        subscriptionChangeComplete(agent);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // Will examine data data and agent's subscription into sink.
    // SYNC: none
    int closeAgentPartImpl(Agent agent, RecordSink sink, int notify) {
        if (agent.isCloseCompleted())
            return 0; // quickly bailout w/o lock if close was already completed
        globalLock.lock(CollectorOperation.CLOSE_AGENT);
        try {
            startSubChangeBatch(notify);
            closeAgentGLocked(agent, sink);
            notify = doneSubChangeBatch();
        } finally {
            globalLock.unlock();
        }
        if ((notify & NOTIFY_SUB_HAS_MORE) != 0)
            return notify; // return non-zero result to indicate there's more to do
        notifySubChange(notify, agent);
        return 0;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    private void closeAgentGLocked(Agent agent, RecordSink sink) {
        if (!agent.isClosed()) {
            agent.startClose(sink); // immediately mark as being closed
            closingAgentsQueue.add(agent); // add to queue on the first invocation
        }
        helpClose();
    }

    /**
     * Helps to complete pending close operations under global lock.
     * @return {@code true} if there are remaining more agents to help close or {@code false} if
     *         closing of all pending agents is complete.
     */
    // SYNC: global
    private boolean helpClose() {
        while (subStepsRemaining > 0) {
            Agent agent = closingAgentsQueue.peek();
            if (agent == null)
                return false; // nothing to help close
            agent.localLock.lock(CollectorOperation.CLOSE_AGENT);
            try {
                closeAgentGLLocked(agent);
            } finally {
                agent.localLock.unlock();
            }
        }
        return true;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    private void closeAgentGLLocked(Agent agent) {
        // pre-increment sub_mod_count to retain structure
        // consistency if makeSteps crashes due to OOM in the middle of operation.
        agent.subModCount++;
        // this may still die due to to OOM, while modifying distributors added/removed lists
        if (agent.performCloseSteps())
            return; // more steps to do
        // we are done closing it
        agents[agent.number] = null;
    }

    // ========== QDCollector Implementation ==========

    boolean shouldStoreEverything(DataRecord record, int cipher, String symbol) {
        return storeEverything && storeEverythingFilter.accept(contract, record, cipher, symbol);
    }

    boolean shouldStoreEverything(int key, int rid) {
        // do not retrieve record, cipher and symbol if they are not needed
        return storeEverything && (storeEverythingFilter == QDFilter.ANYTHING ||
            storeEverythingFilter.accept(contract, records[rid], getCipher(key), getSymbol(key)));
    }

    // SYNC: none
    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        globalLock.lock(CollectorOperation.CREATE_AGENT);
        try {
            return createAgentGLocked(builder);
        } finally {
            globalLock.unlock();
        }
    }

    private QDAgent createAgentGLocked(QDAgent.Builder builder) {
        lastAgentIndex = ArrayUtil.findFreeIndex(agents, lastAgentIndex, MIN_AGENT_INDEX);
        if (lastAgentIndex >= agents.length)
            agents = ArrayUtil.grow(agents, 0);
        mapper.incMaxCounter(scheme.getRecordCount());
        // [QD-509] Do not track per-record stats for anonymous agents and distributors (reduces memory consumption)
        QDStats agentStats = stats.create(QDStats.SType.AGENT,
            builder.getKeyProperties(), builder.getKeyProperties() != null);
        return agents[lastAgentIndex] = createAgentInternal(lastAgentIndex, builder, agentStats);
    }

    // SYNC: none
    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        return distributors.createDistributor(this, builder);
    }

    // SYNC: none
    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return mapper.getMapping().getSymbolIfPresent(chars, offset, length);
    }

    @Override
    public void executeLockBoundTask(Executor executor, Runnable task) {
        lockBoundTaskQueue.add(executor, task);
    }

    @Override
    public void close() {
        management.removeCollector(this);
        stats.close();
    }

// ========== Implementation ==========

    /**
     * Shall return a combination of
     * {@link Notification#SNAPSHOT_BIT} and {@link Notification#UPDATE_BIT}.
     */
    // SYNC: local
    abstract int getNotificationBits(Agent agent);

    // SYNC: none
    final int getCipher(int key) {
        if ((key & SymbolCodec.VALID_CIPHER) == 0)
            return 0; // The subsequent getSymbol will check key validity.
        return key;
    }

    // SYNC: global or local
    final String getSymbol(int key) {
        if ((key & SymbolCodec.VALID_CIPHER) == 0)
            return mapper.getSymbol(key); // Also checks key validity.
        return null;
    }

    // SYNC: global or local
    final String getDecodedSymbol(int key) {
        if ((key & SymbolCodec.VALID_CIPHER) == 0)
            return mapper.getSymbol(key); // Also checks key validity.
        return scheme.getCodec().decode(key);
    }

    @Override
    public final DataRecord getRecord(int rid) {
        return records[rid];
    }

    final int getRid(DataRecord record) {
        int rid = record.getId();
        if (records[rid] == record)
            return rid;
        throw new IllegalArgumentException("Unknown record");
    }

    // SYNC: none
    final int getKey(int cipher, String symbol) {
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher");
            return mapper.getMapping().getKey(symbol);
        }
        return cipher;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    final int addKey(int cipher, String symbol) {
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0) {
            if (cipher != 0)
                throw new IllegalArgumentException("Reserved cipher");
            return mapper.addKey(symbol);
        }
        return cipher;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    final void rehashAgentIfNeeded(Agent agent) {
        if (Hashing.needRehash(agent.sub.shift, agent.sub.overallSize, agent.sub.payloadSize, Hashing.MAX_SHIFT))
            rehashAgent(agent);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    final void rehashAgent(Agent agent) {
        if (agent == total) {
            // total sub might contain expired items - let implementation to collect them and unmark payload flag
            prepareTotalSubForRehash();
            total.sub = total.sub.rehash(Hashing.MAX_SHIFT);
            // nothing more to do for total agent
            return;
        }
        // Remember old queue head pointers in agent
        int oldSnapshotHead = agent.snapshotQueue == null ? 0 : agent.snapshotQueue.getHead();
        int oldUpdateHead = agent.updateQueue == null ? 0 : agent.updateQueue.getHead();
        // rehash matrix
        SubMatrix osub = agent.sub;
        agent.sub = osub.rehash(Hashing.MAX_SHIFT);
        // Fix links: [PREV_AGENT].NEXT_INDEX must reference to new index.
        SubMatrix asub = agent.sub;
        for (int aindex = asub.matrix.length; (aindex -= asub.step) >= 0;) {
            int pagent = asub.getInt(aindex + PREV_AGENT) & PREV_AGENT_MASK;
            if (pagent == 0)
                continue;
            SubMatrix psub = agents[pagent].sub;
            int pindex = psub.getIndex(asub.getInt(aindex), asub.getInt(aindex + 1), 0);
            if (pindex == 0)
                throw new IllegalStateException("Previous agent misses entry");
            psub.setInt(pindex + NEXT_INDEX, aindex);
        }
        // Fix agent queues
        if (agent.snapshotQueue != null)
            agent.snapshotQueue.fixQueue(agent, oldSnapshotHead, osub, SNAPSHOT_QUEUE);
        if (agent.updateQueue != null)
            agent.updateQueue.fixQueue(agent, oldUpdateHead, osub, UPDATE_QUEUE);
    }

    // ================ OVERRIDE HOOKS ================

    // is overridden by Stream to properly support wildcards
    // is overridden by History to complain about non-history records
    protected boolean isSubAllowed(Agent agent, DataRecord record, int cipher, String symbol) {
        return agent.filter.getUpdatedFilter().accept(contract, record, cipher, symbol);
    }

    // is overridden by History to implement subscription filtering
    protected long trimSubTime(RecordCursor cur) {
        return cur.getTime();
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    void addSubInternal(Agent agent, RecordCursor cur, boolean setSub) {
        assert agent != total;

        /*
           Phase 0: Rehash if needed before operation starts to bail out early in case of OOM
           and do other memory allocations in advance.
        */

        rehashAgentIfNeeded(agent);
        rehashAgentIfNeeded(total);

        int rid = getRid(cur.getRecord());
        int key = addKey(cur.getCipher(), cur.getSymbol()); // this can also fail due to OOM
        long time = trimSubTime(cur);

        /*
           Phase 1: Use SubMatrix.addIndexBegin to find the index in agent and total subscriptions
           for this key, but don't yet write new key there. First we need to write
           all the other values, then write key. SubMatrix.addIndexComplete will take care of that
           at the end. This ensure that concurrent SubSnapshot can get a consistent view of newly
           added indices (without a risk of getting their rids or times as zero).
         */

        SubMatrix asub = agent.sub;
        int aindex = asub.addIndexBegin(key, rid);
        // Note, that in history contracts unsubscribed items that are still being processed are considered "payload",
        // so here is a direct check to see if this agent in doubly-linked lists
        boolean newSub = asub.getInt(aindex + PREV_AGENT) == 0;
        boolean wasPayload = asub.isPayload(aindex);

        SubMatrix tsub = total.sub;
        int tindex = tsub.addIndexBegin(key, rid);
        boolean sameSub = false;
        boolean totalRecordAdded = false;
        boolean reduceTimeTotal = false;
        long timeTotal = 0;

        /*
           Phase 2: Weave newly added subscription into double-liked lists
           and make appropriate adjustments to time and attachment
         */

        if (agent.hasAttachmentStrategy())
            asub.setObj(aindex, ATTACHMENT,
                newSub || setSub ? cur.getAttachment() : // copy attachment into new entry and on "setSub",
                    agent.updateAttachment( // combine with old otherwise
                        asub.getObj(aindex, ATTACHMENT), cur, false));

        if (newSub) { // add new sub entry to lists
            if (hasTime) {
                // initialize History fields
                asub.setInt(aindex + HISTORY_SUB_FLAGS, getHistoryTimeSubFlags(cur, time));
                asub.setLong(aindex + TIME_SUB, time);
                asub.setLong(aindex + TIME_KNOWN, Long.MAX_VALUE);
                asub.setLong(aindex + LAST_RECORD, 0);
            }
            // Insert 'agent' into proper double-list right after 'total'.
            int nagent = tsub.getInt(tindex + NEXT_AGENT);
            int nindex = tsub.getInt(tindex + NEXT_INDEX);
            asub.setInt(aindex + NEXT_AGENT, nagent > 0 ? nagent : 0); // beware of negative nagent in tsub
            asub.setInt(aindex + NEXT_INDEX, nindex);
            asub.setInt(aindex + PREV_AGENT, total.number);
            tsub.setInt(tindex + NEXT_AGENT, agent.number);
            tsub.setInt(tindex + NEXT_INDEX, aindex);
            if (nagent > 0) {
                SubMatrix nsub = agents[nagent].sub;
                int nset = nsub.getInt(nindex + PREV_AGENT) & PREV_AGENT_SET;
                nsub.setInt(nindex + PREV_AGENT, agent.number | nset);
                if (hasTime && time < tsub.getLong(tindex + TIME_TOTAL)) {
                    tsub.setLong(tindex + TIME_TOTAL, time);
                    totalRecordAdded = true;
                }
            } else {
                if (hasTime)
                    tsub.setLong(tindex + TIME_TOTAL, time);
                if (nagent == 0) // only add the record that did not really exist (was not a payload)
                    tsub.updateAddedPayload(rid);
                totalRecordAdded = true;
            }
            if (!wasPayload)
                asub.updateAddedPayload(rid);
        } else {
            // update to existing sub entry
            if (hasTime) {
                long timePrev = asub.getLong(aindex + TIME_SUB);
                boolean timeSubFlagsChanged = updateHistoryTimeSubFlags(cur, time, asub, aindex);
                sameSub = time == timePrev && !timeSubFlagsChanged;
                if (!sameSub) {
                    // time or flags changed
                    asub.setLong(aindex + TIME_SUB, time);
                    timeTotal = tsub.getLong(tindex + TIME_TOTAL);
                    if (time < timeTotal) {
                        tsub.setLong(tindex + TIME_TOTAL, time);
                        totalRecordAdded = true;
                    } else if (time > timePrev && timePrev == timeTotal) {
                        reduceTimeTotal = true;
                    }
                }
            } else
                sameSub = true; // without time it is always same sub
        }

        if (TRACE_LOG)
            log.trace("addSubInternal " + cur.getRecord().getName() + ":" + cur.getDecodedSymbol() + "@" + time +
                " setSub=" + setSub +
                " newSub=" + newSub +
                " wasPayload=" + wasPayload +
                " totalRecordAdded=" + totalRecordAdded +
                " reduceTimeTotal=" + reduceTimeTotal +
                " sameSub=" + sameSub +
                " for " + agent);

        if (setSub) {
            // mark this entry and "set" regardless of whether we had added it to the lists or it was there
            int pagent = asub.getInt(aindex + PREV_AGENT);
            asub.setInt(aindex + PREV_AGENT, pagent | PREV_AGENT_SET);
            // If nothing has changed in setSubscription, the we have nothing more to do (no sending data, etc)
            if (sameSub)
                return;
        }

        /*
           Phase 3: Now actually initialize key for newly added entries.
         */

        asub.addIndexComplete(aindex, key, rid);
        tsub.addIndexComplete(tindex, key, rid);

        /*
          Phase 4: Do the rest of subscription maintenance activities.
        */

        // INVARIANT: Cannot have both totalRecordAdd && reduceTimeTotal
        assert !(totalRecordAdded && reduceTimeTotal);

        if (totalRecordAdded)
            totalRecordAdded(key, rid, tsub, tindex, time);
        if (reduceTimeTotal) // Note: reduceTimeTotal will call totalRecordAdded after recomputing time
            reduceTimeTotal(key, rid, tsub, tindex, timeTotal);

        // Should send all Ticker & History data on new or updated subscription item
        enqueueAddedRecord(agent, asub, aindex);
    }

    private boolean updateHistoryTimeSubFlags(RecordCursor cur, long time, SubMatrix asub, int aindex) {
        int prevFlags = asub.getInt(aindex + HISTORY_SUB_FLAGS) & History.SNIP_TIME_SUB_FLAG;
        int newFlags = getHistoryTimeSubFlags(cur, time);
        // Always clear process version and pending counts when processing may is in progress
        // between phases while sub is changing
        asub.setInt(aindex + HISTORY_SUB_FLAGS, newFlags);
        return newFlags != prevFlags;
    }

    private int getHistoryTimeSubFlags(RecordCursor cur, long time) {
        return time == cur.getTime() ? 0 : History.SNIP_TIME_SUB_FLAG;
    }

    // is overridden by History to expire cached HistoryBuffer and unmark them as payload
    // SYNC: global+local
    void prepareTotalSubForRehash() {
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    void totalRecordAdded(int key, int rid, SubMatrix tsub, int tindex, long time) {
        if (distributors.addSub(key, rid, time))
            subNotifyAccumulator |= NOTIFY_SUB_TOTAL_ADDED;
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // This method is provide provided as Ticker & History hook
    // return true when the record is actually removed from total sub (history may keep it when storeEverything)
    // SYNC: global
    boolean totalRecordRemoved(int key, int rid, SubMatrix tsub, int tindex) {
        if (distributors.removeSub(key, rid))
            subNotifyAccumulator |= NOTIFY_SUB_TOTAL_REMOVED;
        return true;
    }

    // SYNC: global+local
    void enqueueAddedRecord(Agent agent, SubMatrix asub, int aindex) {
        // Provided for Ticker & History hook to add items to queues
    }

    // SYNC: global+local
    void dequeueRemovedRecord(Agent agent, SubMatrix asub, int aindex) {
        // Provided for Ticker & History hook to remove items from queues
    }

    // SYNC: global+local
    boolean keepInStreamBufferOnRefilter(Agent agent, RecordCursor cur) {
        // Provided for Stream hook
        return true;
    }

    /**
     * This method refilters buffers in Stream. This method should
     * not be called for closed agents, because their buffers go to the garbage
     * collector anyway.
     */
    // SYNC: global+local
    void refilterStreamBuffersAfterSubscriptionChange(Agent agent) {
        // Provided for Stream hook
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global+local
    void removeSubInternal(Agent agent, RecordCursor cur) {
        int key = getKey(cur.getCipher(), cur.getSymbol());
        int rid = getRid(cur.getRecord());
        SubMatrix asub = agent.sub;

        int aindex = asub.getIndex(key, rid, 0);
        int pagent = asub.getInt(aindex + PREV_AGENT) & PREV_AGENT_MASK;
        if (pagent == 0)
            return; // not subscribed
        if (agent.hasAttachmentStrategy()) {
            Object attachment = agent.updateAttachment(asub.getObj(aindex, ATTACHMENT), cur, true);
            asub.setObj(aindex, ATTACHMENT, attachment);
            if (attachment != null) // don't actually remove, but adjust attachment and return
                return;
        }
        removeSubInternalExisting(agent, asub, aindex, pagent, key, rid);
    }

    // SYNC: global+local
    void removeSubInternalExistingByIndex(Agent agent, int aindex, int pagent) {
        SubMatrix asub = agent.sub;
        removeSubInternalExisting(agent, asub, aindex, pagent,
            asub.getInt(aindex + KEY),
            asub.getInt(aindex + RID));
    }

    // SYNC: global+local
    void removeSubInternalExisting(Agent agent, SubMatrix asub, int aindex, int pagent, int key, int rid) {
        // make the actual remove as a last step, since it may crash due to OOM
        boolean totalRecordRemoved = false;
        // Delete 'agent' from proper double-list.
        SubMatrix psub = agents[pagent].sub;
        int pindex = psub.getIndex(key, rid, 0);
        if (pindex == 0)
            throw new IllegalStateException("Previous agent misses entry");
        int nagent = asub.getInt(aindex + NEXT_AGENT);
        int nindex = asub.getInt(aindex + NEXT_INDEX);
        psub.setInt(pindex + NEXT_AGENT, nagent);
        psub.setInt(pindex + NEXT_INDEX, nindex);
        asub.setInt(aindex + NEXT_AGENT, 0);
        asub.setInt(aindex + NEXT_INDEX, 0);
        asub.setInt(aindex + PREV_AGENT, 0);
        if (nagent > 0) {
            SubMatrix nsub = agents[nagent].sub;
            int nset = nsub.getInt(nindex + PREV_AGENT) & PREV_AGENT_SET;
            nsub.setInt(nindex + PREV_AGENT, pagent | nset);
        } else if (pagent == total.number) {
            totalRecordRemoved = true;
        }
        if (hasTime) // Special handling for History subscription remove
            removeSubInternalExistingTime(asub, aindex, key, rid, psub, pindex, nagent > 0 || pagent != total.number);
        agent.reducedSub = true;
        // remove payload counter is not a payload anymore (history can be still payload it is being processed)
        if (!asub.isPayload(aindex))
            asub.updateRemovedPayload(rid);

        if (TRACE_LOG)
            log.trace("removeSubInternal " + records[rid].getName() + ":" + getDecodedSymbol(key) +
                " totalRecordRemoved=" + totalRecordRemoved +
                " for " + agent);

        if (totalRecordRemoved) {
            if (totalRecordRemoved(key, rid, psub, pindex))
                psub.updateRemovedPayload(rid);
        }
        // Now cleanup Ticker & History queues
        dequeueRemovedRecord(agent, asub, aindex);
    }

    // Used for History only
    private void removeSubInternalExistingTime(SubMatrix asub, int aindex, int key, int rid,
        SubMatrix psub, int pindex, boolean moreAgents)
    {
        if (moreAgents) {
            // Other agents are still subscribed -- check if time needs to be recomputed
            SubMatrix tsub = total.sub;
            int tindex = tsub.getIndex(key, rid, 0);
            if (tindex == 0 || tsub.getInt(tindex + NEXT_AGENT) <= 0)
                throw new IllegalStateException("Total agent misses entry");
            long timeTotal = tsub.getLong(tindex + TIME_TOTAL);
            long timePrev = asub.getLong(aindex + TIME_SUB);
            if (timePrev == timeTotal) // recompute if it was minimal sub time
                reduceTimeTotal(key, rid, tsub, tindex, timeTotal);
        } else {
            psub.setLong(pindex + TIME_TOTAL, Long.MAX_VALUE); // no total subscription any more
        }
        // mark this agent as no longer subscribed and clear process version (to drop pending processing events)
        asub.setInt(aindex + HISTORY_SUB_FLAGS, 0);
        asub.setLong(aindex + TIME_SUB, Long.MAX_VALUE);
        asub.setLong(aindex + TIME_KNOWN, Long.MAX_VALUE);
        // Note: processing may be still in process while subscription is removed
    }

    // SYNC: global
    private void reduceTimeTotal(int key, int rid, SubMatrix tsub, int tindex, long timeTotal) {
        // assert at least one subscribed agent
        int nagent = tsub.getInt(tindex + NEXT_AGENT);
        int nindex = tsub.getInt(tindex + NEXT_INDEX);
        long time = Long.MAX_VALUE;
        while (nagent > 0) {
            SubMatrix nsub = agents[nagent].sub;
            long t = nsub.getLong(nindex + TIME_SUB);
            if (t < time) {
                if (t <= timeTotal)
                    return; // agent with previous old time exists -- nothing to change
                time = t;
            }
            nagent = nsub.getInt(nindex + NEXT_AGENT);
            nindex = nsub.getInt(nindex + NEXT_INDEX);
        }
        // set reduced time
        tsub.setLong(tindex + TIME_TOTAL, time);
        // the following call also trims extra records from HistoryBuffer
        totalRecordAdded(key, rid, tsub, tindex, time);
    }

    // extension point, ticker and history override
    // SYNC: global+local
    void examineSubDataInternalByIndex(Agent agent, int aindex, RecordSink sink) {}

    // SYNC: none
    boolean isSub(Agent agent, DataRecord record, int cipher, String symbol, long time, int timeOffset) {
        int key = getKey(cipher, symbol);
        int rid = getRid(record);
        SubMatrix asub = agent.sub; // Volatile read
        int index = asub.getVolatileIndex(key, rid, 0);
        /*
         * Potential word-tearing in getLong if subscription time is changed concurrently with this isSub
         * invocation, but we cannot really do anything about it.
         */
        return asub.isSubscribed(index) && (!hasTime || time >= asub.getLong(index + timeOffset));
    }

    // SYNC: none
    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return isSub(total, record, cipher, symbol, time, TIME_TOTAL);
    }

    // SYNC: none
    boolean examineSub(Agent agent, RecordSink sink) {
        return new SubSnapshot(agent, QDFilter.ANYTHING).retrieveSubscription(sink);
    }

    // SYNC: none
    @Override
    public boolean examineSubscription(RecordSink sink) {
        return examineSub(total, sink);
    }

    // SYNC: none
    @Override
    public int getSubscriptionSize() {
        return total.sub.payloadSize; // Racy read
    }

    // SYNC: none
    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        this.errorHandler = errorHandler; // Volatile write
    }

    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: none
    void processData(Distributor distributor, RecordSource source) {
        try {
            processDataImpl(distributor, source);
        } catch (final Throwable error) {
            management.setFatalError(error);
            throw error;
        }
    }

    void processDataImpl(Distributor distributor, RecordSource source) {
        Distribution dist = Distribution.getInstance();
        dist.prepareCounters(records.length);
        Notification notif = null;
        boolean make_more_pass;
        do {
            // Initialize distribution
            dist.start(agents, management.getDistributionBucket());
            try {
                // Phase 1: process input, build distribution.
                make_more_pass = processRecordSource(distributor, dist, source);
                onBetweenProcessPhases(); // for testing purposes only
                // Phase 2: update and notify agents.
                if (notif == null)
                    notif = Notification.getInstance();
                // loop phase two while any agent was blocked
                boolean blocked;
                do {
                    processDataUpdate(dist, notif, source);
                    blocked = dist.hasBlocked();
                    if (blocked) {
                        // send notification before blocking on the next pass
                        processDataNotify(notif);
                        notif.clear();
                        // flip blocked agents to queue
                        dist.enqueueBlocked();
                    }
                } while (blocked);
            } finally {
                // Clear distribution to start again (or to release it to the pool)
                dist.done();
            }
        } while (make_more_pass);
        dist.flushAndClearCounters(counters);
        dist.release();
        processDataNotify(notif);
        notif.release();
    }

    // ========== Utility methods for [Ticker|Stream|History].processData methods ==========

    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: none
    private boolean processRecordSource(Distributor distributor, Distribution dist, RecordSource source) {
        globalLock.lock(CollectorOperation.PROCESS_DATA);
        try {
            return processRecordSourceGLocked(distributor, dist, source);
        } finally {
            globalLock.unlock();
        }
    }

    // This method can try to allocate memory and die due to OutOfMemoryError.
    // SYNC: global
    abstract boolean processRecordSourceGLocked(Distributor distributor, Distribution dist, RecordSource source);

    /**
     * Processes data distribution of the specified agent.
     * @return positive index in the {@link Distribution} of the unprocessed record when there is not enough room in the buffer
     *         and processing should block. Zero result means that everything was processed successfully.
     */
    // SYNC: local
    abstract int processAgentDataUpdate(Distribution dist, RecordSource buffer, Agent agent);

    void processDataUpdate(Distribution dist, Notification notif, RecordSource buffer) {
        notif.ensureCapacity(dist.getMaxAgentNumber());
        Agent agent;
        int max_spins = management.getMaxDistributionSpins();
        int m_spins = 0;
        while ((agent = dist.firstAgent()) != null) {
            m_spins++;
            // During each loop determine how many agents can be 'ignored' on this pass and left for later passes.
            int ignorable = dist.numberOfAgents() * (max_spins - 1) / max_spins;
            while (agent != null) {
                if (ignorable <= 0) {
                    // 'ignorable' quota has ended - acquire blocking lock on remaining agents.
                    agent.localLock.lock(CollectorOperation.PROCESS_DATA);
                } else if (!agent.localLock.tryLock(CollectorOperation.PROCESS_DATA)) {
                    // Failed to lock agent from 'ignorable' quota - update quota and skip agent for this pass.
                    ignorable--;
                    agent = dist.nextAgent();
                    continue;
                }
                // The agent is locked (one way or another) - process it and remove from Distribution list.
                int blockIndex;
                try {
                    blockIndex = processDataUpdateLLocked(agent, dist, notif, buffer);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restore interrupted flag
                    blockIndex = 0; // remove agent (as if it was processed) if blocking was interrupted
                } finally {
                    agent.localLock.unlock();
                }
                agent = dist.removeAgent(blockIndex);
            }
        }
        dist.countSpins(m_spins);
    }

    // SYNC: local
    private int processDataUpdateLLocked(Agent agent, Distribution dist, Notification notif, RecordSource buffer)
        throws InterruptedException
    {
        int blockIndex = 0;
        if (!agent.isClosed()) {
            // wait on blocked agents before processing
            if (agent.buffer != null)
                while (agent.buffer.isBlocked())
                    agent.localLock.await();
            // now process
            int bits = getNotificationBits(agent);
            blockIndex = processAgentDataUpdate(dist, buffer, agent);
            bits = ~bits & getNotificationBits(agent);
            if (bits != 0)
                notif.add(agent, bits);
        }
        return blockIndex;
    }

    // SYNC: none
    void processDataNotify(Notification notif) {
        for (Agent agent = notif.firstAgent(); agent != null; agent = notif.nextAgent(agent)) {
            int bits = notif.getBits(agent);
            if ((bits & Notification.SNAPSHOT_BIT) != 0)
                agent.notifySnapshotListener();
            if ((bits & Notification.UPDATE_BIT) != 0)
                agent.notifyDataListener();
        }
    }

    // SYNC: local
    void countRetrieval(Agent agent) {
        if (agent.nRetrieved > 0) {
            counters.countRetrieval(agent.nRetrieved);
            agent.nRetrieved = 0;
        }
    }

    // ========== Debugging ==========

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(contract);
        if (stats != null)
            sb.append("[").append(stats.getFullKeyProperties()).append("]");
        else
            sb.append("@").append(Integer.toHexString(hashCode()));
        return sb.toString();
    }

    public void visitAgents(CollectorDebug.AgentVisitor av) {
        for (Agent agent : agents)
            if (agent != null)
                av.visitAgent(agent);
    }

    <T extends CollectorDebug.SymbolReferenceVisitor> T visitSymbols(T srv, CollectorDebug.RehashCrashInfo rci) {
        for (Agent agent : agents)
            if (agent != null)
                CollectorDebug.visitAgentSymbols(srv, rci, agent);
        distributors.visitDistributorsSymbols(srv);
        return srv;
    }

    public void verify(CollectorDebug.Log log, CollectorDebug.RehashCrashInfo rci) {
        log.info("--- Verifying " + this);
        log.info("Verifying symbols...");
        Mapper verifyMapper = new Mapper(this);
        visitSymbols(new CollectorDebug.VerifySymbolReferences(log, scheme, mapper, verifyMapper), rci)
            .printSummary();
        mapper.getMapping().verify(log, verifyMapper.getMapping());
        log.info("Verifying agents subscription...");
        CollectorDebug.VerifySubscription verifySubscription = new CollectorDebug.VerifySubscription(log, scheme, mapper);
        for (Agent agent : agents)
            if (agent != null && agent != total) {
                verifySubscription.agentNumber = agent.number;
                CollectorDebug.visitAgentSymbols(verifySubscription, rci, agent);
            }
        log.info("Found " + verifySubscription.totalSize + " entries in agents subscription");
        log.info("Verifying total subscription...");
        verifySubscription.verifyTotal(total.sub, agents);
        log.info("Verify completed");
    }

    public void analyzeQueue(CollectorDebug.Log log, String symbol, String record) {
        if (getContract() == QDContract.TICKER || getContract() == QDContract.HISTORY)
            analyzeQueue(log, symbol, record, "snapshot", SNAPSHOT_QUEUE);
        if (getContract() == QDContract.TICKER)
            analyzeQueue(log, symbol, record, "update", UPDATE_QUEUE);
    }

    public void analyzeQueue(CollectorDebug.Log log, String symbol, String record, String name, int offset) {
        log.info("-- Analyzing " + name + " queue" +
            (symbol != null ? " symbol " + symbol : "") +
            (record != null ? " record " + record : "") +
            " in " + this);
        int key = getKeyIfPresent(symbol);
        int rid = getRidIfPresent(record);
        for (Agent agent : agents)
            if (agent != null && agent != total)
                analyzeQueueImpl(log, key, rid, agent, name, offset);
        log.info("Analyze queue completed");
    }

    private void analyzeQueueImpl(CollectorDebug.Log log, int filterKey, int filterRid, Agent agent,
        String name, int offset)
    {
        SubMatrix sub = agent.sub;
        for (int index = sub.matrix.length; (index -= sub.step) >= 0;) {
            if (!sub.isPayload(index))
                continue;
            int queueNext = sub.getInt(index + offset);
            if ((queueNext & ~QUEUE_BIT) == 0 || offset == SNAPSHOT_QUEUE && (queueNext & QUEUE_BIT) == 0)
                continue; // not in queue
            int key = sub.getInt(index + KEY);
            int rid = sub.getInt(index + RID);
            if ((filterKey != -1 && filterKey != key) || (filterRid != -1 && filterRid != rid))
                continue; // not analyzing
            StringBuilder sb = new StringBuilder(
                "Found in " + name + " queue " + CollectorDebug.fmtKeyRid(scheme, mapper, key, rid) + " at " + index);
            if (hasTime) {
                sb.append(" time sub ").append(sub.getInt(index + TIME_SUB)).append(" ").append(sub.getInt(index + TIME_SUB_X));
                sb.append(" time known ").append(sub.getInt(index + TIME_KNOWN)).append(" ").append(sub.getInt(index + TIME_KNOWN_X));
                long lastRecordWithBit = sub.getLong(index + LAST_RECORD);
                if ((lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT) != 0)
                    sb.append(" TX_DIRTY");
                long lastRecord = lastRecordWithBit & TX_DIRTY_LAST_RECORD_BIT;
                if (lastRecord == 0)
                    sb.append(" not in buffer");
                else {
                    if (agent.buffer.isInBuffer(lastRecord)) {
                        sb.append(" still in buffer");
                    } else {
                        sb.append(" dropped from buffer");
                    }
                }
            }
            sb.append(" of ").append(agent);
            log.info(sb.toString());
        }
    }

    public void analyzeSymbolRefs(CollectorDebug.Log log, String symbol, String record,
        CollectorDebug.RehashCrashInfo rci) {
        log.info("-- Analyzing symbols refs" +
            (symbol != null ? " symbol " + symbol : "") +
            (record != null ? " record " + record : "") +
            " in " + this);
        int key = getKeyIfPresent(symbol);
        int rid = getRidIfPresent(record);
        visitSymbols(new CollectorDebug.AnalyzeKeyRid(log, key, rid, scheme, mapper), rci);
        log.info("Analyze completed");
    }

    private int getRidIfPresent(String record) {
        if (record == null)
            return -1;
        DataRecord r = scheme.findRecordByName(record);
        if (r != null)
            return r.getId();
        log.warn("Record is not found for " + record);
        return -1;
    }

    private int getKeyIfPresent(String symbol) {
        int key = symbol == null ? -1 : scheme.getCodec().encode(symbol);
        if (key != 0)
            return key;
        key = mapper.getMapping().getKey(symbol);
        if (key != 0)
            return key;
        log.warn("Key is not found for " + symbol);
        return -1;
    }

    // ============================ Test support methods  ============================

    // can be overriden in test code to deterministically do something between processing phases
    protected void onBetweenProcessPhases() {}

    void droppedLogAccept(String message) {
        if (droppedLog != null)
            droppedLog.accept(message);
    }
}
