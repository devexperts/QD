/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.dxlink.websocket.application.DxLinkJsonMessageFactory.FeedSetupJsonMessage;
import com.devexperts.qd.dxlink.websocket.application.DxLinkJsonMessageFactory.FeedSubscriptionJsonMessage;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QTPConstants;
import com.devexperts.qd.qtp.RuntimeQTPException;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.devexperts.qd.dxlink.websocket.transport.TokenDxLinkLoginHandlerFactory.DXLINK_AUTHORIZATION_SCHEME;

class DxLinkWebSocketQTPComposer extends AbstractQTPComposer {
    private static final String PROTOCOL_VERSION =
        SystemProperties.getProperty("com.devexperts.qd.dxlink.protocolVersion", "0.1");
    private static final String SERVICE_NAME =
        SystemProperties.getProperty("com.devexperts.qd.dxlink.feedService.name", "FEED");
    private static final String ACCEPT_DATA_FORMAT =
        SystemProperties.getProperty("com.devexperts.qd.dxlink.feedService.acceptDataFormat", "COMPACT");
    private static final int MAIN_CHANNEL = 0;
    private static final int TICKER_CHANNEL_NUMBER = 1;
    private static final int STREAM_CHANNEL_NUMBER = 3;
    private static final int HISTORY_CHANNEL_NUMBER = 5;

    private final DxLinkJsonMessageFactory messageFactory;
    private final Map<QDContract, ChannelSubscriptionProcessor> processors = new EnumMap<>(QDContract.class);
    private final HeartbeatProcessor heartbeatProcessor;
    private final DxLinkWebSocketApplicationConnectionFactory factory;
    private final MessageAdapter adapter;
    private final Delegates delegates;
    private ChannelSubscriptionProcessor currentSubscriptionProcessor;
    private boolean initialSetupSent;
    private long lastSentHeartbeatTimeout = -1L;
    private long lastSentDisconnectTimeout = -1L;
    private final List<ByteBuf> messages = new ArrayList<>();
    private int payloadSize = 0;

    public DxLinkWebSocketQTPComposer(DataScheme scheme, Delegates delegates,
        DxLinkJsonMessageFactory messageFactory, HeartbeatProcessor heartbeatProcessor,
        DxLinkWebSocketApplicationConnectionFactory factory, MessageAdapter adapter)
    {
        super(scheme, true);
        this.messageFactory = messageFactory;
        this.heartbeatProcessor = heartbeatProcessor;
        this.factory = factory;
        this.adapter = adapter;
        this.delegates = delegates;
        for (QDContract contract : QDContract.values()) {
            processors.put(contract, new ChannelSubscriptionProcessor(contract));
        }
    }

    boolean composeMessage(MessageProvider provider) {
        try {
            boolean result = provider.retrieveMessages(this);
            stats.updateIOWriteBytes(payloadSize);
            return result;
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
            return false; // it is not actually reached, because abortMessageAndRethrow never returns normally
        }
    }

    void composeKeepalive() {
        try {
            ByteBuf keepalive = messageFactory.createKeepalive(MAIN_CHANNEL);
            messages.add(keepalive);
            stats.updateIOWriteBytes(keepalive.readableBytes());
        } catch (Throwable t) {
            abortMessageAndRethrow(t);
        }
    }

    @Override
    protected void abortMessageAndRethrow(Throwable t) {
        releaseAndClearMessages();
        if (currentSubscriptionProcessor != null) {
            currentSubscriptionProcessor.releaseHeldBuffers();
            currentSubscriptionProcessor = null;
        }
        super.abortMessageAndRethrow(t);
    }

    private void releaseAndClearMessages() {
        for (ByteBuf b : messages) {
            b.release();
        }
        messages.clear();
        payloadSize = 0;
    }

    @Override
    protected int writeRecordHeader(DataRecord record, int cipher, String symbol, int eventFlags) {
        throw new IllegalStateException();
    }

    @Override
    protected void writeHistorySubscriptionTime(DataRecord record, long time) {
        throw new IllegalStateException();
    }

