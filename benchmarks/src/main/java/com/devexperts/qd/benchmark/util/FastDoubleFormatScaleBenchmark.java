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

import com.devexperts.util.MathUtil;
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
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

// Benchmark results: Intel(R) Xeon(R) Gold 6354 CPU @ 3.00GHz, VM: JDK 21, OpenJDK 64-Bit Server VM, 21+35-LTS
//    Benchmark                                          (integer)  (precision)  Mode  Cnt     Score     Error   Units
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2         true            1  avgt    5    90.851 ±   0.078   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2         true            3  avgt    5    93.529 ±   0.133   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2         true            9  avgt    5    74.012 ±   0.127   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2         true           16  avgt    5    60.168 ±   0.076   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2        false            1  avgt    5    79.392 ±   0.096   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2        false            3  avgt    5   101.514 ±   0.112   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2        false            9  avgt    5    73.094 ±   0.059   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale2        false           16  avgt    5    50.930 ±   0.089   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale20         N/A            3  avgt    5   113.571 ±   0.108   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale20         N/A            7  avgt    5   101.471 ±   0.061   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale20         N/A            9  avgt    5    93.868 ±   0.034   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9         true            1  avgt    5    90.405 ±   0.214   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9         true            3  avgt    5    89.728 ±   0.164   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9         true            9  avgt    5    79.284 ±   0.061   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9         true           16  avgt    5    71.560 ±   0.129   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9        false            1  avgt    5    79.180 ±   0.134   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9        false            3  avgt    5    99.610 ±   0.065   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9        false            9  avgt    5    76.662 ±   0.079   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScale9        false           16  avgt    5    61.256 ±   0.081   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9   true            1  avgt    5    98.821 ±   2.611   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9   true            3  avgt    5    92.263 ±   3.598   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9   true            9  avgt    5    81.883 ±   2.327   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9   true           16  avgt    5    73.013 ±   2.372   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9  false            1  avgt    5    86.706 ±   3.329   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9  false            3  avgt    5   105.687 ±   4.018   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9  false            9  avgt    5    85.984 ±   3.337   ns/op
//    FastDoubleFormatScaleBenchmark.fastDoubleScaleString9  false           16  avgt    5    69.321 ±   2.633   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2          true            1  avgt    5   101.296 ±   4.686   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2          true            3  avgt    5   105.454 ±   5.968   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2          true            9  avgt    5   118.064 ±   6.187   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2          true           16  avgt    5   122.953 ±   6.558   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2         false            1  avgt    5    81.300 ±   2.514   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2         false            3  avgt    5    93.810 ±   3.284   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2         false            9  avgt    5   103.802 ±   5.588   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale2         false           16  avgt    5   117.659 ±   5.047   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale20          N/A            3  avgt    5   102.968 ±   4.301   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale20          N/A            7  avgt    5   113.474 ±   5.408   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale20          N/A            9  avgt    5   114.153 ±   5.836   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9          true            1  avgt    5   118.210 ±   4.869   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9          true            3  avgt    5   119.634 ±   4.100   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9          true            9  avgt    5   125.893 ±   6.612   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9          true           16  avgt    5   136.705 ±   4.079   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9         false            1  avgt    5    97.575 ±   3.589   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9         false            3  avgt    5   109.622 ±   4.001   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9         false            9  avgt    5   111.991 ±   3.901   ns/op
//    FastDoubleFormatScaleBenchmark.jdkDoubleScale9         false           16  avgt    5   141.235 ±   6.168   ns/op
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FastDoubleFormatScaleBenchmark {

    private static final int BLOCK_SIZE = 100;

    @State(Scope.Benchmark)
    public static class EngineeringNumbers {

        @Param({"1", "3", "9", "16"})
        private int precision;

        @Param({"true", "false"})
        private boolean integer;

        private double[] values;

        @Setup
        public void setup() {
            Random rnd = new Random(0);
            List<String> stringDoubles = (integer ?
                filterStream(rnd.doubles(1, 1000), rnd, precision) :
                filterStream(rnd.doubles(0, 1), rnd, precision)).collect(Collectors.toList());

            Collections.shuffle(stringDoubles, rnd);

            values = stringDoubles.stream()
                .mapToDouble(Double::parseDouble)
                .toArray();
        }
    }

    @State(Scope.Benchmark)
    public static class ScientificNumbers {

        @Param({"3", "7", "9"})
        private int precision;

        private double[] values;

        @Setup
        public void setup() {
            Random rnd = new Random(0);
            List<String> stringDoubles =
                filterStream(rnd.doubles(0, 1), rnd, precision)
                    .map(value -> value + "E-" + (15 + rnd.nextInt(5)))
                    .collect(Collectors.toList());

            Collections.shuffle(stringDoubles, rnd);

            values = stringDoubles.stream()
                .mapToDouble(Double::parseDouble)
                .toArray();
        }
    }

    private static Stream<String> filterStream(DoubleStream doubleStream, Random rnd, int precision) {
        String format = String.format("%%.%df", precision);
        return doubleStream.mapToObj(v -> (rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, format, v))
            .filter(s -> Double.parseDouble(s) != 0)
            .limit(BLOCK_SIZE);
    }

    /**
     * per-thread buffer
     */
    @State(Scope.Thread)
    public static class ThreadContext {
        public StringBuilder sb = new StringBuilder(64); // enough for any string
    }

    /**
     * DxFeed double formatter with scale precision 2
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleScale2(ThreadContext ctx, EngineeringNumbers engineeringNumbers, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : engineeringNumbers.values) {
            sb.setLength(0);
            MathUtil.formatDouble(sb, d, 2);
            blackhole.consume(sb);
        }
    }

    /**
     * Jdk BigDecimal with scale precision 2
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void jdkDoubleScale2(EngineeringNumbers engineeringNumbers, Blackhole blackhole) {
        for (double d : engineeringNumbers.values) {
            blackhole.consume(BigDecimal.valueOf(d)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toString());
        }
    }

    /**
     * DxFeed double formatter with scale precision 9
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleScale9(ThreadContext ctx, EngineeringNumbers engineeringNumbers, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : engineeringNumbers.values) {
            sb.setLength(0);
            MathUtil.formatDouble(sb, d, 9);
            blackhole.consume(sb);
        }
    }

    /**
     * DxFeed double formatter with scale precision 9 with creation of String to estimate coast of String creation
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleScaleString9(EngineeringNumbers engineeringNumbers, Blackhole blackhole) {
        for (double d : engineeringNumbers.values) {
            blackhole.consume(MathUtil.formatDouble(d, 9));
        }
    }

    /**
     * Jdk BigDecimal with scale precision 9
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void jdkDoubleScale9(EngineeringNumbers engineeringNumbers, Blackhole blackhole) {
        for (double d : engineeringNumbers.values) {
            blackhole.consume(BigDecimal.valueOf(d)
                .setScale(9, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toString());
        }
    }

    /**
     * DxFeed double formatter with scale precision 20
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleScale20(ThreadContext ctx, ScientificNumbers scientificNumbers, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : scientificNumbers.values) {
            sb.setLength(0);
            MathUtil.formatDouble(sb, d, 20);
            blackhole.consume(sb);
        }
    }

    /**
     * Jdk BigDecimal with scale precision 20
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void jdkDoubleScale20(ScientificNumbers scientificNumbers, Blackhole blackhole) {
        for (double d : scientificNumbers.values) {
            blackhole.consume(BigDecimal.valueOf(d)
                .setScale(20, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toString());
        }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(FastDoubleFormatScaleBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
