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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

// Benchmark results: Intel(R) Xeon(R) Gold 6354 CPU @ 3.00GHz, VM: JDK 21, OpenJDK 64-Bit Server VM, 21+35-LTS
//Benchmark                                                   (exponent)   (type)  Mode  Cnt     Score     Error   Units
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false        0  avgt    5   117.048 ±   4.400   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false        2  avgt    5    98.704 ±   3.659   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false        5  avgt    5    99.870 ±   3.671   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false        9  avgt    5    97.555 ±   2.471   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false       13  avgt    5    95.099 ±   2.317   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false       16  avgt    5    94.103 ±   2.426   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false  0.yyyyy  avgt    5    97.307 ±   2.630   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString             false    xx.yy  avgt    5   100.167 ±   3.776   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true        0  avgt    5   119.888 ±   3.552   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true        2  avgt    5   115.162 ±   4.240   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true        5  avgt    5   112.686 ±   4.178   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true        9  avgt    5   110.205 ±   2.813   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true       13  avgt    5   107.254 ±   2.701   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true       16  avgt    5   105.327 ±   2.718   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true  0.yyyyy  avgt    5   103.537 ±   2.962   ns/op
//FastDoubleFormatPrecisionBenchmark.doubleRyuToString              true    xx.yy  avgt    5   108.749 ±   4.034   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false        0  avgt    5    14.438 ±   0.059   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false        2  avgt    5    95.518 ±   0.209   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false        5  avgt    5    90.585 ±   0.152   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false        9  avgt    5    80.932 ±   0.145   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false       13  avgt    5    77.060 ±   0.087   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false       16  avgt    5    73.329 ±   0.048   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false  0.yyyyy  avgt    5    84.631 ±   0.059   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat              false    xx.yy  avgt    5    91.282 ±   5.934   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true        0  avgt    5   107.986 ±   0.108   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true        2  avgt    5   111.523 ±   0.136   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true        5  avgt    5   110.583 ±   0.206   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true        9  avgt    5    99.897 ±   0.115   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true       13  avgt    5    94.423 ±   0.076   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true       16  avgt    5    88.553 ±   0.157   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true  0.yyyyy  avgt    5   101.014 ±   0.053   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormat               true    xx.yy  avgt    5    80.452 ±   0.075   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false        0  avgt    5    14.551 ±   0.015   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false        2  avgt    5    94.622 ±   0.062   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false        5  avgt    5    90.116 ±   0.066   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false        9  avgt    5    82.208 ±   0.061   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false       13  avgt    5    72.570 ±   0.052   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false       16  avgt    5    67.816 ±   0.107   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false  0.yyyyy  avgt    5    84.775 ±   0.244   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision     false    xx.yy  avgt    5    90.136 ±   0.328   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true        0  avgt    5   108.187 ±   0.152   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true        2  avgt    5   110.952 ±   0.140   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true        5  avgt    5   109.091 ±   0.121   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true        9  avgt    5   107.007 ±   0.167   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true       13  avgt    5    92.746 ±   0.128   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true       16  avgt    5    83.983 ±   0.117   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true  0.yyyyy  avgt    5    99.598 ±   0.163   ns/op
//FastDoubleFormatPrecisionBenchmark.fastDoubleFormatPrecision      true    xx.yy  avgt    5    80.945 ±   0.105   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false        0  avgt    5    37.189 ±   1.847   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false        2  avgt    5    53.223 ±   2.172   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false        5  avgt    5    70.927 ±   2.828   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false        9  avgt    5    76.260 ±   2.199   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false       13  avgt    5    80.410 ±   2.223   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false       16  avgt    5    81.978 ±   4.058   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false  0.yyyyy  avgt    5    52.275 ±   2.592   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                   false    xx.yy  avgt    5    52.993 ±   2.059   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true        0  avgt    5    52.500 ±   2.571   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true        2  avgt    5    53.453 ±   2.111   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true        5  avgt    5    55.836 ±   1.638   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true        9  avgt    5    66.542 ±   2.202   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true       13  avgt    5    67.351 ±   2.703   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true       16  avgt    5    73.434 ±   2.852   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true  0.yyyyy  avgt    5    52.746 ±   2.633   ns/op
//FastDoubleFormatPrecisionBenchmark.jdkToString                    true    xx.yy  avgt    5    38.996 ±   1.567   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false        0  avgt    5    13.508 ±   0.761   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false        2  avgt    5   523.645 ±  21.558   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false        5  avgt    5   608.495 ±  25.117   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false        9  avgt    5   681.797 ±  20.959   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false       13  avgt    5   727.461 ±  32.523   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false       16  avgt    5   745.706 ±  26.688   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false  0.yyyyy  avgt    5    74.495 ±   3.018   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying  false    xx.yy  avgt    5    55.221 ±   2.861   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true        0  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true        2  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true        5  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true        9  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true       13  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true       16  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true  0.yyyyy  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.previousAdditionalUnderlying   true    xx.yy  avgt    5     0.006 ±   0.001   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false        0  avgt    5    14.804 ±   0.034   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false        2  avgt    5    65.110 ±   0.066   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false        5  avgt    5    62.881 ±   0.052   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false        9  avgt    5    60.988 ±   0.031   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false       13  avgt    5    58.629 ±   0.064   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false       16  avgt    5    57.525 ±   0.083   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false  0.yyyyy  avgt    5    64.312 ±   0.136   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                   false    xx.yy  avgt    5    47.510 ±   0.081   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true        0  avgt    5    78.863 ±   0.085   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true        2  avgt    5    82.555 ±   0.148   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true        5  avgt    5    82.209 ±   0.175   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true        9  avgt    5    78.200 ±   0.048   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true       13  avgt    5    73.279 ±   1.387   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true       16  avgt    5    71.916 ±   0.082   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true  0.yyyyy  avgt    5    73.133 ±   0.179   ns/op
//FastDoubleFormatPrecisionBenchmark.wideDecimal                    true    xx.yy  avgt    5    42.565 ±   0.039   ns/op

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FastDoubleFormatPrecisionBenchmark {

    private static final int BLOCK_SIZE = 100;

    private static final String SET_1 = "0.yyyyy";
    private static final String SET_2 = "xx.yy";

    @Param({"0", "2", "5", "9", "13", "16", SET_1, SET_2})
    private String type;

    @Param({"false", "true"})
    private boolean exponent;

    private double[] values;

    /**
     * per-thread buffer
     */
    @State(Scope.Thread)
    public static class ThreadContext {
        public StringBuilder sb = new StringBuilder(64); // enough for any string
    }

    @Setup
    public void setup() {
        Random rnd = new Random(0);
        List<String> stringDoubles;

        int precision;
        Stream<String> dataSet;
        switch (type) {
            case SET_1:
                dataSet = filterStream(rnd.doubles(0.0001, 1), rnd, BLOCK_SIZE, 5, false);
                if (exponent) {
                    dataSet = dataSet.map(value -> value + "E-" + (5 + rnd.nextInt(20))); // E[-5,-25)
                }
                stringDoubles = dataSet.collect(Collectors.toList());
                break;
            case SET_2:
                dataSet = filterStream(rnd.doubles(100, 1000), rnd, BLOCK_SIZE, 2, false);
                if (exponent) {
                    dataSet = dataSet.map(value -> value + "E" + (5 + rnd.nextInt(10))); // E[5,15)
                }
                stringDoubles = dataSet.collect(Collectors.toList());
                break;
            default:
                precision = Integer.parseInt(type);
                if (exponent) {
                    stringDoubles = filterStream(rnd.doubles(1, 10), rnd, BLOCK_SIZE, precision)
                        // use only max e100 because of widedecimal doesn't support more than 128
                        .map(value -> value + "E" + (rnd.nextBoolean() ? "-" : "") + rnd.nextInt(100))
                        .collect(Collectors.toList());
                } else {
                    int partSize = BLOCK_SIZE / 4;

                    DoubleStream lowDoublePart1 = precision == 0 ? rnd.doubles(0, 10) : rnd.doubles(0.001, 0.1);
                    DoubleStream lowDoublePart2 = precision == 0 ? rnd.doubles(10, 100) : rnd.doubles(0.1, 10);

                    Stream<String> lowStream = Stream.concat(
                        filterStream(lowDoublePart1, rnd, partSize, precision),
                        filterStream(lowDoublePart2, rnd, partSize, precision)
                    );
                    Stream<String> upperStream = Stream.concat(
                        filterStream(rnd.doubles(100, 10000), rnd, partSize, precision),
                        filterStream(rnd.doubles(10000, 10000000), rnd, partSize, precision)
                    );

                    stringDoubles = Stream.concat(lowStream, upperStream).collect(Collectors.toList());

                    Collections.shuffle(stringDoubles, rnd);
                }
        }

        values = stringDoubles.stream().mapToDouble(Double::parseDouble).toArray();
    }

    private static Stream<String> filterStream(DoubleStream doubleStream, Random rnd, int limit, int precision) {
        return filterStream(doubleStream, rnd, limit, precision, true);
    }

    private static Stream<String> filterStream(
        DoubleStream doubleStream, Random rnd, int limit, int precision, boolean sign)
    {
        String format = String.format("%%.%df", precision);
        return doubleStream.mapToObj(v -> (sign && rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, format, v))
            .filter(s -> Double.parseDouble(s) != 0)
            .limit(limit);
    }

    /**
     * Baseline jdk double formatter
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void jdkToString(ThreadContext ctx, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : values) {
            sb.setLength(0);
            sb.append(d);
            blackhole.consume(sb);
        }
    }

    /**
     * DxFeed double formatter without rounding result similar as jdk toString
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleFormat(ThreadContext ctx, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : values) {
            sb.setLength(0);
            MathUtil.formatDoublePrecision(sb, d, 0);
            blackhole.consume(sb);
        }
    }

    /**
     * DxFeed double formatter with rounding to 9 significant digits
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleFormatPrecision(ThreadContext ctx, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : values) {
            sb.setLength(0);
            MathUtil.formatDoublePrecision(sb, d, 9);
            blackhole.consume(sb);
        }
    }

    /**
     * WideDecimal presentation to string
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void wideDecimal(ThreadContext ctx, Blackhole blackhole) {
        StringBuilder sb = ctx.sb;
        for (double d : values) {
            sb.setLength(0);
            WideDecimal.appendTo(sb, WideDecimal.composeWide(d));
            blackhole.consume(sb);
        }
    }

    /**
     * Previous additional underlyings formatter for engineering values only
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void previousAdditionalUnderlying(ThreadContext ctx, Blackhole blackhole) {
        // available only for engineering format
        if (!exponent) {
            StringBuilder sb = ctx.sb;
            for (double d : values) {
                sb.setLength(0);
                sb.append(formatDouble(d));
                blackhole.consume(sb);
            }
        }
    }

    static String formatDouble(double d) {
        if (d == (double) (long) d) {
            return Long.toString((long) d);
        }
        if (d >= 0.01 && d < 1000000) {
            return Double.toString(d);
        }
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(20);
        nf.setGroupingUsed(false);
        return nf.format(d);
    }

    /**
     * Fast double to string formatter by means of Ryu library
     * This project contains routines to convert IEEE-754 floating-point numbers to decimal strings
     * using the shortest formatting.
     * see <a href="https://github.com/ulfjack/ryu">Ryu</a> for more information
     **/
//    @Benchmark
//    @OperationsPerInvocation(BLOCK_SIZE)
//    public void doubleRyuToString(ThreadContext ctx, Blackhole blackhole) {
//        StringBuilder sb = ctx.sb;
//        for (double d : values) {
//            sb.setLength(0);
//            RyuDouble.doubleToString(sb, d, RyuDouble.RoundingMode.ROUND_EVEN);
//            blackhole.consume(sb);
//        }
//    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(FastDoubleFormatPrecisionBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
