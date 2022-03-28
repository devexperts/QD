/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.impl.AbstractAgent;
import com.devexperts.qd.impl.matrix.management.CollectorOperation;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import static com.devexperts.qd.impl.matrix.Collector.PREV_AGENT;
import static com.devexperts.qd.impl.matrix.Collector.PREV_AGENT_MASK;
import static com.devexperts.qd.impl.matrix.Collector.PREV_AGENT_SET;
import static com.devexperts.qd.impl.matrix.Collector.TIME_SUB;
import static com.devexperts.qd.impl.matrix.Collector.TOTAL_AGENT_INDEX;

/**
 * The <code>Agent</code> is a matrix-based implementation of {@link QDAgent}.
 * <p>
 * The <code>Agent</code> holds a single matrix with current subscription.
 * Payload subscription entries are linked into subscription structure
 * maintained by {@link Collector}. Removed subscription entries are left
 * unlinked until rehash actually removes them from the matrix or they are
 * reclaimed back. See {@link Collector} for full description.
 */
final class Agent extends AbstractAgent {
    private static final int STATE_ACTIVE = Integer.MAX_VALUE;
    private static final int STATE_CLOSE_COMPLETE = 0;

    final Collector collector; // Parent collector.
    final LocalLock localLock; // Protects read/write of queue list. Effectively blocks structural modifications.
    final int number; // Number of this agent in the collector. Always positive.
    final QDFilter filter; // @NotNull
    private final boolean hasVoidRecordListener;
    final QDStats stats;

    private volatile int state = STATE_ACTIVE; // SYNC: write(global), read(none)
    private RecordSink closeSink; // SYNC: global, record sink that gets data while agent is being closed
    private volatile AgentSnapshotProvider snapshotProvider; // SYNC: write(local), read(none)
    private volatile RecordListener snapshotListener; // SYNC: write(local), read(none)
    private volatile RecordListener dataListener; // SYNC: write(local), read(none)

    /**
     * Next agent in the closing queue.
     * @see ClosingAgentsQueue
     */
    Agent nextClosingAgent; // SYNC: rw(global).

    // Current subscription of this agent. See Collector.
    volatile SubMatrix sub; // SYNC: structure(global+local), crosslink(global), rw(local)

    // Subscription modification count.
    int subModCount; // SYNC: write(global+local), read(any)

    final RecordCursorKeeper retrievalKeeper; // SYNC: rw(local). Used by regular agents of ticker
    final AgentQueue snapshotQueue; // SYNC: rw(local). Used by regular agents of Ticker & History.
    final AgentQueue updateQueue; // SYNC: rw(local). Used by regular agents of Ticker.
    final AgentBuffer buffer; // SYNC: rw(local). Used by regular agents Stream & History.

    /*
     * Note on nSnapshotRetrieved balance counter for History:
     *   nSnapshotRetrieved - decreases on each record retrieved from agent buffer
     *                      - increases on each record retrieved from HistoryBuffer
     *   Invariant:
     *      0 <= nSnapshotRetrieved <= History.RETRIEVE_BATCH_SIZE
     *         |                     |
     *         |                     +- when equals, retrieve from agent buffer only
     *         +- when equals, retrieve from HB only
     */

    int nSnapshotHistoryRem; // SYNC: rc(local) -- history only, the remaining size of snapshot batch for next retrieve
    int nSnapshotRetrieved; // SYNC: rc(local) -- balance counter for Ticker & History: last no retrieved from snapshot
    int nRetrieved; // SYNC: rw(local) -- total number of records retrieved from monitoring purposes

    /**
     * Index of the second phase of the ongoing {@code setSubscription} operation or zero.
     * When this field is non-zero subscription cannot be changed ({@link #performSetterCleanupSteps() first!),
     * because this index will become invalid if subscription matrix rehashes.
     */
    private int setterCleanupIndex; // SYNC: rw(global+local)

    /**
     * This field is set to true when subscription was reduced or changed
     * to remember to rehash subscription and/or to refilter outgoing data buffers.
     * It is cleared when rehash and refilter completes.
     */
    boolean reducedSub; // SYNC: rw(global+local)

