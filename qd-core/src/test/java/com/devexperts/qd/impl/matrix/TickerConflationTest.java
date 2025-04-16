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

public class TickerConflationTest extends AbstractCollectorTest {

    @Before
    public void setUp() throws Exception {
        setUp(QDFactory.getDefaultFactory().tickerBuilder().withScheme(SCHEME).withStats(QDStats.VOID).build());
    }

    @Test
    public void testSnapshot() {
        process(agent::addSubscription, SYMBOL);
        process(distributor::process, SYMBOL, 100, 1);
        process(distributor::process, SYMBOL, 200, 2);
        process(distributor::process, SYMBOL, 300, 3);

        assertRetrieve(SYMBOL, 300, 1);
    }

    @Test
    public void testSnapshotStoreEverything() {
        collector.setStoreEverything(true);

        process(distributor::process, SYMBOL, 100, 1);
        process(distributor::process, SYMBOL, 200, 2);
        process(distributor::process, SYMBOL, 300, 3);
        process(agent::addSubscription, SYMBOL);

        assertRetrieve(SYMBOL, 300, 0);
    }

    @Test
    public void testResubscribeStoreEverything() {
        collector.setStoreEverything(true);

        process(distributor::process, SYMBOL, 100, 1);
        process(agent::addSubscription, SYMBOL);
        process(distributor::process, SYMBOL, 200, 2);
        process(agent::addSubscription, SYMBOL);
        process(distributor::process, SYMBOL, 300, 3);

        assertRetrieve(SYMBOL, 300, 2);
    }

    @Test
    public void testTimeMark() {
        // No snapshot if subscribed after event sent
        process(distributor::process, SYMBOL, 100, 1);
        process(agent::addSubscription, SYMBOL);
        assertRetrieveNothing();

        // Initial snapshot
        process(distributor::process, SYMBOL, 200, 2);
        assertRetrieve(SYMBOL, 200, 2);

        // No snapshot after resubscribe
        process(agent::removeSubscription, SYMBOL);
        process(agent::addSubscription, SYMBOL);
        assertRetrieveNothing();

        // Snapshot
        process(distributor::process, SYMBOL, 300, 3);
        assertRetrieve(SYMBOL, 300, 3);
    }

    @Test
    public void testTimeMarkStoreEverything() {
        collector.setStoreEverything(true);

        // Initial snapshot
        process(distributor::process, SYMBOL, 100, 1);
        process(agent::addSubscription, SYMBOL);
        assertRetrieve(SYMBOL, 100, 0);

        // Retrieve
        process(distributor::process, SYMBOL, 200, 2);
        assertRetrieve(SYMBOL, 200, 2);

        // Existing snapshot after resubscribe
        process(agent::removeSubscription, SYMBOL);
        process(agent::addSubscription, SYMBOL);
        assertRetrieve(SYMBOL, 200, 0);

        // Update
        process(distributor::process, SYMBOL, 300, 3);
        assertRetrieve(SYMBOL, 300, 3);
    }

    @Test
    public void testConflatedTimeMark() {
        process(agent::addSubscription, SYMBOL);
        process(distributor::process, SYMBOL, 100, 1);
        assertRetrieve(SYMBOL, 100, 1);

        // Conflation
        process(distributor::process, SYMBOL, 200, 2);
        process(distributor::process, SYMBOL, 300, 3);
        process(distributor::process, SYMBOL, 400, 4);
        assertRetrieve(SYMBOL, 400, 2);
    }

    @Test
    public void testConflatedTimeMarkStoreEverything() {
        collector.setStoreEverything(true);
        
        process(agent::addSubscription, SYMBOL);
        process(distributor::process, SYMBOL, 100, 1);
        assertRetrieve(SYMBOL, 100, 1);

        // Conflation
        process(distributor::process, SYMBOL, 200, 2);
        process(distributor::process, SYMBOL, 300, 3);
        process(distributor::process, SYMBOL, 400, 4);
        assertRetrieve(SYMBOL, 400, 2);
    }
}
