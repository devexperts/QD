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

import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.RecordCounters;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.LockFreePool;

/**
 * The <code>Distribution</code> is an auxiliary data structure used during data distribution.
 */
final class Distribution {

    // ======================= static =======================

    private static final int N_AGENT_PROCESSORS = 4;

    private static final LockFreePool<Distribution> POOL =
        new LockFreePool<>(Distribution.class.getName(), 2 * Runtime.getRuntime().availableProcessors());

    static Distribution getInstance() {
        Distribution result = POOL.poll();
        return result == null ? new Distribution() : result;
    }

    // --------- list layout  ---------

    private static final int NEXT = 0; // index of the next item | flags
    private static final int PAYLOAD1 = 1;
    private static final int PAYLOAD2 = 2;

    private static final int N_INTS = 3;

    // --------- flags  ---------

    /*
     * Flags are used by History to keep additional information about distribution item.
     * They use upper bits.
     */

    // the following four flags are needed to force processing of various event types into 2nd phase

    /**
     * Set for last event in a transaction (either implicit or explicit).
     * Implies that {@code (eventFlags & TX_PENDING) == 0}.
     */
    static final int TX_END_DIST_FLAG = 1 << 31;

    /**
     * Set when updated a "snapshot snip" time while processing event.
     * Implies that {@code (eventFlags & SNAPSHOT_SNIP) != 0}.
     */
    static final int UPDATED_SNIP_DIST_FLAG = 1 << 30;

    /**
     * Set when this event turned on "snapshotBegin" flag in the history buffer or forced snapshot retransmit.
     * Need to reset KNOWN_TIME in agent. Implies that {@code (eventFlags & SNAPSHOT_BEGIN) != 0}.
     */
    static final int SEND_SNAPSHOT_DIST_FLAG = 1 << 29;

    /**
     * Set when record was updated in HB (includes remove of an existing one).
     */
    static final int UPDATED_RECORD_DIST_FLAG = 1 << 28;

    // the following two flags are informative of the state of HB in the 1st phase

    static final int TX_SWEEP_DIST_FLAG = 1 << 27; // when event is a part of implicit snapshot sweep transaction
    static final int HAD_SNAPSHOT_DIST_FLAG = 1 << 26; // when event was processed by HB in "wasSnapshotBeginSeen" state

    // the following flag is needed for a proper bookkeeping of pending count in agent sub HISTORY_SUB_FLAGS
    static final int DEC_PENDING_COUNT_DIST_FLAG = 1 << 25; // when PENDING_COUNT must be decremented in HISTORY_SUB_FLAGS after processing

    private static final int NEXT_MASK = (1 << 25) - 1; // to mask out distFlags

    // ======================= instance data =======================

    // --------- contract-special reusable objects  ---------

    private AgentProcessor[] processors; // Used by Ticker only
    private AgentIterator agentIterator; // Used by Stream and History only
    private RecordBuffer removeBuffer; // Used by History only

    private ProcessVersionTracker processVersionTracker; // Used by History only
    private int curProcessVersion; // Used by History only

    // --------- lists ---------

    private Agent[] sharedAgents; // [agent.number] -> shared array of agents from Collector at the start of distribution.
    private Agent[] agents; // [agent.number] -> cached agent at the start of distribution.
    private SubMatrix[] subs; // [agent.number] -> agent.sub at the start of distribution.
    private int[] subModCounts; // [agent.number] -> agent.sub_mod_count at the start of distribution.

    private int[] nextAffected; // [agent.number] -> next affected agent number; [0] -> first affected (head).
    private int numberAffected; // number of affected agents (queue length).
    private int prevAffected; // agent number of previously iterated agent or 0 for head (for removal).

    private int numberBlocked; // number of blocked agents (queue length).
    private int firstBlocked; // index of the first blocked agents or 0 if nothing blocked, use next_affected for a list of them

    private int[] heads; // [agent.number] -> first payload index for that agent.
    private int[] tails; // [agent.number] -> last payload index for that agent.

    private int[] lists = new int[512 * N_INTS]; // [index] -> next_index, [index + 1] -> 1st payload, [index + 2] -> 2nd payload
    private int size = N_INTS; // the first unused index in lists.
    private int sizeThreshold; // maximal size

    // --------- monitoring counters  ---------

    RecordCounters mIncomingRecords = new RecordCounters();
    RecordCounters mOutgoingRecords = new RecordCounters();
    int mPrevSize;
    int mSpins;

    // ======================= instance code =======================

    private Distribution() {} // only take instances via getInstance

    void release() {
        POOL.offer(this);
    }

    // --------- contract-special reusable objects  ---------