    @Override
    protected void writeMessageHeader(MessageType messageType) {
        if (messageType.isSubscription()) {
            currentSubscriptionProcessor = processors.get(messageType.getContract());
            currentSubscriptionProcessor.begin(messageType.isSubscriptionRemove());
        }
    }

    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        try {
            currentSubscriptionProcessor.add(record, cipher, symbol, time);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    @Override
    public void resetSession() {
        if (currentSubscriptionProcessor != null) {
            currentSubscriptionProcessor.releaseHeldBuffers();
            currentSubscriptionProcessor = null;
        }
        initialSetupSent = false;
        lastSentHeartbeatTimeout = -1L;
        lastSentDisconnectTimeout = -1L;
        releaseAndClearMessages();
        for (ChannelSubscriptionProcessor proc : processors.values()) {
            proc.channelIsOpened = false;
            proc.lastSentRequestedAggregationPeriod = null;
            proc.releaseHeldBuffers();
        }
        super.resetSession();
    }

    @Override
    protected void undoWriteMessageHeaderStateChange() {
        finishComposingMessage(null);
    }

    @Override
    protected void finishComposingMessage(BufferedOutput out) {
        if (currentSubscriptionProcessor != null) {
            try {
                currentSubscriptionProcessor.end();
            } catch (IOException e) {
                throw new RuntimeQTPException(e);
            }
            currentSubscriptionProcessor = null;
        }
    }

    @Override
    protected void writeDescribeProtocolMessage(BufferedOutput out, ProtocolDescriptor descriptor) throws IOException {
        String token = descriptor.getProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY);
        if (token != null) {
            // see AuthToken#AuthToken(String, String)
            String clearToken = token.substring(DXLINK_AUTHORIZATION_SCHEME.length() + 1);
            ByteBuf auth = messageFactory.createAuth(MAIN_CHANNEL, clearToken);
            payloadSize += auth.readableBytes();
            messages.add(auth);
            return;
        }
        long heartbeat = heartbeatProcessor.getHeartbeatTimeout();
        long disconnect = heartbeatProcessor.getDisconnectTimeout();
        if (!initialSetupSent || heartbeat != lastSentHeartbeatTimeout || disconnect != lastSentDisconnectTimeout) {
            ByteBuf setup = messageFactory.createSetup(MAIN_CHANNEL, PROTOCOL_VERSION,
                heartbeat, disconnect, factory.getAgentInfo());
            payloadSize += setup.readableBytes();
            messages.add(setup);
            initialSetupSent = true;
            lastSentHeartbeatTimeout = heartbeat;
            lastSentDisconnectTimeout = disconnect;
        }
        composeRequestedAggregationPeriod();
    }

    private void composeRequestedAggregationPeriod() throws IOException {
        TimePeriod period = getEffectiveRequestedAggregationPeriod();
        for (ChannelSubscriptionProcessor proc : processors.values()) {
            if (proc.channelIsOpened &&
                !Objects.equals(period, proc.lastSentRequestedAggregationPeriod))
            {
                ByteBuf feedSetup = messageFactory.createFeedSetup(proc.channel,
                    period != null ? period.getTime() : null);
                payloadSize += feedSetup.readableBytes();
                messages.add(feedSetup);
                proc.lastSentRequestedAggregationPeriod = period;
            }
        }
    }

    private TimePeriod getEffectiveRequestedAggregationPeriod() {
        String requested = adapter.getRequestedAggregationPeriod();
        if (requested == null || "undefined".equalsIgnoreCase(requested))
            return null;
        return TimePeriod.valueOf(requested);
    }

    @Override
    public boolean hasCapacity() {
        int completedSize = payloadSize +
            (currentSubscriptionProcessor != null ? currentSubscriptionProcessor.payloadSize() : 0);
        return completedSize < QTPConstants.COMPOSER_THRESHOLD;
    }

    @Override
    protected long getMessagePayloadSize() {
        throw new UnsupportedOperationException(
            "getMessagePayloadSize() is not supported, use 'hasCapacity'");
    }

    public List<ByteBuf> retrieveMessages() {
        assert currentSubscriptionProcessor == null;
        List<ByteBuf> snapshot = new ArrayList<>(messages);
        messages.clear();
        payloadSize = 0;
        return snapshot;
    }

