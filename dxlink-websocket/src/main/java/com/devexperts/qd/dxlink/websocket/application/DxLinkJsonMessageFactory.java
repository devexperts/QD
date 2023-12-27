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

import com.devexperts.io.ByteArrayOutput;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class DxLinkJsonMessageFactory {
    public static final String FIELD_NAME_TYPE = "type";
    public static final String FIELD_NAME_CHANNEL = "channel";
    private final JsonGenerator generator;
    private final ByteArrayOutput buffer;

    public DxLinkJsonMessageFactory() {
        try {
            this.buffer = new ByteArrayOutput();
            this.generator = JsonFactory.builder().build().createGenerator((DataOutput) buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ByteBuf createSetup(int channel, String version, Long keepaliveTimeout, Long acceptKeepaliveTimeout,
        Map<String, String> agent) throws IOException
    {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "SETUP");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        generator.writeStringField("version", version);
        if (keepaliveTimeout != null)
            generator.writeNumberField("keepaliveTimeout", keepaliveTimeout / 1000.0);
        if (acceptKeepaliveTimeout != null)
            generator.writeNumberField("acceptKeepaliveTimeout", acceptKeepaliveTimeout / 1000.0);
        if (agent != null && !agent.isEmpty()) {
            generator.writeObjectFieldStart("agent");
            for (Entry<String, String> entry : agent.entrySet()) {
                generator.writeStringField(entry.getKey(), entry.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    ByteBuf createAuth(int channel, String token) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "AUTH");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        generator.writeStringField("token", token);
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    ByteBuf createKeepalive(int channel) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "KEEPALIVE");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    ByteBuf createChannelRequest(int channel, String service, String contract) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "CHANNEL_REQUEST");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        generator.writeStringField("service", service);
        generator.writeObjectFieldStart("parameters");
        generator.writeStringField("contract", contract);
        generator.writeEndObject();
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    ByteBuf createFeedSetup(int channel, Long acceptAggregationPeriod, String acceptDataFormat,
        Map<String, Collection<String>> acceptEventFields) throws IOException
    {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "FEED_SETUP");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        if (acceptAggregationPeriod != null)
            generator.writeNumberField("acceptAggregationPeriod", acceptAggregationPeriod / 1000.0);
        if (acceptDataFormat != null)
            generator.writeStringField("acceptDataFormat", acceptDataFormat);
        generator.writeFieldName("acceptEventFields");
        generator.writeStartObject();
        for (Entry<String, Collection<String>> eventType : acceptEventFields.entrySet()) {
            generator.writeArrayFieldStart(eventType.getKey());
            for (String s : eventType.getValue()) {
                generator.writeString(s);
            }
            generator.writeEndArray();
        }
        generator.writeEndObject();
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    ByteBuf createFeedSubscription(int channel, List<Subscription> add, List<Subscription> remove, Boolean reset)
        throws IOException
    {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME_TYPE, "FEED_SUBSCRIPTION");
        generator.writeNumberField(FIELD_NAME_CHANNEL, channel);
        writeSubscriptions("add", add);
        writeSubscriptions("remove", remove);
        if (reset != null)
            generator.writeBooleanField("reset", reset);
        generator.writeEndObject();
        return getStringFromBufferAndClear();
    }

    private void writeSubscriptions(String nameField, List<Subscription> add) throws IOException {
        generator.writeArrayFieldStart(nameField);
        for (Subscription subscription : add) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_NAME_TYPE, subscription.type);
            generator.writeStringField("symbol", subscription.symbol);
            if (subscription.source != null) {
                generator.writeStringField("source", subscription.source);
            } else if (subscription.fromTime != null) {
                generator.writeNumberField("fromTime", subscription.fromTime);
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private ByteBuf getStringFromBufferAndClear() throws IOException {
        generator.flush();
        ByteBuf byteBuf = Unpooled.copiedBuffer(buffer.getBuffer(), 0, buffer.getPosition());
        buffer.clear();
        return byteBuf;
    }
}
