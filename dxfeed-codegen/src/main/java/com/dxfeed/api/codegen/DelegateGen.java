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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.impl.SchemeFieldTime;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.dxfeed.api.impl.SchemeFieldTime.COMMON_FIELD;
import static com.dxfeed.api.impl.SchemeFieldTime.FIRST_TIME_INT_FIELD;
import static com.dxfeed.api.impl.SchemeFieldTime.SECOND_TIME_INT_FIELD;

/**
 * Generates xxxDelegate classes for events.
 */
class DelegateGen {
    private final ClassName className;
    private final CodeGenType eventClass;
    private final String eventName;
    private final RecordDesc record;
    private final FactoryImplGen factoryGen;
    private final MappingGen mappingGen;
    private final CodeGenEnvironment env;

    private final List<FieldMapping> fieldMappings = new ArrayList<>();
    private final List<GetPutEventCodeGenerator> codeGenerators = new ArrayList<>();

    private ClassName superDelegateClassName;
    private ClassName innerDelegateClassName;
    private String source;
    private QDContract subContract;
    private boolean publishable;

    DelegateGen(ClassName delegateName, CodeGenType eventClass, RecordDesc record,
        FactoryImplGen factoryGen, MappingGen mappingGen, CodeGenEnvironment env)
    {
        this.className = delegateName;
        this.eventClass = eventClass;
        this.eventName = eventClass == null ? null : eventClass.getClassName().getSimpleName();
        this.record = record;
        this.factoryGen = factoryGen;
        this.mappingGen = mappingGen;
        this.env = env;
    }

    ClassName getClassName() {
        return className;
    }

    Map<QDContract, EnumSet<EventDelegateFlags>> getContractFlags() {
        Map<QDContract, EnumSet<EventDelegateFlags>> result = new EnumMap<>(QDContract.class);
        boolean lasting = eventClass.isAssignableTo(LastingEvent.class);
        boolean timeSeries = eventClass.isAssignableTo(TimeSeriesEvent.class);
        boolean indexedOnly = !timeSeries && eventClass.isAssignableTo(IndexedEvent.class);
        QDContract primaryContract =
            subContract != null ? subContract :
            indexedOnly ? QDContract.HISTORY :
            lasting || timeSeries ? QDContract.TICKER : QDContract.STREAM;
        EnumSet<EventDelegateFlags> primaryFlags = EnumSet.of(EventDelegateFlags.SUB);
        if (publishable)
            primaryFlags.add(EventDelegateFlags.PUB);
        result.put(primaryContract, primaryFlags);
        if (publishable && primaryContract != QDContract.STREAM)
            result.put(QDContract.STREAM, EnumSet.of(EventDelegateFlags.PUB));
        if (timeSeries && primaryContract != QDContract.HISTORY) {
            EnumSet<EventDelegateFlags> flags = EnumSet.of(EventDelegateFlags.SUB, EventDelegateFlags.TIME_SERIES);
            if (publishable)
                flags.add(EventDelegateFlags.PUB);
            result.put(QDContract.HISTORY, flags);
        }
        // always support wildcard subscription via stream
        if (!result.containsKey(QDContract.STREAM))
            result.put(QDContract.STREAM, EnumSet.noneOf(EventDelegateFlags.class));
        result.get(QDContract.STREAM).add(EventDelegateFlags.WILDCARD);
        return result;
    }

    DelegateGen suffixes(String suffixesDefault) {
        if (record.regional)
            throw new AssertionError("Suffixes are not supported for regional records");
        record.suffixesDefault = suffixesDefault;
        return this;
    }

    DelegateGen exchanges(String exchangesDefault, boolean regionalOnly) {
        record.exchangesDefault = exchangesDefault;
        record.regionalOnly = regionalOnly;
        return this;
    }

    DelegateGen inheritDelegateFrom(ClassName superDelegateClassName) {
        this.superDelegateClassName = superDelegateClassName;
        return this;
    }

    DelegateGen innerDelegate(ClassName innerDelegateClassName) {
        this.innerDelegateClassName = innerDelegateClassName;
        return this;
    }

