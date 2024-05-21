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
package com.devexperts.qd.tools;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.StreamInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.SubscriptionDumpVisitor;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses binary multiplexor subscription dumps and writes data as a text file.
 */
@ToolSummary(
    info = "Parses collector management subscription dumps and writes data as a text file.",
    argString = "<files>",
    arguments = {
        "<files> -- files to parse"
    }
)
@ServiceProvider
public class SubscriptionDumpParser extends AbstractTool {
    private static final Logging log = Logging.getLogging(SubscriptionDumpParser.class);

    private static final String DEFAULT_COLUMNS = "sRa";
    public static final String ALL_COLUMNS = "'cCsrRaAtT'";

    private final OptionString columns = new OptionString('c', "columns", ALL_COLUMNS,
        "Columns to output and their order, specified as (c)collector/(s)ymbol/(r)ecord/(a)gent/(t)ime. " +
        "Default is '" + DEFAULT_COLUMNS + "'. Capital letters 'CRA' mean printing a name instead of a number. " +
        "'t' prints the first int of time, 'T' prints the second one.");
    private final OptionString sort = new OptionString('s', "sort", ALL_COLUMNS,
        "Columns for sorting, specified as in columns. Default is none. " +
        "Prepend '-' to column name for reverse soring.");
    private final OptionString group = new OptionString('g', "group", ALL_COLUMNS,
        "Groups by a specified set of columns, implies --" + sort.getFullName() + " and --" + columns.getFullName() +
        ", count in group is printed at the beginning. Pipe output to external 'sort' in order to order by count.");
    private final OptionString output = new OptionString('o', "output", "<file>",
        "Output file, by default 'subscription.txt'.");

    @Override
    protected Option[] getOptions() {
        return new Option[] {columns, sort, group, output};
    }

    // ---------------- methods ----------------

    static class CollectorRecord {
        final int num;
        final int id;
        final String keyProperties;
        final String contract;
        final boolean hasTime;
        final String name;

