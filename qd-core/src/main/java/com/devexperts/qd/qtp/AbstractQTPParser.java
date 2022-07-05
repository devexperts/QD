/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.io.BufferedInput;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.fieldreplacer.FieldReplacersCache;
import com.devexperts.qd.stats.QDStats;

import java.io.IOException;

/**
 * Base class for classes that parse QTP messages data from byte stream in some format.
 * This class has some common parsing infrastructure for all formats.
 * Descendants of this class implement particular formats.
 * The input for this parser must be configured with {@link #setInput(BufferedInput)} method
 * immediately after construction.
 *
 * <p>Note, that all methods declared to throw {@link IOException} actually never do so if the underlying
 * {@link BufferedInput} is memory-only. They are declared to throw {@code IOException}
 * only in order not to process it in every subclass but to process it in the outermost methods only,
 * where they are wrapped in {@link RuntimeQTPException} if they happen.
 *
 * <p><b>This class is not thread-safe.</b>
 *
 * <p>It can be reused to parse unrelated messages if they do not contain record descriptions or if
 * {@link #resetSession()} is called to clean up its state.
 *
 * @see AbstractQTPComposer
 */
public abstract class AbstractQTPParser {

    private static final int N_PENDING = 3; // keep up to the messages pending processing

    // ======================== protected instance fields ========================

    protected final DataScheme scheme;

    /**
     * Where to collect stats to. Set by {@link #parse(MessageConsumer)}.
     */
    protected QDStats stats = QDStats.VOID;

    /**
     * Flag to read virtual EventTime/EventSequence fields from messages into {@link RecordMode#TIMESTAMPED_DATA} buffer.
     */
    protected boolean readEventTimeSequence;

    /**
     * Flag to combine add/remove subscription into a single addSubscription message.
     */
    protected boolean mixedSubscription;

    /**
     * Time and sequence which should be set to cursor.
     */
    protected long eventTimeSequence;

    /**
     * Cache of instantiated field replacers.
     */
    protected FieldReplacersCache fieldReplacers;

    // ======================== private instance fields ========================

    /**
     * Current input to parse from.
     */
    private BufferedInput input;

    /**
     * The buffers that are pending processing.
     */
    private final RecordBuffer[] pendingRecordBuffers = new RecordBuffer[N_PENDING];

    /**
     * Message types that are pending process in {@link #pendingRecordBuffers}.
     * Call {@link #processPending(MessageConsumer)} to process the pending messages.
     */
    private final MessageType[] pendingMessageTypes = new MessageType[N_PENDING];

    /**
     * Bit mask of messages in {@link #pendingMessageTypes}.
     */
    private int pendingMessageMask;

