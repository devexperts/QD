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
package com.devexperts.qd.util;

import com.devexperts.io.Marshalled;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;

public class MappingUtil {
    public static int findIntField(DataRecord record, String localName, boolean required) {
        DataField field = record.findFieldByName(localName);
        if (field instanceof DataIntField)
            return field.getIndex();
        if (required)
            throw new IllegalArgumentException("Required int field " + localName + " is missing in record " + record);
        return -1;
    }

    public static int findObjField(DataRecord record, String localName, boolean required) {
        DataField field = record.findFieldByName(localName);
        if (field instanceof DataObjField)
            return field.getIndex();
        if (required)
            throw new IllegalArgumentException("Required field " + localName + " is missing in record " + record);
        return -1;
    }

    public static Marshalled<?> getMarshalled(Object object) {
        return object instanceof Marshalled ? (Marshalled<?>) object : Marshalled.forObject(object);
    }

    private MappingUtil() {}
}
