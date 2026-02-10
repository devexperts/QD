/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
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
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.util.TimePeriod;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class HistoryStickySubscriptionTest {

    private static final long TIME1 = 11111111111L;
    private static final long TIME2 = 22222222222L;

    private static final DataScheme SCHEME = new TestDataScheme(1, 123, TestDataScheme.Type.HAS_TIME);
    private static final SymbolCodec CODEC = SCHEME.getCodec();
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SYMBOL = "TEST";

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

        // remove subscription
        agent.removeSubscription(getSubscription(TIME1));
        // still available subscription

        // check agent and distributor subscription
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);
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

        // remove subscription
        agent.close();

        // check agent and distributor subscription
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);
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
        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        // unsubscribe
        agent.removeSubscription(getSubscription(TIME1));
        // subscribe
        agent.setSubscription(getSubscription(TIME2));

        // updated subscription
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        assertTrue(addSubscriptionListener.elapsed() >= 100);
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME2);

        agent.close();

        // no subscription
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);

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

        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME2);

        QDAgent agent2 = history.agentBuilder().build();
        agent2.setSubscription(getSubscription(TIME1));

        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        agent2.close();

        // check that subscription depth is changed
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        assertTrue(addSubscriptionListener.elapsed() >= 100);
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME2);

        agent1.close();

        // subscription fully removed
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        distributor.close();
        history.close();
    }

    @Test
    public void testStepUnsubscribeSubscribeWithSameTime() {
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
        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        // set lage sticky 1 min
        ((Collector) history).setStickySubscriptionPeriod(TimeUnit.MINUTES.toMillis(1));

        // unsubscribe
        agent.removeSubscription(getSubscription(TIME1));
        // subscribe to same time
        agent.setSubscription(getSubscription(TIME1));

        // nothing change
        assertFalse(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // unsubscribe
        agent.removeSubscription(getSubscription(TIME1));
        // subscribe
        agent.setSubscription(getSubscription(TIME2));

        // nothing change
        assertFalse(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // unsubscribe
        agent.removeSubscription(getSubscription(TIME2));
        // subscribe
        agent.setSubscription(getSubscription(TIME1 - 1));

        // add notification for deeper subscription
        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // return sticky to 100 ms
        ((Collector) history).setStickySubscriptionPeriod(100);

        agent.close();

        // subscription fully removed
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        distributor.close();
        history.close();
    }

    @Test
    public void testNotReduceTimeStepsCloseAgents() {
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

        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME2);

        QDAgent agent2 = history.agentBuilder().build();
        agent2.setSubscription(getSubscription(TIME1));

        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        agent1.close();

        // check that subscription depth is not changed
        assertFalse(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());
        checkSubscription(history, distributor, TIME1);

        agent2.close();

        // subscription fully removed
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        assertTrue(removeSubscriptionListener.elapsed() >= 100);
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

        assertTrue(addSubscriptionListener.isNotifiedAndReset());
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // check agent and distributor subscription
        checkSubscription(history, distributor, TIME1);

        // change subscription
        agent.setSubscription(getSubscription(TIME2));

        // check agent and distributor subscription
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(addSubscriptionListener::isNotified);
        assertTrue(addSubscriptionListener.elapsed() >= 100);
        checkSubscription(history, distributor, TIME2);
        assertFalse(removeSubscriptionListener.isNotifiedAndReset());

        // remove subscription
        agent.removeSubscription(getSubscription(TIME2));

        // subscription fully removed
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(removeSubscriptionListener::isNotified);
        SubscriptionMap distSub = new SubscriptionMap(history.getScheme(), distributor.getAddedRecordProvider());
        assertTrue(distSub.isEmpty());

        agent.close();
        distributor.close();
        history.close();
    }

    @Test
    public void testStickySubscriptionAcceptsUpdatesAfterAgentClose() {
        QDHistory history = createHistory(5000);
        QDDistributor distributor = history.distributorBuilder().build();

        QDAgent agent = history.agentBuilder().withHistorySnapshot(true).build();
        RecordBuffer sub = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(RECORD, CODEC.encode(SYMBOL), SYMBOL).setTime(TIME1);
        agent.addSubscription(sub);

        RecordBuffer data = new RecordBuffer(RecordMode.FLAGGED_DATA);

        RecordCursor cursor1 = data.add(RECORD, CODEC.encode(SYMBOL), SYMBOL);
        cursor1.setTime(TIME1 + 1);
        cursor1.setEventFlags(EventFlag.SNAPSHOT_BEGIN.flag());

        RecordCursor cursor2 = data.add(RECORD, CODEC.encode(SYMBOL), SYMBOL);
        cursor2.setTime(TIME1);
        cursor2.setEventFlags(EventFlag.SNAPSHOT_END.flag());

        distributor.process(data);

        RecordBuffer read = new RecordBuffer(RecordMode.FLAGGED_DATA);
        agent.retrieve(read);

        assertEquals("Should receive 2 records", 2, read.size());

        RecordCursor readCursor = read.next();
        assertEquals(SYMBOL, readCursor.getDecodedSymbol());
        assertEquals(TIME1 + 1, readCursor.getTime());
        assertTrue("First record should have SNAPSHOT_BEGIN flag",
            EventFlag.SNAPSHOT_BEGIN.in(readCursor.getEventFlags()));

        readCursor = read.next();
        assertEquals(SYMBOL, readCursor.getDecodedSymbol());
        assertEquals(TIME1, readCursor.getTime());
        assertTrue("Last record should have SNAPSHOT_END flag",
            EventFlag.SNAPSHOT_END.in(readCursor.getEventFlags()));

        agent.close();

        data = new RecordBuffer(RecordMode.FLAGGED_DATA);
        cursor1 = data.add(RECORD, CODEC.encode(SYMBOL), SYMBOL);
        cursor1.setTime(TIME1 + 2);

        distributor.process(data);

        agent = history.agentBuilder().withHistorySnapshot(true).build();
        sub = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        sub.add(RECORD, CODEC.encode(SYMBOL), SYMBOL).setTime(TIME1);
        agent.addSubscription(sub);

        read = new RecordBuffer(RecordMode.FLAGGED_DATA);
        agent.retrieve(read);

        assertEquals("Should receive 3 records", 3, read.size());

        readCursor = read.next();
        assertEquals(SYMBOL, readCursor.getDecodedSymbol());
        assertEquals(TIME1 + 2, readCursor.getTime());
        assertTrue("First record should have SNAPSHOT_BEGIN flag",
            EventFlag.SNAPSHOT_BEGIN.in(readCursor.getEventFlags()));

        readCursor = read.next();
        assertEquals(SYMBOL, readCursor.getDecodedSymbol());
        assertEquals(TIME1 + 1, readCursor.getTime());
        assertEquals(0, readCursor.getEventFlags());

        readCursor = read.next();
        assertEquals(SYMBOL, readCursor.getDecodedSymbol());
        assertEquals(TIME1, readCursor.getTime());
        assertTrue("Last record should have SNAPSHOT_END flag",
            EventFlag.SNAPSHOT_END.in(readCursor.getEventFlags()));

        agent.close();
        distributor.close();
        history.close();
    }

    private RecordBuffer getSubscription(long time) {
        RecordBuffer sb = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        for (int i = 0; i < 42; i++) {
            String symbol = SYMBOL + i;
            int cipher = SCHEME.getCodec().encode(symbol);
            sb.add(RECORD, cipher, symbol).setTime(time);
        }
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
