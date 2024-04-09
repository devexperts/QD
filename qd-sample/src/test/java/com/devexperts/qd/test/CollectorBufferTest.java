/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
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
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test outgoing data buffering behaviour in Stream and History.
 * Also check that buffer stats are correctly reported.
 */
@RunWith(Parameterized.class)
public class CollectorBufferTest {
    private static final int SEED = 20090923;
    private static final int REPEAT = 100;
    private static final DataScheme SCHEME = new TestDataScheme(2, SEED, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final int MAX_SIZE = 5;

    private String[] symbols;

    @Parameters(name = "{index}")
    public static String[][][] data() {
        return new String[][][] {
            { null },
            { new String[] { "A", "B", "HABAHABA" } }
        };
    }

    public CollectorBufferTest(String[] symbols) {
        this.symbols = symbols;
    }

    @Test
    public void testStreamBufferCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), false);
    }

    @Test
    public void testHistoryBufferCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), false);
    }

    @Test
    public void testStreamBufferPartialCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), true);
    }

    @Test
    public void testHistoryBufferPartialCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), true);
    }

    @Test
    public void testStreamBufferOverflowDropNewest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), false);
    }

    @Test
    public void testHistoryBufferOverflowDropNewest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), false);
    }

    @Test
    public void testStreamBufferOverflowDropOldest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), true);
    }

    @Test
    public void testHistoryBufferOverflowDropOldest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), true);
    }

    @Test
    public void testHistoryBufferAddSubResend() {
        checkBufferAddSubResend(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()));
    }

    private QDStats createStats() {
        return new QDStats(QDStats.SType.ANY, SCHEME);
    }

    private void checkBufferCleanup(QDCollector collector, boolean partial) {
        QDAgent agent = collector.agentBuilder()
            .withKeyProperties("name=BufferCleanup")
            .build();
        QDDistributor dist = collector.distributorBuilder().build();
        TestSubscriptionProvider subp = new TestSubscriptionProvider(SCHEME, SEED, symbols);
        SubscriptionBuffer sub = new SubscriptionBuffer();
        TestDataProvider datap = new TestDataProvider(SCHEME, SEED, symbols);
        RecordBuffer buf = new RecordBuffer();
        Random rnd = new Random(20090925);
        for (int rep = 0; rep < REPEAT; rep++) {
            // subscribe
            sub.clear();
            subp.retrieveSubscription(sub);
            agent.getAddingSubscriptionConsumer().processSubscription(sub.examiningIterator());
            assertTrue(agent.getSubscriptionSize() > 0);

            // publish data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
            datap.retrieveData(buf);
            dist.processData(buf);

            if (partial) {
                // unsubscribe partially
                ArrayList<SymbolObjectMap<Integer>> checksum = allocateMap();
                ArrayList<SymbolObjectMap<Boolean>> stillSub = allocateMap();

                int part = rnd.nextInt(sub.size());
                unsubPart(agent, sub, stillSub, part);

                // check it
                retrieveAndCheckData(agent, buf, checksum, stillSub);

                // now correct data a little bit (increase time), publish and check it again
                incTime(buf);
                dist.processData(buf);
                retrieveAndCheckData(agent, buf, checksum, stillSub);

                // now correct data a little bit (increase time), publish it again
                // it should get buffered in History!
                incTime(buf);
                dist.processData(buf);

                // unsubscibe again (this time leave smaller subscription)
                part = rnd.nextInt(Math.max(1, part));
                unsubPart(agent, sub, stillSub, part);

                // for history -- chanage data in last still subscribed item and replace it (in buffer)
                // this gets code coverage for a additional case in history
                if (collector instanceof QDHistory) {
                    buf.rewind();
                    for (int i = 1; i < part; i++)
                        buf.next(); // skip (part - 1) records
                    tweakValue(buf.writeCurrent());
                    dist.processData(buf);
                }

                // check it
                retrieveAndCheckData(agent, buf, checksum, stillSub);
            }

            // unsubscribe fully
            agent.getRemovingSubscriptionConsumer().processSubscription(sub);
            assertEquals(0, agent.getSubscriptionSize());
            assertEquals(0, getBufferStats(collector).getValue(QDStats.SValue.RID_SIZE));

            // make sure there's no data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
        }
    }

    private <T> ArrayList<SymbolObjectMap<T>> allocateMap() {
        ArrayList<SymbolObjectMap<T>> result = new ArrayList<>();
        for (int i = 0; i < SCHEME.getRecordCount(); i++)
            result.add(SymbolObjectMap.<T>createInstance());
        return result;
    }

    private void incTime(RecordBuffer buf) {
        buf.rewind();
        RecordCursor cur;
        while ((cur = buf.writeNext()) != null) {
            cur.setTime(cur.getTime() + 1);
        }
        buf.rewind();
    }

    private void tweakValues(RecordBuffer buf) {
        buf.rewind();
        RecordCursor cur;
        while ((cur = buf.writeNext()) != null) {
            tweakValue(cur);
        }
        buf.rewind();
    }

    private void tweakValue(RecordCursor cur) {
        for (int j = 2; j < cur.getIntCount(); j++)
            cur.setInt(j, cur.getInt(j) + j);
    }

    private void unsubPart(QDAgent agent, SubscriptionBuffer sub, ArrayList<SymbolObjectMap<Boolean>> stillSub,
        int stillSubPart)
    {
        SubscriptionIterator it = sub.examiningIterator();
        DataRecord rec;
        for (int i = 0; i < stillSubPart; i++) {
            rec = it.nextRecord();
            stillSub.get(rec.getId()).put(it.getCipher(), it.getSymbol(), true);
        }
        while ((rec = it.nextRecord()) != null) {
            stillSub.get(rec.getId()).remove(it.getCipher(), it.getSymbol());
        }
        int expectedSubSize = 0;
        for (int i = 0; i < SCHEME.getRecordCount(); i++)
            expectedSubSize += stillSub.get(i).size();
        it = sub.examiningIterator();
        for (int i = 0; i < stillSubPart; i++) // skip again first part
            it.nextRecord();
        agent.getRemovingSubscriptionConsumer().processSubscription(it);
        assertEquals(expectedSubSize, agent.getSubscriptionSize());
    }

    private void retrieveAndCheckData(QDAgent agent, RecordBuffer origBuf, ArrayList<SymbolObjectMap<Integer>> checksum,
        ArrayList<SymbolObjectMap<Boolean>> stillSub)
    {
        // compute original data checksum
        origBuf.rewind();
        RecordCursor cur;
        while ((cur = origBuf.next()) != null) {
            Integer oldsum = checksum.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            checksum.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                (oldsum == null ? 0 : oldsum) + computeCheckSum(cur));
        }

        // make sure there's exactly remaining data (data where we stillSub)
        RecordBuffer buf = new RecordBuffer();
        agent.retrieveData(buf);
        while ((cur = buf.next()) != null) {
            assertTrue(stillSub.get(cur.getRecord().getId()).contains(cur.getCipher(), cur.getSymbol()));
            // update checksum
            Integer oldsum = checksum.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            checksum.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                oldsum - computeCheckSum(cur));
        }
        // make sure that all checksums for stillSub data agree
        for (int i = 0; i < SCHEME.getRecordCount(); i++) {
            final SymbolObjectMap<Integer> csm = checksum.get(i);
            stillSub.get(i).examineEntries(new SymbolObjectVisitor<Boolean>() {
                public boolean hasCapacity() {
                    return true;
                }

                public void visitEntry(int cipher, String symbol, Boolean value) {
                    assertEquals(0, (int) csm.get(cipher, symbol));
                }
            });
        }
    }

    private int computeCheckSum(RecordCursor cur) {
        int sum = 0;
        for (int i = 0; i < cur.getIntCount(); i++)
            sum ^= Integer.rotateLeft(cur.getInt(i), i);
        return sum;
    }

    private void checkBufferOverflow(QDCollector collector, boolean dropOldest) {
        QDAgent agent = collector.agentBuilder()
            .withKeyProperties("name=BufferOverflow")
            .build();
        agent.setBufferOverflowStrategy(MAX_SIZE, dropOldest, true);
        QDDistributor dist = collector.distributorBuilder().build();
        TestSubscriptionProvider subp = new TestSubscriptionProvider(SCHEME, SEED, symbols);
        final SubscriptionBuffer sub = new SubscriptionBuffer();
        TestDataProvider datap = new TestDataProvider(SCHEME, SEED, symbols);

        RecordBuffer srcbuf = new RecordBuffer();
        RecordBuffer dstbuf = new RecordBuffer();

        final Random rnd = new Random(20090924);
        final ArrayList<SymbolObjectMap<Long>> knownTimes = allocateMap();
        final ArrayList<SymbolObjectMap<Integer>> recordCounts = allocateMap();
        boolean isHistory = collector instanceof QDHistory;
        for (int rep = 0; rep < REPEAT; rep++) {
            sub.clear();

            final int[] resendCount = new int[1];
            final ArrayList<SymbolObjectMap<Boolean>> subFlags = allocateMap();

            subp.retrieveSubscription(new SubscriptionVisitor() {
                public boolean hasCapacity() {
                    return true;
                }

                public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
                    Integer count = recordCounts.get(record.getId()).get(cipher, symbol);
                    Boolean isSub = subFlags.get(record.getId()).get(cipher, symbol);
                    if (isSub != null)
                        return; // newer subscribe twice to the same record in one batch for this test
                    if (count != null) {
                        if (rnd.nextBoolean())
                            return; // don't resubscribe every other time to already published records
                        resendCount[0] += count;
                    }
                    subFlags.get(record.getId()).put(cipher, symbol, Boolean.TRUE);
                    knownTimes.get(record.getId()).put(cipher, symbol, null);
                    sub.visitRecord(record, cipher, symbol, time);
                }
            });

            agent.getAddingSubscriptionConsumer().processSubscription(sub);
            assertTrue(agent.getSubscriptionSize() > 0);

            int expectedRetrieveSize = 0;
            if (isHistory)
                expectedRetrieveSize = resendCount[0];

            dstbuf.clear();
            agent.retrieveData(dstbuf); // retrieve all data that was "resent"
            assertEquals(expectedRetrieveSize, dstbuf.size()); // check now much data was resent
            assertEquals(0, getBufferStats(collector).getValue(QDStats.SValue.RID_SIZE));
            updateKnownTimes(dstbuf, knownTimes);

            srcbuf.clear();
            datap.retrieveData(srcbuf);
            RecordCursor cur;

            // compute how much data will get buffered
            int bufferedCount = 0;
            int nonBufferedCount = 0;
            while ((cur = srcbuf.next()) != null) {
                Long knownTime = knownTimes.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
                if (knownTime != null && cur.getTime() > knownTime) {
                    // record gets buffered when it's time is larger than last retrieved one
                    bufferedCount++;
                } else {
                    nonBufferedCount++;
                }
                Integer recordCount = recordCounts.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
                recordCounts.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(), recordCount == null ?
                    1 : recordCount + 1);
            }
            srcbuf.rewind();
            dist.processData(srcbuf);

            int expectedBufferSize;
            if (isHistory) {
                expectedBufferSize = Math.min(bufferedCount, MAX_SIZE);
                expectedRetrieveSize = expectedBufferSize + nonBufferedCount;
            } else {
                expectedBufferSize = Math.min(bufferedCount + nonBufferedCount, MAX_SIZE);
                expectedRetrieveSize = expectedBufferSize;
            }
            assertEquals(expectedBufferSize, getBufferStats(collector).getValue(QDStats.SValue.RID_SIZE));

            dstbuf.clear();
            agent.retrieveData(dstbuf);
            assertEquals(expectedRetrieveSize, dstbuf.size());
            assertEquals(0, getBufferStats(collector).getValue(QDStats.SValue.RID_SIZE));
            updateKnownTimes(dstbuf, knownTimes);

            if (isHistory) {
                // try to deliver data again and make sure it does not go to consumers
                srcbuf.rewind();
                dist.processData(srcbuf);
                dstbuf.clear();
                agent.retrieveData(dstbuf);
                assertEquals(0, dstbuf.size());
            }
        }
    }

    private void checkBufferAddSubResend(QDCollector collector) {
        QDAgent agent = collector.agentBuilder().build();
        QDDistributor dist = collector.distributorBuilder().build();
        TestSubscriptionProvider subp = new TestSubscriptionProvider(SCHEME, SEED, symbols);
        SubscriptionBuffer sub = new SubscriptionBuffer();
        TestDataProvider datap = new TestDataProvider(SCHEME, SEED, symbols);
        RecordBuffer buf = new RecordBuffer();
        Random rnd = new Random(20090927);
        for (int rep = 0; rep < REPEAT; rep++) {
            // subscribe
            sub.clear();
            subp.retrieveSubscription(sub);
            agent.getAddingSubscriptionConsumer().processSubscription(sub.examiningIterator());
            assertTrue(agent.getSubscriptionSize() > 0);

            // publish data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
            datap.retrieveData(buf);
            dist.processData(buf);

            // retrieve data and make sure it's the same data (data stays in glogal buffer)
            ArrayList<SymbolObjectMap<Integer>> checksum = allocateMap();
            ArrayList<SymbolObjectMap<Boolean>> stillSub = allocateMap();
            buildSubMap(stillSub, sub.examiningIterator());
            retrieveAndCheckData(agent, buf, checksum, stillSub);

            // change data values and republish
            tweakValues(buf);
            dist.processData(buf);
            retrieveAndCheckData(agent, buf, checksum, stillSub);

            // change data values again and republish
            tweakValues(buf);
            dist.processData(buf);
            // ... and resubscribe again before checking what we've got
            agent.getAddingSubscriptionConsumer().processSubscription(sub.examiningIterator());
            retrieveAndCheckData(agent, buf, checksum, stillSub);

            // unsubscribe fully
            agent.getRemovingSubscriptionConsumer().processSubscription(sub);
            assertEquals(0, agent.getSubscriptionSize());
            assertEquals(0, getBufferStats(collector).getValue(QDStats.SValue.RID_SIZE));

            // make sure there's no data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
        }
    }

    private void buildSubMap(ArrayList<SymbolObjectMap<Boolean>> stillSub, SubscriptionIterator it) {
        DataRecord rec;
        while ((rec = it.nextRecord()) != null)
            stillSub.get(rec.getId()).put(it.getCipher(), it.getSymbol(), true);
    }

    private void updateKnownTimes(RecordBuffer buf, ArrayList<SymbolObjectMap<Long>> knownTimes) {
        RecordCursor cur;
        while ((cur = buf.next()) != null) {
            Long knownTime = knownTimes.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            knownTimes.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                knownTime == null ? cur.getTime() : Math.min(knownTime, cur.getTime()));
        }
    }

    private QDStats getBufferStats(QDCollector collector) {
        return collector.getStats().getOrVoid(QDStats.SType.AGENT_DATA);
    }
}
