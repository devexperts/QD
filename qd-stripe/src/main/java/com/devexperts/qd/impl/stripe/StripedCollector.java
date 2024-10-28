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
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import java.util.function.Consumer;

abstract class StripedCollector<C extends QDCollector> extends AbstractCollector {

    final SymbolCodec codec;
    final SymbolStriper striper;
    final int n;
    final QDStats stats;
    final int wildcard;

    boolean enableWildcards;

    StripedCollector(Builder<?> builder, SymbolStriper striper) {
        super(builder);
        if (striper.getStripeCount() <= 1)
            throw new IllegalArgumentException("Invalid striper: " + striper);

        this.codec = scheme.getCodec();
        this.stats = builder.getStats();
        this.wildcard = scheme.getCodec().getWildcardCipher();
        this.striper = striper;
        this.n = striper.getStripeCount();

        for (int i = 0; i < n; i++) {
            if (!striper.getStripeFilter(i).isStable())
                throw new FilterSyntaxException("Unstable striper filter: " + striper.getStripeFilter(i));
        }
    }

    @Override
    public SymbolStriper getStriper() {
        return striper;
    }

    abstract C[] collectors();

    final int index(int cipher, String symbol) {
        return striper.getStripeIndex(cipher, symbol);
    }

    final C collector(int cipher, String symbol) {
        return collectors()[index(cipher, symbol)];
    }

    @Override
    public void setStoreEverything(boolean storeEverything) {
        super.setStoreEverything(storeEverything);
        updateStoreEverythingFilters();
    }

    @Override
    public void setStoreEverythingFilter(SubscriptionFilter filter) {
        super.setStoreEverythingFilter(filter);
        updateStoreEverythingFilters();
    }

    protected void updateStoreEverythingFilters() {
        boolean storeEverything = isStoreEverything();
        QDFilter filter = storeEverythingFilter;

        for (int i = 0; i < n; i++) {
            C collector = collectors()[i];
            collector.setStoreEverythingFilter(CompositeFilters.makeAnd(filter, striper.getStripeFilter(i)));
            collector.setStoreEverything(storeEverything);
        }
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return StripedAgent.createAgent(this, builder);
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        return StripedDistributor.createDistributor(this, builder);
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        for (QDCollector collector : collectors()) {
            collector.setErrorHandler(errorHandler);
        }
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        int index = striper.getStripeIndex(chars, offset, length);
        return collectors()[index].getSymbol(chars, offset, length);
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return collector(cipher, symbol).isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        for (QDCollector collector : collectors()) {
            if (collector.examineSubscription(sink))
                return true;
        }
        return false;
    }

    @Override
    public int getSubscriptionSize() {
        int sum = 0;
        for (QDCollector collector : collectors()) {
            sum += collector.getSubscriptionSize();
        }
        return sum;
    }

    @Override
    public boolean examineData(RecordSink sink) {
        for (QDCollector collector : collectors()) {
            if (collector.examineData(sink))
                return true;
        }
        return false;
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        long position = sub.getPosition();
        for (QDCollector collector : collectors()) {
            sub.setPosition(position);
            if (collector.examineDataBySubscription(sink, sub))
                return true;
        }
        return false;
    }

    @Override
    public void remove(RecordSource source) {
        RecordBuffer[] buf = StripedBuffersUtil.stripeData(this, source);
        for (int i = 0; i < n; i++) {
            if (buf[i] != null && !buf[i].isEmpty())
                collectors()[i].remove(buf[i]);
        }
        StripedBuffersUtil.releaseBuf(buf);
    }

    @Override
    public void close() {
        for (QDCollector collector : collectors()) {
            collector.close();
        }
    }

    @Override
    public void setDroppedLog(Consumer<String> droppedLog) {
        super.setDroppedLog(droppedLog);
        for (QDCollector collector : collectors()) {
            if (collector instanceof AbstractCollector) {
                ((AbstractCollector) collector).setDroppedLog(droppedLog);
            }
        }
    }
}
