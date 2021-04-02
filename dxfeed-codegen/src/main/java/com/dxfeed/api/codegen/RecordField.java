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

import com.devexperts.qd.kit.AbstractDataField;
import com.devexperts.qd.ng.RecordCursor;
import com.dxfeed.api.impl.SchemeFieldTime;

import java.util.HashMap;
import java.util.Map;

class RecordField {
    final String propertyName;
    final String eventName;
    final String fieldName;
    final FieldType fieldType;
    final Map<String, String> suffixToFullNameMap;
    final Map<FieldType.Field, String> fieldToFullNameMap;
    final Map<FieldType.Variable, String> variableToFullNameMap;

    boolean required = true;
    String alt;
    SchemeFieldTime time = SchemeFieldTime.COMMON_FIELD;
    boolean isCompositeOnly;
    boolean enabled = true;
    String onlySuffixesProperty;
    String onlySuffixesDefault;
    String exceptSuffixes;
    String voidSuffixes;
    // Represents either phantom or conditional property
    String conditionalProperty;
    // Flag indicating phantom property
    boolean isPhantom;

    RecordField(String propertyName, String eventName, String fieldName, FieldType fieldType) {
        this.propertyName = propertyName;
        this.eventName = eventName;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        fieldToFullNameMap = new HashMap<>();
        for (FieldType.Field field : fieldType.fields)
            fieldToFullNameMap.put(field, (field.isObject ? "o" : "i") + field.getFullName(propertyName));
        variableToFullNameMap = new HashMap<>();
        for (FieldType.Variable var : fieldType.variables)
            variableToFullNameMap.put(var, var.getFullName(propertyName));
        suffixToFullNameMap = new HashMap<>();
        for (Map.Entry<FieldType.Field, String> e : fieldToFullNameMap.entrySet())
            suffixToFullNameMap.put(e.getKey().suffix, e.getValue());
        for (Map.Entry<FieldType.Variable, String> e : variableToFullNameMap.entrySet())
            suffixToFullNameMap.put(e.getKey().suffix, e.getValue());
    }

    @Override
    public String toString() {
        return fieldName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RecordField))
            return false;
        return fieldName.equals(((RecordField) o).fieldName);
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }

    void generateFieldMappingCode(ClassGen cg) {
        cg.addImport(new ClassName(RecordCursor.class));
        generateImports(cg);
        String defaultPropertyName = AbstractDataField.getDefaultPropertyName(fieldName);
        boolean deprecated = !propertyName.equals(defaultPropertyName);
        if (deprecated)
            for (FieldType.Access access : fieldType.accesses)
                generateFieldAccess(cg, access, defaultPropertyName, true);
        for (FieldType.Access access : fieldType.accesses)
            generateFieldAccess(cg, access, propertyName, false);
    }

    private void generateImports(ClassGen cg) {
        for (ClassName className : fieldType.imports)
            cg.addImport(className);
    }

    private void generateFieldAccess(ClassGen cg, FieldType.Access access, String propertyName, boolean deprecated) {
        // Generate getter
        cg.newLine();
        if (deprecated)
            cg.code("@Deprecated");
        cg.code("public " + access.javaType + " get" + propertyName + access.suffix + "(RecordCursor cursor) {");
        cg.indent();
        if (required) {
            access.generateGetterBody(cg, suffixToFullNameMap);
        } else {
            cg.code("if (" + generateIndexesAreNotDefinedCondition() + ")");
            cg.indent();
            cg.code("return " + (alt != null ? alt : access.defaultValue) + ";");
            cg.unindent();
            access.generateGetterBody(cg, suffixToFullNameMap);
        }
        cg.unindent();
        cg.code("}");
        // Generate setter
        String varName = propertyName.length() > 1 && Character.isUpperCase(propertyName.charAt(0)) && Character.isLowerCase(propertyName.charAt(1)) ?
            Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1) :
            "_" + propertyName;
        cg.newLine();
        if (deprecated)
            cg.code("@Deprecated");

        cg.code("public void set" + propertyName + access.suffix + "(RecordCursor cursor, " + access.javaType + " " + varName + ") {");
        cg.indent();
        if (!required) {
            cg.code("if (" + generateIndexesAreNotDefinedCondition() + ")");
            cg.indent();
            cg.code("return;");
            cg.unindent();
        }
        access.generateSetterBody(cg, suffixToFullNameMap, varName);
        cg.unindent();
        cg.code("}");
    }

    private String generateIndexesAreNotDefinedCondition() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<FieldType.Field, String> e : fieldToFullNameMap.entrySet()) {
            if (e.getKey().required) {
                if (builder.length() > 0)
                    builder.append(" || ");
                builder.append(e.getValue()).append(" < 0");
            }
        }
        if (builder.length() == 0)
            return "true";
        return builder.toString();
    }

    boolean isActive() {
        return !isPhantom && fieldType.accesses.size() > 0;
    }
}
