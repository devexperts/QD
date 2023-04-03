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
package com.devexperts.qd.test;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.monitoring.ConnectorsMonitoringTask;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.stats.QDStats;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class SubscriptionStress implements Runnable {
    private static final int THREADS = 32;
    private static final int RECORDS = 4;
    private static final int SYMBOLS = 100000;
    private static final int MAX_BATCH = 3000;
    private static final int MAX_TIMES = 100;

    private static final Random BASE_RND = new Random(20100102);
    private static final TestDataScheme SCHEME =
        new TestDataScheme(RECORDS, BASE_RND.nextLong(), TestDataScheme.Type.SIMPLE);

    public static void main(String[] args) throws InterruptedException {
        new SubscriptionStress().go();
    }

    private final QDStats stats;
    private final QDTicker ticker;
    private final String[] symbols;
    private final ConnectorsMonitoringTask monitoring;
    private final AtomicLong ops = new AtomicLong();
    private final ThreadLocal<Random> rnd = ThreadLocal.withInitial(() -> new Random(BASE_RND.nextLong()));

    public SubscriptionStress() {
        stats = new QDStats(QDStats.SType.ANY);
        ticker = QDFactory.getDefaultFactory().tickerBuilder().withScheme(SCHEME).withStats(stats).build();
        //ticker.setStoreEverything(true);
        monitoring = new ConnectorsMonitoringTask(stats);
        symbols = new String[SYMBOLS];
        for (int i = 0; i < SYMBOLS; i++) {
            String s = String.format("%06d", i);
            symbols[i] = s;
            assert SCHEME.getCodec().encode(s) == 0; // assert all symbols are non-coding
        }
    }

    private void go() throws InterruptedException {
        for (int i = 0; i < THREADS; i++)
            new Thread(this, "Worker-" + i).start();
        while (true) {
            System.out.println(ops.get() + " ops");
            monitoring.run();
            Thread.sleep(10000);
        }
    }

    public void run() {
        System.out.println("Running " + Thread.currentThread().getName());
        while (true) {
            switch (rnd.get().nextInt(4)) {
            case 0: runAdder(); break;
            case 1: runAdderRemover(); break;
            case 2: runSetter(); break;
            case 3: runDistributor(); break;
            }
            ops.incrementAndGet();
        }
    }

    private void retrieveData(QDAgent agent) {
        RecordBuffer buf = RecordBuffer.getInstance();
        agent.retrieve(buf);
        buf.release();
    }

    private void runAdder() {
        QDAgent agent = ticker.agentBuilder().build();
        int times = rnd.get().nextInt(MAX_TIMES);
        int max = rnd.get().nextInt(MAX_BATCH) + 1;
        for (int i = 0; i < times; i++) {
            RecordBuffer buf = RecordBuffer.getInstance();
            int n = rnd.get().nextInt(max);
            for (int j = 0; j < n; j++)
                buf.add(SCHEME.getRecord(rnd.get().nextInt(RECORDS)), 0, symbols[rnd.get().nextInt(SYMBOLS)]);
            agent.addSubscription(buf);
            buf.release();
            retrieveData(agent);
        }
        agent.close();
    }

    private void runAdderRemover() {
        QDAgent agent = ticker.agentBuilder().build();
        int times = rnd.get().nextInt(MAX_TIMES);
        int max = rnd.get().nextInt(MAX_BATCH) + 1;
        for (int i = 0; i < times; i++) {
            RecordBuffer buf = RecordBuffer.getInstance();
            int n = rnd.get().nextInt(max);
            for (int j = 0; j < n; j++)
                buf.add(SCHEME.getRecord(rnd.get().nextInt(RECORDS)), 0, symbols[rnd.get().nextInt(SYMBOLS)]);
            if (rnd.get().nextBoolean())
                agent.addSubscription(buf);
            else
                agent.removeSubscription(buf);
            buf.release();
            retrieveData(agent);
        }
        agent.close();
    }

    private void runSetter() {
        QDAgent agent = ticker.agentBuilder().build();
        int times = rnd.get().nextInt(MAX_TIMES);
        int max = rnd.get().nextInt(MAX_BATCH) + 1;
        for (int i = 0; i < times; i++) {
            RecordBuffer buf = RecordBuffer.getInstance();
            int n = rnd.get().nextInt(max);
            for (int j = 0; j < n; j++)
                buf.add(SCHEME.getRecord(rnd.get().nextInt(RECORDS)), 0, symbols[rnd.get().nextInt(SYMBOLS)]);
            agent.setSubscription(buf);
            buf.release();
            retrieveData(agent);
        }
        agent.close();
    }

    private void runDistributor() {
        QDDistributor dist = ticker.distributorBuilder().build();
        int times = rnd.get().nextInt(MAX_TIMES);
        int first = rnd.get().nextInt(2);
        for (int i = first; i < first + times; i++) {
            boolean add = i % 2 == 0;
            RecordProvider provider = add ? dist.getAddedRecordProvider() : dist.getRemovedRecordProvider();
            RecordBuffer buf = RecordBuffer.getInstance();
            provider.retrieve(buf);
            if (add)
                dist.process(buf);
            buf.release();
        }
        dist.close();
    }
}
