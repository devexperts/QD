/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.samplecert.SampleCert;
import com.devexperts.util.TimeFormat;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.locks.LockSupport;

public class TestThroughput {

    private static class DistributorThread extends Thread {
        private final int index;
        private final TestThroughputContext ctx;
        private final QDDistributor dist;

        DistributorThread(int index, TestThroughputContext ctx) {
            super("Distributor-" + index);
            setPriority(Thread.MIN_PRIORITY);
            setDaemon(true);
            this.index = index;
            this.ctx = ctx;
            dist = ctx.collectorDist.distributorBuilder().build();
        }

        @Override
        public void run() {
            final TestThroughputGenerator generator = new TestThroughputGenerator(index, ctx);
            boolean doneSub = false;
            int subCounter = 0;

            RecordBuffer buf = RecordBuffer.getInstance();
            buf.setCapacityLimited(true);
            while (true) {
                boolean more = true;
                while (more) {
                    buf.clear();
                    buf.setMode(RecordMode.SUBSCRIPTION);
                    more = dist.getAddedRecordProvider().retrieve(buf);
                    subCounter += buf.size();
                }
                if (!doneSub && subCounter >= ctx.expectedDistSub()) {
                    doneSub = true;
                    ((Collector) ctx.collectorDist).snapshotCounters();
                    System.out.println("Distributor #" + index + " received subscription for " +
                        (ctx.config.wildcard ? "wildcard" : ctx.config.symbols + " symbols") + " on " +
                        ctx.config.records + " record(s). Will generate data on " + generator.n + " symbols.");
                    ctx.doneDists.incrementAndGet();
                }
                buf.clear();
                buf.setMode(RecordMode.DATA);
                generator.retrieveRecordBuffer(buf);
                int cnt = buf.size();
                dist.process(buf);
                ctx.processedByDists(cnt);
            }
        }
    }

    private static class AgentThread extends Thread {
        private final int index;
        private final TestThroughputContext ctx;
        private final QDAgent agent;
        private volatile boolean signalled;

        public AgentThread(int index, TestThroughputContext ctx) {
            super("Agent-" + index);
            setPriority(Thread.MAX_PRIORITY); // try to avoid buffer overflows by giving agent high priority
            setDaemon(true);
            this.index = index;
            this.ctx = ctx;
            agent = ctx.collectorAgent[index].agentBuilder().build();
        }

        public void run() {
            subscribe();
            RecordListener listener = new RecordListener() {
                public void recordsAvailable(RecordProvider provider) {
                    if (signalled)
                        return;
                    signalled = true;
                    LockSupport.unpark(AgentThread.this);
                }
            };
            agent.setRecordListener(listener);
            RecordBuffer buf = RecordBuffer.getInstance();
            buf.setCapacityLimited(true);
            while (true) {
                boolean more = true;
                while (more) {
                    more = agent.retrieve(buf);
                    ctx.receivedByAgents(buf.size());
                    buf.clear();
                }
                while (!signalled) {
                    Thread.interrupted(); // Clear flag so that parking don't return immediately.
                    LockSupport.park();
                }
                signalled = false;
            }
        }

        private void subscribe() {
            RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
            // subscribe to symbol indexes [start, stop)
            long start = ctx.config.subsplit == 0 ? 0 :
                (long) ctx.size() * ctx.config.subsplit * index / ctx.config.agents;
            long stop = ctx.config.subsplit == 0 ? ctx.size() :
                (long) ctx.size() * ctx.config.subsplit * (index + 1) / ctx.config.agents;
            if (ctx.config.wildcard) {
                for (int rid = 0; rid < ctx.config.records; rid++)
                    sub.add(ctx.scheme.getRecord(rid), ctx.codec.getWildcardCipher(), null);
            } else {
                for (int rid = 0; rid < ctx.config.records; rid++) {
                    for (long i = start; i < stop; i++) {
                        int j = (int) (i % ctx.size());
                        sub.add(ctx.scheme.getRecord(rid), ctx.getCipher(j), ctx.getSymbol(j));
                    }
                }
            }
            agent.setSubscription(sub);
            sub.release();
            System.out.println("Agent #" + index + " subscribed to " +
                (ctx.config.wildcard ? "wildcard" : (stop - start) + " symbols") + " on " +
                ctx.config.records + " record(s).");
            ctx.doneAgents.incrementAndGet();
        }
    }

    public static void main(String[] args) throws Exception {
        new TestThroughput().go(args);
    }

    private void go(String[] args) throws Exception {
        // Init SSL server & client properties for sample
        SampleCert.init();

        TestThroughputConfig config = TestThroughputConfig.parseConfig(args);
        if (config == null) {
            TestThroughputConfig.help();
            return;
        }

        Date startTime = new Date(ManagementFactory.getRuntimeMXBean().getStartTime());
        System.out.println("===== Started at " + startTime);
        System.out.println(" START: " + TimeFormat.DEFAULT.format(startTime));
        config.dumpConfig(System.out);

        try (TestThroughputContext ctx = new TestThroughputContext(config)) {
            System.out.println("Initializing " + config.agents + " agents");
            for (int i = 0; i < config.agents; i++) {
                new AgentThread(i, ctx).start();
            }
            while (ctx.doneAgents.get() < config.agents) {
                Thread.sleep(500);
            }
            System.out.println("Initializing " + config.agents + " agents: complete");

            System.out.println("Initializing " + config.dists + " distributors");
            for (int i = 0; i < config.dists; i++) {
                new DistributorThread(i, ctx).start();
            }
            while (!ctx.isInitialized()) {
                Thread.sleep(500);
            }
            System.out.println("Initializing " + config.dists + " distributors: complete");

            // Now dumping stats every statperiod
            int statno = 0;
            if (config.warmup > 0) {
                System.out.println("Warming up for " + config.statperiod * config.warmup + " seconds...");
            }
            while (true) {
                if (ctx.dumpStats())
                    statno++;
                if (config.warmup > 0 && statno == config.warmup) {
                    System.out.println("Finished warm-up, starting main test...");
                    ctx.resetStats();
                }
                if (config.reportcounters != 0 && statno > 0 && statno % config.reportcounters == 0) {
                    String counters = ((Collector) ctx.collectorDist).getCountersSinceSnapshot().textReport();
                    ((Collector) ctx.collectorDist).snapshotCounters();
                    System.out.println(counters);
                }
                if (config.stopafter > 0 && statno >= config.stopafter + config.warmup) {
                    if (config.tukeyk > 0) {
                        System.out.println("dist outliers:" + ctx.distHist.applyTukeyFence(config.tukeyk));
                        System.out.println("agent outliers:" + ctx.agentHist.applyTukeyFence(config.tukeyk));
                    }
                    ctx.dumpReport(System.out);
                    String counters = ((Collector) ctx.collectorDist).getCountersSinceSnapshot().textReport();
                    ((Collector) ctx.collectorDist).snapshotCounters();
                    System.out.println(counters);

                    PrintStream out = new PrintStream(new FileOutputStream(config.stopreport, true));
                    out.println("===== Report at " + new Date() + " (started at " + startTime + ")");
                    out.println(" START: " + TimeFormat.DEFAULT.format(startTime));
                    config.dumpConfig(out);
                    ctx.dumpReport(out);
                    out.close();
                    return;
                }
                Thread.sleep(config.statperiod * 1000L);
            }
        }
    }
}
