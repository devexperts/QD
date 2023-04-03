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

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class VoidAgentTest {
    private static final DataRecord REC_0 = new DefaultRecord(0, "Rec0", true, timeFields("Rec0"), new DataObjField[0]);
    private static final DataRecord REC_1 = new DefaultRecord(1, "Rec1", true, timeFields("Rec1"), new DataObjField[0]);

    private static DataIntField[] timeFields(String recName) {
        return new DataIntField[] {
            new CompactIntField(0, recName + ".Index0", SerialFieldType.COMPACT_INT),
            new CompactIntField(1, recName + ".Index1", SerialFieldType.COMPACT_INT)
        };
    }

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, REC_0, REC_1);

    private QDAgent agent;

    @Test
    public void testTickerVoidAgent() {
        agent = QDFactory.getDefaultFactory().createVoidAgentBuilder(QDContract.TICKER, SCHEME).build();
        assertSub();

        addSub(REC_0, "TEST", 0);
        assertSub(item(REC_0, "TEST"));

        addSub(REC_0, "TEST", 0);
        assertSub(item(REC_0, "TEST"));

        addSub(REC_1, "TEST", 0);
        assertSub(item(REC_0, "TEST"), item(REC_1, "TEST"));

        addSub(REC_1, "XX", 0);
        assertSub(item(REC_0, "TEST"), item(REC_1, "TEST"), item(REC_1, "XX"));

        removeSub(REC_1, "XX");
        assertSub(item(REC_0, "TEST"), item(REC_1, "TEST"));

        removeSub(REC_0, "TEST");
        assertSub(item(REC_1, "TEST"));

        removeSub(REC_0, "TEST");
        assertSub(item(REC_1, "TEST"));

        removeSub(REC_1, "TEST");
        assertSub();
    }

    @Test
    public void testHistoryVoidAgent() {
        agent = QDFactory.getDefaultFactory().createVoidAgentBuilder(QDContract.HISTORY, SCHEME).build();
        assertSub();

        addSub(REC_0, "TEST", 1);
        assertSub(item(REC_0, "TEST", 1));

        addSub(REC_0, "TEST", 2);
        assertSub(item(REC_0, "TEST", 2));

        addSub(REC_1, "TEST", 3);
        assertSub(item(REC_0, "TEST", 2), item(REC_1, "TEST", 3));

        addSub(REC_1, "XX", 4);
        assertSub(item(REC_0, "TEST", 2), item(REC_1, "TEST", 3), item(REC_1, "XX", 4));

        removeSub(REC_1, "XX");
        assertSub(item(REC_0, "TEST", 2), item(REC_1, "TEST", 3));

        removeSub(REC_0, "TEST");
        assertSub(item(REC_1, "TEST", 3));

        removeSub(REC_0, "TEST");
        assertSub(item(REC_1, "TEST", 3));

        removeSub(REC_1, "TEST");
        assertSub();
    }

    @Test
    public void testTickerStress() {
        checkStress(QDContract.TICKER);
    }

    @Test
    public void testHistoryStress() {
        checkStress(QDContract.HISTORY);
    }

    private void checkStress(QDContract contract) {
        Random rnd = new Random(20140905);
        agent = QDFactory.getDefaultFactory().createVoidAgentBuilder(contract, SCHEME).build();
        // add
        int n = 1000;
        Map<Item, Item> items = new HashMap<>();
        for (int i = 0; i < n; i++) {
            DataRecord record = rnd.nextBoolean() ? REC_0 : REC_1;
            String symbol = Integer.toString(rnd.nextInt(10 * n));
            long time = contract == QDContract.HISTORY ? rnd.nextLong() : 0;
            addSub(record, symbol, time);
            Item item = item(record, symbol, time);
            items.put(item, item);
        }
        assertSub(items.values().toArray(new Item[items.size()]));
        // remove random half
        for (Iterator<Item> it = items.values().iterator(); it.hasNext(); ) {
            Item item = it.next();
            if (rnd.nextBoolean()) {
                removeSub(item.record, item.symbol);
                it.remove();
            }
        }
        assertSub(items.values().toArray(new Item[items.size()]));
        // remove all
        assertSub(items.values().toArray(new Item[items.size()]));
        for (Item item : items.values()) {
            removeSub(item.record, item.symbol);
        }
        assertSub();
    }

    private void assertSub(Item... items) {
        assertEquals(items.length, agent.getSubscriptionSize());
        final Map<Item, Item> map = new HashMap<>();
        agent.examineSubscription(new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                Item item = item(cursor.getRecord(), cursor.getDecodedSymbol(), cursor.getTime());
                assertNull(map.put(item, item));
            }
        });
        assertEquals(items.length, map.size());
        for (Item item : items) {
            Item other = map.get(item);
            assertNotNull("Contains " + item.toString(), other);
            assertEquals("time", item.time, other.time);
        }
    }

    private void addSub(DataRecord record, String symbol, long time) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(record, PentaCodec.INSTANCE.encode(symbol), symbol).setTime(time);
        agent.addSubscription(sub);
        sub.release();
    }

    private void removeSub(DataRecord record, String symbol) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(record, PentaCodec.INSTANCE.encode(symbol), symbol);
        agent.removeSubscription(sub);
        sub.release();
    }

    private static Item item(DataRecord record, String symbol) {
        return new Item(record, symbol, 0);
    }

    private static Item item(DataRecord record, String symbol, long time) {
        return new Item(record, symbol, time);
    }

    private static class Item {
        final DataRecord record;
        final String symbol;
        final long time;

        private Item(DataRecord record, String symbol, long time) {
            this.record = record;
            this.symbol = symbol;
            this.time = time;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return record.equals(item.record) && symbol.equals(item.symbol);
        }

        @Override
        public int hashCode() {
            return 31 * record.hashCode() + symbol.hashCode();
        }

        @Override
        public String toString() {
            return "Item{" +
                "record=" + record +
                ", symbol='" + symbol + '\'' +
                ", time=" + time +
                '}';
        }
    }
}
