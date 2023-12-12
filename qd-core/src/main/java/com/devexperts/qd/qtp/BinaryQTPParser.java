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
package com.devexperts.qd.qtp;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedInputPart;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;

import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_PROTOCOL;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_RECORDS;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_DESCRIBE_RESERVED;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_HEARTBEAT;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_PART;
import static com.devexperts.qd.qtp.MessageConstants.MESSAGE_TEXT_FORMAT;
import static com.devexperts.qd.qtp.QTPConstants.MAX_MESSAGE_SIZE;

/**
 * Parses QTP messages in binary format from byte stream.
 * The input for this parser must be configured with {@link #setInput(BufferedInput)} method
 * immediately after construction.
 *
 * @see AbstractQTPParser
 * @see BinaryQTPComposer
 */
public class BinaryQTPParser extends AbstractQTPParser {

    private static final Logging log = Logging.getLogging(BinaryQTPParser.class);

    // ======================== protected instance fields ========================

    protected final SymbolCodec.Reader symbolReader;

    // ======================== private instance fields ========================

    private final BufferedInputPart msg = new BufferedInputPart();
    private SymbolCodec.Resolver symbolResolver;
    private ProtocolDescriptor protocolDescriptor;
    private HeartbeatPayload lastHeartbeatPayload;
    private BinaryRecordDesc[] recordMap;
    private Set<String> unknownRecordNames;

    private IndexedSet<Long, PartitionedMessage> partitionedMessages;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs parser with a specified scheme.
     *
     * @param scheme data scheme.
     */
    public BinaryQTPParser(DataScheme scheme) {
        super(scheme);
        symbolReader = scheme.getCodec().createReader();
    }

    // ------------------------ configuration methods ------------------------

    // extension point to parse binary data without record description messages
    protected boolean isSchemeKnown() {
        return false;
    }

    // ------------------------ session control ------------------------

    /**
     * Resets session state for this parser.
     * Resets the state of all parsed describes so far, as if this parser was just created.
     */
    @Override
    public void resetSession() {
        if (recordMap != null)
            Arrays.fill(recordMap, null);
        if (unknownRecordNames != null)
            unknownRecordNames.clear();
        protocolDescriptor = null;
        if (lastHeartbeatPayload != null)
            lastHeartbeatPayload.clear();
    }

    // ------------------------ top-level parsing ------------------------

    @Override
    protected void parseImpl(BufferedInput in, MessageConsumer consumer) throws IOException {
        parseImpl(in, consumer, MAX_MESSAGE_SIZE); // delegate to internal method with size control
    }

    private void parseImpl(BufferedInput in, MessageConsumer consumer, long maxMessageSize) throws IOException {
        //
        // Each message consists of three elements:
        //
        //  <messageLength> <messageType> <messagePayload>
        //                 |                              |
        //                 +------------------------------+
        //                           messageBody
        //
        if (consumer instanceof SymbolCodec.Resolver)
            symbolResolver = (SymbolCodec.Resolver) consumer;
        try { // clear symbolResolver in finally...
            // limit and process may overflow (because removeBytes is optional, so need to subtract them before comparison)
            while (in.hasAvailable()) {
                if (!resyncOnParse(in))
                    return; // failed to sync stream -- bailout
                long messageStartPosition = in.totalPosition();
                doBeforeMessageLength(in);
                in.mark();
                long longMessageLength;
                try {
                    longMessageLength = in.readCompactLong();
                } catch (EOFException e) {
                    in.reset(); // reset input to retry parsing when more bytes available
                    in.unmark();
                    break; // message is incomplete -- need more bytes
                }
                if (longMessageLength < 0 || longMessageLength > maxMessageSize) {
                    dumpParseHeaderErrorReport(in, "Invalid messageLength=" + longMessageLength);
                    if (resyncOnCorrupted(in))
                        continue; // resync again
                    else {
                        processPending(consumer); // process all good so far
                        // handle error
                        consumer.handleCorruptedStream();
                        break; // cannot continue parsing on stream with corrupted length, do not reset (leave stream on where it is)
                    }
                }
                int messageLength = (int) longMessageLength;
                if (!in.hasAvailable(messageLength)) {
                    in.reset(); // reset input to retry parsing when more bytes available
                    in.unmark();
                    break; // message is incomplete -- need more bytes
                }
                long messageEndPosition = in.totalPosition() + messageLength;
                try {
                    parseMessageBody(in, consumer, messageLength, messageEndPosition);
                } catch (CorruptedException e) {
                    if (resyncOnCorrupted(in))
                        continue; // resync again
                    else {
                        processPending(consumer); // process all good so far
                        // handle error
                        if (e instanceof  CorruptedMessageException)
                            consumer.handleCorruptedMessage(((CorruptedMessageException) e).messageTypeId);
                        else
                            consumer.handleCorruptedStream();
                    }
                }
                // skip the whole message (regardless of whether is was fully parsed or not)
                in.seek(messageEndPosition);
                in.unmark(); // undo mark
                // count bytes processed and continue to the next message
                stats.updateIOReadBytes(messageEndPosition - messageStartPosition);
            }
        } finally {
            symbolResolver = null;
        }
    }

