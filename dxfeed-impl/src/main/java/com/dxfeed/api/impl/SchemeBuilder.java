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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.VoidIntField;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * This class is used to add fields to QD scheme.
 */
public class SchemeBuilder {
    private final Map<String, RecordInfo> records = new LinkedHashMap<>();

    @Nonnull
    private final SchemeProperties schemeProperties;

    public SchemeBuilder(@Nonnull SchemeProperties schemeProperties) {
        this.schemeProperties = Objects.requireNonNull(schemeProperties);
    }

    /**
     * The same as  {@link #addRequiredField(String, String, SerialFieldType)}.
     * @deprecated Use {@link #addRequiredField(String, String, SerialFieldType)} or
     * {@link #addOptionalField(String, String, SerialFieldType, String, String, boolean, SchemeFieldTime)}
     */
    public void addField(String recordName, String fieldName, SerialFieldType type) {
        addFieldInternal(recordName, fieldName, type, SchemeFieldTime.COMMON_FIELD);
    }

    /**
     * The same as {@link #addRequiredField(String, String, SerialFieldType, SchemeFieldTime)}.
     * @deprecated Use {@link #addRequiredField(String, String, SerialFieldType, SchemeFieldTime)} or
     * {@link #addOptionalField(String, String, SerialFieldType, String, String, boolean)}
     */
    public void addField(String recordName, String fieldName, SerialFieldType type, SchemeFieldTime time) {
        addFieldInternal(recordName, fieldName, type, time);
    }

    public void addRequiredField(String recordName, String fieldName, SerialFieldType type, SchemeFieldTime time) {
        addFieldInternal(recordName, fieldName, type, time);
    }

    public void addRequiredField(String recordName, String fieldName, SerialFieldType type) {
        addRequiredField(recordName, fieldName, type, SchemeFieldTime.COMMON_FIELD);
    }

    public void addOptionalField(String recordName, String fieldName, SerialFieldType type, String eventName, String propertyName, boolean enabledByDefault, SchemeFieldTime time) {
        Boolean enabledInProperties = schemeProperties.isEventPropertyEnabled(propertyName, eventName);
        if (enabledInProperties != null)
            enabledByDefault = enabledInProperties;
        if (!enabledByDefault)
            return;
        addFieldInternal(recordName, fieldName, type, time);
    }

    public void addOptionalField(String recordName, String fieldName, SerialFieldType type, String eventName, String propertyName, boolean enabledByDefault) {
        addOptionalField(recordName, fieldName, type, eventName, propertyName, enabledByDefault, SchemeFieldTime.COMMON_FIELD);
    }

    private void addFieldInternal(String recordName, String fieldName, SerialFieldType type, SchemeFieldTime time) {
        RecordInfo info = records.get(recordName);
        if (info == null)
            records.put(recordName, info = new RecordInfo(recordName));
        String name = recordName + "." + fieldName;
        info.addField(name, type.forNamedField(name), time);
    }

    public DataRecord[] buildRecords() {
        DataRecord[] result = new DataRecord[records.size()];
        int id = 0;
        for (int regional = 0; regional < 2; regional++)
            for (RecordInfo info : records.values())
                if ((regional != 0) == (info.recordName.length() > 2 && info.recordName.charAt(info.recordName.length() - 2) == '&')) {
                    result[id] = info.createRecord(id);
                    id++;
                }
        return result;
    }

    private static class RecordInfo {
        final String recordName;
        final LinkedHashMap<String, SerialFieldType> fields = new LinkedHashMap<>();
        int intFieldsCount;
        int objFieldsCount;
        String[] timeFields = new String[2];

        RecordInfo(String recordName) {
            this.recordName = recordName;
        }

        void addField(String fieldName, SerialFieldType type, SchemeFieldTime time) {
            if (time != SchemeFieldTime.COMMON_FIELD) {
                if (type.isObject())
                    throw new IllegalArgumentException("Failed to create default data-scheme: " + fieldName + " time-field must have integer type");
                int timeFieldIndex = time == SchemeFieldTime.FIRST_TIME_INT_FIELD ? 0 : 1;
                if (timeFields[timeFieldIndex] == null)
                    timeFields[timeFieldIndex] = fieldName;
                else
                    if (!timeFields[timeFieldIndex].equals(fieldName))
                        throw new IllegalArgumentException("Failed to create default data-scheme: different time-fields proposed for " + recordName + " record");
            }
            SerialFieldType oldType = fields.get(fieldName);
            if (oldType == null) {
                if (type.isObject())
                    objFieldsCount++;
                else if (type.isLong())
                    intFieldsCount += 2;
                else
                    intFieldsCount++;
                fields.put(fieldName, type);
            } else
                if (oldType != type)
                    throw new IllegalArgumentException("Failed to create default data-scheme: " + fieldName + " field has several different types");
        }

        public DataRecord createRecord(int id) {
            int intIndex = 0;
            int objIndex = 0;
            boolean hasTime = (timeFields[0] != null || timeFields[1] != null);
            if (hasTime) {
                for (int i = 0; i < 2; i++)
                    if (timeFields[i] == null)
                        addField(recordName + ".$VoidTimeField", SerialFieldType.VOID, i == 0 ?
                            SchemeFieldTime.FIRST_TIME_INT_FIELD : SchemeFieldTime.SECOND_TIME_INT_FIELD);
                intIndex = 2;
            }
            DataIntField[] intFields = new DataIntField[intFieldsCount];
            DataObjField[] objFields = new DataObjField[objFieldsCount];
            for (Map.Entry<String, SerialFieldType> entry : fields.entrySet()) {
                SerialFieldType type = entry.getValue();
                String fieldName = entry.getKey();
                if (type.isObject()) {
                    DataObjField field = type.createDefaultObjInstance(objIndex, fieldName);
                    if (field == null)
                        throw new IllegalArgumentException("Cannot construct field " + fieldName + " of type " + type);
                    objFields[objIndex] = field;
                    objIndex++;
                } else {
                    int index;
                    if (hasTime && fieldName.equals(timeFields[0]))
                        index = 0;
                    else if (hasTime && fieldName.equals(timeFields[1]))
                        index = 1;
                    else
                        index = intIndex++;
                    DataIntField field = type.createDefaultIntInstance(index, fieldName);
                    if (field == null)
                        throw new IllegalArgumentException("Cannot construct field " + fieldName + " of type " + type);
                    intFields[index] = field;
                    if (type.isLong()) {
                        if (index + 1 != intIndex)
                            throw new IllegalArgumentException("Cannot add void tail for " + fieldName + " of type " + type + " at index " + index);
                        intFields[intIndex] = new VoidIntField(intIndex, fieldName + "$VoidTail");
                        intIndex++;
                    }
                }
            }
            if (recordName.equals("Configuration"))
                return new VersionedRecord(id, recordName, hasTime, intFields, objFields, "Version");
            return new DefaultRecord(id, recordName, hasTime, intFields, objFields);
        }
    }
}
