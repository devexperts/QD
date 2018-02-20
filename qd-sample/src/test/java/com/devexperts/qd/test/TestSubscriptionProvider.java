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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.*;

public class TestSubscriptionProvider extends AbstractRecordProvider {
    private static final int DEFAULT_RECORD_CNT = 100;

    private final Random rnd;
    private final DataScheme scheme;
    private final int recordCnt;
    private final boolean fixedRecordCnt;
    private final String[] symbols;

    private int records_provided_count;

    TestSubscriptionProvider(DataScheme scheme, long seed) {
        this(scheme, seed, null);
    }

    TestSubscriptionProvider(DataScheme scheme, long seed, String[] symbols) {
        this.rnd = new Random(seed);
        this.scheme = scheme;
        this.recordCnt = DEFAULT_RECORD_CNT;
        this.fixedRecordCnt = false;
        this.symbols = symbols;
    }

    TestSubscriptionProvider(DataScheme scheme, long seed, int recordCnt, boolean fixedRecordCnt) {
        this.rnd = new Random(seed);
        this.scheme = scheme;
        this.recordCnt = recordCnt;
        this.fixedRecordCnt = fixedRecordCnt;
        this.symbols = null;
    }

    @Override
    public RecordMode getMode() {
        return RecordMode.HISTORY_SUBSCRIPTION;
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        int cnt = fixedRecordCnt ? recordCnt : rnd.nextInt(recordCnt) + 1;
        for (int record_no = 0; record_no < cnt; record_no++) {
            DataRecord record = scheme.getRecord(rnd.nextInt(scheme.getRecordCount()));
            String symbol;
            if (symbols == null) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0, n = rnd.nextInt(15) + 1; i < n; i++)
                    sb.append(nextChar());
                symbol = new String(sb);
            } else
                symbol = symbols[rnd.nextInt(symbols.length)];
            int cipher = scheme.getCodec().encode(symbol);
            sink.visitRecord(record, cipher, cipher == 0 ? symbol : null, 0);
            records_provided_count++;
        }
        return false;
    }

    private char nextChar() {
        int i = rnd.nextInt('Z' - 'A');
        char c = (char) ('A' + i);
        return c > 'Z' ? '*' : c; // unencodeable symbol with 1/27th probability
    }

    public int getRecordsProvidedCount() {
        return records_provided_count;
    }
}
