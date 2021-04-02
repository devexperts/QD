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
package com.devexperts.qd.sample;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.PentaCodec;

class TestThroughputScheme extends DefaultScheme {
    public TestThroughputScheme(TestThroughputConfig config) {
        super(new PentaCodec(), createRecords(config));
    }

    private static DataRecord[] createRecords(TestThroughputConfig config) {
        DataRecord[] result = new DataRecord[config.records];
        for (int i = 0; i < config.records; i++) {
            String record = "Record" + i;
            result[i] = new DefaultRecord(i, record, config.ifields >= 2,
                createIntFields(record, config), createObjFields(record, config));
        }
        return result;
    }

    private static DataIntField[] createIntFields(String record, TestThroughputConfig config) {
        DataIntField[] iflds = new DataIntField[config.ifields];
        for (int i = 0; i < config.ifields; i++) {
            iflds[i] = new CompactIntField(i, record + ".Int" + i);
        }
        return iflds;
    }

    private static DataObjField[] createObjFields(String record, TestThroughputConfig config) {
        DataObjField[] oflds = new DataObjField[config.ofields];
        for (int i = 0; i < config.ofields; i++) {
            oflds[i] = new MarshalledObjField(i, record + ".Obj" + i);
        }
        return oflds;
    }
}
