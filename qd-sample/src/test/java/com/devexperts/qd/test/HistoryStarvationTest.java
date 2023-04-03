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
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test ensures that new subscribers that receive data from global history buffer do no starve and
 * are balanced properly in retrieveData with subscribers that receive data from local buffer.
 * See [QD-518] New history subscribers are starved under load of data updates.
 */
public class HistoryStarvationTest {
    // Must be in sync with com.devexperts.qd.impl.matrix.impl.RETRIEVE_BATCH_SIZE=100
    private static final int HISTORY_RETRIEVE_BATCH_SIZE = 100;

    // HISTORY_RETRIEVE_BATCH_SIZE * COUNT must be more than com.devexperts.qd.impl.matrix.impl.SNAPSHOT_BATCH_SIZE=10000
    private static final int COUNT = 200;

    private static final DataScheme SCHEME = new TestDataScheme(1, 20140228, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final SymbolCodec CODEC = SCHEME.getCodec();
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SNAPSHOT_SYMBOL1 = "SNAPSHOT1";
    private static final String SNAPSHOT_SYMBOL2 = "SNAPSHOT2";
    private static final String UPDATED_SYMBOL = "UPDATED";

    @Test
    public void testHistoryStarvation() {
        // make sure that history does not starve in different data update modes
//      for (int b1 = 1; b1 <= 5; b1++)
//          for (int b2 = 0; b2 <= 5; b2++)
//              checkHistoryStarvation(b1 * HISTORY_RETRIEVE_BATCH_SIZE, b2 * HISTORY_RETRIEVE_BATCH_SIZE);
        checkHistoryStarvation(HISTORY_RETRIEVE_BATCH_SIZE, HISTORY_RETRIEVE_BATCH_SIZE);
    }

    private void checkHistoryStarvation(int batchSize1, int batchSize2) {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDAgent agent = history.agentBuilder().build();
        QDDistributor distributor = history.distributorBuilder().build();

        RecordBuffer sub = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(RECORD, CODEC.encode(SNAPSHOT_SYMBOL1), SNAPSHOT_SYMBOL1);
        sub.add(RECORD, CODEC.encode(SNAPSHOT_SYMBOL2), SNAPSHOT_SYMBOL2);
        sub.add(RECORD, CODEC.encode(UPDATED_SYMBOL), UPDATED_SYMBOL);
        agent.addSubscription(sub);

        // switch updated symbol to update mode -- send and retrieve one time
        RecordBuffer data = new RecordBuffer();
        update(data, 0);
        distributor.process(data);
        agent.retrieve(RecordSink.VOID);

        RecordBuffer sink = new RecordBuffer();
        for (int i = 1; i <= COUNT; i++) {
            // process new data snapshot for all symbols
            data.clear();
            snapshotBatch(data, i, batchSize1, batchSize2);
            // just one data update item for starved symbol
            // time increases for it -- updated data incoming
            update(data, i);
            distributor.process(data);
            // retrieve only one batch
            agent.retrieve(new LimitedRecordSink(sink, HISTORY_RETRIEVE_BATCH_SIZE));
        }

        // now check what we've retrieved -- should see both updated and starved symbols
        RecordCursor cursor;
        int snapshotCount1 = 0;
        int snapshotCount2 = 0;
        int updatedCount = 0;
        while ((cursor = sink.next()) != null) {
            assertEquals(RECORD, cursor.getRecord());
            String symbol = cursor.getDecodedSymbol();
            switch (symbol) {
                case SNAPSHOT_SYMBOL1:
                    snapshotCount1++;
                    break;
                case SNAPSHOT_SYMBOL2:
                    snapshotCount2++;
                    break;
                case UPDATED_SYMBOL:
                    updatedCount++;
                    break;
                default:
                    fail(symbol);
                    break;
            }
        }
        int sumCount = snapshotCount1 + snapshotCount2 + updatedCount;
        assertEquals(COUNT * HISTORY_RETRIEVE_BATCH_SIZE, sumCount);
        if (batchSize1 > 0)
            assertTrue("snapshotCount1=" + snapshotCount1, snapshotCount1 > 0);
        if (batchSize2 > 0)
            assertTrue("snapshotCount2=" + snapshotCount2, snapshotCount2 > 0);
        assertTrue("updatedCount=" + updatedCount, updatedCount > 0);
    }

    private void update(RecordBuffer data, int i) {
        data.add(RECORD, CODEC.encode(UPDATED_SYMBOL), UPDATED_SYMBOL).setTime(20140228 + i);
    }

    private void snapshotBatch(RecordBuffer data, int i, int batchSize1, int batchSize2) {
        // time decreases here -- it simulates more and more incoming snapshot data
        for (int j = 0; j < batchSize1; j++)
            data.add(RECORD, CODEC.encode(SNAPSHOT_SYMBOL1), SNAPSHOT_SYMBOL1).setTime(20140228 - (i * batchSize1 + j));
        for (int j = 0; j < batchSize2; j++)
            data.add(RECORD, CODEC.encode(SNAPSHOT_SYMBOL2), SNAPSHOT_SYMBOL2).setTime(20140228 - (i * batchSize2 + j));
    }

    private static class LimitedRecordSink extends AbstractRecordSink {
        private final RecordSink dst;
        private int k;

        private LimitedRecordSink(RecordSink dst, int k) {
            this.dst = dst;
            this.k = k;
        }

        @Override
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