    /**
     * Returns associated agent processor for a specified interleave from 1 to 4.
     */
    AgentProcessor getProcessor(int interleave) {
        AgentProcessor[] processors = this.processors;
        if (processors == null)
            this.processors = processors = new AgentProcessor[N_AGENT_PROCESSORS];
        int index = Math.max(Math.min(interleave, N_AGENT_PROCESSORS) - 1, 0);
        AgentProcessor result = processors[index];
        if (result == null) {
            switch (index) {
            case 0:
                result = new AgentProcessor(this);
                break;
            case 1:
                result = new AgentProcessor2(this);
                break;
            case 2:
                result = new AgentProcessor3(this);
                break;
            case 3:
                result = new AgentProcessor4(this);
                break;
            default:
                throw new AssertionError(); // cannot happen
            }
            processors[index] = result;
        }
        return result;
    }

    AgentIterator getAgentIterator() {
        AgentIterator agentIterator = this.agentIterator;
        if (agentIterator != null)
            return agentIterator;
        return this.agentIterator = new AgentIterator();
    }

    RecordBuffer getRemoveBuffer() {
        RecordBuffer removeBuffer = this.removeBuffer;
        if (removeBuffer != null)
            return removeBuffer;
        return this.removeBuffer = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
    }

    void trackProcessVersion(ProcessVersionTracker processVersionTracker) {
        this.processVersionTracker = processVersionTracker;
        this.curProcessVersion = processVersionTracker.next();
    }

    int getCurProcessVersion() {
        return curProcessVersion;
    }

// --------- lists ---------

    int getMaxAgentNumber() {
        return sharedAgents.length - 1;
    }

    /**
     * Starts this distribution and ensures that it has capacity to work with specified agents.
     * Must be called before work begins.
     */
    void start(Agent[] shared_agents, int threshold) {
        this.sharedAgents = shared_agents;
        this.sizeThreshold = N_INTS + threshold * N_INTS;

        int length = agents == null ? 0 : agents.length;
        if (shared_agents.length <= length)
            return;
        length = Math.max(16, Math.max(length << 1, shared_agents.length));

        agents = new Agent[length];
        subs = new SubMatrix[length];
        subModCounts = new int[length];

        nextAffected = new int[length];

        heads = new int[length];
        tails = new int[length];
    }

    /**
     * Clears this distribution so it holds no references and is ready for next usage.
     * Must be called after work finishes.
     */
    void done() {
        if (processVersionTracker != null) {
            processVersionTracker.done(curProcessVersion);
            processVersionTracker = null;
        }
        if (agentIterator != null)
            agentIterator.clear();
        if (removeBuffer != null)
            removeBuffer.clear();
        sharedAgents = null;

        // Implementation note: 'clearing' methods only clear references to external objects
        // so that GC can eat them. For int-based lists only certain fields are cleared.
        // Other fields are properly cleared and filled when they are re-used again.
        // This is done for better cache hit and smaller CPU load.

        // Normally when 'clear' is called agent list is already cleared by 'removeAgent'.
        // So no waste of CPU here. Nevertheless, play it safe, re-iterate and clear.
        for (int i = nextAffected[0]; i > 0;) {
            int next = nextAffected[i];
            clear(i);
            i = next;
        }
        nextAffected[0] = 0;
        numberAffected = 0;
        prevAffected = 0;

        numberBlocked = 0;
        firstBlocked = 0;

        // 'lists' are actually cleared and filled during their allocation for better cache hit.
        size = N_INTS;

        mPrevSize = size(); // get ready to next countOutgoingRecords
    }

    private void block(int i, int blockIndex) {
        nextAffected[i] = firstBlocked;
        firstBlocked = i;
        numberBlocked++;
        heads[i] = blockIndex;
    }

    private void clear(int i) {
        agents[i] = null;
        subs[i] = null;
        nextAffected[i] = 0;
    }

    /**
     * Adds specified payload for specified agent.
     */
    // This method can try to allocate memory and die due to OutOfMemoryError.
    Agent add(int number, int payload1, int payload2, int flags, int rid) {
        assert (flags & NEXT_MASK) == 0; // only valid flags
        Agent agent = agents[number];
        if (agent == null) {
            agent = sharedAgents[number];
            if (agent.hasVoidRecordListener())
                return agent; // this agent does not need to queue/buffer any data -- skip it
            addNewAgent(number, agent);
        }
        if (size + PAYLOAD2 >= lists.length)
            lists = ArrayUtil.grow(lists, 0);
        lists[size + NEXT] = flags; // 0 | flags
        lists[size + PAYLOAD1] = payload1;
        lists[size + PAYLOAD2] = payload2;
        int tail = tails[number];
        int tailFlags = lists[tail + NEXT] & ~NEXT_MASK;
        lists[tail + NEXT] = size | tailFlags;
        tails[number] = size;
        size += N_INTS;
        mOutgoingRecords.count(rid);
        return agent;
    }

