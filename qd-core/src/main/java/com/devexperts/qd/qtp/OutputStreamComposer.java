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
import com.devexperts.io.Chunk;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This composer stores filtered data and subscription to a specified {@link OutputStream} in binary QTP format.
 * It serves as a convenience object that combines output buffer and composer together.
 * It can be used as {@link MessageVisitor}, {@link DataVisitor}, {@link SubscriptionVisitor} or {@link RecordSink} to
 * store snapshots of data to a file, which can be later read with {@link InputStreamParser}.
 * For more advanced file writing capabilities (to write event tapes) see <b>com.devexperts.qd.qtp.file</b> package.
 *
 * @see InputStreamParser
 */
public class OutputStreamComposer extends BinaryQTPComposer {

    // ======================== private instance fields ========================

    private final ChunkedOutput bytes = new ChunkedOutput(FileConstants.CHUNK_POOL);

    private OutputStream output;
    private SubscriptionFilter filter;

    private MessageType messageType;
    private boolean skipRecord;
    private int recordCounter;

    // ======================== constructor and instance methods ========================

    /**
     * Creates composer for specified data scheme.
     * You must {@link #init(OutputStream, SubscriptionFilter) init} this composer before using it.
     */
    public OutputStreamComposer(DataScheme scheme) {
        super(scheme, true);
        super.setOutput(bytes);
    }


    // ------------------------ configuration methods ------------------------

    /**
     * {@inheritDoc}
     * This implementation throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setOutput(BufferedOutput output) {
        throw new UnsupportedOperationException();
    }

    /**
     * Initializes composer with a specified output stream and subscription filter parameters.
     * It resets session state from previous composing session.
     * Both parameters could be <b>null</b> to deinitialize composer and release object references.
     */
    public void init(OutputStream output, SubscriptionFilter filter) {
        this.output = output;
        this.filter = filter;

        messageType = null;
        skipRecord = false;
        recordCounter = 0;

        resetSession();
    }

    // ------------------------ top-level composing ------------------------

    /**
     * Composes all collectors defined in specified endpoint.
     */
    public void composeEndpoint(QDEndpoint endpoint) {
        for (QDCollector collector : endpoint.getCollectors())
            composeCollector(collector);
    }

    /**
     * Composes specified collector.
     */
    public void composeCollector(QDCollector collector) {
        flush(); // safety measure in case previously visited data was not flushed
        setMessageType(MessageType.forData(collector.getContract()));
        try {
            collector.examineData(this);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
        flush();
    }

    /**
     * Sets message type for subsequent data or subscription.
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns number of composed records since initialization.
     */
    public int getRecordCounter() {
        return recordCounter;
    }

    /**
     * Finishes current message if any and sends all composed bytes to output stream.
     */
    @Override
    public void flush() {
        flushMessage();
        ChunkList chunks = bytes.getOutput(this);
        if (chunks == null)
            return;
        try {
            for (Chunk chunk : chunks)
                output.write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
            chunks.recycle(this);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    /**
     * Finishes current message.
     */
    public void flushMessage() {
        if (inMessage())
            endMessage(); // will copy bytes to output
    }

    // ------------------------ MessageVisitor implementation ------------------------

    @Override
    public boolean visitData(DataProvider provider, MessageType type) {
        setMessageType(type);
        return super.visitData(provider, type);
    }

    @Override
    public boolean visitSubscription(SubscriptionProvider provider, MessageType type) {
        setMessageType(type);
        return super.visitSubscription(provider, type);
    }

    @Override
    public boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length) {
        setMessageType(MessageType.findById(messageType));
        return super.visitOtherMessage(messageType, messageBytes, offset, length);
    }

    // ------------------------ RecordSink implementation ------------------------

    @Override
    public boolean hasCapacity() {
        if (!super.hasCapacity())
            flushMessage();
        return true;
    }

    @Override
    public void append(RecordCursor cursor) {
        skipRecord = filter != null && !filter.acceptRecord(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol());
        if (skipRecord)
            return;
        if (!inMessage())
            beginMessage(messageType);
        recordCounter++;
        super.append(cursor);
    }

    // ------------------------ DataVisitor & SubscriptionVisitor implementation ------------------------

    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        skipRecord = filter != null && !filter.acceptRecord(record, cipher, symbol);
        if (skipRecord)
            return;
        if (!inMessage())
            beginMessage(messageType);
        recordCounter++;
        super.visitRecord(record, cipher, symbol, time);
    }

    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol) {
        skipRecord = filter != null && !filter.acceptRecord(record, cipher, symbol);
        if (skipRecord)
            return;
        if (!inMessage())
            beginMessage(messageType);
        recordCounter++;
        super.visitRecord(record, cipher, symbol);
    }

    @Override
    public void visitIntField(DataIntField field, int value) {
        if (skipRecord)
            return;
        super.visitIntField(field, value);
    }

    @Override
    public void visitObjField(DataObjField field, Object value) {
        if (skipRecord)
            return;
        super.visitObjField(field, value);
    }
}
