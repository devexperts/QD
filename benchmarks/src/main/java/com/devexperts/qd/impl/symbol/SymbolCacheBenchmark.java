/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.symbol;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*

# JMH version: 1.37
# VM version: JDK 21.0.6, OpenJDK 64-Bit Server VM, 21.0.6+7-LTS
# CPU: 2 x Intel(R) Xeon(R) CPU E5-2630 v4 @ 2.20GHz (10 core x 2) - 40 threads total
# RAM: 128 Gb

Benchmark                            (cacheSize)  (type)  Mode  Cnt    Score    Error  Units
SymbolCacheBenchmark.testAcquire          100000   cache  avgt   25  150.473 ±  3.283  ns/op
SymbolCacheBenchmark.testAcquire          100000     chm  avgt   25   74.358 ±  0.455  ns/op
SymbolCacheBenchmark.testAcquire         1000000   cache  avgt   25  327.908 ±  1.575  ns/op
SymbolCacheBenchmark.testAcquire         1000000     chm  avgt   25  416.159 ±  5.201  ns/op
SymbolCacheBenchmark.testResolve          100000   cache  avgt   25  212.838 ±  8.231  ns/op
SymbolCacheBenchmark.testResolve          100000     chm  avgt   25  209.692 ±  8.506  ns/op
SymbolCacheBenchmark.testResolve         1000000   cache  avgt   25  376.217 ± 11.532  ns/op
SymbolCacheBenchmark.testResolve         1000000     chm  avgt   25  370.549 ±  4.625  ns/op
SymbolCacheBenchmark.testResolveKey       100000   cache  avgt   25  237.091 ±  7.727  ns/op
SymbolCacheBenchmark.testResolveKey       100000     chm  avgt   25  404.464 ±  5.114  ns/op
SymbolCacheBenchmark.testResolveKey      1000000   cache  avgt   25  550.714 ± 18.224  ns/op
SymbolCacheBenchmark.testResolveKey      1000000     chm  avgt   25  675.672 ± 22.866  ns/op

# JMH version: 1.37
# VM version: JDK 21.0.2, OpenJDK 64-Bit Server VM, 21.0.2+13-LTS
# Processor	Intel(R) Core(TM) i5-10300H CPU @ 2.50GHz, 2496 Mhz, 4 Core(s), 8 Logical Processor(s)

Benchmark                            (cacheSize)  (type)  Mode  Cnt     Score    Error  Units
SymbolCacheBenchmark.testAcquire          100000   cache  avgt   25   231.401 ±  5.618  ns/op
SymbolCacheBenchmark.testAcquire          100000     chm  avgt   25   342.121 ±  9.289  ns/op
SymbolCacheBenchmark.testAcquire         1000000   cache  avgt   25   715.936 ± 14.796  ns/op
SymbolCacheBenchmark.testAcquire         1000000     chm  avgt   25   878.721 ± 23.414  ns/op
SymbolCacheBenchmark.testResolve          100000   cache  avgt   25   253.607 ± 12.062  ns/op
SymbolCacheBenchmark.testResolve          100000     chm  avgt   25   386.077 ±  9.291  ns/op
SymbolCacheBenchmark.testResolve         1000000   cache  avgt   25   775.974 ± 19.076  ns/op
SymbolCacheBenchmark.testResolve         1000000     chm  avgt   25   873.391 ± 21.528  ns/op
SymbolCacheBenchmark.testResolveKey       100000   cache  avgt   25   599.089 ± 89.877  ns/op
SymbolCacheBenchmark.testResolveKey       100000     chm  avgt   25   673.146 ± 54.349  ns/op
SymbolCacheBenchmark.testResolveKey      1000000   cache  avgt   25  1158.625 ± 19.107  ns/op
SymbolCacheBenchmark.testResolveKey      1000000     chm  avgt   25  1457.712 ± 15.141  ns/op

Note, that testResolveKey for chm generates a lot of garbage

 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class SymbolCacheBenchmark {

    public static final int CONCURRENCY = 16;

    @Param({ "100000", "1000000" })
    private int cacheSize;

    @Param({ "cache", "chm" })
    private String type;

    private List<String> SYMBOLS;
    private List<char[]> CHARS;

    private BenchmarkSymbolCache cache;

    @State(Scope.Thread)
    public static class Counter {
        private int i = 0;

        public int next(int cacheSize) {
            return i++ % cacheSize;
        }
    }

    @Setup
    public void setUp() {
        SYMBOLS = IntStream.range(0, cacheSize)
            .mapToObj(String::valueOf)
            .collect(Collectors.toList());
        CHARS = SYMBOLS.stream()
            .map(SymbolCacheBenchmark::toKey)
            .collect(Collectors.toList());

        Collections.shuffle(SYMBOLS, new Random(1));
        Collections.shuffle(CHARS, new Random(1));

        switch (type) {
            case "cache":
                cache = new BenchmarkSymbolCache.OriginalSymbolCache(
                    SymbolCache.newBuilder().withInitialCapacity(cacheSize).withTtl(1_000_000L));
                break;
            case "chm":
                cache = new ChmSymbolCache(cacheSize, Clock.systemUTC(), 1_000_000L);
                break;
        }

        // Fill up caches with symbols
        cache.clear();
        for (String symbol : SYMBOLS) {
            cache.resolve(symbol);
        }
    }

    @Benchmark
    @Threads(CONCURRENCY)
    public String testResolveKey(Counter c) {
        return cache.resolveKey(CHARS.get(c.next(cacheSize)));
    }

    @Benchmark
    @Threads(CONCURRENCY)
    public String testResolve(Counter c) {
        return cache.resolve(SYMBOLS.get(c.next(cacheSize)));
    }

    @Benchmark
    @Threads(CONCURRENCY)
    public String testAcquire(Counter c) {
        return cache.resolveAndAcquire(SYMBOLS.get(c.next(cacheSize)));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(SymbolCacheBenchmark.class.getSimpleName())
            //.addProfiler("gc")
            .build();
        new Runner(opt).run();
    }

    private static char[] toKey(String symbol) {
        int length = symbol.length();
        char[] key = new char[length + SymbolCache.KEY_HEADER_SIZE];
        System.arraycopy(symbol.toCharArray(), 0, key, SymbolCache.KEY_HEADER_SIZE, length);
        return SymbolCache.embedKey(key, SymbolCache.KEY_HEADER_SIZE, length);
    }
}
