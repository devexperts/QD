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

import com.devexperts.io.Marshalled;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.util.MappingUtil;
import com.devexperts.qd.util.ShortString;
import com.devexperts.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

enum FieldType {
    VOID(new Builder()
        .addField(new Field(false, SerialFieldType.VOID))
    ),
    SEQUENCE(new Builder()
        .addField(new Field(false, SerialFieldType.SEQUENCE))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(int.class))
    ),
    TIME_MILLIS(new Builder()
        .addField(new Field(false, SerialFieldType.TIME_MILLIS))
        .addAccess(Access.createWithAccessPattern("Millis", "long", "0", "getLong(cursor, %s)", "setLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Seconds", "int", "0", "TimeUtil.getSecondsFromTime(getLong(cursor, %s))", "setLong(cursor, %s, %s * 1000L)"))
        .addImport(new ClassName(TimeUtil.class))
        .setMapper(new DefaultMapper("Millis", long.class))
    ),
    TIME_SECONDS(new Builder()
        .addField(new Field(false, SerialFieldType.TIME_SECONDS))
        .addAccess(Access.createWithAccessPattern("Millis", "long", "0", "getInt(cursor, %s) * 1000L", "setInt(cursor, %s, TimeUtil.getSecondsFromTime(%s))"))
        .addAccess(Access.createWithAccessPattern("Seconds", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(TimeUtil.class))
        .setMapper(new DefaultMapper("Millis", long.class))
    ),
    BID_ASK_TIME(new Builder()
        .addField(new Field(false, SerialFieldType.TIME_SECONDS, true, "dxscheme.bat"))
        .addAccess(Access.createWithAccessPattern("Millis", "long", "0", "getAsTimeMillis(cursor, %s)", "setAsTimeMillis(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Seconds", "int", "0", "getAsTimeSeconds(cursor, %s)", "setAsTimeSeconds(cursor, %s, %s)"))
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
    FLAGS(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(int.class))
    ),
    INDEX(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(int.class))
    ),
    LONG(new Builder()
        .addField(new Field(false, SerialFieldType.LONG))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "getLong(cursor, %s)", "setLong(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(long.class))
    ),
    INT(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(int.class))
    ),
    INT_DECIMAL(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT, true))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getAsInt(cursor, %s)", "setAsInt(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Long", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(int.class))
    ),
    DIV_FREQUENCY(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT, true))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getAsInt(cursor, %s)", "setAsInt(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Long", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper("Double", double.class))
    ),
    SIZE(new Builder()
        .addField(new Field(false, SerialFieldType.COMPACT_INT, true, "dxscheme.size"))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getAsInt(cursor, %s)", "setAsInt(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Long", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper("Double", double.class))
    ),
    VOLUME(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true, "dxscheme.volume", "dxscheme.size"))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper("Double", double.class))
    ),
    SHARES(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true, "dxscheme.shares"))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper("Double", double.class))
    ),
    PRICE(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true, "dxscheme.price"))
        .addAccess(Access.createWithAccessPattern("", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(double.class))
    ),
    TURNOVER(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true, "dxscheme.turnover", "dxscheme.price"))
        .addAccess(Access.createWithAccessPattern("", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(double.class))
    ),
    OPEN_INTEREST(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true, "dxscheme.oi"))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DefaultMapper("Double", double.class))
    ),
    DECIMAL_AS_DOUBLE(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true))
        .addAccess(Access.createWithAccessPattern("", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(double.class))
    ),
    DECIMAL_AS_LONG(new Builder()
        .addField(new Field(false, SerialFieldType.DECIMAL, true))
        .addAccess(Access.createWithAccessPattern("", "long", "0", "getAsLong(cursor, %s)", "setAsLong(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Double", "double", "Double.NaN", "getAsDouble(cursor, %s)", "setAsDouble(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("Decimal", "int", "0", "getAsTinyDecimal(cursor, %s)", "setAsTinyDecimal(cursor, %s, %s)"))
        .addAccess(Access.createWithAccessPattern("WideDecimal", "long", "0", "getAsWideDecimal(cursor, %s)", "setAsWideDecimal(cursor, %s, %s)"))
        .setMapper(new DecimalMapper(long.class))
    ),
    CHAR(new Builder()
        .addField(new Field(false, SerialFieldType.UTF_CHAR))
        .addAccess(Access.createWithAccessPattern("", "char", "'\\0'", "(char) getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .setMapper(new DefaultMapper(char.class))
    ),
    SHORT_STRING(new Builder()
        .addField(new Field(false, SerialFieldType.SHORT_STRING))
        .addAccess(Access.createWithAccessPattern("String", "String", "null", "ShortString.decode(getInt(cursor, %s))", "setInt(cursor, %s, (int) ShortString.encode(%s))"))
        .addAccess(Access.createWithAccessPattern("", "int", "0", "getInt(cursor, %s)", "setInt(cursor, %s, %s)"))
        .addImport(new ClassName(ShortString.class))
        .setMapper(new DefaultMapper("String", String.class))
    ),
    STRING(new Builder()
        .addField(new Field(true, SerialFieldType.UTF_CHAR_ARRAY))
        .addAccess(Access.createWithAccessPattern("", "String", "null", "(String) getObj(cursor, %s)", "setObj(cursor, %s, %s)"))
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
        final boolean adaptiveType; // the field has a type controlled by external configuration (system properties)
        final String[] typeSelectors;
        final String suffix;

        Field(boolean isObject, SerialFieldType serialType) {
            this(true, isObject, "", serialType, false);
        }

        Field(boolean isObject, SerialFieldType serialType, boolean adaptiveType, String... typeSelectors) {
            this(true, isObject, "", serialType, adaptiveType, typeSelectors);
        }

        Field(boolean required, boolean isObject, String suffix, SerialFieldType serialType, boolean adaptiveType, String... typeSelectors) {
            this.required = required;
            this.isObject = isObject;
            this.serialType = serialType;
            this.adaptiveType = adaptiveType;
            this.typeSelectors = typeSelectors;
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
                    return "(" + type + ") " + base;
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
                    modValue = "(" + decimalType + ") " + value;
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
