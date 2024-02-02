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

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.services.Services;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.api.impl.DXPublisherImpl;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFactory;
import com.dxfeed.api.impl.EventDelegateSet;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.OrderSource;
import com.fasterxml.jackson.core.JsonParser;
import com.googlecode.openbeans.IntrospectionException;
import com.googlecode.openbeans.Introspector;
import com.googlecode.openbeans.PropertyDescriptor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

final class Delegates {
    private static final Logging log = Logging.getLogging(Delegates.class);

    private static final Delegates.VoidSetter VOID_SETTER = new Delegates.VoidSetter();
    private final IndexedSet<Class<?>, EventDelegateSet<EventType<?>, EventDelegate<EventType<?>>>>
        delegateSetsByEventType =
            IndexedSet.create((IndexerFunction<Class<?>, EventDelegateSet<?, ?>>) EventDelegateSet::eventType);
    IndexedSet<String, EventBuilder> builderByEventType =
        IndexedSet.create((IndexerFunction<String, EventBuilder>) EventBuilder::eventType);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Delegates(DataScheme scheme) {
        for (EventDelegateFactory factory : Services.createServices(EventDelegateFactory.class, null)) {
            for (int i = 0; i < scheme.getRecordCount(); i++) {
                DataRecord record = scheme.getRecord(i);
                Collection<EventDelegate<?>> delegates = factory.createDelegates(record);
                if (delegates == null)
                    continue;
                delegates.forEach(delegate -> {
                    EventDelegateSet delegateSet = delegateSetsByEventType.getByKey(delegate.getEventType());
                    if (delegateSet == null)
                        delegateSetsByEventType.add(delegateSet = delegate.createDelegateSet());
                    delegateSet.add(delegate);
                });
            }
        }
        for (EventDelegateSet<EventType<?>, EventDelegate<EventType<?>>> delegateSet : delegateSetsByEventType) {
            delegateSet.completeConstruction();
            builderByEventType.add(new EventBuilder(delegateSet.eventType().getSimpleName(),
                delegateSet.getAllPubDelegates().get(0)::createEvent,
                createSetters(delegateSet.getAllPubDelegates().get(0).createEvent())));
        }
    }


