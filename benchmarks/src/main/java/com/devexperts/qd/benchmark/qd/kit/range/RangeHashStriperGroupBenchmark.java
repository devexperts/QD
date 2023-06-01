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
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.HashStriper;
import com.devexperts.qd.kit.RangeStriper;
import com.dxfeed.api.impl.DXFeedScheme;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark that runs stripers on the group of 10000 profiles for different symbol groups
 * (bs, fut, futopt, opt, spread) that are available on the classpath.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeHashStriperGroupBenchmark {

    public static final int ITERATIONS = 10_000;

    public static int [] ciphers;
    public static String[] symbols;
    public static final DataScheme scheme = DXFeedScheme.getInstance();

    @Param({
        "bs",
        "fut",
        "futopt",
        "opt",
        "spread",
    })
    public String group;

    public SymbolStriper hash;
    public SymbolStriper range;

    @Setup
    public void setup() {
        InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/" + group + ".csv"));
        String[] symbols1 = new BufferedReader(new InputStreamReader(in)).lines().toArray(String[]::new);
        if (symbols1.length != ITERATIONS) {
            System.exit(-1);
        }

        ciphers = new int[ITERATIONS];
        symbols = new String[ITERATIONS];

        SymbolCodec codec = scheme.getCodec();
        for (int i = 0; i < ITERATIONS; i++) {
            symbols[i] = symbols1[i];
            ciphers[i] = codec.encode(symbols[i]);
        }

        hash = HashStriper.valueOf(scheme, "byhash4");
        range = RangeStriper.valueOf(scheme, "byrange-G-M-S-");
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeFilterGroup(Blackhole blackhole) {
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(range.getStripeIndex(ciphers[i], ciphers[i] == 0 ? symbols[i] : null));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void hashFilterGroup(Blackhole blackhole) {
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(hash.getStripeIndex(ciphers[i], ciphers[i] == 0 ? symbols[i] : null));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeHashStriperGroupBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
