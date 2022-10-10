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
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.webservice.comet.DataMessage;
import com.dxfeed.webservice.rest.Events;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.util.BeanUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

public class DXFeedJson {
    private static final DataModule DATA_MODULE = new DataModule();
    private static final String[] EMPTY_SORT_ORDER = new String[0];
    private static final EventsIntrospector ANNOTATION_INTROSPECTOR = new EventsIntrospector();

    public static final ObjectMapper MAPPER = newMapper(false);
    public static final ObjectMapper MAPPER_INDENT = newMapper(true);

    private static final SerializerFactory SER_FACTORY = MAPPER.getSerializerFactory();
    private static final SerializerProvider SER_PROVIDER = ((DefaultSerializerProvider) MAPPER.getSerializerProvider()).
        createInstance(MAPPER.getSerializationConfig(), SER_FACTORY);

    public static void writeTo(Object result, OutputStream out, String indent) throws IOException {
        mapper(indent).writeValue(out, result);
    }

    public static Object readFrom(InputStream in, Class valueType) throws IOException {
        return MAPPER.readValue(in, valueType);
    }

    private static ObjectMapper mapper(String indent) {
        return indent != null ? MAPPER_INDENT : MAPPER;
    }

    public static List<String> getProperties(Class<?> typeClass) throws JsonMappingException {
        List<String> list = new ArrayList<>();
        for (BeanPropertyWriter property : getProps(typeClass))
            list.add(property.getName());
        return list;
    }

    private static BeanPropertyWriter[] getProps(Class<?> typeClass) throws JsonMappingException {
        return new EventBeanSerializer((BeanSerializer) SER_FACTORY.
            createSerializer(SER_PROVIDER, MAPPER.constructType(typeClass))).getProps();
    }

