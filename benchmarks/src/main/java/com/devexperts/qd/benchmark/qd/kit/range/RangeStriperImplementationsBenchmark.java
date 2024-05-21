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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.SymbolStriper;
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

/**
 * Benchmark that runs different {@link com.devexperts.qd.kit.RangeStriper} implementations.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeStriperImplementationsBenchmark {

    public static final int ITERATIONS = 10_000;

    public static String[] symbols;

    @Param({
        "standard",
        "array",
        "string",
        "lambda",
        "capture",
    })
    public String striperImpl;

    @Param({
        "byrange-BA-CE-",
    })
    public String striperSpec;

    @Param({
        "=1.1*AAAA",
    })
    public String symbol;
    public char[] symbolArr;

    public SymbolStriper striper;
    // Another lambda for lambda-based stripers, otherwise dummy == striper
    public SymbolStriper dummy;

    public int[] stripeChoice;

    @Setup
    public void setup() {
        symbolArr = symbol.toCharArray();

        striper = BenchmarkRangeStriper.createRangeStriper(striperImpl, striperSpec);
        if (striperImpl.equals("lambda")) {
            dummy = BenchmarkRangeStriper.createRangeStriper("lambda2", striperSpec);
        } else if (striperImpl.equals("capture")) {
            dummy = BenchmarkRangeStriper.createRangeStriper("capture2", striperSpec);
        } else {
            dummy = striper;
        }

        Random rnd = new Random(100);
        stripeChoice = new int[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            stripeChoice[i] = rnd.nextInt(3);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void testStriper(Blackhole blackhole) {
        for (int i = 0; i < ITERATIONS; i++) {
            // To confuse HotSpot calls are made on:
            // - strings
            // - char arrays
            // - for lambda-based stripers another lambda implementations are called
            if (stripeChoice[i] == 0) {
                blackhole.consume(striper.getStripeIndex(symbol));
            } else if (stripeChoice[i] == 1) {
                blackhole.consume(striper.getStripeIndex(symbolArr, 0, symbolArr.length));
            } else {
                blackhole.consume(dummy.getStripeIndex(symbol));
            }
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeStriperImplementationsBenchmark.class.getSimpleName())
            .addProfiler("gc")
            //.warmupMode(WarmupMode.BULK)
            .build();
        new Runner(opt).run();
    }
}
