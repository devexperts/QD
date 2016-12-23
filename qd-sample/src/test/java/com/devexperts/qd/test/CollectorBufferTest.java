/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import java.util.ArrayList;
import java.util.Random;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;
import junit.framework.*;

/**
 * Test outgoing data buffering behaviour in Stream and History.
 * Also check that buffer stats are correctly reported.
 */
public class CollectorBufferTest extends TestCase {
    private static final int SEED = 20090923;
    private static final int REPEAT = 100;
    private static final DataScheme SCHEME = new TestDataScheme(2, SEED, TestDataScheme.Type.HAS_TIME_AND_VALUE);
    private static final int MAX_SIZE = 5;

    private String[] symbols;

    public static Test suite() {
        TestSuite suit = new TestSuite(CollectorBufferTest.class.getName());

        // many symbols
        addAll(suit, new TestSuite(CollectorBufferTest.class));

        // repeat with few symbols
        TestSuite child = new TestSuite(CollectorBufferTest.class);
        for (int i = 0; i < child.countTestCases(); i++)
            ((CollectorBufferTest) child.testAt(i)).symbols = new String[] { "A", "B", "HABAHABA" };
        addAll(suit, child);
        return suit;
    }

    private static void addAll(TestSuite suit, TestSuite child) {
        for (int i = 0; i < child.countTestCases(); i++) {
            TestCase test = (TestCase) child.testAt(i);
            suit.addTest(test);
        }
    }

