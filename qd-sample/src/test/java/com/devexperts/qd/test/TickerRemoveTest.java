/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import java.util.Random;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;

public class TickerRemoveTest extends QDTestBase {
    public TickerRemoveTest() {
    }

    public TickerRemoveTest(String s) {
        super(s);
    }

    @TestHash
    @TestStriped
    public void testTickerSub() {
        check(qdf, true);
    }

    @TestStriped
    public void testTickerStoreEverything() {
        check(qdf, false);
    }

    private void check(QDFactory factory, boolean sub) {
        TestDataScheme scheme = new TestDataScheme(20080829);
        QDTicker ticker = factory.createTicker(scheme);
        if (!sub)
            ticker.setStoreEverything(true);
        QDDistributor dist = ticker.distributorBuilder().build();
        QDAgent agent = ticker.agentBuilder().build();
        TestDataProvider dataprovider = new TestDataProvider(scheme, 20080830);
        TestSubscriptionProvider subprovider = new TestSubscriptionProvider(scheme, 20080830);
        final Random r = new Random(20080831);
        for (int i = 0; i < 100; i++) {
            int totalsize = 100; // guess if we are not in sub mode

            // subscribe
            if (sub) {
                RecordBuffer buf = new RecordBuffer(RecordMode.SUBSCRIPTION);
                subprovider.retrieveSubscription(buf);
                agent.addSubscription(buf);

                RecordBuffer total = new RecordBuffer(RecordMode.SUBSCRIPTION);
                ticker.examineSubscription(total);
                totalsize = total.size();
            }

            // submit data to ticker
            RecordBuffer buf0 = new RecordBuffer();
            dataprovider.retrieveData(buf0);
            dist.processData(buf0);

            // examine everything and choose records to remove
            final RecordBuffer buf1 = new RecordBuffer();
            final int max = Math.max(1, totalsize + 50 - r.nextInt(100));
            final int[] cnts = new int[2]; // cnts[0] - total, cnts[1] - accepted
            ticker.examineData(new AbstractRecordSink() {
                @Override
                public boolean hasCapacity() {
                    return cnts[0] < max;
                }

                @Override
                public void append(RecordCursor cursor) {
                    cnts[0]++;
                    boolean accept = r.nextBoolean();
                    if (accept) {
                        cnts[1]++;
                        buf1.append(cursor);
                    }
                }
            });
            assertTrue(buf1.size() + " <= " + max, buf1.size() <= max);
            assertEquals(cnts[1], buf1.size());
        }
    }
}