    private static ObjectMapper newMapper(boolean indent) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(DATA_MODULE);
        if (indent)
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        /*
         * Note: Event classes do not use Jackson annotations. They use JAXB annotations, but only
         * a subset of them is applicable to JSON format. For example, JSON format uses different
         * date-time mapping.
         */
        mapper.setAnnotationIntrospector(ANNOTATION_INTROSPECTOR);
        return mapper;
    }

    static class EventsIntrospector extends JacksonAnnotationIntrospector {
        private final Map<Class<?>, PropertiesMapper> names = new ConcurrentHashMap<>();
        private final Map<Class<?>, String[]> orders = new ConcurrentHashMap<>();

        // Add support to ignore fields using XmlTransient annotation
        @Override
        public boolean hasIgnoreMarker(AnnotatedMember m) {
            if (m.getAnnotation(XmlTransient.class) != null)
                return true;
            return super.hasIgnoreMarker(m);
        }

        // Add support for field ordering using XmlType annotation
        @Override
        public String[] findSerializationPropertyOrder(AnnotatedClass ac) {
            String[] result = orders.computeIfAbsent(ac.getAnnotated(), clazz -> {
                List<XmlType> types = new ArrayList<>(5);
                while (clazz != null) {
                    XmlType type = clazz.getAnnotation(XmlType.class);
                    if (type != null)
                        types.add(type);
                    clazz = clazz.getSuperclass();
                }
                Collections.reverse(types);
                String[] order = types.stream()
                    .flatMap(xmlType -> Stream.of(xmlType.propOrder()))
                    .map(name -> name.toUpperCase().equals(name) ? name.toLowerCase() : name)
                    .toArray(String[]::new);
                return (order.length > 0) ? order : EMPTY_SORT_ORDER;
            });
            return (result.length > 0) ? result : null;
        }

        // Add support to rename fields using XmlElement annotation
        @Override
        public Object findNamingStrategy(AnnotatedClass ac) {
            return names.computeIfAbsent(ac.getAnnotated(), clazz -> new PropertiesMapper(ac));
        }
    }

    static class PropertiesMapper extends PropertyNamingStrategy {
        private final Map<Method, String> map = new HashMap<>();

        PropertiesMapper(AnnotatedClass ac) {
            Set<String> removed = new HashSet<>();
            Map<String, String> renamed = new HashMap<>();
            // First phase: map points to default property name
            for (AnnotatedMethod am : ac.memberMethods()) {
                String propertyName = getDefaultPropertyName(am);
                map.put(am.getAnnotated(), propertyName);
                if (removed.contains(propertyName))
                    continue;
                if (am.getAnnotation(XmlTransient.class) != null) {
                    removed.add(propertyName);
                    continue;
                }
                XmlElement ele = am.getAnnotation(XmlElement.class);
                if (ele != null && !"##default".equals(ele.name())) {
                    String old = renamed.get(propertyName);
                    if (old != null && !old.equals(ele.name())) {
                        throw new IllegalArgumentException("Conflicting names for " + propertyName +
                            ": " + old + " with " + ele.name());
                    }
                    renamed.put(propertyName, ele.name());
                }
            }
            // Second phase: map points to resolved property name
            for (Map.Entry<Method, String> e : map.entrySet()) {
                String pName = e.getValue();
                e.setValue(removed.contains(pName) ? pName + "Removed123456" : renamed.getOrDefault(pName, pName));
            }
        }

        private String getDefaultPropertyName(AnnotatedMethod am) {
            String s = BeanUtil.okNameForMutator(am, "get", true);
            if (s == null)
                s = BeanUtil.okNameForMutator(am, "is", true);
            if (s == null)
                s = BeanUtil.okNameForMutator(am, "set", true);
            return s != null ? s : am.getName();
        }

        @Override
        public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return map.get(method.getAnnotated());
        }

        @Override
        public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
            return map.get(method.getAnnotated());
        }
    }

    static class DataModule extends SimpleModule {
        private DataModule() {
            addSerializer(Events.class, new EventsSerializer());
            addSerializer(DataMessage.class, new DataMessageSerializer());
            addSerializer(CandleSymbol.class, new ToStringSerializer());
            addSerializer(IndexedEventSource.class, new ToStringSerializer());
            addSerializer(char.class, new NullSafeCharSerializer());
            addSerializer(double.class, new DoubleSerializer());
        }
    }

    private static Map<String, Map<Object, List<EventType<?>>>> toEventsMap(List<EventType<?>> events) {
        Map<String, Map<Object, List<EventType<?>>>> typeMap = new TreeMap<>();
        for (EventType<?> event : events) {
            String name = event.getClass().getSimpleName();
            Map<Object, List<EventType<?>>> symbolMap = typeMap.get(name);
            if (symbolMap == null)
                typeMap.put(name, symbolMap = new HashMap<>());
            List<EventType<?>> list = symbolMap.get(event.getEventSymbol());
            if (list == null)
                symbolMap.put(event.getEventSymbol(), list = new ArrayList<>());
            list.add(event);
        }
        return typeMap;
    }

    static class EventsSerializer extends JsonSerializer<Events> {
        @Override
        public void serialize(Events events, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            Map<String, Map<Object, List<EventType<?>>>> typeMap = toEventsMap(events.getEvents());
            jgen.writeStartObject();
            jgen.writeObjectField("status", events.getStatus());
            for (Map.Entry<String, Map<Object, List<EventType<?>>>> typeEntry : typeMap.entrySet()) {
                jgen.writeObjectFieldStart(typeEntry.getKey());
                for (Map.Entry<Object, List<EventType<?>>> symbolEntry : typeEntry.getValue().entrySet()) {
                    Object symbolObject = symbolEntry.getKey();
                    String symbolString = events.getSymbolMap().get(symbolObject);
                    List<EventType<?>> list = symbolEntry.getValue();
                    EventType<?> event0 = list.get(0);
                    boolean array = list.size() > 1 || IndexedEvent.class.isInstance(event0);
                    if (array) {
                        jgen.writeArrayFieldStart(symbolString);
                        for (EventType<?> event : list)
                            jgen.writeObject(event);
                        jgen.writeEndArray();
                    } else {
                        jgen.writeObjectField(symbolString, event0);
                    }
                }
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    static class NullSafeCharSerializer extends JsonSerializer<Character> {
        @Override
        public void serialize(Character value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(value == '\0' ? "" : value.toString());
        }
    }

    static class DoubleSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            if (value.longValue() == value) {
                jgen.writeNumber(value.longValue());
            } else {
                jgen.writeNumber(value);
            }
        }
    }
}
