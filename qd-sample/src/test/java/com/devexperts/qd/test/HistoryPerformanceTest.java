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

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import junit.framework.TestCase;

public class HistoryPerformanceTest extends TestCase {
    // N is so big that N*N behavior will effectively "hang" this test.
    private static final int N = 30000;

    private static final DataScheme SCHEME = new TestDataScheme(20120131, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final SymbolCodec CODEC = SCHEME.getCodec();

    public void testDataRetrieve() {
        String[] symbols = genSymbols();
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        // Subscribe to N symbols
        QDAgent agent = history.agentBuilder().build();
        agent.setSubscription(genSub(symbols));
        for (long time = 1; time <= 2; time++) {
            // Generate records for N symbols
            QDDistributor dist = history.distributorBuilder().build();
            dist.processData(genData(symbols, time));
            // Retrieve N times one record at a time
            OneRecordSink sink = new OneRecordSink();
            for (int i = 0; i < N; i++) {
                agent.retrieveData(sink);
                assertEquals(1, sink.count);
                assertEquals(symbols[i], sink.last_symbol);
                assertEquals(time, sink.last_time);
                sink.count = 0;
            }
        }
    }

    private RecordBuffer genData(String[] symbols, long time) {
        RecordBuffer buf = RecordBuffer.getInstance();
        for (int i = 0; i < N; i++)
            buf.add(RECORD, CODEC.encode(symbols[i]), symbols[i]).setTime(time);
        return buf;
    }

    private SubscriptionBuffer genSub(String[] symbols) {
        SubscriptionBuffer sub = new SubscriptionBuffer();
        for (int i = 0; i < N; i++)
            sub.visitRecord(RECORD, CODEC.encode(symbols[i]), symbols[i]);
        return sub;
    }

    private String[] genSymbols() {
        String[] symbols = new String[N];
        for (int i = 0; i < N; i++)
            symbols[i] = Integer.toString(i);
        return symbols;
    }

    private static class OneRecordSink extends AbstractRecordSink {
        int count;
        String last_symbol;
        long last_time;

        OneRecordSink() {}

        @Override
        public boolean hasCapacity() {
            return count == 0;
        }

        public void append(RecordCursor cursor) {
            count++;
            last_symbol = CODEC.decode(cursor.getCipher(), cursor.getSymbol());
            last_time = cursor.getTime();
        }
    }
}
