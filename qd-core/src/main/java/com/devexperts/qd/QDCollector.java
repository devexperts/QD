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
package com.devexperts.qd;

import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.stats.QDStatsContainer;

import java.util.concurrent.Executor;

/**
 * The {@code QDCollector} represents a hub which collects subscription from
 * data consumers and distributes it among data providers and at the same time it
 * collects data from data providers and distributes it among data consumers.
 * The data consumers are represented by their {@link QDAgent}, and the data providers
 * are represented by their {@link QDDistributor}.
 */
public interface QDCollector extends SubscriptionContainer, QDStatsContainer {

    /**
     * Returns data scheme used by this QD component.
     */
    public DataScheme getScheme();

    /**
     * Returns contract that this collector provides.
     */
    public QDContract getContract();

    /**
     * Returns symbol striper for this QD component.
     */
    public SymbolStriper getStriper();

    /**
     * Returns a builder that can be configured to {@link QDAgent.Builder#build build} custom agents.
     */
    public QDAgent.Builder agentBuilder();

    /**
     * Creates new agent with parameters from the specified builder.
     * @param builder parameters.
     */
    public QDAgent buildAgent(QDAgent.Builder builder);

    /**
     * Creates new agent with the specified subscription filter.
     * Specify null to use no filter.
     * This is a shortcut for
     * <pre>
     *    {@link #agentBuilder() agentBuilder}()
     *        .{@link QDAgent.Builder#withFilter(QDFilter) withFilter}({@link QDFilter QDFilter}.{@link QDFilter#fromFilter(SubscriptionFilter, DataScheme) fromFilter}(filter))
     *        .{@link QDAgent.Builder#build() build}();
     * </pre>
     * @deprecated Use {@link #agentBuilder() agentBuilder} and {@link QDAgent.Builder#build() build}
     */
    public QDAgent createAgent(SubscriptionFilter filter);

    /**
     * Creates new agent with the specified subscription filter and key properties.
     * Specify null to use no filter.
     * This is a shortcut for
     * <pre>
     *    {@link #agentBuilder() agentBuilder}()
     *        .{@link QDAgent.Builder#withFilter(QDFilter) withFilter}({@link QDFilter QDFilter}.{@link QDFilter#fromFilter(SubscriptionFilter, DataScheme) fromFilter}(filter))
     *        .{@link QDAgent.Builder#withKeyProperties(String) withKeyProperties}(keyProperties)
     *        .{@link QDAgent.Builder#build() build}();
     * </pre>
     * @deprecated Use {@link #agentBuilder() agentBuilder} and {@link QDAgent.Builder#build() build}
     */
    public QDAgent createAgent(SubscriptionFilter filter, String keyProperties);

    /**
     * Returns a builder that can be configured to {@link QDDistributor.Builder#build build} custom distributors.
     */
    public QDDistributor.Builder distributorBuilder();

    /**
     * Creates new distributor with parameters from the specified builder.
     * @param builder parameters.
     */
    public QDDistributor buildDistributor(QDDistributor.Builder builder);

    /**
     * Creates new distributor with the specified subscription filter.
     * Specify null to use no filter.
     * This is a shortcut for
     * <pre>
     *    {@link #distributorBuilder() distributorBuilder}()
     *        .{@link QDDistributor.Builder#withFilter(QDFilter) withFilter}({@link QDFilter QDFilter}.{@link QDFilter#fromFilter(SubscriptionFilter, DataScheme) fromFilter}(filter))
     *        .{@link QDDistributor.Builder#build() build}();
     * </pre>
     * @deprecated Use {@link #distributorBuilder() distributorBuilder} and {@link QDDistributor.Builder#build() build}
     */
    public QDDistributor createDistributor(SubscriptionFilter filter);

