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

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark testing best way to check whether char is in [0-9a-zA-Z] range.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeUtilValidCharBenchmark {

    public static final int ITERATIONS = 10_000;

    @Param({"192"})
    public int maxChar;

    public char[] chars;

    @Setup
    public void setup() {
        Random rnd = new Random(1);
        chars = new char[ITERATIONS];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) rnd.nextInt(maxChar);
        }
    }

    // Initialization

    private static final boolean[] BOOLEAN_ARRAY = new boolean[128];
    private static final int[] INT_ARRAY = new int[128 / 32];
    private static BitSet BIT_SET = new BitSet(128);
    private static final long LOW_BITS;
    private static final long HIGH_BITS;
    private static final long LOW_BITS2;
    private static final long HIGH_BITS2;

    static {
        String pattern = "[a-zA-Z0-9]";
        for (int c = 0; c < 128; c++) {
            boolean matches = String.valueOf((char) c).matches(pattern);

            BOOLEAN_ARRAY[c] = matches;
            if (matches)
                INT_ARRAY[c >> 5] |= (Integer.MIN_VALUE >>> (c & 31));
            BIT_SET.set(c, matches);
        }

        long low = 0;
        long high = 0;
        for (int i = 0; i < 64; i++) {
            if (String.valueOf((char) i).matches(pattern))
                low |= 1L << i;
            if (String.valueOf((char) (i + 64)).matches(pattern))
                high |= 1L << i;
        }
        LOW_BITS = low;
        HIGH_BITS = high;

        long low1 = 0;
        long high1 = 0;
        for (int i = 0; i < 64; i++) {
            if (String.valueOf((char) i).matches(pattern))
                low1 |= Long.MIN_VALUE >>> i;
            if (String.valueOf((char) (i + 64)).matches(pattern))
                high1 |= Long.MIN_VALUE >>> i;
        }
        LOW_BITS2 = low1;
        HIGH_BITS2 = high1;

        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (isValidViaPlatform(c) != isValidViaRange(c))
                throw new RuntimeException("Error in isValidViaRange " + (int) c);
            if (isValidViaPlatform(c) != isValidViaBooleanArr(c))
                throw new RuntimeException("Error in isValidViaBooleanArr " + (int) c);
            if (isValidViaPlatform(c) != isValidViaIntArr(c))
                throw new RuntimeException("Error in isValidViaIntArr " + (int) c);
            if (isValidViaPlatform(c) != isValidViaLongs(c))
                throw new RuntimeException("Error in isValidViaLongs " + (int) c);
            if (isValidViaPlatform(c) != isValidViaLongs2(c))
                throw new RuntimeException("Error in isValidViaLongs2 " + (int) c);
            if (isValidViaPlatform(c) != isValidViaLongs3(c))
                throw new RuntimeException("Error in isValidViaLongs3 " + (int) c);
            if (isValidViaPlatform(c) != isValidViaBitSet(c))
                throw new RuntimeException("Error in isValidViaBitSet " + (int) c);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void _baseline(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(c);
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void platform(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaPlatform(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void range(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaRange(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void booleans(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaBooleanArr(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void ints(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaIntArr(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void longs(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaLongs(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void longs2(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaLongs2(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void longs3(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaLongs3(c));
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void bitset(Blackhole blackhole) {
        for (char c : chars)
            blackhole.consume(isValidViaBitSet(c));
    }

    private static boolean isValidViaPlatform(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    private static boolean isValidViaRange(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isValidViaBitSet(char c) {
        return BIT_SET.get(c);
    }

    private static boolean isValidViaBooleanArr(char c) {
        return c < BOOLEAN_ARRAY.length && BOOLEAN_ARRAY[c];
    }

    private static boolean isValidViaIntArr(char c) {
        return ((c - 128) & (INT_ARRAY[(c >> 5) & 3] << c)) < 0;
    }

    private static boolean isValidViaLongs(char c) {
        // lowMask  = ( 0 <= c <=  63) ? -1 : 0
        // highMask = (64 <= c <= 127) ? -1 : 0
        long lowMask = (c - 64L) >> 32;
        long highMask = ((c - 128L) & (63L - c)) >> 32;
        return (((lowMask & LOW_BITS) | (highMask & HIGH_BITS)) & (1L << c)) != 0;
    }

    private static boolean isValidViaLongs2(char c) {
        if (c > 128)
            return false;
        long mask = c - 64;
        return (mask < 0 ? LOW_BITS & (1L << c) : HIGH_BITS & (1L << mask)) != 0;
    }

    private static boolean isValidViaLongs3(char c) {
        //             63-c   c-64   127-c   c-128   (c-128)&(63-c)
        //   0..63      0+     -       0+     -            0+
        //  64..127      -    0+       0+     -             -
        // 128..inf      -    0+        -    0+            0+
        return (((((c - 64L) >> 32) & LOW_BITS2) | ((((c - 128L) & (63L - c)) >> 32) & HIGH_BITS2)) << c) < 0;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeUtilValidCharBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
