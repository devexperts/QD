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
import com.devexperts.qd.ng.RecordBuffer;
import com.dxfeed.event.EventType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ChannelEventsParser implements DxLinkClientReceiver.EventsParser {

    private static final Logging log = Logging.getLogging(ChannelEventsParser.class);

    private final QDContract contract;
    private final Delegates delegates;
    private final DataParser compactDataParser;
    private final DataParser fullDataParser;
    private DataParser dataParser;
    private JsonParser json;

    ChannelEventsParser(QDContract contract, Delegates delegates) {
        this.contract = contract;
        this.delegates = delegates;
        this.compactDataParser = new CompactDataParser();
        this.fullDataParser = new FullDataParser();
    }

    void updateConfig(String dataFormat, Map<String, List<String>> eventFields) {
        if (DxLinkJsonMessageParser.COMPACT.equals(dataFormat)) {
            dataParser = compactDataParser;
        } else if (DxLinkJsonMessageParser.FULL.equals(dataFormat)) {
            dataParser = fullDataParser;
        } else {
            throw new IllegalArgumentException("Unsupported dataFormat: '" + dataFormat + "'");
        }
        dataParser.updateConfig(eventFields);
    }

    DxLinkClientReceiver.EventsParser init(JsonParser json) {
        this.json = json;
        return this;
    }

    @Override
    public void parse(RecordBuffer recordBuffer) throws IOException {
        dataParser.parse(json, contract, recordBuffer);
    }

    @Override
    public QDContract getContract() { return contract; }

    private abstract class DataParser {
        void parse(JsonParser json, QDContract qdContract, RecordBuffer recordBuffer) throws IOException {
            extractEvents(json, event -> delegates.putEventToRecordBuffer(event, qdContract, recordBuffer));
        }

        protected abstract void extractEvents(JsonParser json, Consumer<EventType<?>> recordBuffer) throws IOException;

        abstract void updateConfig(Map<String, List<String>> eventFieldsByEventType);
    }

    private static interface Buffer {
        public void readEvent(Consumer<EventType<?>> recordBuffer) throws IOException;
    }

    private class CompactDataParser extends DataParser {
        private final Map<String, Buffer> buffers = new HashMap<>();

        protected void extractEvents(JsonParser json, Consumer<EventType<?>> recordBuffer) throws IOException {
            skipToken(json, JsonToken.START_ARRAY);
            while (json.currentToken() != JsonToken.END_ARRAY) {
                expectedToken(json, JsonToken.VALUE_STRING);
                String eventType = json.getValueAsString();
                json.nextToken();
                skipToken(json, JsonToken.START_ARRAY);
                buffers.computeIfAbsent(eventType, EmptyBuffer::new).readEvent(recordBuffer);
                skipToken(json, JsonToken.END_ARRAY);
            }
            skipToken(json, JsonToken.END_ARRAY);
        }

        @Override
        void updateConfig(Map<String, List<String>> eventFields) {
            eventFields.forEach((eventType, fieldNames) -> buffers.put(eventType,
                new BufferImpl(delegates.getEventBuilder(eventType, fieldNames))));
        }

        private class BufferImpl implements Buffer {
            private final EventType<?> event;
            private final LinkedHashMap<String, Delegates.Setter> setters;

            private BufferImpl(Delegates.EventBuilder eventBuilder) {
                this.event = eventBuilder.factory.get();
                this.setters = eventBuilder.setters;
            }

            public void readEvent(Consumer<EventType<?>> recordBuffer) throws IOException {
                while (json.currentToken() != JsonToken.END_ARRAY) {
                    expectedValue(json);
                    for (Delegates.Setter setter : setters.values()) {
                        setter.setValue(event, json); // TODO do not check for the expected token
                        json.nextToken();
                    }
                    if (event != null)
                        recordBuffer.accept(event);
                }
            }
        }

        private class EmptyBuffer implements Buffer {
            private final String eventType;

            private EmptyBuffer(String eventType) {
                this.eventType = eventType;
            }

            public void readEvent(Consumer<EventType<?>> recordBuffer) throws IOException {
                log.warn(String.format("Unknown event type: '%s'.", eventType));
                while (json.currentToken() != JsonToken.END_ARRAY) {
                    expectedValue(json);
                    json.nextToken();
                }
            }
        }
    }

    private class FullDataParser extends DataParser {
        private final Map<String, Buffer> buffers = new HashMap<>();

        @Override
        protected void extractEvents(JsonParser json, Consumer<EventType<?>> recordBuffer) throws IOException {
            skipToken(json, JsonToken.START_ARRAY);
            while (json.currentToken() != JsonToken.END_ARRAY) {
                skipToken(json, JsonToken.START_OBJECT);
                // We have agreed that in full-json, the first field will always be the eventType.
                json.nextValue();
                expectedValue(json, "eventType", JsonToken.VALUE_STRING);
                String eventType = json.getValueAsString();
                buffers.computeIfAbsent(eventType, EmptyBuffer::new).readEvent(recordBuffer);
                skipToken(json, JsonToken.END_OBJECT);
            }
            skipToken(json, JsonToken.END_ARRAY);
        }

        @Override
        void updateConfig(Map<String, List<String>> eventFields) {
            eventFields.forEach((eventType, fields) ->
                buffers.put(eventType, new BufferImpl(eventType, delegates.getEventBuilder(eventType, fields))));
        }

        private class BufferImpl implements Buffer {
            private final String eventType;
            private final Supplier<EventType<?>> factory;
            private final LinkedHashMap<String, Delegates.Setter> setters;

            private BufferImpl(String eventType, Delegates.EventBuilder eventBuilder) {
                this.eventType = eventType;
                this.factory = eventBuilder.factory;
                this.setters = eventBuilder.setters;
            }

            public void readEvent(Consumer<EventType<?>> recordBuffer) throws IOException {
                EventType<?> event = factory.get();
                json.nextValue();
                while (json.currentToken() != JsonToken.END_OBJECT) {
                    expectedValue(json);
                    String fieldName = json.currentName();
                    Delegates.Setter setter = setters.get(fieldName);
                    if (setter != null) {
                        setter.setValue(event, json);
                    } else {
                        log.warn(String.format("Unknown field: '%s' for event type '%s'.", fieldName, eventType));
                    }
                    json.nextValue();
                }
                if (event != null)
                    recordBuffer.accept(event);
            }
        }

        private class EmptyBuffer implements Buffer {
            private final String eventType;

            private EmptyBuffer(String eventType) {
                this.eventType = eventType;
            }

            public void readEvent(Consumer<EventType<?>> recordBuffer) throws IOException {
                log.warn(String.format("Unknown event type: '%s'.", eventType));
                while (json.currentToken() != JsonToken.END_OBJECT) {
                    expectedValue(json);
                    json.nextValue();
                }
            }
        }
    }

    private static void skipToken(JsonParser json, JsonToken token) throws IOException {
        if (json.hasToken(token)) {
            json.nextToken();
        } else {
            throw new IllegalStateException(
                String.format("Expected token '%s', but was '%s'.", token, json.currentToken()));
        }
    }

    private static void expectedToken(JsonParser json, JsonToken token) {
        if (!json.hasToken(token)) {
            throw new IllegalStateException(
                String.format("Expected token '%s', but was '%s'.", token, json.currentToken()));
        }
    }

    private static void expectedValue(JsonParser json, String fieldName, JsonToken token) throws IOException {
        if (!fieldName.equals(json.currentName())) {
            throw new IllegalStateException(
                String.format("Expected field name '%s', but was '%s'.", fieldName, json.currentName()));
        }
        if (!json.hasToken(token)) {
            throw new IllegalStateException(
                String.format("Expected token '%s', but was '%s'.", token, json.currentToken()));
        }
    }

    private static void expectedValue(JsonParser json) {
        if (json.currentToken() == null)
            throw new IllegalStateException("Unexpected end of json.");
        if (!json.currentToken().isScalarValue())
            throw new IllegalStateException(
                String.format("Expected scalar value token, but was '%s'.", json.currentToken()));
    }
}