    // ------------------------ resync support ------------------------

    /**
     * Extension point to resynchronize broken/partial input stream before each message.
     * It is invoked before attempting to parse a message.
     * @param in the input, it is not marked.
     * @return true when stream is in sync and message can be parsed.
     */
    protected boolean resyncOnParse(BufferedInput in) throws IOException {
        return true;
    }

    /**
     * Extension point to resynchronize broken/partial input stream on corrupted message.
     * It is invoked on each failure to parse a message.
     * @param in the input, it is marked.
     * @return true when in is set to the next byte to read and stream shall be resynchronized again,
     *         false if error shall be handled.
     */
    protected boolean resyncOnCorrupted(BufferedInput in) throws IOException {
        return false;
    }

    // ------------------------ helper methods to parse message parts ------------------------

    private void parseMessageBody(BufferedInput in, MessageConsumer consumer, int messageLength,
        long messageEndPosition) throws IOException, CorruptedException
    {
        msg.setInput(in, messageLength);
        try {
            msg.mark();
            int typeId = parseMessageType(msg);
            doAfterMessageType(msg);
            parseMessagePayload(msg, consumer, typeId, (int) (messageEndPosition - msg.totalPosition()));
            doAfterMessageBody(msg, typeId);
        } finally {
            // msg will be reused on next calls to parseImpl, we reset it to fail fast
            // if it is accidentally used outside of this block
            msg.resetInput();
        }
    }

    private int parseMessageType(BufferedInput msg)
        throws IOException, CorruptedException
    {
        if (!msg.hasAvailable())
            return MESSAGE_HEARTBEAT; // default message type (zero-length messages are treated as just heartbeats)
        // parse type if not empty message, empty message is heartbeat
        long longTypeId;
        try {
            longTypeId = msg.readCompactLong();
        } catch (EOFException e) {
            dumpParseMessageErrorReport(msg, "Not enough bytes in message to read message typeId", e, -1);
            throw new CorruptedException();
        }
        if (longTypeId < 0 || longTypeId > Integer.MAX_VALUE) {
            dumpParseMessageErrorReport(msg, "Invalid typeId=" + longTypeId, null, -1);
            throw new CorruptedException();
        }
        return (int) longTypeId;
    }

    private void parseMessagePayload(BufferedInput msg, MessageConsumer consumer, int typeId, int payloadLength)
        throws IOException, CorruptedMessageException
    {
        MessageType messageType = MessageType.findById(typeId);
        try {
            if (messageType != null && messageType.hasRecords()) {
                RecordBuffer buf = nextRecordsMessage(consumer, messageType);
                if (!buf.isEmpty() && !buf.hasEstimatedCapacityForBytes(payloadLength)) {
                    // not enough capacity in non-empty buffer -- don't let it grow too big to pool
                    processPending(consumer);
                    buf = nextRecordsMessage(consumer, messageType);
                }
                if (messageType.isData())
                    parseData(msg, buf);
                else if (messageType.isSubscription())
                    parseSubscription(msg, buf, messageType);
                else
                    throw new AssertionError(messageType.toString()); // must be either data or subscription
            } else
                parseOther(msg, consumer, typeId, payloadLength);
        } catch (CorruptedException e) {
            throw new CorruptedMessageException(e, typeId); // rethrow with message type id
        }
    }