    /**
     * If not null, then treats all messages as having this type.
     */
    private MessageType readAs;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs parser with a specified scheme.
     * You must {@link #setInput(BufferedInput) setInput} before using this parser.
     *
     * @param scheme data scheme.
     */
    protected AbstractQTPParser(DataScheme scheme) {
        this.scheme = scheme;
    }

    // ------------------------ configuration methods ------------------------

    /**
     * Changes input to parse messages from.
     * @param input input to parse messages from.
     */
    public void setInput(BufferedInput input) {
        this.input = input;
    }

    /**
     * Changes stats to gather parser statistics.
     * @param stats stats to gather parser statistics.
     */
    public void setStats(QDStats stats) {
        this.stats = stats;
    }

    /**
     * Overrides message type for all data and subscription messages read by this parser.
     * @param readAs the message type that will be reported for all data and subscription messages,
     *               use {@code null} to turn off override.
     */
    public void readAs(MessageType readAs) {
        if (readAs != null && !readAs.hasRecords())
            throw new IllegalArgumentException("Invalid readAs=" + readAs);
        this.readAs = readAs;
    }

    public void setReadEventTimeSequence(boolean readEventTimeSequence) {
        this.readEventTimeSequence = readEventTimeSequence;
    }

    public void setMixedSubscription(boolean mixedSubscription) {
        this.mixedSubscription = mixedSubscription;
    }

    public void setEventTimeSequence(long eventTimeSequence) {
        this.eventTimeSequence = eventTimeSequence;
    }

    public void setFieldReplacers(FieldReplacersCache fieldReplacers) {
        this.fieldReplacers = fieldReplacers;
    }

    // ------------------------ session control ------------------------

    public void resetSession() {}

    // ------------------------ top-level parsing ------------------------

    /**
     * Parses accumulated bytes and submits parsed messages to the specified MessageConsumer.
     * This method also updates per-record parsed bytes statistics in the specified {@link #setStats(QDStats) stats}.
     * This is the main method in this class.
     *
     * @param consumer MessageConsumer to pass parsed messages.
     */
    public final void parse(MessageConsumer consumer) {
        try {
            parseImpl(input, consumer);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        } finally {
            processPending(consumer);
        }
    }

    protected abstract void parseImpl(BufferedInput in, MessageConsumer consumer) throws IOException;

    // ------------------------ helper methods to process data/sub messages ------------------------

    /**
     * Processes pending message from buffers (if any) and resets them to {@code null}.
     */
    protected void processPending(MessageConsumer consumer) {
        Throwable throwable = null;
        for (int i = 0; i < N_PENDING; i++) {
            MessageType pendingMessageType = pendingMessageTypes[i];
            if (pendingMessageType == null)
                break; // done
            try {
                RecordBuffer buf = pendingRecordBuffers[i];
                if (!buf.isEmpty())
                    processRecordsMessage(buf, consumer, pendingMessageType);
                buf.release(); // only release to pool if there were no unexpected exceptions
            } catch (Throwable t) {
                if (throwable == null)
                    throwable = t; // remember only first one
            } finally {
                pendingRecordBuffers[i] = null;
                pendingMessageTypes[i] = null;
                pendingMessageMask &= ~(1 << pendingMessageType.ordinal());
            }
        }
        if (throwable != null) {
            if (throwable instanceof RuntimeException)
                throw (RuntimeException) throwable;
            if (throwable instanceof Error)
                throw (Error) throwable;
            throw new RuntimeException(throwable);
        }
    }

    protected RecordBuffer nextRecordsMessage(MessageConsumer consumer, MessageType messageType) {
        assert messageType.hasRecords();
        if (readAs != null)
            messageType = readAs; // always overwrite with "readAs"
        else if (messageType.isSubscriptionRemove() && mixedSubscription) // support mixed subscription remove + add
            messageType = MessageType.forAddSubscription(messageType.getContract());
        // force message processing when cannot be reordered with buffered messages or when no room for new record buffer
        if ((pendingMessageMask & messageType.cannotReorderWithMask) != 0 ||
            ((pendingMessageMask & (1 << messageType.ordinal())) == 0 && pendingMessageTypes[N_PENDING - 1] != null))
        {
            processPending(consumer);
        }
        for (int i = 0; i < N_PENDING; i++) {
            MessageType pendingMessageType = pendingMessageTypes[i];
            if (pendingMessageType == null) {
                RecordBuffer buf = RecordBuffer.getInstance(getRecordBufferMode(messageType));
                pendingRecordBuffers[i] = buf;
                pendingMessageTypes[i] = messageType;
                pendingMessageMask |= 1 << messageType.ordinal();
                return buf;
            }
            if (pendingMessageType == messageType)
                return pendingRecordBuffers[i];
        }
        throw new AssertionError("Cannot find space for " + messageType + ". nextMessage was not called?");
    }

    // is overriden by ConnectionByteArrayParser for MARKED_DATA
    protected RecordMode getRecordBufferMode(MessageType messageType) {
        RecordMode mode = messageType.getRecordMode();
        if (messageType.isData())
            return readEventTimeSequence ? mode.withEventTimeSequence() : mode;
        else if (messageType.isSubscription())
            return mixedSubscription ? mode.withEventFlags() : mode;
        else
            throw new IllegalArgumentException(messageType.toString());
    }

    /**
     * Processes message from the record buffer into the consumer.
     */
    private void processRecordsMessage(RecordBuffer buf, MessageConsumer consumer, MessageType messageType) {
        messageType = readAs != null && messageType.hasRecords() ? readAs : messageType;
        switch (messageType) {
        case RAW_DATA:
            if (consumer instanceof RawDataConsumer) {
                ((RawDataConsumer) consumer).processData(buf, MessageType.RAW_DATA);
            } else {
                consumer.processTickerData(buf);
                buf.rewind();
                consumer.processStreamData(buf);
                buf.rewind();
                consumer.processHistoryData(buf);
            }
            break;
        case TICKER_DATA:
            consumer.processTickerData(buf);
            break;
        case STREAM_DATA:
            consumer.processStreamData(buf);
            break;
        case HISTORY_DATA:
            consumer.processHistoryData(buf);
            break;
        case TICKER_ADD_SUBSCRIPTION:
            consumer.processTickerAddSubscription(buf);
            break;
        case TICKER_REMOVE_SUBSCRIPTION:
            consumer.processTickerRemoveSubscription(buf);
            break;
        case STREAM_ADD_SUBSCRIPTION:
            consumer.processStreamAddSubscription(buf);
            break;
        case STREAM_REMOVE_SUBSCRIPTION:
            consumer.processStreamRemoveSubscription(buf);
            break;
        case HISTORY_ADD_SUBSCRIPTION:
            consumer.processHistoryAddSubscription(buf);
            break;
        case HISTORY_REMOVE_SUBSCRIPTION:
            consumer.processHistoryRemoveSubscription(buf);
            break;
        }
    }

    protected ProtocolDescriptor applyReadAs(ProtocolDescriptor protocolDescriptor) {
        return readAs == null ? protocolDescriptor :
            ProtocolDescriptor.newPeerProtocolDescriptorReadAs(protocolDescriptor, readAs);
    }

    protected final void setEventTimeSequenceIfNeeded(RecordCursor cur) {
        if (readEventTimeSequence && eventTimeSequence != 0)
            cur.setEventTimeSequence(eventTimeSequence);
    }

    protected void replaceFieldIfNeeded(RecordCursor cursor) {
        if (fieldReplacers == null)
            return;
        fieldReplacers.accept(cursor);
    }
}
