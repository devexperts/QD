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
package com.devexperts.qd.tools.analysis;

import com.devexperts.io.BufferedInput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.BinaryRecordDesc;
import com.devexperts.qd.qtp.MessageConsumer;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.file.BinaryFileQTPParser;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

class Parser extends BinaryFileQTPParser {
    private static final int SLOT_MESSAGE_TYPE = 0;
    private static final int SLOT_PROTOCOL_ELEMENT = 1;
    private static final int SLOT_DATA_MSG = 2;
    private static final int SLOT_FIELD = 3;
    private static final int N_SLOTS = 4;

    private final SymbolCategorizer symbolCategorizer;

    private boolean typeOverride;

    private final List<Section<?>> sections = new ArrayList<>();

    private final Map<String, Pattern> definedGroups = new HashMap<>();

    private final Section<MessageType> allByMessageType;
    private final Section<ProtocolElement> allByProtocolElement;
    private final Section<String> dataMsgByRecordCategory;
    private final Section<SymbolCategory> dataMsgBySymbolCategory;
    private final Map<SymbolCategory,Section<String>> dataMsgByRecordCategoryBySymbolCategory;
    private final Section<SymbolCategory> symbolBySymbolCategory;
    private final Section<SerialFieldType> recordDataByFieldType;
    private final Section<String> recordDataByFieldName;
    private final Section<String> recordDataByGroup;

    private Symbols symbols;
    private DataCompression dataCompression;

    private long[] markedPosition = new long[N_SLOTS];

    private long lastPosition;
    private int lineLen;

    Parser(DataScheme scheme) {
        super(scheme);
        symbolCategorizer = new SymbolCategorizer(scheme);
        allByMessageType = new Section<>("Distribution of all bytes by message type");
        allByProtocolElement = new Section<>("Distribution of all bytes by protocol element");
        dataMsgByRecordCategory = new Section<>("Distribution of data message body bytes by record category");
        dataMsgBySymbolCategory = new Section<>("Distribution of data message body bytes by symbol category");
        dataMsgByRecordCategoryBySymbolCategory = new EnumMap<>(SymbolCategory.class);
        for (SymbolCategory symbolCategory : SymbolCategory.values())
            dataMsgByRecordCategoryBySymbolCategory.put(symbolCategory, new Section<String>(
                "Distribution of data message body bytes by record category for " + symbolCategory + " symbols"));
        symbolBySymbolCategory = new Section<>("Distribution of symbol bytes by symbol category");
        recordDataByFieldType = new Section<>("Distribution of record data bytes by field type");
        recordDataByFieldName = new Section<>("Distribution of record data bytes by field name");
        recordDataByGroup = new Section<>("Distribution of record data bytes by custom group");
    }

    public void setAnalyzeSymbols(boolean analyzeSymbols) {
        symbols = analyzeSymbols ? new Symbols() : null;
    }

    public void defineGroup(String groupName, Pattern pattern) {
        definedGroups.put(groupName, pattern);
    }

    public void setAnalyzeCompression(Pattern pattern) {
        this.dataCompression = pattern != null ? new DataCompression(pattern) : null;
    }

    public void setTypeOverride(boolean typeOverride) {
        this.typeOverride = typeOverride;
    }

    @Override
    protected void parseImpl(BufferedInput in, MessageConsumer consumer) throws IOException {
        super.parseImpl(in, consumer);
        // prints progress dots on screen
        long currentPosition = in.totalPosition();
        while (true) {
            long nextPosition = lastPosition + 65536;
            if (nextPosition > currentPosition)
                break;
            System.out.print('.');
            lineLen++;
            if (lineLen == 64) {
                System.out.printf(Locale.US, " %dM parsed%n", nextPosition / (1024 * 1024));
                lineLen = 0;
            }
            lastPosition = nextPosition;
        }
    }

