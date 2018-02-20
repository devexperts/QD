/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.text;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import com.devexperts.io.BufferedInput;
import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

/**
 * Parses QTP messages in text format from byte stream.
 * The input for this parser must be configured with {@link #setInput(BufferedInput)} method
 * immediately after construction.
 *
 * @see AbstractQTPParser
 */
public class TextQTPParser extends AbstractQTPParser {
    private static final String LEGACY_QD_COMPLETE = TextDelimiters.LEGACY_QD_PREFIX + "COMPLETE"; // onDemand dataextractor marks the end of data with ==QD_COMPLETE

    private static final int EVENT_TIME_DESC = Integer.MAX_VALUE;

    private final LineTokenizer tokenizer;
    private MessageType defaultMessageType;
    private MessageType lastMessageType;
    private ProtocolDescriptor protocolDescriptor;
    private HeartbeatPayload heartbeatPayload;
    private final Map<DataRecord, List<Consumer<RecordCursor>>> replacersCache;

    /**
     * This field stores information about records that were
     * redescribed in DataInput. If some record was redescribed
     * then it maps its name into <tt>int</tt> array.
     * This array contains information about correspondence
     * between old and new record fields. It has following semantics:
     * <ul>
     * <li>
     * If the record didn't present in scheme, but it was described in DataInput,
     * then the mapped array has 0 length (int[0]). In this case we are not interested
     * in new record's fields information, we can't process it anyway
     * and will just skip it, and only need to know that the record
     * was described earlier and we know about it.
     * </li>
     * <li>
     * If some known record was redescribed with some different fields (or
     * without some old fields, or with some new fields, or with the same
     * fields but in different order), then the corresponding array contains
     * mapping between new and old fields. Its size is equal to the number of
     * fields in new record, and its i-th element is equal to
     *     <ol>
     *     <li>
     *     0, if i-th field didn't present in old record.
     *     </li>
     *     <li>
     *     j > 0, if i-th field of new record corresponds to (j - 1) <b>Int</b> field of old record. (1-->0, 2-->1, 3-->2, ...)
     *     </li>
     *     <li>
     *     j < 0, if i-th field of new record corresponds to -(j + 1) <b>Obj</b> field of old record. (-1-->0, -2-->1, -3-->2, ...)
     *     </li>
     *     </ol>
     * </li>
     * <li>
     * If there was no redescription of this record or if it was redescribed
     * with exactly the same fields (following in the same order)
     * then there is no mapping for its name.
     * </li>
     * </ul>
     */
    private HashMap<String, int[]> describedRecords;

    public TextQTPParser(DataScheme scheme) {
        this(scheme, null);
    }

    public TextQTPParser(DataScheme scheme, MessageType defaultMessageType) {
        super(scheme);
        this.defaultMessageType = defaultMessageType;
        lastMessageType = defaultMessageType;
        tokenizer = new LineTokenizer();
        describedRecords = new HashMap<>();
        replacersCache = new HashMap<>();
    }

    public void setDelimiters(TextDelimiters delimiters) {
        tokenizer.setDelimiters(delimiters);
    }

    @Override
    public void resetSession() {
        describedRecords.clear();
        lastMessageType = defaultMessageType;
    }

