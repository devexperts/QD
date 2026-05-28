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

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.monitoring.MonitoringEndpoint;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
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

class TestThroughputContext extends SymbolList implements AutoCloseable {

    private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HHmmss");

    final TestThroughputConfig config;
    final TestThroughputScheme scheme;
    final SymbolCodec codec;
    final MonitoringEndpoint distEndpoint;
    final MonitoringEndpoint agentEndpoint;
    
    int[] ifldmask;

    QDCollector collectorDist;
    QDCollector[] collectorAgent;

    final AtomicInteger doneDists = new AtomicInteger();
    final AtomicInteger doneAgents = new AtomicInteger();
    boolean doneAll;

    Stats totalStats = new Stats();
    Stats lastStats = new Stats();
    Stats deltaStats = new Stats();
    MaxHolder maxSumRps = new MaxHolder();
    long lastTime;

    Hist distHist = new Hist();
    Hist agentHist = new Hist();

    int expectedDistSub() {
        return (config.wildcard ? 1 : config.symbols) * config.records;
    }

    static class Stats {
        public long processedByDists;
        public long receivedByAgents;
    }

    static class Hist {
        List<Long> values = new ArrayList<>();

        void add(long value) {
            values.add(value);
        }

        void clear() {
            values.clear();
        }

        String report() {
            Collections.sort(values);
            int n = values.size() - 1;
            long min = values.get(0);
            long p25 = values.get(n / 4);
            long p50 = values.get(n / 2);
            long p75 = values.get(n * 3 / 4);
            long max = values.get(n);
            long avg = values.stream().mapToLong(Long::longValue).sum() / values.size();
            return new Formatter().format(Locale.US, "%d rps avg; min %d [%d - %d - %d] %d max rps",
                avg, min, p25, p50, p75, max).toString();
        }

        List<Long> applyTukeyFence(double k) {
            if (values.size() < 16)
                return Collections.emptyList();
            Collections.sort(values);
            int n = values.size() - 1;
            long p25 = values.get(n / 4);
            long p75 = values.get(n * 3 / 4);
            long minB = (long) (p25 - (p75 - p25) * k);
            long maxB = (long) (p75 + (p75 - p25) * k);
            int p = 0;
            List<Long> outliers = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                Long v = values.get(i);
                if (v >= minB && v <= maxB) {
                    if (p != i)
                        values.set(p, v);
                    p++;
                } else {
                    outliers.add(v);
                }
            }
            for (int i = values.size(); p < i; ) {
                values.remove(--i);
            }
            return outliers;
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

        // Prepare mask for int field generator
        ifldmask = new int[config.ifields];
        for (int k = 0; k < config.ifields; k++) {
            ifldmask[k] =
                k == 0 ? (int) (config.timemask >> 32) :
                k == 1 ? (int) config.timemask : config.ivalmask;
        }

        // Generate a list of symbols
        int n = config.symbols;
        int symlen = config.symlength;
        SymbolObjectMap<Boolean> map = SymbolObjectMap.createInstance();
        Random r = new Random(1);
        int i = 0;
        int unencodedSymbols = 0;
        while (i < n) {
            String symbol = nextSymbol(r, symlen);
            int cipher = codec.encode(symbol);

            if (map.put(cipher, symbol, true) == null) {
                ciphers[i] = cipher;
                symbols[i] = symbol;
                i++;
                if (cipher == 0)
                    unencodedSymbols++;
            }
        }
        System.out.println("Created " + unencodedSymbols + " unencoded symbols out of " +
            config.symbols + " total symbols.");

        // Create collectors
        distEndpoint = MonitoringEndpoint.newBuilder()
            .withProperties(System.getProperties())
            .withProperty(MonitoringEndpoint.NAME_PROPERTY, "server")
            .acquire();
        agentEndpoint = MonitoringEndpoint.newBuilder()
            .withProperties(System.getProperties())
            .withProperty(MonitoringEndpoint.NAME_PROPERTY, "client")
            .acquire();

