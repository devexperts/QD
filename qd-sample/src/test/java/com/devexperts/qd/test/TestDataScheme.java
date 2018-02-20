/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import java.util.Random;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;

public class TestDataScheme extends DefaultScheme {
    public enum Type {
        SIMPLE(false, 0),
        HAS_TIME(true, 2),
        HAS_TIME_AND_VALUE(true, 3);

        final boolean has_time;
        final int min_int_fields;

        Type(boolean has_time, int min_int_fields) {
            this.has_time = has_time;
            this.min_int_fields = min_int_fields;
        }
    }

    private static final int RECORD_COUNT = 100;
    private static final int MAX_INT_FIELDS = 8;
    private static final int MAX_OBJ_FIELDS = 8;

    // We use this constructor to test QDService's ability to invoke constructor on DataScheme
    // when getInstance() method is not defined.
    public TestDataScheme() {
        this(20070528);
    }

    public TestDataScheme(long seed) {
        this(RECORD_COUNT, seed, Type.SIMPLE);
    }

    public TestDataScheme(long seed, Type type) {
        this(RECORD_COUNT, seed, type);
    }

    public TestDataScheme(int record_count, long seed, Type type) {
        this(record_count, seed, type, null);
    }

    public TestDataScheme(int record_count, long seed, Type type, DataRecord record) {
        super(new PentaCodec(), createRecords(record_count, seed, type, record));
    }

    private static DataRecord[] createRecords(int record_count, long seed, Type type, DataRecord record) {
        Sequencer seq = new Sequencer(seed);
        DataRecord[] records = new DataRecord[record_count];
        for (int i = 0; i < record_count; i++)
            if (record != null && i == record.getId())
                records[i] = record;
            else {
                String name = "Record" + i;
                records[i] = new DefaultRecord(i, name, type.has_time,
                    createIntFields(seq, name, type.min_int_fields), createObjFields(seq, name));
            }
        return records;
    }

    private static DataIntField[] createIntFields(Sequencer seq, String name, int min_int_fields) {
        int cnt = Math.max(seq.nextIntFieldsCount(), min_int_fields);
        DataIntField[] fields = new DataIntField[cnt];
        for (int i = 0; i < cnt; i++)
            fields[i] = seq.nextIntField(i, name);
        return fields;
    }

    private static DataObjField[] createObjFields(Sequencer seq, String name) {
        int cnt = seq.nextObjFieldsCount();
        DataObjField[] fields = new DataObjField[cnt];
        for (int i = 0; i < cnt; i++)
            fields[i] = seq.nextObjField(i, name);
        return fields;
    }

    private static class Sequencer {
        private int field_id;
        private final Random rnd;

        Sequencer(long seed) {
            rnd = new Random(seed);
        }

        int nextFieldId() {
            return field_id++;
        }

        int nextIntFieldsCount() {
            return rnd.nextInt(MAX_INT_FIELDS + 1);
        }

        int nextObjFieldsCount() {
            return rnd.nextInt(MAX_OBJ_FIELDS + 1);
        }

        DataIntField nextIntField(int index, String name) {
            if (rnd.nextInt(2) == 0)
                return new CompactIntField(index, name + ".Field" + nextFieldId());
            else
                return new PlainIntField(index, name + ".Field" + nextFieldId());
        }

        DataObjField nextObjField(int index, String name) {
            return new StringField(index, name + ".Field" + nextFieldId());
        }
    }
}
