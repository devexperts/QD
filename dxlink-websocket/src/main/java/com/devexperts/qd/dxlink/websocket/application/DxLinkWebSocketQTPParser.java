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
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.io.BufferedInput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.MessageConsumer;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.RuntimeQTPException;
import com.devexperts.qd.qtp.fieldreplacer.FieldReplacersCache;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.devexperts.qd.qtp.MessageType.HISTORY_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.HISTORY_REMOVE_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.STREAM_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.STREAM_REMOVE_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.TICKER_ADD_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.TICKER_REMOVE_SUBSCRIPTION;
import static com.devexperts.qd.qtp.MessageType.forData;

/**
 * Parses QTP messages in text format from byte stream. The input for this parser must be configured
 * with {@link #setInput(BufferedInput)} method immediately after construction.
 *
 * @see AbstractQTPParser
 */
class DxLinkWebSocketQTPParser extends AbstractQTPParser {
    private static final long MILLISECOND_IN_SECOND = 1000L;
    private static final Logging log = Logging.getLogging(DxLinkWebSocketQTPParser.class);

    private final DxLinkJsonMessageParser messageParser;
    private final HeartbeatProcessor heartbeatProcessor;
    private ProtocolDescriptor descriptor = ProtocolDescriptor.newPeerProtocolDescriptor(null);
    private MessageConsumer currentConsumer;

    DxLinkWebSocketQTPParser(DataScheme scheme, boolean supportsMixedSubscription, FieldReplacersCache fieldReplacer,
        HeartbeatProcessor heartbeatProcessor, Function<DxLinkClientReceiver, DxLinkJsonMessageParser> factory)
    {
        super(scheme);
        setMixedSubscription(supportsMixedSubscription);
        setFieldReplacers(fieldReplacer);
        this.heartbeatProcessor = heartbeatProcessor;
        this.messageParser = factory.apply(new DxLinkClientReceiverImpl());
    }

    @Override
    protected void parseImpl(BufferedInput in, MessageConsumer consumer) { throw new IllegalStateException(); }

    public void parse(String message, MessageConsumer consumer) {
        try {
            currentConsumer = consumer;
            messageParser.read(message);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Can't parse the message: '%s'", message), e);
        } finally {
            processPending(consumer);
            currentConsumer = null;
        }
    }

    private class DxLinkClientReceiverImpl implements DxLinkClientReceiver {
        @Override
        public void receiveError(int channel, String error, String message) { log.error(message); }

        @Override
        public void receiveKeepalive(int channel) { currentConsumer.processHeartbeat(null); }

        @Override
        public void receiveSetup(String version, Long keepaliveTimeoutInSec, Long acceptKeepaliveTimeoutInSec) {
            ProtocolDescriptor newDescriptor = ProtocolDescriptor.newPeerProtocolDescriptor(descriptor);
            newDescriptor.setProperty(ProtocolDescriptor.TYPE_PROPERTY, "dxlink");
            newDescriptor.setProperty(ProtocolDescriptor.VERSION_PROPERTY, version);
            if (keepaliveTimeoutInSec != null) {
                // terminate the connection if there are no messages from the server for more than the keepaliveTimeout
                newDescriptor.setProperty("keepaliveTimeout", Long.toString(keepaliveTimeoutInSec));
                heartbeatProcessor.receiveUpdateHeartbeatTimeout(keepaliveTimeoutInSec * MILLISECOND_IN_SECOND);
            }
            if (acceptKeepaliveTimeoutInSec != null) {
                // the server will terminate the connection if we do not send any message
                // within the acceptKeepaliveTimeout
                newDescriptor.setProperty("acceptKeepaliveTimeout", Long.toString(acceptKeepaliveTimeoutInSec));
                heartbeatProcessor.receiveUpdateHeartbeatPeriod(acceptKeepaliveTimeoutInSec * MILLISECOND_IN_SECOND);
            }
            descriptor = newDescriptor;
            currentConsumer.processDescribeProtocol(newDescriptor, true);
        }

        @Override
        public void receiveAuthState(int channel, String state) {
            if ("AUTHORIZED".equals(state)) {
                ProtocolDescriptor newDescriptor = ProtocolDescriptor.newPeerProtocolDescriptor(descriptor);
                newDescriptor.setProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY, "");
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(TICKER_ADD_SUBSCRIPTION));
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(TICKER_REMOVE_SUBSCRIPTION));
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(STREAM_ADD_SUBSCRIPTION));
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(STREAM_REMOVE_SUBSCRIPTION));
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(HISTORY_ADD_SUBSCRIPTION));
                newDescriptor.addReceive(newDescriptor.newMessageDescriptor(HISTORY_REMOVE_SUBSCRIPTION));
                descriptor = newDescriptor;
                currentConsumer.processDescribeProtocol(newDescriptor, true);
            } else {
                ProtocolDescriptor newDescriptor = ProtocolDescriptor.newPeerProtocolDescriptor(descriptor);
                newDescriptor.setProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY, state);
                descriptor = newDescriptor;
                currentConsumer.processDescribeProtocol(newDescriptor, true);
            }
        }

        @Override
        public void receiveChannelOpened(int channel, String service, String contract) {
            messageParser.createChannelParser(channel, contract);
        }

        @Override
        public void receiveFeedConfig(int channel, long aggregationPeriod, String dataFormat,
            Map<String, List<String>> eventFields)
        {
            messageParser.updateConfigChannelParser(channel, dataFormat, eventFields);
        }

        @Override
        public void receiveFeedData(EventsParser parser) {
            try {
                parser.parse(nextRecordsMessage(currentConsumer, forData(parser.getContract())));
            } catch (IOException e) {
                throw new RuntimeQTPException(e);
            } finally {
                processPending(currentConsumer);
            }
        }
    }
}
