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

import com.devexperts.logging.Logging;
import com.devexperts.qd.QDContract;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DxLinkJsonMessageParser {
    public static final String COMPACT = "COMPACT";
    public static final String FULL = "FULL";

    private static final long WARN_TIMEOUT_NANOS = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.qtp.socket.readerWarnTimeout", "15s")
    ).getNanos();
    private static final Logging log = Logging.getLogging(DxLinkJsonMessageParser.class);

    private final JsonFactory factory = JsonFactory.builder().build();
    private final Map<String, MessageParsingStrategy> strategies = new HashMap<>();
    private final Map<Integer, ChannelEventsParser> channelParsers = new HashMap<>();
    private final DxLinkClientReceiver receiver;
    private final Delegates delegates;

    DxLinkJsonMessageParser(DxLinkClientReceiver receiver, Delegates delegates) {
        this.receiver = receiver;
        this.delegates = delegates;
        strategies.put("SETUP", this::parseSetup);
        strategies.put("ERROR", this::parseError);
        strategies.put("AUTH_STATE", this::parseAuthState);
        strategies.put("KEEPALIVE", this::parseKeepalive);
        strategies.put("CHANNEL_OPENED", this::parseChannelOpened);
        strategies.put("FEED_CONFIG", this::parseFeedConfig);
        strategies.put("FEED_DATA", this::parseFeedData);
    }

    private static Map<String, List<String>> parseEventFields(JsonParser parser) throws IOException {
        Map<String, List<String>> eventFields = new HashMap<>();
        if (parser.nextToken() != JsonToken.START_OBJECT)
            throw new IllegalStateException("The Json object must begin with '{'.");
        do {
            JsonToken jsonToken = parser.nextValue();
            if (jsonToken == null) {
                throw new IllegalStateException("Unexpected end of json.");
            } else if (jsonToken == JsonToken.START_ARRAY) {
                String eventType = parser.getCurrentName();
                List<String> fields = new ArrayList<>();
                eventFields.put(eventType, fields);
                do {
                    jsonToken = parser.nextValue();
                    if (jsonToken == null) {
                        throw new IllegalStateException("Unexpected end of json.");
                    } else if (jsonToken == JsonToken.END_ARRAY) {
                        break;
                    } else if (jsonToken == JsonToken.VALUE_STRING) {
                        fields.add(parser.getValueAsString());
                    } else {
                        throw new IllegalStateException(String.format("Unexpected token: '%s'.", jsonToken.asString()));
                    }
                } while (true);
            } else if (jsonToken == JsonToken.END_OBJECT) {
                return eventFields;
            } else {
                throw new IllegalStateException(String.format("Unexpected token: '%s'.", jsonToken.asString()));
            }
        } while (true);
    }

    void read(String message) throws IOException {
        long timeNanos = System.nanoTime();
        try (JsonParser jsonParser = factory.createParser(message)) {
            String type = null;
            int channel = -1;
            if (jsonParser.nextToken() != JsonToken.START_OBJECT)
                throw new IllegalStateException("The Json object must begin with '{'.");
            jsonParser.nextValue();
            if ("type".equals(jsonParser.currentName())) {
                type = jsonParser.getValueAsString();
            } else if ("channel".equals(jsonParser.currentName())) {
                channel = jsonParser.getValueAsInt();
            } else {
                throw new IllegalStateException("The first two fields must be 'type' and 'channel'.");
            }
            jsonParser.nextValue();
            if ("type".equals(jsonParser.currentName())) {
                type = jsonParser.getValueAsString();
            } else if ("channel".equals(jsonParser.currentName())) {
                channel = jsonParser.getValueAsInt();
            } else {
                throw new IllegalStateException("The first two fields must be 'type' and 'channel'.");
            }
            MessageParsingStrategy parsingStrategy = this.strategies.get(type);
            if (parsingStrategy == null) {
                log.warn("Unknown message type: '" + type + "'");
            } else {
                parsingStrategy.process(channel, jsonParser);
            }
        }
        long deltaTimeNanos = System.nanoTime() - timeNanos;
        if (deltaTimeNanos > WARN_TIMEOUT_NANOS)
            log.warn("processChunks took " + deltaTimeNanos + " ns");
    }

    void createChannelParser(int channel, String contract) {
        channelParsers.put(channel, new ChannelEventsParser(QDContract.valueOf(contract), delegates));
    }

    void updateConfigChannelParser(int channel, String dataFormat, Map<String, List<String>> eventFields) {
        channelParsers.get(channel).updateConfig(dataFormat, eventFields);
    }

    private void parseSetup(int channel, JsonParser parser) throws IOException {
        String version = null;
        Long keepaliveTimeout = null;
        Long acceptKeepaliveTimeout = null;
        while (!parser.isClosed()) {
            if (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if ("version".equals(fieldName)) {
                    parser.nextToken();
                    version = parser.getValueAsString();
                } else if ("keepaliveTimeout".equals(fieldName)) {
                    if (parser.nextToken() != JsonToken.VALUE_NULL)
                        keepaliveTimeout = (long) (parser.getValueAsDouble() * 1000.0);
                } else if ("acceptKeepaliveTimeout".equals(fieldName)) {
                    if (parser.nextToken() != JsonToken.VALUE_NULL)
                        acceptKeepaliveTimeout = (long) (parser.getValueAsDouble() * 1000.0);
                }
            }
        }
        receiver.receiveSetup(version, keepaliveTimeout, acceptKeepaliveTimeout);
    }

    private void parseError(int channel, JsonParser parser) throws IOException {
        String error = null;
        String message = null;
        while (!parser.isClosed()) {
            if (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if ("error".equals(fieldName)) {
                    parser.nextToken();
                    error = parser.getValueAsString();
                } else if ("message".equals(fieldName)) {
                    parser.nextToken();
                    message = parser.getValueAsString();
                }
            }
        }
        receiver.receiveError(channel, error, message);
    }

    private void parseAuthState(int channel, JsonParser parser) throws IOException {
        while (!parser.isClosed()) {
            if (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if ("state".equals(fieldName)) {
                    parser.nextToken();
                    String state = parser.getValueAsString();
                    receiver.receiveAuthState(channel, state);
                    return;
                }
            }
        }
    }

    private void parseKeepalive(int channel, JsonParser parser) {
        receiver.receiveKeepalive(channel);
    }

    private void parseChannelOpened(int channel, JsonParser parser) throws IOException {
        String service = null;
        String contract = null;
        while (!parser.isClosed()) {
            if (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if ("service".equals(fieldName)) {
                    parser.nextToken();
                    service = parser.getValueAsString();
                }
                if ("parameters".equals(fieldName)) {
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if (parser.currentToken() == JsonToken.FIELD_NAME) {
                            if ("contract".equals(parser.getCurrentName())) {
                                parser.nextToken();
                                contract = parser.getValueAsString();
                            }
                        }
                    }
                }
            }
        }
        receiver.receiveChannelOpened(channel, service, contract);
    }

    private void parseFeedConfig(int channel, JsonParser parser) throws IOException {
        Long aggregationPeriod = null;
        String dataFormat = null;
        Map<String, List<String>> eventFields = Collections.emptyMap();
        while (!parser.isClosed()) {
            if (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                if ("aggregationPeriod".equals(fieldName)) {
                    if (parser.nextToken() != JsonToken.VALUE_NULL)
                        aggregationPeriod = (long) (parser.getValueAsDouble() * 1000.0);
                } else if ("dataFormat".equals(fieldName)) {
                    parser.nextToken();
                    dataFormat = parser.getValueAsString();
                } else if ("eventFields".equals(fieldName)) {
                    eventFields = parseEventFields(parser);
                }
            }
        }
        receiver.receiveFeedConfig(channel, aggregationPeriod, dataFormat, eventFields);
    }

    private void parseFeedData(int channel, JsonParser parser) throws IOException {
        while (!parser.isClosed()) {
            if (JsonToken.FIELD_NAME == parser.nextToken()) {
                String fieldName = parser.getCurrentName();
                if ("data".equals(fieldName)) {
                    parser.nextToken();
                    receiver.receiveFeedData(channelParsers.get(channel).init(parser));
                    return;
                }
            }
        }
    }

    private static interface MessageParsingStrategy {
        void process(int channel, JsonParser message) throws IOException;
    }
}
