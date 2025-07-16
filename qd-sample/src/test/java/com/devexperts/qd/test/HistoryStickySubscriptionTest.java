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
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.util.TimePeriod;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoryStickySubscriptionTest {

    private static final long TIME1 = 11111111111L;
    private static final long TIME2 = 22222222222L;

    private static final DataScheme SCHEME = new TestDataScheme(1, 123, TestDataScheme.Type.HAS_TIME);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SYMBOL = "TEST";
    private static final int CIPHER = SCHEME.getCodec().encode(SYMBOL);

    @Test
    public void testRemoveSubscription() {
        QDHistory history = createHistory(100);

        // prepare distributor
        QDDistributor distributor = history.distributorBuilder().build();
        TestRecordListener removeSubscriptionListener = new TestRecordListener();
        distributor.getRemovedRecordProvider().setRecordListener(removeSubscriptionListener);

        // create agent with subscription
        QDAgent agent = history.agentBuilder().build();
        agent.setSubscription(getSubscription(TIME1));

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME1);

        removeSubscriptionListener.reset();
        // remove subscription
        agent.removeSubscription(getSubscription(TIME1));
        // still available subscription

        // check agent and distributor subscription
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        Assert.assertTrue(removeSubscriptionListener.elapsed() >= 100);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        agent.close();
        distributor.close();
        history.close();
    }

    @Test
    public void testCloseAgent() {
        QDHistory history = createHistory(100);

        // prepare distributor
        QDDistributor distributor = history.distributorBuilder().build();
        TestRecordListener removeSubscriptionListener = new TestRecordListener();
        distributor.getRemovedRecordProvider().setRecordListener(removeSubscriptionListener);

        // create agent with subscription
        QDAgent agent = history.agentBuilder().build();
        agent.setSubscription(getSubscription(TIME1));

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME1);

        removeSubscriptionListener.reset();
        // remove subscription
        agent.close();

        // check agent and distributor subscription
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        Assert.assertTrue(removeSubscriptionListener.elapsed() >= 100);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        distributor.close();
        history.close();
    }

    @Test
    public void testStepUnsubscribeSubscribe() {
        QDHistory history = createHistory(100);

        // prepare distributor
        QDDistributor distributor = history.distributorBuilder().build();
        TestRecordListener addSubscriptionListener = new TestRecordListener();
        TestRecordListener removeSubscriptionListener = new TestRecordListener();
        distributor.getAddedRecordProvider().setRecordListener(addSubscriptionListener);
        distributor.getRemovedRecordProvider().setRecordListener(removeSubscriptionListener);

        // create agent with subscription
        QDAgent agent = history.agentBuilder().build();
        agent.setSubscription(getSubscription(TIME1));

        // check agent and distributor subscription
        Assert.assertTrue(addSubscriptionListener.isNotifiedAndReset());
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        // unsubscribe
        agent.removeSubscription(getSubscription(TIME1));
        // subscribe
        agent.setSubscription(getSubscription(TIME2));

        // updated subscription
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        Assert.assertTrue(addSubscriptionListener.elapsed() >= 100);
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME2);

        agent.close();

        // no subscription
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        Assert.assertTrue(removeSubscriptionListener.elapsed() >= 100);

        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        distributor.close();
        history.close();
    }

    @Test
    public void testReduceTimeStepsCloseAgents() {
        QDHistory history = createHistory(100);

        // prepare distributor
        QDDistributor distributor = history.distributorBuilder().build();
        TestRecordListener addSubscriptionListener = new TestRecordListener();
        TestRecordListener removeSubscriptionListener = new TestRecordListener();
        distributor.getAddedRecordProvider().setRecordListener(addSubscriptionListener);
        distributor.getRemovedRecordProvider().setRecordListener(removeSubscriptionListener);

        // create agents with subscriptions
        QDAgent agent1 = history.agentBuilder().build();
        agent1.setSubscription(getSubscription(TIME2));

        Assert.assertTrue(addSubscriptionListener.isNotifiedAndReset());
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME2);

        QDAgent agent2 = history.agentBuilder().build();
        agent2.setSubscription(getSubscription(TIME1));

        Assert.assertTrue(addSubscriptionListener.isNotifiedAndReset());
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        agent2.close();

        // check that subscription depth is changed
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        Assert.assertTrue(addSubscriptionListener.elapsed() >= 100);
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME2);

        agent1.close();

        // subscription fully removed
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        Assert.assertTrue(removeSubscriptionListener.elapsed() >= 100);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        distributor.close();
        history.close();
    }

    @Test
    public void testReduceStepsUpdateHistoryTime() {
        QDHistory history = createHistory(100);

        // prepare distributor
        QDDistributor distributor = history.distributorBuilder().build();
        TestRecordListener addSubscriptionListener = new TestRecordListener();
        TestRecordListener removeSubscriptionListener = new TestRecordListener();
        distributor.getAddedRecordProvider().setRecordListener(addSubscriptionListener);
        distributor.getRemovedRecordProvider().setRecordListener(removeSubscriptionListener);

        // create agents with subscriptions
        QDAgent agent = history.agentBuilder().build();
        agent.setSubscription(getSubscription(TIME1));

        Assert.assertTrue(addSubscriptionListener.isNotifiedAndReset());
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME1);

        // change subscription
        agent.setSubscription(getSubscription(TIME2));

        // check agent and distributor subscription
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        Assert.assertTrue(addSubscriptionListener.elapsed() >= 100);
        checkSubscription(history, distributor, TIME2);
        Assert.assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // remove subscription
        agent.removeSubscription(getSubscription(TIME2));

        // subscription fully removed
        Awaitility.waitAtMost(1, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        agent.close();
        distributor.close();
        history.close();
    }

    private RecordBuffer getSubscription(long time) {
        RecordBuffer sb = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        sb.add(RECORD, CIPHER, SYMBOL).setTime(time);
        return sb;
    }

    private static class TestRecordListener implements RecordListener {
        boolean notified;
        long time = System.nanoTime();

        @Override
        public synchronized void recordsAvailable(RecordProvider provider) {
            if (!notified) {
                notified = true;
                time = System.nanoTime() - time;
            }
        }

        public synchronized boolean isNotified() {
            return notified;
        }

        public synchronized boolean isNotifiedAndReset() {
            boolean temp = notified;
            notified = false;
            time = System.nanoTime();
            return temp;
        }

        public synchronized long elapsed() {
            return notified ? TimeUnit.NANOSECONDS.toMillis(time) : -1;
        }

        public synchronized void reset() {
            notified = false;
            time = System.nanoTime();
        }
    }

    private QDHistory createHistory(long period) {
        return QDFactory.getDefaultFactory()
            .historyBuilder()
            .withScheme(SCHEME)
            .withStickySubscriptionPeriod(TimePeriod.valueOf(period))
            .build();
    }

    private void checkSubscription(QDHistory history, QDDistributor distributor, long time) {
        // check agent and distributor subscription
        SubscriptionMap agentSub = new SubscriptionMap(history.getScheme(), getSubscription(time));
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertEquals(agentSub, distSub);
    }
}