        CollectorRecord(int num, int id, String keyProperties, String contract, boolean hasTime) {
            this.num = num;
            this.id = id;
            this.keyProperties = keyProperties;
            this.contract = contract;
            this.hasTime = hasTime;
            // build name
            StringBuilder sb = new StringBuilder();
            sb.append(contract);
            if (keyProperties != null)
                sb.append("[").append(keyProperties).append("]");
            else
                sb.append("@").append(Integer.toHexString(id));
            this.name = sb.toString();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class RecRecord {
        final int rid;
        final String name;
        final DataRecord record;

        RecRecord(int rid, String name, DataRecord record) {
            this.rid = rid;
            this.name = name;
            this.record = record;
        }
    }

    static class AgentRecord {
        final int num;
        final CollectorRecord collector;
        final int aid;
        final String name;

        AgentRecord(int num, CollectorRecord collector, int aid, String name) {
            this.num = num;
            this.collector = collector;
            this.aid = aid;
            this.name = name;
        }
    }

    static class SubRecord {
        final RecRecord rec;
        final AgentRecord agent;
        final String symbol;

        SubRecord(RecRecord rec, AgentRecord agent, String symbol) {
            this.rec = rec;
            this.agent = agent;
            this.symbol = symbol;
        }

        public int t0() {
            return 0;
        }

        public int t1() {
            return 0;
        }
    }

    static class SubRecordTime extends SubRecord {
        final int t0;
        final int t1;

        SubRecordTime(RecRecord rec, AgentRecord agent, String symbol, int t0, int t1) {
            super(rec, agent, symbol);
            this.t0 = t0;
            this.t1 = t1;
        }

        @Override
        public int t0() {
            return t0;
        }

        @Override
        public int t1() {
            return t1;
        }
    }

    static class SubRecordComparator implements Comparator<SubRecord> {
        final char[] sortOrder;

        SubRecordComparator(char[] sortOrder) {
            this.sortOrder = sortOrder;
        }

        @Override
        public int compare(SubRecord r1, SubRecord r2) {
            boolean reverse = false;
        loop:
            for (char c : sortOrder) {
                int diff;
                switch (c) {
                case '-': reverse = true; continue loop; // continue to next char
                case 'c': diff = r1.agent.collector.num - r2.agent.collector.num; break;
                case 'C': diff = r1.agent.collector.name.compareTo(r2.agent.collector.name); break;
                case 's': diff = r1.symbol.compareTo(r2.symbol); break;
                case 'r': diff = r1.rec.rid - r2.rec.rid; break;
                case 'R': diff = r1.rec.name.compareTo(r2.rec.name); break;
                case 'a': diff = r1.agent.num - r2.agent.num; break;
                case 'A': diff = r1.agent.name.compareTo(r2.agent.name); break;
                case 't': diff = r1.t0() < r2.t0() ? -1 : r1.t0() > r2.t0() ? 1 : 0; break;
                case 'T': diff = r1.t1() < r2.t1() ? -1 : r1.t1() > r2.t1() ? 1 : 0; break;
                default: throw new BadToolParametersException("Unknown sorting mode '" + c + "'. Only " + ALL_COLUMNS + " are allowed");
                }
                if (diff != 0)
                    return reverse ? -diff : diff;
                reverse = false; // reset reverse for next char
            }
            return 0;
        }
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();
        if (group.isSet() && (sort.isSet() || columns.isSet())) {
            throw new BadToolParametersException(group + " implies --" + sort.getFullName() +
                " and --" + columns.getFullName() + ", don't specify them together");
        }

        List<SubRecord> subs = new ArrayList<>();
        for (String fileName : args) {
            try {
                subs.addAll(readFile(fileName, QDFactory.getDefaultScheme()));
            } catch (IOException e) {
                log.error("Failed to read file " + LogUtil.hideCredentials(fileName), e);
            }
        }

        // group or sort as specified
        SubRecordComparator comparator = null;
        if (group.isSet())
            comparator = new SubRecordComparator(group.getValue().toCharArray());
        else if (sort.isSet())
            comparator = new SubRecordComparator(sort.getValue().toCharArray());

        if (comparator != null)
            Collections.sort(subs, comparator);

        if (!subs.isEmpty()) {
            String outputFile = output.isSet() ? output.getValue() : "subscription.txt";
            try {
                dumpToFile(subs, outputFile, group.isSet() ? comparator : null);
            } catch (IOException e) {
                log.error("Failed to dump to " + LogUtil.hideCredentials(outputFile), e);
            }
        }
    }

    static List<SubRecord> readFile(String fileName, DataScheme scheme) throws IOException {
        SymbolCodec codec = scheme.getCodec();
        SymbolCodec.Reader symbolReader = codec.createReader();

        File file = new File(fileName);
        try (BufferedInput in = new StreamInput(new FileInputStream(file), 100000)) {
            // read and check header
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, SubscriptionDumpVisitor.MAGIC))
                throw new IOException("Invalid magic at the beginning of file");
            int version = in.readCompactInt();
            if (version != SubscriptionDumpVisitor.VERSION_1 && version != SubscriptionDumpVisitor.VERSION_2)
                throw new IOException("Unsupported version " + version);
            long time = in.readCompactLong();
            String qdVersion = readString(in, version);
            String schemeName = readString(in, version);
            String codecName = readString(in, version);
            log.info("File " + LogUtil.hideCredentials(fileName) + " was written at " + TimeFormat.DEFAULT.format(time) +
                " by " + qdVersion + " with scheme " + schemeName + " and codec " + codecName);
            if (!schemeName.equals(scheme.getClass().getName()))
                throw new IOException("Wrong scheme");
            if (!codecName.equals(codec.getClass().getName()))
                throw new IOException("Wrong codec");

            // record map is common for all collectors
            Map<Integer, RecRecord> recordMap = new HashMap<>();

            int collectorCount = 0;
            int agentCount = 0;
            List<SubRecord> subs = new ArrayList<>();
            // read until end of file
            while (true) {
                // read collector
                int cid = in.readCompactInt();
                if (cid == -1)
                    break;
                int num = ++collectorCount;
                CollectorRecord collector = new CollectorRecord(num, cid, readString(in, version), readString(in, version), in.readBoolean());
                log.info("Reading collector " + num + ": " + collector + (collector.hasTime ? " with time" : ""));

                Map<Integer, AgentRecord> agentMap = new HashMap<>();

                while (true) {
                    // read rec
                    int rid = in.readCompactInt();
                    if (rid == -1)
                        break;
                    RecRecord rec;
                    if (rid < 0) {
                        // new rec
                        rid = -rid - 2;
                        String recordName = readString(in, version);
                        if (recordName == null) {
                            rec = new RecRecord(rid, "" + rid, null);
                        } else {
                            DataRecord record = scheme.findRecordByName(recordName);
                            if (record != null && collector.hasTime && !record.hasTime())
                                record = null;
                            rec = new RecRecord(rid, recordName, record);
                        }
                        recordMap.put(rid, rec);
                    } else {
                        rec = recordMap.get(rid);
                        if (rec == null) {
                            rec = new RecRecord(rid, "unknown." + rid, null);
                            recordMap.put(rid, rec);
                        }
                    }

                    // read symbol
                    symbolReader.readSymbol(in, null);
                    String symbol = codec.decode(symbolReader.getCipher(), symbolReader.getSymbol());

                    while (true) {
                        // read agent
                        int aid = in.readCompactInt();
                        if (aid == -1)
                            break;
                        AgentRecord agent;
                        if (aid < 0) {
                            // new agent
                            aid = -aid - 2;
                            String keyProperties = readString(in, version);
                            agent = new AgentRecord(++agentCount, collector, aid, keyProperties == null ? "aid=" + aid : keyProperties);
                            agentMap.put(aid, agent);
                        } else {
                            agent = agentMap.get(aid);
                            if (agent == null) {
                                agent = new AgentRecord(++agentCount, collector, aid, "unknown.aid=" + aid);
                                agentMap.put(aid, agent);
                            }
                        }

                        // create subscription record
                        subs.add(collector.hasTime ?
                            new SubRecordTime(rec, agent, symbol, in.readCompactInt(), in.readCompactInt()) :
                            new SubRecord(rec, agent, symbol));
                    }
                }
            }
            if (in.read() != -1)
                throw new IOException("End of file expected");
            return subs;
        }
    }

