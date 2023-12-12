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
package com.devexperts.mars.common;

import com.devexperts.logging.Logging;
import com.devexperts.monitoring.Monitored;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an monitored instance of Java Bean with properties annotated by {@link Object}.
 *
 * <p>A typical usage of this class involves an annotated bean like this:
 * <pre><tt>
 *     <b>public class</b> ABean {
 *         &#64;{@link Object Monitored}(name="some_value", description="This is some value")
 *         <b>public int</b> getSomeValue() { ... }
 *         // etc -- more monitored properties may be defined
 *     }
 * </tt></pre>
 * A code to register an instance of this bean for MARS monitoring looks like this:
 * <pre><tt>
 *     ABean aBean = ...; // a bean instance to monitor
 *     MARSNode node = ...; // a MARS parent MARS node
 *     {@link MARSScheduler MARSScheduler}.{@link MARSScheduler#schedule(Runnable) schedule}(MARSMonitoredBean.{@link #forInstance(MARSNode, Object) forInstance}(node, aBean));
 * </tt></pre>
 * @param <T> the type of the monitored bean.
 */
public class MARSMonitoredBean<T> implements Runnable {

    private static final Logging log = Logging.getLogging(MARSMonitoredBean.class);

    // --------------------------------- instance fields ---------------------------------

    private final List<Prop> props;
    private T instance;

    // --------------------------------- public static factory ---------------------------------

    /**
     * Analyzes a given bean instance, creates corresponding MARS nodes, and returns an instance of MARSMonitoredBean.
     *
     * @param node MARS node for the values of this bean class.
     * @param instance the bean instance.
     * @param <T> the type of the monitored bean.
     * @return an instance of MARSMonitoredBean.
     * @throws NullPointerException if node or instance are null.
     */
    public static <T> MARSMonitoredBean<T> forInstance(MARSNode node, T instance) {
        List<Prop> props = new ArrayList<>();
        listProps(null, node, instance.getClass(), props);
        return new MARSMonitoredBean<>(props, instance);
    }

    /**
     * Analyzes a given bean class, creates corresponding MARS nodes, and returns an instance of MARSMonitoredBean.
     * Use {@link #update(Object) update} method to set the actual bean instance and update MARS node values.
     *
     * @param node MARS node for the values of this bean class.
     * @param beanClass the bean class.
     * @param <T> the type of the monitored bean.
     * @return an instance of MARSMonitoredBean.
     * @throws NullPointerException if node or beanClass are null.
     */
    public static <T> MARSMonitoredBean<T> forClass(MARSNode node, Class<? extends T> beanClass) {
        List<Prop> props = new ArrayList<>();
        listProps(null, node, beanClass, props);
        return new MARSMonitoredBean<>(props, null);
    }

    // --------------------------------- public methods ---------------------------------

    /**
     * Updates values of the MARS nodes.
     * This method does nothing if the instance was not specified during creating via
     * {@link #forInstance(MARSNode, Object) forInstance} methor or via {@link #update(Object) update} method.
     */
    @Override
    public synchronized void run() {
        for (Prop prop : props)
            prop.update(instance);
    }

    /**
     * Updates values of the nodes using the corresponding instance.
     * Subsequent invocations of {@link #run() run} method will use this instance.
     *
     * @param instance the instance.
     */
    public synchronized void update(T instance) {
        this.instance = instance;
        run();
    }

    /**
     * Removes all MARS nodes for this monitored bean.
     */
    public synchronized void close() {
        for (Prop prop : props) {
            prop.node.remove();
            prop.lastValue = null;
        }
        props.clear();
        instance = null;
    }

    // --------------------------------- private methods ---------------------------------

    public MARSMonitoredBean(List<Prop> props, T instance) {
        this.props = props;
        this.instance = instance;
    }

    private static void listProps(Prop parent, MARSNode node, Class<?> beanClass, List<Prop> props) {
        if (node == null)
            throw new NullPointerException();
        BeanInfo info;
        try {
            info = Introspector.getBeanInfo(beanClass);
        } catch (IntrospectionException e) {
            throw new RuntimeException("Failed to introspect bean class " + beanClass.getName(), e);
        }
        PropertyDescriptor[] pds = info.getPropertyDescriptors();
        if (pds == null)
            return;
        for (PropertyDescriptor pd : pds) {
            Method readMethod = pd.getReadMethod();
            if (readMethod == null)
                continue;
            Monitored monitored = readMethod.getAnnotation(Monitored.class);
            if (monitored == null)
                continue;
            Prop prop = new Prop(parent, readMethod, node, monitored.name(), monitored.description(), monitored.expand());
            props.add(prop);
            if (prop.expand)
                listProps(prop, prop.node, readMethod.getReturnType(), props);
        }
    }

    private static class Prop {
        final Prop parent;
        final Method readMethod;
        final boolean expand;
        final MARSNode node;

        Object lastValue;

        Prop(Prop parent, Method readMethod, MARSNode node, String name, String description, boolean expand) {
            this.parent = parent;
            this.readMethod = readMethod;
            this.expand = expand;
            this.node = node.subNode(name, description);
        }

        public void update(Object instance) {
            Object bean = parent == null ? instance : parent.lastValue;
            if (bean == null) {
                lastValue = null;
                return;
            }
            try {
                lastValue = readMethod.invoke(bean);
            } catch (Exception e) {
                log.error("Failed to retrieve monitored value", e);
                lastValue = null;
                return;
            }
            if (!expand)
                node.setValue(String.valueOf(lastValue));
        }
    }
}
