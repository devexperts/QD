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
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.impl.stripe.StripedFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class AttachmentTest {
    private static final TestDataScheme SCHEME = new TestDataScheme(20140702, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final DataRecord RECORD = SCHEME.getRecord(0);
    private static final String SYMBOL = "ATT";
    private static final int CIPHER = SCHEME.getCodec().encode(SYMBOL);

    @Test
    public void testTicker() {
        check(QDFactory.getDefaultFactory().createTicker(SCHEME), false);
    }

    @Test
    public void testStream() {
        check(QDFactory.getDefaultFactory().createStream(SCHEME), false);
    }

    @Test
    public void testHistory() {
        check(QDFactory.getDefaultFactory().createHistory(SCHEME), false);
    }

    @Test
    public void testStripedTicker() {
        check(new StripedFactory(4).createTicker(SCHEME), false);
    }

    @Test
    public void testStripedStream() {
        check(new StripedFactory(4).createStream(SCHEME), false);
    }

    @Test
    public void testStripedHistory() {
        check(new StripedFactory(4).createHistory(SCHEME), false);
    }

    @Test
    public void testWildcardStream() {
        QDStream stream = QDFactory.getDefaultFactory().createStream(SCHEME);
        stream.setEnableWildcards(true);
        check(stream, true);
    }

    @Test
    public void testWildcardStripedStream() {
        QDStream stream = new StripedFactory(4).createStream(SCHEME);
        stream.setEnableWildcards(true);
        check(stream, true);
    }

    private void check(QDCollector collector, boolean wildcard) {
        QDDistributor distributor = collector.distributorBuilder().build();
        QDAgent agent = collector.agentBuilder().withAttachmentStrategy(new Strategy()).build();

        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION.withAttachment());
        RecordBuffer dataIn = RecordBuffer.getInstance();
        RecordBuffer dataOut = RecordBuffer.getInstance(RecordMode.DATA.withAttachment());
        Random r = new Random(20140702);

        for (int rep = 0;; rep++) {
            // subscribe with attachment of 2
            initSub(sub, wildcard).setAttachment(2);
            agent.addSubscription(sub);
            // process some data
            randomData(dataIn, r);
            distributor.process(dataIn);
            // retrieve data and check attachment of 2
            dataOut.clear();
            agent.retrieve(dataOut);
            assertEquals(1, dataOut.size());
            assertEquals(2, dataOut.next().getAttachment());

            // subscribe AGAIN with attachment of 3 (2 + 3 = 5)
            initSub(sub, wildcard).setAttachment(3);
            agent.addSubscription(sub);
            // process some data
            randomData(dataIn, r);
            distributor.process(dataIn);
            // retrieve data and check attachment of 5
            dataOut.clear();
            agent.retrieve(dataOut);
            // note that history also resends its global data (first data item) on addSub
            int expectedSize = collector.getContract() == QDContract.HISTORY ? 2 : 1;
            assertEquals(expectedSize, dataOut.size());
            for (int i = 0; i < expectedSize; i++)
                assertEquals(5, dataOut.next().getAttachment());

            if (rep == 10)
                break; // last time break without unsubscribe

            // unsubscribe with attachment of 1 (5 - 1 = 4)
            initSub(sub, wildcard).setAttachment(1);
            agent.removeSubscription(sub);
            // process some data
            randomData(dataIn, r);
            distributor.process(dataIn);
            // retrieve data and check attachment of 4
            dataOut.clear();
            agent.retrieve(dataOut);
            assertEquals(1, dataOut.size());
            assertEquals(4, dataOut.next().getAttachment());

            // unsubscribe with attachment of 4 (4 - 1 = 0 -- nothing left)
            initSub(sub, wildcard).setAttachment(4);
            agent.removeSubscription(sub);
            // process some data
            randomData(dataIn, r);
            distributor.process(dataIn);
            // retrieve data and check there's nothing (no subscription anymore)
            dataOut.clear();
            agent.retrieve(dataOut);
            assertEquals(0, dataOut.size());
        }

        // finally, test attachments coming from closeAndExamineDataBySubscription method
        dataOut.clear();
        agent.closeAndExamineDataBySubscription(dataOut);
        int expectedSize;
        switch (collector.getContract()) {
            case TICKER: expectedSize = 1; break;
            case STREAM: expectedSize = 0; break;
            case HISTORY: expectedSize = 2; break;
            default: throw new AssertionError();
        }
        assertEquals(expectedSize, dataOut.size());
        for (int i = 0; i < expectedSize; i++)
            assertEquals(5, dataOut.next().getAttachment());
    }

    private RecordCursor initSub(RecordBuffer sub, boolean wildcard) {
        sub.clear();
        // Wildcard does not support attachment by itself, but
        // when it is used in conjunction with individual subscription, it must work.
        if (wildcard)
            sub.add(RECORD, SCHEME.getCodec().getWildcardCipher(), null);
        return sub.add(RECORD, CIPHER, SYMBOL);
    }

    private void randomData(RecordBuffer dataIn, Random r) {
        dataIn.clear();
        RecordCursor cur = dataIn.add(RECORD, CIPHER, SYMBOL);
        for (int i = 0; i < cur.getIntCount(); i++)
            cur.setInt(i, r.nextInt(10000));
        for (int i = 0; i < cur.getObjCount(); i++)
            cur.setObj(i, r.nextInt(10000));
    }

    private static class Strategy implements QDAgent.AttachmentStrategy<Integer> {
        @Override
        public Integer updateAttachment(Integer oldAttachment, RecordCursor cursor, boolean remove) {
            Integer att = (Integer) cursor.getAttachment();
            int old = oldAttachment == null ? 0 : oldAttachment;
            int cur = att == null ? 0 : att;
            int result = remove ? old - cur : old + cur;
            return result == 0 ? null : result;
        }
    }
}
