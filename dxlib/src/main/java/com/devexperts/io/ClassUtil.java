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
package com.devexperts.io;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilities to work with classes.
 */
public class ClassUtil {
    private ClassUtil() {} // do not create

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>();
    private static final Map<Class<?>, String> PRIMITIVE_TAGS = new HashMap<>();

    private static void putPrimitive(Class<?> c) {
        PRIMITIVE_TYPES.put(c.getName(), c);
    }

    private static void putPrimitiveTag(Class<?> c) {
        String name = c.getName();
        assert name.startsWith("[") && name.length() == 2;
        PRIMITIVE_TAGS.put(c.getComponentType(), name.substring(1));
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
        putPrimitive(void.class);

        putPrimitiveTag(boolean[].class);
        putPrimitiveTag(byte[].class);
        putPrimitiveTag(short[].class);
        putPrimitiveTag(char[].class);
        putPrimitiveTag(int[].class);
        putPrimitiveTag(long[].class);
        putPrimitiveTag(float[].class);
        putPrimitiveTag(double[].class);
    }

    static boolean isPrimitiveType(String name) {
        return PRIMITIVE_TYPES.containsKey(name);
    }

    /**
     * Converts type name like "int[]" to class.
     * @param type the name of type, a result of {@link Class#getTypeName()}, but can be also
     *             the result of {@link Class#getName()}.
     * @param cl Class loader, can be null for system class loader.
     * @return The corresponding class.
     * @throws ClassNotFoundException if class is not found.
     */
    public static Class<?> getTypeClass(String type, ClassLoader cl) throws ClassNotFoundException {
        int dims = 0;
        while (type.endsWith("[]")) {
            type = type.substring(0, type.length() - 2);
            dims++;
        }
        Class<?> c = PRIMITIVE_TYPES.get(type);
        if (c != null) {
            if (dims == 0)
                return c;
            type = PRIMITIVE_TAGS.get(c);
            if (type == null)
                throw new ClassNotFoundException("Array of void does not exist");
        } else if (dims != 0)
            type = "L" + type + ";";
        for (int i = 0; i < dims; i++) {
            type = "[" + type;
        }
        return Class.forName(type, false, cl);
    }

    // resolves class loader for methods that take it as an argument

    /**
     * Resolves class loader. If entering {@link ClassLoader cl} is equal null, then this method return
     * {@link Thread#getContextClassLoader() Thread.currentThread().getContextClassLoader()} or
     * {@link Class#getClassLoader() ClassUtil.class.getClassLoader()}
     * @param cl entering ClassLoader
     * @return  Resolves classLoader
     */
    static ClassLoader resolveContextClassLoader(ClassLoader cl) {
        if (cl != null)
            return cl;
        try {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null)
                return context;
        } catch (SecurityException ignored) {
        }
        return ClassUtil.class.getClassLoader();
    }
}
