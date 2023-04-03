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

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.tools.RandomRecordsProvider;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class HistoryRetrieveTest {
    private static final int REPEAT = 100;
    private static final int MAX_RECORDS_PROVIDE = 100;
    private static final String TEST_SYMBOL = "TEST"; // must be penta-codeable

    private static final String[] OTHERS = new String[] {
        "AA", "BB", "CC", "DD", "EE", "FF", "GG", "HH", "II", "JJ", "KK", "LL", "MM", "NN", "OO", "PP", "QQ",
        "RR", "SS", "TT", "VV", "WW", "XX", "YY", "ZZ" };

    private static final int MAX_AGENTS = 6;

    private static final DataScheme SCHEME;

    static {
        SCHEME = new DefaultScheme(new PentaCodec(), new DataRecord[] {
            new DefaultRecord(0, "History", true, new DataIntField[] {
                new CompactIntField(0, "History.Sequence"), // Always increasing field
                new CompactIntField(1, "History.Stub"), // Always zero field
                new CompactIntField(2, "History.Any"),
            }, null)
        });
    }

    @Test
    public void testRetrieves() {
        System.setProperty("com.devexperts.qd.impl.matrix.Hashing.seed", "2"); // for consistency of testing

        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDDistributor distributor = history.distributorBuilder().build();

        QDAgent testAgent = history.agentBuilder().build(); // will subscribe to "TEST" permanetly

        // subscribe testAgent to TEST permanetly (and never touch it afterwards)
        //System.out.println("+ " + TEST_SYMBOL + " on " + testAgent);
        SubscriptionBuffer testSub = new SubscriptionBuffer();
        DataRecord record = SCHEME.getRecord(0);
        testSub.visitRecord(record, SCHEME.getCodec().encode(TEST_SYMBOL), null);
        AsserteableListener testDl = new AsserteableListener();
        testAgent.setDataListener(testDl);
        testAgent.setSubscription(testSub.examiningIterator());
        testDl.assertNotAvailable();

        DataBuffer data = new DataBuffer();
        RandomRecordsProvider provider =
            new RandomRecordsProvider(record, new String[] { TEST_SYMBOL }, MAX_RECORDS_PROVIDE);
        while (data.size() < MAX_RECORDS_PROVIDE)
            provider.retrieveData(data);
        int expectedDataCount = data.size();

        assertOneAdded(distributor);
        distributor.processData(data);

        testDl.assertAvailable();
        testAgent.retrieveData(data);
        assertEquals(expectedDataCount, data.size());

        //--------- NOW ALL DATA ON "TEST" IS IN HISTORY BUFFER AND WILL REMAIN THERE ----------

        Random rnd = new Random(1);
        List<QDAgent> agents = new ArrayList<QDAgent>();

        for (int repeat = 0; repeat < REPEAT; repeat++) {
            //System.out.println("#" + repeat);

            // create new agent
            if (agents.isEmpty() || (rnd.nextBoolean() && agents.size() < MAX_AGENTS))
                agents.add(history.agentBuilder().build());

            // subscribe and unsibsribe to test && check that data arrives
            QDAgent agent = agents.get(rnd.nextInt(agents.size()));
            //System.out.println("+- " + TEST_SYMBOL + " on " + agent);

            AsserteableListener dl = new AsserteableListener();
            agent.setDataListener(dl);
            agent.addSubscription(testSub.examiningIterator());
            assertNotChanged(distributor);
            dl.assertAvailable();
            data.clear();
            agent.retrieveData(data);
            assertEquals(expectedDataCount, data.size());
            agent.removeSubscription(testSub.examiningIterator());
            assertNotChanged(distributor);

            // Play a lot with other symbols on testAgent to make it rehash a lot and expose bug
            playWithOhers(rnd, testAgent, distributor);

            // Play with other symbols on other agents
            playWithOhers(rnd, agents.get(rnd.nextInt(agents.size())), distributor);

            // close random agent
            if (rnd.nextBoolean() && agents.size() > 0)
                agents.remove(rnd.nextInt(agents.size())).close();
        }
    }

    private void playWithOhers(Random rnd, QDAgent agent, QDDistributor distributor) {
        String symbol = OTHERS[rnd.nextInt(OTHERS.length)];
        //System.out.println("+- " + symbol + " on " + agent);

        AsserteableListener dl = new AsserteableListener();
        agent.setDataListener(dl);

        SubscriptionBuffer sub = new SubscriptionBuffer();
        DataRecord record = SCHEME.getRecord(0);
        sub.visitRecord(record, SCHEME.getCodec().encode(symbol), null);
        agent.addSubscription(sub.examiningIterator());
        assertOneAdded(distributor);
        dl.assertNotAvailable(); // should not have any data there

        DataBuffer data = new DataBuffer();
        RandomRecordsProvider provider =
            new RandomRecordsProvider(record, new String[] { symbol }, MAX_RECORDS_PROVIDE);
        provider.retrieveData(data);
        int expectedSize = data.size();
        distributor.processData(data);

        dl.assertAvailable(); // should receive data from distributor
        agent.retrieveData(data);
        assertEquals(expectedSize, data.size()); // agent should receive all data

        agent.removeSubscription(sub); // let's unsubscribe
        assertOneRemoved(distributor);
    }

    private void assertOneRemoved(QDDistributor distributor) {
        SubscriptionBuffer sub = new SubscriptionBuffer();
        distributor.getAddedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(0, sub.size());
        distributor.getRemovedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(1, sub.size());
    }

    private void assertOneAdded(QDDistributor distributor) {
        SubscriptionBuffer sub = new SubscriptionBuffer();
        distributor.getRemovedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(0, sub.size());
        distributor.getAddedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(1, sub.size());
    }

    private void assertNotChanged(QDDistributor distributor) {
        SubscriptionBuffer sub = new SubscriptionBuffer();
        distributor.getRemovedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(0, sub.size());
        distributor.getAddedSubscriptionProvider().retrieveSubscription(sub);
        assertEquals(0, sub.size());
    }
}
