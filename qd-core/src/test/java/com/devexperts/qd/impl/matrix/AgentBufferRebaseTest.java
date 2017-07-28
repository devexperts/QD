/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import java.util.Random;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.stats.QDStats;
import junit.framework.TestCase;

public class AgentBufferRebaseTest extends TestCase {
    private static final int VALUE_INDEX = 2;
    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true, new DataIntField[] {
        new VoidIntField(0, "Test.Dummy"),
        new CompactIntField(1, "Test.Index"),
        new CompactIntField(VALUE_INDEX, "Test.Value")
    }, new DataObjField[0]);
    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);
    private static final String SYMBOL = "TEST-SYMBOL";
    private static final int MAGIC = 12345678;

    public void testStream() {
        check(new TestStream());
    }

    public void testHistory() {
        check(new TestHistory());
    }

    int nDist;
    int nRetr;

    private void check(QDCollector collector) {
        QDAgent agent = collector.agentBuilder().build();
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, 0, SYMBOL);
        agent.addSubscription(sub);
        sub.release();

        QDDistributor distributor = collector.distributorBuilder().build();
        Random r = new Random(20140820);
        // distribute more and more records starting from 1
        for (int dc = 1; dc <= 100; dc++) {
            RecordBuffer buf = RecordBuffer.getInstance();
            for (int i = 0; i < dc; i++) {
                RecordCursor cursor = buf.add(RECORD, 0, SYMBOL);
                int time = ++nDist;
                cursor.setTime(time);
                cursor.setInt(VALUE_INDEX, time ^ MAGIC);
            }
            distributor.process(buf);
            buf.clear();
            // process a random no of remaining recs
            final int lim = nRetr + r.nextInt(nDist - nRetr) + 1;
            agent.retrieve(new AbstractRecordSink() {
                @Override
                public boolean hasCapacity() {
                    return nRetr < lim;
                }

                @Override
                public void append(RecordCursor cursor) {
                    int time = ++nRetr;
                    assertEquals("record", RECORD, cursor.getRecord());
                    assertEquals("cipher", 0, cursor.getCipher());
                    assertEquals("symbol", SYMBOL, cursor.getSymbol());
                    assertEquals("time", time, cursor.getTime());
                    assertEquals("value", time ^ MAGIC, cursor.getInt(VALUE_INDEX));
                }
            });
            assertEquals("nRetr", lim, nRetr);
        }
    }

    public static class TestAgentBuffer extends AgentBuffer {
        public TestAgentBuffer(Agent agent) {
            super(agent);
        }

        @Override
        int getRebaseThreshold() {
            return 100;
        }
    }

    private static class TestStream extends Stream {
        TestStream() {
            super(QDFactory.getDefaultFactory().streamBuilder()
                .withScheme(SCHEME)
                .withStats(QDStats.VOID));
        }

        @Override
        AgentBuffer createAgentBuffer(Agent agent) {
            return new TestAgentBuffer(agent);
        }
    }

    private static class TestHistory extends History {
        TestHistory() {
            super(QDFactory.getDefaultFactory().historyBuilder()
                .withScheme(SCHEME)
                .withStats(QDStats.VOID));
        }

        @Override
        AgentBuffer createAgentBuffer(Agent agent) {
            return new TestAgentBuffer(agent);
        }
    }
}
