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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;
import org.junit.Before;
import org.junit.Test;

public class TickerSubscriptionTest extends AbstractCollectorTest {

    static int BATCH_SIZE = Ticker.RETRIEVE_BATCH_SIZE;

    protected RecordProvider agentSnapshotProvider;
    /**
     * Note! This code uses internal non-API methods for "white-box" testing
     * in order to emulate specific test scenarios that are hard to recreate using API.
     * Refactoring of such classes as {@link Agent}, {@link AgentQueue}, {@link Ticker}
     * might require refactoring the test code.
     */
    protected RecordProvider agentUpdateProvider;

    @Before
    public void setUp() throws Exception {
        setUp(QDFactory.getDefaultFactory().tickerBuilder().withScheme(SCHEME).withStats(QDStats.VOID).build());

        agentSnapshotProvider = agent.getSnapshotProvider();
        agentUpdateProvider = new AbstractRecordProvider() {
            @Override
            public RecordMode getMode() {
                return getRecordMode();
            }

            @Override
            public boolean retrieve(RecordSink sink) {
                Agent a = (Agent) agent;
                if (!a.updateQueue.isEmpty()) {
                    Ticker ticker = (Ticker) collector;
                    // Note! Using non-public API here to access update queue specifically
                    // instead of calling {@link Agent#retrieve} method
                    a.updateQueue.retrieveForTicker(ticker, a, sink, BATCH_SIZE, Collector.UPDATE_QUEUE);
                }
                return !a.updateQueue.isEmpty();
            }
        };
    }

    @Test
    public void testSnapshotViaSnapshotQueue() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);

        // Retrieve via SNAPSHOT_QUEUE
        assertRetrieve(agentSnapshotProvider, AAPL, 1, 1, 0);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testSnapshotViaUpdateQueue() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);

        // Retrieve via UPDATE_QUEUE
        assertRetrieve(agentUpdateProvider, AAPL, 1, 1, 0);
        assertRetrieveNothing(agentSnapshotProvider);
    }

    @Test
    public void testUpdateAfterSnapshot() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);
        assertRetrieve(agentSnapshotProvider, AAPL, 1, 1, 0);

        process(distributor::process, AAPL, 2, 2);
        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieve(agentUpdateProvider, AAPL, 2, 2, 0);
    }

    @Test
    public void testUpdateAfterSnapshotViaUpdateQueue() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);
        assertRetrieve(agentUpdateProvider, AAPL, 1, 1, 0);

        process(distributor::process, AAPL, 2, 2);
        assertRetrieve(agentUpdateProvider, AAPL, 2, 2, 0);

        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testNoSnapshotAfterUnsubscribe() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);
        process(agent::removeSubscription, AAPL);

        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testNoUpdateAfterUnsubscribe() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);
        assertRetrieve(agentUpdateProvider, AAPL, 1, 1, 0);

        process(distributor::process, AAPL, 2, 2);
        process(agent::removeSubscription, AAPL);

        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testNoSnapshotAfterResubscribe() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);
        process(agent::removeSubscription, AAPL);
        process(agent::addSubscription, AAPL);

        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testSnapshotQueueOddity() {
        process(agent::addSubscription, AAPL);
        process(distributor::process, AAPL, 1, 1);

        // Retrieve via UPDATE_QUEUE
        // This retrieve will reset QUEUE_BITS from both SNAPSHOT_QUEUE and UPDATE_QUEUE
        assertRetrieve(agentUpdateProvider, AAPL, 1, 1, 0);

        // This update will set QUEUE_BIT in UPDATE_QUEUE
        process(distributor::process, AAPL, 2, 2);

        // Retrieve via SNAPSHOT_QUEUE will be successful even though QUEUE_BIT is not set
        // QUEUE_BIT from UPDATE_QUEUE is checked even when processing SNAPSHOT_QUEUE
        assertRetrieve(agentSnapshotProvider, AAPL, 2, 2, 0);

        assertRetrieveNothing(agentSnapshotProvider);
        assertRetrieveNothing(agentUpdateProvider);
    }

    @Test
    public void testResubscribeForReappearingSnapshot() {
        process(agent::addSubscription, AAPL);
        process(agent::addSubscription, MSFT);
        process(distributor::process, AAPL, 100, 1);
        process(distributor::process, MSFT, 200, 2);

        // Re-subscription should keep all queues intact
        process(agent::removeSubscription, MSFT);
        process(agent::addSubscription, MSFT);
        assertRetrieve(AAPL, 100, 1);

        process(distributor::process, MSFT, 300, 3);
        assertRetrieve(agentSnapshotProvider, MSFT, 300, 3, 0);
        assertRetrieveNothing();
    }

    @Test
    public void testResubscribeForExistingSnapshotViaSnapshotQueue() {
        resubscribeForExistingSnapshot();
        assertRetrieve(agentSnapshotProvider, AAPL, 0, 0, 0);
        assertRetrieve(agentSnapshotProvider, MSFT, 0, 0, 0);
        assertRetrieveNothing();
    }

    @Test
    public void testResubscribeForExistingSnapshotViaUpdateQueue() {
        resubscribeForExistingSnapshot();
        assertRetrieve(agentUpdateProvider, AAPL, 0, 0, 0);
        assertRetrieve(agentUpdateProvider, MSFT, 0, 0, 0);
        assertRetrieveNothing();
    }

    private void resubscribeForExistingSnapshot() {
        collector.setStoreEverything(true);
        process(distributor::process, AAPL);
        process(distributor::process, MSFT);

        process(agent::addSubscription, AAPL);
        process(agent::addSubscription, MSFT);
        process(agent::removeSubscription, MSFT);
        process(agent::addSubscription, MSFT);
    }

    /**
     * Force multiple updates and snapshots and check that
     * snapshots and updates are correctly interleaved (50% for both).
     */
    @Test
    public void testRetrieveSnapshotUpdateBalance() {
        // Subscribe to large number of symbols
        String[] SYMBOLS = new String[BATCH_SIZE * 8];
        for (int i = 0; i < SYMBOLS.length; i++) {
            SYMBOLS[i] = String.format("%08d", i);
            process(agent::addSubscription, SYMBOLS[i]);
        }

        // Read snapshots for the first half of symbols
        for (int i = 0; i < SYMBOLS.length / 2; i++) {
            process(distributor::process, SYMBOLS[i], i + 100, 1);
            assertRetrieve(agentSnapshotProvider, SYMBOLS[i], i + 100, 1, 0);
        }

        // Update all
        for (int i = 0; i < SYMBOLS.length; i++) {
            process(distributor::process, SYMBOLS[i], i, 1);
        }

        // Now the first half of symbols has updates and the second half snapshots.
        // The retrieval should see snapshots and updates equally interleaved by batches of BATCH_SIZE
        int[] batches = new int[] { 0, 4, 1, 5, 2, 6, 3, 7 };

        // Use RecordBuffer to retrieve in batches
        for (int batch : batches) {
            RecordBuffer buff = RecordBuffer.getInstance(getRecordMode());
            buff.setCapacityLimit(BATCH_SIZE);
            agent.retrieve(buff);
            for (int i = 0; i < BATCH_SIZE; i++) {
                int offset = BATCH_SIZE * batch + i;
                assertRetrieve(buff, SYMBOLS[offset], offset, 1, 0);
            }
            buff.release();
        }
        assertRetrieveNothing();
    }
}
