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

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionContainer;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.ng.RecordBuffer;

import java.util.Random;
import java.util.Set;

/**
 * Basic tests of {@link QDTicker} functionality.
 */
public class TickerTest extends QDTestBase {
    private Random rnd = new Random();

    private static final int REPEAT_ONE = 2;
    private static final int REPEAT_TWO = 50;
    private static final int LARGE = 5000;
    private static final int REPEAT_TWO_SMALL = 2;

    public TickerTest(String s) {
        super(s);
    }

    @TestHash
    @TestStriped
    public void testTicker() {
        doTestTicker(qdf);
    }

    @TestStriped
    public void testTickerStress() {
        TestDataScheme scheme = new TestDataScheme(rnd.nextLong());
        QDTicker ticker = new MatrixFactory().createTicker(scheme);
        Tweaks.setTickerStress(scheme);
        doTestTicker(ticker);
        Tweaks.setTickerDefaults(scheme);
    }

    @TestHash
    @TestStriped
    public void testTickerLarge() {
        doTestTickerLarge(qdf);
    }

    /**
     * Test ability of ticker to properly process subscription & date
     * (simply in single thread).
     */
    private void doTestTicker(QDFactory factory) {
        doTestTicker(factory.createTicker(new TestDataScheme(rnd.nextLong())));
    }

    private void doTestTicker(QDTicker ticker) {
        long provider_seed = rnd.nextLong();
        TestSubscriptionProvider sub_provider1 = new TestSubscriptionProvider(ticker.getScheme(), provider_seed);
        TestSubscriptionProvider sub_provider2 = new TestSubscriptionProvider(ticker.getScheme(), provider_seed);
        TestDataProvider data_provider = new TestDataProvider(ticker.getScheme(), provider_seed);
        doTestTicker(ticker, sub_provider1, sub_provider2, data_provider, REPEAT_TWO);
    }

    private void doTestTickerLarge(QDFactory factory) {
        QDTicker ticker = factory.createTicker(new TestDataScheme(rnd.nextLong()));
        long provider_seed = rnd.nextLong();
        TestSubscriptionProvider sub_provider1 = new TestSubscriptionProvider(ticker.getScheme(), provider_seed, LARGE, true);
        TestSubscriptionProvider sub_provider2 = new TestSubscriptionProvider(ticker.getScheme(), provider_seed, LARGE, true);
        TestDataProvider data_provider = new TestDataProvider(ticker.getScheme(), provider_seed, LARGE);
        doTestTicker(ticker, sub_provider1, sub_provider2, data_provider, REPEAT_TWO_SMALL);
    }

    private void doTestTicker(QDTicker ticker, TestSubscriptionProvider sub_provider1, TestSubscriptionProvider sub_provider2, TestDataProvider data_provider, int rep) {
        SubscriptionBuffer sub_buffer = new SubscriptionBuffer();
        SubscriptionMap current_sub = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap delta_sub_orig = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap delta_sub_actual = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap delta_sub_expect = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap delta_sub_empty = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap examine_sub = new SubscriptionMap(ticker.getScheme());
        RecordBuffer data_buffer = new RecordBuffer();
        for (int repeat_one = 0; repeat_one < REPEAT_ONE; repeat_one++) {
            QDAgent agent = ticker.agentBuilder().build();
            QDDistributor distributor = ticker.distributorBuilder().build();
            for (int repeat_two = 0; repeat_two < rep; repeat_two++) {
                String pass = (repeat_one + 1) + "/" + REPEAT_ONE + ":" + (repeat_two + 1) + "/" + rep;
                // add subscription to agent
                sub_provider1.retrieveSubscription(sub_buffer);
                agent.addSubscription(sub_buffer);
                // retrieve subscription from distributor (and compare with expected)
                distributor.getAddedSubscriptionProvider().retrieveSubscription(delta_sub_actual);
                sub_provider2.retrieveSubscription(delta_sub_orig);
                delta_sub_expect.addAll(delta_sub_orig);
                delta_sub_expect.removeAll(current_sub);
                assertEquals(pass + ": delta_sub - distributor (add)", delta_sub_expect, delta_sub_actual);
                delta_sub_actual.clear();
                distributor.getRemovedSubscriptionProvider().retrieveSubscription(delta_sub_actual);
                assertEquals(pass + ": delta_sub - distributor (remove)", delta_sub_empty, delta_sub_actual);
                delta_sub_actual.clear();
                // feed data into distributor
                data_provider.retrieveData(data_buffer);
                distributor.processData(data_buffer);
                // retrieve data from agent
                agent.retrieveData(delta_sub_actual);
                assertTrue(pass + "delta_sub - agent from data only", delta_sub_orig.containsAll(delta_sub_actual));
                //assertEquals(pass + ": delta_sub - agent", delta_sub_expect, delta_sub_actual);
                delta_sub_actual.clear();
                // update current subscription
                current_sub.addAll(delta_sub_expect);
                delta_sub_expect.clear();
                delta_sub_orig.clear();
                // check "SubscriptionContainer" information about agent and ticker
                assertSubscription(ticker, current_sub, examine_sub);
                assertSubscription(agent, current_sub, examine_sub);
            }
            if (repeat_one % 2 == 0) {
                agent.close();
                distributor.close();
            } else {
                distributor.close();
                agent.close();
            }
            // Clear structures
            current_sub.clear();
        }
    }

    private void assertSubscription(SubscriptionContainer sc, SubscriptionMap sub, SubscriptionMap examine_sub) {
        assertEquals("sub size", sub.getSubscriptionSize(), sc.getSubscriptionSize());
        examine_sub.clear();
        assertFalse("examined everything", sc.examineSubscription(examine_sub));
        assertEquals("same sub", sub, examine_sub);
    }

    /**
     * Specialized assertEquals for {@link SubscriptionMap} objects.
     */
    private static void assertEquals(String msg, SubscriptionMap expected, SubscriptionMap actual) {
        if (!expected.equals(actual)) {
            StringBuilder sb = new StringBuilder();
            sb.append(msg);
            SubscriptionMap expected_left = new SubscriptionMap(expected);
            expected_left.removeAll(actual);
            if (!expected_left.isEmpty()) {
                sb.append(": ");
                Set<String> symbols = expected_left.getSymbols();
                sb.append(symbols);
                sb.append(" symbols were expected but not found: ");
                sb.append(symbols);
            }
            SubscriptionMap actual_left = new SubscriptionMap(actual);
            actual_left.removeAll(expected);
            if (!actual_left.isEmpty()) {
                sb.append(": ");
                Set<String> symbols = actual_left.getSymbols();
                sb.append(" symbols were actually received but were not expected: ");
                sb.append(symbols);
            }
            fail(new String(sb));
        }
    }
}
