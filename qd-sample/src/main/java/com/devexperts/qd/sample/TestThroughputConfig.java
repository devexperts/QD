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

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;

import java.io.PrintStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

class TestThroughputConfig {
    @Retention(RetentionPolicy.RUNTIME) @interface Doc { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @interface Hex {}

    @Doc("ticker|history|stream")
    public CollectorFactory collector = TICKER;

    @Doc("number of different symbols")
    public int symbols = 300000;

    @Doc("number of records in the data scheme")
    public int records = 1;

    @Doc("number of integer fields in each record")
    public int ifields = 6;

    @Doc("number of object fields in each record")
    public int ofields = 0;

    @Doc("mask for generated record times (first two integer fields)")
    @Hex public long timemask = 0xfff; // at most 4K different timestamps

    @Doc("mask for generated integer values (3rd and further)")
    @Hex public int ivalmask = 0xffff; // relatively small int values for QTP (3 bytes per value)

    @Doc("mask for generated object values")
    @Hex public int ovalmask = 0x3f; // at most 64 different objects

    @Doc("number of distributor threads")
    public int dists = 1;

    @Doc("number of agent threads")
    public int agents = 1;

    @Doc("splits symbols between distributors, so that each symbol is distributed by the\n" +
        "specified number of distributors; zero means that all symbols are distributed by\n" +
        "all distributors (same as setting it to the value of \"dists\")")
    public int distsplit = 0;

    @Doc("splits symbols between agents, so that each symbol is subscribed to by the\n" +
        "specified number of agents; zero means that all symbols are subscribed to by\n" +
        "all agents (same as setting it to the value of \"agents\")")
    public int subsplit = 0;

    @Doc("size of distribution data buffer")
    public int distbuf = 1024;

    @Doc("size of agent data buffer")
    public int agentbuf = 1024;

    @Doc("generate each distribution buffer with one record and symbol")
    public boolean symbatch = false;

    @Doc("enable wildcard subscription for stream")
    public boolean wildcard;

    @Doc("enables over-the-network testing")
    public boolean network;

    @Doc("separate network connection for each agent")
    public boolean multi;

    @Doc("user secure network connection")
    public boolean tls;

    @Doc("use NIO connection for network server")
    public boolean nio;

    @Doc("network port")
    public int port = 20804;

    @Doc("report stats every N seconds")
    public int statperiod = 1;

    @Doc("stop after num of stat lines, 0 -- don't stop")
    public int stopafter = 0;

    @Doc("writes report on stop to file")
    public String stopreport = "throughput.report";

    static TestThroughputConfig parseConfig(String[] args) {
        // Read args
        Properties props = new Properties();
        props.put("collector", "ticker");
    args_loop:
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.isEmpty())
                continue;
            if (i < args.length)
                for (Field f : TestThroughputConfig.class.getFields()) {
                    if (arg.equalsIgnoreCase("-" + f.getName())) {
                        if (f.getType() == boolean.class) {
                            props.put(f.getName(), "true");
                            continue args_loop;
                        } else if (i < args.length - 1) {
                            props.put(f.getName(), args[++i]);
                            continue args_loop;
                        }
                    }
                }
            System.err.println("Unrecognized: " + arg);
            return null;
        }
        // Parse
        TestThroughputConfig config = new TestThroughputConfig();
        try {
            config.collector = getCollectorFactory(props.getProperty("collector"));
            for (Field field : TestThroughputConfig.class.getFields()) {
                String value = props.getProperty(field.getName());
                if (value != null)
                    try {
                        if (field.getType() == int.class)
                            field.set(config, (int) parseNumber(value));
                        if (field.getType() == long.class)
                            field.set(config, parseNumber(value));
                        else if (field.getType() == boolean.class)
                            field.set(config, Boolean.valueOf(value));
                        else if (field.getType() == String.class)
                            field.set(config, value);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();  // should not happen.
                    }
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return null;
        }
        // Validate
        if (config.network && config.nio) {
            System.err.println("-network and -nio options can't be used together.");
            return null;
        }
        if (config.tls && !config.network) {
            System.err.println("-tls option can be used only with -network option.");
            return null;
        }
        if (config.multi && !(config.network || config.nio)) {
            System.err.println("-multi option can be used only with one of -network or -nio option.");
            return null;
        }
        if (config.agents < config.subsplit) {
            System.err.println("-subsplit option cannot be set to a value larger than -agents option.");
            return null;
        }
        if (config.dists < config.distsplit) {
            System.err.println("-distsplit option cannot be set to a value larger than -dists option.");
            return null;
        }
        if (config.collector != STREAM && config.wildcard) {
            System.err.println("-wildcard option can be used only with -collector stream.");
            return null;
        }
        if (config.wildcard && config.subsplit != 0 && config.subsplit != config.agents) {
            System.err.println("-wildcard option cannot be used with -subsplit option.");
            return null;
        }
        return config;
    }