    Agent add(int number, long payload, int flags, int rid) {
        return add(number, (int) (payload >> 32), (int) payload, flags, rid);
    }

    boolean isDuplicate(int number, long payload) {
        if (agents[number] == null)
            return false;
        int tail = tails[number];
        return lists[tail + PAYLOAD1] == (int) (payload >> 32) && lists[tail + PAYLOAD2] == (int) payload;
    }

    void addFlagsToLastAdded(int flags) {
        assert (flags & NEXT_MASK) == 0; // only valid flags
        lists[size - N_INTS + NEXT] |= flags;
    }

    private void addNewAgent(int number, Agent agent) {
        agents[number] = agent;
        subs[number] = agent.sub;
        subModCounts[number] = agent.subModCount;

        nextAffected[number] = nextAffected[0];
        nextAffected[0] = number;
        numberAffected++;

        heads[number] = size;
        tails[number] = 0;
    }

    /**
     * Returns number of payloads in this distribution.
     */
    int size() {
        return (size / N_INTS) - 1;
    }

    boolean hasCapacity() {
        return size < sizeThreshold;
    }

    /**
     * Returns true if any agents are in the blocked list.
     */
    boolean hasBlocked() {
        return firstBlocked > 0;
    }


    /**
     * Puts blocked agents into the queue of affected agents again.
     * Queue must be empty to start with.
     */
    void enqueueBlocked() {
        if (numberAffected != 0)
            throw new IllegalStateException();
        nextAffected[0] = firstBlocked;
        firstBlocked = 0;
        numberAffected = numberBlocked;
        numberBlocked = 0;
    }

    /**
     * Returns number of affected agents currently in the list.
     */
    int numberOfAgents() {
        return numberAffected;
    }

    /**
     * Returns first affected agent or null if none.
     */
    Agent firstAgent() {
        prevAffected = 0;
        return agents[nextAffected[prevAffected]];
    }

    /**
     * Returns next affected agent or null if none.
     * This method does not remove last iterated agent from 'affected' list.
     */
    Agent nextAgent() {
        prevAffected = nextAffected[prevAffected];
        return agents[nextAffected[prevAffected]];
    }

    /**
     * Removes last iterated agent from 'affected' list and returns next affected agent or null if none.
     * This method shall be used as part of iteration in place of {@link #nextAgent} method.
     * @param blockIndex when positive, then agent's unprocessed record will be added to the blocked
     *                    list from the specified index.
     */
    Agent removeAgent(int blockIndex) {
        int current = nextAffected[prevAffected];
        if (current == 0)
            throw new IllegalStateException("No agent to remove.");
        int next = nextAffected[current];
        if (blockIndex > 0)
            block(current, blockIndex);
        else
            clear(current);
        numberAffected--;
        nextAffected[prevAffected] = next;
        return agents[next];
    }

    /**
     * Returns stored sub-matrix of specified agent.
     */
    SubMatrix getSub(Agent agent) {
        return subs[agent.number];
    }

    /**
     * Returns stored sub-mod-count of specified agent.
     */
    int getSubModCount(Agent agent) {
        return subModCounts[agent.number];
    }

    /**
     * Returns first index for specified agent or 0 if none.
     */
    int firstIndex(Agent agent) {
        return heads[agent.number];
    }

    /**
     * Returns next index or 0 if none.
     */
    int nextIndex(int index) {
        return lists[index + NEXT] & NEXT_MASK;
    }

    /**
     * Returns 1st payload for specified index.
     */
    int getPayload1(int index) {
        return lists[index + PAYLOAD1];
    }

    /**
     * Returns 2nd payload for specified index.
     */
    int getPayload2(int index) {
        return lists[index + PAYLOAD2];
    }

    /**
     * Returns 1st and 2nd payload combined into long.
     */
    long getPayloadLong(int index) {
        return ((long) getPayload1(index) << 32) | (getPayload2(index) & 0xffffffffL);
    }

    /**
     * Returns flags for specified index.
     */
    int getFlags(int index) {
        return lists[index + NEXT];
    }

    // --------------------- monitoring ---------------------

    public void prepareCounters(int nRecords) {
        mIncomingRecords.prepare(nRecords);
        mOutgoingRecords.prepare(nRecords);
        mPrevSize = size();
    }

    public void countIncomingRecord(int rid) {
        mIncomingRecords.count(rid);
    }

    public void countSpins(int mSpins) {
        this.mSpins += mSpins;
    }

    void flushAndClearCounters(CollectorCounters counters) {
        counters.countDistributionAndClear(mIncomingRecords, mOutgoingRecords, mSpins);
        mSpins = 0;
    }

}