    // SYNC: global+local (local is virtually held due to confinement in creating thread)
    Agent(Collector collector, int number, Builder builder, QDStats stats) {
        super(collector.getContract(), builder);
        if (number <= 0)
            throw new IllegalArgumentException("Agent number must be positive.");
        this.collector = collector;
        this.localLock = collector.globalLock.newLocalLock();
        this.number = number;
        this.filter = builder.getFilter();
        this.hasVoidRecordListener = builder.hasVoidRecordListener();
        this.stats = stats;

        if (hasVoidRecordListener)
            dataListener = RecordListener.VOID;

        boolean regular = number > TOTAL_AGENT_INDEX; // Non-total agent
        boolean ticker = collector.getContract() == QDContract.TICKER;
        boolean stream = collector.getContract() == QDContract.STREAM;

        this.retrievalKeeper = regular && ticker ? new RecordCursorKeeper() : null;
        this.snapshotQueue = regular && !stream ? new AgentQueue() : null;
        this.updateQueue = regular && ticker ? new AgentQueue() : null;
        this.buffer = regular && !ticker ? collector.createAgentBuffer(this) : null;
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    // ========== internal state management ==========

    // SYNC: none
    boolean isClosed() {
        return state != STATE_ACTIVE;
    }

    // SYNC: none
    boolean isCloseCompleted() {
        return state == STATE_CLOSE_COMPLETE;
    }

    // SYNC: global
    void startClose(RecordSink sink) {
        state = sub.matrix.length;
        closeSink = sink;
    }

    /**
     * Partially closes agent by removing at most {@link Collector#subStepsRemaining} subscription items.
     * @return {@code true} if more steps remain to be done and {@code false} if closing is complete.
     */
    // SYNC: global+local
    boolean performCloseSteps() {
        int index = state;
        if (index == 0)
            return false;
        while ((index -= sub.step) > 0) {
            int pagent = sub.getInt(index + PREV_AGENT);
            if (pagent == 0)
                continue;
            if (closeSink != null)
                collector.examineSubDataInternalByIndex(this, index, closeSink);
            collector.removeSubInternalExistingByIndex(this, index, pagent & PREV_AGENT_MASK);
            if (--collector.subStepsRemaining <= 0) {
                state = index;
                return true;
            }
        }
        // we're done closing it
        // NOte, that queues should have become empty, because of the overloaded recordRemoved methods
        if (buffer != null)
            buffer.clear();
        sub.close();
        collector.mapper.decMaxCounter(collector.getScheme().getRecordCount());
        state = STATE_CLOSE_COMPLETE;
        return false;
    }

    // SYNC: global+local
    void startSetterCleanup() {
        setterCleanupIndex = sub.matrix.length;
    }

    /**
     * Partially performs cleanup (2nd) phase of {@code setSubscription} operation by removing at most
     * {@link Collector#subStepsRemaining} subscription items.
     * @return {@code true} if more steps remain to be done and {@code false} if setter cleanup is complete.
     */
    // SYNC: global+local
    boolean performSetterCleanupSteps() {
        int index = setterCleanupIndex;
        if (index == 0)
            return false;
        while ((index -= sub.step) > 0) {
            int pagent = sub.getInt(index + PREV_AGENT);
            if (pagent == 0)
                continue;
            if ((pagent & PREV_AGENT_SET) != 0) {
                sub.setInt(index + PREV_AGENT, pagent & PREV_AGENT_MASK);
            } else {
                collector.removeSubInternalExistingByIndex(this, index, pagent);
                if (--collector.subStepsRemaining <= 0) {
                    setterCleanupIndex = index;
                    return true;
                }
            }
        }
        setterCleanupIndex = 0;
        return false;
    }

    // SYNC: local
    private boolean setSnapshotListenerLLocked(RecordListener listener) {
        if (this.snapshotListener == listener) // Volatile read
            return false;
        this.snapshotListener = listener; // Volatile write
        return (collector.getNotificationBits(this) & Notification.SNAPSHOT_BIT) != 0;
    }

    // SYNC: local
    private boolean setDataListenerLLocked(RecordListener listener) {
        if (this.dataListener == listener) // Volatile read
            return false;
        this.dataListener = listener; // Volatile write
        return (collector.getNotificationBits(this) & Notification.UPDATE_BIT) != 0;
    }

    boolean hasVoidRecordListener() {
        return dataListener == RecordListener.VOID;
    }

    // SYNC: none
    void notifySnapshotListener() {
        notifyListener(snapshotListener, snapshotProvider);
    }

    // SYNC: none
    void notifyDataListener() {
        notifyListener(dataListener, this);
    }

    // SYNC: none
    private void notifyListener(RecordListener listener, RecordProvider provider) {
        if (listener != null && !isClosed())
            try {
                listener.recordsAvailable(provider);
            } catch (Throwable t) {
                collector.errorHandler.handleDataError(this, t);
            }
    }

    // ========== QDAgent Implementation ==========

    @Override
    public RecordProvider getSnapshotProvider() {
        AgentSnapshotProvider snapshotProvider = this.snapshotProvider;
        if (snapshotProvider != null)
            return snapshotProvider;
        localLock.lock(CollectorOperation.CONFIG_AGENT);
        try {
            return getSnapshotProviderLLocked();
        } finally {
            localLock.unlock();
        }
    }

    // SYNC: local
    private RecordProvider getSnapshotProviderLLocked() {
        AgentSnapshotProvider snapshotProvider = this.snapshotProvider;
        if (snapshotProvider != null)
            return snapshotProvider;
        return this.snapshotProvider = new AgentSnapshotProvider(this);
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        return collector.retrieveData(this, sink, false);
    }

    void setSnapshotListener(RecordListener listener) {
        localLock.lock(CollectorOperation.CONFIG_AGENT);
        try {
            if (!setSnapshotListenerLLocked(listener))
                return;
        } finally {
            localLock.unlock();
        }
        notifySnapshotListener();
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        if (hasVoidRecordListener && listener != null && listener != RecordListener.VOID)
            throw new IllegalArgumentException("only VOID listener is allowed");
        localLock.lock(CollectorOperation.CONFIG_AGENT);
        try {
            if (!setDataListenerLLocked(listener))
                return;
        } finally {
            localLock.unlock();
        }
        notifyDataListener();
    }

    @Override
    public void addSubscription(RecordSource source) {
        int res = 0;
        do {
            res = addSubscriptionPart(source, res);
        } while (res != 0);
    }

    @Override
    public int addSubscriptionPart(RecordSource source, int notify) {
        return collector.addSubscriptionPart(this, source, notify);
    }

    @Override
    public void removeSubscription(RecordSource source) {
        int res = 0;
        do {
            res = removeSubscriptionPart(source, res);
        } while (res != 0);
    }

    @Override
    public int removeSubscriptionPart(RecordSource source, int notify) {
        return collector.removeSubscriptionPart(this, source, notify);
    }

    @Override
    public void setSubscription(RecordSource source) {
        int res = 0;
        do {
            res = setSubscriptionPart(source, res);
        } while (res != 0);
    }

    @Override
    public int setSubscriptionPart(RecordSource source, int notify) {
        return collector.setSubscriptionPart(this, source, notify);
    }

    // SYNC: none
    @Override
    public void close() {
        closeImpl(null);
    }

    // SYNC: none
    @Override
    public int closePart(int notify) {
        return closePartImpl(null, notify);
    }

    // SYNC: none
    @Override
    public void closeAndExamineDataBySubscription(RecordSink sink) {
        if (sink == null)
            throw new NullPointerException();
        closeImpl(sink);
    }

    // SYNC: none
    private void closeImpl(RecordSink sink) {
        int res = 0;
        do {
            res = closePartImpl(sink, res);
        } while (res != 0);
    }

    // SYNC: none
    private int closePartImpl(RecordSink sink, int notify) {
        int res = collector.closeAgentPartImpl(this, sink, notify);
        // close stats as the last operation
        if (res == 0) {
            if (buffer != null)
                buffer.closeStats();
            sub.closeStats();
            stats.close();
        }
        return res;
    }

    // SYNC: none
    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return collector.isSub(this, record, cipher, symbol, time, TIME_SUB);
    }

