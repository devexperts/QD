/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
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
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
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

    @Test
    public void testStickySubscriptionAcceptsUpdatesAfterAgentClose() {
        DataScheme scheme = new TestDataScheme(1, 123, TestDataScheme.Type.SIMPLE);
        SymbolCodec codec = scheme.getCodec();
        DataRecord record = scheme.getRecord(0);
        String symbol = "TEST";

        QDTicker ticker = qdf.tickerBuilder()
            .withScheme(scheme)
            .withStickySubscriptionPeriod(TimePeriod.valueOf(5000))
            .build();

        QDDistributor distributor = ticker.distributorBuilder().build();

        // Create agent and subscribe
        QDAgent agent = ticker.agentBuilder().build();
        RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION);
        sub.add(record, codec.encode(symbol), symbol);
        agent.addSubscription(sub);

        // Send initial data
        RecordBuffer data = new RecordBuffer();
        RecordCursor cursor = data.add(record, codec.encode(symbol), symbol);
        cursor.setInt(0, 100); // set some value
        distributor.process(data);

        // Retrieve data from agent
        RecordBuffer read = new RecordBuffer();
        agent.retrieve(read);
        assertEquals("Should receive 1 record", 1, read.size());
        RecordCursor readCursor = read.next();
        assertEquals(100, readCursor.getInt(0));

        // Close agent - sticky subscription should keep accepting data
        agent.close();

        // Send new data while sticky is active
        data.clear();
        cursor = data.add(record, codec.encode(symbol), symbol);
        cursor.setInt(0, 200); // new value
        distributor.process(data);

        // Create new agent and subscribe
        agent = ticker.agentBuilder().build();
        sub.rewind();
        agent.addSubscription(sub);

        // Retrieve data - should get the updated value (except for HashFactory which doesn't support sticky)
        read.clear();
        agent.retrieve(read);
        if (isHash()) {
            assertTrue("HashFactory doesn't support sticky subscription", read.isEmpty());
        } else {
            assertEquals("Should receive 1 record with updated value", 1, read.size());
            readCursor = read.next();
            assertEquals("Should have updated value from sticky period", 200, readCursor.getInt(0));
        }

        agent.close();
        distributor.close();
        ticker.close();
    }
}
