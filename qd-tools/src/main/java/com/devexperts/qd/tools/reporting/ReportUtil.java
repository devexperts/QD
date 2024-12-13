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
package com.devexperts.qd.tools.reporting;

import com.devexperts.annotation.Experimental;
import com.devexperts.logging.Logging;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlType;

@SuppressWarnings("rawtypes")
@Experimental
public class ReportUtil {

    private static final Logging log = Logging.getLogging(ReportUtil.class);

    private static final List<String> MSG_PROPS =
        Collections.unmodifiableList(Arrays.asList("message", "description", "formattedMessage"));

    public interface EventIntrospector {

        /**
         * Returns list of the attributes for the event;
         * Depending on the nature of introspection algorithm, it may return a list of attributes for the provided event
         * instance (consider a Map-based events) or generally known attributes for this type of events
         * (consider class-based introspection). Therefore, it's correct to return null values from {@link #getAttr}
         * for some attribute names returned by the method.
         *
         * @param event the event object to be introspected
         *
         * @return list of the attributes for the event
         */
        @Nonnull
        public List<String> attrNames(Object event);

        @Nullable
        public Object getAttr(Object event, String attrName);

        /**
         * @param event
         * @return message describing the event
         */
        public String getMessage(Object event);
    }

    static class VoidIntrospector implements EventIntrospector {

        @Nonnull
        @Override
        public List<String> attrNames(Object event) {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public Object getAttr(Object event, String attrName) {
            return null;
        }

        @Override
        public String getMessage(Object event) {
            return event.toString(); // by default object is self-representable
        }
    }

    @SuppressWarnings("rawtypes")
    static class JavaBeansIntrospector implements EventIntrospector {

        private final Class clazz;
        private final LinkedHashMap<String, PropertyDescriptor> props;
        private final List<String> attrNames;
        private final PropertyDescriptor messageProp;

        JavaBeansIntrospector(Class clazz, LinkedHashMap<String, PropertyDescriptor> props,
            PropertyDescriptor messageProp)
        {
            this.clazz = clazz;
            this.props = props;
            attrNames = Collections.unmodifiableList(new ArrayList<>(props.keySet()));
            this.messageProp = messageProp;
        }

        @Nonnull
        @Override
        public List<String> attrNames(Object event) {
            return attrNames;
        }

        @Nullable
        @Override
        public Object getAttr(Object event, String attrName) {
            Objects.requireNonNull(event, "event cannot be null");
            Objects.requireNonNull(attrName, "attrName cannot be null");
            PropertyDescriptor prop = props.get(attrName);
            if (prop == null)
                return null;
            return getProperty(event, prop);
        }

        @Override
        public String getMessage(Object event) {
            if (messageProp == null)
                return event.toString();
            return Objects.toString(getProperty(event, messageProp), "");
        }

        private Object getProperty(Object event, PropertyDescriptor prop) {
            try {
                return prop.getReadMethod().invoke(event);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final EventIntrospector VOID_INTROSPECTOR = new VoidIntrospector();
    @SuppressWarnings("CheckStyle")
    static final WeakHashMap<Class, EventIntrospector> cache = new WeakHashMap<>();

    private ReportUtil() { /* Utility class */ }

    public static EventIntrospector getIntrospector(Object event) {
        Objects.requireNonNull(event, "event must not be null");
        // Current implementation supports only basic Java Beans introspection with attributes ordering based on
        // JAXB annotation @XmlType(propOrder=...)
        synchronized (cache) {
            return cache.computeIfAbsent(event.getClass(), ReportUtil::buildIntrospector);
        }

    }

    private static EventIntrospector buildIntrospector(Class<?> clazz) {

        // Do not try to introspect strings (TODO: also skip other base classes)
        if (clazz == String.class)
            return VOID_INTROSPECTOR;

        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            log.error("Could not get bean information for class " + clazz.getName(), e);
            return VOID_INTROSPECTOR;
        }

        Map<String, PropertyDescriptor> beanProps = new LinkedHashMap<>();
        for (PropertyDescriptor beanProp : beanInfo.getPropertyDescriptors()) {
            if (!"class".equals(beanProp.getName()) && beanProp.getReadMethod() != null)
                beanProps.put(beanProp.getName(), beanProp);
        }

        // filter-out message property - it won't be an attribute.
        PropertyDescriptor messageProp = null;
        for (String pName : MSG_PROPS) {
            PropertyDescriptor prop = beanProps.remove(pName);
            if (prop != null) {
                messageProp = prop;
                break;
            }
        }

        // Simple support for JAXB @XmlType.propOrder
        XmlType xmlElementAnnotation = clazz.getAnnotation(XmlType.class);
        LinkedHashMap<String, PropertyDescriptor> attrs = new LinkedHashMap<>();
        if (xmlElementAnnotation != null) {
            String[] propOrder = xmlElementAnnotation.propOrder();
            if (propOrder != null && propOrder.length > 0 && !propOrder[0].isEmpty()) {
                // have a defined list of attributes with order
                for (String prop : propOrder) {
                    if (MSG_PROPS.contains(prop))
                        continue;
                    PropertyDescriptor descriptor = beanProps.remove(prop);
                    if (descriptor != null)
                        attrs.put(prop, descriptor);
                }
            }
        }
        beanProps.entrySet().stream()
            .filter(e -> !MSG_PROPS.contains(e.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(a -> attrs.put(a.getKey(), a.getValue()));
        return new JavaBeansIntrospector(clazz, attrs, messageProp);
    }

}