    DelegateGen inheritMappingFrom(ClassName superMappingClassName) {
        mappingGen.inheritMappingFrom(superMappingClassName);
        return this;
    }

    DelegateGen source(String source) {
        this.source = source;
        return this;
    }

    DelegateGen injectGetEventCode(String... code) {
        codeGenerators.add(new GetEventCodeInjection(code));
        return this;
    }

    DelegateGen injectPutEventCode(String... code) {
        codeGenerators.add(new PutEventCodeInjection(code));
        return this;
    }

    DelegateGen assign(String property, String value) {
        codeGenerators.add(new FieldAssignment(property, value));
        return this;
    }

    DelegateGen map(String eventPropertyName, String fieldPropertyName, String fieldName, FieldType fieldType) {
        String getterName =
            Stream.of("get" + eventPropertyName + "AsDouble", "get" + eventPropertyName, "is" + eventPropertyName)
            .filter(name -> eventClass.getMethod(name) != null)
            .findFirst().orElse(null);
        // TODO search for appropriate setter below
        String setterName = getterName == null ? null :
            getterName.startsWith("get") ? "set" + getterName.substring(3) :
                getterName.startsWith("is") ? "set" + getterName.substring(2) : null;
        CodeGenType eventPropertyType = getterName != null ? eventClass.getMethod(getterName).getReturnType() : null;
        FieldMapping fieldMapping = new FieldMapping(eventPropertyName, eventPropertyType,
            new RecordField(fieldPropertyName, eventName, fieldName, fieldType),
            getterName, setterName);
        fieldMappings.add(fieldMapping);
        codeGenerators.add(fieldMapping);
        factoryGen.addRecordField(record, fieldMapping.field);
        mappingGen.addRecordField(fieldMapping.field);
        return this;
    }

    DelegateGen map(String eventPropertyName, String fieldName, FieldType fieldType) {
        return map(eventPropertyName, eventPropertyName, fieldName, fieldType);
    }

    DelegateGen map(String name, FieldType fieldType) {
        return map(name, name, name, fieldType);
    }

    DelegateGen field(String fieldPropertyName, String fieldName, FieldType fieldType) {
        FieldMapping fieldMapping = new FieldMapping(
            null, null, new RecordField(fieldPropertyName, eventName, fieldName, fieldType), null, null);
        fieldMappings.add(fieldMapping);
        factoryGen.addRecordField(record, fieldMapping.field);
        mappingGen.addRecordField(fieldMapping.field);
        return this;
    }

    DelegateGen field(String fieldName, FieldType fieldType) {
        return field(AbstractDataField.getDefaultPropertyName(fieldName), fieldName, fieldType);
    }

    DelegateGen phantom(String phantomProperty) {
        if (fieldMappings.isEmpty()) {
            // Phantom record
            record.phantomProperty = phantomProperty;
            mappingGen.phantom = true;
        } else {
            // Phantom field
            RecordField field = lastFieldMapping().field;
            field.conditionalProperty = phantomProperty;
            field.isPhantom = true;
            field.required = false;
            field.enabled = false;
        }
        return this;
    }

    // implies optional
    DelegateGen onlyIf(String conditionalProperty) {
        // Conditional field
        RecordField field = lastFieldMapping().field;
        field.conditionalProperty = conditionalProperty;
        field.isPhantom = false;
        field.required = false;
        field.enabled = false;
        return this;
    }

    DelegateGen internal() {
        lastFieldMapping().internal = true;
        return this;
    }

    DelegateGen optional() {
        lastFieldMapping().field.required = false;
        return this;
    }

    DelegateGen prevOptional() {
        prevFieldMapping().field.required = false;
        return this;
    }

    DelegateGen disabledByDefault() {
        if (lastFieldMapping().field.required)
            throw new IllegalStateException("Only optional fields can be disabled by default");
        lastFieldMapping().field.enabled = false;
        return this;
    }

    DelegateGen alt(String alt) {
        lastFieldMapping().field.alt = alt;
        return this;
    }

