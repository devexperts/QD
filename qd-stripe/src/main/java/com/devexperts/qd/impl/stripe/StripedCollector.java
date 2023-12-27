/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
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
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.kit.HashStriper;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

abstract class StripedCollector<C extends QDCollector> extends AbstractCollector {

    final SymbolCodec codec;
    final HashStriper striper;
    final int n; // always power of 2
    final QDStats stats;
    final int wildcard;

    boolean enableWildcards;

    StripedCollector(Builder<?> builder, int n) {
        super(builder);
        if ((n < 2) || ((n & (n - 1)) != 0))
            throw new IllegalArgumentException("Striping factor N should a power of 2 and at least 2");
        this.codec = scheme.getCodec();
        this.n = n;
        this.stats = builder.getStats();
        this.wildcard = scheme.getCodec().getWildcardCipher();
        this.striper = (HashStriper) HashStriper.valueOf(scheme, n);
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
        for (int i = 0; i < n; i++) {
            if (storeEverything)
                collectors()[i].setStoreEverythingFilter(new StoreFilter(i));
            collectors()[i].setStoreEverything(storeEverything);
        }
    }

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return new StripedAgent<>(this, builder);
    }

    @Override
    public QDDistributor buildDistributor(QDDistributor.Builder builder) {
        return new StripedDistributor<>(this, builder);
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        for (QDCollector collector : collectors())
            collector.setErrorHandler(errorHandler);
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        int i = striper.getStripeIndex(chars, offset, length);
        return collectors()[i].getSymbol(chars, offset, length);
    }

    @Override
    public boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return collector(cipher, symbol).isSubscribed(record, cipher, symbol, time);
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        for (QDCollector collector : collectors())
            if (collector.examineSubscription(sink))
                return true;
        return false;
    }

    @Override
    public int getSubscriptionSize() {
        int sum = 0;
        for (QDCollector collector : collectors())
            sum += collector.getSubscriptionSize();
        return sum;
    }

    @Override
    public boolean examineData(RecordSink sink) {
        for (QDCollector collector : collectors())
            if (collector.examineData(sink))
                return true;
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
        long position = source.getPosition();
        for (QDCollector collector : collectors()) {
            source.setPosition(position);
            collector.remove(source);
        }
    }

    @Override
    public void close() {
        for (QDCollector collector : collectors())
            collector.close();
    }


    private class StoreFilter extends QDFilter {
        private final int i;

        StoreFilter(int i) {
            super(StripedCollector.this.scheme);
            this.i = i;
        }

        @Override
        public boolean isFast() {
            return true;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            SubscriptionFilter storeEverythingFilter = StripedCollector.this.storeEverythingFilter;
            return (storeEverythingFilter == null || storeEverythingFilter.acceptRecord(record, cipher, symbol)) &&
                index(cipher, symbol) == i;
        }

        @Override
        public String getDefaultName() {
            return "storeFilter#" + i;
        }
    }
}