    /**
     * Creates new distributor with the specified subscription filter and key properties.
     * Specify null to use no filter.
     * This is a shortcut for
     * <pre>
     *    {@link #distributorBuilder() distributorBuilder}()
     *        .{@link QDDistributor.Builder#withFilter(QDFilter) withFilter}({@link QDFilter QDFilter}.{@link QDFilter#fromFilter(SubscriptionFilter, DataScheme) fromFilter}(filter))
     *        .{@link QDDistributor.Builder#withKeyProperties(String) withKeyProperties}(keyProperties)
     *        .{@link QDDistributor.Builder#build() build}();
     * </pre>
     * @deprecated Use {@link #distributorBuilder() distributorBuilder} and {@link QDDistributor.Builder#build() build}
     */
    public QDDistributor createDistributor(SubscriptionFilter filter, String keyProperties);

    /**
     * Sets errors handler for all errors that are happening during notification of agents
     * and distributors. Default error handler for collector is initialized via {@link DataScheme#getService} method
     * and {@link QDErrorHandler#DEFAULT} is used if no override is found.
     */
    public void setErrorHandler(QDErrorHandler errorHandler);

    /**
     * Returns status of "store everything" mode.
     * @see #setStoreEverything(boolean)
     */
    public boolean isStoreEverything();

    /**
     * Sets "store everything" mode (disabled by default). In this mode {@link QDDistributor#processData(DataIterator) processData}
     * stores everything into underlying storage regardless of subscription (by default only records that are
     * subscribed on are being stored). When subscription is removed records are not removed from storage
     * (by default they are). Only records that match specified {@link #setStoreEverythingFilter(SubscriptionFilter) filter}
     * are subject to this mode (all records by default).
     *
     * <p>Support of the feature depends on collector implementation.
     */
    public void setStoreEverything(boolean storeEverything);

    /**
     * Sets filter that is used for "store everything" mode. Use <code>null</code> (default)
     * to turn off filtering and store all records when "store everything" mode is on.
     *
     * <p>Support of the feature depends on collector implementation.
     * @see #setStoreEverything(boolean)
     */
    public void setStoreEverythingFilter(SubscriptionFilter filter);

    public String getSymbol(char[] chars, int offset, int length);

    /**
     * Returns {@code true} if collector should process event time sequence, {@code false} otherwise.
     */
    public boolean hasEventTimeSequence();

    /**
     * Examines all stored data via specified data visitor.
     * Returns <code>true</code> if some data was not examined or
     * <code>false</code> if everything was examined.
     *
     * @deprecated Use {@link #examineData(RecordSink)}.
     */
    public boolean examineData(DataVisitor visitor);

    /**
     * Examines all stored data via specified record sink.
     * This method periodically calls {@link RecordSink#flush() RecordSink.flush} method outside of locks.
     * Returns {@code true} if some data was not examined because sink ran out of
     * {@link RecordSink#hasCapacity() capacity} or {@code false} if everything was examined.
     *
     * <p>In {@link QDHistory} collector, this method method uses
     * {@link EventFlag#SNAPSHOT_BEGIN SNAPSHOT_BEGIN}, {@link EventFlag#SNAPSHOT_END SNAPSHOT_END},
     * {@link EventFlag#SNAPSHOT_SNIP SNAPSHOT_SNIP},
     * {@link EventFlag#REMOVE_EVENT REMOVE_EVENT}, and {@link EventFlag#TX_PENDING TX_PENDING} flags appropriately
     * to describe the snapshot and transaction state of stored data, as if the fresh subscription
     * with {@link QDAgent.Builder#withHistorySnapshot(boolean) history snapshot} was created.
     */
    public boolean examineData(RecordSink sink);

