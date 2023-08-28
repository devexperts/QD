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

import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.util.MappingUtil;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates xxxDelegate classes for records.
 */
class MappingGen {
    private final CodeGenEnvironment env;
    private final ClassName className;
    private final Set<RecordField> recordFields = new LinkedHashSet<>();
    private ClassName mappingSuperClassName;

    boolean phantom;

    MappingGen(ClassName className, CodeGenEnvironment env) {
        this.env = env;
        this.className = className;
    }

    ClassName getClassName() {
        return className;
    }

    void inheritMappingFrom(ClassName mappingSuperClassName) {
        this.mappingSuperClassName = mappingSuperClassName;
    }

    void addRecordField(RecordField field) {
        recordFields.add(field);
    }

    void generate() throws IOException {
        if (phantom)
            return;
        ClassGen cg = ClassGen.resolve(className, "TemplateMapping.java", env);
        if (mappingSuperClassName != null) {
            cg.setVariableValue("SUPER_CLASS", mappingSuperClassName.getSimpleName());
            cg.addImport(mappingSuperClassName);
        } else {
            cg.setVariableValue("SUPER_CLASS", "RecordMapping");
            cg.addImport(new ClassName(RecordMapping.class));
        }
        for (RecordField f : recordFields) {
            if (f.isActive()) {
                for (String fieldIndex : f.fieldToFullNameMap.values()) {
                    cg.code("private final int " + fieldIndex + ";");
                }
                for (Map.Entry<FieldType.Variable, String> e : f.variableToFullNameMap.entrySet()) {
                    cg.code("private " + (e.getKey().isFinal ? "final " : "") +
                        e.getKey().javaType + " " + e.getValue() + ";");
                }
            }
        }
        generateMappingConstructorCode(cg);
        for (RecordField f : recordFields) {
            if (f.isActive())
                f.generateFieldMappingCode(cg);
        }
        env.writeSourceFile(className, cg.buildSource());
    }

    private void generateMappingConstructorCode(ClassGen cg) {
        cg.newLine();
        cg.code("public " + className.getSimpleName() + "(DataRecord record) {");
        cg.indent();
        cg.code("super(record);");
        for (RecordField f : recordFields) {
            if (f.isActive()) {
                for (Map.Entry<FieldType.Field, String> e : f.fieldToFullNameMap.entrySet()) {
                    boolean required = f.required && e.getKey().required;
                    if (e.getKey().adaptiveType) {
                        cg.code(e.getValue() + " = " + (e.getKey().isObject ? "findObjField" : "findIntField") +
                            "(\"" + e.getKey().getFullName(f.fieldName) + "\", " + required + ");");
                    } else {
                        cg.addImport(new ClassName(MappingUtil.class));
                        cg.code(e.getValue() + " = MappingUtil." +
                            (e.getKey().isObject ? "findObjField" : "findIntField") +
                            "(record, \"" + e.getKey().getFullName(f.fieldName) + "\", " + required + ");");
                    }
                }
                for (Map.Entry<FieldType.Variable, String> e : f.variableToFullNameMap.entrySet()) {
                    e.getKey().generateInitialization(cg, f.suffixToFullNameMap);
                }
            }
        }
        for (RecordField f : recordFields) {
            if (f.isActive()) {
                if (!f.propertyName.equals(AbstractDataField.getDefaultPropertyName(f.fieldName))) {
                    for (FieldType.Field field : f.fieldToFullNameMap.keySet()) {
                        cg.code("putNonDefaultPropertyName(\"" +
                            field.getFullName(f.fieldName) + "\", \"" + field.getFullName(f.propertyName) + "\");");
                    }
                }
            }
        }
        cg.unindent();
        cg.code("}");
    }
}
