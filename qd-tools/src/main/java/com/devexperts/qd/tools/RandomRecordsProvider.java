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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.util.Decimal;

import java.util.Random;

/**
 * Generates random number of records with random values.
 */
public class RandomRecordsProvider extends AbstractRecordProvider {
    private final DataRecord[] records;
    private final SymbolList symbols;
    private final int min_records;
    private final int max_records;
    private final Random random = new Random(1); // fixed seed for consistent unit-testing
    private final int[] random_decimals = new int[10000];

    private IntFieldGen[][] int_field_gens;
    private ObjFieldGen[][] obj_field_gens;

    { // pre-generate a list of random decimals to generate data fast (Decimal.compose _is_ slow)
        for (int i = 0; i < random_decimals.length; i++)
            random_decimals[i] = Decimal.compose(random.nextInt(1000) / 100.0);
    }

    private int sequence;

    private static DataRecord[] getRecords(DataScheme scheme) {
        DataRecord[] records = new DataRecord[scheme.getRecordCount()];
        for (int i = records.length; --i >= 0;)
            records[i] = scheme.getRecord(i);
        return records;
    }

    public RandomRecordsProvider(DataRecord record, String[] symbols, int max_records) {
        this(new DataRecord[] {record}, symbols, max_records);
    }

    public RandomRecordsProvider(DataScheme scheme, String[] symbols, int max_records) {
        this(getRecords(scheme), symbols, max_records);
    }

    public RandomRecordsProvider(DataRecord[] records, String[] symbols, int max_records) {
        this.records = records;
        this.symbols = new SymbolList(symbols, records[0].getScheme().getCodec());
        this.min_records = 1;
        this.max_records = max_records;
        initFieldGens();
    }

    public RandomRecordsProvider(DataRecord[] records, SymbolList symbolList, int min_records, int max_records) {
        this.records = records;
        this.symbols = new SymbolList(symbolList);
        this.min_records = min_records;
        this.max_records = max_records;
        initFieldGens();
    }

    @Override
    public RecordMode getMode() {
        return RecordMode.DATA;
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        int recs = min_records + random.nextInt(max_records - min_records + 1);
        for (int i = recs; --i >= 0;) {
            int k = random.nextInt(symbols.size());
            String symbol = symbols.getSymbol(k);
            int cipher = symbols.getCipher(k);
            int rec_index = random.nextInt(records.length);
            DataRecord record = records[rec_index];
            sink.visitRecord(record, cipher, symbol);
            for (int j = 0; j < record.getIntFieldCount(); j++)
                sink.visitIntField(record.getIntField(j), int_field_gens[rec_index][j].nextInt());
            for (int j = 0; j < record.getObjFieldCount(); j++)
                sink.visitObjField(record.getObjField(j), obj_field_gens[rec_index][j].nextObj());
        }
        return false;
    }

    private void initFieldGens() {
        int m = records.length;
        int_field_gens = new IntFieldGen[m][];
        obj_field_gens = new ObjFieldGen[m][];
        for (int i = 0; i < m; i++) {
            int nint = records[i].getIntFieldCount();
            int_field_gens[i] = new IntFieldGen[nint];
            for (int j = 0; j < nint; j++)
                int_field_gens[i][j] = getIntFieldGen(records[i].getIntField(j));
            int nobj = records[i].getObjFieldCount();
            obj_field_gens[i] = new ObjFieldGen[nobj];
            for (int j = 0; j < nobj; j++)
                obj_field_gens[i][j] = getObjFieldGen(records[i].getObjField(j));
        }
    }

    private IntFieldGen getIntFieldGen(DataIntField field) {
        if (field.getName().endsWith(".Time"))
            return TIME_GEN;
        if (field.getName().endsWith(".Stub"))
            return STUB_GEN;
        if (field.getName().endsWith(".Sequence")) // always increasing number for unit-tests
            return SEQUENCE_GEN;
        if (field instanceof DecimalField)
            return DECIMAL_GEN;
        return INT_GEN;
    }

    private ObjFieldGen getObjFieldGen(DataObjField field) {
        if (field instanceof StringField)
            return STRING_GEN;
        return NULL_GEN;
    }

    @Override
    public void setRecordListener(RecordListener listener) {}

    //----------- framework for fast generation of next values

    interface IntFieldGen {
        int nextInt();
    }

    interface ObjFieldGen {
        Object nextObj();
    }

    final IntFieldGen TIME_GEN = () -> (int) (System.currentTimeMillis() / 1000) + random.nextInt(10) - 9;

    final IntFieldGen STUB_GEN = () -> 0;

    final IntFieldGen SEQUENCE_GEN = () -> sequence++;

    final IntFieldGen DECIMAL_GEN = () -> random_decimals[random.nextInt(random_decimals.length)];

    final IntFieldGen INT_GEN = () -> random.nextInt(100);

    final ObjFieldGen NULL_GEN = () -> null;

    final ObjFieldGen STRING_GEN = () -> "X" + (100 + random.nextInt(900));
}
