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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms3g", "-Xmx3g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class HistoryBufferBenchmark {

    private static final QDStats QD_STATS = new QDStats(QDStats.SType.ANY);

    private static final DataRecord RECORD = new DefaultRecord(0, "Bench_Record", true,
        new DataIntField[] {
            new TimeMillisField(0, "Bench_Record.Time"),
            new CompactIntField(1, "Bench_Record.Int0"),
            new CompactIntField(2, "Bench_Record.Int1"),
            new CompactIntField(3, "Bench_Record.Int2"),
            new CompactIntField(4, "Bench_Record.Int3"),
            new CompactIntField(5, "Bench_Record.Int4")},
        new DataObjField[] {
            new MarshalledObjField(0, "Bench_Record.Obj0"),
            new MarshalledObjField(1, "Bench_Record.Obj1"),
            new MarshalledObjField(2, "Bench_Record.Obj2")}
    );

    private static RecordBuffer createRecordBuffer(int i, int chipper, String symbol) {
        RecordBuffer buf = new RecordBuffer(RecordMode.TIMESTAMPED_DATA.withEventTimeSequence());
        RecordCursor cur = buf.add(RECORD, chipper, symbol);
        cur.setEventTimeSequence(i);
        for (int j = 0; j < RECORD.getIntFieldCount();) {
            if (RECORD.getIntField(j).getSerialType().isLong()) {
                cur.setLong(j, i);
                j += 2;
            } else {
                cur.setInt(j++, 100000 * i + j);
            }
        }
        for (int j = 0; j < RECORD.getObjFieldCount(); j++) {
            cur.setObj(j, i + "," + j);
        }
        return buf;
    }

    @State(Scope.Thread)
    public static class GeneralState {
        @Param({"16", "512", "4096", "16384", "65536", "131072"})
        private int count;

        private HistoryBufferOld bufferOld;
        private HistoryBuffer bufferNew;

        private RecordCursor[] recordCursors;
        private List<Integer> insertionSequence;

        @Setup(Level.Invocation)
        public void setup() {
            bufferOld = new HistoryBufferOld(RECORD, false);
            bufferNew = new HistoryBuffer(RECORD, false);

            insertionSequence = new ArrayList<>();
            recordCursors = new RecordCursor[count];
            for (int i = 0; i < count; i++) {
                recordCursors[i] = createRecordBuffer(i, 1000 + i, "BENCH_" + i).current();
                insertionSequence.add(i);
            }
            Collections.shuffle(insertionSequence);

            System.gc();
        }
    }

    @State(Scope.Thread)
    public static class PredefinedState {
        @Param({"5", "30", "60"})
        private int percent;

        private HistoryBufferOld bufferOld;
        private HistoryBuffer bufferNew;

        private RecordCursor[] recordCursors;

        private int start;
        private int end;

        @Setup(Level.Invocation)
        public void setup(GeneralState state) {
            start = (state.count * percent / 200);
            end = state.count - Math.max(start, 1);

            bufferOld = state.bufferOld;
            bufferNew = state.bufferNew;

            recordCursors = state.recordCursors;

            for (int i = 0; i < start; i++) {
                bufferOld.putRecord(i, state.recordCursors[i], false, QD_STATS, 0);
                bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0);
            }

            for (int i = end; i < state.count; i++) {
                bufferOld.putRecord(i, recordCursors[i], false, QD_STATS, 0);
                bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0);
            }

            System.gc();
        }
    }

    @Benchmark
    public void simpleSequenceInsertOld(GeneralState state, Blackhole blackhole) {
        for (int i = 0; i < state.count; i++) {
            blackhole.consume(state.bufferOld.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void simpleSequenceInsertNew(GeneralState state, Blackhole blackhole) {
        for (int i = 0; i < state.count; i++) {
            blackhole.consume(state.bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void simpleDualInsertOld(GeneralState state, Blackhole blackhole) {
        int half = state.count / 2;
        for (int i = half, j = half + 1; i > 0 && j < state.count; i--, j++) {
            blackhole.consume(state.bufferOld.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
            blackhole.consume(state.bufferOld.putRecord(j, state.recordCursors[j], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void simpleDualInsertNew(GeneralState state, Blackhole blackhole) {
        int half = state.count / 2;
        for (int i = half, j = half + 1; i > 0 && j < state.count; i--, j++) {
            blackhole.consume(state.bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
            blackhole.consume(state.bufferNew.putRecord(j, state.recordCursors[j], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void largeSequentialMiddleInsertOld(PredefinedState state, Blackhole blackhole) {
        // Insert sequential chunk in the middle
        for (int i = state.start; i < state.end; i++) {
            blackhole.consume(state.bufferOld.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void largeSequentialMiddleInsertNew(PredefinedState state, Blackhole blackhole) {
        // Insert sequential chunk in the middle
        for (int i = state.start; i < state.end; i++) {
            blackhole.consume(state.bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void insertRandomNew(GeneralState state, Blackhole blackhole) {
        for (Integer i : state.insertionSequence) {
            blackhole.consume(state.bufferNew.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    @Benchmark
    public void insertRandomOld(GeneralState state, Blackhole blackhole) {
        for (Integer i : state.insertionSequence) {
            blackhole.consume(state.bufferOld.putRecord(i, state.recordCursors[i], false, QD_STATS, 0));
        }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(HistoryBufferBenchmark.class.getSimpleName()).build()).run();
    }
}
