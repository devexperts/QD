/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
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

public class RecordBufferDataTest extends QDTestBase {
    private static final DataScheme SCHEME = new TestDataScheme(6784, TestDataScheme.Type.HAS_TIME);

    public RecordBufferDataTest() {
    }

    public RecordBufferDataTest(String s) {
        super(s);
    }

    @TestHash
    @TestStriped
    public void testTicker() {
        check(qdf.createTicker(SCHEME));
    }

    @TestHash
    @TestStriped
    public void testStream() {
        check(qdf.createStream(SCHEME));
    }

    @TestHash
    @TestStriped
    public void testHistory() {
        check(qdf.createHistory(SCHEME));
    }

    private void check(QDCollector collector) {
        int cnt = 100;
        TestDataProvider data_prov = new TestDataProvider(SCHEME, 5474, cnt);
        TestSubscriptionProvider sub_prov = new TestSubscriptionProvider(SCHEME, 5474, cnt, true);
        RecordBuffer bufin = new RecordBuffer();
        SubscriptionBuffer sub = new SubscriptionBuffer();
        data_prov.retrieveData(bufin);
        sub_prov.retrieveSubscription(sub);
        QDAgent agent = collector.agentBuilder().build();
        agent.setSubscription(sub);
        QDDistributor dist = collector.distributorBuilder().build();
        dist.processData(bufin);
        RecordBuffer bufout = new RecordBuffer();
        agent.retrieveData(bufout);
        bufin.rewind();

        assertEquals(cnt, bufin.size());
        assertEquals(cnt, bufout.size());
        SubscriptionMap mapin = new SubscriptionMap(SCHEME, bufin);
        SubscriptionMap mapout = new SubscriptionMap(SCHEME, bufout);
        assertEquals(cnt, mapin.getSubscriptionSize());
        assertEquals(cnt, mapout.getSubscriptionSize());
        assertEquals(mapin, mapout);
    }
}


