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
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.TimeSequenceUtil;

import java.io.IOException;
import java.util.Arrays;

/**
 * Base class for classes that compose QTP messages data into a byte stream in some format.
 * This class has some common composing infrastructure for all formats.
 * Descendants of this class implement particular formats.
 * The output for this composer must be configured with {@link #setOutput(BufferedOutput)} method
 * immediately after construction.
 *
 * <p>Note, that all methods declared to throw {@link IOException} actually never do so if the underlying
 * {@link BufferedOutput} is memory-only. They are declared to throw {@code IOException}
 * only in order not to process it in every subclass but to process it in the outermost methods only,
 * where they are wrapped in {@link RuntimeQTPException} if they happen.
 *
 * <h3>Method naming convention</h3>
 *
 * <p><b>composeXXX</b> methods are entry-level methods. The main method is {@link #compose(MessageProvider)}.
 * All compose methods update the number of written bytes in {@link #stats}.
 *
 * <p><b>visitXXX and writeXXX</b> methods are helper methods that are invoked during composing. However,
 * <b>visitXXXData/Subscription</b> methods can be invoked directly when stats are not needed.
 * They update only per-record {@link #stats} when appropriate.
 *
 * <p><b>This class is not thread-safe.</b>
 *
 * <p>It can be reused to compose unrelated messages record descriptions are not being written
 * (see {@link #AbstractQTPComposer(DataScheme, boolean)} or if {@link #resetSession()} is called
 * to clean up its state.
 *
 * @see AbstractQTPParser
 */
