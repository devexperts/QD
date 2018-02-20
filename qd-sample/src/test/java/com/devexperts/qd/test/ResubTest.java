/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import java.util.Random;

import com.devexperts.qd.*;
import junit.framework.TestCase;

/**
 * Tests ability to resubscribe properly when new distributor connects.
 */
public class ResubTest extends TestCase {
    private static DataScheme SCHEME = new TestDataScheme(1, TestDataScheme.Type.HAS_TIME);
    private static final Random RND = new Random(2);
    private static final int REPEAT = 100;

    public void testResubTicker() {
        resubTest(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    public void testResubStream() {
        resubTest(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

    public void testResubHistory() {
        resubTest(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void resubTest(QDCollector collector) {
        for (int repeat = 0; repeat < REPEAT; repeat++) {
            TestSubscriptionProvider prov;
            long sub_seed = RND.nextLong();

            // Create distributor #1
            QDDistributor dist1 = collector.distributorBuilder().build();
            SubListener dist1_sl = new SubListener();
            SubListener dist1r_sl = new SubListener();
            dist1.getAddedSubscriptionProvider().setSubscriptionListener(dist1_sl);
            dist1.getRemovedSubscriptionProvider().setSubscriptionListener(dist1r_sl);

            // Create agent with subscription
            QDAgent agent = collector.agentBuilder().build();
            prov = new TestSubscriptionProvider(SCHEME, sub_seed);
            SubscriptionBuffer sub = new SubscriptionBuffer();
            prov.retrieveSubscription(sub);
            agent.addSubscription(sub);
            SubscriptionMap expect_sub = new SubscriptionMap(SCHEME, new TestSubscriptionProvider(SCHEME, sub_seed));

            // Check that subscription in distributor #1 is Ok
            dist1_sl.assertAvailable();
            dist1r_sl.assertNotAvailable();
            SubscriptionMap dist1_sub = new SubscriptionMap(SCHEME, dist1.getAddedRecordProvider());
            assertEquals("dist1_sub", expect_sub, dist1_sub);
            assertEmpty("dist1r_sub", dist1.getRemovedSubscriptionProvider());

            // Close distributor #1 & create new one (#2)
            dist1.close();
            QDDistributor dist2 = collector.distributorBuilder().build();
            SubListener dist2_sl = new SubListener();
            SubListener dist2r_sl = new SubListener();
            dist2.getAddedSubscriptionProvider().setSubscriptionListener(dist2_sl);
            dist2.getRemovedSubscriptionProvider().setSubscriptionListener(dist2r_sl);

            // Check that subscription in distributor #2 is Ok
            dist2_sl.assertAvailable();
            dist2r_sl.assertNotAvailable();
            SubscriptionMap dist2_sub = new SubscriptionMap(SCHEME, dist2.getAddedRecordProvider());
            assertEquals("dist2_sub", expect_sub, dist2_sub);
            assertEmpty("dist2r_sub", dist2.getRemovedSubscriptionProvider());

            // Close agent
            agent.close();

            // Check that all subscription was removed from distributor #2
            dist2_sl.assertNotAvailable();
            dist2r_sl.assertAvailable();
            assertEmpty("dist2_sub*", dist2.getAddedSubscriptionProvider());
            SubscriptionMap dist2r_sub = new SubscriptionMap(SCHEME, dist2.getRemovedRecordProvider());
            assertEquals("dist2r_sub*", expect_sub, dist2r_sub);

            // Close distributor #2
            dist2.close();
        }
    }

    private void assertEmpty(final String name, SubscriptionProvider provider) {
        provider.retrieveSubscription(new SubscriptionVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
                fail(name);
            }
        });
    }

    private static class SubListener implements SubscriptionListener {
        boolean available;

        SubListener() {}

        public void subscriptionAvailable(SubscriptionProvider provider) {
            available = true;
        }

        public void assertAvailable() {
            assertTrue(available);
            available = false;
        }

        public void assertNotAvailable() {
            assertFalse(available);
        }
    }
}