    private void parseOther(BufferedInput msg, MessageConsumer consumer, int typeId, int payloadLength)
        throws IOException, CorruptedException
    {
        switch (typeId) {
        case MESSAGE_HEARTBEAT:
            processPending(consumer); // shall be in-order with record message
            parseHeartbeat(msg, consumer);
            break;
        case MESSAGE_DESCRIBE_RECORDS:
            // todo: error handling is not atomic now -- partially parsed message is still being processed.
            parseDescribeRecords(msg);
            break;
        case MESSAGE_DESCRIBE_PROTOCOL:
            processPending(consumer); // shall be in-order with record message
            parseDescribeProtocol(msg, consumer);
            break;
        case MESSAGE_PART:
            parseMessagePart(msg, consumer, payloadLength);
            break;
        case MESSAGE_DESCRIBE_RESERVED:
        case MESSAGE_TEXT_FORMAT:
            // just ignore those messages without any processing
            break;
        default:
            if (consumer instanceof MessageConsumerAdapter)
                ((MessageConsumerAdapter) consumer).processOtherMessage(typeId, msg, payloadLength);
            else {
                byte[] bytes = new byte[payloadLength];
                msg.readFully(bytes);
                consumer.processOtherMessage(typeId, bytes, 0, payloadLength);
            }
        }
    }

    private void parseDescribeProtocol(BufferedInput msg, MessageConsumer consumer) throws CorruptedException {
        try {
            protocolDescriptor = ProtocolDescriptor.newPeerProtocolDescriptor(protocolDescriptor);
            protocolDescriptor.parseFrom(msg);
        } catch (IOException e) {
            dumpParseMessageErrorReport(msg, e.getMessage(), e, -1);
            throw new CorruptedException(e);
        }
        ProtocolDescriptor desc = applyReadAs(protocolDescriptor);
        // MIND THE BUG: [QD-808] Event flags are not sent immediately after connection establishment (random effect)
        // NOTE: MUST INVOKE onDescribeProtocol first
        onDescribeProtocol(desc);
        // then pass it to consumer
        consumer.processDescribeProtocol(desc, true);
    }

    /**
     * Extension point for ConnectionQTPParser.
     * Invoked <b>after</b> {@link MessageConsumer#processDescribeProtocol(ProtocolDescriptor, boolean)} .
     */
    void onDescribeProtocol(ProtocolDescriptor desc) {}

    private void parseHeartbeat(BufferedInput msg, MessageConsumer consumer) throws CorruptedException {
        try {
            if (!msg.hasAvailable())
                return; // skip empty heartbeat
            if (lastHeartbeatPayload == null)
                lastHeartbeatPayload = new HeartbeatPayload();
            else
                lastHeartbeatPayload.clear();
            lastHeartbeatPayload.parseFrom(msg);
        } catch (IOException e) {
            dumpParseMessageErrorReport(msg, e.getMessage(), e, -1);
            throw new CorruptedException(e);
        }
        consumer.processHeartbeat(lastHeartbeatPayload);
        onHeartbeat(lastHeartbeatPayload);
    }

    /**
     * Extension point for ConnectionQTPParser.
     * Invoked <b>after</b> {@link MessageConsumer#processHeartbeat(HeartbeatPayload)}.
     */
    void onHeartbeat(HeartbeatPayload heartbeatPayload) {}

    private void parseDescribeRecords(BufferedInput msg) throws CorruptedException {
        long lastRecPosition = msg.totalPosition();
        try {
            while (msg.hasAvailable()) {
                // Read record description from stream
                int id = msg.readCompactInt();
                String recordName = msg.readUTFString();
                int nFld = msg.readCompactInt();
                if (id < 0 || recordName == null || recordName.isEmpty() || nFld < 0)
                    throw new IOException("Corrupted record information");
                String[] names = new String[nFld]; // names
                int[] types = new int[nFld]; // types
                for (int i = 0; i < nFld; i++) {
                    String name = msg.readUTFString();
                    int type = msg.readCompactInt();
                    if (name == null || name.isEmpty() ||
                        type < SerialFieldType.Bits.MIN_TYPE_ID || type > SerialFieldType.Bits.MAX_TYPE_ID)
                    {
                        throw new IOException("Corrupted field information for field " + name + ", " +
                            "type " + Integer.toHexString(type) + " in record #" + id + " " + recordName);
                    }
                    names[i] = name;
                    types[i] = type;
                }
                // Try to find record
                DataRecord record = scheme.findRecordByName(recordName);
                if (record == null) {
                    // Complain (at most once) if not found
                    if (unknownRecordNames == null)
                        unknownRecordNames = new HashSet<>();
                    if (unknownRecordNames.add(recordName))
                        // complain only once about unknown record, see QD-436
                        log.info("Record #" + id + " '" + recordName + "' " +
                            "is not found in data scheme. Incoming data and subscription will be skipped.");
                }
                // Create reader descriptor
                try {
                    remapRecord(id, wrapRecordDesc(
                        new BinaryRecordDesc(record, nFld, names, types, readEventTimeSequence, BinaryRecordDesc.DIR_READ)));
                } catch (BinaryRecordDesc.InvalidDescException e) {
                    log.info("Record #" + id + " '" + recordName + "' " + "cannot be parsed: " + e.getMessage());
                }
                lastRecPosition = msg.totalPosition();
            }
        } catch (IOException e) {
            dumpParseMessageErrorReport(msg, e.getMessage(), e, lastRecPosition);
            throw new CorruptedException(e);
        }
    }

