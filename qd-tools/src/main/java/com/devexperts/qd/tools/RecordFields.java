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
package com.devexperts.qd.tools;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;

import java.util.HashSet;
import java.util.Set;

final class RecordFields {
    private final DataRecord record;
    private final int intFieldCount;
    private final int objFieldCount;
    private final int[] intIndexes;
    private final int[] objIndexes;
    private final Set<DataField> fieldSet = new HashSet<>();

    RecordFields(DataRecord record, OptionFields fields) {
        this.record = record;
        // int fields
        int ifc = 0;
        for (int i = 0; i < record.getIntFieldCount(); i++)
            if (fields.matches(record.getIntField(i))) {
                ifc++;
                fieldSet.add(record.getIntField(i));
            }
        intFieldCount = ifc;
        intIndexes = new int[ifc];
        for (int i = 0, j = 0; i < record.getIntFieldCount(); i++)
            if (fields.matches(record.getIntField(i)))
                intIndexes[j++] = i;
        // obj fields
        int ofs = 0;
        for (int i = 0; i < record.getObjFieldCount(); i++)
            if (fields.matches(record.getObjField(i))) {
                ofs++;
                fieldSet.add(record.getObjField(i));
            }
        objFieldCount = ofs;
        objIndexes = new int[ofs];
        for (int i = 0, j = 0; i < record.getObjFieldCount(); i++)
            if (fields.matches(record.getObjField(i)))
                objIndexes[j++] = i;
    }

    public DataRecord getRecord() {
        return record;
    }

    public boolean isEmpty() {
        return intFieldCount == 0 && objFieldCount == 0;
    }

    public int getIntFieldCount() {
        return intFieldCount;
    }

    public int getObjFieldCount() {
        return objFieldCount;
    }

    public int getIntIndex(int i) {
        return intIndexes[i];
    }

    public int getObjIndex(int i) {
        return objIndexes[i];
    }

    public boolean contains(DataField f) {
        return fieldSet.contains(f);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intFieldCount; i++) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(record.getIntField(intIndexes[i]));
        }
        for (int i = 0; i < objFieldCount; i++) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(record.getObjField(objIndexes[i]));
        }
        return sb.toString();
    }

}
