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

import com.dxfeed.annotation.EventFieldMapping;
import com.dxfeed.annotation.EventFieldType;
import com.dxfeed.annotation.EventTypeMapping;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleEventDelegateImpl;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.MarketEventDelegateImpl;
import com.dxfeed.event.market.OrderBase;
import com.dxfeed.event.market.OrderBaseDelegateImpl;
import com.dxfeed.event.market.TradeBase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.dxfeed.api.codegen.CodeGenUtils.emptyToDefault;

@SupportedAnnotationTypes("com.dxfeed.annotation.EventTypeMapping")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ImplCodeGenAnnotationProcessor extends AbstractProcessor {
    private Filer filer;
    private Types types;
    private Elements elements;

    private CodeGenEnvironment env;
    private CodeGenContext ctx;
    private AnnotationProcessorTypeFactory typeFactory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Log.setLogger(new Log.MessagerLogger(processingEnv.getMessager()));
        filer = processingEnv.getFiler();
        types = processingEnv.getTypeUtils();
        elements = processingEnv.getElementUtils();

        env = new AnnotationProcessorEnvironment(filer);
        ctx = new CodeGenContext(env);
        typeFactory = new AnnotationProcessorTypeFactory(elements, types);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> processedElements = roundEnv.getElementsAnnotatedWith(EventTypeMapping.class);
        if (processedElements.isEmpty())
            return false;
        try {
            for (Element element : processedElements) {
                if (element.getKind() != ElementKind.CLASS) {
                    Log.error("Only classes may be annotated by EventTypeMapping.", element);
                    continue;
                }
                TypeElement classElement = (TypeElement) element;
                CodeGenType classType = typeFactory.asType(classElement);
                if (!classType.isAssignableTo(EventType.class)) {
                    Log.error("Only subtypes of EventType may be annotated by EventTypeMapping.", classType);
                    continue;
                }
                String className = classType.getClassName().getSimpleName();

                EventTypeMapping eventTypeMapping = classElement.getAnnotation(EventTypeMapping.class);
                String recordName = emptyToDefault(eventTypeMapping.recordName(), className);

                DelegateGen delegate = ctx.delegate(className, classType, className, recordName);

                ClassName delegateSuperclass = resolveDelegateSuperclass(classType);
                delegate.inheritDelegateFrom(delegateSuperclass);
                delegate.innerDelegate(resolveInnerDelegate(classType));
                delegate.inheritMappingFrom(resolveMappingSuperclass(classType));
                if (delegateSuperclass.equals(new ClassName(OrderBaseDelegateImpl.class)))
                    delegate.source("m.getRecordSource()");

                Map<String, CodeGenExecutable> getters = new HashMap<>();
                Map<String, CodeGenExecutable> setters = new HashMap<>();
                Set<String> properties = new HashSet<>();

                for (CodeGenExecutable method : classType.getDeclaredExecutables()) {
                    MethodType methodType = classifyMethod(method);
                    if (method.getAnnotation(EventFieldMapping.class) != null && methodType != MethodType.GETTER) {
                        Log.error("Only getters may be annotated by EventFieldMapping.", method);
                        continue;
                    }
                    if (methodType == MethodType.UNDEFINED)
                        continue;
                    String methodName = method.getName();
                    String property = methodName.startsWith("is") ? methodName.substring(2) : methodName.substring(3);
                    properties.add(property);
                    switch (methodType) {
                    case GETTER:
                        getters.put(property, method);
                        break;
                    case SETTER:
                        setters.put(property, method);
                        break;
                    }
                }

                for (String property : properties) {
                    CodeGenExecutable getter = getters.get(property);
                    if (getter == null || !setters.containsKey(property)) {
                        if (getter != null && getter.getAnnotation(EventFieldMapping.class) != null)
                            Log.error("Annotated getter does not have corresponding setter.", getter);
                        continue;
                    }
                    Optional<EventFieldMapping> fieldMapping = Optional.ofNullable(getter.getAnnotation(EventFieldMapping.class));
                    String fieldName = fieldMapping.map(mapping -> emptyToDefault(mapping.fieldName(), null)).orElse(property);
                    EventFieldType mappingFieldType = fieldMapping
                        .map(EventFieldMapping::type)
                        .filter(type -> type != EventFieldType.DEFAULT)
                        .orElse(resolveFieldType(getter));

                    if (mappingFieldType == null) {
                        Log.error("Failed to resolve property type automatically." +
                            " Please specify the type explicitly with EventFieldMapping annotation.", getter);
                        continue;
                    }
                    if (mappingFieldType == EventFieldType.TRANSIENT) {
                        Log.info("Property marked as transient. Skipping it.", getter);
                        continue;
                    }

                    FieldType fieldType = mapToFieldType(mappingFieldType);
                    if (fieldType == null) {
                        Log.error("Failed to resolve serialized form of field", getter);
                        continue;
                    }
                    if (!fieldType.mapper.hasMapping(getter.getReturnType())) {
                        Log.error("Cannot map " + getter.getReturnType() + " to " + mappingFieldType, getter);
                        continue;
                    }

                    delegate.map(property, fieldName, fieldType);
                    if (fieldMapping.map(EventFieldMapping::optional).orElse(false))
                        delegate.optional();
                }
                delegate.publishable();
                ctx.factory(classType.getClassName().getPackageName()).useMarketEventSymbolsToGetBaseRecordName();
            }

            if (!roundEnv.errorRaised()) {
                ctx.generateSources();
                ctx.generateResources();
            }
        } catch (Exception ex) {
            Log.error("Exception thrown", null, ex);
        }
        return true;
    }

    private ClassName resolveInnerDelegate(CodeGenType eventType) {
        CodeGenType superclass = eventType.getSuperclass();
        if (superclass.isSameType(Object.class) ||
            superclass.isSameType(MarketEvent.class) ||
            superclass.isSameType(TradeBase.class) ||
            superclass.isSameType(OrderBase.class))
            return null;
        return NamingConventions.buildDelegateName(superclass.getClassName());
    }

    private ClassName resolveDelegateSuperclass(CodeGenType eventType) {
        if (eventType.isAssignableTo(OrderBase.class))
            return new ClassName(OrderBaseDelegateImpl.class);
        if (eventType.isAssignableTo(MarketEvent.class))
            return new ClassName(MarketEventDelegateImpl.class);
        if (eventType.isAssignableTo(Candle.class))
            return new ClassName(CandleEventDelegateImpl.class);
        return new ClassName(EventDelegate.class);
    }

    private ClassName resolveMappingSuperclass(CodeGenType eventType) {
        CodeGenType superclass = eventType.getSuperclass();
        if (superclass.isSameType(Object.class))
            return null;
        return NamingConventions.buildMappingName(superclass.getClassName());
    }

    private static MethodType classifyMethod(CodeGenExecutable method) {
        String name = method.getName();
        int params = method.getParameters().size();
        CodeGenType returnType = method.getReturnType();
        if ((name.startsWith("get") || name.startsWith("is")) && params == 0 && !returnType.isSameType(void.class))
            return MethodType.GETTER;
        if (name.startsWith("set") && params == 1 && returnType.isSameType(void.class))
            return MethodType.SETTER;
        return MethodType.UNDEFINED;
    }

    private EventFieldType resolveFieldType(CodeGenExecutable getter) {
        if (getter.isOverriding())
            return EventFieldType.TRANSIENT;
        CodeGenType returnType = getter.getReturnType();
        if (returnType.isPrimitive()) {
            if (returnType.isSameType(char.class))
                return EventFieldType.CHAR;
            if (returnType.isSameType(double.class) || returnType.isSameType(float.class) || returnType.isSameType(long.class))
                return EventFieldType.DECIMAL;
            return EventFieldType.INT;
        }
        if (returnType.isSameType(String.class))
            return EventFieldType.STRING;
        return EventFieldType.MARSHALLED;
    }

    private FieldType mapToFieldType(EventFieldType fieldSerializedType) {
        switch (fieldSerializedType) {
        case CHAR: return FieldType.CHAR;
        case DATE: return FieldType.DATE;
        case DECIMAL: return FieldType.DECIMAL_AS_DOUBLE;
        case INT: return FieldType.INT;
        case LONG: return FieldType.LONG;
        case SHORT_STRING: return FieldType.SHORT_STRING;
        //noinspection deprecation
        case TIME: return FieldType.TIME_SECONDS;
        case TIME_SECONDS: return FieldType.TIME_SECONDS;
        case TIME_MILLIS: return FieldType.TIME_MILLIS;
        case STRING: return FieldType.STRING;
        case MARSHALLED: return FieldType.MARSHALLED;
        }
        // should not enter here
        return null;
    }


    private enum MethodType {
        GETTER, SETTER, UNDEFINED
    }
}
