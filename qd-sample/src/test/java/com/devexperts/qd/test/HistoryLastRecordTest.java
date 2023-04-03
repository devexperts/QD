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
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoryLastRecordTest {
    private static final DataScheme SCHEME = new TestDataScheme(1, 20090923, TestDataScheme.Type.HAS_TIME);
    private static final int REP = 3;

    @Test
    public void testLastRecord() {
        for (int s = 1; s <= 4; s++) {
            for (int n = 2; n <= 10; n++) {
                for (int k = 1; k < s * n; k++) {
                    checkLastRecord(s, n, k, false);
                    checkLastRecord(s, n, k, true);
                }
            }
        }
    }

    private void checkLastRecord(int s, int n, int k, boolean dectime) {
        DataRecord rec = SCHEME.getRecord(0);

        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDAgent agent = history.agentBuilder().build();
        QDDistributor dist = history.distributorBuilder().build();

        // subscribe to S symbols
        SubscriptionBuffer sub = new SubscriptionBuffer();
        int sym[] = new int[s];
        for (int i = 0; i < s; i++) {
            sym[i] = SCHEME.getCodec().encode("" + (char) ('A' + i));
            sub.visitRecord(rec, sym[i], null);
        }
        agent.addSubscription(sub);

        RecordBuffer srcbuf = new RecordBuffer();
        RecordBuffer dstbuf = new RecordBuffer();

        // Do it REP times
        for (int rep = 1; rep <= REP; rep++) {
            // add S*N records
            for (int i = 0; i < s; i++) {
                for (int j = 1; j <= n; j++) {
                    int time = j + n * (rep - 1);
                    if (dectime)
                        time = n * REP + 1 - time;
                    srcbuf.add(rec, sym[i], null).setTime(time);
                }
            }
            dist.processData(srcbuf);

            // now retrieve just K items
            agent.retrieveData(new LimitedRecordSink(dstbuf, k));
            assertEquals(k * rep, dstbuf.size());
        }

        // retrieve the rest
        agent.retrieveData(dstbuf);
        assertEquals(REP * s * n, dstbuf.size());

        // check that no record was lost/duped
        dstbuf.rewind();
        boolean[][] f = new boolean[s][REP * n];
        RecordCursor cur;
        while ((cur = dstbuf.next()) != null) {
            int i = 0;
            while (sym[i] != cur.getCipher()) {
                i++;
            }
            long j = cur.getTime() - 1;
            assertTrue(j >= 0 && j < REP * n);
            f[i][(int) j] = true;
        }
        for (int i = 0; i < s; i++) {
            for (int j = 0; j < REP * n; j++) {
                assertTrue(f[i][j]);
            }
        }
    }

    private class LimitedRecordSink extends AbstractRecordSink {
        private final RecordSink dst;
        private int k;

        private LimitedRecordSink(RecordSink dst, int k) {
            this.dst = dst;
            this.k = k;
        }

        public boolean hasCapacity() {
            return k > 0;
        }

        @Override
        public void append(RecordCursor cursor) {
            dst.append(cursor);
            k--;
        }
    }
}
