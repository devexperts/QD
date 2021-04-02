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
package com.dxfeed.api.codegen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class JavaClassType extends BaseCodeGenType implements CodeGenType {
    private final Class<?> cls;
    private ClassName className;

    JavaClassType(Class<?> cls) {
        this.cls = cls;
    }

    @Override
    public CodeGenExecutable getMethod(String name) {
        try {
            return new JavaExecutable(cls.getMethod(name));
        } catch (NoSuchMethodException e1) {
            // public method not found - search for package-private in this class and all superclasses
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    return new JavaExecutable(c.getDeclaredMethod(name));
                } catch (NoSuchMethodException e) {
                    // Do nothing - continue with a superclass.
                }
            }
            return null;
        }
    }

    @Override
    public Collection<CodeGenExecutable> getDeclaredExecutables() {
        List<CodeGenExecutable> result = new ArrayList<>();
        for (Constructor<?> constructor : cls.getConstructors())
            result.add(new JavaExecutable(constructor));
        for (Method method : cls.getDeclaredMethods())
            result.add(new JavaExecutable(method));
        return result;
    }

    @Override
    public boolean isAssignableTo(Class<?> cls) {
        return cls.isAssignableFrom(this.cls);
    }

    @Override
    public boolean isSameType(Class<?> cls) {
        return cls == this.cls;
    }

    @Override
    public ClassName getClassName() {
        if (className == null)
            className = new ClassName(cls);
        return className;
    }

    @Override
    public CodeGenType getSuperclass() {
        Class<?> superclass = cls.getSuperclass();
        return superclass == null ? null : new JavaClassType(superclass);
    }

    @Override
    public boolean isPrimitive() {
        return cls.isPrimitive();
    }

    @Override
    public Object getUnderlyingType() {
        return cls;
    }

    private class JavaExecutable implements CodeGenExecutable {
        private final Executable executable;

        private JavaExecutable(Executable executable) {
            this.executable = executable;
        }

        @Override
        public String getName() {
            return executable.getName();
        }

        @Override
        public boolean isOverriding() {
            return executable.getDeclaringClass() != cls;
        }

        @Override
        public List<CodeGenType> getParameters() {
            return Arrays.stream(executable.getParameterTypes())
                .map(JavaClassType::new)
                .collect(Collectors.toList());
        }

        @Override
        public CodeGenType getReturnType() {
            if (executable instanceof Method)
                return new JavaClassType(((Method) executable).getReturnType());
            else
                return new JavaClassType(executable.getDeclaringClass());
        }

        @Override
        public String generateCall(String instance, String... values) {
            String params = Arrays.stream(values).collect(Collectors.joining(", ", "(", ")"));
            String implClass = cls.getSimpleName();
            if (executable instanceof Constructor)
                return "new " + implClass + params;
            if (Modifier.isStatic(executable.getModifiers()))
                return implClass + "." + getName() + params;
            return instance + "." + getName() + params;
        }

        @Override
        public boolean isInstanceMethod() {
            return executable instanceof Method && !Modifier.isStatic(executable.getModifiers());
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return executable.getAnnotation(annotationClass);
        }

        @Override
        public Object getUnderlyingExecutable() {
            return executable;
        }
    }
}
