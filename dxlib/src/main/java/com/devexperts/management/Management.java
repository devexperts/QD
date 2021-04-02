/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.management;

import com.devexperts.annotation.Description;
import com.devexperts.logging.Logging;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.StandardMBean;

/**
 * JMX management utilities.
 */
public class Management {
    private static final Logging log = Logging.getLogging(Management.class);
    private static final String MXBEAN = "MXBean";

    private Management() {
    } // utility class - don't create or inherit

    /**
     * Convenient method to get an MBean name for a singleton class.
     *
     * @return bean name of {@code <package>:type=<class-name>}
     */
    public static String getMBeanNameForClass(Class<?> clazz) {
        String name = clazz.getName();
        int i = name.lastIndexOf('.');
        return (i >= 0 ? name.substring(0, i) : "default") + ":type=" + (i >= 0 ? name.substring(i + 1) : name);
    }

    /**
     * Convenient method to register JMX MBean with a specified management interface.
     * This method invokes {@link #wrapMBean(Object, Class) wrapMBean(mbean, mbeanInterface)} to convert this MBean
     * into DynamicMBean and then registers it in the
     * {@link ManagementFactory#getPlatformMBeanServer() platform MBeanServer}.
     *
     * <p> This methods ignores all unexpected errors that might happen during an attempt to register
     * MBean and logs them. The resulting {@link Registration} will return true on its
     * {@link Registration#hasFailed() hasFailed()} method.
     *
     * <p> When bean with the corresponding name is already registered, then the resulting {@link Registration}
     * will return true on its {@link Registration#hasExisted() hasExisted()} method.
     *
     * @param mbean          the MBean to be registered.
     * @param mbeanInterface the Management Interface exported by this MBean's implementation.
     *                       If null, then this object will use standard JMX design pattern to determine the management
     *                       interface associated with the given implementation.
     * @param name           the object name of MBean.
     *                       Use {@link #getMBeanNameForClass(Class)} to register a singleton mbean instance.
     * @return registration object.
     * Use registration object to {@link Registration#unregister() unregister} the bean when it
     * is no longer needed.
     */
    public static Registration registerMBean(Object mbean, Class<?> mbeanInterface, String name) {
        try {
            mbean = wrapMBean(mbean, mbeanInterface);
            return new Registration(false, false,
                ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, new ObjectName(name)));
        } catch (InstanceAlreadyExistsException e) {
            return new Registration(false, true, null);
        } catch (Exception e) {
            log.error("Unexpected MBean registration exception for " + name, e);
            return new Registration(true, false, null);
        }
    }

    /**
     * Wraps MBean into a dynamic MBean with support for annotation-based descriptions.
     * MBean's management interface, its getter method and operations can be annotated with
     * {@link Description} annotation ({@link ManagementDescription} is deprecated)
     * to provide description text and, for operation, parameter names and descriptions.
     *
     * <p> This method just returns mbean if it already implements {@link DynamicMBean} interface.
     *
     * <p> This method retrieves each readable MBean attribute and if it throws
     * {@link UnsupportedOperationException}, then the corresponding attribute is not
     * included into meta-information of the resulting dynamic MBean.
     *
     * <p> When {@code mbeanInterface} is null, then this method also supports MXBean naming convention by
     * checking if mbean instance implements interface with a suffix "MXBean".
     * The bean with "MXBean" interface is registered as an MXBean with open types
     * (assuming all its operations and attributes are compatible with open types).
     *
     * @param mbean          the MBean to be registered.
     * @param mbeanInterface the Management Interface exported by this MBean's implementation.
     *                       If null, then this object will use standard JMX design pattern to determine the management
     *                       interface associated with the given implementation.
     * @throws IllegalArgumentException if mbean and/or interface is not compliant with JMX specification.
     */
    @SuppressWarnings({"unchecked"})
    public static DynamicMBean wrapMBean(Object mbean, Class<?> mbeanInterface) {
        if (mbean instanceof DynamicMBean)
            return (DynamicMBean) mbean;
        if (mbeanInterface == null) {
            // check for MXBean
            try {
                Class<?> candidate = Class.forName(mbean.getClass().getName() + MXBEAN);
                if (candidate.isInstance(mbean))
                    mbeanInterface = candidate;
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        return new MBean(new StandardMBean(mbean, (Class<Object>) mbeanInterface,
            mbeanInterface != null && mbeanInterface.getSimpleName().endsWith(MXBEAN)));
    }

    static String getDescription(String defaultDescription, AnnotatedElement element) {
        String description = null;
        Description descriptionAnnotation = element.getAnnotation(Description.class);
        if (descriptionAnnotation != null) {
            description = descriptionAnnotation.value();
        } else {
            ManagementDescription managementDescriptionAnnotation = element.getAnnotation(ManagementDescription.class);
            if (managementDescriptionAnnotation != null)
                description = managementDescriptionAnnotation.value();
        }
        return description != null ? description : defaultDescription;
    }

    static MBeanAttributeInfo[] getDescription(MBeanAttributeInfo[] attributes, StandardMBean delegate,
        Class<?> mbeanInterface)
    {
        List<MBeanAttributeInfo> result = new ArrayList<>(attributes.length);
        for (int i = 0; i < attributes.length; i++) {
            MBeanAttributeInfo attribute = attributes[i];
            if (attribute.isReadable()) {
                // test attribute first
                try {
                    delegate.getAttribute(attribute.getName());
                } catch (UnsupportedOperationException e) {
                    // this attribute is unsupported -- skip it
                    continue;
                } catch (MBeanException e) {
                    if (e.getTargetException() instanceof UnsupportedOperationException) {
                        // this attribute is unsupported -- skip it
                        continue;
                    }
                } catch (ReflectionException | AttributeNotFoundException e) {
                    // ignore
                }
                // then find attribute description
                try {
                    Method method = mbeanInterface.getMethod((attribute.isIs() ? "is" : "get") + attribute.getName());
                    String description = getDescription(null, method);
                    if (description != null) {
                        attribute = new MBeanAttributeInfo(attribute.getName(), attribute.getType(), description,
                            attribute.isReadable(), attribute.isWritable(), attribute.isIs());
                    }
                } catch (NoSuchMethodException e) {
                    // skip
                }
            }
            result.add(attribute);
        }
        return result.toArray(new MBeanAttributeInfo[result.size()]);
    }

    static MBeanOperationInfo[] getDescription(MBeanOperationInfo[] operations, Class<?> mbeanInterface) {
        MBeanOperationInfo[] result = new MBeanOperationInfo[operations.length];
        for (int i = 0; i < operations.length; i++) {
            MBeanOperationInfo operation = operations[i];
            MBeanParameterInfo[] signature = operation.getSignature();
            try {
                Class<?>[] params = new Class[signature.length];
                for (int j = 0; j < signature.length; j++) {
                    MBeanParameterInfo param = signature[j];
                    params[j] = getTypeClass(param.getType());
                }
                Method method = mbeanInterface.getMethod(operation.getName(), params);

                ManagementDescription a = method.getAnnotation(ManagementDescription.class);
                if (a != null) {
                    ManagementParameterDescription[] aps = a.parameters();
                    for (int j = 0; j < Math.min(signature.length, aps.length); j++) {
                        MBeanParameterInfo param = signature[j];
                        ManagementParameterDescription ap = aps[j];
                        signature[j] = new MBeanParameterInfo(ap.name(), param.getType(), ap.value());
                    }
                    operation = new MBeanOperationInfo(operation.getName(), a.value(),
                        signature, operation.getReturnType(), a.impact());
                } else {
                    Description methodDescription = method.getAnnotation(Description.class);
                    for (int paramI = 0; paramI < method.getParameterAnnotations().length; paramI++) {
                        Description parameterDescription = null;
                        for (int j = 0; j < method.getParameterAnnotations()[paramI].length; j++) {
                            Annotation annotation = method.getParameterAnnotations()[paramI][j];
                            if (annotation instanceof Description)
                                parameterDescription = (Description) annotation;
                        }
                        MBeanParameterInfo param = signature[paramI];
                        signature[paramI] = new MBeanParameterInfo(
                            parameterDescription != null ? parameterDescription.name() : signature[paramI].getName(),
                            param.getType(),
                            parameterDescription != null ? parameterDescription.value() : signature[paramI].getDescription()
                        );
                    }
                    operation = new MBeanOperationInfo(
                        operation.getName(),
                        methodDescription != null ? methodDescription.value() : operation.getDescription(),
                        signature, operation.getReturnType(), MBeanOperationInfo.UNKNOWN
                    );
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // ignore
            }
            result[i] = operation;
        }
        return result;
    }

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>();

    private static void putPrimitive(Class<?> c) {
        PRIMITIVE_TYPES.put(c.getName(), c);
    }

    static {
        putPrimitive(boolean.class);
        putPrimitive(byte.class);
        putPrimitive(short.class);
        putPrimitive(char.class);
        putPrimitive(int.class);
        putPrimitive(long.class);
        putPrimitive(float.class);
        putPrimitive(double.class);
    }

    private static Class<?> getTypeClass(String type) throws ClassNotFoundException {
        Class<?> c = PRIMITIVE_TYPES.get(type);
        if (c != null)
            return c;
        return Class.forName(type);
    }

    private static class MBean implements DynamicMBean {
        private final DynamicMBean delegate;
        private final MBeanInfo info;

        MBean(StandardMBean delegate) {
            this.delegate = delegate;
            Class<?> mbeanInterface = delegate.getMBeanInterface();
            MBeanInfo bi = delegate.getMBeanInfo();
            info = new MBeanInfo(bi.getClassName(),
                getDescription(bi.getDescription(), mbeanInterface),
                getDescription(bi.getAttributes(), delegate, mbeanInterface),
                bi.getConstructors(),
                getDescription(bi.getOperations(), mbeanInterface),
                bi.getNotifications());
        }

        public MBeanInfo getMBeanInfo() {
            return info;
        }

        public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException
        {
            return delegate.getAttribute(attribute);
        }

        public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException
        {
            delegate.setAttribute(attribute);
        }

        public AttributeList getAttributes(String[] attributes) {
            return delegate.getAttributes(attributes);
        }

        public AttributeList setAttributes(AttributeList attributes) {
            return delegate.setAttributes(attributes);
        }

        public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException
        {
            return delegate.invoke(actionName, params, signature);
        }
    }

    /**
     * Result of {@link #registerMBean(Object, Class, String) registerMBean} invocation.
     */
    public static class Registration {
        private final boolean failed;
        private final boolean existed;
        private ObjectInstance instance;

        Registration(boolean failed, boolean existed, ObjectInstance instance) {
            this.failed = failed;
            this.existed = existed;
            this.instance = instance;
        }

        /**
         * Returns JMX object instance.
         *
         * @return JMX object instance.
         */
        public ObjectInstance getInstance() {
            return instance;
        }

        /**
         * Returns true if registration failed.
         *
         * @return true if registration failed.
         */
        public boolean hasFailed() {
            return failed;
        }

        /**
         * Returns true if the object already exists.
         *
         * @return true if the object already exists.
         */
        public boolean hasExisted() {
            return existed;
        }

        /**
         * Unregister the object if registration is not {@link #hasFailed() failed} and
         * the object does not {@link #hasExisted() already exists}.
         */
        public synchronized void unregister() {
            if (instance != null) {
                try {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(instance.getObjectName());
                } catch (Exception e) {
                    log.error("Unexpected MBean unregistration exception for " + instance.getObjectName(), e);
                }
                instance = null;
            }
        }
    }
}
