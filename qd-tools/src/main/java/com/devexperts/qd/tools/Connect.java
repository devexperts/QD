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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;

/**
 * Connect tool with taping into file functionality.
 */
@ToolSummary(
    info = "Connects to specified address(es).",
    argString = {
        "<address> <records> <symbols> [<date-time>]",
        "<address> <subscription-address>"
    },
    arguments = {
        "<address>              -- address to connect for data (see @link{address})",
        "<subscription-address> -- address to take subscription from",
        "<records>              -- record names pattern (see @link{filters})",
        "<symbols>              -- comma-separated list of symbols, or \"all\" for a wildcard subscription",
        "<date-time>            -- Date and time for history subscription in standard format (see @link{time format})."
    }
)
@ServiceProvider
public class Connect extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionCollector collector = new OptionCollector("ticker");
    private final OptionStripe stripe = new OptionStripe();
    private final OptionString tape = new OptionString('t', "tape", "<file>[<opts>]",
        "Tape incoming data into the specified file. See @link{tape} for more details."
    );
    private final OptionFields fields = new OptionFields();
    private final Option quiet = new Option('q', "quiet", "Be quiet (do not dump every incoming data record).");
    private final Option stamp = new Option('S', "stamp", "Print timestamp for every incoming data record.");
    private final OptionInteger topSymbols = new OptionInteger('T', "top", "<n>", "Display n top frequent symbols.");
    private final OptionName name = new OptionName("connect");
    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();

    private QDEndpoint endpoint;
    private ConnectionProcessor processor;
    private List<MessageConnector> subscriptionConnectors;

    @Override
    protected Option[] getOptions() {
        return new Option[] {
            logfile, collector, stripe, tape, fields, quiet, stamp, topSymbols, name, stat, html, rmi
        };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length < 2 || args.length > 5) {
            wrongNumberOfArguments();
        }

        boolean clone_subscription = args.length == 2;

        String address = args[0];
        log.info("Using address " + LogUtil.hideCredentials(address));

        DataScheme scheme = QDFactory.getDefaultScheme();
        endpoint = collector.createEndpoint(name.getName());

        TopSymbolsCounter topSymbolsCounter = null;
        if (topSymbols.isSet()) {
            int number = topSymbols.getValue();
            topSymbolsCounter = new TopSymbolsCounter(scheme.getCodec(), number);
            endpoint.registerMonitoringTask(topSymbolsCounter.createReportingTask());
        }

        RecordFields[] rfs = fields.createRecordFields(scheme, false);
        processor = new ConnectionProcessor(endpoint, tape.getValue(), quiet.isSet(), stamp.isSet(), rfs, topSymbolsCounter);
        processor.start();

        if (clone_subscription) {
            String subscription_address = args[1];
            log.info("Using subscription address " + LogUtil.hideCredentials(subscription_address));
            subscriptionConnectors = MessageConnectors.createMessageConnectors(
                new SubscriptionAdapter.Factory(endpoint, null, processor, processor.dataVisitor), subscription_address, QDStats.VOID);
            MessageConnectors.startMessageConnectors(subscriptionConnectors);
        } else {
            String record_list = args[1];
            String symbol_list = args[2];
            String date_time = null;
            if (args.length > 3)
                date_time = args[3];
            if (args.length > 4)
                date_time += "-" + args[4];

            DataRecord[] records = Tools.parseRecords(record_list, scheme);
            String[] symbols = Tools.parseSymbols(symbol_list, scheme);
            long millis = date_time == null ? 0 : TimeFormat.DEFAULT.parse(date_time).getTime();
            ConnectorRecordsSymbols connector = new ConnectorRecordsSymbols(records, symbols, millis);
            connector.subscribe(endpoint, processor);
        }

        connectToDataSource(endpoint, address);
    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return endpoint.getConnectors();
    }

    @Override
    public List<Closeable> closeOnExit() {
        return Arrays.asList(endpoint, processor, () -> {
            if (subscriptionConnectors != null) subscriptionConnectors.forEach(messageConnector -> {
                try {
                    messageConnector.stopAndWait();
                } catch (InterruptedException ignore) {
                }
            });
        });
    }

    public static void connectToDataSource(QDEndpoint endpoint, String address) {
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(endpoint, null), address, endpoint.getRootStats());
        endpoint.addConnectors(connectors).startConnectors();
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Connect.class, args);
    }
}
