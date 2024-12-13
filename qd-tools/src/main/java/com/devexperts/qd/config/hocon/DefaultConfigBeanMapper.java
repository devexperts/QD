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
package com.devexperts.qd.config.hocon;

import com.devexperts.qd.config.Required;
import com.devexperts.util.TimePeriod;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import com.typesafe.config.Optional;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * ConfigBeanMapper implementation based on Java Bean introspection facility
 */
class DefaultConfigBeanMapper implements ConfigBeanMapper {
    public static <T> T createInternal(Config config, Class<T> clazz) {
        if (!config.isResolved()) {
            throw new ConfigException.NotResolved(
                "need to Config#resolve() a config before using it to initialize a bean, see the API docs for Config#resolve()");
        }

        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            throw new ConfigException.BadBean("Could not get bean information for class " + clazz.getName(), e);
        }

        try {
            List<PropertyDescriptor> beanProps = Arrays.stream(beanInfo.getPropertyDescriptors())
                .filter(beanProp -> beanProp.getReadMethod() != null && beanProp.getWriteMethod() != null)
                .collect(Collectors.toList());

            Set<String> unusedProperties = new HashSet<>(config.root().keySet());

            // Fill in the bean instance
            T bean = clazz.newInstance();
            for (PropertyDescriptor beanProp : beanProps) {
                Method setter = beanProp.getWriteMethod();
                Type parameterType = setter.getGenericParameterTypes()[0];
                // TODO: check annotations
                String configPropName = beanProp.getName();
                // Is the property key missing in the config?
                if (!config.hasPath(configPropName)) {
                    // If so, continue if the field is marked as @{link Optional}
                    if (isOptionalProperty(clazz, beanProp)) {
                        continue;
                    }
                    // Otherwise, raise a {@link Missing} exception right here
                    throw new ConfigException.Missing(config.origin(), beanProp.getName());
                }
                Object unwrapped = getValue(clazz, parameterType, config, configPropName);
                setter.invoke(bean, unwrapped);
                unusedProperties.remove(configPropName);
            }

            // check for unused properties
            List<ConfigException.ValidationProblem> problems = new ArrayList<>();
            for (String property : unusedProperties) {
                // Skip CAPITALIZED_PROPERTIES - reserved for user variables
                if (property.matches("[A-Z_][A-Z0-9_]*"))
                    continue;
                problems.add(new ConfigException.ValidationProblem(property, config.origin(),
                    "Unused config property '" + property + "'"));
            }
            if (!problems.isEmpty())
                throw new ConfigException.ValidationFailed(problems);

            return bean;
        } catch (InstantiationException e) {
            throw new ConfigException.BadBean(
                clazz.getName() + " needs a public no-args constructor to be used as a bean", e);
        } catch (IllegalAccessException e) {
            throw new ConfigException.BadBean(
                clazz.getName() + " getters and setters are not accessible, they must be for use as a bean", e);
        } catch (InvocationTargetException e) {
            throw new ConfigException.BadBean("Calling bean method on " + clazz.getName() + " caused an exception", e);
        }
    }

    private static boolean isSupportedType(Type type) {
        Class<?> pClass = typeToClass(type);
        if (pClass == null)
            return false;
        if (isBasicSupportedClass(pClass) || pClass.isEnum())
            return true;
        if (pClass == List.class || pClass == Set.class) {
            Type componentType = ((ParameterizedType) type).getActualTypeArguments()[0];
            return isSupportedType(componentType);
        }
        if (pClass == Map.class) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            return arguments[0] == String.class && isSupportedType(arguments[1]);
        }
        return supportsConversionFromString(pClass) || isSupportedBean(pClass);
    }

    /**
     * Classes directly supported by {@link Config} instances
     */
    private static boolean isBasicSupportedClass(Class<?> pClass) {
        return pClass == Boolean.class || pClass == boolean.class ||
            pClass == Integer.class || pClass == int.class ||
            pClass == Double.class || pClass == double.class ||
            pClass == Long.class || pClass == long.class ||
            pClass == Number.class ||
            pClass == String.class ||
            pClass == Duration.class || pClass == TimePeriod.class || pClass == TemporalAmount.class ||
            pClass == ConfigMemorySize.class ||
            pClass == Config.class || pClass == ConfigObject.class ||
            pClass == ConfigValue.class || pClass == ConfigList.class ||
            pClass == Object.class;
    }

    private static Object getValue(@Nullable Class<?> beanClass, Type valueType, Config config, String path) {
        Class<?> pClass = typeToClass(valueType);
        if (pClass == null)
            throw getUnsupportedTypeException(beanClass, path, valueType);

        // TODO: lookup mappers using provided registry / annotations
        if (pClass == Boolean.class || pClass == boolean.class) {
            return config.getBoolean(path);
        } else if (pClass == Integer.class || pClass == int.class) {
            return config.getInt(path);
        } else if (pClass == Double.class || pClass == double.class) {
            return config.getDouble(path);
        } else if (pClass == Long.class || pClass == long.class) {
            return config.getLong(path);
        } else if (pClass == Number.class) {
            return config.getNumber(path);
        } else if (pClass == String.class) {
            return config.getString(path);
        } else if (pClass == Duration.class) {
            return config.getDuration(path);
        } else if (pClass == TemporalAmount.class) {
            return config.getTemporal(path);
        } else if (pClass == ConfigMemorySize.class) {
            return config.getMemorySize(path);
        } else if (pClass == TimePeriod.class) {
            return TimePeriod.valueOf(config.getString(path));
        } else if (pClass == Object.class) {
            return config.getAnyRef(path);
        } else if (pClass == List.class) {
            return getListValue(beanClass, valueType, config, path);
        } else if (pClass == Set.class) {
            return getSetValue(beanClass, valueType, config, path);
        } else if (pClass == Map.class) {
            return getMapValue(beanClass, (ParameterizedType) valueType, config, path);
        } else if (pClass == Config.class) {
            return config.getConfig(path);
        } else if (pClass == ConfigObject.class) {
            return config.getObject(path);
        } else if (pClass == ConfigValue.class) {
            return config.getValue(path);
        } else if (pClass == ConfigList.class) {
            return config.getList(path);
        } else if (pClass.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum anEnum = getEnum(config, path, (Class<Enum>) pClass, true);
            return anEnum;
        }

        if (supportsConversionFromString(pClass)) {
            ConfigValue value = config.getValue(path);
            if (value.valueType() == ConfigValueType.STRING) {
                try {
                    return findValueOfMethod(pClass).invoke(null, value.unwrapped());
                } catch (Exception e) {
                    throw new ConfigException.BadValue(config.origin(), path,
                        "Failed to create " + pClass.getName() + " object from '" + value.unwrapped() + "'", e);
                }
            }
        }
        if (isSupportedBean(pClass)) {
            // FIXME: shall consult factory for a mapper
            return createInternal(config.getConfig(path), pClass);
        }
        throw getUnsupportedTypeException(beanClass, path, valueType);
    }

    private static @Nullable Class<?> typeToClass(Type type) {
        Type rawType = type;
        if (type instanceof ParameterizedType)
            rawType = ((ParameterizedType) type).getRawType();
        if (rawType instanceof Class)
            return (Class<?>) rawType;
        return null;
    }

    private static ConfigException.BadBean getUnsupportedTypeException(@Nullable Class<?> beanClass,
        String property, Type type)
    {
        return new ConfigException.BadBean(
            "Bean property '" + property + "'" +
                (beanClass == null ? "" : " of class " + beanClass.getName()) +
                " has unsupported type " + type);
    }

    private static Map<String, Object> getMapValue(Class<?> beanClass, ParameterizedType parameterType, Config config,
        String configPropName)
    {
        Type[] typeArgs = parameterType.getActualTypeArguments();
        if (typeArgs[0] != String.class) {
            throw getUnsupportedTypeException(beanClass, configPropName, parameterType);
        }
        // raw unwrapped config graph for Objects
        if (typeArgs[1] == Object.class)
            return config.getObject(configPropName).unwrapped();

        if (typeArgs[1] instanceof Class &&
            !supportsConversionFromString((Class<?>) typeArgs[1]) &&
            isSupportedBean((Class<?>) typeArgs[1]))
        {
            Config mapBase = config.getConfig(configPropName);
            HashMap<String, Object> map = new HashMap<>();
            for (Map.Entry<String, ConfigValue> e : mapBase.root().entrySet()) {
                // FIXME: shall consult provider for a mapper
                ConfigValue v = e.getValue();
                Config c = v instanceof ConfigObject ? ((ConfigObject) v).toConfig() : v.atKey("a").getConfig("a");
                map.put(e.getKey(), createInternal(c, (Class<?>) typeArgs[1]));
            }
            return map;
        }

        if (isSupportedType(typeArgs[1])) {
            // generic algo for other supported types
            Config mapBase = config.getConfig(configPropName);
            HashMap<String, Object> map = new HashMap<>();
            for (String key : mapBase.root().keySet()) {
                map.put(key, getValue(null, typeArgs[1], mapBase, key));
            }
            return map;
        }

        throw getUnsupportedTypeException(beanClass, configPropName, parameterType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getSetValue(Class<?> beanClass, Type parameterType, Config config, String configPropName) {
        return new HashSet((List) getListValue(beanClass, parameterType, config, configPropName));
    }

    private static Object getListValue(Class<?> beanClass, Type parameterType, Config config, String configPropName) {
        Type elementType = ((ParameterizedType) parameterType).getActualTypeArguments()[0];

        if (elementType == Boolean.class) {
            return config.getBooleanList(configPropName);
        } else if (elementType == Integer.class) {
            return config.getIntList(configPropName);
        } else if (elementType == Double.class) {
            return config.getDoubleList(configPropName);
        } else if (elementType == Long.class) {
            return config.getLongList(configPropName);
        } else if (elementType == String.class) {
            return config.getStringList(configPropName);
        } else if (elementType == Duration.class) {
            return config.getDurationList(configPropName);
        } else if (elementType == ConfigMemorySize.class) {
            return config.getMemorySizeList(configPropName);
        } else if (elementType == Object.class) {
            return config.getAnyRefList(configPropName);
        } else if (elementType == Config.class) {
            return config.getConfigList(configPropName);
        } else if (elementType == ConfigObject.class) {
            return config.getObjectList(configPropName);
        } else if (elementType == ConfigValue.class) {
            return config.getList(configPropName);
        } else if (elementType instanceof Class<?> && ((Class<?>) elementType).isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Enum> enumList = getEnumList(config, configPropName, (Class<Enum>) elementType, true);
            return enumList;
        } else if (elementType instanceof Class<?> &&
            !supportsConversionFromString((Class<?>) elementType) &&
            isSupportedBean((Class<?>) elementType))
        {
            // TODO: support constructing objects from primitive
            List<Object> beanList = new ArrayList<>();
            List<? extends Config> configList = config.getConfigList(configPropName);
            for (Config listMember : configList) {
                beanList.add(createInternal(listMember, (Class<?>) elementType));
            }
            return beanList;
        } else if (isSupportedType(elementType)) {
            List<Object> list = new ArrayList<>();
            ConfigList configList = config.getList(configPropName);
            for (ConfigValue v : configList) {
                // Using path with an index won't work (an issue of typesafe config implementation)
                // BAD: list.add(getValue(null, elementType, config, configPropName + "." + index));
                if (v == null || v.valueType() == ConfigValueType.NULL) {
                    list.add(null);
                } else {
                    Config elemConfig = v.atKey("elem").root().withOrigin(v.origin()).toConfig();
                    list.add(getValue(null, elementType, elemConfig, "elem"));
                }
            }
            return list;
        } else {
            throw getUnsupportedTypeException(beanClass, configPropName, elementType);
        }
    }

    private static <T extends Enum<T>> T getEnum(Config config, String path, Class<T> enumType, boolean ignoreCase) {
        if (ignoreCase) {
            return findEnumIgnoreCase(config, path, enumType, config.getString(path));
        } else {
            return config.getEnum(enumType, path);
        }
    }

    private static <T extends Enum<T>>
    List<T> getEnumList(Config config, String path, Class<T> enumType, boolean ignoreCase)
    {
        if (ignoreCase) {
            List<String> strings = config.getStringList(path);
            List<T> result = new ArrayList<>(strings.size());
            for (String s : strings) {
                T candidate = findEnumIgnoreCase(config, path, enumType, s);
                result.add(candidate);
            }
            return result;
        } else {
            return config.getEnumList(enumType, path);
        }
    }

    private static <T extends Enum<T>> T findEnumIgnoreCase(Config cfg, String path, Class<T> enumType, String s) {
        T candidate = null;
        for (T v : enumType.getEnumConstants()) {
            if (v.name().equalsIgnoreCase(s)) {
                if (candidate == null) {
                    candidate = v;
                } else {
                    throw new ConfigException.BadValue(cfg.origin(), path,
                        "Redundant enum candidate " + s + " for " + enumType.getName());
                }
            }
        }
        if (candidate == null) {
            throw new ConfigException.BadValue(cfg.origin(), path,
                "Can't convert " + s + " to a " + enumType.getName() + " value");
        }
        return candidate;
    }

    /**
     * Check if provided class is constructable using generic Java Beans introspection
     */
    private static boolean isSupportedBean(Class<?> clazz) {
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            return false;
        }

        for (PropertyDescriptor beanProp : beanInfo.getPropertyDescriptors()) {
            if (beanProp.getReadMethod() != null && beanProp.getWriteMethod() != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean supportsConversionFromString(Class<?> clazz) {
        return findValueOfMethod(clazz) != null;
    }

    private static Method findValueOfMethod(Class<?> clazz) {
        Method valueOfMethod = null;
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) &&
                Modifier.isPublic(method.getModifiers()) &&
                clazz.isAssignableFrom(method.getReturnType()) &&
                method.getParameterCount() == 1 &&
                method.getParameterTypes()[0] == String.class &&
                "valueOf".equals(method.getName()))
            {
                valueOfMethod = method;
                break;
            }
        }
        return valueOfMethod;
    }

    private static boolean isOptionalProperty(Class<?> beanClass, PropertyDescriptor beanProp) {
        Field field = getField(beanClass, beanProp.getName());
        boolean fieldRequired = field != null && field.getAnnotationsByType(Required.class).length != 0;
        boolean fieldOptional = field != null && field.getAnnotationsByType(Optional.class).length != 0;
        boolean getterRequired = beanProp.getReadMethod().getAnnotationsByType(Required.class).length != 0;
        boolean getterOptional = beanProp.getReadMethod().getAnnotationsByType(Optional.class).length != 0;
        boolean required = fieldRequired || getterRequired;
        boolean optional = fieldOptional || getterOptional;
        if (required && optional) {
            throw new IllegalArgumentException("Property " + beanClass.getSimpleName() + "." + beanProp.getName() +
                " is marked as Required and Optional at the same time");
        }
        // all fields are optional by default for the moment (maybe we will support a class-wide overrides later).
        return optional || !required;
    }

    private static Field getField(Class<?> beanClass, String fieldName) {
        while (beanClass != null) {
            try {
                Field field = beanClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // Don't give up yet. Try to look for field in super class, if any.
            }
            beanClass = beanClass.getSuperclass();
        }
        return null;
    }

    static String toCamelCase(String originalName) {
        String[] words = originalName.split("-+");
        StringBuilder nameBuilder = new StringBuilder(originalName.length());
        for (String word : words) {
            if (nameBuilder.length() == 0) {
                nameBuilder.append(word);
            } else {
                nameBuilder.append(word.substring(0, 1).toUpperCase());
                nameBuilder.append(word.substring(1));
            }
        }
        return nameBuilder.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(HoconConfigProvider factory, Config config, @Nullable String property, Type valueType) {
        if (property == null && valueType instanceof Class)
            return createInternal(config, (Class<T>) valueType);
        return (T) getValue(null, valueType, config, property);
    }

}
