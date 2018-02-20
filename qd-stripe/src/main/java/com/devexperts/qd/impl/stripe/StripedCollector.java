/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.*;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

abstract class StripedCollector<C extends QDCollector> extends AbstractCollector {
    private static final int MAGIC = 0xB46394CD;

    final DataScheme scheme;
    final SymbolCodec codec;
    final int n; // always power of 2
    final int shift;
    final QDStats stats;
    final int wildcard;

    boolean enableWildcards;
    boolean storeEverything;
    QDFilter storeEverythingFilter = QDFilter.ANYTHING; // @NotNull

    StripedCollector(Builder<?> builder, int n) {
        super(builder);
        if ((n < 2) || ((n & (n - 1)) != 0))
            throw new IllegalArgumentException("Striping factor N should a power of 2 and at least 2");
        this.scheme = builder.getScheme();
        this.codec = scheme.getCodec();
        this.n = n;
        this.shift = 32 - Integer.numberOfTrailingZeros(n);
        this.stats = builder.getStats();
        this.wildcard = scheme.getCodec().getWildcardCipher();
        QDLog.log.debug("Creating striped " + getContract() + "[" + stats.getFullKeyProperties() + "], N=" + n);
    }

    abstract C[] collectors();

    private int index(int hash) {
        return hash * MAGIC >>> shift;
    }

    final int index(int cipher, String symbol) {
        return index(cipher != 0 ? codec.hashCode(cipher) : symbol.hashCode());
    }

    final C collector(int cipher, String symbol) {
        return collectors()[index(cipher, symbol)];
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public boolean isStoreEverything() {
        return storeEverything;
    }

    @Override
    public void setStoreEverything(boolean storeEverything) {
        this.storeEverything = storeEverything;
        for (int i = 0; i < n; i++) {
            if (storeEverything)
                collectors()[i].setStoreEverythingFilter(new StoreFilter(i));
            collectors()[i].setStoreEverything(storeEverything);
        }
    }

    @Override
    public void setStoreEverythingFilter(SubscriptionFilter filter) {
        storeEverythingFilter = QDFilter.fromFilter(filter, scheme);
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
        int hash = 0;
        for (int i = 0; i < length; i++)
            hash = 31 * hash + chars[offset + i];
        return collectors()[index(hash)].getSymbol(chars, offset, length);
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
