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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests ability to resubscribe properly when new distributor connects.
 */
public class ResubTest {
    private static DataScheme SCHEME = new TestDataScheme(1, TestDataScheme.Type.HAS_TIME);
    private static final Random RND = new Random(2);
    private static final int REPEAT = 100;

    @Test
    public void testResubTicker() {
        resubTest(QDFactory.getDefaultFactory().createTicker(SCHEME));
    }

    @Test
    public void testResubStream() {
        resubTest(QDFactory.getDefaultFactory().createStream(SCHEME));
    }

    @Test
    public void testResubHistory() {
        resubTest(QDFactory.getDefaultFactory().createHistory(SCHEME));
    }

    private void resubTest(QDCollector collector) {
        for (int repeat = 0; repeat < REPEAT; repeat++) {
            TestSubscriptionProvider prov;
            long subSeed = RND.nextLong();

            // Create distributor #1
            QDDistributor dist1 = collector.distributorBuilder().build();
            SubListener dist1AddSubListener = new SubListener();
            SubListener dist1RemoveSubListener = new SubListener();
            dist1.getAddedSubscriptionProvider().setSubscriptionListener(dist1AddSubListener);
            dist1.getRemovedSubscriptionProvider().setSubscriptionListener(dist1RemoveSubListener);

            // Create agent with subscription
            QDAgent agent = collector.agentBuilder().build();
            prov = new TestSubscriptionProvider(SCHEME, subSeed);
            SubscriptionBuffer sub = new SubscriptionBuffer();
            prov.retrieveSubscription(sub);
            agent.addSubscription(sub);
            SubscriptionMap expectSub = new SubscriptionMap(SCHEME, new TestSubscriptionProvider(SCHEME, subSeed));

            // Check that subscription in distributor #1 is Ok
            dist1AddSubListener.assertAvailable();
            dist1RemoveSubListener.assertNotAvailable();
            SubscriptionMap dist1Sub = new SubscriptionMap(SCHEME, dist1.getAddedRecordProvider());
            assertEquals("dist1Sub", expectSub, dist1Sub);
            assertEmpty("dist1RSub", dist1.getRemovedSubscriptionProvider());

            // Close distributor #1 & create new one (#2)
            dist1.close();
            QDDistributor dist2 = collector.distributorBuilder().build();
            SubListener dist2AddSubListener = new SubListener();
            SubListener dist2RemoveSubListener = new SubListener();
            dist2.getAddedSubscriptionProvider().setSubscriptionListener(dist2AddSubListener);
            dist2.getRemovedSubscriptionProvider().setSubscriptionListener(dist2RemoveSubListener);

            // Check that subscription in distributor #2 is Ok
            dist2AddSubListener.assertAvailable();
            dist2RemoveSubListener.assertNotAvailable();
            SubscriptionMap dist2Sub = new SubscriptionMap(SCHEME, dist2.getAddedRecordProvider());
            assertEquals("dist2Sub", expectSub, dist2Sub);
            assertEmpty("dist2RSub", dist2.getRemovedSubscriptionProvider());

            // Close agent
            agent.close();

            // Check that all subscription was removed from distributor #2
            dist2AddSubListener.assertNotAvailable();
            dist2RemoveSubListener.assertAvailable();
            assertEmpty("dist2Sub*", dist2.getAddedSubscriptionProvider());
            SubscriptionMap dist2RSub = new SubscriptionMap(SCHEME, dist2.getRemovedRecordProvider());
            assertEquals("dist2RSub*", expectSub, dist2RSub);

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
