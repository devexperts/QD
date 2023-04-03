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

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionContainer;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.ng.RecordBuffer;
import org.junit.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Basic tests of {@link QDTicker} functionality.
 */
public class TickerTest extends QDTestBase {
    private final Random rnd = new Random();

    private static final int REPEAT_ONE = 2;
    private static final int REPEAT_TWO = 50;
    private static final int LARGE = 5000;
    private static final int REPEAT_TWO_SMALL = 2;

    public TickerTest(String matrixType) {
        super(matrixType);
    }

    @Test
    public void testTicker() {
        doTestTicker(qdf);
    }

    @Test
    public void testTickerStress() {
        assumeTrue(isMatrix() || isStriped());
        TestDataScheme scheme = new TestDataScheme(rnd.nextLong());
        QDTicker ticker = new MatrixFactory().createTicker(scheme);
        Tweaks.setTickerStress(scheme);
        doTestTicker(ticker);
        Tweaks.setTickerDefaults(scheme);
    }

    @Test
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
        long providerSeed = rnd.nextLong();
        TestSubscriptionProvider subProvider1 = new TestSubscriptionProvider(ticker.getScheme(), providerSeed);
        TestSubscriptionProvider subProvider2 = new TestSubscriptionProvider(ticker.getScheme(), providerSeed);
        TestDataProvider dataProvider = new TestDataProvider(ticker.getScheme(), providerSeed);
        doTestTicker(ticker, subProvider1, subProvider2, dataProvider, REPEAT_TWO);
    }

    private void doTestTickerLarge(QDFactory factory) {
        QDTicker ticker = factory.createTicker(new TestDataScheme(rnd.nextLong()));
        long providerSeed = rnd.nextLong();
        TestSubscriptionProvider subProvider1 =
            new TestSubscriptionProvider(ticker.getScheme(), providerSeed, LARGE, true);
        TestSubscriptionProvider subProvider2 =
            new TestSubscriptionProvider(ticker.getScheme(), providerSeed, LARGE, true);
        TestDataProvider dataProvider = new TestDataProvider(ticker.getScheme(), providerSeed, LARGE);
        doTestTicker(ticker, subProvider1, subProvider2, dataProvider, REPEAT_TWO_SMALL);
    }

    private void doTestTicker(QDTicker ticker, TestSubscriptionProvider subProvider1,
        TestSubscriptionProvider subProvider2, TestDataProvider dataProvider, int rep)
    {
        SubscriptionBuffer subBuffer = new SubscriptionBuffer();
        SubscriptionMap currentSub = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap deltaSubOrig = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap deltaSubActual = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap deltaSubExpect = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap deltaSubEmpty = new SubscriptionMap(ticker.getScheme());
        SubscriptionMap examineSub = new SubscriptionMap(ticker.getScheme());
        RecordBuffer dataBuffer = new RecordBuffer();
        for (int repeatOne = 0; repeatOne < REPEAT_ONE; repeatOne++) {
            QDAgent agent = ticker.agentBuilder().build();
            QDDistributor distributor = ticker.distributorBuilder().build();
            for (int repeatTwo = 0; repeatTwo < rep; repeatTwo++) {
                String pass = (repeatOne + 1) + "/" + REPEAT_ONE + ":" + (repeatTwo + 1) + "/" + rep;
                // add subscription to agent
                subProvider1.retrieveSubscription(subBuffer);
                agent.addSubscription(subBuffer);
                // retrieve subscription from distributor (and compare with expected)
                distributor.getAddedSubscriptionProvider().retrieveSubscription(deltaSubActual);
                subProvider2.retrieveSubscription(deltaSubOrig);
                deltaSubExpect.addAll(deltaSubOrig);
                deltaSubExpect.removeAll(currentSub);
                assertSubEquals(pass + ": delta_sub - distributor (add)", deltaSubExpect, deltaSubActual);
                deltaSubActual.clear();
                distributor.getRemovedSubscriptionProvider().retrieveSubscription(deltaSubActual);
                assertSubEquals(pass + ": delta_sub - distributor (remove)", deltaSubEmpty, deltaSubActual);
                deltaSubActual.clear();
                // feed data into distributor
                dataProvider.retrieveData(dataBuffer);
                distributor.processData(dataBuffer);
                // retrieve data from agent
                agent.retrieveData(deltaSubActual);
                assertTrue(pass + "delta_sub - agent from data only", deltaSubOrig.containsAll(deltaSubActual));
                //assertEquals(pass + ": delta_sub - agent", deltaSubExpect, deltaSubActual);
                deltaSubActual.clear();
                // update current subscription
                currentSub.addAll(deltaSubExpect);
                deltaSubExpect.clear();
                deltaSubOrig.clear();
                // check "SubscriptionContainer" information about agent and ticker
                assertSubscription(ticker, currentSub, examineSub);
                assertSubscription(agent, currentSub, examineSub);
            }
            if (repeatOne % 2 == 0) {
                agent.close();
                distributor.close();
            } else {
                distributor.close();
                agent.close();
            }
            // Clear structures
            currentSub.clear();
        }
    }

    private void assertSubscription(SubscriptionContainer sc, SubscriptionMap sub, SubscriptionMap examineSub) {
        assertEquals("sub size", sub.getSubscriptionSize(), sc.getSubscriptionSize());
        examineSub.clear();
        assertFalse("examined everything", sc.examineSubscription(examineSub));
        assertSubEquals("same sub", sub, examineSub);
    }

    /**
     * Specialized assertEquals for {@link SubscriptionMap} objects.
     */
    private static void assertSubEquals(String msg, SubscriptionMap expected, SubscriptionMap actual) {
        if (!expected.equals(actual)) {
            StringBuilder sb = new StringBuilder();
            sb.append(msg);
            SubscriptionMap expectedLeft = new SubscriptionMap(expected);
            expectedLeft.removeAll(actual);
            if (!expectedLeft.isEmpty()) {
                sb.append(": ");
                Set<String> symbols = expectedLeft.getSymbols();
                sb.append(symbols);
                sb.append(" symbols were expected but not found: ");
                sb.append(symbols);
            }
            SubscriptionMap actualLeft = new SubscriptionMap(actual);
            actualLeft.removeAll(expected);
            if (!actualLeft.isEmpty()) {
                sb.append(": ");
                Set<String> symbols = actualLeft.getSymbols();
                sb.append(" symbols were actually received but were not expected: ");
                sb.append(symbols);
            }
            fail(new String(sb));
        }
    }
}
