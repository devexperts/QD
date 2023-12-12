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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.CollectorDebug;
import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.dump.DebugDumpImpl;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.BuiltinFields;
import com.devexperts.qd.qtp.MessageType;

import java.util.List;

public abstract class CollectorManagementImplBase extends CollectorManagement implements CollectorMXBean {
    private static final int REPORT_ROWS_LIMIT = 10_000; // to avoid accidental OOM

    private static final Logging log = Logging.getLogging(CollectorManagementImplBase.class);

    protected final String keyProperties;
    protected final String name;

    protected CollectorManagementImplBase(DataScheme scheme, QDContract contract, String keyProperties, String name) {
        super(scheme, contract);
        this.keyProperties = keyProperties;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    protected abstract List<Collector> getCollectors();

    @Override
    public int getCollectorCount() {
        return getCollectors().size();
    }

    @Override
    public void verifyCollectors() {
        for (Collector c : getCollectors())
            c.verify(CollectorDebug.DEFAULT, null);
    }

    @Override
    public void dumpSubscriptionToFile(String file) {
        SubscriptionDumpImpl.makeDump(file, scheme, getCollectors());
    }

    @Override
    public void dumpCollectorsToFile(String file) {
        DebugDumpImpl.makeDump(file, getCollectors());
    }

    @Override
    public String reportData(String recordName, String symbol, boolean boundsOnly, String format) {
        log.info("reportData(" + recordName + "," + symbol + "," + boundsOnly + "," + format + ")");
        ReportBuilder rb = new ReportBuilder(format);
        try {
            for (Collector collector : getCollectors())
                reportDataImpl(rb, collector, recordName, symbol, boundsOnly);
            log.info("reportData = " + rb.getLineCount() + " lines");
            return rb.toString();
        } catch (final Throwable t) {
            log.error("reportData failed", t);
            throw t;
        }
    }

    @Override
    public String reportSubscription(String recordName, String symbol, String format) {
        log.info("reportSubscription(" + recordName + "," + symbol + "," + format + ")");
        try {
            ReportBuilder rb = new ReportBuilder(format);
            for (Collector collector : getCollectors())
                reportSubscriptionImpl(rb, collector, recordName, symbol);
            log.info("reportSubscription = " + rb.getLineCount() + " lines");
            return rb.toString();
        } catch (final Throwable t) {
            log.error("reportSubscription failed", t);
            throw t;
        }
    }

    private void reportDataImpl(ReportBuilder rb, Collector collector, String recordName, String symbol, boolean boundsOnly) {
        QDContract contract = collector.getContract();
        if (!contract.hasSnapshotData())
            return;
        rb.header(nameCollector(collector), ReportBuilder.HEADER_LEVEL_COLLECTOR);
        DataScheme scheme = collector.getScheme();
        boolean allSymbols = "*".equals(symbol);
        boolean allRecords = "*".equals(recordName);
        int cipher = allSymbols ? 0 : scheme.getCodec().encode(symbol);
        DataRecord record = allRecords ? null : scheme.findRecordByName(recordName);
        if (!allRecords && record == null) {
            rb.message("No such record in scheme");
            return;
        }
        ReportDataSink sink = new ReportDataSink(rb, contract, record, cipher, symbol, allSymbols, allRecords, boundsOnly);
        sink.begin();
        if (!allRecords && !allSymbols) {
            // fast path for a single record-symbol
            RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
            sub.add(record, cipher, symbol);
            collector.examineDataBySubscription(sink, sub);
            sub.release();
        } else {
            // everything else like full data scan (slow path)
            collector.examineData(sink);
        }
        sink.end();
    }

    private static class ReportDataSink extends AbstractRecordSink {
        private final ReportBuilder rb;
        private final QDContract contract;
        private final DataRecord record;
        private final int cipher;
        private final String symbol;
        private final boolean allSymbols;
        private final boolean allRecords;
        private final boolean boundsOnly;

        private int totalRows;
        private int shownRows;

        private RecordCursor.Owner lastOwner;
        private RecordCursor last;
        private boolean hasLast;

        ReportDataSink(ReportBuilder rb, QDContract contract, DataRecord record, int cipher, String symbol,
            boolean allSymbols, boolean allRecords, boolean boundsOnly)
        {
            this.rb = rb;
            this.contract = contract;
            this.record = record;
            this.cipher = cipher;
            this.symbol = symbol;
            this.allSymbols = allSymbols;
            this.allRecords = allRecords;
            this.boundsOnly = boundsOnly;
        }

        void begin() {
            if (contract == QDContract.HISTORY && boundsOnly)
                rb.message("Note: Only first and last rows are shown");
            rb.beginTable().newRow();
            rb.td("Record");
            rb.td("Symbol");
            if (record == null)
                return; // will not show headers
            for (int i = 0; i < record.getIntFieldCount(); i++)
                rb.td(record.getIntField(i).getLocalName());
            for (int i = 0; i < record.getObjFieldCount(); i++)
                rb.td(record.getObjField(i).getLocalName());
            if (contract.usesEventFlags())
                rb.td(BuiltinFields.EVENT_FLAGS_FIELD_NAME);
        }

        @Override
        public void append(RecordCursor cursor) {
            if (shownRows >= REPORT_ROWS_LIMIT)
                return;
            if (!allSymbols && (cipher == 0 ? !symbol.equals(cursor.getSymbol()) : cipher != cursor.getCipher()))
                return;
            if (!allRecords && record != cursor.getRecord())
                return;
            totalRows++;
            if (boundsOnly) {
                if (!hasLast || last.getRecord() != cursor.getRecord() ||
                    (last.getCipher() == 0 ? !last.getSymbol().equals(cursor.getSymbol()) : last.getCipher() != cursor.getCipher()))
                {
                    // print first
                    flushLast();
                    printDataRow(cursor);
                }
                if (last == null || last.getRecord() != cursor.getRecord()) {
                    lastOwner = RecordCursor.allocateOwner(cursor.getRecord(), RecordMode.FLAGGED_DATA);
                    last = lastOwner.cursor();
                }
                lastOwner.setSymbol(cursor.getCipher(), cursor.getSymbol());
                last.copyFrom(cursor);
                hasLast = true;
            } else {
                // print all
                printDataRow(cursor);
            }
        }

        private void flushLast() {
            if (hasLast) {
                printDataRow(last);
                hasLast = false;
            }
        }

        void end() {
            flushLast();
            rb.endTable();
            rb.message("Total rows: " + totalRows);
            if (contract == QDContract.HISTORY && boundsOnly)
                rb.message("Shown rows: " + shownRows);
        }

        private void printDataRow(RecordCursor cursor) {
            shownRows++;
            rb.newRow();
            DataRecord record = cursor.getRecord();
            rb.td(record.getName());
            rb.td(cursor.getDecodedSymbol());
            for (int i = 0; i < record.getIntFieldCount(); i++)
                rb.td(record.getIntField(i).getString(cursor));
            for (int i = 0; i < record.getObjFieldCount(); i++)
                rb.td(record.getObjField(i).getString(cursor));
            if (contract.usesEventFlags())
                rb.td(EventFlag.formatEventFlags(cursor.getEventFlags(), MessageType.forData(contract)));
        }
    }

    private void reportSubscriptionImpl(ReportBuilder rb, Collector collector, String recordName, String symbol) {
        QDContract contract = collector.getContract();
        rb.header(nameCollector(collector), ReportBuilder.HEADER_LEVEL_COLLECTOR);
        DataScheme scheme = collector.getScheme();
        boolean allSymbols = "*".equals(symbol);
        boolean allRecords = "*".equals(recordName);
        int cipher = allSymbols ? 0 : scheme.getCodec().encode(symbol);
        DataRecord record = allRecords ? null : scheme.findRecordByName(recordName);
        if (!allRecords && record == null) {
            rb.message("No such record in scheme");
            return;
        }
        ReportSubscriptionSink sink = new ReportSubscriptionSink(rb, contract, cipher, symbol, record, allSymbols, allRecords);
        sink.begin();
        collector.examineSubscription(sink);
        sink.end();
    }

    private static class ReportSubscriptionSink extends AbstractRecordSink {
        private final ReportBuilder rb;
        private final QDContract contract;
        private final int cipher;
        private final String symbol;
        private final DataRecord record;
        private final boolean allSymbols;
        private final boolean allRecords;

        private int totalRows;

        ReportSubscriptionSink(ReportBuilder rb, QDContract contract, int cipher, String symbol,
            DataRecord record,
            boolean allSymbols, boolean allRecords)
        {
            this.rb = rb;
            this.contract = contract;
            this.cipher = cipher;
            this.symbol = symbol;
            this.record = record;
            this.allSymbols = allSymbols;
            this.allRecords = allRecords;
        }

        void begin() {
            rb.beginTable().newRow();
            rb.td("Record");
            rb.td("Symbol");
            if (contract.hasTime()) {
                rb.td("Time (hi)");
                rb.td("Time (lo)");
                rb.td("Time (long)");
            }
        }

        @Override
        public void append(RecordCursor cursor) {
            if (totalRows >= REPORT_ROWS_LIMIT)
                return;
            if (!allSymbols && (cipher == 0 ? !symbol.equals(cursor.getSymbol()) : cipher != cursor.getCipher()))
                return;
            if (!allRecords && record != cursor.getRecord())
                return;
            totalRows++;
            rb.newRow();
            rb.td(cursor.getRecord().getName());
            rb.td(cursor.getDecodedSymbol());
            if (contract.hasTime()) {
                rb.td(cursor.getRecord().getIntField(0).getString(cursor));
                rb.td(cursor.getRecord().getIntField(1).getString(cursor));
                rb.td(cursor.getTime());
            }
        }

        void end() {
            rb.endTable();
            rb.message("Total rows: " + totalRows);
        }
    }

    private String nameCollector(Collector collector) {
        CollectorManagementImplOneContract management = (CollectorManagementImplOneContract) collector.getManagement();
        return management.getName() + " @" + Integer.toHexString(System.identityHashCode(collector));
    }

    private CollectorCountersImpl completeCountersSinceSnapshot() {
        List<Collector> collectors = getCollectors();
        if (collectors.size() == 1) // typical case
            return (CollectorCountersImpl) collectors.get(0).getCountersSinceSnapshot();
        // fallback in case of multiple collectors per management item
        CollectorCountersImpl result = new CollectorCountersImpl(scheme, 0, 0);
        for (Collector collector : collectors)
            result.add((CollectorCountersImpl) collector.getCountersSinceSnapshot());
        return result;
    }

    @Override
    public String getAllLockOperations() {
        return CollectorCountersImpl.getAllLockOperations();
    }

    @Override
    public String getCounters() {
        return completeCountersSinceSnapshot().toString();
    }

    @Override
    public String reportCounters(String format, Integer topSize) {
        int top = topSize == null ? CollectorCountersImpl.TOP_N : topSize;
        ReportBuilder rb = new ReportBuilder(format);
        List<Collector> collectors = getCollectors();
        for (Collector collector : collectors) {
            CollectorCountersImpl counters = (CollectorCountersImpl) collector.getCountersSinceSnapshot();
            counters.reportTo(rb, nameCollector(collector), top);
        }
        if (collectors.size() > 1)
            completeCountersSinceSnapshot().reportTo(rb, name, top);
        return rb.toString();
    }

    @Override
    public void resetCounters() {
        for (Collector collector : getCollectors())
            collector.snapshotCounters();
    }
}
