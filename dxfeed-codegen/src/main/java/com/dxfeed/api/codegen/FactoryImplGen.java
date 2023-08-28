/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.impl.SchemeBuilder;
import com.dxfeed.api.impl.SchemeFieldTime;
import com.dxfeed.event.market.MarketEventSymbols;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates xxxEventDelegateFactory classes for event packages.
 */
class FactoryImplGen {
    private final ClassName className;
    private final CodeGenEnvironment env;
    private final Map<RecordDesc, Map<String, RecordField>> recordsFields = new LinkedHashMap<>();
    private final Map<RecordDesc, List<DelegateGen>> recordDelegates = new LinkedHashMap<>();
    private final Map<MappingGen, Set<RecordDesc>> mappingRecords = new LinkedHashMap<>();
    private boolean useMarketEventSymbol;

    FactoryImplGen(ClassName className, CodeGenEnvironment env) {
        this.className = className;
        this.env = env;
    }

    void generate() throws IOException {
        ClassGen cg = ClassGen.resolve(className, "TemplateFactoryImpl.java", env);
        generateBuildSchemeCode(cg);
        cg.newLine();
        generateCreateDelegatesCode(cg, false);
        cg.newLine();
        generateCreateDelegatesCode(cg, true);
        cg.newLine();
        generateCreateMappingCode(cg);
        if (useMarketEventSymbol) {
            cg.newLine();
            generateGenBaseRecordName(cg);
        }
        env.writeSourceFile(className, cg.buildSource());
    }

    private void generateBuildSchemeCode(ClassGen cg) {
        cg.addImport(new ClassName(SchemeBuilder.class));
        cg.code("@Override");
        cg.code("public void buildScheme(SchemeBuilder builder) {");
        cg.indent();
        boolean first = true;
        for (Map.Entry<RecordDesc, Map<String, RecordField>> recordEntry : recordsFields.entrySet()) {
            RecordDesc record = recordEntry.getKey();
            if (first) {
                first = false;
            } else {
                cg.newLine();
            }
            if (record.phantomProperty != null) {
                cg.addImport(new ClassName(SystemProperties.class));
                cg.code("if (SystemProperties.getBooleanProperty(\"" + record.phantomProperty + "\", false)) {");
                cg.indent();
            }
            if (record.regional) { // Composite and regional records
                //FIXME regionalOnly and exchangesDefault are used only for Book&I
                if (!record.regionalOnly)
                    generateFieldCode(cg, recordEntry.getValue(), "\"" + record + "\"", false); // Composite
                cg.addImport(new ClassName(SystemProperties.class));
                //FIXME regionalOnly and exchangesDefault are used only for Book&I
                if (record.exchangesDefault != null) {
                    cg.code("for (char exchange : SystemProperties.getProperty(" +
                        "\"" + record.exchangesProperty + "\", \"" + record.exchangesDefault + "\")"
                        + ".toCharArray()) {");
                } else {
                    cg.code("for (char exchange : getExchanges(\"" + record.exchangesProperty + "\")) {");
                }
                cg.indent();
                cg.code("String recordName = \"" + record + "&\" + exchange;");
                generateFieldCode(cg, recordEntry.getValue(), "recordName", true);
                cg.unindent();
                cg.code("}");
            } else if (record.suffixesDefault != null) { // Multiple records with different suffixes
                cg.addImport(new ClassName(SystemProperties.class));
                cg.code("for (String suffix : SystemProperties.getProperty(" +
                    "\"" + record.suffixesProperty + "\", " +
                    "\"" + record.suffixesDefault + "\").split(\"\\\\|\")) {");
                cg.indent();
                cg.code("String recordName = \"" + record + "\" + suffix;");
                generateFieldCode(cg, recordEntry.getValue(), "recordName", false);
                cg.unindent();
                cg.code("}");
            } else { // Simple single record
                generateFieldCode(cg, recordEntry.getValue(), "\"" + record + "\"", false);
            }
            if (record.phantomProperty != null) {
                cg.unindent();
                cg.code("}");
            }
        }
        cg.unindent();
        cg.code("}");
    }