    public void print(PrintWriter out, long fileSize) {
        for (Section<?> section : sections)
            section.print(out, fileSize);
        if (symbols != null)
            symbols.print(out, fileSize);
        if (dataCompression != null)
            dataCompression.print(out, fileSize);
    }

    @Override
    protected void doBeforeMessageLength(BufferedInput in) {
        markPosition(in, SLOT_MESSAGE_TYPE);
        markPosition(in, SLOT_PROTOCOL_ELEMENT);
    }

    @Override
    protected void doAfterMessageType(BufferedInput in) {
        allByProtocolElement.get(ProtocolElement.MESSAGE_HEADER).inc(countBytes(in, SLOT_PROTOCOL_ELEMENT));
    }

    @Override
    protected void doAfterMessageBody(BufferedInput in, int messageType) {
        MessageType type = MessageType.findById(messageType);
        allByMessageType.get(type).inc(countBytes(in, SLOT_MESSAGE_TYPE));
        if (type != null)
            switch (type) {
            case HEARTBEAT:
                allByProtocolElement.get(ProtocolElement.HEARTBEAT_PAYLOAD).inc(countBytes(in, SLOT_PROTOCOL_ELEMENT));
                break;
            case DESCRIBE_PROTOCOL:
                allByProtocolElement.get(ProtocolElement.PROTOCOL_DESCRIPTION).inc(countBytes(in, SLOT_PROTOCOL_ELEMENT));
                break;
            case DESCRIBE_RECORDS:
                allByProtocolElement.get(ProtocolElement.RECORD_DESCRIPTION).inc(countBytes(in, SLOT_PROTOCOL_ELEMENT));
                break;
            }
        //flushBits();
    }

    @Override
    protected void readSymbol(BufferedInput msg) throws IOException {
        markPosition(msg, SLOT_DATA_MSG);
        markPosition(msg, SLOT_PROTOCOL_ELEMENT);
        super.readSymbol(msg);
        int symbolBytes = countBytes(msg, SLOT_PROTOCOL_ELEMENT) - symbolReader.getEventFlagsBytes();
        allByProtocolElement.get(ProtocolElement.SYMBOL).inc(symbolBytes);
        allByProtocolElement.get(ProtocolElement.EVENT_FLAGS).inc(symbolReader.getEventFlagsBytes());
        if (symbolBytes > 1) {
            SymbolCategory category = symbolCategorizer.getSymbolCategory(symbolReader.getCipher(), symbolReader.getSymbol());
            symbolBySymbolCategory.get(category).inc(symbolBytes);
            if (symbols != null)
                symbols.countSymbol(category, symbolReader.getCipher(), symbolReader.getSymbol(), symbolBytes);
        } else
            symbolBySymbolCategory.get(SymbolCategory.REPEAT_LAST).inc(symbolBytes);
    }

    @Override
    protected int readRecordId(BufferedInput msg) throws IOException {
        markPosition(msg, SLOT_PROTOCOL_ELEMENT);
        int id = super.readRecordId(msg);
        allByProtocolElement.get(ProtocolElement.RECORD_ID).inc(countBytes(msg, SLOT_PROTOCOL_ELEMENT));
        return id;
    }

    @Override
    protected BinaryRecordDesc wrapRecordDesc(BinaryRecordDesc desc) {
        return new RecordReader(super.wrapRecordDesc(desc));
    }

    class RecordReader extends BinaryRecordDesc {
        RecordReader(BinaryRecordDesc desc) {
            super(desc);
        }