    // implies optional
    DelegateGen onlySuffixes(String suffixesProperty, String suffixesDefault) {
        FieldMapping fm = lastFieldMapping();
        if (record.suffixesDefault == null)
            throw new AssertionError("Record should have suffixes");
        fm.field.onlySuffixesProperty = suffixesProperty;
        fm.field.onlySuffixesDefault = suffixesDefault;
        fm.field.required = false;
        fm.field.enabled = false;
        return this;
    }

    // implies optional
    DelegateGen exceptSuffixes(String suffixes) {
        FieldMapping fm = lastFieldMapping();
        if (record.suffixesDefault == null)
            throw new AssertionError("Record should have suffixes");
        fm.field.exceptSuffixes = suffixes;
        fm.field.required = false;
        fm.field.enabled = false;
        return this;
    }

    DelegateGen voidForSuffixes(String suffixes) {
        lastFieldMapping().field.voidSuffixes = suffixes;
        return this;
    }

    DelegateGen time(int timeness) {
        lastFieldMapping().setTimeness(timeness);
        return this;
    }

    DelegateGen compositeOnly() {
        lastFieldMapping().field.isCompositeOnly = true;
        return this;
    }

    DelegateGen subContract(QDContract contract) {
        this.subContract = contract;
        return this;
    }

    DelegateGen withPlainEventFlags() {
        injectGetEventCode("event.setEventFlags(cursor.getEventFlags());");
        injectPutEventCode("cursor.setEventFlags(event.getEventFlags());");
        return this;
    }

    DelegateGen publishable() {
        publishable = true;
        return this;
    }

    DelegateGen mapTimeAndSequence() {
        return mapTimeAndSequence("Time", "Sequence");
    }

    DelegateGen mapTimeAndSequence(String timeFieldName, String sequenceFieldName) {
        map("Time", timeFieldName, FieldType.TIME_SECONDS).internal();
        map("Sequence", sequenceFieldName, FieldType.SEQUENCE).internal();
        assign("TimeSequence", "(((long) #Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)");
        injectPutEventCode(
            "#Time.Seconds=(int) (event.getTimeSequence() >>> 32)#;",
            "#Sequence=(int) event.getTimeSequence()#;"
        );
        return this;
    }

    DelegateGen mapTimeAndSequenceToIndex() {
        return mapTimeAndSequenceToIndex("Time", "Sequence");
    }

    DelegateGen mapTimeAndSequenceToIndex(String timeFieldName, String sequenceFieldName) {
        map("Time", timeFieldName, FieldType.TIME_SECONDS).time(0).internal();
        map("Sequence", sequenceFieldName, FieldType.SEQUENCE).time(1).internal();
        assign("Index", "(((long) #Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)");
        injectPutEventCode(
            "#Time.Seconds=(int) (event.getIndex() >>> 32)#;",
            "#Sequence=(int) event.getIndex()#;"
        );
        return this;
    }

    private FieldMapping lastFieldMapping() {
        return fieldMappings.get(fieldMappings.size() - 1);
    }

    private FieldMapping prevFieldMapping() {
        return fieldMappings.get(fieldMappings.size() - 2);
    }

    void generate() throws IOException {
        if (className == null)
            return;
        ClassGen cg = ClassGen.resolve(className, "TemplateDelegate.java", env);
        if (superDelegateClassName != null) {
            cg.setVariableValue("SUPER_CLASS", superDelegateClassName.getSimpleName() + "<" + eventName + ">");
            cg.addImport(superDelegateClassName);
        } else {
            cg.setVariableValue("SUPER_CLASS", "EventDelegate<" + eventName + ">");
            cg.addImport(new ClassName(EventDelegate.class));
        }
        cg.addImport(eventClass.getClassName());
        cg.addImport(mappingGen.getClassName());
        cg.code("private final " + mappingGen.getClassName().getSimpleName() + " m;");
        if (innerDelegateClassName != null) {
            cg.addImport(innerDelegateClassName);
            cg.code("private final " + innerDelegateClassName.getSimpleName() + " d;");
        }
        generateDelegateConstructorCode(cg);
        generateGetMappingCode(cg);
        generateCreateEventCode(cg);
        generateGetEventCode(cg);
        if (publishable)
            generatePutEventCode(cg);
        if (source != null)
            generateGetSource(cg);
        env.writeSourceFile(className, cg.buildSource());
    }

