/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;

public class ExamineSubTest extends QDTestBase {
    private static final DataScheme SCHEME = new TestDataScheme(6543, TestDataScheme.Type.HAS_TIME);

    public ExamineSubTest() {
    }

    public ExamineSubTest(String s) {
        super(s);
    }

    @TestHash
    @TestStriped
    public void testExamineSubTicker() {
        check(qdf.createTicker(SCHEME));
    }

    @TestHash
    @TestStriped
    public void testExamineSubStream() {
        check(qdf.createStream(SCHEME));
    }

    @TestHash
    @TestStriped
    public void testExamineSubHistory() {
        check(qdf.createHistory(SCHEME));
    }

    private void check(QDCollector collector) {
        final RecordBuffer sub = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        int size = 100;
        for (int i = 0; i < size; i++) {
            String symbol = "LongSymbol" + i;
            int cipher = SCHEME.getCodec().encode(symbol);
            sub.visitRecord(SCHEME.getRecord(0), cipher, symbol, 0);
        }
        SubscriptionMap expected = new SubscriptionMap(SCHEME, true);
        sub.newSource().retrieve(expected);
        final QDAgent agent = collector.agentBuilder().build();
        agent.addSubscription(sub.newSource());
        final SubscriptionMap actual = new SubscriptionMap(SCHEME, true);
        assertEquals(size, agent.getSubscriptionSize());
        agent.examineSubscription(new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                actual.visitRecord(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol(), cursor.getTime());
                // Concurrent removal of all subscriptions
                if (actual.getSubscriptionSize() == 10) {
                    agent.removeSubscription(sub.newSource());
                }
            }
        });
        assertEquals(0, agent.getSubscriptionSize());
        assertTrue(expected.containsAll(actual));
    }
}
