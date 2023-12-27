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
package com.devexperts.qd.tools;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses binary multiplexor subscription dumps and creates agents that correlate to hosts in the subscription dumps.
 * Each agent creates a new connection.
 */
@ToolSummary(
    info = "A load generator based on actual production subscription dumps.",
    argString = "<address> <files>",
    arguments = {
        "<address>      -- address to connect",
        "<files>        -- files to parse"
    }
)
@ServiceProvider
public class SubscriptionDumpRepeater extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionCollector collector = new OptionCollector("all");
    private final OptionString tape = new OptionString('t', "tape", "<file>[<opts>]",
        "Tape incoming data into the specified file. See @link{tape} for more details."
    );
    private final OptionFields fields = new OptionFields();
    private final Option quiet = new Option('q', "quiet", "Be quiet (do not dump every incoming data record).");
    private final Option stamp = new Option('S', "stamp", "Print timestamp for every incoming data record.");
    private final OptionInteger topSymbols = new OptionInteger('T', "top", "<n>", "Display n top frequent symbols.");
    private final OptionName name = new OptionName("");
    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();
    private final OptionString filter = new OptionString('F', "filter", "<regex>", "Filter agents by name.");

    private final List<QDEndpoint> endpoints = new ArrayList<>();
    private ConnectionProcessor processor;

    TopSymbolsCounter topSymbolsCounter = null;

    @Override
    protected Option[] getOptions() {
        return new Option[] {logfile, collector, tape, fields, quiet, stamp, topSymbols, name, stat, html, rmi, filter};
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0)
            noArguments();
        if (args.length < 2)
            wrongNumberOfArguments();
        String address = args[0];
        log.info("Using address " + LogUtil.hideCredentials(address));

        DataScheme scheme = QDFactory.getDefaultScheme();

        if (topSymbols.isSet()) {
            int number = topSymbols.getValue();
            topSymbolsCounter = new TopSymbolsCounter(scheme.getCodec(), number);
        }

        RecordFields[] rfs = fields.createRecordFields(scheme, false);
        processor = new ConnectionProcessor("connection-processor", scheme, getQdContracts(), tape.getValue(),
            quiet.isSet(), stamp.isSet(), rfs, topSymbolsCounter);
        processor.start();

        List<DumpAgent> dumpAgents = new ArrayList<>();
        int countAgent = 0;
        for (int i = 1; i < args.length; i++) {
            List<SubscriptionDumpParser.SubRecord> subs;
            try {
                subs = SubscriptionDumpParser.readFile(args[i], scheme);
            } catch (IOException e) {
                log.error("Failed to read file " + LogUtil.hideCredentials(args[i]), e);
                continue;
            }
            subs.sort(new SubscriptionDumpParser.SubRecordComparator(new char[] { 'A' }));
            DumpAgent dumpAgent = null;
            log.info(subs.size() + " subscription entities have been collected. Launching agents...");
            String agentName = null;
            Pattern pattern = filter.isSet() ? Pattern.compile(filter.getValue()) : null;
            for (SubscriptionDumpParser.SubRecord sub : subs) {
                if (!sub.agent.name.equals(agentName)) {
                    if (dumpAgent != null)
                        dumpAgent.start();
                    agentName = sub.agent.name;
                    String name = this.name.getName().isEmpty() ? sub.agent.name : this.name.getName() + '-' +
                        countAgent;
                    dumpAgent = (pattern == null || pattern.matcher(agentName).find()) ?
                        new DumpAgentImpl(name, address, processor) : new DummyDumpAgent(name);
                    dumpAgents.add(dumpAgent);
                    countAgent++;
                }
                dumpAgent.add(sub, scheme.getCodec());
            }
            if (dumpAgent != null)
                dumpAgent.start();
        }
        List<DumpStatistics> dumpStatistics = new ArrayList<>();
        for (DumpAgent dumpAgent : dumpAgents) {
            dumpStatistics.add(dumpAgent.statistic());
        }
        dumpStatistics.sort(Collections.reverseOrder());
        StringBuilder builder = new StringBuilder("All agents are up and running:\n");
        for (DumpStatistics ds : dumpStatistics) {
            ds.print(builder);
        }
        builder.delete(builder.length() - 1, builder.length());
        log.info(builder.toString());
    }

    private Set<QDContract> getQdContracts() {
        List<QDCollector.Factory> factories = collector.getCollectorFactories();
        Set<QDContract> contracts = new HashSet<>();
        for (QDCollector.Factory factory : factories) {
            contracts.add(factory.getContract());
        }
        return contracts;
    }

    private static interface DumpAgent {
        public void add(SubscriptionDumpParser.SubRecord sub, SymbolCodec codec);

        public void start();

        public DumpStatistics statistic();
    }

    private class DummyDumpAgent implements DumpAgent {
        private final DumpStatistics dumpStatistics;

        private DummyDumpAgent(String name) {
            this.dumpStatistics = new DumpStatistics("DUMMY " + name);
            log.info("Dummy agent \"" + name + "\" was created.");
        }

        @Override
        public void add(SubscriptionDumpParser.SubRecord sub, SymbolCodec codec) {
            dumpStatistics.increment(sub.agent.collector.contract);
        }

        @Override
        public void start() {
            // nothing
        }

        @Override
        public DumpStatistics statistic() {
            return dumpStatistics;
        }
    }

    private class DumpAgentImpl implements DumpAgent {
        private final Map<String, RecordBuffer> recordBufferByContract = new HashMap<>();
        private final String name;
        private final QDEndpoint endpoint;
        private final DumpStatistics dumpStatistics;
        private final String address;
        ConnectorRecordsSymbols.Listener listener;

        private DumpAgentImpl(String name, String address, ConnectorRecordsSymbols.Listener listener) {
            this.name = name;
            this.dumpStatistics = new DumpStatistics(name);
            this.address = address;
            this.listener = listener;
            // JMX bean naming rules
            endpoint = collector.createEndpoint(name.replace(',', ' ').replace('=', '-'));
            if (topSymbols.isSet())
                endpoint.registerMonitoringTask(topSymbolsCounter.createReportingTask());
            endpoints.add(endpoint);
            for (QDCollector collector : endpoint.getCollectors()) {
                recordBufferByContract.put(collector.getContract().toString(),
                    RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION));
            }
            log.info("Agent \"" + name + "\" was created.");
        }

        @Override
        public void add(SubscriptionDumpParser.SubRecord sub, SymbolCodec codec) {
            RecordBuffer recordBuffer = recordBufferByContract.get(sub.agent.collector.contract);
            if (recordBuffer != null) {
                recordBuffer.add(sub.rec.record, codec.encode(sub.symbol), sub.symbol)
                    .setTime(((long) sub.t0() << 32) | ((long) sub.t1() & 0xFFFFFFFFL));
            }
            dumpStatistics.increment(sub.agent.collector.contract);
        }

        @Override
        public void start() {
            for (QDCollector collector : endpoint.getCollectors()) {
                RecordBuffer recordBuffer = recordBufferByContract.get(collector.getContract().toString());
                QDAgent agent = collector.agentBuilder().withOptSet(ProtocolOption.SUPPORTED_SET).build();
                agent.setSubscription(recordBuffer);
                recordBuffer.rewind();
                MessageType message = MessageType.forData(collector.getContract());
                agent.setRecordListener(provider -> listener.recordsAvailable(provider, message));
            }
            List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
                new DistributorAdapter.Factory(endpoint, null), address, endpoint.getRootStats());
            endpoint.addConnectors(connectors).startConnectors();
            log.info("Agent \"" + name + "\" is up and running.");
        }

        @Override
        public DumpStatistics statistic() {
            return dumpStatistics;
        }
    }

    private static class DumpStatistics implements Comparable<DumpStatistics> {
        private final Map<String, AtomicInteger> statistic =
            new HashMap<>((int) (QDContract.values().length / 0.75 + 1));
        private final String nameAgent;
        private int total = 0;

        private DumpStatistics(String nameAgent) {
            this.nameAgent = nameAgent;
            for (QDContract contract : QDContract.values()) {
                statistic.computeIfAbsent(contract.toString(), c -> new AtomicInteger(0));
            }
        }

        void increment(String contract) {
            statistic.get(contract).getAndIncrement();
            total++;
        }

        @Override
        public int compareTo(DumpStatistics o) {
            return Integer.compare(this.total, o.total);
        }

        public void print(StringBuilder builder) {
            builder
                .append("total=")
                .append(total)
                .append(" (");
            statistic.forEach((contract, count) ->
                builder.append(contract).append('=').append(count.get()).append(", "));
            builder.delete(builder.length() - ", ".length(), builder.length());
            builder.append(')')
                .append(' ')
                .append(nameAgent)
                .append('\n');
        }
    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return endpoints.stream().flatMap(e -> e.getConnectors().stream()).collect(Collectors.toList());
    }

    @Override
    public List<Closeable> closeOnExit() {
        List<Closeable> closeables = new ArrayList<>(endpoints);
        closeables.add(processor);
        return closeables;
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(SubscriptionDumpRepeater.class, args);
    }
}