    public void testStreamBufferCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), false);
    }

    public void testHistoryBufferCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), false);
    }

    public void testStreamBufferPartialCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), true);
    }

    public void testHistoryBufferPartialCleanup() {
        checkBufferCleanup(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), true);
    }

    public void testStreamBufferOverflowDropNewest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), false);
    }

    public void testHistoryBufferOverflowDropNewest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), false);
    }

    public void testStreamBufferOverflowDropOldest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createStream(SCHEME, createStats()), true);
    }

    public void testHistoryBufferOverflowDropOldest() {
        checkBufferOverflow(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()), true);
    }

    public void testHistoryBufferAddSubResend() {
        checkBufferAddSubResend(QDFactory.getDefaultFactory().createHistory(SCHEME, createStats()));
    }

    private QDStats createStats() {
        return new QDStats(QDStats.SType.ANY, SCHEME);
    }

    private void checkBufferCleanup(QDCollector collector, boolean partial) {
        QDStats buffer_stats = getBufferStats(collector);
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
                ArrayList<SymbolObjectMap<Boolean>> still_sub = allocateMap();

                int part = rnd.nextInt(sub.size());
                unsubPart(agent, sub, still_sub, part);

                // check it
                retrieveAndCheckData(agent, buf, checksum, still_sub);

                // now correct data a little bit (increase time), publish and check it again
                incTime(buf);
                dist.processData(buf);
                retrieveAndCheckData(agent, buf, checksum, still_sub);

                // now correct data a little bit (increase time), publish it again
                // it should get buffered in History!
                incTime(buf);
                dist.processData(buf);

                // unsubscibe again (this time leave smaller subscription)
                part = rnd.nextInt(Math.max(1, part));
                unsubPart(agent, sub, still_sub, part);

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
                retrieveAndCheckData(agent, buf, checksum, still_sub);
            }

            // unsubscribe fully
            agent.getRemovingSubscriptionConsumer().processSubscription(sub);
            assertEquals(0, agent.getSubscriptionSize());
            assertEquals(0, buffer_stats.getValue(QDStats.SValue.RID_SIZE));

            // make sure there's no data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
        }
    }

    private <T> ArrayList<SymbolObjectMap<T>> allocateMap() {
        ArrayList<SymbolObjectMap<T>> result = new ArrayList<SymbolObjectMap<T>>();
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

    private void unsubPart(QDAgent agent, SubscriptionBuffer sub, ArrayList<SymbolObjectMap<Boolean>> still_sub, int still_sub_part) {
        SubscriptionIterator it = sub.examiningIterator();
        DataRecord rec;
        for (int i = 0; i < still_sub_part; i++) {
            rec = it.nextRecord();
            still_sub.get(rec.getId()).put(it.getCipher(), it.getSymbol(), true);
        }
        while ((rec = it.nextRecord()) != null) {
            still_sub.get(rec.getId()).remove(it.getCipher(), it.getSymbol());
        }
        int expected_sub_size = 0;
        for (int i = 0; i < SCHEME.getRecordCount(); i++)
            expected_sub_size += still_sub.get(i).size();
        it = sub.examiningIterator();
        for (int i = 0; i < still_sub_part; i++) // skip again first part
            it.nextRecord();
        agent.getRemovingSubscriptionConsumer().processSubscription(it);
        assertEquals(expected_sub_size, agent.getSubscriptionSize());
    }

    private void retrieveAndCheckData(QDAgent agent, RecordBuffer orig_buf, ArrayList<SymbolObjectMap<Integer>> checksum, ArrayList<SymbolObjectMap<Boolean>> still_sub) {
        // compute original data checksum
        orig_buf.rewind();
        RecordCursor cur;
        while ((cur = orig_buf.next()) != null) {
            Integer oldsum = checksum.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            checksum.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                (oldsum == null ? 0 : oldsum) + computeCheckSum(cur));
        }

        // make sure there's exactly remaining data (data where we still_sub)
        RecordBuffer buf = new RecordBuffer();
        agent.retrieveData(buf);
        while ((cur = buf.next()) != null) {
            assertTrue(still_sub.get(cur.getRecord().getId()).contains(cur.getCipher(), cur.getSymbol()));
            // update checksum
            Integer oldsum = checksum.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            checksum.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                oldsum - computeCheckSum(cur));
        }
        // make sure that all checksums for still_sub data agree
        for (int i = 0; i < SCHEME.getRecordCount(); i++) {
            final SymbolObjectMap<Integer> csm = checksum.get(i);
            still_sub.get(i).examineEntries(new SymbolObjectVisitor<Boolean>() {
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

    private void checkBufferOverflow(QDCollector collector, boolean drop_oldest) {
        QDStats buffer_stats = getBufferStats(collector);
        QDAgent agent = collector.agentBuilder()
            .withKeyProperties("name=BufferOverflow")
            .build();
        agent.setBufferOverflowStrategy(MAX_SIZE, drop_oldest, true);
        QDDistributor dist = collector.distributorBuilder().build();
        TestSubscriptionProvider subp = new TestSubscriptionProvider(SCHEME, SEED, symbols);
        final SubscriptionBuffer sub = new SubscriptionBuffer();
        TestDataProvider datap = new TestDataProvider(SCHEME, SEED, symbols);

        RecordBuffer srcbuf = new RecordBuffer();
        RecordBuffer dstbuf = new RecordBuffer();

        final Random rnd = new Random(20090924);
        final ArrayList<SymbolObjectMap<Long>> known_times = allocateMap();
        final ArrayList<SymbolObjectMap<Integer>> record_counts = allocateMap();
        boolean is_history = collector instanceof QDHistory;
        for (int rep = 0; rep < REPEAT; rep++) {
            sub.clear();

            final int[] resend_count = new int[1];
            final ArrayList<SymbolObjectMap<Boolean>> sub_flags = allocateMap();

            subp.retrieveSubscription(new SubscriptionVisitor() {
                public boolean hasCapacity() {
                    return true;
                }

                public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
                    Integer count = record_counts.get(record.getId()).get(cipher, symbol);
                    Boolean is_sub = sub_flags.get(record.getId()).get(cipher, symbol);
                    if (is_sub != null)
                        return; // newer subscribe twice to the same record in one batch for this test
                    if (count != null) {
                        if (rnd.nextBoolean())
                            return; // don't resubscribe every other time to already published records
                        resend_count[0] += count;
                    }
                    sub_flags.get(record.getId()).put(cipher, symbol, Boolean.TRUE);
                    known_times.get(record.getId()).put(cipher, symbol, null);
                    sub.visitRecord(record, cipher, symbol, time);
                }
            });

            agent.getAddingSubscriptionConsumer().processSubscription(sub);
            assertTrue(agent.getSubscriptionSize() > 0);

            int expected_retrieve_size = 0;
            if (is_history)
                expected_retrieve_size = resend_count[0];

            dstbuf.clear();
            agent.retrieveData(dstbuf); // retrieve all data that was "resent"
            assertEquals(expected_retrieve_size, dstbuf.size()); // check now much data was resent
            assertEquals(0, buffer_stats.getValue(QDStats.SValue.RID_SIZE));
            updateKnownTimes(dstbuf, known_times);

            srcbuf.clear();
            datap.retrieveData(srcbuf);
            RecordCursor cur;

            // compute how much data will get buffered
            int buffered_cnt = 0;
            int nonbuffered_cnt = 0;
            while ((cur = srcbuf.next()) != null) {
                Long known_time = known_times.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
                if (known_time != null && cur.getTime() > known_time) {
                    // record gets buffered when it's time is larger than last retrieved one
                    buffered_cnt++;
                } else {
                    nonbuffered_cnt++;
                }
                Integer record_count = record_counts.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
                record_counts.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(), record_count == null ? 1 : record_count + 1);
            }
            srcbuf.rewind();
            dist.processData(srcbuf);

            int expected_buffer_size;
            if (is_history) {
                expected_buffer_size = Math.min(buffered_cnt, MAX_SIZE);
                expected_retrieve_size = expected_buffer_size + nonbuffered_cnt;
            } else {
                expected_buffer_size = Math.min(buffered_cnt + nonbuffered_cnt, MAX_SIZE);
                expected_retrieve_size = expected_buffer_size;
            }
            assertEquals(expected_buffer_size, buffer_stats.getValue(QDStats.SValue.RID_SIZE));

            dstbuf.clear();
            agent.retrieveData(dstbuf);
            assertEquals(expected_retrieve_size, dstbuf.size());
            assertEquals(0, buffer_stats.getValue(QDStats.SValue.RID_SIZE));
            updateKnownTimes(dstbuf, known_times);

            if (is_history) {
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
        QDStats buffer_stats = getBufferStats(collector);
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
            ArrayList<SymbolObjectMap<Boolean>> still_sub = allocateMap();
            buildSubMap(still_sub, sub.examiningIterator());
            retrieveAndCheckData(agent, buf, checksum, still_sub);

            // change data values and republish
            tweakValues(buf);
            dist.processData(buf);
            retrieveAndCheckData(agent, buf, checksum, still_sub);

            // change data values again and republish
            tweakValues(buf);
            dist.processData(buf);
            // ... and resubscribe again before checking what we've got
            agent.getAddingSubscriptionConsumer().processSubscription(sub.examiningIterator());
            retrieveAndCheckData(agent, buf, checksum, still_sub);

            // unsubscribe fully
            agent.getRemovingSubscriptionConsumer().processSubscription(sub);
            assertEquals(0, agent.getSubscriptionSize());
            assertEquals(0, buffer_stats.getValue(QDStats.SValue.RID_SIZE));

            // make sure there's no data
            buf.clear();
            agent.retrieveData(buf);
            assertEquals(0, buf.size());
        }
    }

    private void buildSubMap(ArrayList<SymbolObjectMap<Boolean>> still_sub, SubscriptionIterator it) {
        DataRecord rec;
        while ((rec = it.nextRecord()) != null)
            still_sub.get(rec.getId()).put(it.getCipher(), it.getSymbol(), true);
    }

    private void updateKnownTimes(RecordBuffer buf, ArrayList<SymbolObjectMap<Long>> known_times) {
        RecordCursor cur;
        while ((cur = buf.next()) != null) {
            Long known_time = known_times.get(cur.getRecord().getId()).get(cur.getCipher(), cur.getSymbol());
            known_times.get(cur.getRecord().getId()).put(cur.getCipher(), cur.getSymbol(),
                known_time == null ? cur.getTime() : Math.min(known_time, cur.getTime()));
        }
    }

    private QDStats getBufferStats(QDCollector collector) {
        return collector.getStats().getOrCreate(QDStats.SType.AGENT_DATA);
    }
}
