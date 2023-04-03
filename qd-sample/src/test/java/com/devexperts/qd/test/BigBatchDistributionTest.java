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
import com.devexperts.qd.HistorySubscriptionFilter;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Tests that distribution of events in big batches works correctly:
 * a) they distribute all the incoming data
 * b) they do not reorder incoming data
 *
 * NOTE: Batches shall be big enough to trigger multi-pass processing during distribution.
 */
public class BigBatchDistributionTest {
    private static final DataScheme SCHEME = new TestDataScheme(20160819, TestDataScheme.Type.HAS_TIME);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final int SIZE = 150_000; // 150k is bigger than 100k default Distribution Bucket size

    @Test
    public void testTickerSymbols() {
        checkCollector(QDContract.TICKER, true, true, true);
        checkCollector(QDContract.TICKER, true, false, false);
    }

    @Test
    public void testStreamSymbols() {
        checkCollector(QDContract.STREAM, true, true, true);
        checkCollector(QDContract.STREAM, true, false, false);
    }

    @Test
    public void testStreamTime() {
        checkCollector(QDContract.STREAM, false, true, true);
        checkCollector(QDContract.STREAM, false, false, false);
    }

    @Test
    public void testHistorySymbols() {
        checkCollector(QDContract.HISTORY, true, true, true);
        checkCollector(QDContract.HISTORY, true, false, false);
    }

    @Test
    public void testHistoryTime() {
        checkCollector(QDContract.HISTORY, false, true, false);
        checkCollector(QDContract.HISTORY, false, false, false);
    }

    private void checkCollector(QDContract contract, boolean forSymbols,
        boolean sourceAscending, boolean resultAscending)
    {
        String desc = " in test(" + contract + ", " + forSymbols +
            ", " + sourceAscending + ", " + resultAscending + ")";
        QDCollector collector = QDFactory.getDefaultFactory().collectorBuilder(contract)
            .withScheme(SCHEME)
            .withHistoryFilter(new HistorySubscriptionFilter() {
                @Override
                public long getMinHistoryTime(DataRecord record, int cipher, String symbol) {
                    return Long.MIN_VALUE;
                }

                @Override
                public int getMaxRecordCount(DataRecord record, int cipher, String symbol) {
                    return Integer.MAX_VALUE;
                }
            }).build();
        RecordBuffer buffer = new RecordBuffer(RecordMode.DATA);

        QDAgent agent = collector.agentBuilder().build();
        for (int i = 0, n = forSymbols ? SIZE : 1; i < n; i++) {
            buffer.add(RECORD, getCipher(i, forSymbols), null).setTime(Long.MIN_VALUE);
        }
        agent.setSubscription(buffer);
        if (buffer.next() != null)
            fail("leftover subscription in buffer after setSubscription(buffer)" + desc);
        buffer.clear();

        QDDistributor distributor = collector.distributorBuilder().build();
        for (int i = 0; i < SIZE; i++) {
            int idx = sourceAscending ? i : SIZE - 1 - i;
            buffer.add(RECORD, getCipher(idx, forSymbols), null).setTime(getTime(idx));
        }
        distributor.process(buffer);
        if (buffer.next() != null)
            fail("leftover data in buffer after process(buffer)" + desc);
        buffer.clear();

        if (agent.retrieve(buffer))
            fail("leftover data in agent after retrieve(buffer)" + desc);
        for (int i = 0; i < SIZE; i++) {
            int idx = resultAscending ? i : SIZE - 1 - i;
            int cipher = getCipher(idx, forSymbols);
            int time = getTime(idx);
            RecordCursor cursor = buffer.next();
            if (cursor == null) {
                fail("not enough data in buffer retrieved from agent for " + idx + desc);
            }
            if (cursor.getCipher() != cipher) {
                fail("wrong symbol " + SCHEME.getCodec().decode(cursor.getCipher()) + " instead of " +
                    SCHEME.getCodec().decode(cipher) + " for " + idx + desc);
            }
            if (cursor.getTime() != time) {
                fail("wrong time " + cursor.getTime() + " instead of " + time + " for " + idx + desc);
            }
        }
        if (buffer.next() != null) {
            fail("leftover data in buffer retrieved from agent" + desc);
        }
    }

    private final char[] symbolChars = new char[6];

    private int getCipher(int idx, boolean forSymbols) {
        // test symbols are all encodeable by PentaCodec to improve test performance
        if (!forSymbols)
            idx = 0;
        for (int i = 0; i < symbolChars.length; i++)
            symbolChars[i] = (char) ('A' + ((idx >>> (4 * (symbolChars.length - 1 - i))) & 15));
        int cipher = SCHEME.getCodec().encode(symbolChars, 0, symbolChars.length);
        if ((cipher & SymbolCodec.VALID_CIPHER) == 0)
            throw new IllegalArgumentException("unencodeable cipher for " + idx);
        return cipher;
    }

    private int getTime(int idx) {
        // test negative, zero and positive times
        return idx - SIZE / 2;
    }
}
