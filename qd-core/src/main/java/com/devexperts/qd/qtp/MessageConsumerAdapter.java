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
import com.devexperts.io.ByteArrayInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataConsumer;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.SubscriptionConsumer;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordSource;

/**
 * Implementation of MessageConsumer that does nothing. Error methods in this
 * implementation write message to the log file with {@link com.devexperts.logging.Logging#error} and
 * all functional methods skip incoming data and call {@link #handleUnknownMessage}.
 */
public class MessageConsumerAdapter implements MessageConsumer, MessageConstants, SymbolCodec.Resolver {

    private static final Logging log = Logging.getLogging(MessageConsumerAdapter.class);

    /**
     * Returns symbol used for specified characters or <code>null</code> if not found.
     */
    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return null;
    }

    // ========== Error Handling ==========

    @Override
    public void handleCorruptedStream() {
        log.error("Corrupted QTP byte stream!!!");
    }

    @Override
    public void handleCorruptedMessage(int messageTypeId) {
        log.error("Corrupted QTP message " + messageTypeIdToString(messageTypeId) + "!!!");
    }

    @Override
    public void handleUnknownMessage(int messageTypeId) {
        log.error("Unknown QTP message " + messageTypeIdToString(messageTypeId) + "!!!");
    }

    private static String messageTypeIdToString(int messageTypeId) {
        MessageType messageType = MessageType.findById(messageTypeId);
        return "#" + messageTypeId + (messageType == null ? "" : ":" + messageType);
    }

    // ========== Incoming Message Processing ==========

    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {}

    @Override
    public void processHeartbeat(HeartbeatPayload heartbeatPayload) {}

    public void processTimeProgressReport(long timeMillis) {}

    /**
     * This method calls either {@link #processData(DataIterator, MessageType)} or
     * {@link #processSubscription(SubscriptionIterator, MessageType)} depending on message.
     * When message is {@link MessageType#RAW_DATA} and this class does not implement
     * {@link RawDataConsumer}, then {@link #processData(DataIterator, MessageType)}
     * for ticket, stream, and history message types is invoked.
     */
    public final void processRecordSource(RecordSource source, MessageType message) {
        if (message.isData()) {
            if (message == MessageType.RAW_DATA && !(this instanceof RawDataConsumer)) {
                expandRawData(source);
            } else {
                processData(source, message);
            }
        } else if (message.isSubscription())
            processSubscription(source, message);
        else
            handleUnknownMessage(message.getId());
    }

    private void expandRawData(RecordSource source) {
        long position = source.getPosition();
        processData(source, MessageType.TICKER_DATA);
        source.setPosition(position);
        processData(source, MessageType.STREAM_DATA);
        source.setPosition(position);
        processData(source, MessageType.HISTORY_DATA);
    }

    // Is overriden in subclasses
    protected void processData(DataIterator iterator, MessageType message) {
        DataConsumer.VOID.processData(iterator); // handleUnknownMessage has a right to ignore the problem
        handleUnknownMessage(message.getId());
    }

    // Is overriden in subclasses
    protected void processSubscription(SubscriptionIterator iterator, MessageType message) {
        SubscriptionConsumer.VOID.processSubscription(iterator);  // handleUnknownMessage has a right to ignore the problem
        handleUnknownMessage(message.getId());
    }

    /**
     * This implementation calls {@code processData(iterator, MessageType.TICKER_DATA)}.
     */
    @Override
    public void processTickerData(DataIterator iterator) {
        processData(iterator, MessageType.TICKER_DATA);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.TICKER_ADD_SUBSCRIPTION)}.
     */
    @Override
    public void processTickerAddSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.TICKER_ADD_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.TICKER_REMOVE_SUBSCRIPTION)}.
     */
    @Override
    public void processTickerRemoveSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.TICKER_REMOVE_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code processData(iterator, MessageType.STREAM_DATA)}.
     */
    @Override
    public void processStreamData(DataIterator iterator) {
        processData(iterator, MessageType.STREAM_DATA);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.STREAM_ADD_SUBSCRIPTION)}.
     */
    @Override
    public void processStreamAddSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.STREAM_ADD_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.STREAM_REMOVE_SUBSCRIPTION)}.
     */
    @Override
    public void processStreamRemoveSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.STREAM_REMOVE_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code processData(iterator, MessageType.HISTORY_DATA)}.
     */
    @Override
    public void processHistoryData(DataIterator iterator) {
        processData(iterator, MessageType.HISTORY_DATA);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.HISTORY_ADD_SUBSCRIPTION)}.
     */
    @Override
    public void processHistoryAddSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.HISTORY_ADD_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code processSubscription(iterator, MessageType.HISTORY_REMOVE_SUBSCRIPTION)}.
     */
    @Override
    public void processHistoryRemoveSubscription(SubscriptionIterator iterator) {
        processSubscription(iterator, MessageType.HISTORY_REMOVE_SUBSCRIPTION);
    }

    /**
     * This implementation calls {@code handleUnknownMessage(message_type)}.
     */
    @Override
    public final void processOtherMessage(int messageTypeId, byte[] bytes, int ofs, int len) {
        processOtherMessage(messageTypeId, new ByteArrayInput(bytes, ofs, len), len);
    }

    public void processOtherMessage(int messageTypeId, BufferedInput data, int len) {
        handleUnknownMessage(messageTypeId);
    }
}