    private void generateDelegateConstructorCode(ClassGen cg) {
        cg.newLine();
        cg.addImport(new ClassName(DataRecord.class));
        cg.addImport(new ClassName(QDContract.class));
        cg.addImport(new ClassName(EnumSet.class));
        cg.addImport(new ClassName(EventDelegateFlags.class));
        cg.code("public " + className.getSimpleName() +
            "(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {");
        cg.indent();
        cg.code("super(record, contract, flags);");
        cg.addImport(mappingGen.getClassName());
        cg.code("m = record.getMapping(" + mappingGen.getClassName().getSimpleName() + ".class);");
        if (innerDelegateClassName != null)
            cg.code("d = new " + innerDelegateClassName.getSimpleName() + "(record, contract, flags);");
        cg.unindent();
        cg.code("}");
    }

    private void generateGetMappingCode(ClassGen cg) {
        cg.newLine();
        cg.code("@Override");
        cg.code("public " + mappingGen.getClassName().getSimpleName() + " getMapping() {");
        cg.indent();
        cg.code("return m;");
        cg.unindent();
        cg.code("}");
    }

    private void generateGetEventCode(ClassGen cg) {
        cg.newLine();
        cg.code("@Override");
        cg.code("public " + eventName + " getEvent(" + eventName + " event, RecordCursor cursor) {");
        cg.indent();
        if (innerDelegateClassName != null) {
            cg.code("d.getEvent(event, cursor);");
        } else {
            cg.code("super.getEvent(event, cursor);");
        }
        for (GetPutEventCodeGenerator codeGenerator : codeGenerators) {
            codeGenerator.generateGetEventCodePiece(cg);
        }
        cg.code("return event;");
        cg.unindent();
        cg.code("}");
    }

    private void generateCreateEventCode(ClassGen cg) {
        cg.newLine();
        cg.code("@Override");
        cg.code("public " + eventName + " createEvent() {");
        cg.indent();
        cg.code("return new " + eventName + "();");
        cg.unindent();
        cg.code("}");
    }

    private void generatePutEventCode(ClassGen cg) {
        cg.newLine();
        cg.addImport(new ClassName(RecordCursor.class));
        cg.addImport(new ClassName(RecordBuffer.class));
        cg.code("@Override");
        cg.code("public RecordCursor putEvent(" + eventName + " event, RecordBuffer buf) {");
        cg.indent();
        if (innerDelegateClassName != null) {
            cg.code("RecordCursor cursor = d.putEvent(event, buf);");
        } else {
            cg.code("RecordCursor cursor = super.putEvent(event, buf);");
        }
        for (GetPutEventCodeGenerator codeGenerator : codeGenerators) {
            codeGenerator.generatePutEventCodePiece(cg);
        }
        cg.code("return cursor;");
        cg.unindent();
        cg.code("}");
    }

    private void generateGetSource(ClassGen cg) {
        cg.newLine();
        cg.addImport(new ClassName(IndexedEventSource.class));
        cg.code("@Override");
        cg.code("public IndexedEventSource getSource() {");
        cg.indent();
        cg.code("return " + source + ";");
        cg.unindent();
        cg.code("}");
    }

    private String mksubst(String value) {
        while (true) {
            int i = value.indexOf('#');
            if (i < 0)
                break;
            int j = value.indexOf('#', i + 1);
            if (j < 0)
                break;
            String name = value.substring(i + 1, j);
            String assign = null;
            int k = name.indexOf('=');
            if (k >= 0) {
                assign = name.substring(k + 1);
                name = name.substring(0, k);
            }
            String suffix = "";
            k = name.lastIndexOf('.');
            if (k >= 0) {
                suffix = name.substring(k + 1);
                name = name.substring(0, k);
            }
            FieldMapping m = findMappingByProperty(name);
            String replace = assign == null ?
                "m.get" + m.field.propertyName + suffix + "(cursor)" :
                "m.set" + m.field.propertyName + suffix + "(cursor, " + assign + ")";
            value = value.substring(0, i) + replace + value.substring(j + 1);
        }
        return value;
    }

