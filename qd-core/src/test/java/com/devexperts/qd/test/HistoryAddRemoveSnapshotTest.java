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
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import junit.framework.TestCase;

/**
 * This is a test for [QD-695] NullPointerException at AgentQueue.retrieveSnapshotForHistory.
 * NPE was happening when agent has subscription for data item, then incoming data
 * (and subscription goes to snapshot queue), then unsubscription, then subscription again.
 */
public class HistoryAddRemoveSnapshotTest extends TestCase {
    private static final int VALUE_INDEX = 2;
    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] {
            new CompactIntField(0, "Test.1"),
            new CompactIntField(1, "Test.2"),
            new CompactIntField(VALUE_INDEX, "Test.Value")
        }, new DataObjField[0]);
    private static final PentaCodec CODEC = PentaCodec.INSTANCE;
    private static final DataScheme SCHEME = new DefaultScheme(CODEC, RECORD);

    private final QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
    private final QDDistributor distributor = history.distributorBuilder().build();
    private QDAgent agent = history.agentBuilder().build();

    public void testHistoryAddRemoveSnapshot() {
        // subscribe to two symbols (IBM & MSFT)
        setSubTime("IBM", 0);
        setSubTime("MSFT", 0);
        // send some data on IBM (first!) -- goes to the head of snapshot queue
        distribute("IBM", 3, 10);
        distribute("IBM", 2, 11);
        distribute("IBM", 1, 12);
        // send some data on MSFT (second) -- goes to the tail of snaphsot queue
        distribute("MSFT", 3, 20);
        distribute("MSFT", 2, 21);
        distribute("MSFT", 1, 22);
        // Do not retrieve, but unsubscribe from MSFT (second one!)
        removeSub("MSFT");
        // subscribe on MSFT again -- not data, but still in snapshot queue!
        setSubTime("MSFT", 0);
        // expect data on IBM
        expect("IBM", 3, 10);
        expect("IBM", 2, 11);
        expect("IBM", 1, 12);
        // expect no data retrieved on MSFT (while it may still be in the snapshot queue)
        expectNothing();
    }

    private void setSubTime(String symbol, long timeSub) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        RecordCursor cursor = sub.add(RECORD, CODEC.encode(symbol), symbol);
        cursor.setTime(timeSub);
        agent.addSubscription(sub);
        sub.release();
    }

    private void distribute(String symbol, long time, int value) {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.FLAGGED_DATA);
        RecordCursor cursor = buf.add(RECORD, CODEC.encode(symbol), symbol);
        cursor.setTime(time);
        cursor.setInt(VALUE_INDEX, value);
        distributor.process(buf);
        buf.release();
    }

    private void removeSub(String symbol) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(RECORD, CODEC.encode(symbol), symbol);
        agent.removeSubscription(sub);
        sub.release();
    }

    private void expect(final String symbol, final long time, final int value) {
        final boolean[] received = {false};
        agent.retrieve(new AbstractRecordSink() {
            @Override
            public boolean hasCapacity() {
                return !received[0];
            }

            @Override
            public void append(RecordCursor cursor) {
                assertEquals("symbol", symbol, cursor.getDecodedSymbol());
                assertEquals("time", time, cursor.getTime());
                assertEquals("value", value, cursor.getInt(VALUE_INDEX));
                received[0] = true;
            }
        });
        assertTrue("received", received[0]);
    }

    private void expectNothing() {
        boolean hasMore = agent.retrieve(new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                fail();
            }
        });
        assertFalse(hasMore);
    }
}
