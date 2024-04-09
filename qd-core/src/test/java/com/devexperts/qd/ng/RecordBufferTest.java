/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RecordBufferTest {

    private static final int BUFFER_SIZE = 10;

    private static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] { new CompactIntField(0, "Test.Int0"), new CompactIntField(1, "Test.Int1"),
            new CompactIntField(2, "Test.Int2")},
        new DataObjField[] { new MarshalledObjField(0, "Test.Obj0"), new MarshalledObjField(1, "Test.Obj1")});

    private static final DataRecord RECORD2 = new DefaultRecord(1, "Test2", true,
        new DataIntField[] { new CompactIntField(0, "Test2.Int0"), new CompactIntField(1, "Test2.Int1")},
        new DataObjField[] { new MarshalledObjField(0, "Test2.Obj0")});

    private static final DataRecord RECORD_REPLACE = new DefaultRecord(0, "Test_Replace", true,
        new DataIntField[] { new CompactIntField(0, "Test_Replace.Int0"), new CompactIntField(1, "Test_Replace.Int1"),
            new CompactIntField(2, "Test_Replace.Int2")},
        new DataObjField[] {
            new MarshalledObjField(0, "Test_Replace.Obj0"), new MarshalledObjField(1, "Test_Replace.Obj1")});

    private static final DataRecord RECORD_REPLACE2 = new DefaultRecord(1, "Test_Replace2", true,
        new DataIntField[] {
            new CompactIntField(0, "Test_Replace2.Int0"), new CompactIntField(1, "Test_Replace2.Int1")},
        new DataObjField[] { new MarshalledObjField(0, "Test_Replace2.Obj0")});

    @Test
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

    @Test
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
    @Test
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
    @Test
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

    @Test
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
            assertNotNull(cur);
            assertEquals(i, cur.getCipher());
            if (j < ciphers.length && ciphers[j] < i)
                j++;
            boolean shallBeUnlinked = j < ciphers.length && ciphers[j] == i;
            assertEquals(shallBeUnlinked, cur.isUnlinked());
        }
        buf.rewind();
    }

    @Test
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
        assertNotNull(cur);
        assertEquals("symbol1", "TEST-SYM1", cur.getSymbol());
        assertEquals("mark1", 0, cur.getTimeMark());
        assertEquals("ets1", baseEts, cur.getEventTimeSequence());

        cur = buf2.next();
        assertNotNull(cur);
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("mark2", 0, cur.getTimeMark());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());

        cur = buf2.next();
        assertNotNull(cur);
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("mark3", 0, cur.getTimeMark());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());

        assertNull("no next", buf2.next());
    }

    @Test
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
        assertNotNull(cur);
        assertEquals("symbol1", "TEST-SYM1", cur.getSymbol());
        assertEquals("ets1", baseEts, cur.getEventTimeSequence());
        cur = buf2.next();
        assertNotNull(cur);
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());
        cur = buf2.next();
        assertNotNull(cur);
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());
        assertNull("no next", buf2.next());

        buf1.rewind();
        buf1.next();
        RecordBuffer buf3 = RecordBuffer.getInstance(buf1.getMode());
        buf3.process(buf1);
        assertEquals("buf3.size", 2, buf3.size());

        cur = buf3.next();
        assertNotNull(cur);
        assertEquals("symbol2", "TEST-SYM2", cur.getSymbol());
        assertEquals("ets2", baseEts << 1, cur.getEventTimeSequence());
        cur = buf3.next();
        assertNotNull(cur);
        assertEquals("symbol3", "TEST-SYM3", cur.getSymbol());
        assertEquals("ets3", baseEts << 2, cur.getEventTimeSequence());
        assertNull("no next", buf3.next());
    }

    @Test
    public void testVoid() {
        assertNull(RecordSource.VOID.current());
        assertNull(RecordSource.VOID.next());
        assertEquals(0, RecordSource.VOID.getPosition());
        assertEquals(0, RecordSource.VOID.getLimit());
    }

    @Test
    public void removeRecordsExceptions() {
        RecordBuffer buf = createRecordBuffer();
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeAt(Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeAt(Long.MIN_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeRange(0, Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeRange(Long.MIN_VALUE, Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeRange(30, 10));
        assertThrows(IndexOutOfBoundsException.class, () -> buf.removeRange(0, Long.MAX_VALUE));
    }

    @Test
    public void removeEmptyRange() {
        RecordBuffer buf = createRecordBuffer();
        int size = buf.size();
        buf.removeRange(0, 0);
        assertEquals(size, buf.size());
        buf.removeRange(5, 5);
        assertEquals(size, buf.size());

        for (; buf.hasNext(); buf.next()) {}
        long start = buf.getPosition();
        buf.removeRange(start, start);
        assertEquals(size, buf.size());
    }

    @Test
    public void removeRecordAtAfterFullIteration() {
        for (int removeIndex = 1; removeIndex < BUFFER_SIZE; removeIndex++) {
            removeRecordFullIteration(removeIndex);
        }
    }

    @Test
    public void removeRecordAtCursor() {
        for (int removeIndex = 1; removeIndex < BUFFER_SIZE; removeIndex++) {
            for (int cursorPosition = 1; cursorPosition < BUFFER_SIZE; cursorPosition++) {
                removeRecordAtCursorIntegrity(cursorPosition, removeIndex);
            }
        }
    }

    @Test
    public void removeRecordBufPosition() {
        for (int removeIndex = 1; removeIndex < BUFFER_SIZE; removeIndex++) {
            for (int bufferPosition = 1; bufferPosition < BUFFER_SIZE; bufferPosition++) {
                removeRecordAtBufPositionIntegrity(bufferPosition, bufferPosition);
            }
        }
    }

    @Test
    public void removeDifferentRangesOfRecords() {
        for (int start = 1; start < BUFFER_SIZE; start++) {
            for (int end = start; end <= BUFFER_SIZE; end++) {
                removeRangeOfRecords(start, end);
            }
        }
    }

    @Test
    public void removeRangeOfRecordsCursor() {
        for (int start = 1; start < BUFFER_SIZE; start++) {
            for (int end = start; end <= BUFFER_SIZE; end++) {
                for (int position = 1; position < BUFFER_SIZE; position++) {
                    removeRecordAtCursorIntegrityRange(start, end, position);
                }
            }
        }
    }

    @Test
    public void removeRangeOfRecordsBufPosition() {
        for (int start = 1; start < BUFFER_SIZE; start++) {
            for (int end = start; end <= BUFFER_SIZE; end++) {
                for (int position = 1; position < BUFFER_SIZE; position++) {
                    removeRecordAtBufPositionIntegrityRange(start, end, position);
                }
            }
        }
    }

    @Test
    public void replaceWithUninitializedCursors() {
        RecordBuffer buf = createRecordBuffer();
        // create a copy in a way that does not initialize readCursor nor writeCursor
        RecordBuffer copy = new RecordBuffer(buf.getMode());
        copy.process(buf);

        // test when both cursors are uninitialized
        copy.replaceSymbolAt(0, 0, "SYMBOL_REPLACE1");
        copy.replaceRecordAt(0, RECORD_REPLACE2);
        // initialize readCursor but not writeCursor
        copy.current();
        copy.replaceSymbolAt(0, 0, "SYMBOL_REPLACE2");
        copy.replaceRecordAt(0, RECORD2);
        // initialize writeCursor
        copy.writeCurrent();
        copy.replaceSymbolAt(0, 0, "SYMBOL_REPLACE3");
        copy.replaceRecordAt(0, RECORD_REPLACE2);
    }

    @Test
    public void replaceSymbolAt() {
        RecordBuffer buf = createRecordBuffer();
        for (int i = 0; buf.hasNext(); i++) {
            long bufferPosition = buf.getPosition();
            RecordCursor readCursor = buf.current();
            RecordCursor writeCursor = buf.writeNext();

            assertNotNull(readCursor);
            assertNotNull(writeCursor);

            String symbol = "SYMBOL_REPLACE_" + i;
            int cipher = symbol.hashCode();

            buf.replaceSymbolAt(bufferPosition, cipher, symbol);

            assertEquals(buf.getSymbolAt(bufferPosition), symbol);
            assertEquals(buf.getCipherAt(bufferPosition), cipher);

            long writePosition = writeCursor.getPosition();
            buf.replaceSymbolAt(writePosition, cipher, symbol);

            assertEquals(readCursor.getSymbol(), symbol);
            assertEquals(readCursor.getCipher(), cipher);

            assertEquals(writeCursor.getSymbol(), symbol);
            assertEquals(writeCursor.getCipher(), cipher);
        }
    }

    @Test
    public void replaceRecordAt() {
        RecordBuffer buf = createRecordBuffer();
        while (buf.hasNext()) {
            long bufferPosition = buf.getPosition();
            RecordCursor readCursor = buf.current();
            RecordCursor writeCursor = buf.writeNext();

            assertNotNull(readCursor);
            assertNotNull(writeCursor);

            DataRecord record = buf.getRecordAt(bufferPosition);

            if (record == RECORD) {
                buf.replaceRecordAt(bufferPosition, RECORD_REPLACE);

                assertEquals(readCursor.getRecord(), RECORD_REPLACE);
                assertEquals(writeCursor.getRecord(), RECORD_REPLACE);
            } else if (record == RECORD2) {
                buf.replaceRecordAt(bufferPosition, RECORD_REPLACE2);

                assertEquals(readCursor.getRecord(), RECORD_REPLACE2);
                assertEquals(writeCursor.getRecord(), RECORD_REPLACE2);
            }
        }
    }

    private void removeRangeOfRecords(int start, int end) {
        RecordBuffer buf = createRecordBuffer();

        long startPosition = 0;
        long endPosition = -1;
        List<String> records = new ArrayList<>();
        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                if (i == start) {
                    startPosition = cursor.getPosition();
                }
                if (i == end) {
                    endPosition = cursor.getPosition();
                }
                if (i < start || i >= end) {
                    records.add(createStr(cursor));
                }
            }
        }

        // next position after last element
        if (endPosition == -1) {
            endPosition = buf.getPosition();
        }

        buf.removeRange(startPosition, endPosition);
        buf.rewind();

        assertEquals(buf.size(), records.size());
        for (int i = 0; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            assertNotNull(cursor);
            assertEquals(createStr(cursor), records.get(i));
        }
    }

    private void removeRecordAtCursorIntegrityRange(int start, int end, int cursorPosition) {
        RecordBuffer buf = createRecordBuffer();

        long startPosition = 0;
        long endPosition = -1;
        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                if (i == start) {
                    startPosition = cursor.getPosition();
                }
                if (i == end) {
                    endPosition = cursor.getPosition();
                }
            }
        }

        // next position after last element
        if (endPosition == -1) {
            endPosition = buf.getPosition();
        }

        buf.rewind();

        RecordCursor read = null;
        RecordCursor write = null;

        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                long tmp = cursor.getPosition();
                if (i == cursorPosition) {
                    read = buf.cursorAt(tmp);
                    write = buf.writeCursorAt(tmp);
                    break;
                }
            }
        }

        assertNotNull(read);
        assertNotNull(write);

        String readCursorStr = createStr(read);
        String writeCursorStr = createStr(write);

        buf.removeRange(startPosition, endPosition);

        if (cursorPosition >= start && cursorPosition < end) {
            assertEquals(getCursorIntOffset(read), -2000000000);
            assertEquals(getCursorObjOffset(read), -2000000000);
            assertEquals(getCursorIntOffset(write), -2000000000);
            assertEquals(getCursorObjOffset(write), -2000000000);
        } else {
            assertEquals(readCursorStr, createStr(read));
            assertEquals(writeCursorStr, createStr(write));
        }
    }

    private void removeRecordAtBufPositionIntegrityRange(int start, int end, int bufferPosition) {
        RecordBuffer buf = createRecordBuffer();
        int count = buf.size();

        long startPosition = 0;
        long endPosition = -1;
        List<String> records = new ArrayList<>();
        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                records.add(createStr(cursor));
                if (i == start) {
                    startPosition = cursor.getPosition();
                }
                if (i == end) {
                    endPosition = cursor.getPosition();
                }
            }
        }

        // next position after last element
        if (endPosition == -1) {
            endPosition = buf.getPosition();
        }

        buf.rewind();

        for (int i = 1; buf.hasNext() && i < bufferPosition; i++) {
            buf.next();
        }

        RecordCursor cursor = buf.current();
        assertNotNull(cursor);
        String cursorStr = createStr(cursor);

        // remove records
        buf.removeRange(startPosition, endPosition);

        cursor = buf.current();
        if (bufferPosition < start ||  bufferPosition >= end) {
            // same record
            assertNotNull(cursor);
            assertEquals(cursorStr, createStr(cursor));
        } else if (end != count + 1) {
            // next record
            assertNotNull(cursor);
            assertEquals(records.get(end - 1), createStr(cursor));
        } else {
            // null, removed last record
            assertNull(cursor);
        }
    }

    public void removeRecordFullIteration(int removeAt) {
        RecordBuffer buf = createRecordBuffer();

        long position = 0;
        List<String> records = new ArrayList<>();
        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                if (i == removeAt) {
                    position = cursor.getPosition();
                } else {
                    records.add(createStr(cursor));
                }
            }
        }

        // remove element
        buf.removeAt(position);

        buf.rewind();

        assertEquals(buf.size(), records.size());
        for (int i = 0; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            assertNotNull(cursor);
            assertEquals(createStr(cursor), records.get(i));
        }
    }

    private void removeRecordAtCursorIntegrity(int removeIndex, int cursorPosition) {
        RecordBuffer buf = createRecordBuffer();
        int count = buf.size();

        long removeAt = 0;

        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                long tmp = cursor.getPosition();
                if (i == removeIndex) {
                    removeAt = tmp;
                    break;
                }
            }
        }

        buf.rewind();

        RecordCursor read = null;
        RecordCursor write = null;

        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                long tmp = cursor.getPosition();
                if (i == cursorPosition) {
                    read = buf.cursorAt(tmp);
                    write = buf.writeCursorAt(tmp);
                    break;
                }
            }
        }

        assertNotNull(read);
        assertNotNull(write);

        String readCursorStr = createStr(read);
        String writeCursorStr = createStr(write);

        // remove element
        buf.removeAt(removeAt);

        // check cursors look at correct element
        assertEquals(buf.size(), count - 1);

        if (removeIndex == cursorPosition) {
            assertEquals(getCursorIntOffset(read), -2000000000);
            assertEquals(getCursorObjOffset(read), -2000000000);
            assertEquals(getCursorIntOffset(write), -2000000000);
            assertEquals(getCursorObjOffset(write), -2000000000);
        } else {
            try {
                assertEquals(readCursorStr, createStr(read));
                assertEquals(writeCursorStr, createStr(write));
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    private void removeRecordAtBufPositionIntegrity(int removeIndex, int bufferPosition) {
        RecordBuffer buf = createRecordBuffer();
        int count = buf.size();

        long removeAt = 0;

        List<String> records = new ArrayList<>();
        for (int i = 1; buf.hasNext(); i++) {
            RecordCursor cursor = buf.next();
            if (cursor != null) {
                records.add(createStr(cursor));
                long tmp = cursor.getPosition();
                if (i == removeIndex) {
                    removeAt = tmp;
                }
            }
        }

        buf.rewind();

        for (int i = 1; buf.hasNext() && i < bufferPosition; i++) {
            buf.next();
        }

        RecordCursor cursor = buf.current();
        assertNotNull(cursor);
        String cursorStr = createStr(cursor);

        // remove element
        buf.removeAt(removeAt);

        // check buffer position look at correct element
        assertEquals(buf.size(), count - 1);

        cursor = buf.current();
        if (removeIndex != bufferPosition) {
            // same record
            assertNotNull(cursor);
            assertEquals(cursorStr, createStr(cursor));
        } else if (bufferPosition < count) {
            // next record
            assertNotNull(cursor);
            assertEquals(records.get(bufferPosition), createStr(cursor));
        } else {
            // null, removed last record
            assertNull(cursor);
        }
    }

    private RecordBuffer createRecordBuffer() {
        RecordBuffer buf = new RecordBuffer(RecordMode.DATA.withEventTimeSequence());
        int chipper = 1000000000;
        for (int i = 1; i < BUFFER_SIZE; i++) {
            RecordCursor cur = buf.add(record(i), chipper + i, "SYMBOL_" + i);
            cur.setEventTimeSequence(eventTimeSequence(i));
            for (int j = 0; j < cur.getIntCount(); j++) {
                cur.setInt(j, iVal(i, j));
            }
            for (int j = 0; j < cur.getObjCount(); j++) {
                cur.setObj(j, oVal(i, j));
            }
        }
        return buf;
    }

    private String createStr(RecordCursor cursor) {
        StringBuilder sb = new StringBuilder();
        sb.append(cursor.getCipher()).append("-")
            .append(cursor.getIntCount()).append("-")
            .append(cursor.getObjCount()).append("---");
        for (int i = 0; i < cursor.getIntCount(); i++) {
            sb.append(cursor.getInt(i)).append("-");
        }
        for (int i = 0; i < cursor.getObjCount(); i++) {
            sb.append(cursor.getObj(i)).append("-");
        }
        return sb.toString();
    }

    private int getCursorIntOffset(RecordCursor cursor) {
        return cursor.getIntPositionInternal() + cursor.getMode().intBufOffset;
    }

    private int getCursorObjOffset(RecordCursor cursor) {
        return cursor.getObjPositionInternal() + cursor.getMode().objBufOffset;
    }
}
