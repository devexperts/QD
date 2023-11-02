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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.MarshalledObjField;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RecordModeBufAppendTest {
    private final DataRecord record = new DefaultRecord(
        0, "Test", true,
        new DataIntField[] {
            new CompactIntField(0, "Test.I0"),
            new CompactIntField(1, "Test.I1"),
            new CompactIntField(2, "Test.I2"),
        },
        new DataObjField[] {
            new MarshalledObjField(0, "Test.O0"),
            new MarshalledObjField(1, "Test.O1"),
        }
    );

    @Test
    public void testDataToSubscription() {
        RecordBuffer buf0 = new RecordBuffer();
        RecordBuffer buf1 = new RecordBuffer(RecordMode.SUBSCRIPTION);

        assertEmptyData(buf0.add(record, 0, "TEST-SYM"), 0, "TEST-SYM");
        assertEmptyData(buf0.add(record, 1234, null), 1234, null);

        copyBuf0to1(buf0, buf1);

        assertSub(buf1.next(), 0, "TEST-SYM");
        assertSub(buf1.next(), 1234, null);
        assertNull(buf1.next());
    }

    @Test
    public void testDataToHistorySubscription() {
        RecordBuffer buf0 = new RecordBuffer();
        RecordBuffer buf1 = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        RecordCursor cur;

        cur = buf0.add(record, 0, "TEST-SYM");
        cur.setInt(0, 55);
        cur.setInt(1, 66);
        assertHistoryData(cur, 0, "TEST-SYM", 55, 66);

        cur = buf0.add(record, 1234, null);
        cur.setInt(0, 77);
        cur.setInt(1, 88);
        assertHistoryData(cur, 1234, null, 77, 88);

        copyBuf0to1(buf0, buf1);

        assertHistorySub(buf1.next(), 0, "TEST-SYM", 55, 66);
        assertHistorySub(buf1.next(), 1234, null, 77, 88);
        assertNull(buf1.next());
    }

    @Test
    public void testSubscriptionToData() {
        RecordBuffer buf0 = new RecordBuffer(RecordMode.SUBSCRIPTION);
        RecordBuffer buf1 = new RecordBuffer();

        assertSub(buf0.add(record, 0, "TEST-SYM"), 0, "TEST-SYM");
        assertSub(buf0.add(record, 1234, null), 1234, null);

        copyBuf0to1(buf0, buf1);

        assertEmptyData(buf1.next(), 0, "TEST-SYM");
        assertEmptyData(buf1.next(), 1234, null);

        assertNull(buf1.next());
    }

    @Test
    public void testHistorySubscriptionToData() {
        RecordBuffer buf0 = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        RecordBuffer buf1 = new RecordBuffer();
        RecordCursor cur;

        cur = buf0.add(record, 0, "TEST-SYM");
        cur.setInt(0, 55);
        cur.setInt(1, 66);
        assertHistorySub(cur, 0, "TEST-SYM", 55, 66);

        cur = buf0.add(record, 1234, null);
        cur.setInt(0, 77);
        cur.setInt(1, 88);
        assertHistorySub(cur, 1234, null, 77, 88);

        copyBuf0to1(buf0, buf1);

        assertHistoryData(buf1.next(), 0, "TEST-SYM", 55, 66);
        assertHistoryData(buf1.next(), 1234, null, 77, 88);
        assertNull(buf1.next());
    }

    @Test
    public void testHistorySubscriptionToSubscription() {
        RecordBuffer buf0 = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        RecordBuffer buf1 = new RecordBuffer(RecordMode.SUBSCRIPTION);
        RecordCursor cur;

        cur = buf0.add(record, 0, "TEST-SYM");
        cur.setInt(0, 55);
        cur.setInt(1, 66);
        assertHistorySub(cur, 0, "TEST-SYM", 55, 66);

        cur = buf0.add(record, 1234, null);
        cur.setTime((77L << 32) + 88);
        assertHistorySub(cur, 1234, null, 77, 88);

        copyBuf0to1(buf0, buf1);

        assertSub(buf1.next(), 0, "TEST-SYM");
        assertSub(buf1.next(), 1234, null);
        assertNull(buf1.next());
    }

    @Test
    public void testSubscriptionToHistorySubscription() {
        RecordBuffer buf0 = new RecordBuffer(RecordMode.SUBSCRIPTION);
        RecordBuffer buf1 = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);

        assertSub(buf0.add(record, 0, "TEST-SYM"), 0, "TEST-SYM");
        assertSub(buf0.add(record, 1234, null), 1234, null);

        copyBuf0to1(buf0, buf1);

        assertHistorySub(buf1.next(), 0, "TEST-SYM", 0, 0);
        assertHistorySub(buf1.next(), 1234, null, 0, 0);
        assertNull(buf1.next());
    }

    private void copyBuf0to1(RecordBuffer buf0, RecordBuffer buf1) {
        assertEquals(2, buf0.size());
        assertEquals(0, buf1.size());
        buf1.process(buf0);
        assertEquals(2, buf0.size());
        assertEquals(2, buf1.size());
    }

    private void assertSub(RecordCursor cur, int cipher, String symbol) {
        assertNotNull(cur);
        assertEquals(record, cur.getRecord());
        assertEquals(cipher, cur.getCipher());
        assertEquals(symbol, cur.getSymbol());
        assertEquals(0, cur.getIntCount());
        assertEquals(0, cur.getObjCount());
    }

    private void assertHistorySub(RecordCursor cur, int cipher, String symbol, int i0, int i1) {
        assertNotNull(cur);
        assertEquals(record, cur.getRecord());
        assertEquals(cipher, cur.getCipher());
        assertEquals(symbol, cur.getSymbol());
        assertEquals(2, cur.getIntCount());
        assertEquals(0, cur.getObjCount());
        assertEquals(i0, cur.getInt(0));
        assertEquals(i1, cur.getInt(1));
    }

    private void assertEmptyData(RecordCursor cur, int cipher, String symbol) {
        assertNotNull(cur);
        assertEquals(record, cur.getRecord());
        assertEquals(cipher, cur.getCipher());
        assertEquals(symbol, cur.getSymbol());
        assertEquals(3, cur.getIntCount());
        assertEquals(2, cur.getObjCount());
        assertEquals(0, cur.getInt(0));
        assertEquals(0, cur.getInt(1));
        assertEquals(0, cur.getInt(2));
        assertNull(cur.getObj(0));
        assertNull(cur.getObj(1));
    }

    private void assertHistoryData(RecordCursor cur, int cipher, String symbol, int i0, int i1) {
        assertNotNull(cur);
        assertEquals(record, cur.getRecord());
        assertEquals(cipher, cur.getCipher());
        assertEquals(symbol, cur.getSymbol());
        assertEquals(3, cur.getIntCount());
        assertEquals(2, cur.getObjCount());
        assertEquals(i0, cur.getInt(0));
        assertEquals(i1, cur.getInt(1));
        assertEquals(0, cur.getInt(2));
        assertNull(cur.getObj(0));
        assertNull(cur.getObj(1));
    }
}