        @Override
        protected void readFields(BufferedInput msg, RecordCursor cur, int nDesc) throws IOException {
            markPosition(msg, SLOT_PROTOCOL_ELEMENT);
            super.readFields(msg, cur, nDesc);
            allByProtocolElement.get(ProtocolElement.RECORD_DATA).inc(countBytes(msg, SLOT_PROTOCOL_ELEMENT));
            if (cur != null) {
                int dataMsgBytes = countBytes(msg, SLOT_DATA_MSG);
                String recordCategory = getRecordCategory(cur.getRecord());
                SymbolCategory symbolCategory = symbolCategorizer.getSymbolCategory(cur.getCipher(), cur.getSymbol());
                dataMsgByRecordCategory.get(recordCategory).inc(dataMsgBytes);
                dataMsgBySymbolCategory.get(symbolCategory).inc(dataMsgBytes);
                dataMsgByRecordCategoryBySymbolCategory.get(symbolCategory).get(recordCategory).inc(dataMsgBytes);
            }
        }

        @Override
        protected void beforeField(BufferedInput msg) {
            markPosition(msg, SLOT_FIELD);
        }

        @Override
        protected void setIntValue(RecordCursor cur, int index, int value, BufferedInput msg) {
            super.setIntValue(cur, index, value, msg);
            DataIntField field = cur.getRecord().getIntField(index);
            int bytes = countField(field, msg);
            SerialFieldType type = field.getSerialType();
            if (dataCompression != null && type != SerialFieldType.VOID)
                dataCompression.count(getReportKeys(field), type, value, bytes);
        }

        @Override
        protected void setLongValue(RecordCursor cur, int index, long value, BufferedInput msg) {
            super.setLongValue(cur, index, value, msg);
            DataIntField field = cur.getRecord().getIntField(index);
            int bytes = countField(field, msg);
            SerialFieldType type = field.getSerialType();
            if (dataCompression != null && type != SerialFieldType.VOID)
                dataCompression.count(getReportKeys(field), type, value, bytes);
        }

        @Override
        protected void setObjValue(RecordCursor cur, int index, Object value, BufferedInput msg) {
            super.setObjValue(cur, index, value, msg);
            countField(cur.getRecord().getObjField(index), msg);
        }
    }

    class ReportKeys {
        final SerialFieldType fieldType;
        final String fieldName;
        final String[] fieldGroups;

        final boolean analyzeFieldTypeCompression;
        final boolean analyzeFieldNameCompression;
        final String[] analyzeCompressionGroups;

        ReportKeys(DataField field) {
            String name = field.getName();
            SerialFieldType actualSerialType = field.getSerialType();
            fieldType = typeOverride ? actualSerialType.forNamedField(name) : actualSerialType;
            fieldName = field.getPropertyName() + ":" + fieldType;
            List<String> g = new ArrayList<>();
            for (Map.Entry<String, Pattern> entry : definedGroups.entrySet()) {
                if (entry.getValue().matcher(name).matches())
                    g.add(entry.getKey());
            }
            fieldGroups = g.toArray(new String[g.size()]);
            // compression
            analyzeFieldTypeCompression = dataCompression != null &&
                dataCompression.pattern.matcher(fieldType.toString()).matches();
            analyzeFieldNameCompression = dataCompression != null &&
                dataCompression.pattern.matcher(fieldName).matches();
            g.clear();
            if (dataCompression != null)
                for (String group : fieldGroups)
                    if (dataCompression.pattern.matcher(group).matches())
                        g.add(group);
            analyzeCompressionGroups = g.toArray(new String[g.size()]);
        }
    }

    private final Map<DataField, ReportKeys> reportKeyCache = new HashMap<>();

    private ReportKeys getReportKeys(DataField field) {
        ReportKeys keys = reportKeyCache.get(field);
        if (keys == null)
            reportKeyCache.put(field, keys = new ReportKeys(field));
        return keys;
    }

    private int countField(DataField field, BufferedInput msg) {
        int bytes = countBytes(msg, SLOT_FIELD);
        ReportKeys keys = getReportKeys(field);
        recordDataByFieldType.get(keys.fieldType).inc(bytes);
        recordDataByFieldName.get(keys.fieldName).inc(bytes);
        for (String group : keys.fieldGroups)
            recordDataByGroup.get(group).inc(bytes);
        return bytes;
    }

