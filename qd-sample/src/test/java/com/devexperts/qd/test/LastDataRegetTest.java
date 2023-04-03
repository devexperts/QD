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
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * This unit test makes sure that <code>addSubcription</code> message
 * forces {@link com.devexperts.qd.QDTicker} and {@link com.devexperts.qd.QDHistory} to
 * resend all the corresponding data.
 */
public class LastDataRegetTest {
    private static final int SEED = 1234;
    private static final DataScheme SCHEME = new TestDataScheme(SEED, TestDataScheme.Type.HAS_TIME);
    private static final int REC_COUNT = 100;

    @Test
    public void testTicker() {
        check(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testHistory() {
        check(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void check(QDCollector collector) {
        // create agent & set subscription
        QDAgent agent = collector.agentBuilder().build();
        AsserteableListener dl = new AsserteableListener();
        agent.setDataListener(dl);
        agent.setSubscription(getSubscription(REC_COUNT));
        SubscriptionMap subOrig = new SubscriptionMap(SCHEME, getSubscription(REC_COUNT));
        dl.assertNotAvailable();
        // create distributor & check subscription
        QDDistributor distributor = collector.distributorBuilder().build();
        AsserteableListener sl = new AsserteableListener();
        distributor.getAddedSubscriptionProvider().setSubscriptionListener(sl);
        sl.assertAvailable();
        SubscriptionMap sub1 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub1", subOrig, sub1);
        // distribute data
        distributor.processData(getData(REC_COUNT));
        // check data arrival to agent
        dl.assertAvailable();
        SubscriptionMap data1 = new SubscriptionMap(SCHEME, agent);
        assertEquals("data1", subOrig, data1);
        // now add the same subscription again & check that the same data arrives again (but not subsription!)
        agent.addSubscription(getSubscription(REC_COUNT));
        sl.assertNotAvailable();
        dl.assertAvailable();
        SubscriptionMap data2 = new SubscriptionMap(SCHEME, agent);
        assertEquals("data2", subOrig, data2);
        // make sure that when we "setSubscription" data does _not_ arrive again
        agent.setSubscription(getSubscription(REC_COUNT));
        sl.assertNotAvailable();
        dl.assertNotAvailable();
        // now set more subscription. Make sure additional subscription arrives and old data does not repeat itself
        agent.setSubscription(getSubscription(2 * REC_COUNT));
        sl.assertAvailable();
        dl.assertNotAvailable();
        SubscriptionMap subMore = new SubscriptionMap(SCHEME, getSubscription(2 * REC_COUNT));
        subMore.removeAll(subOrig);
        SubscriptionMap sub2 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub2", subMore, sub2);
        // now provide more data. Make sure that just additional data arrives
        distributor.processData(getData(2 * REC_COUNT));
        dl.assertAvailable();
        SubscriptionMap data3 = new SubscriptionMap(SCHEME, agent);
        assertEquals("data3", subMore, data3);
    }

    private RecordBuffer getSubscription(int recCount) {
        TestSubscriptionProvider subp = new TestSubscriptionProvider(SCHEME, SEED, recCount, true);
        RecordBuffer sb = new RecordBuffer(RecordMode.SUBSCRIPTION);
        subp.retrieveSubscription(sb);
        return sb;
    }

    private RecordBuffer getData(int recCount) {
        TestDataProvider datap = new TestDataProvider(SCHEME, SEED, recCount);
        RecordBuffer buf = new RecordBuffer();
        datap.retrieveData(buf);
        return buf;
    }
}