    @Override
    protected void parseImpl(BufferedInput in, MessageConsumer consumer) throws IOException {
        while (in.hasAvailable()) {
            try {
                if (!tokenizer.reset(in))
                break;
            } catch (CorruptedTextFormatException e) {
                processPending(consumer); // process all good so far
                QDLog.log.error("Invalid text format", e);
                consumer.handleCorruptedStream();
                break;
            }

            String token = tokenizer.nextToken();
            if (token == null)  // Empty line
                continue;
            TextDelimiters delimiters = tokenizer.getDelimiters();
            if (delimiters.messageTypePrefix != null && token.startsWith(delimiters.messageTypePrefix)) {
                // New message header
                String messageName = token.substring(delimiters.messageTypePrefix.length());
                if (messageName.equals(LEGACY_QD_COMPLETE))
                    ; /* IGNORE LEGACY ==QD_COMPLETE lines from onDemand service */
                else if (messageName.equals(ProtocolDescriptor.MAGIC_STRING)) {
                    // special processing for protocol descriptor with in-line parameters
                    processPending(consumer); // flush all we had so far first
                    // Note, that protocol descriptor is open-ended and its parsing never fails
                    parseDescribeProtocol(consumer);
                } else if (messageName.isEmpty()) {
                    // special processing for heartbeat message with in-line parameters
                    processPending(consumer); // flush all we had so far first
                    if (!parseHeartbeat(consumer)) {
                        processPending(consumer); // process all good so far
                        consumer.handleCorruptedMessage(MessageType.HEARTBEAT.getId());
                    }
                } else {
                    // strip legacy QD_ prefix if present
                    if (messageName.startsWith(TextDelimiters.LEGACY_QD_PREFIX))
                        messageName = messageName.substring(TextDelimiters.LEGACY_QD_PREFIX.length());
                    MessageType messageType = MessageType.findByName(messageName);
                    if (messageType == null) {
                        processPending(consumer); // process all good so far
                        QDLog.log.error("Unknown message type \"" + messageName + "\"");
                        consumer.handleUnknownMessage(MessageType.TEXT_FORMAT.getId());
                    } else {
                        // note that everything following message type header is ignored (reserved for future extension)
                        lastMessageType = messageType;
                    }
                }
            } else if (token.startsWith(delimiters.describePrefix)) {
                // Record description
                String recordName = token.substring(delimiters.describePrefix.length());
                if (!parseRecordDescription(recordName)) {
                    processPending(consumer); // process all good so far
                    consumer.handleCorruptedMessage(MessageType.DESCRIBE_RECORDS.getId());
                }
            } else {
                // Data or subscription
                if (lastMessageType != null) {
                    RecordBuffer buf = nextRecordsMessage(consumer, lastMessageType);
                    switch (lastMessageType) {
                    case TICKER_DATA:
                    case STREAM_DATA:
                    case HISTORY_DATA:
                    case RAW_DATA:
                        if (!parseData(buf, token)) {
                            processPending(consumer); // process all good so far
                            consumer.handleCorruptedMessage(lastMessageType.getId());
                        }
                        break;
                    case TICKER_ADD_SUBSCRIPTION:
                    case TICKER_REMOVE_SUBSCRIPTION:
                    case STREAM_ADD_SUBSCRIPTION:
                    case STREAM_REMOVE_SUBSCRIPTION:
                    case HISTORY_ADD_SUBSCRIPTION:
                    case HISTORY_REMOVE_SUBSCRIPTION:
                        if (!parseSubscription(buf, token)) {
                            processPending(consumer); // process all good so far
                            consumer.handleCorruptedMessage(lastMessageType.getId());
                        }
                        break;
                    default:
                        processPending(consumer); // process all good so far
                        consumer.handleUnknownMessage(lastMessageType.getId());
                    }
                }
            }
        }
    }

    private void parseDescribeProtocol(MessageConsumer consumer) {
        protocolDescriptor = ProtocolDescriptor.newPeerProtocolDescriptor(protocolDescriptor);
        protocolDescriptor.appendFromTextTokens(tokenizer.getTokens(), tokenizer.getNextIndex());
        consumer.processDescribeProtocol(applyReadAs(protocolDescriptor), true);
    }

    private boolean parseHeartbeat(MessageConsumer consumer) {
        if (heartbeatPayload == null)
            heartbeatPayload = new HeartbeatPayload();
        else
            heartbeatPayload.clear();
        try {
            heartbeatPayload.appendFromTextTokens(tokenizer.getTokens(), tokenizer.getNextIndex());
        } catch (InvalidFormatException e) {
            QDLog.log.error(e.getMessage(), e);
            return false;
        }
        consumer.processHeartbeat(heartbeatPayload);
        return true;
    }

