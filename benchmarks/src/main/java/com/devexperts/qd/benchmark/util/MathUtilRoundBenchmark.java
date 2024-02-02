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
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

// Benchmark results: Intel(R) Xeon(R) Gold 6354 CPU @ 3.00GHz, VM: JDK 21, OpenJDK 64-Bit Server VM, 21+35-LTS
//    Benchmark                                           (exponent)  (precision)  Mode  Cnt     Score     Error   Units
//    MathUtilRoundBenchmark.mathRoundCeiling                  false            0  avgt    5     7.235 ±   0.052   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false            3  avgt    5    77.821 ±   6.609   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false            5  avgt    5    75.368 ±   0.760   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false            9  avgt    5    56.700 ±   0.545   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false           13  avgt    5    47.140 ±   0.123   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false           16  avgt    5    42.652 ±   0.030   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                  false           18  avgt    5    41.573 ±   0.023   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true            0  avgt    5    94.091 ±   0.151   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true            3  avgt    5    88.213 ±   0.249   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true            5  avgt    5    81.343 ±   0.198   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true            9  avgt    5    75.626 ±   0.217   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true           13  avgt    5    61.685 ±   0.580   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true           16  avgt    5    54.603 ±   0.102   ns/op
//    MathUtilRoundBenchmark.mathRoundCeiling                   true           18  avgt    5    53.970 ±   0.336   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false            0  avgt    5     7.255 ±   0.024   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false            3  avgt    5    77.115 ±   0.524   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false            5  avgt    5    75.376 ±   0.295   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false            9  avgt    5    59.214 ±   1.134   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false           13  avgt    5    47.126 ±   0.066   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false           16  avgt    5    42.666 ±   0.075   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                   false           18  avgt    5    41.583 ±   0.094   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true            0  avgt    5    93.502 ±   0.095   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true            3  avgt    5    84.807 ±   0.073   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true            5  avgt    5    80.314 ±   0.183   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true            9  avgt    5    75.468 ±   0.349   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true           13  avgt    5    61.407 ±   0.075   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true           16  avgt    5    53.816 ±   0.136   ns/op
//    MathUtilRoundBenchmark.mathRoundHalfUp                    true           18  avgt    5    53.710 ±   0.126   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false            0  avgt    5     3.135 ±   0.013   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false            3  avgt    5    18.165 ±   0.036   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false            5  avgt    5    17.631 ±   0.102   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false            9  avgt    5    17.673 ±   0.026   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false           13  avgt    5    17.791 ±   0.032   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false           16  avgt    5    17.712 ±   0.035   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                 false           18  avgt    5    17.646 ±   0.038   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true            0  avgt    5    12.222 ±   0.020   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true            3  avgt    5    12.292 ±   0.081   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true            5  avgt    5    12.474 ±   0.026   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true            9  avgt    5    12.486 ±   0.035   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true           13  avgt    5    12.555 ±   0.015   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true           16  avgt    5    12.530 ±   0.034   ns/op
//    MathUtilRoundBenchmark.mathRoundPrevious                  true           18  avgt    5    12.530 ±   0.021   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false            0  avgt    5     3.136 ±   0.011   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false            3  avgt    5    10.123 ±   0.035   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false            5  avgt    5    10.292 ±   0.004   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false            9  avgt    5    10.366 ±   0.018   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false           13  avgt    5    10.289 ±   0.006   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false           16  avgt    5    10.586 ±   0.009   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                    false           18  avgt    5    10.292 ±   0.012   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true            0  avgt    5     7.226 ±   0.016   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true            3  avgt    5     7.068 ±   0.015   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true            5  avgt    5     7.082 ±   0.016   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true            9  avgt    5     7.473 ±   0.017   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true           13  avgt    5     7.168 ±   0.003   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true           16  avgt    5     7.339 ±   0.032   ns/op
//    MathUtilRoundBenchmark.mathRoundShort                     true           18  avgt    5     7.332 ±   0.020   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false            0  avgt    5    10.696 ±   0.016   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false            3  avgt    5    78.776 ±   0.312   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false            5  avgt    5    75.119 ±   0.229   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false            9  avgt    5    59.462 ±   0.120   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false           13  avgt    5    49.924 ±   0.052   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false           16  avgt    5    45.196 ±   0.287   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                  false           18  avgt    5    44.074 ±   0.109   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true            0  avgt    5    95.730 ±   3.752   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true            3  avgt    5    90.517 ±   1.124   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true            5  avgt    5    80.525 ±   0.347   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true            9  avgt    5    71.097 ±   0.108   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true           13  avgt    5    54.973 ±   0.496   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true           16  avgt    5    47.404 ±   0.070   ns/op
//    MathUtilRoundBenchmark.mathScaleCeiling                   true           18  avgt    5    46.610 ±   0.125   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false            0  avgt    5    10.695 ±   0.011   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false            3  avgt    5    78.645 ±   0.553   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false            5  avgt    5    75.168 ±   0.164   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false            9  avgt    5    60.393 ±   0.098   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false           13  avgt    5    49.812 ±   0.070   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false           16  avgt    5    45.464 ±   0.105   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                   false           18  avgt    5    44.173 ±   0.050   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true            0  avgt    5    97.336 ±   0.368   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true            3  avgt    5    93.586 ±   6.056   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true            5  avgt    5    80.916 ±   0.654   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true            9  avgt    5    70.458 ±   0.146   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true           13  avgt    5    54.646 ±   0.132   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true           16  avgt    5    49.746 ±   0.085   ns/op
//    MathUtilRoundBenchmark.mathScaleHalfUp                    true           18  avgt    5    49.462 ±   0.036   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false            0  avgt    5    56.993 ±   2.131   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false            3  avgt    5    79.708 ±   4.688   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false            5  avgt    5    94.291 ±   4.121   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false            9  avgt    5   106.027 ±   5.299   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false           13  avgt    5   115.292 ±   5.996   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false           16  avgt    5   113.574 ±   6.155   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                   false           18  avgt    5   143.953 ±  13.576   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true            0  avgt    5   276.724 ±  10.686   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true            3  avgt    5   283.437 ±  11.345   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true            5  avgt    5   291.322 ±  12.924   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true            9  avgt    5   329.841 ±  12.923   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true           13  avgt    5   430.067 ±  17.202   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true           16  avgt    5   456.346 ±  18.059   ns/op
//    MathUtilRoundBenchmark.roundBigDecimal                    true           18  avgt    5   468.103 ±  18.434   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false            0  avgt    5    56.444 ±   1.856   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false            3  avgt    5    78.226 ±   3.050   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false            5  avgt    5    88.369 ±   4.324   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false            9  avgt    5   104.724 ±   4.350   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false           13  avgt    5   114.162 ±   5.865   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false           16  avgt    5   112.974 ±   4.887   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                   false           18  avgt    5   130.612 ±   5.374   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true            0  avgt    5   254.183 ±   9.358   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true            3  avgt    5   282.297 ±   9.343   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true            5  avgt    5   268.287 ±  10.017   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true            9  avgt    5   300.085 ±  15.433   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true           13  avgt    5   368.838 ±  24.214   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true           16  avgt    5   397.149 ±  12.853   ns/op
//    MathUtilRoundBenchmark.scaleBigDecimal                    true           18  avgt    5   404.102 ±  12.324   ns/op

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MathUtilRoundBenchmark {

    private static final int MAX_DECIMAL_DIGITS = 14;
    private static final long[] POW10 = LongStream.iterate(1L, v -> v * 10).limit(MAX_DECIMAL_DIGITS + 1).toArray();

    private static double roundDecimalPrev(double x) {
        if (Double.isNaN(x) || x == Math.floor(x))
            return x; // integer, NaN, or +/- inf
        double signum = Math.signum(x);
        double abs = Math.abs(x);
        int pow = Math.min(MAX_DECIMAL_DIGITS, MAX_DECIMAL_DIGITS - 1 - (int) (Math.floor(Math.log10(abs))));
        for (int i = pow; i >= 0; i--) {
            long mantissa = (long) (POW10[i] * abs + 0.5);
            if (mantissa < POW10[MAX_DECIMAL_DIGITS])
                return signum * mantissa / POW10[i];
        }
        // Mantissa >= 10^14 with fractions -- just round
        return Math.round(x);
    }

    private static final int BLOCK_SIZE = 1000;
    private static final int ROUND_PRECISION = 7;
    private static final MathContext MATH_CONTEXT = new MathContext(ROUND_PRECISION);

    @Param({"0", "3", "5", "9", "13", "16", "18"})
    private int precision;

    @Param({"false", "true"})
    private boolean exponent;

    private double[] values;

    @Setup
    public void setup() {
        Random rnd = new Random(0);
        List<String> stringDoubles;
        if (exponent) {
            stringDoubles = filterStream(rnd.doubles(1, 10), rnd, BLOCK_SIZE, precision)
                .map(value -> value + "E" + (rnd.nextBoolean() ? "-" : "") + rnd.nextInt(308))
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

        values = stringDoubles.stream().mapToDouble(Double::parseDouble).toArray();
    }

    private static Stream<String> filterStream(DoubleStream doubleStream, Random rnd, int limit, int precision) {
        String format = String.format("%%.%df", precision);
        return doubleStream.mapToObj(value -> (rnd.nextBoolean() ? "-" : "") + String.format(Locale.US, format, value))
            .filter(s -> Double.parseDouble(s) != 0)
            .limit(limit);
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathRoundPrevious(Blackhole blackhole) {
        for (double value : values) {
            double d = roundDecimalPrev(value);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathRoundShort(Blackhole blackhole) {
        for (double value : values) {
            double d = MathUtil.roundDecimal(value);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathRoundHalfUp(Blackhole blackhole) {
        for (double value : values) {
            double d = MathUtil.roundPrecision(value, ROUND_PRECISION, RoundingMode.HALF_UP);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathRoundCeiling(Blackhole blackhole) {
        for (double value : values) {
            double d = MathUtil.roundPrecision(value, ROUND_PRECISION, RoundingMode.CEILING);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathScaleHalfUp(Blackhole blackhole) {
        for (double value : values) {
            double d = MathUtil.roundDecimal(value, ROUND_PRECISION, RoundingMode.HALF_UP);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void mathScaleCeiling(Blackhole blackhole) {
        for (double value : values) {
            double d = MathUtil.roundDecimal(value, ROUND_PRECISION, RoundingMode.CEILING);
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void scaleBigDecimal(Blackhole blackhole) {
        for (double value : values) {
            double d = BigDecimal.valueOf(value).setScale(ROUND_PRECISION, RoundingMode.HALF_UP).doubleValue();
            blackhole.consume(d);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BLOCK_SIZE)
    public void roundBigDecimal(Blackhole blackhole) {
        for (double value : values) {
            double d = BigDecimal.valueOf(value).round(MATH_CONTEXT).doubleValue();
            blackhole.consume(d);
        }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(MathUtilRoundBenchmark.class.getSimpleName())
                .build()
        ).run();
    }
}
