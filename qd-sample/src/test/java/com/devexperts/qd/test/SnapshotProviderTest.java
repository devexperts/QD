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
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SnapshotProviderTest {
    private static final DataScheme SCHEME = new TestDataScheme(20140721, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SYMBOL = "LongTestSymbol"; // no cipher
    private static final int SNAPSHOT = 0;
    private static final int DATA = 1;

    @Test
    public void testTickerSnapshotProvider() {
        checkSnapshotProvider(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testHistorySnapshotProvider() {
        checkSnapshotProvider(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void checkSnapshotProvider(QDCollector collector) {
        // Create distributor & agent
        QDDistributor distributor = collector.distributorBuilder().build();
        final QDAgent agent = collector.agentBuilder().build();

        // Install snapshot & data records listeners
        final boolean[] available = new boolean[2];
        agent.getSnapshotProvider().setRecordListener(new RecordListener() {
            @Override
            public void recordsAvailable(RecordProvider provider) {
                assertSame(agent.getSnapshotProvider(), provider);
                available[SNAPSHOT] = true;
            }
        });
        agent.setRecordListener(new RecordListener() {
            @Override
            public void recordsAvailable(RecordProvider provider) {
                assertSame(agent, provider);
                available[DATA] = true;
            }
        });

        // Subscribe to record & symbol
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, 0, SYMBOL);
        agent.addSubscription(sub);

        assertFalse(available[SNAPSHOT]);
        assertFalse(available[DATA]);

        // Process data item via distributor
        RecordBuffer dataIn = RecordBuffer.getInstance();
        dataIn.add(RECORD, 0, SYMBOL);
        distributor.process(dataIn);

        // make sure both snapshot & data are available
        assertTrue(available[SNAPSHOT]);
        assertTrue(available[DATA]);

        // retrieve this item via snapshot
        RecordBuffer dataOut = RecordBuffer.getInstance();
        assertFalse(agent.getSnapshotProvider().retrieve(dataOut));
        assertEquals(1, dataOut.size());
        RecordCursor cur = dataOut.next();
        assertEquals(RECORD, cur.getRecord());
        assertEquals(SYMBOL, cur.getSymbol());

        // ensure that nothing more comes out via regular data retrieve
        dataOut.clear();
        assertFalse(agent.retrieve(dataOut));
        assertEquals(0, dataOut.size());
    }
}
