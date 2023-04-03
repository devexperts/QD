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
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;

import java.util.Random;

public class TestDataProvider extends AbstractRecordProvider {
    private final Random rnd;
    private final TestSubscriptionProvider subscription;

    private int recordsProvidedCount;

    public TestDataProvider(DataScheme scheme, long seed) {
        this.rnd = new Random(seed + 1);
        this.subscription = new TestSubscriptionProvider(scheme, seed);
    }

    public TestDataProvider(DataScheme scheme, long seed, String[] symbols) {
        this.rnd = new Random(seed + 1);
        this.subscription = new TestSubscriptionProvider(scheme, seed, symbols);
    }

    public TestDataProvider(DataScheme scheme, long seed, int recordCnt) {
        this.rnd = new Random(seed + 1);
        this.subscription = new TestSubscriptionProvider(scheme, seed, recordCnt, true);
    }

    public TestDataProvider(DataScheme scheme, long seed, int recordCnt, boolean fixedRecordCnt) {
        this.rnd = new Random(seed + 1);
        this.subscription = new TestSubscriptionProvider(scheme, seed, recordCnt, fixedRecordCnt);
    }

    @Override
    public RecordMode getMode() {
        return RecordMode.DATA;
    }

    @Override
    public boolean retrieve(final RecordSink sink) {
        return subscription.retrieve(new AbstractRecordSink() {
            // temporary buffer to prepare data record for the sink
            RecordBuffer dataBuf = new RecordBuffer();

            @Override
            public boolean hasCapacity() {
                return sink.hasCapacity();
            }

            @Override
            public void append(RecordCursor cursor) {
                DataRecord record = cursor.getRecord();
                dataBuf.clear();
                RecordCursor dataCur = dataBuf.add(
                    record, cursor.getCipher(), (cursor.getCipher() == 0 ? cursor.getSymbol() : null));
                for (int i = 0, n = record.getIntFieldCount(); i < n; i++) {
                    int value = rnd.nextInt();
                    if (record.hasTime() && i == 0)
                        value = Math.abs(value); // make sure we only generate positive time
                    dataCur.setInt(i, value);
                }
                for (int i = 0, n = record.getObjFieldCount(); i < n; i++)
                    dataCur.setObj(i, "String" + rnd.nextInt());
                sink.append(dataCur);
                recordsProvidedCount++;
            }
        });
    }

    public int getRecordsProvidedCount() {
        return recordsProvidedCount;
    }
}
