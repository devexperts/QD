/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
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
import com.devexperts.qd.kit.CompactCharField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.ShortStringField;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.kit.WideDecimalField;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.util.Decimal;
import com.devexperts.qd.util.TimeMarkUtil;
import com.devexperts.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Generates a random number of records with random values.
 */
public class RandomRecordsProvider extends AbstractRecordProvider {
    private static final int SIZE = 1 << 16;
    private static final int MILLIS_SHIFT = 22;
    private static final int MAX_SEQUENCE = (1 << MILLIS_SHIFT) - 1;

    private static DataRecord[] getRecords(DataScheme scheme) {
        DataRecord[] records = new DataRecord[scheme.getRecordCount()];
        for (int i = 0; i < records.length; i++) {
            records[i] = scheme.getRecord(i);
        }
        return records;
    }

    private final RecordGenerator[] generators;
    private final SymbolList symbols;
    private final int[] randomDecimals = new int[SIZE];
    private final long[] randomWideDecimals = new long[SIZE];
    private final String[] randomStrings = new String[SIZE];
    private final IntSupplier countRecords;
    private long timeMillis;
    private int timeMark;
    private int sequence;
    private int randomIndex = 0;

    { // pre-generate a list of random decimals to generate data fast (the methods #compose and #composeWide are slow)
        for (int i = 0; i < SIZE; i++) {
            randomDecimals[i] = Decimal.compose(nextInt(1000) / 100.0);
            randomWideDecimals[i] = Decimal.tinyToWide(randomDecimals[i]);
            randomStrings[i] = "X" + (100 + nextInt(900));
        }
    }

    public RandomRecordsProvider(DataRecord record, String[] symbols, int maxRecords) {
        this(new DataRecord[]{record}, symbols, maxRecords);
    }

    public RandomRecordsProvider(DataScheme scheme, String[] symbols, int maxRecords) {
        this(getRecords(scheme), symbols, maxRecords);
    }

    public RandomRecordsProvider(DataRecord[] records, String[] symbols, int maxRecords) {
        this(records, new SymbolList(symbols, records[0].getScheme().getCodec()), 1, maxRecords);
    }

    public RandomRecordsProvider(DataRecord[] records, SymbolList symbolList, int minRecords, int maxRecords) {
        generators = new RecordGenerator[records.length];
        for (int i = 0; i < generators.length; i++) {
            generators[i] = new RecordGenerator(records[i]);
        }
        this.symbols = new SymbolList(symbolList);
        if (minRecords == maxRecords) {
            this.countRecords = () -> minRecords;
        } else {
            this.countRecords = () -> minRecords + nextInt(maxRecords - minRecords + 1);
        }
    }

    @Override
    public RecordMode getMode() {
        return RecordMode.DATA;
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        timeMillis = System.currentTimeMillis();
        timeMark = TimeMarkUtil.currentTimeMark();
        int recs = countRecords.getAsInt();
        for (int i = recs; --i >= 0;) {
            generators[nextInt(generators.length)].retrieve(sink);
        }
        return false;
    }

    @Override
    public void setRecordListener(RecordListener listener) {}

    class RecordGenerator {

        private final RecordCursor.Owner owner;
        private final RecordCursor cursor;
        private final Consumer<RecordCursor>[] fieldGenerators;

        public RecordGenerator(DataRecord record) {
            owner = RecordCursor.allocateOwner(record, RecordMode.DATA.withTimeMark());
            cursor = owner.cursor();
            int nint = cursor.getIntCount();
            int nobj = cursor.getObjCount();
            List<Consumer<RecordCursor>> generators = new ArrayList<>(nint + nobj);
            for (int j = 0; j < nint; j++) {
                DataIntField intField = record.getIntField(j);
                generators.add(getIntFieldGen(intField));
                if (intField.getSerialType().isLong())
                    j++; // skip the next VoidIntField
            }
            for (int j = 0; j < nobj; j++) {
                generators.add(getObjFieldGen(record.getObjField(j)));
            }
            fieldGenerators = generators.toArray(new Consumer[0]);
        }

        void retrieve(RecordSink sink) {
            int k = nextInt(symbols.size());
            owner.setSymbol(symbols.getCipher(k), symbols.getSymbol(k));
            cursor.setTimeMark(timeMark);
            for (Consumer<RecordCursor> fieldGenerator : fieldGenerators) {
                fieldGenerator.accept(cursor);
            }
            sink.append(cursor);
        }
    }

    private Consumer<RecordCursor> getIntFieldGen(final DataIntField field) {
        final int index = field.getIndex();
        if (field.getName().endsWith("Time")) {
            if (field.getSerialType().isLong()) {
                return cursor -> cursor.setLong(index, timeMillis);
            } else {
                return cursor -> cursor.setInt(index, (int) (timeMillis / 1000L));
            }
        }
        if (field.getName().endsWith("Stub") || field.getName().endsWith("Flag") || field instanceof VoidIntField)
            return cursor -> cursor.setInt(index, 0);
        if (field instanceof CompactCharField || field instanceof ShortStringField)
            return cursor -> cursor.setInt(index, ('A' + randomIndex++ & 15));
        if (field.getName().endsWith("History.Sequence"))
            return cursor -> cursor.setInt(index, sequence++);
        if (field.getName().endsWith("Sequence")) {
            return cursor -> {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                cursor.setInt(index, TimeUtil.getMillisFromTime(timeMillis) << MILLIS_SHIFT | sequence);
            };
        }
        if (field instanceof DecimalField)
            return cursor -> cursor.setInt(index, randomDecimals[randomIndex++ & (SIZE - 1)]);
        if (field instanceof WideDecimalField)
            return cursor -> cursor.setLong(index, randomWideDecimals[randomIndex++ & (SIZE - 1)]);
        return cursor -> cursor.setInt(index, randomIndex++ & 127);
    }

    private Consumer<RecordCursor> getObjFieldGen(DataObjField field) {
        final int index = field.getIndex();
        if (field instanceof StringField)
            return cursor -> cursor.setObj(index, randomStrings[randomIndex++ & (SIZE - 1)]);
        return cursor -> cursor.setObj(index, null);
    }

    // Supper fast pseudo-random number generator
    private int seed = 0;

    private static final int MULTIPLIER = 0x3EECE66D;
    private static final int ADDEND = 0xB;

    private int nextInt(int bound) {
        return (int) (((next() & 0xFFFFFFFFL) * bound) >>> 32);
    }

    private int next() {
        seed = (int) ((seed & 0xFFFFFFFFL) * MULTIPLIER + ADDEND);
        return seed;
    }
}
