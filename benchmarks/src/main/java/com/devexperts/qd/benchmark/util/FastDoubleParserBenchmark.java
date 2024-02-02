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

import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import com.devexperts.util.MathUtil;
import com.devexperts.util.WideDecimal;
import com.dxfeed.glossary.AdditionalUnderlyings;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

// Benchmark results: CPU Intel(R) Xeon(R) Gold 6354 CPU @ 3.00GHz, JDK 20.0.2, OpenJDK 64-Bit Server VM, 20.0.2+9-FR
//    Benchmark                                              (exponent)   (type)  Mode  Cnt    Score   Error  Units
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false        0  avgt    5    9.377 ± 0.039  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false        2  avgt    5   13.476 ± 0.036  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false        5  avgt    5   17.973 ± 0.091  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false        9  avgt    5   23.253 ± 0.031  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false       13  avgt    5   30.020 ± 0.105  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false       16  avgt    5   34.968 ± 0.082  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false  0.yyyyy  avgt    5   13.007 ± 0.036  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser       false    xx.yy  avgt    5   11.999 ± 0.085  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true        0  avgt    5    9.728 ± 0.086  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true        2  avgt    5   15.058 ± 0.038  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true        5  avgt    5   18.108 ± 0.052  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true        9  avgt    5   21.895 ± 0.174  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true       13  avgt    5   27.088 ± 0.204  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true       16  avgt    5   31.926 ± 0.116  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true  0.yyyyy  avgt    5   17.153 ± 0.122  ns/op
//    FastDoubleParserBenchmark.additionalUnderlyingsParser        true    xx.yy  avgt    5   15.947 ± 0.064  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false        0  avgt    5   10.543 ± 0.042  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false        2  avgt    5   13.880 ± 0.090  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false        5  avgt    5   17.588 ± 0.104  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false        9  avgt    5   22.265 ± 0.101  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false       13  avgt    5   28.293 ± 0.085  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false       16  avgt    5   34.132 ± 0.575  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false  0.yyyyy  avgt    5   15.935 ± 0.037  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser              false    xx.yy  avgt    5   12.396 ± 0.101  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true        0  avgt    5   17.359 ± 0.084  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true        2  avgt    5   20.144 ± 0.014  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true        5  avgt    5   22.581 ± 0.019  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true        9  avgt    5   27.629 ± 0.025  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true       13  avgt    5   30.620 ± 0.009  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true       16  avgt    5   35.533 ± 0.076  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true  0.yyyyy  avgt    5   23.403 ± 0.020  ns/op
//    FastDoubleParserBenchmark.fastDoubleUtilParser               true    xx.yy  avgt    5   16.836 ± 0.045  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false        0  avgt    5   19.778 ± 0.419  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false        2  avgt    5   24.418 ± 2.095  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false        5  avgt    5   27.574 ± 2.103  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false        9  avgt    5   45.734 ± 1.693  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false       13  avgt    5   72.215 ± 2.828  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false       16  avgt    5   93.217 ± 3.332  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false  0.yyyyy  avgt    5   26.409 ± 0.936  ns/op
//    FastDoubleParserBenchmark.jdkParser                         false    xx.yy  avgt    5   22.301 ± 0.693  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true        0  avgt    5   85.412 ± 3.303  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true        2  avgt    5   89.940 ± 3.494  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true        5  avgt    5  100.660 ± 3.723  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true        9  avgt    5  114.028 ± 4.892  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true       13  avgt    5  126.263 ± 5.236  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true       16  avgt    5  188.405 ± 7.744  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true  0.yyyyy  avgt    5   58.695 ± 1.746  ns/op
//    FastDoubleParserBenchmark.jdkParser                          true    xx.yy  avgt    5   26.420 ± 0.660  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false        0  avgt    5   13.609 ± 0.031  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false        2  avgt    5   18.400 ± 0.137  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false        5  avgt    5   20.646 ± 0.062  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false        9  avgt    5   24.051 ± 0.142  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false       13  avgt    5   35.819 ± 1.198  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false       16  avgt    5   71.960 ± 2.555  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false  0.yyyyy  avgt    5   17.706 ± 0.013  ns/op
//    FastDoubleParserBenchmark.modernFastParser                  false    xx.yy  avgt    5   17.205 ± 0.010  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true        0  avgt    5   21.382 ± 0.584  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true        2  avgt    5   23.880 ± 0.668  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true        5  avgt    5   26.679 ± 0.698  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true        9  avgt    5   29.841 ± 0.601  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true       13  avgt    5   32.144 ± 0.835  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true       16  avgt    5   35.888 ± 1.274  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true  0.yyyyy  avgt    5   24.002 ± 0.919  ns/op
//    FastDoubleParserBenchmark.modernFastParser                   true    xx.yy  avgt    5   20.258 ± 0.029  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false        0  avgt    5   34.015 ± 1.277  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false        2  avgt    5   52.150 ± 1.449  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false        5  avgt    5   60.895 ± 1.647  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false        9  avgt    5   70.382 ± 1.892  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false       13  avgt    5   99.892 ± 3.912  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false       16  avgt    5  117.590 ± 3.220  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false  0.yyyyy  avgt    5   51.383 ± 1.845  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                 false    xx.yy  avgt    5   49.126 ± 1.489  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true        0  avgt    5  156.712 ± 6.069  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true        2  avgt    5  171.000 ± 4.974  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true        5  avgt    5  192.782 ± 7.755  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true        9  avgt    5  211.425 ± 7.852  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true       13  avgt    5  224.562 ± 7.238  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true       16  avgt    5  287.863 ± 8.679  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true  0.yyyyy  avgt    5  144.769 ± 3.915  ns/op
//    FastDoubleParserBenchmark.wideDecimalParser                  true    xx.yy  avgt    5   58.026 ± 0.834  ns/op

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FastDoubleParserBenchmark {

    //use MethodHandle to access private static method with similar access time as direct invoke
    private static final MethodHandle METHOD_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method method =
                AdditionalUnderlyings.class.getDeclaredMethod("parseDouble", String.class, int.class, int.class);
            method.setAccessible(true);
            METHOD_HANDLE = lookup.unreflect(method);
            method.setAccessible(false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int BLOCK_SIZE = 100;

    private static final String SET_1 = "0.yyyyy";
    private static final String SET_2 = "xx.yy";

    @Param({"0", "2", "5", "9", "13", "16", SET_1, SET_2})
    private String type;

    @Param({"false", "true"})
    private boolean exponent;

    private List<String> stringDoubles;

    @Setup
    public void setup() {
        Random rnd = new Random(0);
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
    }

    private static Stream<String> filterStream(DoubleStream doubleStream, Random rnd, int limit, int precision) {
        return filterStream(doubleStream, rnd, limit, precision, true);
    }

    private static Stream<String> filterStream(
        DoubleStream doubleStream, Random rnd, int limit, int precision, boolean sign)
    {
        String precisionFormat = String.format("%%.%df", precision);
        return doubleStream.mapToObj(value ->
                (sign && rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, precisionFormat, value))
            .filter(s -> Double.parseDouble(s) != 0)
            .limit(limit);
    }


    /**
     * Baseline java sdk parser fully compatible with IEEE 754
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void jdkParser(Blackhole blackhole) {
        for (String stringDouble : stringDoubles) {
            double result = Double.parseDouble(stringDouble);
            blackhole.consume(result);
        }
    }

    /**
     * DxFeed additional underlyings parser applicable only for engineering format
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void additionalUnderlyingsParser(Blackhole blackhole) throws Throwable {
        for (String stringDouble : stringDoubles) {
            double result = (double) METHOD_HANDLE.invokeExact(stringDouble, 0, stringDouble.length());
            blackhole.consume(result);
        }
    }

    /**
     * DxFeed fast double parser with some precision restrictions after
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void fastDoubleUtilParser(Blackhole blackhole) {
        for (String stringDouble : stringDoubles) {
            double result = MathUtil.parseDouble(stringDouble);
            blackhole.consume(result);
        }
    }

    /**
     * WideDecimal parser to long format and to double
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void wideDecimalParser(Blackhole blackhole) {
        for (String stringDouble : stringDoubles) {
            double result = WideDecimal.toDouble(WideDecimal.parseWide(stringDouble));
            blackhole.consume(result);
        }
    }

    /**
     * Modern fast parser fully compatible with IEEE 754
     * This is a Java port of Daniel Lemire's fast double parser
     * <a href="https://github.com/lemire/fast_double_parser">Daniel Lemire's fast double parser</a>
     */
    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void modernFastParser(Blackhole blackhole) {
        for (String stringDouble : stringDoubles) {
            double result = JavaDoubleParser.parseDouble(stringDouble);
            blackhole.consume(result);
        }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(FastDoubleParserBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
