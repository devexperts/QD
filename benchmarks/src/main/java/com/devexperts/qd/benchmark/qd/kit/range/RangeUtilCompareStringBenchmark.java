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

import com.devexperts.qd.kit.RangeUtil;
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
 * Benchmark that runs stripers on predefined single symbols.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeUtilCompareStringBenchmark {

    public static final int ITERATIONS = 10_000;

    @Param({
        "AWS",
        "HXEU22P4.04:XCEC",
        "OSX",
        "UHUG24C",
    })
    public String symbol;

    public char[] symbolChars;
    public String[] futopt;

    @Setup
    public void setup() {
        symbolChars = symbol.toCharArray();

        InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/futopt.csv"));
        futopt = new BufferedReader(new InputStreamReader(in)).lines().toArray(String[]::new);
        if (futopt.length != ITERATIONS) {
            System.exit(-1);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeDoNothing(Blackhole blackhole) {
        for (String s : futopt) {
            blackhole.consume(s);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeDefault(Blackhole blackhole) {
        for (String s : futopt) {
            blackhole.consume(RangeUtil.compareByString(symbolChars, s, 2));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeCustom(Blackhole blackhole) {
        for (String s : futopt) {
            blackhole.consume(compareByString(symbolChars, s, 2));
        }
    }

    public static int compareByString(char[] s1, String s2, int s2from) {
        int len1 = s1.length;
        int len2 = s2.length() - s2from;
        int lim = Math.min(len1, len2);
        for (int i = 0, j = s2from; i < lim; i++, j++) {
            char c1 = s1[i];
            char c2 = s2.charAt(j);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return len1 - len2;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeUtilCompareStringBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
