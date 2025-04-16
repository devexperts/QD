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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.TimeMillisField;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.stats.QDStats;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class HistoryBufferTest {

    private static final Random RANDOM = new Random(0);

    private static final DataRecord RECORD = new DefaultRecord(0, "Test_Record", true,
        new DataIntField[] {
            new TimeMillisField(0, "Test_Record.Time"),
            new CompactIntField(1, "Test_Record.Int0"),
            new CompactIntField(2, "Test_Record.Int1"),
            new CompactIntField(3, "Test_Record.Int2")},
        new DataObjField[] {
            new MarshalledObjField(0, "Test_Record.Obj0"),
            new MarshalledObjField(1, "Test_Record.Obj1")}
    );

    private static final QDStats QD_STATS = new QDStats(QDStats.SType.ANY);

    @Test
    public void testEmptyBuffer() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);
        verifyBuffers(bufferOld, bufferNew, 0, 10);
    }

    @Test
    public void testSimpleInsert() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Fill buffer to near capacity
        int[] largeSequence = IntStream.range(0, 64).toArray();
        generateHistoryBuffer(largeSequence, bufferOld, bufferNew, false);

        verifyBuffers(bufferOld, bufferNew, 0, 64);
    }

    @Test
    public void testInsertBiDirection() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        for (int i = 0, left = 1000, right = 1000; i < 1000; i++) {
            putToBuffers(left - i, bufferOld, bufferNew, false);
            putToBuffers(right + i, bufferOld, bufferNew, false);
        }

        verifyBuffers(bufferOld, bufferNew, 0, 2000);
    }

    @Test
    public void testAvailableCount() {
        for (int i = 0; i < 1000; i++) {
            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int j = 0; j < 2048; j++) {
                boolean shouldRemove = RANDOM.nextBoolean();
                int time = RANDOM.nextInt(2048);

                min = Math.min(min, time);
                max = Math.max(max, time);

                RecordBuffer buf = createRecordBuffer(time);
                RecordCursor cursor = buf.current();

                bufferOld.putRecord(time, cursor, shouldRemove, QD_STATS, 0);
                bufferNew.putRecord(time, cursor, shouldRemove, QD_STATS, 0);
            }

            Assert.assertEquals(bufferOld.getAvailableCount(min - 1, max + 1), bufferNew.getAvailableCount(min - 1, max + 1));
            Assert.assertEquals(bufferOld.getAvailableCount(min, max + 1), bufferNew.getAvailableCount(min, max + 1));
            Assert.assertEquals(bufferOld.getAvailableCount(min - 1, max), bufferNew.getAvailableCount(min - 1, max));
            Assert.assertEquals(
                bufferOld.getAvailableCount((min * 3 + max) / 4, (min + 3 * max) / 4),
                bufferNew.getAvailableCount((min * 3 + max) / 4, (min + 3 * max) / 4));

            verifyBuffers(bufferOld, bufferNew, min, max);
        }
    }

    @Test
    public void testInsertAndRemoveSameElements() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Fill initial data
        int[] initial = IntStream.range(0, 20).toArray();
        generateHistoryBuffer(initial, bufferOld, bufferNew, false);
        // Remove some data
        int[] remove = {5, 7, 9, 11, 13, 15};
        generateHistoryBuffer(remove, bufferOld, bufferNew, true);
        // insert removed data
        generateHistoryBuffer(remove, bufferOld, bufferNew, false);

        verifyBuffers(bufferOld, bufferNew, 0, 20);
    }

    @Test
    public void testRandomInsertRemove() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        for (int i = 0; i < 100000; i++) {
            // Alternate between insert and remove operations
            boolean shouldRemove = RANDOM.nextBoolean();
            int time = RANDOM.nextInt(2048) + 1;

            RecordBuffer buf = createRecordBuffer(time);
            RecordCursor cursor = buf.current();

            bufferOld.putRecord(time, cursor, shouldRemove, QD_STATS, 0);
            bufferNew.putRecord(time, cursor, shouldRemove, QD_STATS, 0);

            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            Assert.assertEquals(bufferOld.getMaxAvailableTime(), bufferNew.getMaxAvailableTime());
            Assert.assertEquals(bufferOld.getMinAvailableTime(), bufferNew.getMinAvailableTime());
            Assert.assertEquals(bufferOld.getAvailableCount(0, 2049), bufferNew.getAvailableCount(0, 2049));
            Assert.assertEquals(bufferOld.getAvailableCount(500, 1500), bufferNew.getAvailableCount(500, 1500));
        }

        verifyBuffers(bufferOld, bufferNew, 0, 2049);
    }

    @Test
    public void testConsecutiveRemoveInsert() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Initial sequence
        int[] initial = {1, 2, 3, 4, 5};
        generateHistoryBuffer(initial, bufferOld, bufferNew, false);

        // Remove and immediately insert at the same position
        for (int i: initial) {
            // Remove
            RecordBuffer removeBuf = createRecordBuffer(i);
            RecordCursor removeCursor = removeBuf.current();
            bufferOld.putRecord(i, removeCursor, true, QD_STATS, 0);
            bufferNew.putRecord(i, removeCursor, true, QD_STATS, 0);
            // Insert
            RecordBuffer insertBuf = createRecordBuffer(i);
            RecordCursor insertCursor = insertBuf.current();
            bufferOld.putRecord(i, insertCursor, false, QD_STATS, 0);
            bufferNew.putRecord(i, insertCursor, false, QD_STATS, 0);
        }

        verifyBuffers(bufferOld, bufferNew, 1, 5);
    }

    @Test
    public void testGapCreationAndFill() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Create sequence with intentional gaps
        int[] gapFull = IntStream.range(11, 13).toArray();
        int[] gapNotFull = IntStream.range(111, 142).toArray();
        Set<Integer> exclusion  = IntStream.concat(Arrays.stream(gapFull), Arrays.stream(gapNotFull))
            .boxed()
            .collect(Collectors.toSet());
        int[] initial = IntStream.range(0, 256).filter(i -> !exclusion.contains(i)).toArray();
        generateHistoryBuffer(initial, bufferOld, bufferNew, false);

        // Fill full gap
        generateHistoryBuffer(gapFull, bufferOld, bufferNew, false);
        // Fill not full gap
        generateHistoryBuffer(gapNotFull, bufferOld, bufferNew, false);

        verifyBuffers(bufferOld, bufferNew, 0, 256);
    }

    @Test
    public void testRandomizedGapInsertion() {
        for (int iterations = 0; iterations < 1000; iterations++) {
            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            int[] sequence = RANDOM.ints(0, 2000).distinct().limit(100).sorted().toArray();
            generateHistoryBuffer(sequence, bufferOld, bufferNew, false);

            List<Integer> gapTimes = new ArrayList<>();
            Set<Integer> times = Arrays.stream(sequence).boxed().collect(Collectors.toSet());
            for (int i = 1; i < sequence[sequence.length - 1]; i++) {
                if (!times.contains(i) && RANDOM.nextBoolean()) {
                    gapTimes.add(i);
                }
            }
            Collections.shuffle(gapTimes);

            generateHistoryBuffer(gapTimes.stream().mapToInt(value -> value).toArray(), bufferOld, bufferNew, false);

            verifyBuffers(bufferOld, bufferNew, sequence[0], sequence[sequence.length - 1]);
        }
    }

    @Test
    public void testRandomRemoval() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        int[] initial = IntStream.range(0, 100).toArray();
        generateHistoryBuffer(initial, bufferOld, bufferNew, false);

        // Random removal of entries
        for (int i = 0; i < 50; i++) {
            int time = RANDOM.nextInt(98) + 1;
            RecordBuffer buf = createRecordBuffer(time);
            RecordCursor cursor = buf.current();

            bufferOld.putRecord(time, cursor, true, QD_STATS, 0);
            bufferNew.putRecord(time, cursor, true, QD_STATS, 0);
        }

        verifyBuffers(bufferOld, bufferNew, 0, 100);
    }

    @Test
    public void testBoundaryInsertions() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Initial data
        int[] initial = {10, 20, 30, 40, 50};
        generateHistoryBuffer(initial, bufferOld, bufferNew, false);

        // Boundary insertions
        int[] boundaries = {9, 51}; // Just outside existing range
        generateHistoryBuffer(boundaries, bufferOld, bufferNew, false);

        // Insert between every existing record
        int[] between = {15, 25, 35, 45};
        generateHistoryBuffer(between, bufferOld, bufferNew, false);

        verifyBuffers(bufferOld, bufferNew, 0, 55);
    }

    @Test
    public void testCompactionTrigger() {
        for (int iterations = 0; iterations < 1000; iterations++) {
            int count = 1024 + RANDOM.nextInt(1000);

            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            for (int i = 0; i < count; i++) {
                putToBuffers(i, bufferOld, bufferNew, false);
            }

            int parts = 1 + RANDOM.nextInt(20);
            int partCount = count / parts;
            for (int i = 0; i < parts; i++) {
                int start = i * partCount;
                for (int j = 0; j < partCount / 3; j++) {
                    putToBuffers(start + j, bufferOld, bufferNew, true);
                }
            }

            RecordBuffer buf = createRecordBuffer(partCount);
            RecordCursor cursor = buf.current();
            bufferOld.putRecord(partCount, cursor, false, QD_STATS, 0);
            bufferNew.putRecord(partCount, cursor, false, QD_STATS, 0);

            verifyBuffers(bufferOld, bufferNew, 0, count);
        }
    }

    @Test
    public void testEmptyBufferRemoveOldRecords() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Call removeOldRecords on empty buffer
        bufferOld.removeOldRecords(100, QD_STATS, 0);
        bufferNew.removeOldRecords(100, QD_STATS, 0);

        // Verify empty state is maintained
        Assert.assertEquals(0, bufferOld.size());
        Assert.assertEquals(0, bufferNew.size());
        verifyBuffers(bufferOld, bufferNew, 0, 100);
    }

    @Test
    public void testFullBufferRemoveOldRecords() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // fill buffers
        int[] sequence = IntStream.range(0, 2048).toArray();
        generateHistoryBuffer(sequence, bufferOld, bufferNew, false);

        // remove all
        bufferOld.removeOldRecords(2049, QD_STATS, 0);
        bufferNew.removeOldRecords(2049, QD_STATS, 0);

        // Verify empty buffer
        Assert.assertEquals(0, bufferOld.size());
        Assert.assertEquals(0, bufferNew.size());
        verifyBuffers(bufferOld, bufferNew, 0, 2049);
    }

    @Test
    public void testRemoveOldRecords() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            QDStats statsOld = new QDStats(QDStats.SType.ANY);
            QDStats statsNew = new QDStats(QDStats.SType.ANY);

            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            // just only to pass validation
            setFieldValue(bufferOld, "everSnapshotTime", Long.MIN_VALUE);
            setFieldValue(bufferNew, "everSnapshotTime", Long.MIN_VALUE);

            // Fill buffer with random data
            int[] elements = RANDOM.ints(3000).distinct().toArray();
            generateHistoryBuffer(elements, bufferOld, bufferNew, false);

            // remove random elements
            for (int i : elements) {
                if (RANDOM.nextBoolean()) {
                    putToBuffers(i, bufferOld, bufferNew, true);
                }
            }

            Arrays.sort(elements);
            int min = elements[0];
            int max = elements[elements.length - 1];
            int median = elements[elements.length / 2];

            // Verify initial state
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            // Remove approximate half
            bufferOld.removeOldRecords(median, statsOld, 0);
            bufferNew.removeOldRecords(median, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            // Verify that records with time < median are removed
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            Assert.assertTrue(bufferOld.getMinAvailableTime() >= median);
            Assert.assertTrue(bufferNew.getMinAvailableTime() >= median);
            verifyBuffers(bufferOld, bufferNew, median, max);

            // Remove all records
            bufferOld.removeOldRecords(max + 1, statsOld, 0);
            bufferNew.removeOldRecords(max + 1, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            // Verify empty buffer
            Assert.assertEquals(0, bufferOld.size());
            Assert.assertEquals(0, bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);
        }
    }

    @Test
    public void testEmptyBufferEnforceMaxRecordCount() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Call enforceMaxRecordCount on empty buffer
        bufferOld.enforceMaxRecordCount(50, QD_STATS, 0);
        bufferNew.enforceMaxRecordCount(50, QD_STATS, 0);

        // Verify empty state is maintained
        Assert.assertEquals(0, bufferOld.size());
        Assert.assertEquals(0, bufferNew.size());
        verifyBuffers(bufferOld, bufferNew, 0, 100);
    }

    @Test
    public void testFullBufferEnforceMaxRecordCount() {
        QDStats statsOld = new QDStats(QDStats.SType.ANY);
        QDStats statsNew = new QDStats(QDStats.SType.ANY);

        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        int[] sequence = IntStream.range(0, 2048).toArray();
        generateHistoryBuffer(sequence, bufferOld, bufferNew, false);

        // Call enforceMaxRecordCount on empty buffer
        bufferOld.enforceMaxRecordCount(0, statsOld, 0);
        bufferNew.enforceMaxRecordCount(0, statsNew, 0);

        Assert.assertEquals(
            statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

        // Verify empty state is maintained
        Assert.assertEquals(0, bufferOld.size());
        Assert.assertEquals(0, bufferNew.size());
        verifyBuffers(bufferOld, bufferNew, 0, 2048);
    }

    @Test
    public void testRandomizedEnforceMaxRecordCount() {
        for (int iteration = 0; iteration < 100; iteration++) {
            QDStats statsOld = new QDStats(QDStats.SType.ANY);
            QDStats statsNew = new QDStats(QDStats.SType.ANY);

            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            // Fill buffer with random data
            int[] elements = RANDOM.ints(3000).distinct().toArray();
            generateHistoryBuffer(elements, bufferOld, bufferNew, false);

            // remove random elements
            for (int i : elements) {
                if (RANDOM.nextBoolean()) {
                    putToBuffers(i, bufferOld, bufferNew, true);
                }
            }

            Arrays.sort(elements);
            int min = elements[0];
            int max = elements[elements.length - 1];

            // Verify initial state
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            // Generate a random maxCount between 0 and the current count
            int maxCount = RANDOM.nextInt(bufferOld.size() + 1);

            // Enforce the max record count
            bufferOld.enforceMaxRecordCount(maxCount, statsOld, 0);
            bufferNew.enforceMaxRecordCount(maxCount, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            // Verify results random size
            Assert.assertEquals(maxCount, bufferOld.size());
            Assert.assertEquals(maxCount, bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            // max count 3
            bufferOld.enforceMaxRecordCount(3, statsOld, 0);
            bufferNew.enforceMaxRecordCount(3, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            // Verify results size 3
            maxCount = Math.min(3, bufferOld.size());
            Assert.assertEquals(maxCount, bufferOld.size());
            Assert.assertEquals(maxCount, bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            // Mx count 0
            bufferOld.enforceMaxRecordCount(0, statsOld, 0);
            bufferNew.enforceMaxRecordCount(0, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            // Verify empty buffers
            Assert.assertEquals(0, bufferOld.size());
            Assert.assertEquals(0, bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);
        }
    }

    @Test
    public void testFullRemoveSnapshotFromSnipTime() {
        QDStats statsOld = new QDStats(QDStats.SType.ANY);
        QDStats statsNew = new QDStats(QDStats.SType.ANY);

        RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
        RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        int[] sequence = IntStream.range(0, 100).toArray();
        generateHistoryBuffer(sequence, bufferOld, bufferNew, false);

        // Call removeSnapshotSweepBetween on full buffer
        bufferOld.snapshotSnipAndRemove(100, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
        bufferNew.snapshotSnipAndRemove(100, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

        Assert.assertEquals(
            statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

        compareRecordBuffers(removeBufferOld, removeBufferNew);
        verifyBuffers(bufferOld, bufferNew, 0, 100);
    }

    @Test
    public void testRandomRemoveSnapshotFromSnipTime() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            // just only to pass validation
            setFieldValue(bufferOld, "everSnapshotTime", Long.MIN_VALUE);
            setFieldValue(bufferNew, "everSnapshotTime", Long.MIN_VALUE);

            // Fill buffer with random data
            int[] elements = RANDOM.ints(3000).distinct().toArray();
            generateHistoryBuffer(elements, bufferOld, bufferNew, false);

            // remove random elements
            for (int i : elements) {
                if (RANDOM.nextBoolean()) {
                    putToBuffers(i, bufferOld, bufferNew, true);
                }
            }

            Arrays.sort(elements);
            int min = elements[0];
            int max = elements[elements.length - 1];

            // Verify initial state
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            for (int i = 0; i < elements.length; i++) {
                if (i % 42 != 0) {
                    continue;
                }
                int time = elements[i];

                QDStats statsOld = new QDStats(QDStats.SType.ANY);
                QDStats statsNew = new QDStats(QDStats.SType.ANY);

                RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
                RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

                // Call removeSnapshotSweepBetween on full buffer
                bufferOld.snapshotSnipAndRemove(time, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
                bufferNew.snapshotSnipAndRemove(time, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

                Assert.assertEquals(
                    statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

                compareRecordBuffers(removeBufferOld, removeBufferNew);
                verifyBuffers(bufferOld, bufferNew, time, max + 1);
            }

            // remove all alive
            QDStats statsOld = new QDStats(QDStats.SType.ANY);
            QDStats statsNew = new QDStats(QDStats.SType.ANY);

            RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
            RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

            // Call removeSnapshotSweepBetween on full buffer
            bufferOld.snapshotSnipAndRemove(max + 1, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
            bufferNew.snapshotSnipAndRemove(max + 1, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

            Assert.assertEquals(
                statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

            Assert.assertEquals(0, bufferOld.size());
            Assert.assertEquals(0, bufferNew.size());
            compareRecordBuffers(removeBufferOld, removeBufferNew);
            verifyBuffers(bufferOld, bufferNew, 0, max + 1);
        }
    }

    @Test
    public void testFullRemoveUpdateSnapshotTimeAndSweepRemove() {
        QDStats statsOld = new QDStats(QDStats.SType.ANY);
        QDStats statsNew = new QDStats(QDStats.SType.ANY);

        RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
        RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        int[] sequence = IntStream.range(0, 100).toArray();
        generateHistoryBuffer(sequence, bufferOld, bufferNew, false);

        // Call updateSnapshotTimeAndSweepRemove with 0 time to trim
        bufferOld.updateSnapshotTimeAndSweepRemove(0, 0, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
        bufferNew.updateSnapshotTimeAndSweepRemove(0, 0, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

        Assert.assertEquals(
            statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

        compareRecordBuffers(removeBufferOld, removeBufferNew);
        verifyBuffers(bufferOld, bufferNew, 0, 100);
    }

    @Test
    public void testVariousTimeNoGapsUpdateSnapshotTimeAndSweepRemove() throws Exception {
        // Define arrays of initial snapshot times and test values
        long[] initialSnapshotTimes = {
            Long.MAX_VALUE,  // Initial never set value
            100,            // Standard value
            50,             // Lower value
            200             // Higher value
        };

        long[] updateTimes = {
            Long.MAX_VALUE,  // No update should occur when time == Long.MAX_VALUE
            250,            // Higher than all initial times
            150,            // Between initial times
            100,            // Equal to some initial times
            50,             // Equal to some initial times
            30,             // Equal to some initial snip times
            20,             // Below some initial snip times
            0,              // Very low value
            -10             // Negative value
        };

        long[] trimValues = {
            0,              // No trimming
            10,             // Low trim value
            40,             // Moderate trim value
            80,             // Higher trim value
            120,            // High trim value
            500             // Value above all other times
        };

        // Create dataset for testing
        int[] elements = IntStream.range(1, 201).toArray();

        // Test all combinations
        for (long initialSnapshotTime : initialSnapshotTimes) {
            for (long time : updateTimes) {
                for (long trimToTime : trimValues) {

                    QDStats statsOld = new QDStats(QDStats.SType.ANY);
                    QDStats statsNew = new QDStats(QDStats.SType.ANY);

                    RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
                    RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

                    HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
                    HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

                    setFieldValue(bufferOld, "everSnapshotTime", Long.MIN_VALUE);
                    setFieldValue(bufferNew, "everSnapshotTime", Long.MIN_VALUE);

                    generateHistoryBuffer(elements, bufferOld, bufferNew, false);

                    setFieldValue(bufferOld, "snapshotTime", initialSnapshotTime);
                    setFieldValue(bufferNew, "snapshotTime", initialSnapshotTime);

                    bufferOld.updateSnapshotTimeAndSweepRemove(time, trimToTime, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
                    bufferNew.updateSnapshotTimeAndSweepRemove(time, trimToTime, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

                    Assert.assertEquals(
                        statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

                    compareRecordBuffers(removeBufferOld, removeBufferNew);
                    verifyBuffers(bufferOld, bufferNew, 1, 201);
                }
            }
        }
    }

    @Test
    public void testRandomUpdateSnapshotTimeAndSweepRemove() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            // just only to pass validation
            setFieldValue(bufferOld, "everSnapshotTime", Long.MIN_VALUE);
            setFieldValue(bufferNew, "everSnapshotTime", Long.MIN_VALUE);

            // Fill buffer with random data
            int[] elements = RANDOM.ints(3000).distinct().toArray();
            generateHistoryBuffer(elements, bufferOld, bufferNew, false);

            // remove random elements
            for (int i : elements) {
                if (RANDOM.nextBoolean()) {
                    putToBuffers(i, bufferOld, bufferNew, true);
                }
            }

            Arrays.sort(elements);
            int min = elements[0];
            int max = elements[elements.length - 1];

            // Verify initial state
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            for (int i = 0; i < 1000; i++) {
                long initialSnapshotTime = RANDOM.nextInt(max + 1);
                long time = RANDOM.nextInt((int) initialSnapshotTime + 1);
                long trimToTime = RANDOM.nextInt((int) initialSnapshotTime + 1);

                QDStats statsOld = new QDStats(QDStats.SType.ANY);
                QDStats statsNew = new QDStats(QDStats.SType.ANY);

                RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
                RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

                setFieldValue(bufferOld, "snapshotTime", initialSnapshotTime);
                setFieldValue(bufferNew, "snapshotTime", initialSnapshotTime);

                // Call updateSnapshotTimeAndSweepRemove with 0 time to trim
                bufferOld.updateSnapshotTimeAndSweepRemove(time, trimToTime, RECORD, 1, "TEST", removeBufferOld, statsOld, 0);
                bufferNew.updateSnapshotTimeAndSweepRemove(time, trimToTime, RECORD, 1, "TEST", removeBufferNew, statsNew, 0);

                Assert.assertEquals(
                    statsOld.getValue(QDStats.SValue.RID_REMOVED), statsNew.getValue(QDStats.SValue.RID_REMOVED));

                compareRecordBuffers(removeBufferOld, removeBufferNew);
                verifyBuffers(bufferOld, bufferNew, time, max + 1);
            }
        }
    }

    @Test
    public void testVariousRangesExamineDataRange() throws Exception {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        // Fill buffer with data - wide range with gaps
        int[] elements = IntStream.range(1, 101).toArray();
        generateHistoryBuffer(elements, bufferOld, bufferNew, false);

        // remove all elements except 10, 20 .... 100
        for (int i : elements) {
            if (i % 10 != 0) {
                putToBuffers(i, bufferOld, bufferNew, true);
            }
        }

        // Verify initial state
        Assert.assertEquals(bufferOld.size(), bufferNew.size());

        long[] everSnapshotTimeArray = LongStream
            .concat(LongStream.rangeClosed(0, 101), LongStream.of(Long.MIN_VALUE, Long.MIN_VALUE))
            .toArray();

        for (boolean snapshotFlag : new boolean[] {false, true}) {
            if (snapshotFlag) {
                setFieldValue(bufferOld, "flags", (byte) 0x04);
                setFieldValue(bufferNew, "flags", (byte) 0x04);
            }

            for (long everSnapshotTime : everSnapshotTimeArray) {
                setFieldValue(bufferOld, "everSnapshotTime", everSnapshotTime);
                setFieldValue(bufferNew, "everSnapshotTime", everSnapshotTime);

                // Test different ranges and verify results
                verifyBuffers(bufferOld, bufferNew, 5, 15);    // Partial start
                verifyBuffers(bufferOld, bufferNew, 15, 25);   // Middle range
                verifyBuffers(bufferOld, bufferNew, 95, 105);  // Partial end
                verifyBuffers(bufferOld, bufferNew, 1, 110);   // Entire range and beyond
                verifyBuffers(bufferOld, bufferNew, 35, 65);   // Middle range with multiple records
                verifyBuffers(bufferOld, bufferNew, 45, 45);   // Single point (should not match any)
                verifyBuffers(bufferOld, bufferNew, 50, 50);   // Single point (exact match)
                verifyBuffers(bufferOld, bufferNew, 110, 120); // Range after all data
                verifyBuffers(bufferOld, bufferNew, 100, 110); // Range after all data with equal border
                verifyBuffers(bufferOld, bufferNew, 0, 5);     // Range before all data
                verifyBuffers(bufferOld, bufferNew, 0, 10);    // Range before all data with equal border
                verifyBuffers(bufferOld, bufferNew, 10, 80);   // Range from direct times
                verifyBuffers(bufferOld, bufferNew, 20, 75);   // Range from direct left border
                verifyBuffers(bufferOld, bufferNew, 25, 70);   // Range from direct right border
                verifyBuffers(bufferOld, bufferNew, 15, 95);   // large range
            }
        }
    }

    @Test
    public void testExamineDataRetrieve() {
        for (int iteration = 0; iteration < 1000; iteration++) {
            HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
            HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

            // Fill buffer with random data
            int[] elements = RANDOM.ints(1100).distinct().toArray();
            generateHistoryBuffer(elements, bufferOld, bufferNew, false);

            // remove random elements
            for (int i : elements) {
                if (RANDOM.nextBoolean()) {
                    putToBuffers(i, bufferOld, bufferNew, true);
                }
            }

            Arrays.sort(elements);
            int min = elements[0];
            int max = elements[elements.length - 1];

            // Verify initial state
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            verifyBuffers(bufferOld, bufferNew, min, max + 1);

            // random generated params
            long timeKnown = RANDOM.nextInt(max + 1);
            long toTime = RANDOM.nextInt((int) timeKnown + 1);
            int limit = RANDOM.nextInt(max + 1);
            int snapshotEndFlag = RANDOM.nextBoolean() ? History.SNAPSHOT_END : History.SNAPSHOT_SNIP;
            boolean txEnd = RANDOM.nextBoolean();
            boolean useFlags = RANDOM.nextBoolean();

            RecordBuffer removeBufferOld = RecordBuffer.getInstance(RecordMode.DATA);
            RecordBuffer removeBufferNew = RecordBuffer.getInstance(RecordMode.DATA);

            // Call updateSnapshotTimeAndSweepRemove with 0 time to trim
            bufferOld.examineDataRetrieve(RECORD, 1, "TEST", timeKnown, toTime, removeBufferOld,
                new RecordCursorKeeperOld(), null, limit, History.SNAPSHOT_BEGIN, snapshotEndFlag, txEnd, useFlags);
            bufferNew.examineDataRetrieve(RECORD, 1, "TEST", timeKnown, toTime, removeBufferNew,
                new RecordCursorKeeper(), null, limit, History.SNAPSHOT_BEGIN, snapshotEndFlag, txEnd, useFlags);

            compareRecordBuffers(removeBufferOld, removeBufferNew);
            verifyBuffers(bufferOld, bufferNew, min, max + 1);
        }
    }

    @Test
    public void testGapOverflow() {
        HistoryBufferOld bufferOld = new HistoryBufferOld(RECORD, false);
        HistoryBuffer bufferNew = new HistoryBuffer(RECORD, false);

        long left = Long.MIN_VALUE + 1000_000;
        long right = Long.MAX_VALUE - 1000_000;
        for (int i = 0; i < 1000; i++) {
            putToBuffers(left - i, bufferOld, bufferNew, false);
            putToBuffers(right + i, bufferOld, bufferNew, false);
        }
        verifyBuffers(bufferOld, bufferNew, Long.MIN_VALUE, Long.MAX_VALUE);

        putToBuffers(right / 2, bufferOld, bufferNew, false);
        verifyBuffers(bufferOld, bufferNew, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    // Helper method to insert
    private void generateHistoryBuffer(int[] ids, HistoryBufferOld bufferOld, HistoryBuffer bufferNew, boolean remove) {
        for (int i : ids) {
            putToBuffers(i, bufferOld, bufferNew, remove);
            Assert.assertEquals(bufferOld.size(), bufferNew.size());
            Assert.assertEquals(bufferOld.getMaxAvailableTime(), bufferNew.getMaxAvailableTime());
            Assert.assertEquals(bufferOld.getMinAvailableTime(), bufferNew.getMinAvailableTime());
        }
    }

    private void putToBuffers(long i, HistoryBufferOld bufferOld, HistoryBuffer bufferNew, boolean remove) {
        RecordBuffer recordBuffer = createRecordBuffer(i);
        RecordCursor cursor = recordBuffer.current();

        bufferOld.putRecord(i, cursor, remove, QD_STATS, 0);
        bufferNew.putRecord(i, cursor, remove, QD_STATS, 0);
    }

    // Helper method for buffer verification
    private void verifyBuffers(HistoryBufferOld bufferOld, HistoryBuffer bufferNew, long startTime, long endTime) {
        bufferNew.selfValidate();
        // Verify the LTR direction
        RecordBuffer sinkOld = RecordBuffer.getInstance(RecordMode.DATA);
        RecordBuffer sinkNew = RecordBuffer.getInstance(RecordMode.DATA);

        boolean resultOld = bufferOld.examineDataRangeLTR(RECORD, 0, "TEST_", startTime, endTime,
            sinkOld, new RecordCursorKeeperOld(), null);
        boolean resultNew = bufferNew.examineDataRangeLTR(RECORD, 0, "TEST_", startTime, endTime,
            sinkNew, new RecordCursorKeeper(), null);

        Assert.assertEquals(resultOld, resultNew);
        compareBuffer(bufferOld, bufferNew, sinkOld, sinkNew, startTime, endTime);

        // Verify the RTL direction
        sinkOld = RecordBuffer.getInstance(RecordMode.DATA);
        sinkNew = RecordBuffer.getInstance(RecordMode.DATA);

        resultOld = bufferOld.examineDataRangeRTL(RECORD, 0, "TEST_", endTime, startTime,
            sinkOld, new RecordCursorKeeperOld(), null);
        resultNew = bufferNew.examineDataRangeRTL(RECORD, 0, "TEST_", endTime, startTime,
            sinkNew, new RecordCursorKeeper(), null);

        Assert.assertEquals(resultOld, resultNew);
        compareBuffer(bufferOld, bufferNew, sinkOld, sinkNew, startTime, endTime);
    }

    private void compareBuffer(HistoryBufferOld firstHistoryBuffer, HistoryBuffer secondHistoryBuffer,
                               RecordBuffer firstRecordBuffer, RecordBuffer secondRecordBuffer, long startTime, long endTime)
    {
        Assert.assertEquals(firstHistoryBuffer.size(), secondHistoryBuffer.size());
        Assert.assertEquals(firstHistoryBuffer.getMaxAvailableTime(), secondHistoryBuffer.getMaxAvailableTime());
        Assert.assertEquals(firstHistoryBuffer.getMinAvailableTime(), secondHistoryBuffer.getMinAvailableTime());
        Assert.assertEquals(
            firstHistoryBuffer.getAvailableCount(startTime, endTime),
            secondHistoryBuffer.getAvailableCount(startTime, endTime));
        Assert.assertEquals(
            firstHistoryBuffer.getAvailableCount((startTime * 3 + endTime) / 4, (startTime + 3 * endTime) / 4),
            secondHistoryBuffer.getAvailableCount((startTime * 3 + endTime) / 4, (startTime + 3 * endTime) / 4));

        compareRecordBuffers(firstRecordBuffer, secondRecordBuffer);
    }

    private void compareRecordBuffers(RecordBuffer firstRecordBuffer, RecordBuffer secondRecordBuffer) {
        firstRecordBuffer.rewind();
        secondRecordBuffer.rewind();

        Assert.assertEquals(firstRecordBuffer.size(), secondRecordBuffer.size());

        while (firstRecordBuffer.hasNext() && secondRecordBuffer.hasNext()) {
            RecordCursor cursorOld = firstRecordBuffer.next();
            RecordCursor cursorNew = secondRecordBuffer.next();

            Assert.assertNotNull(cursorOld);
            Assert.assertNotNull(cursorNew);

            Assert.assertEquals(cursorOld.getIntCount(), cursorNew.getIntCount());
            Assert.assertEquals(cursorOld.getObjCount(), cursorNew.getObjCount());
            Assert.assertEquals(cursorOld.getCipher(), cursorNew.getCipher());
            Assert.assertEquals(cursorOld.getSymbol(), cursorNew.getSymbol());

            for (int i = 0; i < cursorOld.getIntCount(); i++) {
                Assert.assertEquals(cursorOld.getInt(i), cursorNew.getInt(i));
            }
            for (int i = 0; i < cursorNew.getObjCount(); i++) {
                Assert.assertEquals(cursorOld.getObj(i), cursorNew.getObj(i));
            }
        }
    }

    private RecordBuffer createRecordBuffer(long i) {
        RecordBuffer buf = new RecordBuffer(RecordMode.TIMESTAMPED_DATA.withEventTimeSequence());
        RecordCursor cur = buf.add(RECORD, 0, "Test");
        cur.setEventTimeSequence(i);
        for (int j = 0; j < RECORD.getIntFieldCount();) {
            if (RECORD.getIntField(j).getSerialType().isLong()) {
                cur.setLong(j, i);
                j += 2;
            } else {
                cur.setInt(j++, iVal(i, j));
            }
        }
        for (int j = 0; j < RECORD.getObjFieldCount(); j++) {
            cur.setObj(j, oVal(i, j));
        }
        return buf;
    }

    private int iVal(long i, int j) {
        return (int) (100000 * i + j);
    }

    private Object oVal(long i, long j) {
        return i + "," + j;
    }

    public static void setFieldValue(Object object, String fieldName, Object valueTobeSet) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, valueTobeSet);
    }
}
