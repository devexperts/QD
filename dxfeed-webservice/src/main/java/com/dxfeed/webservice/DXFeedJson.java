/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.webservice;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import javax.xml.bind.annotation.XmlTransient;

import com.dxfeed.event.*;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.webservice.comet.DataMessage;
import com.dxfeed.webservice.rest.Events;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class DXFeedJson {
	private static final String EVENTS_FILTER_ID = "EVENTS-FILTER-ID";
	private static final EventsFilter EVENTS_FILTER = new EventsFilter();
	private static final DataModule DATA_MODULE = new DataModule();
	private static final EventsIntrospector ANNOTATION_INTROSPECTOR = new EventsIntrospector();
	private static final EventsFilterProvider FILTER_PROVIDER = new EventsFilterProvider();

	public static final ObjectMapper MAPPER = newMapper(false);
	public static final ObjectMapper MAPPER_INDENT = newMapper(true);

	private static final SerializerFactory SER_FACTORY = MAPPER.getSerializerFactory();
	private static final SerializerProvider SER_PROVIDER = ((DefaultSerializerProvider)MAPPER.getSerializerProvider()).
		createInstance(MAPPER.getSerializationConfig(), SER_FACTORY);

	public static void writeTo(Object result, OutputStream out, String indent) throws IOException {
		mapper(indent).writeValue(out, result);
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
		return new EventBeanSerializer((BeanSerializer)SER_FACTORY.
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
		mapper.setFilters(FILTER_PROVIDER);
		return mapper;
	}

	static class EventsIntrospector extends JacksonAnnotationIntrospector {
		@Override
		public Object findFilterId(Annotated a) {
			return (a instanceof AnnotatedClass) &&
				EventType.class.isAssignableFrom(a.getRawType()) ? EVENTS_FILTER_ID : null;
		}
	}

	static class EventsFilter extends SimpleBeanPropertyFilter {
		@Override
		protected boolean include(BeanPropertyWriter writer) {
			return writer.getAnnotation(XmlTransient.class) == null;
		}

		@Override
		protected boolean include(PropertyWriter writer) {
			return true;
		}
	}

	static class EventsFilterProvider extends FilterProvider {
		@Override
		public BeanPropertyFilter findFilter(Object filterId) {
			return filterId == EVENTS_FILTER_ID ? EVENTS_FILTER : null;
		}
	}

	static class DataModule extends SimpleModule {
		private DataModule() {
			addSerializer(Events.class, new EventsSerializer());
			addSerializer(DataMessage.class, new DataMessageSerializer());
			addSerializer(CandleSymbol.class, new ToStringSerializer());
			addSerializer(IndexedEventSource.class, new ToStringSerializer());
			addSerializer(char.class, new NullSafeCharSerializer());
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
}