    void dumpConfig(PrintStream out) {
        Map<String, String> sys = new TreeMap<>();
        // Properties.stringPropertyNames() is properly synchronized to avoid ConcurrentModificationException.
        for (String key : System.getProperties().stringPropertyNames())
            if (key.startsWith("com.devexperts.qd.") || key.equals("java.vm.name") || key.equals("java.version"))
                sys.put(key, System.getProperty(key));
        out.println("SYSTEM: " + QDFactory.getVersion() + " " + sys);
        try {
            for (Field f : TestThroughputConfig.class.getFields()) {
                out.println("CONFIG: " + f.getName() + " = " + toString(f, f.get(this)));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();  // should not happen
        }
    }

    private static String toString(Field f, Object value) {
        if (f.getAnnotation(Hex.class) == null)
            return String.valueOf(value);
        return "0x" + Long.toHexString(((Number) value).longValue());
    }

    private static CollectorFactory getCollectorFactory(String collector_arg) {
        CollectorFactory result = COLLECTOR_FACTORIES.get(collector_arg.toUpperCase(Locale.US));
        if (result == null)
            throw new IllegalArgumentException("Unrecognized collector: " + collector_arg);
        return result;
    }

    private static long parseNumber(String arg) {
        int mult = 1;
        if (arg.endsWith("k") || arg.endsWith("K")) {
            mult = 1000;
            arg = arg.substring(0, arg.length() - 1);
        } else if (arg.endsWith("m") || arg.endsWith("M")) {
            mult = 1000000;
            arg = arg.substring(0, arg.length() - 1);
        }
        try {
            return mult * Long.decode(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unrecognized number: " + arg);
        }
    }

    static void help() {
        System.err.println("Usage: java " + TestThroughput.class.getName());
        TestThroughputConfig def = new TestThroughputConfig();
        try {
            for (Field f : TestThroughputConfig.class.getFields()) {
                StringBuilder sb = new StringBuilder();
                sb.append(" -").append(f.getName());
                while (sb.length() < 12)
                    sb.append(' ');
                sb.append(' ');
                sb.append(f.getAnnotation(Doc.class).value().replace("\n", "\n             "));
                sb.append(", default is ").append(toString(f, f.get(def)));
                System.err.println(sb);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace(); // should not happen
        }
    }

    static interface CollectorFactory {
        QDCollector createCollector(TestThroughputConfig config, TestThroughputScheme scheme);
    }

    private static final CollectorFactory TICKER = new CollectorFactory() {
        public QDCollector createCollector(TestThroughputConfig config, TestThroughputScheme scheme) {
            return QDFactory.getDefaultFactory().createTicker(scheme);
        }

        public String toString() {
            return "TICKER";
        }
    };

    private static final CollectorFactory STREAM = new CollectorFactory() {
        public QDCollector createCollector(TestThroughputConfig config, TestThroughputScheme scheme) {
            QDStream stream = QDFactory.getDefaultFactory().createStream(scheme);
            if (config.wildcard)
                stream.setEnableWildcards(true);
            return stream;
        }

        public String toString() {
            return "STREAM";
        }
    };

    private static final CollectorFactory HISTORY = new CollectorFactory() {
        public QDCollector createCollector(TestThroughputConfig config, TestThroughputScheme scheme) {
            return QDFactory.getDefaultFactory().createHistory(scheme);
        }

        public String toString() {
            return "HISTORY";
        }
    };

    private static final Map<String, CollectorFactory> COLLECTOR_FACTORIES = new HashMap<>();

    private static void putFactory(CollectorFactory factory) {
        COLLECTOR_FACTORIES.put(factory.toString(), factory);
    }

    static {
        putFactory(STREAM);
        putFactory(TICKER);
        putFactory(HISTORY);
    }
}