public abstract class AbstractQTPComposer extends AbstractMessageVisitor
    implements DataVisitor, SubscriptionVisitor, RecordSink
{
    private static final byte RECORD_STATE_NEW = 0;
    private static final byte RECORD_STATE_DESCRIBED = 1;

    // ======================== protected instance fields ========================

    protected final DataScheme scheme;

    protected QDStats stats = QDStats.VOID;

    protected MessageType currentMessageType;

    protected boolean writeEventTimeSequence;
    protected boolean writeHeartbeat;
    protected ProtocolOption.Set optSet = ProtocolOption.EMPTY_SET; // backwards-compatible by default

    /**
     * This is temporary buffer where message is composed into. It always uses {@link ChunkPool#DEFAULT default}
     * chunk pool that is aligned in chunk size with {@link QTPConstants#COMPOSER_THRESHOLD}.
     */
    protected final ChunkedOutput msg = new ChunkedOutput(); // message is composed into here

    // ======================== private instance fields ========================

    private final byte[] recordState; // null when scheme should not be described

    private BufferedOutput output; // actual output, configured with setOutput

    private long messagePayloadStartPosition;

    private int lastRecordId = -1;
    private long lastRecordPosition;
    private long lastPayloadTimeSequence;

    private boolean inMessage; // true when beginMessage was invoked, but endMessage was not yet.

    // ======================== constructor and instance methods ========================

    /**
     * Constructs composer.
     * You must {@link #setOutput(BufferedOutput) setOutput} before using this composer.
     *
     * @param scheme the data scheme.
     * @param describeRecords if <code>true</code>, then describe messages are composed right before
     *                        records are used for the first time and this instance keeps its state and shall not be
     *                        reused for different communication sessions. See {@link #resetSession()}.
     */
    protected AbstractQTPComposer(DataScheme scheme, boolean describeRecords) {
        this.scheme = scheme;
        if (describeRecords) {
            recordState = new byte[scheme.getRecordCount()];
            //noinspection ConstantConditions
            assert RECORD_STATE_NEW == 0; // not need to clear array
        } else
            recordState = null;
    }

    // ------------------------ configuration methods ------------------------

    /**
     * Changes output for the composed messages.
     * @param output output for the composed messages.
     */
    public void setOutput(BufferedOutput output) {
        this.output = output;
    }

    /**
     * Changes stats to gather composer statistics.
     * @param stats stats to gather composer statistics.
     */
    public void setStats(QDStats stats) {
        if (stats == null)
            throw new NullPointerException();
        this.stats = stats;
    }

    public void setWriteEventTimeSequence(boolean writeEventTimeSequence) {
        this.writeEventTimeSequence = writeEventTimeSequence;
    }

    public void setWriteHeartbeat(boolean writeHeartbeat) {
        this.writeHeartbeat = writeHeartbeat;
    }

    public void setOptSet(ProtocolOption.Set optSet) {
        this.optSet = optSet;
    }

    // ------------------------ session control ------------------------

    /**
     * Resets session state for composer with describe records mode. Resets the state of all described
     * records so far, so that describe messages are started to being sent again, as if this composer
     * was just created.
     * @see #AbstractQTPComposer(DataScheme, boolean)
     * @throws UnsupportedOperationException when composer was constructed without describe records mode.
     */
    public void resetSession() {
        if (recordState == null)
            throw new UnsupportedOperationException("describe records mode was not set");
        Arrays.fill(recordState, RECORD_STATE_NEW);
        if (inMessage) {
            inMessage = false;
            undoWriteMessageHeaderStateChange();
            msg.clear();
        }
    }

    // ------------------------ top-level composing ------------------------

    /**
     * Composes message from the corresponding message provider to {@link #setOutput(BufferedOutput) output}
     * and updates number of written bytes in {@link #setStats(QDStats) stats}.
     *
     * <p>This is the outer method from which all the other <b>visitXXX</b> and <b>writeXXX</b> should be written in order
     * to properly record written bytes statistics if needed. However, there are also other <b>composeXXX</b> methods
     * that must be invoked directly (not from inside of this compose invocation).
     *
     * <p>Inside of this method {@link #endMessage()} and {@link #beginMessage(MessageType)} can be used to
     * to arbitrarily delimit messages.
     *
     * @return <code>false</code> if it had composed all available messages and no messages left to compose,
     *         <code>true</code> if more message remain to be composed
     */
    public final boolean compose(MessageProvider provider) {
        try {
            long composeStartPosition = output.totalPosition(); // the actual position
            boolean result = provider.retrieveMessages(this);
            stats.updateIOWriteBytes(output.totalPosition() - composeStartPosition);
            return result;
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
            return false; // it is not actually reached, because abortMessageAndRethrow never returns normally
        }
    }

    /**
     * Composes describe protocol message to {@link #setOutput(BufferedOutput) output}
     * and updates number of written bytes in {@link #setStats(QDStats) stats}.
     */
    public final void composeDescribeProtocol(ProtocolDescriptor descriptor) {
        try {
            long composeStartPosition = output.totalPosition(); // the actual position
            writeDescribeProtocolMessage(output, descriptor);
            stats.updateIOWriteBytes(output.totalPosition() - composeStartPosition);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    /**
     * Composes empty heartbeat message to {@link #setOutput(BufferedOutput) output}
     * and updates number of written bytes in {@link #setStats(QDStats) stats}.
     */
    public final void composeEmptyHeartbeat() {
        try {
            long composeStartPosition = output.totalPosition(); // the actual position
            writeEmptyHeartbeatMessage(output);
            stats.updateIOWriteBytes(output.totalPosition() - composeStartPosition);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    /**
     * Composes heartbeat message to {@link #setOutput(BufferedOutput) output}
     * and updates number of written bytes in {@link #setStats(QDStats) stats}.
     */
    public final void composeHeartbeatMessage(HeartbeatPayload heartbeatPayload) {
        try {
            long composeStartPosition = output.totalPosition(); // the actual position
            writeHeartbeatMessage(output, heartbeatPayload);
            stats.updateIOWriteBytes(output.totalPosition() - composeStartPosition);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    /**
     * Composes heartbeat message with progress report to {@link #setOutput(BufferedOutput) output}
     * and updates number of written bytes in {@link #setStats(QDStats) stats}.
     */
    public final void composeTimeProgressReport(long timeMillis) {
        HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
        heartbeatPayload.setTimeMillis(timeMillis);
        composeHeartbeatMessage(heartbeatPayload);
    }

    // ------------------------ support for bounded-size messages ------------------------

    /**
     * Returns {@code true} when this message's capacity had not exceeded threshold yet.
     * This implementation always returns {@code true} when invoked not in message,
     * e.g. the overall number of bytes composed is not checked by this implementation
     * (only the message size for data and subscription messages is limited).
     * Each call to {@link #beginMessage(MessageType)} resets capacity threshold for a new message.
     */
    @Override
    public boolean hasCapacity() {
        return !inMessage || msg.totalPosition() < messagePayloadStartPosition + QTPConstants.COMPOSER_THRESHOLD;
    }

    // number of bytes composed in message payload so far, returns zero when not in message
    protected long getMessagePayloadSize() {
        return !inMessage ? 0 : msg.totalPosition() - messagePayloadStartPosition;
    }

    // ------------------------ MessageVisitor implementation ------------------------

    @Override
    public void visitDescribeProtocol(ProtocolDescriptor descriptor) {
        try {
            writeDescribeProtocolMessage(output, descriptor);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    @Override
    public void visitHeartbeat(HeartbeatPayload heartbeatPayload) {
        if (heartbeatPayload.hasTimeMillis())
            lastPayloadTimeSequence = TimeSequenceUtil.getTimeSequenceFromTimeMillis(
                heartbeatPayload.getTimeMillis());
        if (!writeHeartbeat)
            return; // don't write the actual heartbeat in this mode
        if (heartbeatPayload.isEmpty())
            return; // write nothing
        try {
            writeHeartbeatMessage(output, heartbeatPayload);
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visitData(DataProvider provider, MessageType type) {
        if (!type.isData())
            throw new IllegalArgumentException(type.toString());
        if (!hasCapacity()) // in case when implementation overrides hasCapacity to limit the overall number of bytes composed
            return true;
        try {
            beginMessage(type);
            boolean hasMore;
            // QD-990: optimize for "spurious returns", when provider has more data but had bailed out despite the fact
            // that this composer still has capacity. Loop early, instead or returning and doing it on outer level
            do {
                hasMore = provider.retrieveData(this);
            } while (hasMore && hasCapacity());
            flushRecordStats();
            endMessage();
            return hasMore;
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
            return false; // it is not actually reached, because abortMessageAndRethrow never returns normally
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visitSubscription(SubscriptionProvider provider, MessageType type) {
        if (!type.isSubscription())
            throw new IllegalArgumentException(type.toString());
        if (!hasCapacity()) // in case when implementation overrides hasCapacity to limit the overall number of bytes composed
            return true;
        try {
            beginMessage(type);
            boolean hasMore;
            // QD-990: optimize for "spurious returns", when provider has more data but had bailed out despite the fact
            // that this composer still has capacity. Loop early, instead or returning and doing it on outer level
            do {
                hasMore = provider.retrieveSubscription(this);
            } while (hasMore && hasCapacity());
            flushRecordStats();
            endMessage();
            return hasMore;
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
            return false; // it is not actually reached, because abortMessageAndRethrow never returns normally
        }
    }

    /**
     * This method consumes other message type.
     *
     * @param messageType integer number representing a type of the message.
     * @param messageBytes array containing message data.
     * @param offset position of the first byte of message data in {@code messageBytes} array.
     * @param length number of bytes starting from {@code offset} in {@code messageBytes} related to this message.
     * @return <tt>true</tt> if the whole message was not processed because the visitor is full
     *         and <tt>false</tt> if the message was successfully processed.
     */
    @Override
    public boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length) {
        if (!hasCapacity()) // in case when implementation overrides hasCapacity to limit the overall number of bytes composed
            return true;
        try {
            beginMessage(MessageType.findById(messageType));
            writeOtherMessageBody(messageBytes, offset, length);
            endMessage();
            return false;
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
            return false; // it is not actually reached, because abortMessageAndRethrow never returns normally
        }
    }

    // ------------------------ RecordSink, DataVisitor & SubscriptionVisitor implementation ------------------------

    @Override
    public void append(RecordCursor cursor) {
        try {
            DataRecord record = cursor.getRecord();
            beginRecord(record);
            int eventFlags = writeRecordHeader(record, cursor.getCipher(), cursor.getSymbol(), cursor.getEventFlags());
            writeRecordPayload(cursor, eventFlags);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>This implementation does nothing.
     */
    @Override
    public void flush() {}

    /** @deprecated Use {@link #append(RecordCursor)} */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol) {
        try {
            beginRecord(record);
            writeRecordHeader(record, cipher, symbol, 0);
            if (writeEventTimeSequence)
                writeEventTimeSequence(lastPayloadTimeSequence);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    /** @deprecated Use {@link #append(RecordCursor)} */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        try {
            beginRecord(record);
            writeRecordHeader(record, cipher, symbol, 0);
            if (currentMessageType.isHistorySubscriptionAdd())
                writeHistorySubscriptionTime(record, time);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    @Override
    public void visitIntField(DataIntField field, int value) {
        try {
            writeIntField(field, value);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    @Override
    public void visitObjField(DataObjField field, Object value) {
        try {
            writeObjField(field, value);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    // ------------------------ helper methods to delimit messages ------------------------

    /**
     * Returns true if we are within a message and false if not.
     */
    protected final boolean inMessage() {
        return inMessage;
    }

    /**
     * Begins new message, must be followed by {@link #endMessage()}.
     */
    public final void beginMessage(MessageType messageType){
        if (inMessage)
            throw new IllegalStateException("Already in message");
        inMessage = true;
        currentMessageType = messageType;
        // Clear msg buffer just in-case previous message composing crashed and left msg in dirty state,
        // because the code was not wrapped in try { ... } catch (Throwable t) { abortMessageAndRethrow(t); }
        msg.clear();
        try {
            writeMessageHeader(messageType);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
        messagePayloadStartPosition = msg.totalPosition();
    }

    /**
     * End current message that was started with {@link #beginMessage(MessageType)} and copies composed
     * message to {@link #setOutput(BufferedOutput) output}.
     *
     * <p>This method can be invoked while composing message with
     * {@link #visitData(DataProvider, MessageType)} or {@link #visitSubscription(SubscriptionProvider, MessageType)}.
     * It can be followed by {@link #resetSession()}.
     * If invoked while composing, the message should be resumed by {@link #beginMessage(MessageType)}
     * invocation before returning control to the composing method.
     */
    public final void endMessage() {
        if (!inMessage)
            throw new IllegalStateException("Not in message");
        inMessage = false;
        if (messagePayloadStartPosition == msg.totalPosition()) {
            // collapse message without payload
            undoWriteMessageHeaderStateChange();
            msg.clear();
            return;
        }
        // flush message to out
        try {
            finishComposingMessage(output);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    protected void abortMessageAndRethrow(Throwable t) {
        inMessage = false;
        msg.clear();
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        if (t instanceof  Error)
            throw (Error) t;
        throw new RuntimeQTPException(t);
    }


    // ------------------------ impl methods to write special messages ------------------------

    // special: is invoked outside of beginMessage/endMessage
    protected void writeDescribeProtocolMessage(BufferedOutput out, ProtocolDescriptor descriptor) throws IOException {}

    // special: is invoked outside of beginMessage/endMessage
    protected void writeEmptyHeartbeatMessage(BufferedOutput out) throws IOException {}

    // special: is invoked outside of beginMessage/endMessage
    protected void writeHeartbeatMessage(BufferedOutput out, HeartbeatPayload heartbeatPayload) throws IOException {}

    // ------------------------ impl methods to write protocol elements ------------------------

    /**
     * Composes header for a message of a specific type.
     *
     * @param messageType the message type.
     * @throws IOException never. If it has been thrown then it means an internal error.
     */
    protected abstract void writeMessageHeader(MessageType messageType) throws IOException;

    // extension point for text format to rollback internal state when message turns out to be empty
    protected void undoWriteMessageHeaderStateChange() {}

    // Returns supported (narrowed down) events flags
    protected abstract int writeRecordHeader(DataRecord record, int cipher, String symbol, int eventFlags) throws IOException;

    protected void writeRecordPayload(RecordCursor cursor, int eventFlags) throws IOException {
        DataRecord record = cursor.getRecord();
        if (currentMessageType.isData()) {
            if (writeEventTimeSequence)
                writeEventTimeSequence(getEventTimeSequence(cursor));
            for (int i = 0; i < cursor.getIntCount(); i++)
                writeField(record.getIntField(i), cursor);
            for (int i = 0; i < cursor.getObjCount(); i++)
                writeField(record.getObjField(i), cursor);
        } else if (currentMessageType.isHistorySubscriptionAdd())
            writeHistorySubscriptionTime(record, cursor.getTime());
    }

    // it is called only when "writeEventTimeSequence" is true
    protected void writeEventTimeSequence(long eventTimeSequence) throws IOException {}

    protected abstract void writeHistorySubscriptionTime(DataRecord record, long time) throws IOException;

    protected abstract void writeIntField(DataIntField field, int value) throws IOException;

    protected abstract void writeObjField(DataObjField field, Object value) throws IOException;

    protected abstract void writeField(DataField field, RecordCursor cursor) throws IOException;

    // by default, other kinds of messages are not supported
    protected void writeOtherMessageBody(byte[] messageBytes, int offset, int length) throws IOException {}

    /**
     * Performs actions necessary to finish composing a message
     * (for example, inserts message size, inserts records descriptions if necessary).
     * Used by {@link BinaryQTPComposer}. This implementation just copies message
     * from {@link #msg} to {@code out}.
     */
    protected void finishComposingMessage(BufferedOutput out) throws IOException {
        ChunkList chunks = msg.getOutput(this);
        out.writeAllFromChunkList(chunks, this);
    }

    // ------------------------ helper methods for time sequences ------------------------

    protected long getEventTimeSequence(RecordCursor cursor) {
        long eventTimeSequence = cursor.getEventTimeSequence();
        return eventTimeSequence != 0 ? eventTimeSequence : lastPayloadTimeSequence;
    }

    // ------------------------ describe records support ------------------------

    protected void describeRecord(DataRecord record) throws IOException {}

    // ------------------------ helper methods for  describe records and per-record stats ------------------------

    // ConnectionByteArrayComposer overrides to update rtt
    void updateMoreIOWriteRecordStats() {}

    protected final void beginRecord(DataRecord record) {
        flushRecordStats();
        int id = record.getId();
        if (recordState != null && recordState[id] == RECORD_STATE_NEW) {
            try {
                describeRecord(record);
            } catch (IOException e) {
                throw new AssertionError(e); // cannot happen
            }
            recordState[id] = RECORD_STATE_DESCRIBED;
        }
        lastRecordId = id;
        lastRecordPosition = msg.totalPosition();
    }

    private void flushRecordStats() {
        if (lastRecordId >= 0) {
            stats.updateIOWriteRecordBytes(lastRecordId, msg.totalPosition() - lastRecordPosition);
            if (currentMessageType.isData()) {
                stats.updateIOWriteDataRecord();
            } else {
                stats.updateIOWriteSubRecord();
            }
            updateMoreIOWriteRecordStats();
            lastRecordId = -1;
        }
    }
}
