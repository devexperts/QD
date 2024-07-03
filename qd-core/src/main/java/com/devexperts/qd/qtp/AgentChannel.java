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
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordFilter;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Single channel of QD data within agent adapter.
 */
class AgentChannel implements RecordListener {

    // ======================================== private static constants ========================================

    /*
     * Subscription processing modes for the current configuration.
     */

    private static final byte SUB_NO_FILTER = 0; // there is no filter (completeSubscriptionFilter == QDFilter.ANYTHING)
    private static final byte SUB_FILTER_AGENT = 1; // agent filters subscription under its global lock (fast sub, not keeping rejected)
    private static final byte SUB_FILTER_PROCESS = 2; // initial processing thread filters subscription outside of lock (fast sub)
    private static final byte SUB_FILTER_EXECUTOR = 3; // execution threads filters subscription outside of lock (slow sub check!)

    /*
     * When hasSubscriptionExecutor() is true, then all subscription-changing actions are offloaded into a background
     * thread. Each action is represented by SubAction class and they are kept in SubActionQueue. The ActionQueue class
     * provides a guarantee that at most one action is performed at any give time.
     *
     * Otherwise, subscription changing actions are performed in the invoking thread and synchronized by this to
     * guarantee serial execution.
     */

    private static final byte ACTION_CLOSE = 0; // close agent (LOCK BOUND)
    private static final byte ACTION_RECONFIGURE_1 = 1; // phase1 of reconfigure -- capture and filter old sub, then phase2 or phase3
    private static final byte ACTION_RECONFIGURE_2 = 2; // phase2 of reconfigure -- change sub and done (LOCK BOUND)
    private static final byte ACTION_RECONFIGURE_3 = 3; // phase3 of reconfigure -- close old agent, then phase4 (LOCK BOUND)
    private static final byte ACTION_RECONFIGURE_4 = 4; // phase4 of reconfigure -- create new agent and addSub (LOCK BOUND)
    private static final byte ACTION_ADD_SUB_FILTER = 5; // filter for add sub (used for slow filters = SUB_FILTER_EXECUTOR)
    private static final byte ACTION_REMOVE_SUB_FILTER = 6; // filter for remove sub (used for slow filters = SUB_FILTER_EXECUTOR)
    private static final byte ACTION_ADD_SUB = 7; // actual add sub (LOCK BOUND)
    private static final byte ACTION_REMOVE_SUB = 8; // actual remove sub (LOCK BOUND)

    /**
     * Constants for {@link #dataAvailableState}.
     */

    private static final int DATA_NOT_AVAILABLE = 0;
    private static final int DATA_AVAILABLE = 1;
    private static final int DATA_WAIT = 2;
    private static final int DATA_WAIT_AVAILABLE = DATA_WAIT | DATA_AVAILABLE;

