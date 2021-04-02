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

import java.io.IOException;
import java.io.InvalidClassException;
import java.util.ArrayList;


/**
 * A typed implementation of {@link Marshaller}.
 * The types can be stored in the following ways as an array of classes or a comma-separated list of type names.
 * It resolves class names into classes only when needed, but not earlier.
 * <b>This class is thread-safe.</b>
 */
final class TypedMarshallerImpl<T> extends Marshaller.Typed<T> {

    // ================== private instance fields ==================

    private final boolean single; // true to marshal a single object, false to marshall an array of objects
    private final String types;
    private volatile Class<?>[] classes;

    // ================== constructor & methods ==================

    TypedMarshallerImpl(boolean single, Class<?>... classes) {
        if (classes == null)
            throw new NullPointerException();
        if (single && classes.length != 1)
            throw new IllegalArgumentException();
        this.single = single;
        StringBuilder sb = new StringBuilder();
        for (Class<?> clazz : classes) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(clazz.getName());
        }
        this.types = sb.toString();
        this.classes = classes;
    }

    TypedMarshallerImpl(boolean single, String types) {
        if (types == null)
            throw new NullPointerException();
        this.single = single;
        this.types = types;
    }

    @Override
    public String getTypes() {
        return types;
    }

    @Override
    public Class<?>[] getClasses(ClassLoader cl) throws InvalidClassException {
        Class<?>[] classes = this.classes;
        if (classes != null)
            return classes;
        return getClassesWithExplicitLoaderSync(cl);
    }

    @Override
    public boolean supportsNullObject() {
        return single;
    }

    @Override
    public boolean representsNullObjectAsNullBytes() {
        return false;
    }

    private synchronized Class<?>[] getClassesWithExplicitLoaderSync(ClassLoader cl) throws InvalidClassException {
        Class<?>[] classes = this.classes;
        if (classes != null)
            return classes;
        ArrayList<String> typeList = new ArrayList<>();
        if (!types.isEmpty()) {
            int i = 0;
            int j;
            while ((j = types.indexOf(',', i)) != -1) {
                typeList.add(types.substring(i, j));
                i = j + 1;
            }
            typeList.add(types.substring(i));
        }
        if (single && typeList.size() != 1)
            throw new IllegalArgumentException("Must have a single type name, but found '" + types + "'");
        classes = new Class<?>[typeList.size()];
        cl = ClassUtil.resolveContextClassLoader(cl);
        for (int i = 0; i < classes.length; i++) {
            try {
                classes[i] = ClassUtil.getTypeClass(typeList.get(i), cl);
            } catch (ClassNotFoundException e) {
                throw new InvalidClassException("Class not found: " + e.getMessage());
            }
        }
        this.classes = classes; // volatile write only after all resolved
        return classes;
    }

    private Class<?>[] getClassesWithImplicitLoader(Object[] objects) throws InvalidClassException {
        Class<?>[] classes = this.classes;
        if (classes != null)
            return classes;
        return getClassesWithImplicitLoaderSync(objects);
    }

    private synchronized Class<?>[] getClassesWithImplicitLoaderSync(Object[] objects) throws InvalidClassException {
        Class<?>[] classes = this.classes;
        if (classes != null)
            return classes;
        ClassLoader cl = null;
        for (Object object : objects) {
            if (object != null) {
                cl = object.getClass().getClassLoader();
                if (cl != null)
                    break;
            }
        }
        return getClassesWithExplicitLoaderSync(cl);
    }

    @Override
    public void writeObjectTo(BufferedOutput out, T object) throws IOException {
        // wrap to an array of objects when single or cast to use IOUtil methods that are array-based
        Object[] objects = single ? new Object[]{object} : (Object[]) object;
        ObjectSerializer.writeBodiesWithTypes(out, getClassesWithImplicitLoader(objects), objects);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T readObjectFrom(BufferedInput in, int length, SerialClassContext serialContext) throws IOException {
        if (serialContext == null)
            throw new NullPointerException();
        Object[] objects = ObjectDeserializer.readBodiesWithTypes(in, length, getClasses(serialContext.getClassLoader()), serialContext);
        // unwrap from an array of object when single
        return single ? (T) objects[0] : (T) objects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TypedMarshallerImpl))
            return false;
        TypedMarshallerImpl<?> that = (TypedMarshallerImpl<?>) o;
        return single == that.single && types.equals(that.types);
    }

    @Override
    public int hashCode() {
        return (single ? 1 : 0) + 2 * types.hashCode();
    }

    @Override
    public String toString() {
        return "TypedMarshaller{" +
            "single=" + single +
            ", types='" + types + '\'' +
            '}';
    }
}
