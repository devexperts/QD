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
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryPerformanceTest {
    // N is so big that N*N behavior will effectively "hang" this test.
    private static final int N = 30000;

    private static final DataScheme SCHEME = new TestDataScheme(20120131, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final SymbolCodec CODEC = SCHEME.getCodec();

    @Test
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
                assertEquals(symbols[i], sink.lastSymbol);
                assertEquals(time, sink.lastTime);
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
        String lastSymbol;
        long lastTime;

        OneRecordSink() {}

        @Override
        public boolean hasCapacity() {
            return count == 0;
        }

        public void append(RecordCursor cursor) {
            count++;
            lastSymbol = CODEC.decode(cursor.getCipher(), cursor.getSymbol());
            lastTime = cursor.getTime();
        }
    }
}
