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
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests that collector implementations are robust in presence of exceptions thrown by record sink.
 */
public class CollectorRobustnessTest {
    private static final DataScheme SCHEME = new TestDataScheme(20140506, TestDataScheme.Type.HAS_TIME);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SYMBOL_CRASH = "SYMBOL_CRASH";
    private static final String SYMBOL_OK = "SYMBOL_OK";

    @Test
    public void testTicker() {
        checkCollector(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testStream() {
        checkCollector(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

    @Test
    public void testHistory() {
        checkCollector(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void checkCollector(QDCollector collector) {
        // create agent with subscription
        QDAgent agent = collector.agentBuilder().build();
        RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, 0, SYMBOL_CRASH);
        sub.add(RECORD, 0, SYMBOL_OK);
        agent.setSubscription(sub);
        Listener listener = new Listener();
        agent.setRecordListener(listener);
        assertEquals(0, listener.availableCnt);
        // feed records into collector by passing the subscription-mode buffer as data
        sub.rewind();
        collector.distributorBuilder().build().process(sub);
        // ensure one notification
        assertEquals(1, listener.availableCnt);
        // try to retrieve data from agent with exception
        try {
            agent.retrieve(listener);
            fail("Cannot complete normally");
        } catch (RuntimeException e) {
            // was expected
            assertEquals("Crash", e.getMessage());
        }
        assertEquals(1, listener.crashCnt);
        assertEquals(0, listener.okCnt);
        assertEquals(0, listener.otherCnt);
        assertEquals(1, listener.availableCnt);
        // should retrieve next data record on next retrieve attempt
        agent.retrieve(listener);
        assertEquals(1, listener.crashCnt);
        assertEquals(1, listener.okCnt);
        assertEquals(0, listener.otherCnt);
        assertEquals(1, listener.availableCnt);
    }

    private static class Listener extends AbstractRecordSink implements RecordListener {
        int crashCnt;
        int okCnt;
        int otherCnt;
        int availableCnt;

        @Override
        public void append(RecordCursor cursor) {
            if (cursor.getCipher() != 0) {
                otherCnt++;
                return;
            }
            String symbol = cursor.getSymbol();
            if (symbol == SYMBOL_CRASH) {
                crashCnt++;
                throw new RuntimeException("Crash");
            } else if (symbol == SYMBOL_OK) {
                okCnt++;
            } else {
                otherCnt++;
            }
        }

        public void recordsAvailable(RecordProvider provider) {
            availableCnt++;
        }
    }
}