        collectorDist = config.collector.createCollector(config, scheme, distEndpoint.getRootStats());
        collectorAgent = new QDCollector[config.agents];
        int collectors = 1;
        if (config.network || config.nio) {
            System.out.println("Configuring network loopback connection via port "  + config.port + " ...");

            // Configure and start server
            List<MessageConnector> server = MessageConnectors.createMessageConnectors(
                new AgentAdapter.Factory(collectorDist),
                (config.tls ? "tls+" : "") + (config.nio ? "nio" : "") + ":" + config.port,
                distEndpoint.getRootStats());
            MessageConnectors.setThreadPriority(server, Thread.MAX_PRIORITY);
            MessageConnectors.startMessageConnectors(server);
            Thread.sleep(2000); // wait to start them

            // Configure and start client
            if (config.multi) {
                for (int j = 0; j < config.agents; j++) {
                    collectorAgent[j] = createClient(config);
                }
                collectors += config.agents;
            } else {
                Arrays.fill(collectorAgent, createClient(config));
                collectors++;
            }
            Thread.sleep(2000); // wait to start them
        } else {
            Arrays.fill(collectorAgent, collectorDist);
        }
        System.out.println("Created " + collectors + " collectors.");
    }

    public void close() {
        collectorDist.close();
        if (config.multi) {
            for (QDCollector qdCollector : collectorAgent) {
                qdCollector.close();
            }
        } else {
            collectorAgent[0].close();
        }
        distEndpoint.release();
        agentEndpoint.release();
    }

    private QDCollector createClient(TestThroughputConfig config) {
        QDCollector collector = config.collector.createCollector(config, scheme, agentEndpoint.getRootStats());
        List<MessageConnector> client = MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(collector),
            (config.tls ? "tls+" : "") + "127.0.0.1:" + config.port, agentEndpoint.getRootStats());
        MessageConnectors.setThreadPriority(client, Thread.MAX_PRIORITY);
        MessageConnectors.startMessageConnectors(client);
        return collector;
    }

    synchronized void processedByDists(int cnt) {
        totalStats.processedByDists += cnt;
    }

    synchronized void receivedByAgents(int cnt) {
        totalStats.receivedByAgents += cnt;
    }

    boolean dumpStats() {
        Field[] fields = Stats.class.getFields();
        try {
            synchronized (this) {
                for (Field f : fields) {
                    long total = f.getLong(totalStats);
                    long last = f.getLong(lastStats);
                    f.setLong(deltaStats, total - last);
                    f.setLong(lastStats, total);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        long now = System.currentTimeMillis();
        long delta = now - lastTime;
        boolean report = doneAll;
        if (report) {
            System.out.println(time() + " " +
                "DIST: " + perf(deltaStats.processedByDists, delta, null, distHist) + "; " +
                "AGENT: " + perf(deltaStats.receivedByAgents, delta, null, agentHist));
        }
        lastTime = now;
        doneAll = isInitialized();
        return report;
    }

    void resetStats() {
        Field[] fields = Stats.class.getFields();
        try {
            synchronized (this) {
                for (Field f : fields) {
                    f.setLong(totalStats, 0);
                    f.setLong(deltaStats, 0);
                    f.setLong(lastStats, 0);
                }
                lastTime = System.currentTimeMillis();
                distHist.clear();
                agentHist.clear();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Check if both agents and distributors are initialized and ready for testing. */
    boolean isInitialized() {
        return doneDists.get() >= config.dists && doneAgents.get() >= config.agents;
    }

    void dumpReport(PrintStream out) {
        out.println(" DIST: " + distHist.report());
        out.println("AGENT: " + agentHist.report());
    }

    private String perf(long value, long deltaTime, MaxHolder maxHolder, Hist hist) {
        long rps = 1000 * value / deltaTime;
        boolean isMax = maxHolder != null && rps > maxHolder.max;
        if (isMax)
            maxHolder.max = rps;
        hist.add(rps);
        return value + " (" + rps + " rps" + (maxHolder == null ? "" : isMax ? " * " : "   ") + ")";
    }

    private String time() {
        return dateFormat.format(new Date());
    }

    private static String nextSymbol(Random rnd, int length) {
        int len = (length > 0) ? length : rnd.nextInt(3) + rnd.nextInt(4) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (rnd.nextInt(50) == 0) {
                sb.append((char) ('0' + rnd.nextInt(10)));
            } else {
                sb.append((char) ('A' + rnd.nextInt(26)));
            }
        }
        return sb.toString();
    }
}
