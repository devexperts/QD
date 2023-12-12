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
package com.devexperts.qd.qtp.text;

import com.devexperts.io.BufferedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.BuiltinFields;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageConsumer;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Parses QTP messages in text format from byte stream.
 * The input for this parser must be configured with {@link #setInput(BufferedInput)} method
 * immediately after construction.
 *
 * @see AbstractQTPParser
 */
public class TextQTPParser extends AbstractQTPParser {
    private static final String LEGACY_QD_COMPLETE = TextDelimiters.LEGACY_QD_PREFIX + "COMPLETE"; // onDemand dataextractor marks the end of data with ==QD_COMPLETE

    private static final DataField EVENT_TIME_DESC = new VoidIntField(Integer.MAX_VALUE, "EventTimePlaceHolder");

    private static final Logging log = Logging.getLogging(TextQTPParser.class);
    
    private final LineTokenizer tokenizer;
    private MessageType defaultMessageType;
    private MessageType lastMessageType;
    private ProtocolDescriptor protocolDescriptor;
    private HeartbeatPayload heartbeatPayload;

    /**
     * This field stores information about records that were
     * described in DataInput. If some record was described
     * then it maps its name into <tt>DataField</tt> array.
     * This array contains information about correspondence
     * between incoming fields and scheme record fields.
     * The i-th element corresponds to i-th incoming field (as described) and contains:
     * <ul>
     *     <li>
     *         EVENT_TIME_DESC if it is EventTime field
     *     </li>
     *     <li>
     *         corresponding scheme record field if matching field found
     *     </li>
     *     <li>
     *         null if scheme record does not contain field with such name
     *     </li>
     * </ul>
     */
    private final HashMap<String, DataField[]> describedRecords;

    public TextQTPParser(DataScheme scheme) {
        this(scheme, null);
    }

    public TextQTPParser(DataScheme scheme, MessageType defaultMessageType) {
        super(scheme);
        this.defaultMessageType = defaultMessageType;
        lastMessageType = defaultMessageType;
        tokenizer = new LineTokenizer();
        describedRecords = new HashMap<>();
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
                log.error("Invalid text format", e);
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
                        log.error("Unknown message type \"" + messageName + "\"");
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
            log.error(e.getMessage(), e);
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
        DataRecord record = scheme.findRecordByName(recordName);
        DataField[] fieldsRearrangement = describedRecords.get(recordName);
        if (record == null) {
            if (fieldsRearrangement == null) {
                log.error("Unknown record \"" + recordName + "\"");
                return false;
            } else {
                // This record was not initially described in our scheme, but was described later.
                // We have nothing to do with it and just skip it.
                return true;
            }
        }

        String symbol = tokenizer.nextToken();
        if (symbol == null) {
            log.error("Symbol name expected for record \"" + recordName + "\"");
            return false;
        }
        int cipher = scheme.getCodec().encode(symbol);

        RecordCursor cur = buf.add(record, cipher, symbol);

        if (fieldsRearrangement == null) {
            // Record was not re-described. Just read it.
            for (int i = 0, n = record.getIntFieldCount(); tokenizer.hasMoreTokens() && i < n; i++)
                if (!(record.getIntField(i) instanceof VoidIntField))
                    trySetField(record.getIntField(i), cur, tokenizer.nextToken());
            for (int i = 0, n = record.getObjFieldCount(); tokenizer.hasMoreTokens() && i < n; i++)
                trySetField(record.getObjField(i), cur, tokenizer.nextToken());
        } else {
            // Record was re-described.
            for (int i = 0; tokenizer.hasMoreTokens() && i < fieldsRearrangement.length; i++) {
                DataField field = fieldsRearrangement[i];
                String token = tokenizer.nextToken();
                if (field == EVENT_TIME_DESC) {
                    trySetEventTime(cur, token);
                } else if (field != null) {
                    trySetField(field, cur, token);
                }
            }
        }
        parseExtraTokens(cur, true);
        setEventTimeSequenceIfNeeded(cur);
        replaceFieldIfNeeded(cur);
        return true;
    }

    private static void trySetEventTime(RecordCursor cursor, String value) {
        if (value != null)
            try {
                cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(
                    TimeFormat.DEFAULT.parse(value).getTime()));
            } catch (IllegalArgumentException e) {
                log.error("Cannot parse \"" + BuiltinFields.EVENT_TIME_FIELD_NAME + "\" field: " + e.getMessage());
            }
    }

    private static void trySetField(DataField field, RecordCursor cursor, String value) {
        try {
            field.setString(cursor, value);
        } catch (IllegalArgumentException e) {
            log.error("Cannot parse record field \"" + field.getName() + "\": " + e.getMessage());
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
        DataField[] fieldsRearrangement = describedRecords.get(recordName);
        if (record == null) {
            if (fieldsRearrangement == null) {
                log.error("Subscription to unknown record \"" + recordName + "\"");
                return false;
            } else {
                // This record was not initially described in our scheme, but was described later.
                // We have nothing to do with it and just skip it.
                return true;
            }
        }

        String symbol = tokenizer.nextToken();
        if (symbol == null) {
            log.error("Symbol name expected for record \"" + recordName + "\"");
            return false;
        }
        int cipher = scheme.getCodec().encode(symbol);

        long time = 0;
        if (lastMessageType.isHistorySubscriptionAdd()) {
            String timeStr = tokenizer.nextToken();
            if (timeStr != null) {
                try {
                    time = ((long) record.getIntField(0).parseString(timeStr) << 32);
                    timeStr = tokenizer.nextToken();
                    if (timeStr != null)
                        time |= record.getIntField(1).parseString(timeStr) & 0xFFFFFFFFL;
                } catch (IllegalArgumentException e) {
                    log.error("Cannot parse time for historySubscriptionAdd subscription");
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
                log.error("Skipping extra token \"" + s + "\". Expected '=' is not found");
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
                        log.error("Skipping extra token \"" + s + "\". Field is not found");
                    else
                        trySetField(field, cur, value);
                } else
                    log.error("Skipping extra token \"" + s + "\" in subscription");
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
            log.error("Invalid symbol field name '" + symbolString + "'");
            return false;
        }
        DataRecord record = scheme.findRecordByName(recordName);
        ArrayList<DataField> fieldsRearrangement = new ArrayList<>();
        for (String fieldName; (fieldName = tokenizer.nextToken()) != null;) {
            DataField field = record == null ? null : record.findFieldByName(fieldName);
            if (field == null && readEventTimeSequence && fieldName.equals(BuiltinFields.EVENT_TIME_FIELD_NAME))
                field = EVENT_TIME_DESC;
            fieldsRearrangement.add(field);
        }
        describedRecords.put(recordName, fieldsRearrangement.toArray(new DataField[fieldsRearrangement.size()]));
        return true;
    }
}
