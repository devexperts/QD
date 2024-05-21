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
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.stats.QDStats;

public abstract class AbstractCollectorBuilder<T extends QDCollector> implements QDCollector.Builder<T> {

    private final QDContract contract;
    private DataScheme scheme;
    private QDStats stats;
    private HistorySubscriptionFilter historyFilter;
    private boolean withEventTimeSequence;
    private boolean storeEverything;
    private SubscriptionFilter storeEverythingFilter;
    private SymbolStriper striper;

    protected AbstractCollectorBuilder(QDContract contract) {
        this.contract = contract;
    }

    @Override
    public abstract T build();

    @Override
    public QDCollector.Builder<T> copyFrom(QDCollector.Builder<?> other) {
        scheme = other.getScheme();
        stats = other.getStats();
        historyFilter = other.getHistoryFilter();
        withEventTimeSequence = other.hasEventTimeSequence();
        storeEverything = other.isStoreEverything();
        storeEverythingFilter = other.getStoreEverythingFilter();
        striper = other.getStriper();
        return this;
    }

    @Override
    public final QDContract getContract() {
        return contract;
    }

    @Override
    public final QDCollector.Builder<T> withScheme(DataScheme scheme) {
        this.scheme = scheme;
        return this;
    }

    @Override
    public final DataScheme getScheme() {
        if (scheme == null)
            return QDFactory.getDefaultScheme();
        return scheme;
    }

    @Override
    public final QDCollector.Builder<T> withStats(QDStats stats) {
        this.stats = stats;
        return this;
    }

    @Override
    public final QDStats getStats() {
        if (stats == null)
            stats = QDFactory.createStats(contract.getStatsType(), getScheme());
        return stats;
    }

    @Override
    public final QDCollector.Builder<T> withHistoryFilter(HistorySubscriptionFilter historyFilter) {
        this.historyFilter = historyFilter;
        return this;
    }

    @Override
    public final HistorySubscriptionFilter getHistoryFilter() {
        return historyFilter;
    }

    @Override
    public QDCollector.Builder<T> withEventTimeSequence(boolean value) {
        this.withEventTimeSequence = value;
        return this;
    }

    @Override
    public boolean hasEventTimeSequence() {
        return withEventTimeSequence;
    }

    @Override
    public QDCollector.Builder<T> withStoreEverything(boolean storeEverything) {
        this.storeEverything = storeEverything;
        return this;
    }

    @Override
    public boolean isStoreEverything() {
        return storeEverything;
    }

    @Override
    public QDCollector.Builder<T> withStoreEverythingFilter(SubscriptionFilter filter) {
        storeEverythingFilter = filter;
        return this;
    }

    @Override
    public SubscriptionFilter getStoreEverythingFilter() {
        return storeEverythingFilter;
    }

    @Override
    public QDCollector.Builder<T> withStriper(SymbolStriper striper) {
        this.striper = striper;
        return this;
    }

    @Override
    public SymbolStriper getStriper() {
        if (striper == null)
            striper = MonoStriper.INSTANCE;
        return striper;
    }
}