    /**
     * Examines all stored data for a given subscription via specified sink.
     * This method periodically calls {@link RecordSink#flush() RecordSink.flush} method outside of locks.
     * Returns {@code true} if some data was not examined because sink ran out of
     * {@link RecordSink#hasCapacity() capacity} or {@code false} if everything was examined.
     *
     * <p>In {@link QDHistory} collector, this method method uses
     * {@link EventFlag#SNAPSHOT_BEGIN SNAPSHOT_BEGIN}, {@link EventFlag#SNAPSHOT_END SNAPSHOT_END},
     * {@link EventFlag#SNAPSHOT_SNIP SNAPSHOT_SNIP},
     * {@link EventFlag#REMOVE_EVENT REMOVE_EVENT}, and {@link EventFlag#TX_PENDING TX_PENDING} flags appropriately
     * to describe the snapshot and transaction state of stored data, as if the fresh subscription
     * with {@link QDAgent.Builder#withHistorySnapshot(boolean) history snapshot} was created.
     *
     * @param sink the sink to append data to.
     * @param sub the subscription source.
     */
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub);

    /**
     * Removes the corresponding records from the underlying storage.
     * Removal is performed under the global lock.
     * This method is designed to facilitate cleanup of stale data in
     * {@link #setStoreEverything(boolean) storeEverything} mode.
     */
    public void remove(RecordSource source);

    /**
     * Cooperative mechanism to execute a task that spends most of its time under the global lock of this collector
     * (like add/remove/set subscription or closing of agent). It adds a task to an internal
     * queue to ensure that at most one such task attempt to run at any given time.
     * @param executor the executor for the task.
     * @param task the runnable task.
     */
    public void executeLockBoundTask(Executor executor, Runnable task);

    /**
     * Closes this collector and frees all external resources that are associated with it.
     * This method unregisters all monitoring beans that were created for this collector.
     */
    public void close();

    /**
     * Factory for {@link QDCollector} instances.
     */
    public interface Factory {
        /**
         * Returns this factory's contract.
         */
        public QDContract getContract();

        /**
         * Creates collector instance using a given base factory, data scheme, and stats.
         */
        public QDCollector createCollector(QDFactory factory, QDCollector.Builder<?> builder);

        /**
         * Returns type of the stats stat should be used for collector created by this factory.
         * @return One of {@link QDStats.SType#TICKER}, {@link QDStats.SType#STREAM}, or {@link QDStats.SType#HISTORY}
         */
        public QDStats.SType getStatsType();
    }

    /**
     * Builder for collectors.
     */
    public interface Builder<T extends QDCollector> {
        /**
         * Returns collector's contract.
         */
        public QDContract getContract();

        /**
         * Adds scheme to collector builder.
         */
        public Builder<T> withScheme(DataScheme scheme);

        /**
         * Returns specified scheme.
         * If no scheme specified then {@link QDFactory#getDefaultScheme() default scheme} will be returned.
         */
        public DataScheme getScheme();

        /**
         * Adds stats to collector builder.
         */
        public Builder<T> withStats(QDStats stats);

        /**
         * Returns specified stats. If no stats specified then
         * {@link QDFactory#createStats(QDStats.SType, DataScheme) default stats implementation} will be
         * created and returned.
         */
        public QDStats getStats();

        /**
         * Adds history filter to collector builder.
         */
        public Builder<T> withHistoryFilter(HistorySubscriptionFilter historyFilter);

        /**
         * Returns specified history filter or {@code null} if no filter is specified.
         */
        public HistorySubscriptionFilter getHistoryFilter();

        /**
         * Specifies should collector process event time sequence or not.
         */
        public Builder<T> withEventTimeSequence(boolean withEventTimeSequence);

        /**
         * Returns true if collector should process event time sequence.
         */
        public boolean hasEventTimeSequence();

        /**
         * Specifies if collector should work in "story everything" mode.
         */
        public Builder<T> withStoreEverything(boolean storeEverything);

        /**
         * Returns true if collector should work in "story everything" mode.
         */
        public boolean isStoreEverything();

        /**
         * Specifies filter to be used for "story everything" feature.
         */
        public Builder<T> withStoreEverythingFilter(SubscriptionFilter filter);

        /**
         * Returns filter to be used for "story everything" feature.
         */
        public SubscriptionFilter getStoreEverythingFilter();

        /**
         * Specifies symbol striper to be used for collector.
         */
        public Builder<T> withStriper(SymbolStriper striper);

        /**
         * Returns symbol striper for collector.
         * Striper must be stable in the sense that all its filters must be {@link QDFilter#isStable() stable}.
         */
        public SymbolStriper getStriper();

        /**
         * Copies properties from others.
         */
        public Builder<T> copyFrom(Builder<?> other);

        /**
         * Builds collector and cast it to needful type.
         */
        public T build();
    }
}