    private static final AtomicIntegerFieldUpdater<AgentChannel> DATA_AVAILABLE_STATE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AgentChannel.class, "dataAvailableState");

    // ======================================== helper classes ========================================

    private static class SubAction {
        Config config; // Configuration for this action
        byte action; // One of ACTION_XXX constants
        RecordBuffer sub; // may be null for actions that don't need it.
        int notify; // result of the previous xxxPart operation (for add/removeSubscriptionPart and closePart)
    }

    /**
     * This is a synchronized queue of SubAction elements that also knows how to
     * append action to the last not taken (yet) element.
     */
    private class SubActionQueue implements Runnable {
        // pooled object to make it all garbage-free for a synchronous case
        private SubAction pooledAction;

        // true when a runnable task was scheduled or is already running
        private boolean scheduled;

        // true when a runnable task is running (for assertions only)
        private boolean running;

        // typically, due to merging, it contains at most one item
        private ArrayDeque<SubAction> queue = new ArrayDeque<>(2);

        @Override
        public void run() {
            SubAction next = null;
            try {
                SubAction current = poll();
                if (current == null)
                    return;
                next = processSubAction(current);
                if (next == null)
                    pooledAction = current;
            } finally {
                finish(next);
            }
        }

        private synchronized SubAction poll() {
            assert scheduled && !running;
            running = true;
            return queue.poll();
        }

        private synchronized void finish(SubAction next) {
            assert scheduled && running;
            running = false;
            if (!isClosed() && next != null)
                queue.addFirst(next); // next action to execute goes to the head of the queue
            if (queue.isEmpty())
                scheduled = false;
            else if (hasSubscriptionExecutor())
                scheduleInExecutor();
        }

        private void scheduleIfNeeded() {
            assert Thread.holdsLock(this);
            if (scheduled)
                return;
            scheduled = true;
            if (hasSubscriptionExecutor())
                scheduleInExecutor();
            else
                runInPlace(); // without subscription executor just run task in place
        }

        private void scheduleInExecutor() {
            assert Thread.holdsLock(this);
            assert !queue.isEmpty();
            assert hasSubscriptionExecutor();
            QDCollector collector = getSubActionCollector(queue.getFirst());
            if (collector == null)
                shaper.getSubscriptionExecutor().execute(this); // regular task (does filtering, etc)
            else
                collector.executeLockBoundTask(shaper.getSubscriptionExecutor(), this); // LOCK BOUND task
        }

        private void runInPlace() {
            assert Thread.holdsLock(AgentChannel.this) && Thread.holdsLock(this) && !hasSubscriptionExecutor();
            while (scheduled)
                run();
        }

        // this method is for new actions without buffer
        synchronized void addAction(Config config, byte action) {
            assert action == ACTION_RECONFIGURE_1 || action == ACTION_CLOSE;
            if (isClosed())
                return;
            // overwrite config in the last queued action when it is of the same type
            // this will merge repeated queued reconfiguration requests (which are still in phase1) together
            if (!queue.isEmpty()) {
                SubAction last = queue.getLast();
                if (last.action == action) {
                    last.config = config;
                    return; // and return
                }
            }
            // add new item to the queue
            SubAction a = getSubActionInstance();
            a.config = config;
            a.action = action;
            a.sub = null;
            a.notify = 0;
            queue.add(a);
            scheduleIfNeeded();
        }

        synchronized void addActionAndConsumeBuffer(Config config, byte action, RecordBuffer sub) {
            if (isClosed())
                return;
            // Only try to merge buffers when this buffer was not filled up to capacity
            if (sub.hasCapacity() && !queue.isEmpty()) {
                SubAction last = queue.getLast();
                if (last.config == config && last.action == action) {
                    while (last.sub.hasCapacity()) {
                        RecordCursor cur = sub.next();
                        if (cur == null) {
                            // merged everything into the last action -- release sub and that's it
                            sub.release();
                            return; // it should be already scheduled, not need to do anything else
                        }
                        last.sub.append(cur);
                    }
                }
            }
            // Append remaining as a new action
            SubAction a = getSubActionInstance();
            a.config = config;
            a.action = action;
            a.sub = sub;
            a.notify = 0;
            queue.add(a);
            scheduleIfNeeded();
        }

        synchronized void addActionAndCopySource(Config config, byte action, RecordSource source) {
            if (isClosed())
                return;
            RecordBuffer sub = null; // copy to here
            // Try to merge into last item in the queue
            if (!queue.isEmpty()) {
                SubAction last = queue.getLast();
                if (last.config == config && last.action == action && last.sub.hasCapacity())
                    sub = last.sub; // will merge into sub
            }
            // Now copy from source to sub
            RecordCursor cur;
            while ((cur = source.next()) != null) {
                if (sub == null) {
                    // allocate new capacity-limited buffer
                    sub = RecordBuffer.getInstance(source.getMode());
                    sub.setCapacityLimited(true);
                    // allocate and add new action
                    SubAction a = getSubActionInstance();
                    a.config = config;
                    a.action = action;
                    a.sub = sub;
                    a.notify = 0;
                    queue.add(a);
                }
                sub.append(cur);
                if (!sub.hasCapacity())
                    sub = null; // that's it for this buffer
            }
            scheduleIfNeeded();
        }

        synchronized void addActionListToHeadAndConsumeBuffers(Config config, byte action, List<RecordBuffer> subList) {
            assert running; // only used inside the running action
            if (isClosed())
                return;
            for (int i = subList.size(); --i >= 0;) {
                RecordBuffer sub = subList.get(i);
                // allocate and add new action
                SubAction a = getSubActionInstance();
                a.config = config;
                a.action = action;
                a.sub = sub;
                a.notify = 0;
                queue.addFirst(a); // add to head (!!!)
            }
        }

        private SubAction getSubActionInstance() {
            SubAction a = pooledAction;
            if (a == null)
                a = new SubAction();
            else
                pooledAction = null;
            return a;
        }

        // Close action overwrites all other actions
        synchronized void addCloseAction() {
            queue.clear();
            addAction(CLOSED_CONFIG, ACTION_CLOSE);
        }

        private boolean isClosed() {
            assert Thread.holdsLock(this);
            return !queue.isEmpty() && queue.getFirst().action == ACTION_CLOSE;
        }
    }

    // Agent channel configuration.
    // It has final field for everything and it is captured from ChannelShaper that can change.
    private static class Config {
        final QDCollector collector;
        final QDFilter subscriptionFilter;  // not-null; shaper.getSubscriptionFilter
        final QDFilter completeSubscriptionFilter; // not-null; also includes filter received from remote peer
        final byte subFilterMode; // where completeSubscriptionFilter is checked? see SUB_FILTER_XXX
        final long aggregationPeriod;

        Config(QDCollector collector, QDFilter subscriptionFilter, QDFilter completeSubscriptionFilter,
            byte subFilterMode, long aggregationPeriod)
        {
            this.collector = collector;
            this.subscriptionFilter = subscriptionFilter;
            this.completeSubscriptionFilter = completeSubscriptionFilter;
            this.subFilterMode = subFilterMode;
            this.aggregationPeriod = aggregationPeriod;
        }

        boolean hasAggregationPeriod() {
            return aggregationPeriod != 0;
        }
    }

    // A configuration and a corresponding agent that is active for it
    private static class AgentConfig {
        final Config config;
        final QDAgent agent;

        private AgentConfig(Config config, QDAgent agent) {
            this.config = config;
            this.agent = agent;
        }
    }

    // Magic constant for closed channel
    private static final Config CLOSED_CONFIG = new Config(null, null, null, SUB_NO_FILTER, 0);

    private static class FilteringRecordProvider extends AbstractRecordProvider {
        private final FilteringRecordSink filteringSink;
        private RecordProvider dataSource;

        FilteringRecordProvider() {
            filteringSink = new FilteringRecordSink();
        }

        void set(RecordProvider dataSource, SubscriptionFilter subscriptionFilter, RecordFilter dataFilter) {
            this.dataSource = dataSource;
            filteringSink.set(subscriptionFilter, dataFilter);
        }

        @Override
        public RecordMode getMode() {
            return dataSource.getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            try {
                filteringSink.recordSink = sink;
                return dataSource.retrieve(filteringSink);
            } finally {
                filteringSink.recordSink = null;
            }
        }
    }

    private static class FilteringRecordSink extends AbstractRecordSink {
        private SubscriptionFilter subscriptionFilter;
        private RecordFilter dataFilter;

        RecordSink recordSink;

        FilteringRecordSink() {}

        void set(SubscriptionFilter subscriptionFilter, RecordFilter dataFilter) {
            this.subscriptionFilter = subscriptionFilter;
            this.dataFilter = dataFilter;
        }

        @Override
        public boolean hasCapacity() {
            return recordSink.hasCapacity();
        }

        @Override
        public void append(RecordCursor cursor) {
            if ((subscriptionFilter == null || subscriptionFilter.acceptRecord(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol())) &&
                (dataFilter == null || dataFilter.accept(cursor)))
            {
                recordSink.append(cursor);
            }
        }
    }

    // ======================================== instance fields ========================================

    // ---------------------- config ----------------------

    final AgentChannels.Owner owner;
    final ChannelShaper shaper;

    // ---------------------- subscription (pending change) ----------------------

    /**
     * subActionConfig -- current sub action configuration.
     *
     * 1. When hasSubscriptionExecutor() is true, then subActionConfig is changed under lock in the original thread
     *    and a separate thread actually performs subscription action using the config in SubAction and updates
     *    reference to the agent as needed.
     * 2. Otherwise, it is updated and the subscription change is applied under lock.
     */
    private volatile Config subActionConfig; // Guarded by this on write
    private final SubActionQueue subActionQueue = new SubActionQueue();

    // ---------------------- subscription (applied) ----------------------

    /**
     * agentConfig -- current agent's configuration for data processing.
     *
     * 1. When hasSubscriptionExecutor() is true, then it is updated in a separate thread when the corresponding
     *    SubAction is processed from the queue and when agent reference changes.
     * 2. Otherwise, it is updated under lock at the same time when subActionConfig changes.
     */
    private volatile AgentConfig agentConfig; // initially null, assigned to _before_ initializing agent listeners
    private QDAgent rejectedAgent; // keeps rejected subscription (if needed), it is set only once, SYNC(this)

    // ---------------------- data ----------------------

    private FilteringRecordProvider filteringRecordProvider;

    private volatile boolean snapshotIsAvailable;

    /**
     * {@link #DATA_NOT_AVAILABLE}   -- data is not available, notify when becomes available;
     * {@link #DATA_AVAILABLE}       -- data is available, already notified;
     * {@link #DATA_WAIT}            -- data is not available, do not notify when becomes available (wait until next retrieve time);
     * {@link #DATA_WAIT_AVAILABLE}  -- data is available, was not notified (wait until next retrieve time).
     */
    private volatile int dataAvailableState = DATA_NOT_AVAILABLE;

    /**
     * Last retrieve time + period, moves to next period when all data from prev period was retrieved.
     * It is only used in {@link #DATA_WAIT} and {@link #DATA_WAIT_AVAILABLE} states of {@link #dataAvailableState},
     * and is reset to zero in other states. It is updated only by {@link #retrieveSnapshotOrData} method.
     */
    private volatile long nextDataTime;

    double quota; // in range [0..1], 1 means channel can send data; directly accessed from AgentAdapter

    // ======================================== creation ========================================

    AgentChannel(AgentChannels.Owner owner, ChannelShaper shaper) {
        this.owner = owner;
        this.shaper = shaper;
        subActionConfig = createNewConfig();
        shaper.bind(this);
    }

    private boolean hasSubscriptionExecutor() {
        return shaper.hasSubscriptionExecutor(); // it is a fixed property of shaper
    }

    private Config createNewConfig() {
        QDFilter subscriptionFilter = shaper.getSubscriptionFilter();
        //TODO peerFilter should be part of the ChannelShaper
        QDFilter completeSubscriptionFilter =
            CompositeFilters.makeAnd(owner.getPeerFilter(shaper.getContract()), subscriptionFilter);
        byte subFilterMode;
        if (completeSubscriptionFilter == QDFilter.ANYTHING) {
            subFilterMode = SUB_NO_FILTER;
        } else if (hasSubscriptionExecutor()) {
            // With subscription executor check fast filters before execution directly in the processing thread,
            // to potentially save on context switch if there are not matching sub items
            subFilterMode = completeSubscriptionFilter.isFast() ?
                SUB_FILTER_PROCESS : SUB_FILTER_EXECUTOR;
        } else {
            // Without subscription executor check fast filter under agent's global lock when we don't need to keep rejected stuff
            // (default behavior for ordinary filters that are typically used in mux),
            // but move slow filters outside the global lock (still taking processing thread, though)
            subFilterMode = completeSubscriptionFilter.isFast() && !shaper.isKeepRejected() ?
                SUB_FILTER_AGENT : SUB_FILTER_PROCESS;
        }
        return new Config(shaper.getCollector(), subscriptionFilter, completeSubscriptionFilter,
            subFilterMode, shaper.getAggregationPeriod());
    }

    // ======================================== (re)configuration ========================================

    // This method is used in assertions only
    private boolean underLockOrInSubActionThread() {
        if (hasSubscriptionExecutor())
            return !Thread.holdsLock(this) && subActionQueue.running;
        else
            return Thread.holdsLock(this);
    }

    // call from shaper update
    void reconfigureIfNeeded() {
        // quick lock-free check if there is any need to reconfigure (double check under lock)
        if (needToReconfigure())
            reconfigureIfNeededSync(); // synchronize this
    }

    // assign CLOSED_CONFIG value to the subActionConfig field only during synchronization by this object
    private boolean needToReconfigure() {
        Config config = this.subActionConfig; // Atomic read
        return config != CLOSED_CONFIG &&
            (config.collector != shaper.getCollector() ||
            config.subscriptionFilter != shaper.getSubscriptionFilter() ||
            config.aggregationPeriod != shaper.getAggregationPeriod());
    }

    // sync call from sub-change or unsync from shaper update
    private synchronized void reconfigureIfNeededSync() {
        if (!needToReconfigure())
            return; // no actual reconfiguration needed
        if (isChannelClosed())
            return;
        // Capture current configuration change request
        subActionConfig = createNewConfig();
        subActionQueue.addAction(subActionConfig, ACTION_RECONFIGURE_1);
    }

    private SubAction processReconfigurePhase1Action(final SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.sub == null;
        if (agentConfig == null)
            initNewAgentIfNeeded(a.config);
        Config oldConfig = agentConfig.config;
        boolean collectorChanged = oldConfig.collector != a.config.collector;
        boolean filterChanged =  !oldConfig.completeSubscriptionFilter.equals(a.config.completeSubscriptionFilter) ||
            oldConfig.subFilterMode != a.config.subFilterMode;
        boolean aggregationChanged = oldConfig.aggregationPeriod != a.config.aggregationPeriod;
        if (!collectorChanged && !filterChanged && !aggregationChanged) {
            // nothing actually changed -- it was just a fluke (filter instance might have changed without substance)
            agentConfig = new AgentConfig(a.config, agentConfig.agent);
            filterChanged = true;
        }
        if (!collectorChanged && !filterChanged) {
            // update aggregation period change
            updateAgentAggregation(a.config);
            return null; // nothing more to do
        }
        // Figure out what is the next phase
        // When filtering is not inside agent and collector is still the same,
        // so we can just find filtered out sub on agent and go to phase2 to change it
        if (!collectorChanged && oldConfig.subFilterMode != SUB_FILTER_AGENT && a.config.subFilterMode != SUB_FILTER_AGENT) {
            reconfigurePhase1Light(a);
            return null;
        }
        // Radical change -- will need to create agent from scratch in phase 3 and addSub on it in phase 4
        // Create full subscription snapshot. No optimization here -- put it all into one huge buffer
        QDContract contract = shaper.getContract();
        final RecordBuffer reincarnationBuffer =
            RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract));
        final RecordBuffer rejectionBuffer = !shaper.isKeepRejected() ? null :
            RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract));
        // Now figure out who does filtering of subscription and filter in-place if needed
        RecordSink sink = reincarnationBuffer;
        switch (a.config.subFilterMode) {
        case SUB_FILTER_EXECUTOR:
        case SUB_FILTER_PROCESS:
            // do it right here
            sink = new AbstractRecordSink() {
                @Override
                public void append(RecordCursor cur) {
                    if (a.config.completeSubscriptionFilter.accept(shaper.getContract(), cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                        reincarnationBuffer.append(cur);
                    else if (rejectionBuffer != null)
                        rejectionBuffer.append(cur);
                }
            };
            break;
        default:
            // when keepRejected is true, the mode is either SUB_FILTER_EXECUTOR or SUB_FILTER_PROCESS
            assert rejectionBuffer == null;
        }
        // now examine all sub (including previously rejected) with/without filtering
        agentConfig.agent.examineSubscription(sink);
        if (shaper.isKeepRejected()) {
            if (rejectedAgent != null)
                rejectedAgent.examineSubscription(sink);
            // and immediately update rejected agent using resulting rejectionBuffer
            if (!rejectionBuffer.isEmpty())
                getOrCreateRejectedAgent().setSubscription(rejectionBuffer);
            rejectionBuffer.release();
        }
        // make the actual phase3 in a separate task
        a.action = ACTION_RECONFIGURE_3;
        a.sub = reincarnationBuffer;
        return a;
    }

    private void reconfigurePhase1Light(final SubAction a) {
        // The same agent is kept, so update its aggregation right here
        updateAgentAggregation(a.config);
        // Create subscription snapshot and filter it in-place into a chain of buffers for removeSub in phase2
        // Optimization here -- put sub change into capacity-limited chunks
        final List<RecordBuffer> changeSubList = new ArrayList<>();
        // figure out which sub items need to be removed
        agentConfig.agent.examineSubscription(new AbstractRecordSink() {
            RecordBuffer sub; // current buffer
            QDContract contract = shaper.getContract();
            @Override
            public void append(RecordCursor cur) {
                if (a.config.completeSubscriptionFilter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                    return; // the record is still accepted by the filter -- nothing to do
                if (sub == null) {
                    sub = RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract).withEventFlags());
                    sub.setCapacityLimited(true);
                    changeSubList.add(sub);
                }
                sub.add(cur).setEventFlags(EventFlag.REMOVE_SYMBOL.flag()); // flag to remove sub
                if (!sub.hasCapacity())
                    sub = null; // add nothing more to this buffer
            }
        });
        if (shaper.isKeepRejected())
            processReconfigurePhase1LightRejectedAgent(a, changeSubList);
        if (!changeSubList.isEmpty())
            subActionQueue.addActionListToHeadAndConsumeBuffers(a.config, ACTION_RECONFIGURE_2, changeSubList);
    }

    private void processReconfigurePhase1LightRejectedAgent(final SubAction a, final List<RecordBuffer> changeSubList) {
        // reexamine previously rejected subscription and figure out which sub items need to be added
        if (rejectedAgent != null)
            rejectedAgent.examineSubscription(new AbstractRecordSink() {
                RecordBuffer sub; // current buffer
                QDContract contract = shaper.getContract();
                @Override
                public void append(RecordCursor cur) {
                    if (!a.config.completeSubscriptionFilter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol()))
                        return; // the record is still rejected by the filter -- nothing to do
                    if (sub == null) {
                        sub = RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract));
                        sub.setCapacityLimited(true);
                        changeSubList.add(sub);
                    }
                    sub.append(cur);
                    if (!sub.hasCapacity())
                        sub = null; // add nothing more to this buffer
                }
            });
        // now immediately add removed subs to rejected agent and remove added (reverse remove flag)
        QDContract contract = shaper.getContract();
        for (RecordBuffer sub : changeSubList) {
            RecordBuffer rejectedSub = RecordBuffer.getInstance(
                RecordMode.addedSubscriptionFor(contract).withEventFlags());
            RecordCursor cur;
            while ((cur = sub.next()) != null) {
                RecordCursor rejectedCur = rejectedSub.add(cur);
                rejectedCur.setEventFlags(EventFlag.REMOVE_SYMBOL.in(cur.getEventFlags()) ?
                    0 : EventFlag.REMOVE_SYMBOL.flag());
            }
            sub.rewind();
            if (!rejectedSub.isEmpty())
                getOrCreateRejectedAgent().addSubscription(rejectedSub);
            rejectedSub.release();
        }
    }

    // Phase 2 of reconfiguration -- change subscription (add / remove)
    private SubAction processReconfigurePhase2Action(SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.sub != null;
        assert agentConfig != null; // reconfigureNow makes this check and bails out if agent is not created yet
        // change subscription (there is a mix of adds and removes in there)
        a.notify = agentConfig.agent.addSubscriptionPart(a.sub, a.notify);
        if (a.notify != 0) // has more addSub steps to do
            return a;
        // done -- release buffer
        a.sub.release();
        return null;
    }

    // Phase 3 of reconfiguration -- close old agent
    private SubAction processReconfigurePhase3Action(SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.sub != null;
        assert agentConfig != null; // reconfigureNow makes this check and bails out if agent is not created yet
        // Collector has changed or need to switch to filtering in/out of agent -- close old agent
        a.notify = agentConfig.agent.closePart(a.notify);
        if (a.notify != 0) // has more close steps to do
            return a;
        // create new agent in a separate task
        a.action = ACTION_RECONFIGURE_4;
        return a;
    }

    // Phase 4 of reconfiguration -- create new agent
    private SubAction processReconfigurePhase4Action(SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.sub != null;
        assert agentConfig != null; // reconfigureNow makes this check and bails out if agent is not created yet
        if (a.notify == 0) { // Create new agent on first invocation
            initNewAgent(a.config);
            // bail out if no need to restore subscription
            if (a.sub.isEmpty()) {
                a.sub.release();
                return null;
            }
        }
        a.notify = agentConfig.agent.addSubscriptionPart(a.sub, a.notify);
        if (a.notify != 0) // has more addSub steps to do
            return a;
        // done -- release buffer
        a.sub.release();
        return null;
    }

    /**
     * Creates agent for the specified configuration.
     */
    private QDAgent createAgent(Config config) {
        assert underLockOrInSubActionThread();
        QDCollector collector = config.collector;
        return collector == null ?
            // create void agent just to keep subscription if actual collector is null
            owner.createVoidAgent(shaper.getContract()) :
            // create real agent if collector is defined
            owner.createAgent(collector,
            config.subFilterMode == SUB_FILTER_AGENT ? config.completeSubscriptionFilter : QDFilter.ANYTHING);
    }

    private synchronized QDAgent getOrCreateRejectedAgent() {
        if (rejectedAgent != null)
            return rejectedAgent;
        // create rejected agent (if needed)
        return rejectedAgent = owner.createVoidAgent(shaper.getContract());
    }

    final synchronized void close() {
        if (isChannelClosed())
            return;
        shaper.close(); // immediately close shaper (stop tracking dynamic filters)
        subActionConfig = CLOSED_CONFIG;
        subActionQueue.addCloseAction();
    }

    private boolean isChannelClosed() {
        return subActionConfig == CLOSED_CONFIG;
    }

    final synchronized void closeAndExamineDataBySubscription(RecordBuffer buf) {
        if (isChannelClosed())
            return;
        shaper.close(); // immediately close shaper (stop tracking dynamic filters)
        subActionConfig = CLOSED_CONFIG;
        if (agentConfig == null)
            return; // nothing to do -- agent was not created yet
        QDAgent agent = agentConfig.agent;
        // update agent's configuration (first time)
        if (agentConfig.config != CLOSED_CONFIG)
            agentConfig = new AgentConfig(CLOSED_CONFIG, agent);
        agent.closeAndExamineDataBySubscription(buf);
    }

    // ======================================== data retrieval ========================================

    // NOTE: This method can be invoked concurrently with itself and with retrieveSnapshotOrData and nextRetrieveTime
    @Override
    public void recordsAvailable(RecordProvider provider) {
        AgentConfig agentConfig = this.agentConfig; // volatile read current config, it cannot be null
        QDAgent agent = agentConfig.agent;
        if (provider == agent) {
            // data update
            int oldState;
            do { // CAS loop for dataAvailableState
                oldState = dataAvailableState;
                if ((oldState & DATA_AVAILABLE) != 0)
                    return; // we already know that data is available -- nothing to do
            } while (!DATA_AVAILABLE_STATE_UPDATER.compareAndSet(this, oldState, oldState | DATA_AVAILABLE));
            if ((oldState & DATA_WAIT) != 0)
                return; // Do not send notification -- wait until next retrieve time comes
            // otherwise, on DATA_NOT_AVAILABLE -> DATA_AVAILABLE transition send notification to the listener
            owner.recordsAvailable();
        } else if (agentConfig.config.hasAggregationPeriod() && provider == agent.getSnapshotProvider()) {
            // snapshot
            if (snapshotIsAvailable)
                return;
            snapshotIsAvailable = true;
            owner.recordsAvailable(); // hasSnapshotOrDataForNow is now true !!!
        }
    }

    boolean hasSnapshotOrDataForNow(long currentTime) {
        return snapshotIsAvailable || hasDataForNow(currentTime);
    }

    private boolean hasDataForNow(long currentTime) {
        switch (dataAvailableState) {
        case DATA_NOT_AVAILABLE: return false;
        case DATA_AVAILABLE: return true;
        case DATA_WAIT: return false;
        case DATA_WAIT_AVAILABLE: return currentTime >= nextDataTime;
        default: throw new IllegalStateException();
        }
    }

    // This method is never invoked concurrently, but it can be concurrent with recordsAvailable.
    // Note, that this method is the only method that updates nextDataTime.
    boolean retrieveSnapshotOrData(long currentTime) {
        AgentConfig agentConfig = this.agentConfig; // volatile read current config, it cannot be null
        Config config = agentConfig.config;
        QDAgent agent = agentConfig.agent;
        //  see com.devexperts.qd.impl.matrix.Ticker.retrieveDataLLLocked method when we retrieve data, we also retrieve
        //  a snapshot, and if the snapshot has been processed, the next call to retrieve the snapshot
        //  under if "snapshotIsAvailable" will not call the event listener
        if (hasDataForNow(currentTime)) {
            // this is protective measure, so that time "resets" in non-wait modes, thus making code more robust in presence of clock jumps
            if (dataAvailableState == DATA_AVAILABLE)
                nextDataTime = 0;
            // We are about to retrieveFromProvider, so we reset DATA_AVAILABLE and set DATA_WAIT based on the presence of period
            long period = config.aggregationPeriod;
            dataAvailableState = period <= 0 ? DATA_NOT_AVAILABLE : DATA_WAIT;
            // Perform the retrieve
            boolean result = true;
            try {
                result = retrieveFromProvider(config, agent);
            } finally {
                // Analyze retrieve result
                if (result) {
                    // has more data -- update state with DATA_AVAILABLE flag, so hasDataForNow will continue to return true
                    dataAvailableState = period <= 0 ? DATA_AVAILABLE : DATA_WAIT_AVAILABLE;
                } else {
                    // Note: we put nextDataTime=0 in case period <= 0, because we don't want to be affected by backward time jumps when period is not set
                    if (period <= 0) {
                        // dataAvailableState was already set to DATA_NOT_AVAILABLE.
                        nextDataTime = 0;
                    } else {
                        /*
                         * dataAvailableState was already set to DATA_WAIT, not update nextDataTime.
                         * We try to keep precise timing here as long as the retrieveData was not delayed for too much (half of period).
                         * Note, that here currentTime >= prevTime, or we would not have been here because !hasDataForNow.
                         */
                        if (currentTime < nextDataTime + period / 2)
                            nextDataTime = nextDataTime + period; // was not delayed too much -- keep precise period
                        else
                            nextDataTime = currentTime + period; // was delayed too much (or first time) -- schedule from current time
                    }
                }
            }
            // Return retrieve result
            return result;
        }
        if (snapshotIsAvailable) {
            // data is waiting its time-slice, so we'll retrieve only snapshot (without aggregation)
            snapshotIsAvailable = false; // It is set to false only when we are about to retrieveFromProvider
            boolean result = true;
            try {
                result = retrieveFromProvider(config, agent.getSnapshotProvider());
            } finally {
                if (result) {
                    snapshotIsAvailable = true;
                }
            }
            return result;
        }
        return false; // false alarm
    }

    // NOTE: This method can be invoked concurrently with itself and with retrieveSnapshotOrData and recordsAvailable
    long nextRetrieveTime(long currentTime) {
        if (snapshotIsAvailable)
            return 0;
        while (true) { // CAS loop for dataAvailableState
            int oldState = dataAvailableState;
            switch (oldState) {
            case DATA_NOT_AVAILABLE:
                return Long.MAX_VALUE; // will notify when data becomes available
            case DATA_AVAILABLE:
                return 0; // must retrieve now
            case DATA_WAIT:
                /*
                 * This is how the waiting logic switches back to notification:
                 * When we've waited until nextDataTime and there's still no data, we just
                 * switch to DATA_NOT_AVAILABLE state and until data becomes available.
                 */
                long nextTime = nextDataTime;
                if (currentTime < nextTime)
                    return nextTime;
                if (!DATA_AVAILABLE_STATE_UPDATER.compareAndSet(this, oldState, DATA_NOT_AVAILABLE))
                    continue; // retry CAS loop
                return Long.MAX_VALUE; // state was set to DATA_NOT_AVAILABLE, will wait until recordsAvailable notification comes in
            case DATA_WAIT_AVAILABLE:
                return nextDataTime; // there is data, must retrieve when time comes
            default:
                throw new IllegalStateException();
            }
        }
    }

    // This method is never invoked concurrently
    // supports filtering (!)
    private boolean retrieveFromProvider(Config config, RecordProvider provider) {
        try {
            // if config.subFilterMode != SUB_FILTER_AGENT, then completeSubscriptionFilter was not used in createAgentAndUpdateConfig
            // So, stream contract with wildcard support needs additional data filtering (all other contracts are precise on sub)
            QDFilter remainingSubscriptionFilter = config.subFilterMode != SUB_FILTER_AGENT &&
                (config.collector instanceof QDStream && ((QDStream) config.collector).getEnableWildcards()) ?
                config.completeSubscriptionFilter : null;
            RecordFilter dataFilter = shaper.getDataFilter();
            if (remainingSubscriptionFilter != null && remainingSubscriptionFilter != QDFilter.ANYTHING || dataFilter != null) {
                if (filteringRecordProvider == null)
                    filteringRecordProvider = new FilteringRecordProvider();
                filteringRecordProvider.set(provider, remainingSubscriptionFilter, dataFilter);
                provider = filteringRecordProvider;
            }
            return owner.retrieveData(provider, shaper.getContract());
        } finally {
            if (filteringRecordProvider != null)
                filteringRecordProvider.set(null, null, null);
        }
    }

    // ======================================== subscription processing ========================================

    synchronized void processSubscription(RecordSource source, boolean isSubscriptionAdd) {
        // check if configuration needs to be updated
        reconfigureIfNeededSync();
        // read current config
        Config config = subActionConfig;
        if (config == CLOSED_CONFIG)
            return; // nothing to do when closed
        byte action = config.subFilterMode == SUB_FILTER_EXECUTOR ?
            (isSubscriptionAdd ? ACTION_ADD_SUB_FILTER : ACTION_REMOVE_SUB_FILTER) :
            (isSubscriptionAdd ? ACTION_ADD_SUB : ACTION_REMOVE_SUB);
        if (config.subFilterMode == SUB_FILTER_PROCESS) { // filter subscription right here in this mode
            filterSubscriptionAndAddActions(source, config, action);
            return;
        }
        subActionQueue.addActionAndCopySource(config, action, source);
    }

    private void filterSubscriptionAndAddActions(RecordSource source, Config config, byte action) {
        assert action == ACTION_ADD_SUB || action == ACTION_REMOVE_SUB;
        // Make sure to split filtered subscription into limited size chunks in case it comes in larger piece
        RecordBuffer sub = null; // will take it from pool only when needed (when filter accepts)
        RecordBuffer rejectedSub = null; // will take it from pool only when needed (when filter rejects)
        QDContract contract = shaper.getContract();
        RecordCursor cur;
        while ((cur = source.next()) != null) {
            if (!config.completeSubscriptionFilter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol())) {
                if (shaper.isKeepRejected()) {
                    if (rejectedSub == null)
                        rejectedSub = RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract).withEventFlags());
                    rejectedSub.append(cur);
                }
                continue; // continue when filter does not accept
            }
            if (sub == null) {
                sub = RecordBuffer.getInstance(source.getMode());
                sub.setCapacityLimited(true); // limit capacity (!)
            }
            sub.append(cur);
            if (!sub.hasCapacity()) { // don't add anymore to this buffer -- add action
                subActionQueue.addActionAndConsumeBuffer(subActionConfig, action, sub);
                sub = null;
            }
        }
        if (sub != null)
            subActionQueue.addActionAndConsumeBuffer(subActionConfig, action, sub);
        if (rejectedSub != null) {
            switch (action) {
            case ACTION_ADD_SUB:
                getOrCreateRejectedAgent().addSubscription(rejectedSub);
                break;
            case ACTION_REMOVE_SUB:
                if (rejectedAgent != null)
                    rejectedAgent.removeSubscription(rejectedSub);
                break;
            default:
                assert false;
            }
            rejectedSub.release();
        }
    }

    // Return != null collector for LOCK BOUND actions
    QDCollector getSubActionCollector(SubAction a) {
        switch (a.action) {
        case ACTION_CLOSE:
        case ACTION_RECONFIGURE_2:
        case ACTION_RECONFIGURE_3:
        case ACTION_ADD_SUB:
        case ACTION_REMOVE_SUB:
            // these actions work on the current collector (if it is defined)
            AgentConfig agentConfig = this.agentConfig;
            return agentConfig == null ? null : agentConfig.config.collector;
        case ACTION_RECONFIGURE_4:
            // this action creates new agent on new collector and updates agentConfig with it,
            // so before it has started its collector can be found only via a.config
            return a.config.collector;
        default:
            return null; // other actions to not use collector's global lock
        }
    }

    // It is invoked inside shaper.subscriptionExecutor threads or under lock if !hasSubscriptionExecutor
    // AT MOST ONE COPY OF THIS METHOD IS WORKING AT ANY TIME
    // Returns next action to be executed immediately after this one (reusing "a" instance), or null if done
    SubAction processSubAction(SubAction a) {
        switch (a.action) {
        case ACTION_CLOSE:
            return processCloseAction(a);
        case ACTION_RECONFIGURE_1:
            return processReconfigurePhase1Action(a);
        case ACTION_RECONFIGURE_2:
            return processReconfigurePhase2Action(a);
        case ACTION_RECONFIGURE_3:
            return processReconfigurePhase3Action(a);
        case ACTION_RECONFIGURE_4:
            return processReconfigurePhase4Action(a);
        case ACTION_ADD_SUB_FILTER:
        case ACTION_REMOVE_SUB_FILTER:
            return processSubscriptionFilterAction(a);
        case ACTION_ADD_SUB:
        case ACTION_REMOVE_SUB:
            return processSubscriptionChangeAction(a);
        default:
            throw new AssertionError();
        }
    }

    private SubAction processCloseAction(SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.action == ACTION_CLOSE && a.sub == null;
        assert isChannelClosed();
        if (agentConfig == null)
            return null; // nothing to do -- agent was not created yet
        QDAgent agent = agentConfig.agent;
        // update agent's configuration (first time)
        if (agentConfig.config != CLOSED_CONFIG)
            agentConfig = new AgentConfig(CLOSED_CONFIG, agent);
        // close agent part
        a.notify = agent.closePart(a.notify);
        return a.notify != 0 ? a : null; // has more close steps to do?
    }

    private SubAction processSubscriptionFilterAction(SubAction a) {
        assert underLockOrInSubActionThread();
        assert (a.action == ACTION_ADD_SUB_FILTER || a.action == ACTION_REMOVE_SUB_FILTER) && a.sub != null;
        RecordBuffer sub = RecordBuffer.getInstance(a.sub.getMode());
        RecordBuffer rejectedSub = null; // will take it from pool only when needed (when filter rejects)
        QDContract contract = shaper.getContract();
        RecordCursor cur;
        while ((cur = a.sub.next()) != null) {
            if (!a.config.completeSubscriptionFilter.accept(contract, cur.getRecord(), cur.getCipher(), cur.getSymbol())) {
                // add to rejected agent
                if (shaper.isKeepRejected()) {
                    if (rejectedSub == null)
                        rejectedSub = RecordBuffer.getInstance(RecordMode.addedSubscriptionFor(contract).withEventFlags());
                    rejectedSub.append(cur);
                }
                continue; // continue when filter does not accept
            }
            sub.append(cur);
        }
        a.sub.release(); // release source buffer
        if (sub.isEmpty()) {
            sub.release(); // just release filtered buffer and do nothing more
            return null;
        }
        // process rejectedSub
        if (rejectedSub != null) {
            switch (a.action) {
            case ACTION_ADD_SUB_FILTER:
                getOrCreateRejectedAgent().addSubscription(rejectedSub);
                break;
            case ACTION_REMOVE_SUB_FILTER:
                if (rejectedAgent != null)
                    rejectedAgent.removeSubscription(rejectedSub);
                break;
            default:
                assert false;
            }
            rejectedSub.release();
        }
        // another task to actually add/remove sub
        a.action = a.action == ACTION_ADD_SUB_FILTER ? ACTION_ADD_SUB : ACTION_REMOVE_SUB;
        a.sub = sub;
        return a;
    }

    private SubAction processSubscriptionChangeAction(SubAction a) {
        assert underLockOrInSubActionThread();
        assert a.sub != null;
        switch (a.action) {
        case ACTION_ADD_SUB:
            initNewAgentIfNeeded(a.config);
            a.notify = agentConfig.agent.addSubscriptionPart(a.sub, a.notify);
            break;
        case ACTION_REMOVE_SUB:
            if (agentConfig == null) {
                a.sub.release();
                return null;
            }
            a.notify = agentConfig.agent.removeSubscriptionPart(a.sub, a.notify);
            break;
        default:
            assert false;
        }
        if (a.notify != 0) // has more add/removeSub steps to do
            return a;
        // done -- release buffer
        a.sub.release();
        return null;
    }

    private void initNewAgentIfNeeded(Config config) {
        assert underLockOrInSubActionThread();
        if (agentConfig != null)
            return; // already have agent
        initNewAgent(config);
    }

    private void initNewAgent(Config config) {
        assert underLockOrInSubActionThread();
        // create new agent
        QDAgent agent = createAgent(config);
        // store agent's config
        agentConfig = new AgentConfig(config, agent);
        // ... and only then init it listeners
        agent.setRecordListener(this);
        if (config.hasAggregationPeriod())
            agent.getSnapshotProvider().setRecordListener(this);
    }

    private void updateAgentAggregation(Config config) {
        // update agent configuration first
        agentConfig = new AgentConfig(config, agentConfig.agent);
        // then set listener
        agentConfig.agent.getSnapshotProvider().setRecordListener(config.hasAggregationPeriod() ? this : null);
    }
}

