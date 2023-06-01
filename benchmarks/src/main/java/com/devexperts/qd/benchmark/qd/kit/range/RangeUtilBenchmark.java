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

import com.devexperts.qd.kit.RangeFilter;
import com.devexperts.qd.kit.RangeUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Benchmark that runs stripers on predefined single symbols.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeUtilBenchmark {

    @Param({
        ".A",
        "./A",
        "?^%#$^@A",
        "=-A",
        "=2*A",
        "=+2.*A",
        "=0.2*A",
        "=-2.*1234",
        "=-2.2.2*IBM+MSFT",
        "=-2.5*",
        "=2.2.2*IBM+MSFT",
        "=+2IBM+2*MSFT",
        "=1234+2345",
    })
    public String symbol;

    public Matcher matcher = Pattern.compile(RangeFilter.SYMBOL_PATTERN).matcher("");

    @Benchmark
    public void rangeRegex(Blackhole blackhole) {
        matcher.reset(symbol);
        if (matcher.matches())
            blackhole.consume(matcher.start(1));
    }

    @Benchmark
    public void rangeUtil(Blackhole blackhole) {
        blackhole.consume(RangeUtil.skipPrefix(symbol, symbol.length()));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeUtilBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
