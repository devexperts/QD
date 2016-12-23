/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.codegen;

import java.util.*;

import com.devexperts.io.Marshalled;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.util.*;
import com.devexperts.util.TimeUtil;

enum FieldType {
    VOID(new Builder()
        .addField(new Field(false, SerialFieldType.VOID))
    ),
    SEQUENCE(new Builder()
        .addField(new Field(false, SerialFieldType.SEQUENCE))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(int.class))
    ),
    TIME(new Builder()
        .addField(new Field(false, SerialFieldType.TIME))
        .addAccess(Access.createWithAccessPattern("Millis", "long", "0", "getInt(cursor, %s) * 1000L", "setInt(cursor, %s, TimeUtil.getSecondsFromTime(%s))"))
        .addAccess(Access.createWithAccessPattern("Seconds", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(TimeUtil.class))
        .setMapper(new DefaultMapper("Millis", long.class))
    ),
    TIME_NANO_PART(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(int.class))
    ),
    DATE(new Builder()
        .addField(new Field(false, SerialFieldType.DATE))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(int.class))
    ),
    INT(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(int.class))
    ),
    DECIMAL_AS_DOUBLE(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL))
        .addAccess(Access.createWithAccessPattern("", "double", "Double.NaN", "Decimal.toDouble(getInt(cursor, %s))", "setInt(cursor, %s, Decimal.compose(%s))"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(Decimal.class))
        .setMapper(new DecimalMapper(double.class))
    ),
    DECIMAL_AS_LONG(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "(long)Decimal.toDouble(getInt(cursor, %s))", "setInt(cursor, %s, Decimal.composeDecimal(%s, 0))"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "Decimal.toDouble(getInt(cursor, %s))", "setInt(cursor, %s, Decimal.compose(%s))"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(Decimal.class))
        .setMapper(new DecimalMapper(long.class))
    ),
    DECIMAL_OR_INT_AS_LONG(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL))
        .addVariable(new Variable(true, "boolean", "IsDecimal") {
            @Override
            void generateInitialization(ClassGen cg, Map<String, String> suffixToFullNameMap) {
                String fieldName = suffixToFullNameMap.get("");
                String varName = suffixToFullNameMap.get(this.suffix);
                cg.code(varName + " = MappingUtil.isDecimalField(record, " + fieldName + ");");
            }
        })
        .addAccess(new Access("", "long", "0") {
            @Override
            void generateGetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("return (long)Decimal.toDouble(getInt(cursor, " + index + "));");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("return getInt(cursor, " + index + ");");
                cg.unindent();
                cg.code("}");
            }

            @Override
            void generateSetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap, String value) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", Decimal.composeDecimal(" + value + ", 0));");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", (int)" + value + ");");
                cg.unindent();
                cg.code("}");
            }
        })
        .addAccess(new Access("Double", "double", "Double.NaN") {
            @Override
            void generateGetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("return Decimal.toDouble(getInt(cursor, " + index + "));");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("return getInt(cursor, " + index + ");");
                cg.unindent();
                cg.code("}");
            }

            @Override
            void generateSetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap, String value) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", Decimal.compose(" + value + "));");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", (int)" + value + ");");
                cg.unindent();
                cg.code("}");
            }
        })
        .addAccess(new Access("Decimal", "int", "0") {
            @Override
            void generateGetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("return getInt(cursor, " + index + ");");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("return Decimal.composeDecimal(getInt(cursor, " + index + "), 0);");
                cg.unindent();
                cg.code("}");
            }

            @Override
            void generateSetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap, String value) {
                String index = suffixToFullNameMap.get("");
                String isDecimalVar = suffixToFullNameMap.get("IsDecimal");
                cg.code("if (" + isDecimalVar + ") {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", " + value + ");");
                cg.unindent();
                cg.code("} else {");
                cg.indent();
                cg.code("setInt(cursor, " + index + ", (int)Decimal.toDouble(" + value + "));");
                cg.unindent();
                cg.code("}");
            }
        })
        .addImport(new ClassName(Decimal.class))
        .addImport(new ClassName(MappingUtil.class))
        .setMapper(new DecimalMapper(long.class))
    ),
    DECIMAL_AS_SHARES(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "(long)(Decimal.toDouble(getInt(cursor, %s)) * 1000 + 0.5)", "setInt(cursor, %s, Decimal.composeDecimal(%s, 3))"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "Decimal.toDouble(getInt(cursor, %s)) * 1000", "setInt(cursor, %s, Decimal.compose(%s / 1000))"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(Decimal.class))
        .setMapper(new DecimalMapper(long.class))
    ),
    CHAR(new Builder()
        .addField(new Field(false, SerialFieldType.UTF_CHAR))
        .addAccess(Access.createWithAccessPattern("", "char", "'\\0'", "(char)getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(char.class))
    ),
    SHORT_STRING(new Builder()
        .addField(new Field(false, SerialFieldType.SHORT_STRING))
        .addAccess(Access.createWithAccessPattern("String", "String", "null", "ShortString.decode(getInt(cursor, %s))", "setInt(cursor, %s, (int)ShortString.encode(%s))"))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(ShortString.class))
        .setMapper(new DefaultMapper("String", String.class))
    ),
    STRING(new Builder()
        .addField(new Field(true, SerialFieldType.UTF_CHAR_ARRAY))
        .addAccess(Access.createWithAccessPattern("", "String", "null", "(String)getObj(cursor, %s)", "setObj(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(String.class))
    ),
    MARSHALLED(new Builder()
        .addField(new Field(true, SerialFieldType.SERIAL_OBJECT))
        .addAccess(Access.createWithAccessPattern("", "Marshalled<?>", "null", "MappingUtil.getMarshalled(getObj(cursor, %s))", "setObj(cursor, %s, %s)"))
        .addImport(new ClassName(MappingUtil.class))
        .addImport(new ClassName(Marshalled.class))
        .setMapper(new MarshalledMapper())
    );

    final List<Field> fields;
    final List<Variable> variables;
    final List<Access> accesses;
    final List<ClassName> imports;
    final TypeMapper mapper;

    FieldType(Builder builder) {
        this.fields = builder.fields;
        this.variables = builder.variables;
        this.accesses = builder.accesses;
        this.imports = builder.imports;
        this.mapper = builder.mapper;
    }

    private static final class Builder {
        final List<Field> fields = new ArrayList<>();
        final List<Variable> variables = new ArrayList<>();
        final List<Access> accesses = new ArrayList<>();
        final List<ClassName> imports = new ArrayList<>();
        TypeMapper mapper;

        Builder addField(Field field) {
            fields.add(field);
            return this;
        }

        Builder addVariable(Variable variable) {
            variables.add(variable);
            return this;
        }

        Builder addAccess(Access access) {
            accesses.add(access);
            return this;
        }

        Builder addImport(ClassName className) {
            imports.add(className);
            return this;
        }

        Builder setMapper(TypeMapper mapper) {
            this.mapper = mapper;
            return this;
        }
    }

    static final class Field {
        final boolean required;
        final boolean isObject;
        final SerialFieldType serialType;
        final String suffix;

        Field(boolean isObject, SerialFieldType serialType) {
            this(true, "", isObject, serialType);
        }

        Field(boolean required, String suffix, boolean isObject, SerialFieldType serialType) {
            this.required = required;
            this.isObject = isObject;
            this.serialType = serialType;
            this.suffix = suffix;
        }

        String getFullName(String name) {
            return name + (suffix.isEmpty() ? "" : ("_" + suffix));
        }
    }

    abstract static class Variable {
        final boolean isFinal;
        final String javaType; // e.g. int, Object, List<String>, ...
        final String suffix;

        Variable(boolean isFinal, String javaType, String suffix) {
            this.isFinal = isFinal;
            this.javaType = javaType;
            this.suffix = suffix;
        }

        String getFullName(String name) {
            return "v" + name + suffix;
        }

        abstract void generateInitialization(ClassGen cg, Map<String, String> suffixToFullNameMap);
    }

    abstract static class Access {
        final String suffix;
        final String javaType;
        final String defaultValue;

        Access(String suffix, String javaType, String defaultValue) {
            this.suffix = suffix;
            this.javaType = javaType;
            this.defaultValue = defaultValue;
        }

        static Access createWithAccessPattern(String suffix, String javaType, String defaultValue, final String getterPattern, final String setterPattern) {
            return new Access(suffix, javaType, defaultValue) {
                @Override
                void generateGetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap) {
                    String index = suffixToFullNameMap.get("");
                    cg.code("return " + String.format(getterPattern, index) + ";");
                }

                @Override
                void generateSetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap, String value) {
                    String index = suffixToFullNameMap.get("");
                    cg.code(String.format(setterPattern, index, value) + ";");
                }
            };
        }

        abstract void generateGetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap);

        abstract void generateSetterBody(ClassGen cg, Map<String, String> suffixToFullNameMap, String value);
    }

    interface TypeMapper {
        String generateGetter(CodeGenType type, String getterFormat);

        String generateSetter(CodeGenType type, String setterFormat, String value);

        void configureImports(ClassGen cg, CodeGenType type);

        boolean hasMapping(CodeGenType type);
    }

    private static class DefaultMapper implements TypeMapper {
        private final String suffix;
        private final CodeGenType applicableType;

        DefaultMapper(Class<?> applicableType) {
            this("", applicableType);
        }

        DefaultMapper(String suffix, Class<?> applicableType) {
            this.suffix = suffix;
            this.applicableType = new JavaClassType(applicableType);
        }

        @Override
        public String generateGetter(CodeGenType type, String getterFormat) {
            return String.format(getterFormat, suffix);
        }

        @Override
        public String generateSetter(CodeGenType type, String setterFormat, String value) {
            return String.format(setterFormat, suffix, value);
        }

        @Override
        public void configureImports(ClassGen cg, CodeGenType type) {
            // nothing to do
        }

        @Override
        public boolean hasMapping(CodeGenType type) {
            return type.equals(applicableType);
        }
    }

    private static class DecimalMapper implements TypeMapper {
        private final CodeGenType decimalType;

        DecimalMapper(Class<?> decimalType) {
            this.decimalType = new JavaClassType(decimalType);
        }

        @Override
        public String generateGetter(CodeGenType type, String getterFormat) {
            String base = String.format(getterFormat, "");
            if (type.isPrimitive()) {
                if (type.isSameType(boolean.class))
                    return base + " != 0";
                if (!CodeGenUtils.isPrimitiveAssignable(decimalType, type))
                    return "(" + type + ")" + base;
                return base;
            } else {
                return ClassValueMappingRegistry.getInstance().deserialize(type, decimalType, base);
            }
        }

        @Override
        public String generateSetter(CodeGenType type, String setterFormat, String value) {
            String modValue;
            if (type.isPrimitive()) {
                if (type.isSameType(boolean.class))
                    modValue = value + " ? 1 : 0";
                else if (!CodeGenUtils.isPrimitiveAssignable(type, decimalType))
                    modValue = "(" + decimalType + ")" + value;
                else modValue = value;
            } else {
                modValue = ClassValueMappingRegistry.getInstance().serialize(type, decimalType, value);
            }
            return String.format(setterFormat, "", modValue);
        }

        @Override
        public void configureImports(ClassGen cg, CodeGenType type) {
            if (!type.isPrimitive())
                cg.addImport(type.getClassName());
        }

        @Override
        public boolean hasMapping(CodeGenType type) {
            return type.isPrimitive() || ClassValueMappingRegistry.getInstance().canSerialize(type, decimalType);
        }
    }

    private static class MarshalledMapper implements TypeMapper {
        @Override
        public String generateGetter(CodeGenType type, String getterFormat) {
            String base = String.format(getterFormat, "");
            if (type.isSameType(Marshalled.class))
                return base;
            return "(" + type.getClassName().getSimpleName() + ")Marshalled.unwrap(" + base + ")";
        }

        @Override
        public String generateSetter(CodeGenType type, String setterFormat, String value) {
            String modValue = value;
            if (!type.isSameType(Marshalled.class))
                modValue = "Marshalled.forObject(" + value + ")";
            return String.format(setterFormat, "", modValue);
        }

        @Override
        public void configureImports(ClassGen cg, CodeGenType type) {
            if (!type.isSameType(Marshalled.class)) {
                cg.addImport(new ClassName(Marshalled.class));
                cg.addImport(type.getClassName());
            }
        }

        @Override
        public boolean hasMapping(CodeGenType type) {
            return !type.isPrimitive();
        }
    }
}
