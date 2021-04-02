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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

class AnnotationProcessorTypeFactory {
    private final Elements elements;
    private final Types types;

    AnnotationProcessorTypeFactory(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    CodeGenType asType(TypeElement element) {
        return new Type(element);
    }

    CodeGenType asType(TypeMirror typeMirror) {
        return new Type(typeMirror);
    }

    private class Type extends BaseCodeGenType implements CodeGenType {
        private final TypeMirror typeMirror;
        private final TypeElement element;
        private ClassName className;

        private Type(TypeMirror typeMirror) {
            this.typeMirror = types.erasure(typeMirror);
            this.element = (TypeElement) types.asElement(typeMirror);
        }

        private Type(TypeElement element) {
            this.typeMirror = types.erasure(element.asType());
            this.element = element;
        }

        @Override
        public boolean isPrimitive() {
            return element == null;
        }

        @Override
        public Object getUnderlyingType() {
            return element;
        }

        @Override
        public CodeGenExecutable getMethod(String name) {
            if (isPrimitive())
                return null;
            List<ExecutableElement> methods = ElementFilter.methodsIn(elements.getAllMembers(element));
            ExecutableElement method = methods.stream()
                .filter(exec -> exec.getSimpleName().contentEquals(name) && exec.getParameters().isEmpty())
                .findFirst().orElse(null);
            if (method == null)
                return null;
            return new Executable(method);
        }

        @Override
        public Collection<CodeGenExecutable> getDeclaredExecutables() {
            if (isPrimitive())
                return Collections.emptyList();
            return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR)
                .map(e -> new Executable((ExecutableElement) e))
                .collect(Collectors.toList());
        }

        private TypeMirror getTypeMirrorOfClass(Class<?> cls) {
            if (cls.isPrimitive()) {
                if (cls == void.class)
                    return types.getNoType(TypeKind.VOID);
                return types.getPrimitiveType(TypeKind.valueOf(cls.getName().toUpperCase()));
            }
            return types.erasure(elements.getTypeElement(cls.getCanonicalName()).asType());
        }

        @Override
        public boolean isAssignableTo(Class<?> cls) {
            return types.isAssignable(typeMirror, getTypeMirrorOfClass(cls));
        }

        @Override
        public boolean isSameType(Class<?> cls) {
            return types.isSameType(typeMirror, getTypeMirrorOfClass(cls));
        }

        @Override
        public ClassName getClassName() {
            if (className == null) {
                if (isPrimitive()) {
                    className = new ClassName(null, typeMirror.getKind().name().toLowerCase());
                } else {
                    className = new ClassName(element.getQualifiedName().toString());
                }
            }
            return className;
        }

        @Override
        public CodeGenType getSuperclass() {
            if (isPrimitive())
                return null;
            TypeMirror superclass = element.getSuperclass();
            return superclass.getKind() == TypeKind.NONE ? null : new Type((TypeElement) types.asElement(superclass));
        }
    }

    private class Executable implements CodeGenExecutable {
        private final ExecutableElement element;

        private Executable(ExecutableElement element) {
            this.element = element;
        }

        @Override
        public String getName() {
            return element.getSimpleName().toString();
        }

        private boolean hasMethod(TypeElement element, Predicate<ExecutableElement> predicate) {
            if (element.getEnclosedElements().stream()
                .anyMatch(e -> e.getKind() == ElementKind.METHOD && predicate.test((ExecutableElement) e)))
                return true;
            if (element.getSuperclass().getKind() != TypeKind.NONE &&
                hasMethod((TypeElement) types.asElement(element.getSuperclass()), predicate))
                return true;
            return element.getInterfaces().stream()
                .anyMatch(e -> hasMethod((TypeElement) types.asElement(e), predicate));
        }

        @Override
        public boolean isOverriding() {
            TypeElement implementingClass = (TypeElement) element.getEnclosingElement();
            return hasMethod(implementingClass, method -> elements.overrides(element, method, implementingClass));
        }

        @Override
        public List<CodeGenType> getParameters() {
            return element.getParameters().stream()
                .map(el -> asType(el.asType()))
                .collect(Collectors.toList());
        }

        @Override
        public CodeGenType getReturnType() {
            if (element.getKind() == ElementKind.CONSTRUCTOR)
                return asType((TypeElement) element.getEnclosingElement());
            return asType(element.getReturnType());
        }

        @Override
        public String generateCall(String instance, String... values) {
            String params = Arrays.stream(values).collect(Collectors.joining(", ", "(", ")"));
            String implClass = element.getEnclosingElement().getSimpleName().toString();
            if (element.getKind() == ElementKind.CONSTRUCTOR)
                return "new " + implClass + params;
            if (element.getModifiers().contains(Modifier.STATIC))
                return implClass + "." + getName() + params;
            return instance + "." + getName() + params;
        }

        @Override
        public boolean isInstanceMethod() {
            return !element.getModifiers().contains(Modifier.STATIC) && element.getKind() != ElementKind.CONSTRUCTOR;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return element.getAnnotation(annotationClass);
        }

        @Override
        public Object getUnderlyingExecutable() {
            return element;
        }

        @Override
        public String toString() {
            TypeElement implementingClass = (TypeElement) element.getEnclosingElement();
            return implementingClass.getQualifiedName() + "." + getName() +
                getParameters().stream()
                    .map(type -> type.getClassName().toString())
                    .collect(Collectors.joining(",", "(", ")"));
        }
    }
}