    private void generateFieldCode(ClassGen cg, Map<String, RecordField> fields, String recordNameReference,
        boolean isRegional)
    {
        for (Map.Entry<String, RecordField> fieldEntry : fields.entrySet()) {
            String fieldName = fieldEntry.getKey();
            RecordField f = fieldEntry.getValue();
            if (isRegional && f.isCompositeOnly)
                continue;

            cg.addImport(new ClassName(SerialFieldType.class));
            for (FieldType.Field field : f.fieldType.fields) {
                String typeExpr = "SerialFieldType." + field.serialType;
                if (field.adaptiveType || field.typeSelectors.length != 0) {
                    String selectMethod = field.serialType.isTime() ? "selectTime" : "selectDecimal";
                    typeExpr = selectMethod + "(" + typeExpr;
                    for (String typeSelector : field.typeSelectors) {
                        typeExpr += ", \"" + typeSelector + "\"";
                    }
                    typeExpr = typeExpr + ")";
                }
                if (f.voidSuffixes != null)
                    typeExpr = "suffix.matches(\"" + f.voidSuffixes + "\") ? SerialFieldType.VOID : " + typeExpr;
                if (f.time != SchemeFieldTime.COMMON_FIELD)
                    cg.addImport(new ClassName(SchemeFieldTime.class));
                if (f.required) {
                    cg.code("builder.addRequiredField(" +
                        recordNameReference + ", \"" + field.getFullName(fieldName) + "\", " + typeExpr +
                        (f.time == SchemeFieldTime.COMMON_FIELD ? "" : ", SchemeFieldTime." + f.time) + ");");
                } else {
                    cg.code("builder.addOptionalField(" +
                        recordNameReference + ", \"" + field.getFullName(fieldName) + "\", " + typeExpr +
                        ", \"" + f.eventName + "\", \"" + f.propertyName + "\", " + generateEnabledCondition(cg, f) +
                        (f.time == SchemeFieldTime.COMMON_FIELD ? "" : ", SchemeFieldTime." + f.time) + ");");
                }
            }
        }
    }

    private String generateEnabledCondition(ClassGen cg, RecordField f) {
        if (f.enabled) {
            // Field is unconditionally enabled
            return "true";
        }

        String enabledCondition = "";
        if (f.conditionalProperty != null) {
            // Field is enabled if system property is set
            cg.addImport(new ClassName(SystemProperties.class));
            enabledCondition += "SystemProperties.getBooleanProperty(\"" + f.conditionalProperty + "\", false)";
        }
        if (f.onlySuffixesDefault != null) {
            if (!enabledCondition.isEmpty()) {
                enabledCondition += " && ";
            }
            // Field is enabled if suffix matches pattern
            enabledCondition += "suffix.matches(" + (f.onlySuffixesProperty != null ?
                "SystemProperties.getProperty(\"" + f.onlySuffixesProperty + "\", \"" + f.onlySuffixesDefault + "\")" :
                "\"" + f.onlySuffixesDefault + "\"") + ")";

        }
        if (f.exceptSuffixes != null) {
            if (!enabledCondition.isEmpty()) {
                enabledCondition += " && ";
            }
            // Field is enabled if not suffix matches pattern
            enabledCondition += "!suffix.matches(\"" + f.exceptSuffixes + "\")";
        }
        return (enabledCondition.isEmpty()) ? "false" : enabledCondition;
    }

