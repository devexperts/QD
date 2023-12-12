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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Two data sources comparing tool.
 */
@ToolSummary(
    info = "Compares data received from two sources.",
    argString = {
        "<address> <records> <symbols> [<date-time>]"
    },
    arguments = {
        "<address>   -- address for main connection (see @link{address})",
        "<records>   -- record names pattern (see @link{filters})",
        "<symbols>   -- comma-separated list of symbols",
        "<date-time> -- Date and time for history subscription in standard format (see @link{time format})."
    }
)
@ServiceProvider
public class Compare extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();
    private final OptionCollector collector = new OptionCollector("ticker");
    private final OptionString otherAddress = new OptionString('A', "other-address", "<address>", "Another address to compare with.");
    private final OptionCollector otherCollector = new OptionCollector('C', "other-collector", "Compare with another collector (same as --collector by default).", "ticker");
    private final OptionString otherRecord = new OptionString('R', "other-record", "<rec>", "Compare record with the other. Works only with one record in <records>.");
    private final OptionFields fields = new OptionFields();
    private final OptionName names = new OptionName("compare1/compare2");
    private final OptionStat stat = new OptionStat();
    private final OptionManagementHtml html = OptionManagementHtml.getInstance();
    private final OptionManagementRmi rmi = OptionManagementRmi.getInstance();

    private final List<MessageConnector> connectors = new ArrayList<>();
    private QDEndpoint endpoint1;
    private QDEndpoint endpoint2;

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile, collector, otherAddress, otherCollector, otherRecord, fields, names, stat, html, rmi };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length < 3 || args.length > 5) {
            wrongNumberOfArguments();
        }

        if (!(otherAddress.isSet() || otherCollector.isSet() || otherRecord.isSet())) {
            throw new OptionParseException("One of --other-<something> options must be set.");
        }
        if (!stat.isSet()) {
            throw new OptionParseException(stat + " option must be set.");
        }

        String address = args[0];

        DataScheme scheme = QDFactory.getDefaultScheme();

        int[] remap = null;
        RecordFields[] recordFieldsArr = fields.createRecordFields(scheme, true);

        String recordList = args[1];
        String symbolList = args[2];
        String dateTimeStr = null;
        if (args.length > 3)
            dateTimeStr = args[3];
        if (args.length > 4)
            dateTimeStr += "-" + args[4];

        DataRecord[] records = Tools.parseRecords(recordList, scheme);
        String[] symbols = Tools.parseSymbols(symbolList, scheme);
        long millis = dateTimeStr == null ? 0 : TimeFormat.DEFAULT.parse(dateTimeStr).getTime();

        ConnectorRecordsSymbols connector1 = new ConnectorRecordsSymbols(records, symbols, millis);
        ConnectorRecordsSymbols connector2;

        if (otherRecord.isSet()) {
            if (records.length != 1) {
                System.err.println("You must use " + otherRecord + " with exactly one source record");
                return;
            }
            DataRecord record1 = records[0];
            DataRecord record2 = scheme.findRecordByName(otherRecord.getValue());
            if (record2 == null) {
                System.err.println("Record \"" + otherRecord.getValue() + "\" is not found");
                return;
            }
            remap = new int[scheme.getRecordCount()];
            Arrays.fill(remap, -1);
            remap[record1.getId()] = record2.getId();
            log.info("Comparing fields \"" + recordFieldsArr[record1.getId()] + "\" vs \"" + recordFieldsArr[record2.getId()] + "\"");
            if (recordFieldsArr[record2.getId()].getIntFieldCount() != recordFieldsArr[record1.getId()].getIntFieldCount() ||
                recordFieldsArr[record2.getId()].getObjFieldCount() != recordFieldsArr[record1.getId()].getObjFieldCount())
            {
                System.err.println("Number of fields in compared records \"" + record1 + "\" and \"" + record2 + "\" does not match");
                return;
            }

            connector2 = new ConnectorRecordsSymbols(new DataRecord[]{record2}, symbols, millis);
        } else {
            connector2 = connector1;
        }

        log.info("Using address " + LogUtil.hideCredentials(address));
        endpoint1 = collector.createEndpoint(names.getName(0));
        Processor processor1 = new Processor(scheme, recordFieldsArr);
        connector1.subscribe(endpoint1, processor1);
        Connect.connectToDataSource(endpoint1, address);
        connectors.addAll(endpoint1.getConnectors());

        if (otherAddress.isSet())
            log.info("Using other address " + LogUtil.hideCredentials(otherAddress.getValue()));

        endpoint2 = (otherCollector.isSet() ? otherCollector : collector).createEndpoint(names.getName(1));
        Processor processor2 = new Processor(scheme, recordFieldsArr);
        connector2.subscribe(endpoint2, processor2);
        Connect.connectToDataSource(endpoint2, otherAddress.isSet() ? otherAddress.getValue() : address);
        connectors.addAll(endpoint2.getConnectors());

        endpoint1.registerMonitoringTask(new Comparer(scheme, processor1.buffers, processor2.buffers, names.getName(0), names.getName(1), remap));
    }

    private static class Processor implements ConnectorRecordsSymbols.Listener {
        final SymbolObjectMap<CompareBuffer>[] buffers;
        private final RecordFields[] rfs;
        private final RecordBuffer localBuf = new RecordBuffer(RecordMode.FLAGGED_DATA);

        @SuppressWarnings("unchecked")
        Processor(DataScheme scheme, RecordFields[] rfs) {
            this.buffers = (SymbolObjectMap<CompareBuffer>[]) new SymbolObjectMap[scheme.getRecordCount()];
            this.rfs = rfs;
        }

        @Override
        public void recordsAvailable(RecordProvider provider, MessageType message) {
            localBuf.clear();
            provider.retrieve(localBuf);
            long timestamp = System.currentTimeMillis();
            for (RecordCursor cursor; (cursor = localBuf.next()) != null;) {
                int recordId = cursor.getRecord().getId();
                SymbolObjectMap<CompareBuffer> map = buffers[recordId];
                if (map == null)
                    map = buffers[recordId] = SymbolObjectMap.createInstance();
                CompareBuffer local = map.get(cursor.getCipher(), cursor.getSymbol());
                if (local == null)
                    map.put(cursor.getCipher(), cursor.getSymbol(), local = new CompareBuffer(rfs[recordId]));
                local.add(timestamp, cursor);
            }
            localBuf.clear();
        }
    }

    @Override
    public List<MessageConnector> mustWaitWhileActive() {
        return connectors;
    }

    @Override
    public List<Closeable> closeOnExit() {
        return Arrays.asList(endpoint1, endpoint2);
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Compare.class, args);
    }
}
