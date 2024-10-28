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
package com.devexperts.qd.impl;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.util.LegacyAdapter;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public abstract class AbstractCollector implements QDCollector {
    protected final QDContract contract;
    protected final DataScheme scheme;
    protected boolean storeEverything;
    protected QDFilter storeEverythingFilter; // @NotNull
    protected Consumer<String> droppedLog;

    private final QDAgent.Builder agentBuilder;
    private final DistributorBuilder distributorBuilder = new DistributorBuilder();
    private final boolean withEventTimeSequence;

    protected AbstractCollector(Builder<?> builder) {
        this.contract = builder.getContract();
        this.scheme = builder.getScheme();
        this.withEventTimeSequence = builder.hasEventTimeSequence();
        this.storeEverything = builder.isStoreEverything();
        this.storeEverythingFilter = QDFilter.fromFilter(builder.getStoreEverythingFilter(), scheme);
        this.agentBuilder = new AgentBuilder().withEventTimeSequence(withEventTimeSequence);
    }

    public void setDroppedLog(Consumer<String> droppedLog) {
        this.droppedLog = droppedLog;
    }

    @Override
    public final QDContract getContract() {
        return contract;
    }

    @Override
    public final DataScheme getScheme() {
        return scheme;
    }

    @Override
    public SymbolStriper getStriper() {
        return MonoStriper.INSTANCE;
    }

    @Override
    public final QDAgent.Builder agentBuilder() {
        return agentBuilder;
    }

    @Override
    public final QDAgent createAgent(SubscriptionFilter filter) {
        return agentBuilder
            .withFilter(QDFilter.fromFilter(filter, getScheme()))
            .build();
    }

    @Override
    public final QDAgent createAgent(SubscriptionFilter filter, String keyProperties) {
        return agentBuilder
            .withFilter(QDFilter.fromFilter(filter, getScheme()))
            .withKeyProperties(keyProperties)
            .build();
    }

    @Override
    public final QDDistributor.Builder distributorBuilder() {
        return distributorBuilder;
    }

    @Override
    public final QDDistributor createDistributor(SubscriptionFilter filter) {
        return distributorBuilder
            .withFilter(QDFilter.fromFilter(filter, getScheme()))
            .build();
    }

    @Override
    public final QDDistributor createDistributor(SubscriptionFilter filter, String keyProperties) {
        return distributorBuilder
            .withFilter(QDFilter.fromFilter(filter, getScheme()))
            .withKeyProperties(keyProperties)
            .build();
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return null;
    }

    @Override
    public final boolean examineData(DataVisitor visitor) {
        return examineData(LegacyAdapter.of(visitor));
    }

    @Override
    public final boolean examineSubscription(SubscriptionVisitor visitor) {
        return examineSubscription(LegacyAdapter.of(visitor));
    }

    @Override
    public boolean examineData(RecordSink sink) {
        return false;
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        return false;
    }

    @Override
    public void remove(RecordSource source) {
        // do nothing by default
    }

    @Override
    public boolean hasEventTimeSequence() {
        return withEventTimeSequence;
    }

    @Override
    public boolean isStoreEverything() {
        return storeEverything;
    }

    @Override
    public void setStoreEverything(boolean storeEverything) {
        this.storeEverything = storeEverything;
    }

    @Override
    public void setStoreEverythingFilter(SubscriptionFilter filter) {
        this.storeEverythingFilter = QDFilter.fromFilter(filter, scheme);
    }

    @Override
    public void executeLockBoundTask(Executor executor, Runnable task) {
        // no support for cooperation on lock-bound tasks by default in this abstract implementation
        executor.execute(task);
    }

    @Override
    public abstract QDAgent buildAgent(QDAgent.Builder builder);

    @Override
    public abstract QDDistributor buildDistributor(QDDistributor.Builder builder);

    public class AgentBuilder extends AbstractAgentBuilder {
        @Override
        public QDAgent build() {
            return buildAgent(this);
        }
    }

    public class DistributorBuilder extends AbstractBuilder<QDDistributor.Builder, DistributorBuilder>
        implements QDDistributor.Builder
    {
        @Override
        public QDDistributor build() {
            return buildDistributor(this);
        }

        @Override
        public String toString() {
            return "DistributorBuilder{" + super.toString() +
                '}';
        }
    }
}
