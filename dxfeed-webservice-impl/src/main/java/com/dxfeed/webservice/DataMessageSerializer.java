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
package com.dxfeed.webservice;

import com.dxfeed.event.EventType;
import com.dxfeed.webservice.comet.DataMessage;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

class DataMessageSerializer extends StdSerializer<DataMessage> {
    private static final String EVENT_SYMBOL = "eventSymbol";

    protected PropertySerializerMap serializers = PropertySerializerMap.emptyForProperties();

    DataMessageSerializer() {
        super(DataMessage.class);
    }

    @Override
    public void serialize(DataMessage value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException
    {
        Class<?> eventType = value.getEventType();
        EventBeanSerializer serializer = (EventBeanSerializer) findSerializer(eventType, provider, true);
        BeanPropertyWriter[] props = serializer.getProps();

        jgen.writeStartArray();
        if (value.isSendScheme())
            serializeScheme(eventType, jgen, props);
        else
            jgen.writeString(eventType.getSimpleName());
        jgen.writeStartArray();
        for (EventType<?> event : value.getEvents())
            for (BeanPropertyWriter prop : props)
                if (EVENT_SYMBOL.equals(prop.getName()))
                    jgen.writeString(value.getSymbolMap().get(event.getEventSymbol()));
                else {
                    Object propValue;
                    try {
                        propValue = prop.get(event); // access property via reflection
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    serializeValue(propValue, jgen, provider);
                }
        jgen.writeEndArray();
        jgen.writeEndArray();
    }

    private void serializeScheme(Class<?> eventType, JsonGenerator jgen, BeanPropertyWriter[] props)
        throws IOException
    {
        jgen.writeStartArray();
        jgen.writeString(eventType.getSimpleName());
        jgen.writeStartArray();
        for (BeanPropertyWriter prop : props)
            jgen.writeString(prop.getName());
        jgen.writeEndArray();
        jgen.writeEndArray();
    }

    private void serializeValue(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (value == null) {
            provider.defaultSerializeNull(jgen);
            return;
        }
        findSerializer(value.getClass(), provider, false).serialize(value, jgen, provider);
    }

    private JsonSerializer<Object> findSerializer(Class<?> type, SerializerProvider provider, boolean eventBean)
        throws JsonMappingException
    {
        JsonSerializer<Object> serializer = serializers.serializerFor(type);
        if (serializer != null)
            return serializer;
        serializer = provider.findValueSerializer(type, null);
        if (eventBean)
            serializer = new EventBeanSerializer((BeanSerializer) serializer);
        serializers = serializers.newWith(type, serializer);
        return serializer;
    }
}