    /**
     * It is like {@link DXPublisherImpl#publishEvents}.
     *
     * @param event event to publish.
     * @param qdContract qdContract to filter delegate
     * @param recordBuffer recordBuffer into which the event should be recorded
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void putEventToRecordBuffer(EventType<?> event, QDContract qdContract, RecordBuffer recordBuffer) {
        EventDelegateSet delegateSet = delegateSetsByEventType.getByKey(event.getClass());
        // Delegates search by symbol with region, source, exchange, etc.
        List<EventDelegate<EventType<?>>> delegates = delegateSet.getPubDelegatesByEvent(event);
        for (EventDelegate<EventType<?>> delegate : delegates) {
            if (delegate.getContract() == qdContract)
                delegate.putEvent(event, recordBuffer);
        }
    }

    EventBuilder getEventBuilder(String eventType, List<String> fields) {
        EventBuilder eventBuilder = builderByEventType.getByKey(eventType);
        if (eventBuilder == null) {
            log.warn(String.format("Unknown event type: '%s'.", eventType));
            LinkedHashMap<String, Delegates.Setter> setters = new LinkedHashMap<>();
            for (String field : fields) {
                setters.put(field, VOID_SETTER);
            }
            return new Delegates.EventBuilder(eventType, () -> null, setters);
        } else {
            LinkedHashMap<String, Setter> setters = new LinkedHashMap<>();
            for (String field : fields) {
                setters.put(field, eventBuilder.setters.getOrDefault(field, VOID_SETTER));
            }
            return new Delegates.EventBuilder(eventType, eventBuilder.factory, setters);
        }
    }

    Map<String, List<String>> fieldsByEventType() {
        Map<String, List<String>> fieldsByEventType = new HashMap<>();
        for (EventBuilder builder : builderByEventType) {
            fieldsByEventType.put(builder.eventType(), new ArrayList<>(builder.setters.keySet()));
        }
        return fieldsByEventType;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static LinkedHashMap<String, Setter> createSetters(EventType<?> event) {
        try {
            LinkedHashMap<String, Setter> setters = new LinkedHashMap<>();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(event.getClass()).getPropertyDescriptors()) {
                final Method readMethod = pd.getReadMethod();
                final Method writeMethod = pd.getWriteMethod();
                if (isTransientField(readMethod, writeMethod))
                    continue;
                final String name = extractFieldName(pd, readMethod);
                final Class<?> parameterType = writeMethod.getParameterTypes()[0];
                if (parameterType == double.class) {
                    setters.put(name, new DoubleSetter(writeMethod));
                } else if (parameterType == long.class) {
                    setters.put(name, new LongSetter(writeMethod));
                } else if (parameterType == int.class) {
                    setters.put(name, new IntegerSetter(writeMethod));
                } else if (parameterType == String.class) {
                    setters.put(name, new StringSetter(writeMethod));
                } else if (parameterType == char.class) {
                    setters.put(name, new CharSetter(writeMethod));
                } else if (parameterType == boolean.class) {
                    setters.put(name, new BooleanSetter(writeMethod));
                } else if (parameterType.isEnum()) {
                    setters.put(name, new EnumSetter(writeMethod, (Class<? extends Enum>) parameterType));
                } else if (OrderSource.class.isAssignableFrom(parameterType)) {
                    setters.put(name, new OrderSourceSetter(writeMethod));
                } else if ("eventSymbol".equals(name)) {
                    if (event.getClass().getSimpleName().contains("Candle")) {
                        setters.put(name, new CandleSymbolSetter(writeMethod));
                    } else {
                        setters.put(name, new StringSetter(writeMethod));
                    }
                } else if ("attachment".equals(name)) {
                    setters.put(name, new StringSetter(writeMethod));
                } else {
                    throw new IllegalStateException();
                }
            }
            return setters;
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isTransientField(Method readMethod, Method writeMethod) {
        return readMethod == null || readMethod.getAnnotation(XmlTransient.class) != null || writeMethod == null ||
            writeMethod.getAnnotation(XmlTransient.class) != null;
    }

    private static String extractFieldName(PropertyDescriptor propertyDescriptor, Method readMethod) {
        final XmlElement xmlElement = readMethod.getAnnotation(XmlElement.class);
        if (xmlElement != null && !xmlElement.name().equals("##default"))
            return xmlElement.name();
        return propertyDescriptor.getName();
    }

    abstract static class Setter {
        private final Method setter;

        private Setter(Method setter) { this.setter = setter; }

        // TODO Current implementation is based on reflection, it would be better to convert a MethodHandle
        //  to a functional interface implementation using the same feature, Java 8’s lambda expressions
        //  and method references use. It could also eliminate unnecessary boxing of primitives.
        public void setValue(EventType<?> event, JsonParser parser) throws IOException {
            try {
                this.setter.invoke(event, value(parser));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IOException(e);
            }
        }

        protected abstract Object value(JsonParser parser) throws IOException;
    }

    static final class VoidSetter extends Setter {
        VoidSetter() { super(null); }

        @Override
        public void setValue(EventType<?> event, JsonParser fieldIterator) {}

        @Override
        protected Object value(JsonParser parser) {
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class EnumSetter extends Setter {
        private final Class<? extends Enum> enumClazz;

        private EnumSetter(Method setter, Class<? extends Enum> enumClazz) {
            super(setter);
            this.enumClazz = enumClazz;
        }

        @Override
        protected Object value(JsonParser parser) throws IOException {
            return Enum.valueOf(this.enumClazz, parser.getValueAsString());
        }
    }

    private static final class CandleSymbolSetter extends Setter {
        private CandleSymbolSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException {
            return CandleSymbol.valueOf(parser.getValueAsString());
        }
    }

    private static final class OrderSourceSetter extends Setter {
        private OrderSourceSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException {
            return OrderSource.valueOf(parser.getValueAsString());
        }
    }

    private static final class StringSetter extends Setter {
        private StringSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException { return parser.getValueAsString(); }
    }

    private static final class BooleanSetter extends Setter {
        private BooleanSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException { return parser.getValueAsBoolean(); }
    }

    private static final class CharSetter extends Setter {
        private CharSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException {
            final String stringValue = parser.getValueAsString();
            return stringValue.isEmpty() ? '\0' : stringValue.charAt(0);
        }
    }

    private static final class IntegerSetter extends Setter {
        private IntegerSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException { return parser.getValueAsInt(); }
    }

    private static final class LongSetter extends Setter {
        private LongSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException { return parser.getValueAsLong(); }
    }

    private static final class DoubleSetter extends Setter {
        private DoubleSetter(Method setter) { super(setter); }

        @Override
        protected Object value(JsonParser parser) throws IOException { return parser.getValueAsDouble(); }
    }

    static class EventBuilder {
        final String eventType;
        final Supplier<EventType<?>> factory;
        final LinkedHashMap<String, Setter> setters;

        private EventBuilder(String eventType, Supplier<EventType<?>> factory, LinkedHashMap<String, Setter> setters) {
            this.eventType = eventType;
            this.factory = factory;
            this.setters = setters;
        }

        public String eventType() {
            return eventType;
        }
    }
}