    private static String readString(BufferedInput in, int version) throws IOException {
        return version == SubscriptionDumpVisitor.VERSION_1 ? in.readUTF() : in.readUTFString();
    }

    // ========== Parser internal state ==========

    private void dumpToFile(List<SubRecord> subs, String file, SubRecordComparator groupComparator) throws IOException {
        char[] printFormat = (group.isSet() ? group.getValue() :
            columns.isSet() ? columns.getValue() : DEFAULT_COLUMNS).toCharArray();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file), 100000))) {
            int n = subs.size();
            log.info(n + " subscription entities have been collected, writing to " + LogUtil.hideCredentials(file));
            for (int i = 0; i < n; i++) {
                SubRecord sub = subs.get(i);
                if (groupComparator != null) {
                    int count = 1;
                    while (i + 1 < n && groupComparator.compare(sub, subs.get(i + 1)) == 0) {
                        i++;
                        count++;
                    }
                    out.print(count);
                    out.print('\t');
                }
                for (int j = 0; j < printFormat.length; j++) {
                    char c = printFormat[j];
                    switch (c) {
                    case 'c':
                        out.print(sub.agent.collector.num);
                        break;
                    case 'C':
                        out.print(sub.agent.collector.name);
                        break;
                    case 's':
                        out.print(sub.symbol);
                        break;
                    case 'r':
                        out.print(sub.rec.rid);
                        break;
                    case 'R':
                        out.print(sub.rec.name);
                        break;
                    case 'a':
                        out.print(sub.agent.num);
                        break;
                    case 'A':
                        out.print(sub.agent.name);
                        break;
                    case 't':
                        out.print(sub.rec.record == null || !sub.agent.collector.hasTime ? "N/A" : sub.rec.record.getIntField(0).toString(sub.t0()));
                        break;
                    case 'T':
                        out.print(sub.rec.record == null || !sub.agent.collector.hasTime ? "N/A" : sub.rec.record.getIntField(1).toString(sub.t1()));
                        break;
                    default:
                        throw new BadToolParametersException("Unknown column code '" + c + "'. Only " + ALL_COLUMNS + " are allowed");
                    }
                    if (j < printFormat.length - 1)
                        out.print('\t');
                }
                out.println();
            }
        }
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(SubscriptionDumpParser.class, args);
    }
}
