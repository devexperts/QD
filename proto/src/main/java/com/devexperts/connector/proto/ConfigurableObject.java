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
package com.devexperts.connector.proto;

import com.devexperts.util.ConfigUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Connectivity-related object that can be configured.
 */
public class ConfigurableObject implements Cloneable {
    private static final String IS_PREFIX = "is";
    private static final String GET_PREFIX = "get";
    private static final String SET_PREFIX = "set";

    private Map<ConfigurationKey<?>, IntrospectedKey<?>> introspectedConfiguration;

    /**
     * Creates a copy of this {@link ConfigurableObject} with the same initial configuration.
     */
    @Override
    public ConfigurableObject clone() {
        try {
            return (ConfigurableObject) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a set of supported configuration keys.
     * This implementation returns a set of configuration keys
     * based on the analysis of all methods of this object with {@link Configurable} annotation.
     *
     * @return a set of supported configuration keys.
     */
    public Set<ConfigurationKey<?>> supportedConfiguration() {
        return introspectConfiguration().keySet();
    }

    /**
     * Returns the value of the configuration key or {@code null}.
     * This implementation queries configuration using methods of this
     * object with {@link Configurable} annotation.
     *
     * @param key the configuration key.
     * @param <T> the value type of the configuration key.
     * @return the value of the configuration key or {@code null}.
     */
    @SuppressWarnings({"unchecked"})
    public <T> T getConfiguration(ConfigurationKey<T> key) {
        IntrospectedKey<?> introspectedKey = introspectConfiguration().get(key);
        if (introspectedKey == null)
            return null;
        Object result;
        try {
            result = introspectedKey.getter.invoke(this);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Getter is not accessible for '" + introspectedKey + "' in " + getClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new ConfigurationException(introspectedKey, e.getCause());
        }
        // NOTE: Getter with non-matching result type is explicitly allowed
        // check what type was requested and return it
        if (key.getType() == introspectedKey.getter.getReturnType())
            return (T) result;
        if (key.getType() == String.class)
            return (T) String.valueOf(result);
        throw new IllegalArgumentException("Cannot coerce type of '" + introspectedKey + "' to '" + key + "' to get value");
    }

    /**
     * Changes the value of the given configuration key.
     * This implementation changes configuration using methods of this
     * object with {@link Configurable} annotation.
     *
     * @param key the configuration key.
     * @param value the value to set.
     * @param <T> the value type of the configuration key.
     * @return {@code true} if the key is supported by this factory and thus was set and {@code false} otherwise.
     * @throws ConfigurationException if the key is supported but the value was invalid.
     */
    public <T> boolean setConfiguration(ConfigurationKey<T> key, T value)
        throws ConfigurationException
    {
        if (value != null && !key.getType().isInstance(value))
            throw new IllegalArgumentException("Invalid value class " + value.getClass().getName() + " for '" + key + "'");
        IntrospectedKey<?> introspectedKey = introspectConfiguration().get(key);
        if (introspectedKey == null)
            return false;
        // coerce value type
        Object resolvedValue;
        if (key.getType() == introspectedKey.getType())
            resolvedValue = value;
        else if (key.getType() == String.class) {
            // Coerce from string to the target value
            resolvedValue = ConfigUtil.convertStringToObject(introspectedKey.getType(), (String) value);
        } else
            throw new IllegalArgumentException("Cannot coerce type of '" + key + "' to '" + introspectedKey + "' to set value");
        try {
            introspectedKey.setter.invoke(this, resolvedValue);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Setter is not accessible for '" + introspectedKey + "' in " + getClass().getName(), e); // should not happen
        } catch (InvocationTargetException e) {
            throw new ConfigurationException(introspectedKey, e.getCause());
        }
        return true;
    }

    /**
     * Process all configuration updates. It is invoked when all configuration were set with
     * {@link #setConfiguration(ConfigurationKey, Object) setConfiguration} invocation.
     */
    public void reinitConfiguration() {
        // nothing to do here
    }

    private void ensureAccessible(Method method) {
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers()))
            return; // public -- Ok
        if (method.isAccessible())
            return; // already accessible
        try {
            method.setAccessible(true);
        } catch (SecurityException e) {
            // ignore -- failed
        }
    }

    private synchronized Map<ConfigurationKey<?>, IntrospectedKey<?>> introspectConfiguration() {
        if (introspectedConfiguration != null)
            return introspectedConfiguration;
        introspectedConfiguration = new LinkedHashMap<>();
        for (Method setter : getClass().getMethods()) {
            Configurable configurable = setter.getAnnotation(Configurable.class);
            if (configurable != null) {
                String setterName = setter.getName();
                if (!Modifier.isPublic(setter.getModifiers()))
                    throw new IllegalArgumentException("@Configurable setter '" + setterName + "' method is not public");
                if (!setterName.startsWith(SET_PREFIX) || setterName.length() <= SET_PREFIX.length() || !Character.isUpperCase(setterName.charAt(SET_PREFIX.length())))
                    throw new IllegalArgumentException("@Configurable setter '" + setterName + "' method has invalid name");
                if (setter.getReturnType() != void.class)
                    throw new IllegalArgumentException("@Configurable setter '" + setterName + "' method has invalid return type");
                Class<?>[] parameterTypes = setter.getParameterTypes();
                if (parameterTypes.length != 1)
                    throw new IllegalArgumentException("@Configurable setter '" + setterName + "' method has wrong number of parameters");
                // find getter
                Method getter;
                try {
                    getter = getClass().getMethod(GET_PREFIX + setterName.substring(SET_PREFIX.length()));
                } catch (NoSuchMethodException e) {
                    try {
                        getter = getClass().getMethod(IS_PREFIX + setterName.substring(SET_PREFIX.length()));
                    } catch (NoSuchMethodException e1) {
                        throw new IllegalArgumentException("@Configurable setter '" + setterName + "' has no matching getter");
                    }
                }
                if (!Modifier.isPublic(getter.getModifiers()))
                    throw new IllegalArgumentException("@Configurable setter '" + setterName + "' has non-public getter");
                // NOTE: Getter with non-matching result type is explicitly allowed
                // Key's type stores setter's parameter type
                // get name from annotation or create default one
                String name = !configurable.name().isEmpty() ? configurable.name() :
                    Character.toLowerCase(setterName.charAt(SET_PREFIX.length())) + setterName.substring(SET_PREFIX.length() + 1);
                ensureAccessible(getter);
                ensureAccessible(setter);
                IntrospectedKey<?> introspectedKey = new IntrospectedKey<Object>(name, parameterTypes[0],
                    configurable.description(), getter, setter);
                introspectedConfiguration.put(introspectedKey, introspectedKey);
            }
        }
        return introspectedConfiguration;
    }

    static class IntrospectedKey<T> extends ConfigurationKey<T> {
        final Method getter;
        final Method setter;

        @SuppressWarnings({"rawtypes", "unchecked"})
        IntrospectedKey(String name, Class type, String description, Method getter, Method setter) {
            super(name, type, description);
            this.getter = getter;
            this.setter = setter;
        }
    }

}
