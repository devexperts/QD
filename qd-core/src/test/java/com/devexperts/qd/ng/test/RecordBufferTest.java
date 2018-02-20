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
package com.devexperts.qd.ng.test;

import java.util.HashSet;
import java.util.Set;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import junit.framework.TestCase;

public class RecordBufferTest extends TestCase {
    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] { new CompactIntField(0, "Test.Int0"), new CompactIntField(1, "Test.Int1"), new CompactIntField(2, "Test.Int2")},
        new DataObjField[] { new MarshalledObjField(0, "Test.Obj0"), new MarshalledObjField(1, "Test.Obj1")});
    private static final DataRecord RECORD2 = new DefaultRecord(1, "Test2", true,
        new DataIntField[] { new CompactIntField(0, "Test2.Int0"), new CompactIntField(1, "Test2.Int1")},
        new DataObjField[] { new MarshalledObjField(0, "Test2.Obj0")});

    public void testGetAt() {
        RecordBuffer buf = new RecordBuffer(RecordMode.DATA.withEventTimeSequence());
        for (int i = 1; i <= 100; i++) {
            RecordCursor cur = buf.add(record(i), i, symbol(i));
            cur.setEventTimeSequence(eventTimeSequence(i));
            for (int j = 0; j < cur.getIntCount(); j++)
                cur.setInt(j, iVal(i, j));
            for (int j = 0; j < cur.getObjCount(); j++)
                cur.setObj(j, oVal(i, j));
        }
        assertEquals(100, buf.size());
        assertEquals(0, buf.getPosition());
        for (int i = 1; i <= 100; i++) {
            RecordCursor cur = buf.next();
            assertNotNull(cur);

            assertEquals(record(i), cur.getRecord());
            assertEquals(i, cur.getCipher());
            assertEquals(symbol(i), cur.getSymbol());
            assertEquals(eventTimeSequence(i), cur.getEventTimeSequence());

            long position = cur.getPosition();
            assertEquals(record(i), buf.getRecordAt(position));
            assertEquals(i, buf.getCipherAt(position));
            assertEquals(symbol(i), buf.getSymbolAt(position));
            assertEquals(eventTimeSequence(i), buf.getEventTimeSequenceAt(position));

            for (int j = 0; j < cur.getIntCount(); j++)
                assertEquals(iVal(i, j), cur.getInt(j));
            for (int j = 0; j < cur.getObjCount(); j++)
                assertEquals(oVal(i, j), cur.getObj(j));
        }
        assertNull(buf.next());
        assertEquals(buf.getLimit(), buf.getPosition());
    }

    public void testCompact() {
        Set<RecordMode> modes = new HashSet<>();
        modes.add(RecordMode.DATA);
        modes.add(RecordMode.HISTORY_SUBSCRIPTION);
        modes.add(RecordMode.SUBSCRIPTION);
        modes.add(RecordMode.MARKED_DATA);
        modes.add(RecordMode.TIMESTAMPED_DATA);
        modes.add(RecordMode.DATA.withAttachment());
        modes.add(RecordMode.DATA.withTimeMark().withEventTimeSequence().withAttachment());
        modes.add(RecordMode.SUBSCRIPTION.withTimeMark().withEventTimeSequence().withAttachment());
        for (RecordMode mode : modes) {
            RecordBuffer buf = new RecordBuffer(mode);
            for (int i = 1; i <= 100; i++) {
                RecordCursor cur = buf.add(RECORD, i, null);
                for (int j = 0; j < cur.getIntCount(); j++)
                    cur.setInt(j, iVal(i, j));
                for (int j = 0; j < cur.getObjCount(); j++)
                    cur.setObj(j, oVal(i, j));
            }
            assertEquals(100, buf.size());
            assertEquals(0, buf.getPosition());
            boolean result = buf.compact(cur -> cur.getCipher() % 2 != 0);
            assertTrue(result);
            assertEquals(50, buf.size());
            assertEquals(0, buf.getPosition());
            RecordCursor cur;
            int expect = 1;
            while ((cur = buf.next()) != null) {
                assertEquals(expect, cur.getCipher());
                for (int j = 0; j < cur.getIntCount(); j++)
                    assertEquals(iVal(expect, j), cur.getInt(j));
                for (int j = 0; j < cur.getObjCount(); j++)
                    assertEquals(oVal(expect, j), cur.getObj(j));
                expect += 2;
            }
            assertEquals(101, expect);
            assertEquals(buf.getLimit(), buf.getPosition());
        }
    }

    private DataRecord record(int i) {
        return i % 2 == 0 ? RECORD : RECORD2;
    }

    private String symbol(int i) {
        return String.valueOf(i);
    }

    private long eventTimeSequence(int i) {
        return ((long)~i << 32) + i;
    }

    private int iVal(int i, int j) {
        return 123 + i + j * 3278;
    }

    private Object oVal(int i, int j) {
        return i + "," + j;
    }

    // Cleanup 2 of 3 records
    public void testCompactCleanup1() {
        RecordBuffer buf = new RecordBuffer(RecordMode.FLAGGED_DATA);
        buf.add(RECORD, 1, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        buf.add(RECORD, 2, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        buf.add(RECORD, 3, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        assertEquals(3, buf.size());
        buf.next();
        buf.next();
        buf.compact();
        assertEquals(1, buf.size());
        assertEquals(0, buf.getPosition());
        assertEquals(0, buf.add(RECORD, 4, null).getEventFlags());
        assertEquals(0, buf.add(RECORD, 5, null).getEventFlags());
    }

    // Cleanup 1 of 3 records
    public void testCompactCleanup2() {
        RecordBuffer buf = new RecordBuffer(RecordMode.FLAGGED_DATA);
        buf.add(RECORD, 1, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        buf.add(RECORD, 2, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        buf.add(RECORD, 3, null).setEventFlags(EventFlag.REMOVE_EVENT.flag());
        assertEquals(3, buf.size());
        buf.next();
        buf.compact();
        assertEquals(2, buf.size());
        assertEquals(0, buf.getPosition());
        assertEquals(0, buf.add(RECORD, 4, null).getEventFlags());
        assertEquals(0, buf.add(RECORD, 5, null).getEventFlags());
    }

    public void testUnlink() {
        RecordBuffer buf = new RecordBuffer(RecordMode.DATA.withLink());
        RecordCursor cur;

        cur = buf.add(RECORD, 1, null);
        long pos1 = cur.getPosition();

        cur = buf.add(RECORD, 2, null);
        cur.setLinkTo(pos1);
        long pos2 = cur.getPosition();

        cur = buf.add(RECORD, 3, null);
        long pos3 = cur.getPosition();

        cur = buf.add(RECORD, 4, null);
        cur.setLinkTo(pos2);
        long pos4 = cur.getPosition();

        cur = buf.add(RECORD, 5, null);
        long pos5 = cur.getPosition();

        cur = buf.add(RECORD, 6, null);
        long pos6 = cur.getPosition();

        cur = buf.add(RECORD, 7, null);
        cur.setLinkTo(pos5);
        long pos7 = cur.getPosition();

        cur = buf.add(RECORD, 8, null);
        cur.setLinkTo(pos6);
        long pos8 = cur.getPosition();

        checkUnlinked(buf);

        buf.unlinkFrom(pos4);
        checkUnlinked(buf, 1, 2, 4);

        buf.unlinkFrom(pos8);
        checkUnlinked(buf, 1, 2, 4, 6, 8);

        buf.unlinkFrom(pos3);
        checkUnlinked(buf, 1, 2, 3, 4, 6, 8);

        buf.unlinkFrom(pos7);
        checkUnlinked(buf, 1, 2, 3, 4, 5, 6, 7, 8);

    }

    private void checkUnlinked(RecordBuffer buf, int... ciphers) {
        assertEquals(8, buf.size());
        int j = 0;
        for (int i = 1; i <= 8; i++) {
            RecordCursor cur = buf.next();
            assertEquals(i, cur.getCipher());
            if (j < ciphers.length && ciphers[j] < i)
                j++;
            boolean shallBeUnlinked = j < ciphers.length && ciphers[j] == i;
            assertEquals(shallBeUnlinked, cur.isUnlinked());
        }
        buf.rewind();
    }

    public void testAddBetweenDifferentModes() {
        RecordBuffer buf1 = RecordBuffer.getInstance(RecordMode.DATA.withLink().withEventTimeSequence());
        RecordCursor cur = buf1.add(RECORD, 0, "TEST-SYM1");
        long baseEts = 0x123456789abcdefL;
        cur.setEventTimeSequence(baseEts);
        long pos1 = cur.getPosition();
        cur = buf1.add(RECORD, 0, "TEST-SYM2");
        cur.setLinkTo(pos1);
        cur.setEventTimeSequence(baseEts << 1);
        long pos2 = cur.getPosition();
        cur = buf1.add(RECORD, 0, "TEST-SYM3");
        cur.setLinkTo(pos2);
        cur.setEventTimeSequence(baseEts << 2);
        assertEquals("buf1.size", 3, buf1.size());

        RecordBuffer buf2 = RecordBuffer.getInstance(RecordMode.MARKED_DATA.withEventTimeSequence());
        buf2.process(buf1);
        assertEquals("buf2.size", 3, buf1.size());

        cur = buf2.next();
        assertEquals("symbol1", "TEST-SYM1", cur.getSymbol());
        assertEquals("mark1", 0, cur.getTimeMark());
        assertEquals("ets1", baseEts, cur.getEventTimeSequence());

        cur = buf2.next();
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("mark2", 0, cur.getTimeMark());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());

        cur = buf2.next();
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("mark3", 0, cur.getTimeMark());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());

        assertEquals("no next", null, buf2.next());
    }

    public void testFastCopy() {
        long baseEts = 0x123456789abcdefL;
        RecordCursor cur;
        RecordBuffer buf1 = RecordBuffer.getInstance(RecordMode.DATA.withEventTimeSequence());

        cur = buf1.add(RECORD, 0, "TEST-SYM1");
        cur.setEventTimeSequence(baseEts);
        cur = buf1.add(RECORD, 0, "TEST-SYM2");
        cur.setEventTimeSequence(baseEts << 1);
        cur = buf1.add(RECORD, 0, "TEST-SYM3");
        cur.setEventTimeSequence(baseEts << 2);
        assertEquals("buf1.size", 3, buf1.size());

        RecordBuffer buf2 = RecordBuffer.getInstance(buf1.getMode());
        buf2.process(buf1);
        assertEquals("buf2.size", 3, buf2.size());

        cur = buf2.next();
        assertEquals("symbol1", "TEST-SYM1", cur.getSymbol());
        assertEquals("ets1", baseEts, cur.getEventTimeSequence());
        cur = buf2.next();
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());
        cur = buf2.next();
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());
        assertEquals("no next", null, buf2.next());

        buf1.rewind();
        buf1.next();
        RecordBuffer buf3 = RecordBuffer.getInstance(buf1.getMode());
        buf3.process(buf1);
        assertEquals("buf3.size", 2, buf3.size());

        cur = buf3.next();
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());
        cur = buf3.next();
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());
        assertEquals("no next", null, buf3.next());
    }

    public void testVoid() {
        assertEquals(null, RecordSource.VOID.current());
        assertEquals(null, RecordSource.VOID.next());
        assertEquals(0, RecordSource.VOID.getPosition());
        assertEquals(0, RecordSource.VOID.getLimit());
    }
}
