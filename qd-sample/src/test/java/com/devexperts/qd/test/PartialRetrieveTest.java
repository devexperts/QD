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

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.tools.RandomRecordsProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PartialRetrieveTest {
    private static final int REPEAT = 100;
    private static final int MAX_RECORDS_PROVIDE = 100;
    private static final int MAX_RECORDS_RETRIEVE = 10;

    private static final String TEST_SYMBOL = "TEST"; // must be penta-codeable

    private static final DataScheme SCHEME;

    static {
        SCHEME = new DefaultScheme(new PentaCodec(), new DataRecord[] {
            new DefaultRecord(0, "History", true, new DataIntField[] {
                new CompactIntField(0, "History.Sequence"), // Always increasing field
                new CompactIntField(1, "History.Stub"), // Always zero field
                new CompactIntField(2, "History.Any"),
            }, null)
        });
    }

    @Test
    public void testHistoryPartialRetrieve() {
        testPartialRetrieve(QDFactory.getDefaultFactory().createHistory(SCHEME), true);
    }

    @Test
    public void testStreamPartialRetrieve() {
        testPartialRetrieve(QDFactory.getDefaultFactory().createStream(SCHEME), true);
    }

    @Test
    public void testTickerPartialRetrieve() {
        testPartialRetrieve(QDFactory.getDefaultFactory().createTicker(SCHEME), false);
    }

    private void testPartialRetrieve(QDCollector collector, boolean checkSize) {
        QDDistributor distributor = collector.distributorBuilder().build();
        QDAgent agent = collector.agentBuilder().build();

        DataRecord record = SCHEME.getRecord(0);
        SubscriptionBuffer sub = new SubscriptionBuffer();
        sub.visitRecord(record, SCHEME.getCodec().encode(TEST_SYMBOL), null, Long.MIN_VALUE);
        agent.setSubscription(sub);

        DataProvider provider = new RandomRecordsProvider(record, new String[] { TEST_SYMBOL }, MAX_RECORDS_PROVIDE);

        int sizeProvided = 0;
        int sizeRetrieved = 0;

        for (int repeat = 0; repeat < REPEAT; repeat++) {
            // Provide data
            DataBuffer providerBuffer = new DataBuffer();
            provider.retrieveData(providerBuffer);
            sizeProvided += providerBuffer.size();
            distributor.processData(providerBuffer);

            // Retrive in small chunks -- should not fail!!!
            DataBuffer retrieveBuffer = new DataBuffer() {
                public boolean hasCapacity() {
                    return size() < MAX_RECORDS_RETRIEVE;
                }
            };
            agent.retrieveData(retrieveBuffer);
            sizeRetrieved += retrieveBuffer.size();
        }

        // Retrieve rest & check
        DataBuffer buf = new DataBuffer();
        agent.retrieveData(buf);
        sizeRetrieved += buf.size();
        if (checkSize)
            assertEquals(sizeRetrieved, sizeProvided);
    }
}