    private void generateCreateDelegatesCode(ClassGen cg, boolean streamOnly) {
        cg.addImport(new ClassName(Collection.class));
        cg.addImport(new ClassName(ArrayList.class));
        cg.addImport(new ClassName(EventDelegate.class));
        cg.addImport(new ClassName(DataRecord.class));
        cg.code("@Override");
        cg.code("public Collection<EventDelegate<?>> create" + (streamOnly ? "StreamOnly" : "") +
            "Delegates(DataRecord record) {");
        cg.indent();
        cg.code("Collection<EventDelegate<?>> result = new ArrayList<>();");
        boolean elseIf = false;
        for (Map.Entry<MappingGen, Set<RecordDesc>> entry : mappingRecords.entrySet()) {
            MappingGen mapping = entry.getKey();
            Set<RecordDesc> records = entry.getValue();
            boolean ifGenerated = false;
            for (RecordDesc record : records) {
                if (record.phantomProperty != null || !recordDelegates.containsKey(record))
                    continue;
                if (!ifGenerated) {
                    cg.code((elseIf ? "} else " : "") +
                        "if (record.getMapping(" + mapping.getClassName().getSimpleName() + ".class) != null) {");
                    cg.indent();
                    elseIf = true;
                    ifGenerated = true;
                }
                for (DelegateGen delegate : recordDelegates.get(record)) {
                    cg.addImport(delegate.getClassName());
                    cg.addImport(new ClassName(QDContract.class));
                    cg.addImport(new ClassName(EventDelegateFlags.class));
                    cg.addImport(new ClassName(EnumSet.class));
                    Map<QDContract, EnumSet<EventDelegateFlags>> contractFlags = delegate.getContractFlags();
                    if (streamOnly) {
                        EnumSet<EventDelegateFlags> flagsUnion = EnumSet.noneOf(EventDelegateFlags.class);
                        for (EnumSet<EventDelegateFlags> fs : contractFlags.values()) {
                            flagsUnion.addAll(fs);
                        }
                        contractFlags = new EnumMap<>(QDContract.class);
                        flagsUnion.remove(EventDelegateFlags.TIME_SERIES); // don't support TimeSeries in STREAM_FEED
                        contractFlags.put(QDContract.STREAM, flagsUnion);
                    }
                    for (Map.Entry<QDContract, EnumSet<EventDelegateFlags>> mapEntries : contractFlags.entrySet()) {
                        cg.addImport(delegate.getClassName());
                        cg.code("result.add(new " + delegate.getClassName().getSimpleName() +
                            "(record, QDContract." + mapEntries.getKey().name() + ", " +
                            eventDelegatesFlagsString(mapEntries.getValue()) + "));");
                    }
                }
            }
            if (ifGenerated)
                cg.unindent();
        }
        cg.code("}");
        cg.code("return result;");
        cg.unindent();
        cg.code("}");
    }

    private String eventDelegatesFlagsString(EnumSet<EventDelegateFlags> flags) {
        StringBuilder sb = new StringBuilder();
        sb.append("EnumSet.of(");
        String sep = "";
        for (EventDelegateFlags flag : flags) {
            sb.append(sep);
            sep = ", ";
            sb.append("EventDelegateFlags.");
            sb.append(flag.name());
        }
        sb.append(")");
        return sb.toString();
    }

    private void generateCreateMappingCode(ClassGen cg) {
        cg.addImport(new ClassName(RecordMapping.class));
        cg.addImport(new ClassName(DataRecord.class));
        cg.code("@Override");
        cg.code("public RecordMapping createMapping(DataRecord record) {");
        cg.indent();
        cg.code("String baseRecordName = getBaseRecordName(record.getName());");
        for (Map.Entry<MappingGen, Set<RecordDesc>> entry : mappingRecords.entrySet()) {
            MappingGen mapping = entry.getKey();
            if (mapping.phantom)
                continue;
            for (RecordDesc record : entry.getValue()) {
                cg.addImport(mapping.getClassName());
                cg.code("if (baseRecordName.equals(\"" + record + "\"))");
                cg.indent();
                cg.code("return new " + mapping.getClassName().getSimpleName() + "(record)" + ";");
                cg.unindent();
            }
        }
        cg.code("return null;");
        cg.unindent();
        cg.code("}");
    }

    private void generateGenBaseRecordName(ClassGen cg) {
        cg.addImport(new ClassName(MarketEventSymbols.class));
        cg.code("@Override");
        cg.code("protected String getBaseRecordName(String recordName) {");
        cg.indent();
        cg.code("return MarketEventSymbols.getBaseSymbol(recordName);");
        cg.unindent();
        cg.code("}");
    }

    void addRecordField(RecordDesc record, RecordField field) {
        Map<String, RecordField> recordFields = recordsFields.computeIfAbsent(record, k -> new LinkedHashMap<>());
        RecordField oldField = recordFields.get(field.fieldName);
        if (oldField == null) {
            recordFields.put(field.fieldName, field);
        } else if (field.fieldType != oldField.fieldType) {
            throw new IllegalArgumentException("FieldType mismatches for field \"" + field + "\"");
        }
        // todo: try to ensure that same fields cannot have different attribute values (timeness, isCompositeOnly, etc.)
    }

    void addRecordDelegate(RecordDesc record, DelegateGen delegate) {
        List<DelegateGen> delegates = recordDelegates.computeIfAbsent(record, k -> new ArrayList<>());
        delegates.add(delegate);
    }

    void addRecordMapping(RecordDesc record, MappingGen mapping) {
        Set<RecordDesc> set = mappingRecords.computeIfAbsent(mapping, k -> new LinkedHashSet<>());
        set.add(record);
    }

    void useMarketEventSymbolsToGetBaseRecordName() {
        useMarketEventSymbol = true;
    }

    ClassName getClassName() {
        return className;
    }
}
