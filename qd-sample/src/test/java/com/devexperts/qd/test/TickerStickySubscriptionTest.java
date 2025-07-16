/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.util.TimePeriod;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TickerStickySubscriptionTest extends QDTestBase {
    private final Random rnd = new Random();

    public TickerStickySubscriptionTest(String matrixType) {
        super(matrixType);
    }

    @Test
    public void testStickySubscriptionTickerKeep() throws InterruptedException {
        testStickySubscriptionTicker(3000, false);
    }

    @Test
    public void testStickySubscriptionTickerRemove() throws InterruptedException {
        testStickySubscriptionTicker(100, true);
    }

    private void testStickySubscriptionTicker(long stickyPeriod, boolean makePause) throws InterruptedException {
        long seed = rnd.nextLong();

        QDTicker qdTicker = qdf.tickerBuilder()
            .withScheme(new TestDataScheme(seed))
            .withStickySubscriptionPeriod(TimePeriod.valueOf(stickyPeriod))
            .build();

        TestSubscriptionProvider subProvider = new TestSubscriptionProvider(qdTicker.getScheme(), seed);
        TestDataProvider dataProvider = new TestDataProvider(qdTicker.getScheme(), seed);

        RecordBuffer dataBuffer = new RecordBuffer();
        RecordBuffer subBuffer = new RecordBuffer(RecordMode.SUBSCRIPTION);

        SubscriptionMap retrieveData = new SubscriptionMap(qdTicker.getScheme());
        SubscriptionMap examineSub = new SubscriptionMap(qdTicker.getScheme());
        SubscriptionMap distributorSub = new SubscriptionMap(qdTicker.getScheme());

        subProvider.retrieve(subBuffer);

        QDAgent agent1 = qdTicker.agentBuilder().build();
        QDDistributor distributor = qdTicker.distributorBuilder().build();

        // add subscription
        agent1.addSubscription(subBuffer);
        // get distributor subscription
        distributor.getAddedRecordProvider().retrieve(distributorSub);
        // get current agent subscription
        agent1.examineSubscription(examineSub);
        // compare subscription in agent and distributor
        assertEquals(examineSub, distributorSub);

        // get data for agent subscription
        agent1.retrieve(retrieveData);
        // retrieve nothing
        assertTrue(retrieveData.isEmpty());

        // feed data into distributor
        dataProvider.retrieve(dataBuffer);
        distributor.process(dataBuffer);

        // retrieve data and compare
        agent1.retrieve(retrieveData);
        assertEquals(retrieveData, distributorSub);

        // remove agent subscription
        examineSub.clear();
        subBuffer.rewind();
        agent1.removeSubscription(subBuffer);
        agent1.examineSubscription(examineSub);
        // subscription removed from agent
        assertTrue(examineSub.isEmpty());

        if (makePause) {
            Thread.sleep(stickyPeriod * 3);
        }

        // create second agent and subscribe
        QDAgent agent2 = qdTicker.agentBuilder().build();
        subBuffer.rewind();
        agent2.addSubscription(subBuffer);

        // retrieve data and compare
        retrieveData.clear();
        agent2.retrieve(retrieveData);
        if (isHash() || makePause) {
            assertTrue(retrieveData.isEmpty());
        } else {
            assertEquals(retrieveData, distributorSub);
        }

        distributor.close();

        agent1.close();
        agent2.close();

        qdTicker.close();
    }
}
