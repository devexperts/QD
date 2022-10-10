/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.RangeStriper;
import com.devexperts.qd.SymbolStriper;
import com.dxfeed.api.impl.DXFeedScheme;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark that runs stripers on predefined single symbols.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeStriperSingleBenchmark {

    public static final DataScheme scheme = DXFeedScheme.getInstance();

    @Param({
        "byrange_A_",
        "byrange_M_",
        "byrange_U_",
        "byrange_BA_CE_",
        "byrange_MA_NZ_",
        "byrange_CE_NZ_",
        "byrange_SPSN22_SPSN23_",
        "byrange_SPX22070_SPX22079_",
    })
    public String striperSpec;

    @Param({
        "standard",
        "another",
    })
    public String striperImpl;

    @Param({
        "AAAA",
        "=1.1*AAAA",
        "MMMM",
        "VVVVV",
        "MMMMMMMMMMM",
        "SPX22070111",
    })
    public String symbol;

    public SymbolStriper striper;

    @Setup
    public void setup() {
        if (striperImpl.equals("standard")) {
            striper = RangeStriper.valueOf(scheme, striperSpec);
        } else {
            striper = LambdaBasedRangeStriper.valueOf(scheme, striperSpec);
        }
    }

    @Benchmark
    public void rangeFilterUniverse(Blackhole blackhole) {
        blackhole.consume(striper.getStripeIndex(0, symbol));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeStriperSingleBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
