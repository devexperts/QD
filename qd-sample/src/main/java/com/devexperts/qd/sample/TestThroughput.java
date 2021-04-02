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

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.samplecert.SampleCert;

import java.io.FileOutputStream;
import java.io.PrintStream;
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
            dist = ctx.collector_dist.distributorBuilder().build();
        }

        @Override
        public void run() {
            final TestThroughputGenerator generator = new TestThroughputGenerator(index, ctx);
            boolean doneSub = false;
            int subCounter = 0;

            while (true) {
                RecordBuffer buf = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
                dist.getAddedSubscriptionProvider().retrieveSubscription(buf);
                subCounter += buf.size();
                if (!doneSub && subCounter >= ctx.expectedDistSub()) {
                    doneSub = true;
                    System.out.println("Distributor #" + index + " received subscription for " +
                        (ctx.config.wildcard ? "wildcard" : ctx.config.symbols + " symbols") + " on " +
                        ctx.config.records + " record(s). Will generate data on " + generator.n + " symbols.");
                    ctx.done_dists.incrementAndGet();
                }
                buf.clear();
                buf.setMode(RecordMode.DATA);
                generator.retrieveRecordBuffer(buf);
                int cnt = buf.size();
                dist.processData(buf);
                ctx.processedByDists(cnt);
                buf.release();
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
            agent = ctx.collector_agent[index].agentBuilder().build();
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
            while (true) {
                boolean more;
                do {
                    RecordBuffer buf = RecordBuffer.getInstance();
                    more = agent.retrieveData(buf);
                    ctx.receivedByAgents(buf.size());
                    buf.release();
                } while (more);
                while (!signalled) {
                    interrupted(); // Clear flag so that parking don't return immediately.
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
            ctx.done_agents.incrementAndGet();
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
        config.dumpConfig(System.out);
        TestThroughputContext ctx = new TestThroughputContext(config);
        for (int i = 0; i < config.dists; i++)
            new DistributorThread(i, ctx).start();
        System.out.println("Created " + config.dists + " distributor threads.");
        for (int i = 0; i < config.agents; i++)
            new AgentThread(i, ctx).start();
        System.out.println("Created " + config.agents + " agent threads.");
        // Now dumping stats every 10 seconds
        int statno = 0;
        while (true) {
            if (ctx.dumpStats())
                statno++;
            if (config.stopafter > 0 && statno >= config.stopafter) {
                ctx.dumpReport(System.out);
                PrintStream out = new PrintStream(new FileOutputStream(config.stopreport, true));
                out.println("===== Report at " + new Date());
                config.dumpConfig(out);
                ctx.dumpReport(out);
                out.close();
                return;
            }
            Thread.sleep(config.statperiod * 1000L);
        }
    }
}
