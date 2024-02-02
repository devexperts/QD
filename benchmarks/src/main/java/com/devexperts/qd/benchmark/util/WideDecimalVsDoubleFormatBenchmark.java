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
package com.devexperts.qd.benchmark.util;

import com.devexperts.util.WideDecimal;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(1)
@State(Scope.Thread)
public class WideDecimalVsDoubleFormatBenchmark {

    public static final int BLOCK_SIZE = 10_000;
    public double[] values;
    private StringBuilder sb;

    @Param({"1", "100"})
    public int delim;

    @Param({"100000", "1000000000000"})
    public long bound;

    @Setup
    public void setup() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        values = new double[BLOCK_SIZE];
        for (int i = 0; i < values.length; i++) {
            values[i] = ((double) rnd.nextLong(bound)) / delim;
        }
        sb = new StringBuilder(30);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public int doubleToString() {
        int r = 0;
        StringBuilder sb = this.sb;
        double[] values = this.values;
        for (double d : values) {
            sb.append(d);
            r += sb.length();
            sb.setLength(0);
        }
        return r;
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public int wideDecimal() {
        int r = 0;
        StringBuilder sb = this.sb;
        double[] values = this.values;
        for (double d : values) {
            WideDecimal.appendTo(sb, WideDecimal.composeWide(d));
            r += sb.length();
            sb.setLength(0);
        }
        return r;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(WideDecimalVsDoubleFormatBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