    /**
     * Parses specified record inside some data message and stores it
     * in the record buffer.
     *
     * @param buf the record buffer.
     * @param recordName name of a record to parse.
     * @return true, if record was parsed successfully, false otherwise.
     */
    private boolean parseData(RecordBuffer buf, String recordName) {
        DataRecord originalRecord = scheme.findRecordByName(recordName);
        int[] fieldsPermutation = describedRecords.get(recordName);
        if (originalRecord == null) {
            if (fieldsPermutation == null) {
                QDLog.log.error("Unknown record \"" + recordName + "\"");
                return false;
            } else {
                // This record was not initially described in our scheme, but was described later.
                // We have nothing to do with it and just skip it.
                return true;
            }
        }

        String symbol = tokenizer.nextToken();
        if (symbol == null) {
            QDLog.log.error("Symbol name expected for record \"" + recordName + "\"");
            return false;
        }
        int cipher = scheme.getCodec().encode(symbol);

        RecordCursor cur = buf.add(originalRecord, cipher, symbol);

        if (fieldsPermutation == null) {
            // Record was not re-described. Just read it.
            for (int i = 0, n = originalRecord.getIntFieldCount(); tokenizer.hasMoreTokens() && i < n; i++)
                trySetIntField(cur, i, tokenizer.nextToken());
            for (int i = 0, n = originalRecord.getObjFieldCount(); tokenizer.hasMoreTokens() && i < n; i++)
                trySetObjField(cur, i, tokenizer.nextToken());
        } else {
            // Record was re-described.
            for (int i = 0; tokenizer.hasMoreTokens() && i < fieldsPermutation.length; i++) {
                int k = fieldsPermutation[i];
                String token = tokenizer.nextToken();
                if (k == 0) {
                    // no correspondence with original record field
                } else if (k == EVENT_TIME_DESC) {
                    trySetEventTime(cur, token);
                } else if (k > 0) {
                    // Int field
                    trySetIntField(cur, k - 1, token);
                } else {
                    // Obj field
                    trySetObjField(cur, -(k + 1), token);
                }
            }
        }
        parseExtraTokens(cur, true);
        setEventTimeSequenceIfNeeded(cur);
        replaceFieldIfNeeded(cur);
        return true;
    }

    private void replaceFieldIfNeeded(RecordCursor cursor) {
        if (fieldReplacers == null)
            return;
        List<Consumer<RecordCursor>> replacers = replacersCache.get(cursor.getRecord());
        if (replacers == null) {
            replacers = new ArrayList<>();
            for (FieldReplacer fieldReplacer : fieldReplacers) {
                Consumer<RecordCursor> replacer = fieldReplacer.createFieldReplacer(cursor.getRecord());
                if (replacer != null)
                    replacers.add(replacer);
            }
            replacersCache.put(cursor.getRecord(), replacers);
        }
        for (Consumer<RecordCursor> replacer : replacers)
            replacer.accept(cursor);
    }

