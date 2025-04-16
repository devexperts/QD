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
import com.devexperts.qd.stats.QDStats;
import org.junit.Before;
import org.junit.Test;

/**
 * This is a test for [QD-695] NullPointerException at AgentQueue.retrieveSnapshotForHistory.
 * NPE was happening when agent has subscription for data item, then incoming data
 * (and subscription goes to snapshot queue), then unsubscription, then subscription again.
 */
public class HistoryAddRemoveSnapshotTest extends AbstractCollectorTest {

    @Before
    public void setUp() throws Exception {
        setUp(QDFactory.getDefaultFactory().historyBuilder().withScheme(SCHEME).withStats(QDStats.VOID).build());
    }

    @Test
    public void testHistoryAddRemoveSnapshot() {
        // subscribe to two symbols AAPL and MSFT
        process(agent::addSubscription, AAPL, 0, 0, 0);
        process(agent::addSubscription, MSFT, 0, 0, 0);

        // send some data on AAPL (first!) -- goes to the head of snapshot queue
        process(distributor::process, AAPL, 10, 0, 3);
        process(distributor::process, AAPL, 11, 0, 2);
        process(distributor::process, AAPL, 12, 0, 1);

        // send some data on MSFT (second) -- goes to the tail of snaphsot queue
        process(distributor::process, MSFT, 20, 0, 3);
        process(distributor::process, MSFT, 21, 0, 2);
        process(distributor::process, MSFT, 22, 0, 1);

        // do not retrieve, but unsubscribe from MSFT (second one!)
        process(agent::removeSubscription, MSFT);

        // subscribe on MSFT again -- not data, but still in snapshot queue!
        process(agent::addSubscription, MSFT, 0, 0, 0);

        // expect data on AAPL
        assertRetrieve(AAPL, 10, 0, 3);
        assertRetrieve(AAPL, 11, 0, 2);
        assertRetrieve(AAPL, 12, 0, 1);

        // expect no data retrieved on MSFT (while it may still be in the snapshot queue)
        assertRetrieveNothing();
    }
}
