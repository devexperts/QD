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
package com.devexperts.qd.logger;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.LogUtil;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class LoggedCollector extends AbstractCollector {
    protected Logger log;

    private final QDCollector delegate;
    private final Counter agent_cnt = new Counter();
    private final Counter distributor_cnt = new Counter();

    public LoggedCollector(Logger log, QDCollector delegate, Builder<?> builder) {
        super(builder);
        if (delegate.getScheme() != builder.getScheme())
            throw new IllegalArgumentException("Scheme doesn't match");
        this.log = log;
        this.delegate = delegate;
    }

    @Override
    public boolean isStoreEverything() {
        return delegate.isStoreEverything();
    }

    @Override
    public void setStoreEverything(boolean store_everything) {
        log.debug("setStoreEverything(" + store_everything + ")");
        delegate.setStoreEverything(store_everything);
    }

    @Override
    public void setStoreEverythingFilter(SubscriptionFilter filter) {
        log.debug("setStoreEverythingFilter(" + LogUtil.hideCredentials(filter) + ")");
        delegate.setStoreEverythingFilter(filter);
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        int n = agent_cnt.next();
        log.debug("buildAgent(" + builder + ") = #" + n);
        return new LoggedAgent(log.child("agent" + n), delegate.buildAgent(builder), getScheme());
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        int n = distributor_cnt.next();
        log.debug("buildDistributor(" + builder + ") = #" + n);
        return new LoggedDistributor(log.child("distributor" + n), delegate.buildDistributor(builder));
    }

    @Override
    public QDStats getStats() {
        return delegate.getStats();
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return delegate.isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return delegate.examineSubscription(sink);
    }

    @Override
    public int getSubscriptionSize() {
        return delegate.getSubscriptionSize();
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        delegate.setErrorHandler(errorHandler);
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return delegate.getSymbol(chars, offset, length);
    }

    @Override
    public boolean examineData(RecordSink sink) {
        return delegate.examineData(sink);
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        return delegate.examineDataBySubscription(sink, sub);
    }

    @Override
    public void remove(RecordSource source) {
        delegate.remove(source);
    }

    @Override
    public void executeLockBoundTask(Executor executor, Runnable task) {
        delegate.executeLockBoundTask(executor, task);
    }

    @Override
    public void close() {
        log.debug("close()");
        delegate.close();
    }

    @Override
    public void setDroppedLog(Consumer<String> droppedLog) {
        super.setDroppedLog(droppedLog);
        if (delegate instanceof AbstractCollector) {
            ((AbstractCollector) delegate).setDroppedLog(droppedLog);
        }
    }
}
