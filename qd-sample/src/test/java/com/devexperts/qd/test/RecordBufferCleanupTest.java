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

import java.util.Random;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import junit.framework.TestCase;

public class RecordBufferCleanupTest extends TestCase {
    private static final Random r = new Random(20081221);
    private static final DataScheme SCHEME = new TestDataScheme(20081221);

    private int fillAndCheckClean(RecordBuffer buf) {
        int cnt = 100 + r.nextInt(100);
        for (int i = 0; i < cnt; i++) {
            DataRecord rec = SCHEME.getRecord(i % SCHEME.getRecordCount());
            String symbol = "SYM" + i;
            RecordCursor cur = buf.add(rec, SCHEME.getCodec().encode(symbol), symbol);
            for (int j = 0; j < cur.getIntCount(); j++) {
                assertEquals(cur.getInt(j), 0);
                cur.setInt(j, j + 1);
            }
            for (int j = 0; j < cur.getObjCount(); j++) {
                assertEquals(cur.getObj(j), null);
                cur.setObj(j, j + 1);
            }
        }
        return cnt;
    }

    private void check(long position, int offset, int cnt, RecordBuffer buf) {
        long savePoisition = buf.getPosition();
        buf.setPosition(position);
        for (int i = offset; i < cnt; i++) {
            DataRecord rec = SCHEME.getRecord(i % SCHEME.getRecordCount());
            String symbol = "SYM" + i;
            RecordCursor cur = buf.next();
            assertEquals(cur.getRecord(), rec);
            assertEquals(cur.getCipher(), SCHEME.getCodec().encode(symbol));
            for (int j = 0; j < cur.getIntCount(); j++) {
                assertEquals(cur.getInt(j), j + 1);
            }
            for (int j = 0; j < cur.getObjCount(); j++) {
                assertEquals(cur.getObj(j), j + 1);
            }
        }
        buf.setPosition(savePoisition);
    }

    public void testSetLimit() {
        RecordBuffer buf = new RecordBuffer();
        int cnt1 = fillAndCheckClean(buf);
        assertEquals(cnt1, buf.size());
        check(0, 0, cnt1, buf);
        long limit = buf.getLimit();
        int cnt2 = fillAndCheckClean(buf);
        assertEquals(cnt1 + cnt2, buf.size());
        check(limit, 0, cnt2, buf);
        buf.setLimit(limit);
        assertEquals(cnt1, buf.size());
        int cnt3 = fillAndCheckClean(buf);
        assertEquals(cnt1 + cnt3, buf.size());
        check(0, 0, cnt1, buf);
        check(limit, 0, cnt3, buf);
    }

    public void testRemoveAt() {
        RecordBuffer buf = new RecordBuffer();
        int cnt1 = fillAndCheckClean(buf);
        assertEquals(cnt1, buf.size());
        long limit = buf.getLimit();
        int cnt2 = fillAndCheckClean(buf);
        assertEquals(cnt1 + cnt2, buf.size());
        check(0, 0, cnt1, buf);
        check(limit, 0, cnt2, buf);
        buf.removeAt(limit);
        assertEquals(cnt1 + cnt2 - 1, buf.size());
        check(0, 0, cnt1, buf);
        check(limit, 1, cnt2, buf);
        long limit2 = buf.getLimit();
        int cnt3 = fillAndCheckClean(buf);
        assertEquals(cnt1 + cnt2 - 1 + cnt3, buf.size());
        check(0, 0, cnt1, buf);
        check(limit, 1, cnt2, buf);
        check(limit2, 0, cnt3, buf);
    }
}
