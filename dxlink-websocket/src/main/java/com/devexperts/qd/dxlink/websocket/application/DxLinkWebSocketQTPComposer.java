/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
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
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QTPConstants;
import com.devexperts.qd.qtp.RuntimeQTPException;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.SystemProperties;
import com.dxfeed.event.market.MarketEventSymbols;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Delegates delegates;
    private ChannelSubscriptionProcessor currentSubscriptionProcessor;
    private List<ByteBuf> messages = new ArrayList<>();
    private int payloadSize = 0;

    public DxLinkWebSocketQTPComposer(DataScheme scheme, Delegates delegates,
        DxLinkJsonMessageFactory messageFactory, HeartbeatProcessor heartbeatProcessor,
        DxLinkWebSocketApplicationConnectionFactory factory)
    {
        super(scheme, true);
        this.messageFactory = messageFactory;
        this.heartbeatProcessor = heartbeatProcessor;
        this.factory = factory;
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
        messages.clear();
        payloadSize = 0;
        super.abortMessageAndRethrow(t);
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
            currentSubscriptionProcessor.add(record.getName(), record.getScheme().getCodec().decode(cipher, symbol),
                TimeSequenceUtil.getTimeMillisFromTimeSequence(time));
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
    }

    @Override
    public void resetSession() {
        currentSubscriptionProcessor = null;
        messages.clear();
        payloadSize = 0;
        super.resetSession();
    }

    @Override
    protected void undoWriteMessageHeaderStateChange() {
        finishComposingMessage(null);
    }

    @Override
    protected void finishComposingMessage(BufferedOutput out) {
        if (currentSubscriptionProcessor != null) {
            currentSubscriptionProcessor.end();
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
        } else {
            ByteBuf setup = messageFactory.createSetup(MAIN_CHANNEL, PROTOCOL_VERSION,
                heartbeatProcessor.getHeartbeatTimeout(), heartbeatProcessor.getDisconnectTimeout(),
                factory.getAgentInfo());
            payloadSize += setup.readableBytes();
            messages.add(setup);
        }
    }

    @Override
    public boolean hasCapacity() {
        int completedSize = payloadSize +
            (currentSubscriptionProcessor != null ? currentSubscriptionProcessor.payloadSize() : 0);
        return completedSize < QTPConstants.COMPOSER_THRESHOLD;
    }

    @Override
    protected long getMessagePayloadSize() {
        return payloadSize;
    }

    public List<ByteBuf> retrieveMessages() {
        List<ByteBuf> m = this.messages;
        messages = new ArrayList<>();
        payloadSize = 0;
        return m;
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
        private final List<Subscription> subscriptions = new ArrayList<>();
        private final Map<String, Collection<String>> fieldsByTypeToSend = new HashMap<>();
        private final SubscriptionFactory subscriptionFactory;
        private boolean isRemove;
        private boolean channelIsOpened = false;
        private ByteBuf channelRequest;
        private ByteBuf feedSetup;
        private ByteBuf feedSubscription;

        private ChannelSubscriptionProcessor(QDContract contract) {
            this.contract = contract;
            fieldsByType = delegates.fieldsByEventType();
            switch (contract) {
                case TICKER:
                    channel = TICKER_CHANNEL_NUMBER;
                    subscriptionFactory = new TickerAndStreamSubscriptionFactory();
                    break;
                case STREAM:
                    channel = STREAM_CHANNEL_NUMBER;
                    subscriptionFactory = new TickerAndStreamSubscriptionFactory();
                    break;
                case HISTORY:
                    channel = HISTORY_CHANNEL_NUMBER;
                    subscriptionFactory = new HistorySubscriptionFactory();
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        public void begin(boolean isRemove) {
            this.isRemove = isRemove;
            subscriptions.clear();
            fieldsByTypeToSend.clear();
            channelRequest = null;
            feedSetup = null;
            feedSubscription = null;
        }

        public void add(String eventType, String symbol, long fromTime) throws IOException {
            Subscription subscription = subscriptionFactory.createSubscription(eventType, symbol, fromTime);
            if (subscription != null) {
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
                        fieldsByTypeToSend.put(subscription.type, fields);
                        // TODO stop regenerating feedSetup for every new feedType
                        feedSetup = messageFactory.createFeedSetup(channel, factory.getAcceptAggregationPeriod().getTime(),
                            ACCEPT_DATA_FORMAT, fieldsByTypeToSend);
                    }
                }
                subscriptions.add(subscription);
                feedSubscription = messageFactory.createFeedSubscription(channel,
                    isRemove ? Collections.emptyList() : subscriptions,
                    isRemove ? subscriptions : Collections.emptyList(), false);
            }
        }

        public void end() {
            if (channelRequest != null)
                messages.add(channelRequest);
            if (feedSetup != null)
                messages.add(feedSetup);
            if (feedSubscription != null)
                messages.add(feedSubscription);
            payloadSize += payloadSize();
        }

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

    private static interface SubscriptionFactory {
        public Subscription createSubscription(String eventTypeWithSource, String symbol, long fromTime);
    }

    private static class TickerAndStreamSubscriptionFactory implements SubscriptionFactory {
        @Override
        public Subscription createSubscription(String eventTypeWithRegionOrSource, String symbol, long fromTime) {
            int indexSource = eventTypeWithRegionOrSource.indexOf('#');
            int indexRegion = eventTypeWithRegionOrSource.indexOf('&');
            final String eventType;
            final String eventSymbol;
            final String eventSource;
            if (indexSource > 0) {
                eventType = eventTypeWithRegionOrSource.substring(0, indexSource);
                eventSource = eventTypeWithRegionOrSource.substring(indexSource + 1);
                eventSymbol = symbol;
            } else if (indexRegion > 0) {
                eventType = eventTypeWithRegionOrSource.substring(0, indexRegion);
                if (symbol.equals("*")) {
                    return null;
                } else {
                    eventSymbol = MarketEventSymbols.changeExchangeCode(symbol,
                        MarketEventSymbols.getExchangeCode(eventTypeWithRegionOrSource));
                }
                eventSource = null;
            } else {
                eventType = eventTypeWithRegionOrSource;
                eventSymbol = symbol;
                eventSource = null;
            }
            return new Subscription(eventType, eventSymbol, eventSource, null);
        }
    }

    private static class HistorySubscriptionFactory implements SubscriptionFactory {
        @Override
        public Subscription createSubscription(String eventTypeWithSource, String symbol, long fromTime) {
            int indexSource = eventTypeWithSource.indexOf('#');
            final String eventType;
            final String source;
            if (indexSource > 0) {
                eventType = eventTypeWithSource.substring(0, indexSource);
                source = eventTypeWithSource.substring(indexSource + 1);
            } else if (eventTypeWithSource.contains("Order")) {
                eventType = eventTypeWithSource;
                source = "DEFAULT";
            } else {
                eventType = eventTypeWithSource;
                source = null;
            }
            return new Subscription(eventType, symbol, source, fromTime);
        }
    }
}