    @Override
    protected long readSubscriptionTime(BufferedInput msg) throws IOException {
        markPosition(msg, SLOT_PROTOCOL_ELEMENT);
        long time = super.readSubscriptionTime(msg);
        allByProtocolElement.get(ProtocolElement.SUBSCRIPTION_TIME).inc(countBytes(msg, SLOT_PROTOCOL_ELEMENT));
        return time;
    }

    private void markPosition(BufferedInput in, int slot) {
        markedPosition[slot] = in.totalPosition();
    }

    private int countBytes(BufferedInput in, int slot) {
        int result = (int) (in.totalPosition() - markedPosition[slot]);
        markPosition(in, slot);
        return result;
    }

    private String getRecordCategory(DataRecord record) {
        if (record == null)
            return "UNKNOWN"; // records that are not in our scheme
        String name = record.getName();
        int i = Math.max(name.lastIndexOf('&'), name.lastIndexOf('*'));
        return i >= 0 ? name.substring(0, i + 1) + '*' : name;
    }

    private static class Counter {
        final String name;
        long times;
        long bytes;

        Counter(String name) {
            this.name = name;
        }

        Counter(String name, long times, long bytes) {
            this.name = name;
            this.times = times;
            this.bytes = bytes;
        }

        private void inc(int inc) {
            if (inc > 0) {
                times++;
                bytes += inc;
            }
        }

        public double average() {
            return times == 0 ? 0 : (double) bytes / times;
        }
    }

    private class Section<K> {
        final String name;
        final Map<K, Counter> counters = new HashMap<>();

        Section(String name) {
            this.name = name;
            sections.add(this);
        }

        Counter get(K key) {
            Counter counter = counters.get(key);
            if (counter == null)
                counters.put(key, counter = new Counter(key == null ? "NULL" : key.toString()));
            return counter;
        }

        public void print(PrintWriter out, long fileSize) {
            List<Counter> cs = new ArrayList<>(counters.values());
            if (cs.isEmpty())
                return;

            Collections.sort(cs, new Comparator<Counter>() {
                @Override
                public int compare(Counter o1, Counter o2) {
                    return o1.name.compareTo(o2.name);
                }
            });

            long totalTimes = 0;
            long totalBytes = 0;

            for (Counter c : cs) {
                totalTimes += c.times;
                totalBytes += c.bytes;
            }
            if (totalBytes == 0)
                return;
            cs.add(new Counter("TOTAL", totalTimes, totalBytes));

            List<String[]> lines = new ArrayList<>();
            lines.add(new String[] { "_name_", "_times_", "_avg_bytes_", "_total_bytes", "_bytes%_" });
            for (Counter c : cs) {
                if (c.bytes == 0)
                    continue; // skip empty
                lines.add(new String[] {
                    c.name,
                    Util.formatCount(c.times),
                    Util.formatAverage(c.average()),
                    Util.formatCount(c.bytes),
                    Util.formatPercent(100.0 * c.bytes / fileSize)
                });
            }

            int[] maxLen = new int[lines.get(0).length];
            for (String[] line : lines) {
                for (int i = 0; i < line.length; i++)
                    maxLen[i] = Math.max(maxLen[i], line[i].length());
            }

            Util.printHeader(name, out);
            for (String[] line : lines) {
                for (int i = 0; i < line.length; i++) {
                    if (i > 0)
                        Util.printSpaces(maxLen[i] - line[i].length() + 1, out);
                    out.print(line[i]);
                    if (i == 0)
                        Util.printSpaces(maxLen[i] - line[i].length(), out);
                }
                out.println();
            }
        }
    }

    private enum ProtocolElement {
        MESSAGE_HEADER,
        RECORD_DESCRIPTION, PROTOCOL_DESCRIPTION, HEARTBEAT_PAYLOAD,
        RECORD_ID, SYMBOL, EVENT_FLAGS, RECORD_DATA, SUBSCRIPTION_TIME
    }
}
