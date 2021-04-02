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

import com.dxfeed.annotation.ClassValueMapping;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class ClassValueMappingRegistry {
    private static final String ERROR_STRING = "ClassValueMapping annotation may only be assigned to constructors with 1 parameter, " +
        "static non-void methods with 1 parameter and non-void instance methods without parameters.";
    private static final ClassValueMappingRegistry INSTANCE = new ClassValueMappingRegistry();

    private final Map<CodeGenType, Entry> entries = new HashMap<>();

    private ClassValueMappingRegistry() {
    }

    static ClassValueMappingRegistry getInstance() {
        return INSTANCE;
    }

    String serialize(CodeGenType type, CodeGenType serializedType, String value) {
        Entry entry = entries.computeIfAbsent(type, this::buildEntry);
        return generateCall(entry.serializers.get(serializedType), value);
    }

    String deserialize(CodeGenType type, CodeGenType serializedType, String value) {
        Entry entry = entries.computeIfAbsent(type, this::buildEntry);
        return generateCall(entry.deserializers.get(serializedType), value);
    }

    boolean canSerialize(CodeGenType type, CodeGenType serializedType) {
        Entry entry = entries.computeIfAbsent(type, this::buildEntry);
        return entry.serializers.containsKey(serializedType) && entry.deserializers.containsKey(serializedType);
    }

    private String generateCall(CodeGenExecutable mapper, String value) {
        if (mapper.isInstanceMethod())
            return mapper.generateCall(value);
        else
            return mapper.generateCall(null, value);
    }

    private Entry buildEntry(CodeGenType type) {
        Map<CodeGenType, CodeGenExecutable> serializers = new HashMap<>();
        Map<CodeGenType, CodeGenExecutable> deserializers = new HashMap<>();
        for (CodeGenExecutable executable : type.getDeclaredExecutables()) {
            ClassValueMapping mapping = executable.getAnnotation(ClassValueMapping.class);
            if (mapping == null)
                continue;
            MethodType methodType = resolveMethodType(type, executable);
            if (methodType == MethodType.UNDEFINED)
                continue;
            CodeGenType serializedType = methodType == MethodType.SERIALIZER ? executable.getReturnType() : executable.getParameters().get(0);
            if (!serializedType.isSameType(int.class)) {
                Log.error("Only mapping to int is supported, but " + serializedType + " found.", executable);
                continue;
            }
            CodeGenExecutable oldValue = methodType == MethodType.SERIALIZER
                ? serializers.put(serializedType, executable)
                : deserializers.put(serializedType, executable);
            if (oldValue != null) {
                Log.error("Multiple mappings to " + serializedType + " found. Previous was: " + oldValue.getName() + ".", executable);
            }
        }
        for (Iterator<CodeGenType> it = serializers.keySet().iterator(); it.hasNext(); ) {
            CodeGenType t = it.next();
            if (!deserializers.containsKey(t)) {
                Log.error("Deserializer for " + t + " not found.", type);
                it.remove();
            }
        }
        for (Iterator<CodeGenType> it = deserializers.keySet().iterator(); it.hasNext(); ) {
            CodeGenType t = it.next();
            if (!serializers.containsKey(t)) {
                Log.error("Serializer for " + t + " not found.", type);
                it.remove();
            }
        }
        return new Entry(serializers, deserializers);
    }

    private MethodType resolveMethodType(CodeGenType type, CodeGenExecutable executable) {
        if (executable.isInstanceMethod()) {
            if (executable.getParameters().isEmpty() && !executable.getReturnType().isSameType(void.class)) {
                return MethodType.SERIALIZER;
            } else {
                Log.error(ERROR_STRING, executable);
            }
        } else if (executable.getParameters().size() == 1) {
            CodeGenType from = executable.getParameters().get(0);
            CodeGenType to = executable.getReturnType();
            if (from.equals(to)) {
                Log.error("Mapping and mapped type must be different.", executable);
            } else if (from.equals(type)) {
                return MethodType.SERIALIZER;
            } else if (to.equals(type)) {
                return MethodType.DESERIALIZER;
            } else {
                Log.error("Either return type or parameter type must be " + type + ".", executable);
            }
        } else {
            Log.error(ERROR_STRING, executable);
        }
        return MethodType.UNDEFINED;
    }

    private static class Entry {
        final Map<CodeGenType, CodeGenExecutable> serializers;
        final Map<CodeGenType, CodeGenExecutable> deserializers;

        Entry(Map<CodeGenType, CodeGenExecutable> serializers, Map<CodeGenType, CodeGenExecutable> deserializers) {
            this.serializers = serializers;
            this.deserializers = deserializers;
        }
    }

    private enum MethodType {
        SERIALIZER, DESERIALIZER, UNDEFINED
    }
}
