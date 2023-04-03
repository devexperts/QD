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
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class HistoryCacheSendTest {
    private static final int CNT = 100;
    private static final int CNT2 = 30;

    private static final long TIME1 = 1141225291L << 32;
    private static final long TIME_STEP = (24L * 3600) << 32;
    private static final long TIME2 = TIME1 + TIME_STEP * CNT2;
    private static final long TIME_MAX = TIME1 + TIME_STEP * CNT;

    private final DataScheme SCHEME = new TestDataScheme(1, 3132, TestDataScheme.Type.HAS_TIME);
    private final DataRecord RECORD = SCHEME.getRecord(0);
    private final int CIPHER = SCHEME.getCodec().encode("MSFT");
    private final Random rnd = new Random(323232);

    private interface SubOp {
        void sub(QDAgent agent, SubscriptionIterator sub);
    }

    private static final SubOp SET_SUB = new SubOp() {
        public void sub(QDAgent agent, SubscriptionIterator sub) {
            agent.setSubscription(sub);
        }
    };

    private static final SubOp ADD_SUB = new SubOp() {
        public void sub(QDAgent agent, SubscriptionIterator sub) {
            agent.addSubscription(sub);
        }
    };

    @Test
    public void testCacheSendSmallerSetSub() {
        cacheSendSmaller(SET_SUB);
    }

    @Test
    public void testCacheSendSmallerAddSub() {
        cacheSendSmaller(ADD_SUB);
    }

    @Test
    public void testCacheSendLargerSetSub() {
        cacheSendLarger(SET_SUB);
    }

    @Test
    public void testCacheSendLargerAddSub() {
        cacheSendLarger(ADD_SUB);
    }

    @Test
    public void testCacheSendMoreDataSetSub() {
        cacheSendMoreData(SET_SUB);
    }

    @Test
    public void testCacheSendMoreDataAddSub() {
        cacheSendMoreData(ADD_SUB);
    }

    private void cacheSendSmaller(SubOp sop) {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDDistributor distributor = history.distributorBuilder().build();
        AsserteableListener al = new AsserteableListener();
        AsserteableListener rl = new AsserteableListener();
        distributor.getAddedSubscriptionProvider().setSubscriptionListener(al);
        distributor.getRemovedSubscriptionProvider().setSubscriptionListener(rl);
        // create agent with large subscription. make sure subscription gets reported
        QDAgent agent1 = history.agentBuilder().build();
        AsserteableListener dl1 = new AsserteableListener();
        agent1.setDataListener(dl1);
        sop.sub(agent1, getSubscription(TIME1));
        al.assertAvailable();
        rl.assertNotAvailable();
        dl1.assertNotAvailable();
        SubscriptionMap origSub1 = new SubscriptionMap(SCHEME, getSubscription(TIME1));
        SubscriptionMap sub1 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub1", origSub1, sub1);
        // distribute data with TIME1 and up (fill up cache)
        DataBuffer data = getData();
        distributor.processData(data.examiningIterator());
        // make sure agent1 receives all of that data, but in reverse order
        dl1.assertAvailable();
        ComparingDataVisitor.compare(agent1, data.examiningIterator(true));

        // create 2nd agent with smaller subscription. make sure subscription does not gets reported
        QDAgent agent2 = history.agentBuilder().build();
        AsserteableListener dl2 = new AsserteableListener();
        agent2.setDataListener(dl2);
        sop.sub(agent2, getSubscription(TIME2));
        al.assertNotAvailable();
        rl.assertNotAvailable();
        // make sure agent2 receives only data it has just subscibed to (in reverse order).
        dl2.assertAvailable();
        ComparingDataVisitor.compare(agent2, data.examiningIterator(data.size() - 1, CNT2 - 1));

        // uses setSubscription to subscribe again. Make sure data is NOT received again
        agent2.setSubscription(getSubscription(TIME2));
        al.assertNotAvailable();
        rl.assertNotAvailable();
        dl2.assertNotAvailable();

        // uses addSubscription to subscribe again. Make sure data is received again
        agent2.addSubscription(getSubscription(TIME2));
        al.assertNotAvailable();
        rl.assertNotAvailable();
        // make sure agent2 receives only data it has just subscibed to (in reverse order).
        dl2.assertAvailable();
        ComparingDataVisitor.compare(agent2, data.examiningIterator(data.size() - 1, CNT2 - 1));
    }

    private void cacheSendLarger(SubOp sop) {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDDistributor distributor = history.distributorBuilder().build();
        AsserteableListener al = new AsserteableListener();
        AsserteableListener rl = new AsserteableListener();
        distributor.getAddedSubscriptionProvider().setSubscriptionListener(al);
        distributor.getRemovedSubscriptionProvider().setSubscriptionListener(rl);
        // create agent with small subscription. make sure subscription gets reported
        QDAgent agent1 = history.agentBuilder().build();
        AsserteableListener dl1 = new AsserteableListener();
        agent1.setDataListener(dl1);
        sop.sub(agent1, getSubscription(TIME2));
        al.assertAvailable();
        rl.assertNotAvailable();
        dl1.assertNotAvailable();
        SubscriptionMap origSub1 = new SubscriptionMap(SCHEME, getSubscription(TIME2));
        SubscriptionMap sub1 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub1", origSub1, sub1);

        // distribute data for small subscription and make sure it fully arrives (in reverse order)
        DataBuffer data = getData();
        distributor.processData(data.examiningIterator(CNT2, CNT));
        dl1.assertAvailable();
        ComparingDataVisitor.compare(agent1, data.examiningIterator(CNT - 1, CNT2 - 1));

        // create agent with larger subscription. make sure subscrpiton gets reported
        QDAgent agent2 = history.agentBuilder().build();
        AsserteableListener dl2 = new AsserteableListener();
        agent2.setDataListener(dl2);
        sop.sub(agent2, getSubscription(TIME1));
        al.assertAvailable();
        rl.assertNotAvailable();
        SubscriptionMap origSub2 = new SubscriptionMap(SCHEME, getSubscription(TIME1));
        SubscriptionMap sub2 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub2", origSub2, sub2);

        // make sure we don't have data on agent1, but have previously distributed data on agent2
        dl1.assertNotAvailable();
        dl2.assertAvailable();
        ComparingDataVisitor.compare(agent2, data.examiningIterator(CNT - 1, CNT2 - 1));

        // distribute all the data (even repeat data we have just sent).
        distributor.processData(data.examiningIterator());

        // make sure agent1 gets nothing and agent2 gets rest of it
        dl1.assertNotAvailable();
        ComparingDataVisitor.compare(agent1, DataBuffer.VOID);
        dl2.assertAvailable();
        ComparingDataVisitor.compare(agent2, data.examiningIterator(CNT2 - 1, -1));
    }

    private void cacheSendMoreData(SubOp sop) {
        QDHistory history = QDFactory.getDefaultFactory().createHistory(SCHEME);
        QDDistributor distributor = history.distributorBuilder().build();
        AsserteableListener al = new AsserteableListener();
        AsserteableListener rl = new AsserteableListener();
        distributor.getAddedSubscriptionProvider().setSubscriptionListener(al);
        distributor.getRemovedSubscriptionProvider().setSubscriptionListener(rl);
        // create agent with small subscription. make sure subscription gets reported
        QDAgent agent1 = history.agentBuilder().build();
        AsserteableListener dl1 = new AsserteableListener();
        agent1.setDataListener(dl1);
        sop.sub(agent1, getSubscription(TIME_MAX));
        al.assertAvailable();
        rl.assertNotAvailable();
        dl1.assertNotAvailable();
        SubscriptionMap origSub1 = new SubscriptionMap(SCHEME, getSubscription(TIME_MAX));
        SubscriptionMap sub1 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub1", origSub1, sub1);

        // create agent with larger subscription. make sure subscrpiton gets reported
        QDAgent agent2 = history.agentBuilder().build();
        AsserteableListener dl2 = new AsserteableListener();
        agent2.setDataListener(dl2);
        sop.sub(agent2, getSubscription(TIME1));
        al.assertAvailable();
        rl.assertNotAvailable();
        SubscriptionMap origSub2 = new SubscriptionMap(SCHEME, getSubscription(TIME1));
        SubscriptionMap sub2 = new SubscriptionMap(SCHEME, distributor.getAddedRecordProvider());
        assertEquals("sub2", origSub2, sub2);

        // make sure we don't have data on agent1
        dl1.assertNotAvailable();
        dl2.assertNotAvailable();

        // distribute all the data
        DataBuffer data = getData();
        distributor.processData(data.examiningIterator());

        // make sure agent1 gets nothing and agent2 gets it all
        dl1.assertNotAvailable();
        ComparingDataVisitor.compare(agent1, DataBuffer.VOID);

        dl2.assertAvailable();
        ComparingDataVisitor.compare(agent2, data.examiningIterator(true));
    }

    private RecordBuffer getSubscription(long time) {
        RecordBuffer sb = new RecordBuffer(RecordMode.HISTORY_SUBSCRIPTION);
        sb.visitRecord(RECORD, CIPHER, null, time);
        return sb;
    }

    private DataBuffer getData() {
        DataBuffer data = new DataBuffer();
        for (int i = 0; i < CNT; i++) {
            long time = TIME1 + TIME_STEP * i;
            data.visitRecord(RECORD, CIPHER, null);
            data.visitIntField(RECORD.getIntField(0), (int) (time >>> 32));
            data.visitIntField(RECORD.getIntField(1), (int) (time));
            for (int j = 2; j < RECORD.getIntFieldCount(); j++)
                data.visitIntField(RECORD.getIntField(j), rnd.nextInt());
            for (int j = 0; j < RECORD.getObjFieldCount(); j++)
                data.visitObjField(RECORD.getObjField(j), "String" + rnd.nextInt());
        }
        return data;
    }
}
