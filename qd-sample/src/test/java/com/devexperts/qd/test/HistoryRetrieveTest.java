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
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HistoryRetrieveTest extends TestCase {
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

    public void testRetrieves() {
        System.setProperty("com.devexperts.qd.impl.matrix.Hashing.seed", "2"); // for consistency of testing

        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDDistributor distributor = history.distributorBuilder().build();

        QDAgent test_agent = history.agentBuilder().build(); // will subscribe to "TEST" permanetly

        // subscribe test_agent to TEST permanetly (and never touch it afterwards)
        //System.out.println("+ " + TEST_SYMBOL + " on " + test_agent);
        SubscriptionBuffer test_sub = new SubscriptionBuffer();
        DataRecord record = SCHEME.getRecord(0);
        test_sub.visitRecord(record, SCHEME.getCodec().encode(TEST_SYMBOL), null);
        AsserteableListener test_dl = new AsserteableListener();
        test_agent.setDataListener(test_dl);
        test_agent.setSubscription(test_sub.examiningIterator());
        test_dl.assertNotAvailable();

        DataBuffer data = new DataBuffer();
        RandomRecordsProvider provider = new RandomRecordsProvider(record, new String[] { TEST_SYMBOL }, MAX_RECORDS_PROVIDE);
        while (data.size() < MAX_RECORDS_PROVIDE)
            provider.retrieveData(data);
        int expected_data_count = data.size();

        assertOneAdded(distributor);
        distributor.processData(data);

        test_dl.assertAvailable();
        test_agent.retrieveData(data);
        assertEquals(expected_data_count, data.size());

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
            agent.addSubscription(test_sub.examiningIterator());
            assertNotChanged(distributor);
            dl.assertAvailable();
            data.clear();
            agent.retrieveData(data);
            assertEquals(expected_data_count, data.size());
            agent.removeSubscription(test_sub.examiningIterator());
            assertNotChanged(distributor);

            // Play a lot with other symbols on test_agent to make it rehash a lot and expose bug
            playWithOhers(rnd, test_agent, distributor);

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
        RandomRecordsProvider provider = new RandomRecordsProvider(record, new String[] { symbol }, MAX_RECORDS_PROVIDE);
        provider.retrieveData(data);
        int expected_size = data.size();
        distributor.processData(data);

        dl.assertAvailable(); // should receive data from distributor
        agent.retrieveData(data);
        assertEquals(expected_size, data.size()); // agent should receive all data

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