    // is overridden by ConnectionByteArrayParser to do the actual job
    void updateCursorTimeMark(RecordCursor cursor) {}

    // is overridden by ConnectionByteArrayParser to update rtt
    void updateMoreIOReadSubRecordStats() {}

    // is overridden by ConnectionByteArrayParser to update rtt and lag
    void updateMoreIOReadDataRecordStats() {}

    private void parseData(BufferedInput msg, RecordBuffer buf) throws CorruptedException {
        symbolReader.reset(ProtocolOption.SUPPORTED_SET);
        long lastRecPosition = msg.totalPosition();
        long startBufLimit = buf.getLimit();
        try {
            while (msg.hasAvailable()) {
                readSymbol(msg);
                int id = readRecordId(msg);
                BinaryRecordDesc rr = getOrCreateRecordDesc(id);
                if (rr == null)
                    throw new IOException("Unknown record #" + id);
                RecordCursor cur = rr.readRecord(msg, buf, symbolReader.getCipher(), symbolReader.getSymbol(),
                    symbolReader.getEventFlags());
                setEventTimeSequenceIfNeeded(cur);
                replaceFieldIfNeeded(cur);
                long position = msg.totalPosition();
                if (cur != null) {
                    updateCursorTimeMark(cur);
                    stats.updateIOReadRecordBytes(cur.getRecord().getId(), position - lastRecPosition);
                    stats.updateIOReadDataRecord();
                    updateMoreIOReadDataRecordStats();
                }
                lastRecPosition = position;
            }
        } catch (IOException | IllegalStateException e) {
            // IllegalStateException Happens when visiting of previous record was not properly finished.
            // IllegalStateException Happens when data schemes are incompatible or message is corrupted.
            dumpParseDataErrorReport(msg, e, buf, startBufLimit, lastRecPosition);
            throw new CorruptedException(e);
        }
    }

    private void parseSubscription(BufferedInput msg, RecordBuffer buf, MessageType messageType) throws CorruptedException {
        symbolReader.reset(ProtocolOption.SUPPORTED_SET);
        long lastRecPosition = msg.totalPosition();
        long startBufLimit = buf.getLimit();
        boolean historySubscriptionAdd = messageType.isHistorySubscriptionAdd();
        try {
            while (msg.hasAvailable()) {
                readSymbol(msg);
                int id = readRecordId(msg);
                BinaryRecordDesc rr = getOrCreateRecordDesc(id);
                if (rr == null)
                    throw new IOException("Unknown record #" + id);
                long time = 0;
                if (historySubscriptionAdd)
                    time = readSubscriptionTime(msg);
                long position = msg.totalPosition();
                DataRecord record = rr.getRecord();
                if (record != null) {
                    RecordCursor cur = buf.add(record, symbolReader.getCipher(), symbolReader.getSymbol());
                    setEventTimeSequenceIfNeeded(cur);
                    cur.setEventFlags(symbolReader.getEventFlags());
                    cur.setTime(time);
                    cur.setEventFlags(messageType.isSubscriptionRemove() ? EventFlag.REMOVE_SYMBOL.flag() : 0);
                    stats.updateIOReadRecordBytes(record.getId(), position - lastRecPosition);
                    stats.updateIOReadSubRecord();
                    updateMoreIOReadSubRecordStats();
                }
                lastRecPosition = position;
            }
        } catch (IOException | IllegalStateException e) {
            // IllegalStateException Happens when visiting of previous record was not properly finished.
            // IllegalStateException Happens when data schemes are incompatible or message is corrupted.
            dumpParseSubscriptionErrorReport(msg, e, buf, startBufLimit, historySubscriptionAdd, lastRecPosition);
            throw new CorruptedException(e);
        }
    }

