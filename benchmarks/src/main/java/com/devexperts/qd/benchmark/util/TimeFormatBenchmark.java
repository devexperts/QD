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
package com.devexperts.qd.benchmark.util;

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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class TimeFormatBenchmark {

    public static final String PRODUCTION_IMPL = "*production*";

    public static final int ITERATIONS = 10_000;

    public final Random random = new Random();

    @Param({
        TimeFormat.NO_CACHE,
        TimeFormat.CACHE_SHARED_BIN_MASK,
        TimeFormat.CACHE_SHARED_REMAINDER,
        TimeFormat.CACHE_SHARED_ARRAY_MASK,
        TimeFormat.CACHE_MILLIS_AND_MINUTES,
        PRODUCTION_IMPL
    })
    public String cacheMode;

    public LongFunction<String> formatter;

    @Setup
    public void setup() {
        if (PRODUCTION_IMPL.equals(cacheMode)) {
            com.devexperts.util.TimeFormat tf = com.devexperts.util.TimeFormat.DEFAULT.withMillis().withTimeZone();
            formatter = tf::format;
        } else {
            System.setProperty("com.devexperts.util.timeformat.cache", cacheMode);
            TimeFormat tf = TimeFormat.DEFAULT.withMillis().withTimeZone();
            formatter = tf::format;
        }
    }

    /**
     * Random times on a wide range
     */
    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void randomTimes(Blackhole blackhole) {
        LongFunction<String> formatter = this.formatter;
        Random rnd = random;

        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            long time = baseTime + rnd.nextLong() % (TimeUnit.DAYS.toMillis(1000));
            blackhole.consume(formatter.apply(time));
        }
    }

    /**
     * Relatively near times with forward progression
     */
    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void nearTimes(Blackhole blackhole) {
        LongFunction<String> formatter = this.formatter;
        Random rnd = random;

        long time = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            time += rnd.nextInt(500) - 100;
            blackhole.consume(formatter.apply(time));
        }
    }

    /**
     * Single time
     */
    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void singleTime(Blackhole blackhole) {
        LongFunction<String> formatter = this.formatter;
        Random rnd = random;

        long time = rnd.nextLong() >> 24;
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(formatter.apply(time));
        }
    }

    /**
     * Progressing times with small batches of repeating values
     */
    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void seqBatches(Blackhole blackhole) {
        LongFunction<String> formatter = this.formatter;
        Random rnd = random;

        long time = System.currentTimeMillis();
        for (int i = 0, batchSize = 0; i < ITERATIONS; i++, batchSize--) {
            if (batchSize == 0) {
                batchSize = 1 + rnd.nextInt(20);
                time += rnd.nextInt(500) - 100;
            }
            blackhole.consume(formatter.apply(time));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(TimeFormatBenchmark.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}
