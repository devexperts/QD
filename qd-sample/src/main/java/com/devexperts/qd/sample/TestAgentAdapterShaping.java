/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.tools.RandomRecordsProvider;
import com.devexperts.qd.util.LegacyIteratorUtils;
import com.devexperts.qd.util.SymbolObjectMap;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * This class models weighted data shaping in agent adapter.
 */
public class TestAgentAdapterShaping {
    public static void main(String[] args) throws InterruptedException {
        final int[] weights = {1, 2, 5, 10, 100, 50, 25, 1, 3, 1};
        final int n = weights.length;
        final int k = 26 * 26;

        final int port = 5577;

        final DataScheme scheme = SampleScheme.getInstance();
        final DataRecord record = scheme.getRecord(0);

        final String[] allSymbols = new String[n * k];
        final int[] allCiphers = new int[n * k];
        final String[][] symbols = new String[n][k];

        final QDContract contract = QDContract.TICKER;
        final QDCollector[] collectors = new QDCollector[n];
        final ChannelShaper[] shapers = new ChannelShaper[n];
        final SymbolObjectMap<Integer> symbolIndexMap = SymbolObjectMap.createInstance();
        for (int i = 0; i < n; i++) {
            collectors[i] = QDFactory.getDefaultFactory().createTicker(scheme);

            for (int j = 0; j < k; j++) {
                int t = i * k + j;
                symbols[i][j] = allSymbols[t] = "" + (j / 26 - 'A') + (j % 26 - 'A') + i;
                allCiphers[t] = scheme.getCodec().encode(allSymbols[i]);
                symbolIndexMap.put(allCiphers[t], allSymbols[t], i);
            }
            shapers[i] = new ChannelShaper(contract, null);
            shapers[i].setCollector(collectors[i]);
            shapers[i].setWeight(weights[i]);
        }

        final AtomicLongArray count = new AtomicLongArray(n);

        final MessageAdapter counterAdapter = new MessageAdapter(QDStats.VOID) {
            private final SubscriptionIterator subscriptionIterator = new SubscriptionIterator() {
                private int i = -1;

                @Override
                public int getCipher() {
                    return allCiphers[i];
                }

                @Override
                public String getSymbol() {
                    return allSymbols[i];
                }

                @Override
                public long getTime() {
                    return 0;
                }

                @Override
                public DataRecord nextRecord() {
                    if (i == (n * k) - 1)
                        return null;
                    i++;
                    return record;
                }
            };

            @Override
            public DataScheme getScheme() {
                return scheme;
            }

            @Override
            public boolean retrieveMessages(MessageVisitor visitor) {
                super.retrieveMessages(visitor);
                SubscriptionProvider provider = new SubscriptionProvider() {
                    @Override
                    public boolean retrieveSubscription(SubscriptionVisitor visitor) {
                        return LegacyIteratorUtils.processSubscription(subscriptionIterator, visitor);
                    }

                    @Override
                    public void setSubscriptionListener(SubscriptionListener listener) {
                        throw new UnsupportedOperationException();
                    }
                };

                return visitor.visitSubscription(provider, MessageType.forAddSubscription(contract));
            }

            @Override
            public void processData(DataIterator iterator, MessageType type) {
                while (iterator.nextRecord() != null) {
                    int i = symbolIndexMap.get(iterator.getCipher(), iterator.getSymbol());
                    count.incrementAndGet(i);
                }
            }
        };


        MessageConnectors.startMessageConnectors(
            MessageConnectors.createMessageConnectors(MessageConnectors.applicationConnectionFactory(
                stats -> new AgentAdapter(scheme, stats).initialize(shapers)), ":" + port)
        );

        MessageConnectors.startMessageConnectors(
            MessageConnectors.createMessageConnectors(MessageConnectors.applicationConnectionFactory(new MessageAdapter.Factory() {
                boolean created = false;
                @Override
                public MessageAdapter createAdapter(QDStats stats) {
                    if (created)
                        throw new IllegalStateException();
                    created = true;
                    return counterAdapter;
                }
            }), "localhost:" + port)
        );

        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                QDDistributor[] distributors = new QDDistributor[n];
                DataProvider[] providers = new DataProvider[n];
                for (int i1 = 0; i1 < n; i1++) {
                    distributors[i1] = collectors[i1].distributorBuilder().build();
                    providers[i1] = new RandomRecordsProvider(record, symbols[i1], 10);
                }
                RecordBuffer buffer = new RecordBuffer();
                for (int i1 = 0; !Thread.interrupted(); i1 = (i1 + 1) % n) {
                    providers[i1].retrieveData(buffer);
                    distributors[i1].processData(buffer);
                    buffer.clear();
                }
                System.out.println("generator exit");
            });
            t.setPriority(Thread.NORM_PRIORITY);
            t.start();
        }

        while (true) {
            for (int i = 0; i < n; i++) {
                System.out.println(weights[i] + " " + count.get(i));
            }
            System.out.println("");
            Thread.sleep(5000);
        }
    }
}
