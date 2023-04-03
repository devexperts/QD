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
package com.devexperts.qd.sample;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.SymbolObjectMap;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

class TestThroughputContext extends SymbolList {
    private final DateFormat DF = new SimpleDateFormat("yyyyMMdd HHmmss");

    final TestThroughputConfig config;
    final TestThroughputScheme scheme;
    final SymbolCodec codec;

    int[] ifldmask;

    QDCollector collector_dist;
    QDCollector[] collector_agent;

    final AtomicInteger done_dists = new AtomicInteger();
    final AtomicInteger done_agents = new AtomicInteger();
    boolean done_all;

    Stats total_stats = new Stats();
    Stats last_stats = new Stats();
    Stats delta_stats = new Stats();
    MaxHolder max_sum_rps = new MaxHolder();
    long last_time;

    Hist hdist = new Hist();
    Hist hagent = new Hist();

    int expectedDistSub() {
        return (config.wildcard ? 1 : config.symbols) * config.records;
    }

    static class Stats {
        public long processed_by_dists;
        public long received_by_agents;
    }

    static class Hist {
        List<Long> values = new ArrayList<Long>();
        double avg;

        void add(long value) {
            values.add(value);
            int n = values.size();
            avg = (avg * (n - 1) + value) / n;
        }

        String report() {
            Collections.sort(values);
            int n = values.size() - 1;
            long min = values.get(0);
            long p25 = values.get(n / 4);
            long p50 = values.get(n / 2);
            long p75 = values.get(n * 3 / 4);
            long max = values.get(n);
            return new Formatter().format(Locale.US, "%d rps avg; min %d [%d - %d - %d] %d max rps",
                (int) avg, min, p25, p50, p75, max).toString();
        }
    }

    static class MaxHolder {
        long max;
    }

    TestThroughputContext(TestThroughputConfig config) throws InterruptedException {
        super(config.symbols);
        this.config = config;
        this.scheme = new TestThroughputScheme(config);
        this.codec = scheme.getCodec();
        // prepare mask for intfield generator
        ifldmask = new int[config.ifields];
        for (int k = 0; k < config.ifields; k++)
            ifldmask[k] =
                k == 0 ? (int) (config.timemask >> 32) :
                k == 1 ? (int) config.timemask :
                config.ivalmask;
        // Generate a list of symbols
        int n = config.symbols;
        SymbolObjectMap<Boolean> map = SymbolObjectMap.createInstance();
        Random r = new Random(1);
        int i = 0;
        int uncoded_symbols = 0;
        while (i < n) {
            String s = nextSymbol(r);
            int cipher = codec.encode(s);
            String symbol = cipher == 0 ? s : null;
            if (map.put(cipher, symbol, true) == null) {
                ciphers[i] = cipher;
                symbols[i] = symbol;
                i++;
                if (cipher == 0)
                    uncoded_symbols++;
            }
        }
        System.out.println("Created " + uncoded_symbols + " uncoded symbols out of " + config.symbols + " total symbols.");
        // Create collectors
        collector_dist = config.collector.createCollector(config, scheme);
        collector_agent = new QDCollector[config.agents];
        int collectors = 1;
        if (config.network || config.nio) {
            System.out.println("Configuring network loopback connection via port "  + config.port + " ...");

            // Configure and start server
            List<MessageConnector> server = MessageConnectors.createMessageConnectors(
                new AgentAdapter.Factory(collector_dist),
                (config.tls ? "tls+" : "") + (config.nio ? "nio" : "") + ":" + config.port, QDStats.VOID);
            MessageConnectors.setThreadPriority(server, Thread.MAX_PRIORITY);
            MessageConnectors.startMessageConnectors(server);
            Thread.sleep(2000); // wait to start them

            // Configure and start client
            if (config.multi) {
                for (int j = 0; j < config.agents; j++)
                    collector_agent[j] = createClient(config);
                collectors += config.agents;
            } else {
                Arrays.fill(collector_agent, createClient(config));
                collectors++;
            }
            Thread.sleep(2000); // wait to start them
        } else {
            Arrays.fill(collector_agent, collector_dist);
        }
        System.out.println("Created " + collectors + " collectors.");
    }

    private QDCollector createClient(TestThroughputConfig config) {
        QDCollector collector = config.collector.createCollector(config, scheme);
        List<MessageConnector> client = MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(collector),
            (config.tls ? "tls+" : "") + "127.0.0.1:" + config.port, QDStats.VOID);
        MessageConnectors.setThreadPriority(client, Thread.MAX_PRIORITY);
        MessageConnectors.startMessageConnectors(client);
        return collector;
    }

    synchronized void processedByDists(int cnt) {
        total_stats.processed_by_dists += cnt;
    }

    synchronized void receivedByAgents(int cnt) {
        total_stats.received_by_agents += cnt;
    }

    boolean dumpStats() {
        Field[] fields = Stats.class.getFields();
        try {
            synchronized (this) {
                for (Field f : fields) {
                    Long total = (Long) f.get(total_stats);
                    Long last = (Long) f.get(last_stats);
                    f.set(delta_stats, total - last);
                    f.set(last_stats, total);
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        long cur_time = System.currentTimeMillis();
        long delta_time = cur_time - last_time;
        boolean report = done_all;
        if (report) {
            System.out.println(time() + " " +
                "DIST: " + perf(delta_stats.processed_by_dists, delta_time, null, hdist) + "; " +
                "AGENT: " + perf(delta_stats.received_by_agents, delta_time, null, hagent));
        }
        last_time = cur_time;
        done_all = done_dists.get() >= config.dists && done_agents.get() >= config.agents;
        return report;
    }

    void dumpReport(PrintStream out) {
        out.println(" DIST: " + hdist.report());
        out.println("AGENT: " + hagent.report());
    }

    private String perf(long value, long delta_time, MaxHolder max_holder, Hist hist) {
        long rps = 1000 * value / delta_time;
        boolean is_max = max_holder != null && rps > max_holder.max;
        if (is_max)
            max_holder.max = rps;
        hist.add(rps);
        return value + " (" + rps + " rps" + (max_holder == null ? "" : is_max ? " * " : "   ") +")";
    }

    private String time() {
        return DF.format(new Date());
    }

    private static String nextSymbol(Random r) {
        int len = r.nextInt(3) + r.nextInt(4) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (r.nextInt(50) == 0)
                sb.append((char) ('0' + r.nextInt(10)));
            else
                sb.append((char) ('A' + r.nextInt(26)));
        }
        return sb.toString();
    }
}