    private FieldMapping findMappingByProperty(String name) {
        for (FieldMapping mapping : fieldMappings) {
            if (name.equals(mapping.eventPropertyName))
                return mapping;
        }
        throw new IllegalArgumentException("Mapping for field " + name + " is not found");
    }

    private abstract static class GetPutEventCodeGenerator {
        GetPutEventCodeGenerator() {
        }

        abstract void generateGetEventCodePiece(ClassGen cg);

        abstract void generatePutEventCodePiece(ClassGen cg);
    }

    private class GetEventCodeInjection extends GetPutEventCodeGenerator {
        final String[] lines;

        GetEventCodeInjection(String... lines) {
            this.lines = lines;
        }

        @Override
        void generateGetEventCodePiece(ClassGen cg) {
            for (String line : lines) {
                cg.code(mksubst(line));
            }
        }

        @Override
        void generatePutEventCodePiece(ClassGen cg) {
        }
    }

    private class PutEventCodeInjection extends GetPutEventCodeGenerator {
        final String[] lines;

        PutEventCodeInjection(String... lines) {
            this.lines = lines;
        }

        @Override
        void generateGetEventCodePiece(ClassGen cg) {
        }

        @Override
        void generatePutEventCodePiece(ClassGen cg) {
            for (String line : lines) {
                cg.code(mksubst(line));
            }
        }
    }

    private class FieldAssignment extends GetPutEventCodeGenerator {
        final String property;
        final String value;

        FieldAssignment(String property, String value) {
            this.property = property;
            this.value = value;
        }

        @Override
        void generateGetEventCodePiece(ClassGen cg) {
            cg.code("event.set" + property + "(" + mksubst(value) + ");");
        }

        @Override
        void generatePutEventCodePiece(ClassGen cg) {
        }
    }

    private static class FieldMapping extends GetPutEventCodeGenerator {
        final String eventPropertyName;
        final CodeGenType eventPropertyType;
        final RecordField field;
        final String getterName;
        final String setterName;

        boolean internal;

        FieldMapping(String eventPropertyName, CodeGenType eventPropertyType, RecordField field, String getterName,
            String setterName)
        {
            this.eventPropertyName = eventPropertyName;
            this.eventPropertyType = eventPropertyType;
            this.field = field;
            this.getterName = getterName;
            this.setterName = setterName;
        }

        void setTimeness(int timenessId) {
            if (timenessId < 0 || timenessId > 1)
                throw new IllegalArgumentException();
            SchemeFieldTime time = (timenessId == 0 ? FIRST_TIME_INT_FIELD : SECOND_TIME_INT_FIELD);
            if (field.time != COMMON_FIELD && field.time != time)
                throw new IllegalArgumentException("Timeness mismatches for field \"" + field + "\"");
            field.time = time;
        }

        @Override
        void generateGetEventCodePiece(ClassGen cg) {
            if (internal)
                return;
            FieldType.TypeMapper mapper = field.fieldType.mapper;
            String getter = mapper.generateGetter(eventPropertyType, "m.get" + field.propertyName + "%s(cursor)");
            mapper.configureImports(cg, eventPropertyType);
            cg.code("event." + setterName + "(" + getter + ");");
        }

        @Override
        void generatePutEventCodePiece(ClassGen cg) {
            if (internal)
                return;
            String value = "event." + getterName + "()";
            FieldType.TypeMapper mapper = field.fieldType.mapper;
            mapper.configureImports(cg, eventPropertyType);
            cg.code(mapper.generateSetter(eventPropertyType, "m.set" + field.propertyName + "%s(cursor, %s);", value));
        }
    }
}
