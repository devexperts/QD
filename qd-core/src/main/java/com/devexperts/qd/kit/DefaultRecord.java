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
package com.devexperts.qd.kit;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.qtp.BuiltinFields;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The <code>DefaultRecord</code> is a basic implementation of data record.
 * Note, that this implementation works only with fields that are derived from
 * {@link AbstractDataIntField} and {@link AbstractDataObjField} classes.
 */
public class DefaultRecord implements DataRecord {

    private DefaultScheme scheme; // effectively final, post-filled during scheme construction

    protected final int id;
    protected final String name;
    protected final boolean has_time;

    private final AbstractDataIntField[] intFields;
    private final AbstractDataObjField[] objFields;
    private final Map<String, DataField> fieldsByName = new HashMap<>();

    // mappings are ordered according mapping factory priority so that builtin mappings go first before custom ones
    private final Map<Class<? extends RecordMapping>, RecordMapping> mappings;
    private final RecordMapping mapping; // first mapping according to mapping factory priority

    public DefaultRecord(int id, String name, boolean hasTime, DataIntField[] dataIntFields, DataObjField[] dataObjFields) {
        this.id = id;
        this.name = name;
        this.has_time = hasTime;

        // Cast fields once here for better performance.
        intFields = dataIntFields == null ? new AbstractDataIntField[0] :
            Arrays.copyOf(dataIntFields, dataIntFields.length, AbstractDataIntField[].class);
        objFields = dataObjFields == null ? new AbstractDataObjField[0] :
            Arrays.copyOf(dataObjFields, dataObjFields.length, AbstractDataObjField[].class);

        // Cross-link fields with record.
        for (AbstractDataIntField field : intFields)
            field.setRecord(this);
        for (AbstractDataObjField field : objFields)
            field.setRecord(this);

        // Verify fields and record integrity.
        if (hasTime && intFields.length < 2)
            throw new IllegalArgumentException("Not enough Int-fields to contain time.");
        if (id < 0)
            throw new IllegalArgumentException("Record id is negative.");
        for (int i = 0; i < intFields.length; i++) {
            if (intFields[i].getIndex() != i)
                throw new IllegalArgumentException("Int field index #" + i + " does not match: " + intFields[i]);
        }
        for (int i = 0; i < objFields.length; i++) {
            if (objFields[i].getIndex() != i)
                throw new IllegalArgumentException("Obj field index #" + i + " does not match: " + objFields[i]);
        }

        // Populate fieldsByName with default names - before building mappings.
        putDefaultFieldNames(intFields);
        putDefaultFieldNames(objFields);

        // Build mappings - after record is built and fieldsByName is populated with default names.
        mappings = Collections.unmodifiableMap(RecordMappingFactoryHolder.createMapping(this));
        mapping = mappings.isEmpty() ? null : mappings.values().iterator().next();

        // Populate fieldsByName with non-default names - after default ones.
        putNonDefaultFieldNames(intFields);
        putNonDefaultFieldNames(objFields);
    }

    private void putDefaultFieldNames(AbstractDataField[] fields) {
        for (AbstractDataField field : fields) {
            putFieldByName(field, field.getName());
            putFieldByName(field, field.getLocalName());
            putFieldByName(field, field.getPropertyName()); // initially, this is a default field property name
        }
    }

    private void putNonDefaultFieldNames(AbstractDataField[] fields) {
        for (AbstractDataField field : fields) {
            for (RecordMapping mapping : mappings.values()) {
                String propertyName = mapping.getNonDefaultPropertyName(field.getLocalName());
                if (propertyName != null) {
                    putFieldByName(field, propertyName);
                    field.setPropertyName(propertyName); // redefine field property name
                }
            }
        }
    }

    private void putFieldByName(DataField field, String name) {
        if (name.equals(BuiltinFields.EVENT_SYMBOL_FIELD_NAME))
            throw new IllegalArgumentException("Field name conflicts with " + BuiltinFields.EVENT_SYMBOL_FIELD_NAME + ": " +
                field.getName());
        DataField otherField = fieldsByName.put(name, field);
        if (otherField != null && otherField != field)
            throw new IllegalArgumentException("Field name conflict between " +
                field.getName() + " and " + otherField.getName());
    }

    public void addFieldAlias(String oldName, String newName) {
        if (newName.equals(BuiltinFields.EVENT_SYMBOL_FIELD_NAME)) {
            throw new IllegalArgumentException(
                "Field name conflicts with " + BuiltinFields.EVENT_SYMBOL_FIELD_NAME + ": " +
                    oldName);
        }
        DataField sourceField = fieldsByName.get(oldName);
        if (sourceField == null)
            throw new IllegalArgumentException("Field with name " + oldName + " doesn't exist");
        DataField targetField = fieldsByName.get(newName);
        if (targetField != null) {
            if (sourceField != targetField) {
                throw new IllegalArgumentException("Field name conflicts with " + targetField.getName() + ": " +
                    newName);
            }
        } else {
            fieldsByName.put(newName, sourceField);
            if (sourceField instanceof AbstractDataField)
                ((AbstractDataField)sourceField).setPropertyName(newName);
        }
    }

    /**
     * Sets reference to parent data scheme.
     *
     * @throws IllegalStateException if parent scheme already set to different instance.
     */
    public final void setScheme(DefaultScheme scheme) {
        if (this.scheme != scheme && this.scheme != null)
            throw new IllegalStateException("Parent scheme already set to different instance.");
        this.scheme = scheme;
    }

    public final String toString() {
        return name;
    }

    // ========== DataRecord Implementation ==========

    @Override
    public final DefaultScheme getScheme() {
        return scheme;
    }

    @Override
    public RecordMapping getMapping() {
        return mapping;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends RecordMapping> T getMapping(Class<T> mappingClass) {
        if (mapping == null)
            return null;
        if (mapping.getClass() == mappingClass)
            return (T) mapping;
        if (mappings.size() == 1)
            return null;
        return (T) mappings.get(mappingClass);
    }

    @Override
    public Map<Class<? extends RecordMapping>, RecordMapping> getMappings() {
        return mappings;
    }

    @Override
    public final int getId() {
        return id;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final boolean hasTime() {
        return has_time;
    }

    @Override
    public final int getIntFieldCount() {
        return intFields.length;
    }

    @Override
    public final AbstractDataIntField getIntField(int index) {
        return intFields[index];
    }

    @Override
    public final int getObjFieldCount() {
        return objFields.length;
    }

    @Override
    public final AbstractDataObjField getObjField(int index) {
        return objFields[index];
    }

    @Override
    public final DataField findFieldByName(String name) {
        return fieldsByName.get(name);
    }

    @Override
    public boolean update(RecordCursor from, RecordCursor to) {
        return to.updateDataFrom(from);
    }

    @Override
    public void writeFields(BufferedOutput out, RecordCursor cursor) throws IOException {
        for (AbstractDataIntField field : intFields)
            field.write(out, cursor);
        for (AbstractDataObjField field : objFields)
            field.write(out, cursor);
    }

    @Override
    public void readFields(BufferedInput in, RecordCursor cursor) throws IOException {
        for (AbstractDataIntField field : intFields)
            field.read(in, cursor);
        for (AbstractDataObjField field : objFields)
            field.read(in, cursor);
    }
}
