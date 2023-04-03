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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.ng.RecordBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordBufferDataTest extends QDTestBase {
    private static final DataScheme SCHEME = new TestDataScheme(6784, TestDataScheme.Type.HAS_TIME);

    public RecordBufferDataTest(String matrixType) {
        super(matrixType);
    }

    @Test
    public void testTicker() {
        check(qdf.createTicker(SCHEME));
    }

    @Test
    public void testStream() {
        check(qdf.createStream(SCHEME));
    }

    @Test
    public void testHistory() {
        check(qdf.createHistory(SCHEME));
    }

    private void check(QDCollector collector) {
        int count = 100;
        TestDataProvider dataProvider = new TestDataProvider(SCHEME, 5474, count);
        TestSubscriptionProvider subscriptionProvider = new TestSubscriptionProvider(SCHEME, 5474, count, true);
        RecordBuffer bufIn = new RecordBuffer();
        SubscriptionBuffer sub = new SubscriptionBuffer();
        dataProvider.retrieveData(bufIn);
        subscriptionProvider.retrieveSubscription(sub);
        QDAgent agent = collector.agentBuilder().build();
        agent.setSubscription(sub);
        QDDistributor dist = collector.distributorBuilder().build();
        dist.processData(bufIn);
        RecordBuffer bufOut = new RecordBuffer();
        agent.retrieveData(bufOut);
        bufIn.rewind();

        assertEquals(count, bufIn.size());
        assertEquals(count, bufOut.size());
        SubscriptionMap mapIn = new SubscriptionMap(SCHEME, bufIn);
        SubscriptionMap mapOut = new SubscriptionMap(SCHEME, bufOut);
        assertEquals(count, mapIn.getSubscriptionSize());
        assertEquals(count, mapOut.getSubscriptionSize());
        assertEquals(mapIn, mapOut);
    }
}


