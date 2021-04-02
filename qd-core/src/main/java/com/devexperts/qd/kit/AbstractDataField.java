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

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SerialFieldType;

public abstract class AbstractDataField implements DataField {
    private DataRecord record;
    private final int index;
    private final String name;
    private String localName;
    private String propertyName;
    private final SerialFieldType serialType;

    AbstractDataField(int index, String name, SerialFieldType serialType) {
        if (index < 0)
            throw new IllegalArgumentException("index < 0");
        if (name == null)
            throw new NullPointerException("name");
        this.index = index;
        this.name = name;
        this.serialType = serialType;
    }

    /**
     * Sets reference to parent data record.
     *
     * @throws IllegalStateException if parent record already set to different instance.
     */
    public final synchronized void setRecord(DataRecord record) {
        if (this.record != record && this.record != null)
            throw new IllegalStateException("Parent record already set to different instance");
        String prefix = record.getName() + ".";
        if (!name.startsWith(prefix))
            throw new IllegalArgumentException("Field name must start with record name and dot, " +
                "but '" + name + "' " +
                "does not start with'" + prefix + "'");
        this.record = record;
        this.localName = name.substring(prefix.length());
        this.propertyName = getDefaultPropertyName(localName);
    }

    public final String toString() {
        return name;
    }

    public final DataRecord getRecord() {
        DataRecord result = record;
        if (result == null)
            throw new IllegalStateException("Trying to get parent record when it is not initialized yet");
        return result;
    }

    public final int getIndex() {
        return index;
    }

    public final String getName() {
        return name;
    }

    public final String getLocalName() {
        String result = localName;
        if (result == null)
            throw new IllegalStateException("Trying to get local name when parent record is not initialized yet");
        return result;
    }

    public String getPropertyName() {
        String result = propertyName;
        if (result == null)
            throw new IllegalStateException("Trying to get property name when parent record is not initialized yet");
        return result;
    }

    // for initialization from DefaultRecord
    void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public final SerialFieldType getSerialType() {
        return serialType;
    }

    // ========== static helper method ==========

    public static String getDefaultPropertyName(String localName) {
        return localName.replace(".", "");
    }
}