    protected void readSymbol(BufferedInput msg) throws IOException {
        symbolReader.readSymbol(msg, symbolResolver);
    }

    protected int readRecordId(BufferedInput msg) throws IOException {
        return msg.readCompactInt();
    }

    // overridden in file analysis tool
    protected BinaryRecordDesc wrapRecordDesc(BinaryRecordDesc desc) {
        return desc;
    }

    protected long readSubscriptionTime(BufferedInput msg) throws IOException {
        return msg.readCompactLong();
    }

    @Nonnull
    protected BinaryRecordDesc[] newRecordMap(BinaryRecordDesc[] recordMap, int id) {
        int len = recordMap == null ? 0 : recordMap.length;
        int newLen = Math.max(Math.max(10, id + 1), len * 3 / 2);
        BinaryRecordDesc[] newRecordMap = new BinaryRecordDesc[newLen];
        if (recordMap != null)
            System.arraycopy(recordMap, 0, newRecordMap, 0, len);
        return newRecordMap;
    }

    // Note, this protected method is overridden in MDS to correct records
    protected void remapRecord(int id, BinaryRecordDesc rr) {
        if (recordMap == null || id >= recordMap.length)
            recordMap = newRecordMap(recordMap, id);
        recordMap[id] = rr;
    }

    private BinaryRecordDesc getRecordDesc(int id) {
        BinaryRecordDesc[] recordMap = this.recordMap;
        return recordMap != null && id >= 0 && id < recordMap.length ? recordMap[id] : null;
    }

    private BinaryRecordDesc getOrCreateRecordDesc(int id) {
        BinaryRecordDesc rr = getRecordDesc(id);
        if (rr != null)
            return rr;
        if (!isSchemeKnown())
            return null;
        // No description, but scheme is known
        if (id >= 0 && id < scheme.getRecordCount()) {
            DataRecord record = scheme.getRecord(id);
            try {
                rr = wrapRecordDesc(new BinaryRecordDesc(record, false, BinaryRecordDesc.DIR_READ, true));
                remapRecord(id, rr);
                return rr;
            } catch (BinaryRecordDesc.InvalidDescException e) {
                log.info("Record #" + id + " '" + record.getName() + "' " + "cannot be parsed: " + e.getMessage());
            }
        }
        return null;
    }

    // ------------------------ message part support ------------------------

    private static class PartitionedMessage {
        final long id;
        final long totalLength;
        final ChunkedInput in;
        long remaining;

        PartitionedMessage(long id, long totalLength) {
            this.id = id;
            this.totalLength = totalLength;
            this.in = new ChunkedInput();
            this.remaining = totalLength;
        }
    }

    // todo: optionally: reuse partitioned messages to avoid garbage
    private static final IndexerFunction.LongKey<PartitionedMessage> PARTITIONED_MESSAGE_BY_ID_INDEXER = message -> message.id;