    @Override
    protected void writeIntField(DataIntField field, int value) {
        throw new UnsupportedOperationException(
            "Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeObjField(DataObjField field, Object value) {
        throw new UnsupportedOperationException(
            "Legacy field-by-field writing is not supported, use 'append'");
    }

    @Override
    protected void writeField(DataField field, RecordCursor cursor) {
        throw new UnsupportedOperationException(
            "Legacy field-by-field writing is not supported, use 'append'");
    }

    private class ChannelSubscriptionProcessor {
        private final QDContract contract;
        private final int channel;
        private final Map<String, List<String>> fieldsByType;
        private final DxLinkSubscriptionFactory subscriptionFactory = new DxLinkSubscriptionFactory();
        private boolean isRemove;
        private boolean channelIsOpened = false;
        // Last aggregation period (ms) sent in a FEED_SETUP for this channel; null until first emission.
        // Used to skip redundant FEED_SETUPs on unrelated DESCRIBE_PROTOCOL triggers.
        private TimePeriod lastSentRequestedAggregationPeriod = null;
        // channelRequest is built in add() and held until end() transfers it to the outer messages
        // list. Abort/reset paths must release this buffer if it has not been transferred yet.
        private ByteBuf channelRequest;
        // FEED_SETUP and FEED_SUBSCRIPTION are streamed into a single buffer as records arrive
        // in add(); end() closes them and transfers ownership to the outer messages list.
        // Abort/reset paths call abort() on whichever is still open.
        private FeedSetupJsonMessage feedSetup;
        private FeedSubscriptionJsonMessage feedSubscription;
        // TimePeriod snapshotted at first field-bearing add() and written into the FEED_SETUP
        // prologue. Reused in end() to update lastSentRequestedAggregationPeriod so that snapshot
        // value matches the on-wire output even if the property changes mid-batch.
        private TimePeriod pendingRequestedAggregationPeriod = null;

        private ChannelSubscriptionProcessor(QDContract contract) {
            this.contract = contract;
            fieldsByType = delegates.fieldsByEventType();
            switch (contract) {
                case TICKER:
                    channel = TICKER_CHANNEL_NUMBER;
                    break;
                case STREAM:
                    channel = STREAM_CHANNEL_NUMBER;
                    break;
                case HISTORY:
                    channel = HISTORY_CHANNEL_NUMBER;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        public void begin(boolean isRemove) {
            clear();
            this.isRemove = isRemove;
        }

        private void clear() {
            // After a successful end(), buffers have already been handed off to the outer messages
            // list (channelRequest directly, feedSetup/feedSubscription via close()). Just drop
            // local references. Abort/reset paths use releaseHeldBuffers() instead.
            channelRequest = null;
            feedSetup = null;
            feedSubscription = null;
            pendingRequestedAggregationPeriod = null;
        }

        // Release any pooled buffers held only by this processor (i.e. not yet handed off to the
        // outer messages list). Called from abortMessageAndRethrow / resetSession so we never leak
        // a pooled buffer that was created in add() but never reached end().
        private void releaseHeldBuffers() {
            if (channelRequest != null) {
                channelRequest.release();
                channelRequest = null;
            }
            if (feedSetup != null) {
                feedSetup.abort();
                feedSetup = null;
            }
            if (feedSubscription != null) {
                feedSubscription.abort();
                feedSubscription = null;
            }
            pendingRequestedAggregationPeriod = null;
        }

        public void add(DataRecord record, int cipher, String symbol, long time) throws IOException {
            DxLinkSubscription subscription =
                subscriptionFactory.createSubscription(contract, record, cipher, symbol, time);
            if (subscription == null)
                return;
            if (!channelIsOpened) {
                channelRequest =
                    messageFactory.createChannelRequest(channel, SERVICE_NAME, this.contract.name());
                channelIsOpened = true;
            }
            if (!fieldsByType.isEmpty()) {
                List<String> fields = fieldsByType.remove(subscription.type);
                if (fields != null) {
                    List<String> acceptedFields = factory.getAcceptedEventFieldsByType(subscription.type);
                    if (!acceptedFields.isEmpty())
                        fields.removeIf(field -> !"eventSymbol".equals(field) && !acceptedFields.contains(field));
                    if (DxLinkJsonMessageParser.FULL.equals(ACCEPT_DATA_FORMAT))
                        fields.add(0, "eventType");
                    if (feedSetup == null) {
                        // Snapshot the requested aggregation period exactly once so the value
                        // written into the FEED_SETUP prologue is the same one we assign to
                        // lastSentRequestedAggregationPeriod in end().
                        pendingRequestedAggregationPeriod = getEffectiveRequestedAggregationPeriod();
                        Long ms = pendingRequestedAggregationPeriod != null ?
                            pendingRequestedAggregationPeriod.getTime() : null;
                        feedSetup = messageFactory.openFeedSetupWithFields(channel, ms, ACCEPT_DATA_FORMAT);
                    }
                    feedSetup.appendAcceptEventFields(subscription.type, fields);
                }
            }
            if (feedSubscription == null) {
                feedSubscription = messageFactory.openFeedSubscription(channel, isRemove);
            }
            feedSubscription.appendSubscription(subscription);
        }

        public void end() throws IOException {
            int batchSize = 0;
            if (channelRequest != null) {
                batchSize += channelRequest.readableBytes();
                messages.add(channelRequest);
                channelRequest = null;
            }
            if (feedSetup != null) {
                ByteBuf buf = feedSetup.close();
                batchSize += buf.readableBytes();
                messages.add(buf);
                feedSetup = null;
                lastSentRequestedAggregationPeriod = pendingRequestedAggregationPeriod;
            }
            if (feedSubscription != null) {
                ByteBuf buf = feedSubscription.close();
                batchSize += buf.readableBytes();
                messages.add(buf);
                feedSubscription = null;
            }
            payloadSize += batchSize;
            clear();
        }

        // Trailing tokens ('}' / ']}') are not yet written into the buffer — payloadSize() under-counts
        // by <= 2 bytes per held JsonMessage. Harmless vs COMPOSER_THRESHOLD = 8 KB.
        public int payloadSize() {
            int size = 0;
            if (channelRequest != null)
                size += channelRequest.readableBytes();
            if (feedSetup != null)
                size += feedSetup.readableBytes();
            if (feedSubscription != null)
                size += feedSubscription.readableBytes();
            return size;
        }
    }
}