    // SYNC: none
    @Override
    public boolean examineSubscription(RecordSink sink) {
        return collector.examineSub(this, sink);
    }

    // SYNC: none
    @Override
    public int getSubscriptionSize() {
        return sub.payloadSize;
    }

    @Override
    public void setMaxBufferSize(int maxBufferSize) {
        super.setMaxBufferSize(maxBufferSize); // just check
        if (buffer == null)
            return;
        localLock.lock(CollectorOperation.CONFIG_AGENT);
        try {
            buffer.setBufferSizeLLocked(maxBufferSize);
        } finally {
            localLock.unlock();
        }
    }

    @Override
    public void setBufferOverflowStrategy(BufferOverflowStrategy bufferOverflowStrategy) {
        super.setBufferOverflowStrategy(bufferOverflowStrategy); // just check
        if (buffer == null)
            return;
        localLock.lock(CollectorOperation.CONFIG_AGENT);
        try {
            buffer.setBufferOverflowStrategyLLocked(bufferOverflowStrategy);
        } finally {
            localLock.unlock();
        }
    }

    // ========== Debugging ==========

    @Override
    public String toString() {
        return "agent #" + number +
            (stats != null ? " [" + stats.getFullKeyProperties() + "]" : "") +
            " of " + collector;
    }
}