    private void parseMessagePart(BufferedInput msg, MessageConsumer consumer, int payloadLength) throws IOException, CorruptedException {
        //
        // First message part payload consists of these elements:
        //
        //  <id> <totalLength> <messageType> <messagePayloadPart>
        //      |                                                |
        //      +------------------------------------------------+
        //                      messagePart
        //
        // Subsequent parts do not have any structure inside messagePart
        //
        PartitionedMessage partitionedMessage;
        int partLength;
        try {
            long idPosition = msg.totalPosition();
            long id = msg.readCompactLong();
            partLength = payloadLength - (int) (msg.totalPosition() - idPosition);
            if (partitionedMessages == null)
                partitionedMessages = IndexedSet.createLong(PARTITIONED_MESSAGE_BY_ID_INDEXER);
            partitionedMessage = partitionedMessages.getByKey(id);
            if (partitionedMessage == null) {
                if (partLength == 0)
                    return;
                long lengthPosition = msg.totalPosition();
                long totalLength = msg.readCompactLong();
                // TODO: control maximum length of partitioned (RMI) messages 
                if (totalLength < 0 || totalLength > Integer.MAX_VALUE) {
                    dumpParseMessageErrorReport(msg, "Invalid totalLength=" + totalLength, null, -1);
                    throw new CorruptedException();
                }
                int lengthLen = (int) (msg.totalPosition() - lengthPosition);
                int typeId = parseMessageType(msg);
                if (typeId < 0)
                    return; // reported error in typeId
                if (typeId == MESSAGE_PART) {
                    dumpParseMessageErrorReport(msg, "Cannot have MESSAGE_PART inside MESSAGE_PART", null, -1);
                    throw new CorruptedException();
                }
                msg.seek(lengthPosition); // undo reading length and type, will need to read them into message part
                partitionedMessages.add(partitionedMessage = new PartitionedMessage(id, totalLength + lengthLen));
            }
            if (partLength == 0) { // cancellation of an earlier partially sent message
                partitionedMessages.removeKey(id);
                return;
            }
        } catch (EOFException e) {
            dumpParseMessageErrorReport(msg, e.getMessage(), e, -1);
            throw new CorruptedException(e);
        }

        int readLen = (int) Math.min(partLength, partitionedMessage.remaining);
        partitionedMessage.remaining -= readLen;
        while (readLen > 0) {
            Chunk chunk = ChunkPool.DEFAULT.getChunk(this);
            int chunkReadLen = Math.min(chunk.getLength(), readLen);
            msg.readFully(chunk.getBytes(), chunk.getOffset(), chunkReadLen);
            chunk.setLength(chunkReadLen, this);
            readLen -= chunkReadLen;
            partitionedMessage.in.addToInput(chunk, this);
        }
        if (partitionedMessage.remaining <= 0) {
            try {
                parseImpl(partitionedMessage.in, consumer, partitionedMessage.totalLength);
            } finally {
                partitionedMessages.remove(partitionedMessage);
                partitionedMessage.in.clear(); // recycle all remaining unparsed chunks
            }
        }
    }

    // ------------------------ parsing error reporting ------------------------

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final int DUMP_LAST_RECORDS = 10;

    // in must be marked
    private void dumpParseHeaderErrorReport(BufferedInput in, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Corrupted QTP byte stream: ").append(message);
        long lastPosition = in.totalPosition();
        in.reset();
        in.mark(); // reset and mark again to resync if needed
        int cnt = (int) (lastPosition - in.totalPosition());
        appendBytes(sb, in, cnt, -1);
        log.error(sb.toString());
    }

    private void dumpParseMessageErrorReport(BufferedInput msg, String message, Exception e, long lastRecPosition) {
        StringBuilder sb = new StringBuilder();
        appendMessageErrorHead(sb, message);
        appendMessageErrorTail(msg, sb, lastRecPosition);
        log.error(sb.toString(), e);
    }

    private void dumpParseDataErrorReport(BufferedInput msg, Exception e, RecordBuffer buf,
        long startBufLimit,
        long lastRecPosition)
    {
        StringBuilder sb = new StringBuilder();
        appendMessageErrorHead(sb, e.getMessage());
        sb.append("\n++> === Last parsed data ===");
        // dump at most 10 last data records (RecordBuffer.next() advances but does not remove records)
        RecordBuffer rb = prepareToDumpLastRecords(buf);
        RecordCursor cur;
        while ((cur = rb.next()) != null) {
            sb.append("\n++> ");
            DataRecord record = cur.getRecord();
            sb.append(record.getName());
            String symbol = scheme.getCodec().decode(cur.getCipher(), cur.getSymbol());
            sb.append('\t').append(symbol);
            for (int i = 0; i < record.getIntFieldCount(); i++)
                try {
                    sb.append('\t').append(record.getIntField(i).getString(cur));
                } catch (Throwable ignored) {
                }
            for (int i = 0; i < record.getObjFieldCount(); i++)
                try {
                    sb.append('\t').append(record.getObjField(i).getString(cur));
                } catch (Throwable ignored) {
                }
        }
        appendLastSymbol(sb);
        appendMessageErrorTail(msg, sb, lastRecPosition);
        log.error(sb.toString(), e);
        recoverBuffer(buf, startBufLimit);
    }

