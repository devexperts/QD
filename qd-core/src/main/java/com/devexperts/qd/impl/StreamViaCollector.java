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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

import java.util.function.Consumer;

/**
 * The {@code StreamViaCollector} simulates {@link QDStream} interface using
 * specified {@link QDCollector}. As a result it passes only records passed by
 * original collector contract.
 */
public class StreamViaCollector extends AbstractCollector implements QDStream {
    protected final QDCollector collector;

    public StreamViaCollector(QDCollector collector, Builder<?> builder) {
        super(builder);
        if (collector.getScheme() != builder.getScheme())
            throw new IllegalArgumentException("Scheme doesn't match");
        this.collector = collector;
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return collector.buildAgent(builder);
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        return collector.buildDistributor(builder);
    }

    @Override
    public QDStats getStats() {
        return collector.getStats();
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return collector.isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return collector.examineSubscription(sink);
    }

    @Override
    public int getSubscriptionSize() {
        return collector.getSubscriptionSize();
    }

    @Override
    public void setEnableWildcards(boolean enableWildcards) {
        // not implemented
    }

    @Override
    public boolean getEnableWildcards() {
        return false;
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        collector.setErrorHandler(errorHandler);
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return collector.getSymbol(chars, offset, length);
    }

    @Override
    public void close() {
        collector.close();
    }

    @Override
    public void setDroppedLog(Consumer<String> droppedLog) {
        super.setDroppedLog(droppedLog);
        if (collector instanceof AbstractCollector) {
            ((AbstractCollector) collector).setDroppedLog(droppedLog);
        }
    }
}