    private static void trySetEventTime(RecordCursor cursor, String value) {
        if (value != null)
            try {
                cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(
                    TimeFormat.DEFAULT.parse(value).getTime()));
            } catch (IllegalArgumentException e) {
                QDLog.log.error("Cannot parse \"" + BuiltinFields.EVENT_TIME_FIELD_NAME + "\" field: " + e.getMessage());
            }
    }

    private static void trySetIntField(RecordCursor cursor, int fieldIndex, String value) {
        DataIntField field = cursor.getRecord().getIntField(fieldIndex);
        try {
            cursor.setInt(fieldIndex, field.parseString(value));
        } catch (IllegalArgumentException e) {
            QDLog.log.error("Cannot parse record field \"" + field.getName() + "\": " + e.getMessage());
        }
    }

    private static void trySetObjField(RecordCursor cursor, int fieldIndex, String value) {
        DataObjField field = cursor.getRecord().getObjField(fieldIndex);
        try {
            cursor.setObj(fieldIndex, field.parseString(value));
        } catch (IllegalArgumentException e) {
            QDLog.log.error("Cannot parse record field \"" + field.getName() + "\": " + e.getMessage());
        }
    }

    /**
     * Parses subscription for specified record and stores it in the record buffer.
     *
     * @param buf the record buffer.
     * @param recordName name of a record.
     * @return true, if subscription was parsed successfully, false otherwise.
     */
    private boolean parseSubscription(RecordBuffer buf, String recordName) {
        DataRecord record = scheme.findRecordByName(recordName);
        int[] fieldsRearrangement = describedRecords.get(recordName);
        if (record == null) {
            if (fieldsRearrangement == null) {
                QDLog.log.error("Subscription to unknown record \"" + recordName + "\"");
                return false;
            } else {
                // This record was not initially described in our scheme, but was described later.
                // We have nothing to do with it and just skip it.
                return true;
            }
        }

        String symbol = tokenizer.nextToken();
        if (symbol == null) {
            QDLog.log.error("Symbol name expected for record \"" + recordName + "\"");
            return false;
        }
        int cipher = scheme.getCodec().encode(symbol);

        long time = 0;
        if (lastMessageType.isHistorySubscriptionAdd()) {
            String timeStr = tokenizer.nextToken();
            if (timeStr != null) {
                try {
                    time = record.getIntField(0).parseString(timeStr);
                    time <<= 32;
                    timeStr = tokenizer.nextToken();
                    if (timeStr != null)
                        time |= record.getIntField(1).parseString(timeStr);
                } catch (IllegalArgumentException e) {
                    QDLog.log.error("Cannot parse time for historySubscriptionAdd subscription");
                    return false;
                }
            }
        }

        RecordCursor cur = buf.add(record, cipher, symbol);
        cur.setTime(time);
        cur.setEventFlags(lastMessageType.isSubscriptionRemove() ? EventFlag.REMOVE_SYMBOL.flag() : 0);
        parseExtraTokens(cur, false);
        return true;
    }

    private void parseExtraTokens(RecordCursor cur, boolean data) {
        while (tokenizer.hasMoreTokens()) {
            String s = tokenizer.nextToken();
            int i = s.indexOf('=');
            if (i < 0) {
                QDLog.log.error("Skipping extra token \"" + s + "\". Expected '=' is not found");
                continue;
            }
            String key = s.substring(0, i);
            String value = s.substring(i + 1);
            switch (key) {
            case BuiltinFields.EVENT_FLAGS_FIELD_NAME:
                cur.setEventFlags(EventFlag.parseEventFlags(value, lastMessageType));
                break;
            case BuiltinFields.EVENT_TIME_FIELD_NAME:
                trySetEventTime(cur, value);
                break;
            default:
                if (data) {
                    DataField field = cur.getRecord().findFieldByName(key);
                    if (field == null)
                        QDLog.log.error("Skipping extra token \"" + s + "\". Field is not found");
                    else if (field instanceof DataIntField)
                        trySetIntField(cur, field.getIndex(), value);
                    else
                        trySetObjField(cur, field.getIndex(), value);
                } else
                    QDLog.log.error("Skipping extra token \"" + s + "\" in subscription");
            }
        }
    }

    /**
     * Parses description for specified record.
     * @param recordName name of a record which is being described.
     * @return true, if description was parsed successfully, false otherwise.
     */
    private boolean parseRecordDescription(String recordName) {
        String symbolString = tokenizer.nextToken();
        if (!BuiltinFields.SYMBOL_FIELD_NAME.equals(symbolString) &&
            !BuiltinFields.EVENT_SYMBOL_FIELD_NAME.equals(symbolString))
        {
            QDLog.log.error("Invalid symbol field name '" + symbolString + "'");
            return false;
        }
        DataRecord record = scheme.findRecordByName(recordName);
        if (record == null) {
            // There was no such record in our scheme. We just put new int[0]
            // into our describedRecords map in order to register that we
            // know about this record since now.
            describedRecords.put(recordName, new int[0]);
            return true;
        }
        ArrayList<Integer> fieldsRearrangement = new ArrayList<>();
        String fieldName;
        while ((fieldName = tokenizer.nextToken()) != null)
            fieldsRearrangement.add(getRecordFieldIndex(record, fieldName));
        int[] rearrangement = new int[fieldsRearrangement.size()];
        for (int i = 0; i < rearrangement.length; i++)
            rearrangement[i] = fieldsRearrangement.get(i);
        if (!equalRearrangement(record, rearrangement))
            describedRecords.put(recordName, rearrangement);
        return true;
    }

    private static boolean equalRearrangement(DataRecord record, int[] rearrangement) {
        if (rearrangement.length != record.getIntFieldCount() + record.getObjFieldCount())
            return false;
        int j = 0;
        for (int i = 0; i < record.getIntFieldCount(); i++)
            if (rearrangement[j++] != i + 1)
                return false;

        for (int i = 0; i < record.getObjFieldCount(); i++)
            if (rearrangement[j++] != -(i + 1))
                return false;
        return true;
    }

    private int getRecordFieldIndex(DataRecord record, String fieldName) {
        DataField field = record.findFieldByName(fieldName);
        if (field instanceof DataIntField)
            return field.getIndex() + 1;
        if (field instanceof DataObjField)
            return -(field.getIndex() + 1);
        if (readEventTimeSequence && fieldName.equals(BuiltinFields.EVENT_TIME_FIELD_NAME))
            return EVENT_TIME_DESC;
        return 0;
    }
}
