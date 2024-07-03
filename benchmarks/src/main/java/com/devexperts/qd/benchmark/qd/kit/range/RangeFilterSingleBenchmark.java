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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.RangeFilter;
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
 * Benchmark that runs filters on predefined single symbols.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeFilterSingleBenchmark {

    public static final DataScheme scheme = DXFeedScheme.getInstance();

    @Param({
        "range---",
        "range-A-B-",
        "range-U-Z-",
        "range-BA-CE-",
        "range-MA-NZ-",
        "range-SPSN22-SPSN23-",
        "range-SPX22070-SPX22079-",
    })
    public String filterSpec;

    @Param({
        "standard",
        "another",
    })
    public String filterImpl;

    @Param({
        "AAAA",
        "=1.1*AAAA",
        "MMMM",
        "VVVVV",
        "MMMMMMMMMMM",
        "SPX22070111",
    })
    public String symbol;

    public QDFilter filter;

    @Setup
    public void setup() {
        if (filterImpl.equals("standard")) {
            filter = RangeFilter.valueOf(scheme, filterSpec);
        } else {
            filter = LongCodePrefixRangeFilter.valueOf(scheme, filterSpec);
        }
    }

    @Benchmark
    public void rangeFilterSingle(Blackhole blackhole) {
        // Note that cipher is not calculated here - only string branch is used
        blackhole.consume(filter.accept(null, null, 0, symbol));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeFilterSingleBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
