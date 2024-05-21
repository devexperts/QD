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

import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark that runs stripers through ALL available profiles in "securities.ipf.zip" available on the run path.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeStriperByFileBenchmark {

    // Securities IPF to read from (should contain only TYPE,SYMBOL)
    public static String SECURITIES_IPF = System.getProperty("securities.ipf", "securities.ipf.zip");
    // Number of profiles in the securities IPF
    public static final int ITERATIONS = 3_566_892;

    public static int [] ciphers;
    public static String[] symbols;

    @Param({
        "byrange-A-",
        "byrange-M-",
        "byrange-U-",
        "byrange-BA-CE-",
        "byrange-MA-NZ-",
        "byrange-CE-NZ-",
        "byrange-SPSN22-SPSN23-",
        "byrange-SPX22070-SPX22079-",
    })
    public String striperSpec;

    @Param({
        "standard",
    })
    public String striperImpl;

    public SymbolStriper striper;

    @Setup
    public void setup() {
        List<InstrumentProfile> profiles = Collections.emptyList();
        try {
            profiles = new InstrumentProfileReader().readFromFile(SECURITIES_IPF);
        } catch (IOException e) {
            System.err.println("Error reading instruments from " + SECURITIES_IPF);
            e.printStackTrace(System.err);
            System.exit(-1);
        }

        if (profiles.size() != ITERATIONS) {
            System.err.println("Set ITERATIONS const to " + profiles.size() + " and recompile/rerun test");
            System.exit(-1);
        }

        ciphers = new int[ITERATIONS];
        symbols = new String[ITERATIONS];

        SymbolCodec codec = BenchmarkRangeStriper.SCHEME.getCodec();
        for (int i = 0; i < ITERATIONS; i++) {
            symbols[i] = profiles.get(i).getSymbol();
            ciphers[i] = codec.encode(symbols[i]);
        }

        striper = BenchmarkRangeStriper.createRangeStriper(striperImpl, striperSpec);
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeFilterUniverse(Blackhole blackhole) {
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(striper.getStripeIndex(ciphers[i], ciphers[i] == 0 ? symbols[i] : null));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeStriperByFileBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