    private void dumpParseSubscriptionErrorReport(BufferedInput msg, Exception e, RecordBuffer buf,
        long startBufLimit,
        boolean history, long lastRecPosition)
    {
        StringBuilder sb = new StringBuilder();
        appendMessageErrorHead(sb, e.getMessage());
        sb.append("\n++> === Last parsed subscription ===");
        // dump at most 10 last data records (RecordBuffer.next() advances but does not remove records)
        RecordBuffer rb = prepareToDumpLastRecords(buf);
        RecordCursor cur;
        while ((cur = rb.next()) != null) {
            sb.append("\n++> ");
            sb.append(cur.getRecord().getName());
            sb.append('\t').append(cur.getDecodedSymbol());
            if (history)
                sb.append('\t').append(cur.getTime());
        }
        appendLastSymbol(sb);
        appendMessageErrorTail(msg, sb, lastRecPosition);
        log.error(sb.toString(), e);
        recoverBuffer(buf, startBufLimit);
    }

    private static void appendMessageErrorHead(StringBuilder sb, String message) {
        sb.append("Corrupted QTP message: ").append(message);
    }

    private void appendLastSymbol(StringBuilder sb) {
        sb.append("\n++> Last symbol: (").append(symbolReader.getCipher());
        sb.append(", ").append(symbolReader.getSymbol());
        sb.append(") = ").append(scheme.getCodec().decode(symbolReader.getCipher(), symbolReader.getSymbol()));
    }

    private void appendMessageErrorTail(BufferedInput msg, StringBuilder sb, long lastRecPosition) {
        try {
            // dump at most 160 bytes
            long curPosition = msg.totalPosition();
            msg.reset();
            int parsedBytes = (int) (curPosition - msg.totalPosition());
            int skipBytes = Math.max(0, parsedBytes - 160);
            msg.skip(skipBytes);
            int cnt = parsedBytes - skipBytes;
            appendBytes(sb, msg, cnt, lastRecPosition);
        } catch (IOException e) {
            throw new AssertionError(e); // cannot happen
        }
    }

    private void appendBytes(StringBuilder sb, BufferedInput in, int cnt, long lastRecPosition) {
        try {
            sb.append("\n++> === Last parsed bytes ===");
            if (lastRecPosition >= 0)
                sb.append("\n++> '|' shows where to last record was successfully parsed");
            sb.append("\n++> '!' shows where to last byte was read");
            // show 16-32 extra bytes if available
            long startShowPosition = in.totalPosition();
            int wantToShowCnt = cnt + 32 - cnt % 16;
            int canShowCnt = Math.min(wantToShowCnt, in.available());
            char[] asciiBuf = new char[16];
            for (int i = 0; i < wantToShowCnt; i++) {
                if ((i & 0xf) == 0)
                    sb.append(String.format(Locale.US, "\n++> 0x%08x: ", startShowPosition + i));
                int b = i < canShowCnt ? in.read() : -1;
                sb.append(i == cnt ? '!' : startShowPosition + i == lastRecPosition ? '|' : ' ');
                if (b < 0)
                    sb.append("  ");
                else
                    sb.append(HEX[(b >> 4) & 15]).append(HEX[b & 15]);
                asciiBuf[i & 0xf] = b < 0 ? ' ' : b >= ' ' ? (char) b : '.';
                if ((i & 0xf) == 0xf)
                    sb.append("   ").append(asciiBuf);
            }
            sb.append("\n++> === END ===");
        } catch (IOException e) {
            throw new AssertionError(e); // cannot happen
        }
    }

    private RecordBuffer prepareToDumpLastRecords(RecordBuffer buf) {
        for (int i = 0; i < buf.size() - DUMP_LAST_RECORDS; i++)
            buf.next();
        return buf;
    }

    private void recoverBuffer(RecordBuffer buf, long startBufLimit) {
        buf.rewind();
        buf.setLimit(startBufLimit);
    }

    // ------------------------ symbol receiver implementation ------------------------

    /**
     * This exception is thrown to indicate that stream is corrupted.
     */
    protected static class CorruptedException extends Exception {
        private static final long serialVersionUID = 0;

        public CorruptedException() {}

        public CorruptedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * This exception is thrown to indicate that message is corrupted.
     */
    protected static class CorruptedMessageException extends CorruptedException {
        private static final long serialVersionUID = 0;

        protected final int messageTypeId;

        public CorruptedMessageException(Throwable cause, int messageTypeId) {
            super(cause);
            this.messageTypeId = messageTypeId;
        }
    }

    // ------------------------ hooks for statistical analysis  ------------------------

    protected void doBeforeMessageLength(BufferedInput in) {}
    protected void doAfterMessageType(BufferedInput in) {}
    protected void doAfterMessageBody(BufferedInput in, int messageType) {}

}
