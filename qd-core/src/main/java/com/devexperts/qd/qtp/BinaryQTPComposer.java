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
package com.devexperts.qd.qtp;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.IOUtil;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;

import java.io.IOException;

/**
 * Composes QTP messages in binary format into byte stream.
 * The output for this composer must be configured with {@link #setOutput(BufferedOutput)} method
 * immediately after construction.
 *
 * @see AbstractQTPComposer
 * @see BinaryQTPParser
 */
public class BinaryQTPComposer extends AbstractQTPComposer {
    private static final int MAX_MESSAGE_LENGTH = Integer.MAX_VALUE;
    private static final int MAX_MESSAGE_LENGTH_LENGTH = IOUtil.getCompactLength(MAX_MESSAGE_LENGTH);

    // ======================== private instance fields ========================

    private final SymbolCodec.Writer symbolWriter;
    private final ByteArrayOutput aux = new ByteArrayOutput(); // will use it to write message length
    private BinaryRecordDesc[] recordMap;
    private long messageBodyStartPosition;
    private int currentSupportedFlags;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs binary composer.
     * You must {@link #setOutput(BufferedOutput) setOutput} before using this composer.
     *
     * @param scheme the data scheme.
     * @param describeRecords if <code>true</code>, then describe messages are composed right before
     *                        records are used for the first time and this instance keeps its state and shall not be
     *                        reused for different communication sessions. See {@link #resetSession()}.
     */
    public BinaryQTPComposer(DataScheme scheme, boolean describeRecords) {
        super(scheme, describeRecords);
        symbolWriter = scheme.getCodec().createWriter();
    }

    // ------------------------ impl methods to write special messages ------------------------

    @Override
    protected void writeDescribeProtocolMessage(BufferedOutput out, ProtocolDescriptor descriptor) throws IOException {
        beginMessage(MessageType.DESCRIBE_PROTOCOL);
        descriptor.composeTo(msg);
        endMessage();
    }

    @Override
    protected void writeEmptyHeartbeatMessage(BufferedOutput out) throws IOException {
        out.writeByte(0);
    }

    @Override
    protected void writeHeartbeatMessage(BufferedOutput out, HeartbeatPayload heartbeatPayload) throws IOException {
        beginMessage(MessageType.HEARTBEAT);
        heartbeatPayload.composeTo(msg);
        endMessage();
    }

    // ------------------------ impl methods to write protocol elements ------------------------

    @Override
    protected int writeRecordHeader(DataRecord record, int cipher, String symbol, int eventFlags) throws IOException {
        eventFlags &= currentSupportedFlags;
        symbolWriter.writeSymbol(msg, cipher, symbol, eventFlags);
        msg.writeCompactInt(record.getId());
        return eventFlags;
    }

    @Override
    protected void writeRecordPayload(RecordCursor cursor, int eventFlags) throws IOException {
        if (currentMessageType.isData()) {
            getRecordDesc(cursor.getRecord()).writeRecord(msg, cursor, eventFlags, getEventTimeSequence(cursor));
        } else if (currentMessageType.isHistorySubscriptionAdd())
            writeHistorySubscriptionTime(cursor.getRecord(), cursor.getTime());
    }

    @Override
    protected void writeHistorySubscriptionTime(DataRecord record, long time) throws IOException {
        msg.writeCompactLong(time);
    }

    @Override
    protected void writeEventTimeSequence(long eventTimeSequence) throws IOException {
        throw new UnsupportedOperationException("Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeIntField(DataIntField field, int value) throws IOException {
        throw new UnsupportedOperationException("Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeObjField(DataObjField field, Object value) throws IOException {
        throw new UnsupportedOperationException("Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeField(DataField field, RecordCursor cursor) throws IOException {
        throw new UnsupportedOperationException("Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeOtherMessageBody(byte[] messageBytes, int offset, int length) throws IOException {
        msg.write(messageBytes, offset, length);
    }

    @Override
    protected void writeMessageHeader(MessageType messageType) throws IOException {
        symbolWriter.reset(optSet);
        currentSupportedFlags = EventFlag.getSupportedEventFlags(optSet, currentMessageType);
        // reserve space for maximal message length. This way we guarantee that even if message
        // is big, then we'll be able to merge its length and its first chunk into a single chunk
        msg.writeCompactInt(MAX_MESSAGE_LENGTH);
        messageBodyStartPosition = msg.totalPosition();
        msg.writeCompactInt(messageType.getId());
    }

    @Override
    protected void finishComposingMessage(BufferedOutput out) throws IOException {
        // compute message body length
        long messageBodyLength = msg.totalPosition() - messageBodyStartPosition;
        // take message body chunks
        ChunkList chunks = msg.getOutput(this);
        // correct first chunk to remove extra space that was reserved by writeMessageHeader
        // and replace it with the actual message body length
        Chunk chunk0 = chunks.get(0);
        int shift = MAX_MESSAGE_LENGTH_LENGTH - IOUtil.getCompactLength(messageBodyLength);
        aux.setBuffer(chunk0.getBytes());
        aux.setPosition(chunk0.getOffset() + shift);
        aux.writeCompactLong(messageBodyLength);
        aux.setBuffer(null);
        chunks.setChunkRange(0, chunk0.getOffset() + shift, chunk0.getLength() - shift, this);
        // write corrected chunks to output.
        out.writeAllFromChunkList(chunks, this);
    }

    // ------------------------ describe records support ------------------------

    // ConnectionQTPComposer overrides
    protected BinaryRecordDesc getRequestedRecordDesc(DataRecord record) {
        return null;
    }

    // ConnectionQTPComposer overrides
    protected boolean isWideDecimalSupported() {
        return true;
    }

    private void remapRecord(int id, BinaryRecordDesc rw) {
        if (recordMap == null || id >= recordMap.length) {
            int len = recordMap == null ? 0 : recordMap.length;
            int newLen = Math.max(Math.max(10, id + 1), len * 3 / 2);
            BinaryRecordDesc[] newRecordMap = new BinaryRecordDesc[newLen];
            if (recordMap != null)
                System.arraycopy(recordMap, 0, newRecordMap, 0, len);
            recordMap = newRecordMap;
        }
        recordMap[id] = rw;
    }

    private BinaryRecordDesc getRecordDesc(DataRecord record) throws IOException {
        int id = record.getId();
        if (recordMap != null && id >= 0 && id < recordMap.length && recordMap[id] != null)
            return recordMap[id];
        BinaryRecordDesc desc = getRequestedRecordDesc(record);
        try {
            // If the requested record description from the other side did not list any fields, then it
            // is a signal to send all fields known in the scheme (when the receiver on the other side is schema-less)
            BinaryRecordDesc rw = desc != null && !desc.isEmpty() ?
                new BinaryRecordDesc(record, desc.nDesc, desc.names, desc.types, writeEventTimeSequence, BinaryRecordDesc.DIR_WRITE) :
                new BinaryRecordDesc(record, writeEventTimeSequence, BinaryRecordDesc.DIR_WRITE, isWideDecimalSupported());
            remapRecord(id, rw);
            return rw;
        } catch (BinaryRecordDesc.InvalidDescException e) {
            throw new IOException("Cannot write record '" + record.getName() + "': " + e.getMessage(), e);
        }
    }

    @Override
    protected void describeRecord(DataRecord record) throws IOException {
        MessageType lastMessageType = currentMessageType;
        endMessage();
        beginMessage(MessageType.DESCRIBE_RECORDS);
        BinaryRecordDesc rw = getRecordDesc(record);
        msg.writeCompactInt(record.getId());
        msg.writeUTFString(record.getName());
        msg.writeCompactInt(rw.nDesc);
        for (int i = 0; i < rw.nDesc; i++) {
            msg.writeUTFString(rw.names[i]);
            msg.writeCompactInt(rw.types[i]);
        }
        endMessage();
        beginMessage(lastMessageType);
    }
}
