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
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.kit.RangeStriper;
import com.dxfeed.api.impl.DXFeedScheme;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmark that runs stripers on the group of 10000 profiles for different symbol groups
 * (bs, fut, futopt, opt, spread) that are available on the classpath.
 */
@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RangeStringStriperGroupBenchmark {

    public static final int ITERATIONS = 10_000;

    public static int [] ciphers;
    public static String[] symbols;
    public static final DataScheme scheme = DXFeedScheme.getInstance();

    @Param({
        "code1",
        "code2",
        "code3",
        "code4",
    })
    public String striperSpec;

    @Param({
        "bs",
        "fut",
        "futopt",
        "opt",
        "spread",
    })
    public String group;

    public SymbolStriper striper;

    @Setup
    public void setup() {
        List<String> symbols1 = Collections.emptyList();
        try {
            URL url = this.getClass().getResource("/" + group + ".csv");
            if (url == null)
                throw new IOException("Cannot find resource file for " + group);
            symbols1 = Files.readAllLines(Paths.get(url.toURI()));
        } catch (IOException | URISyntaxException e) {
            System.err.println("Error reading instruments from " + group + ".csv");
            e.printStackTrace(System.err);
            System.exit(-1);
        }

        if (symbols1.size() != ITERATIONS) {
            System.exit(-1);
        }

        ciphers = new int[ITERATIONS];
        symbols = new String[ITERATIONS];

        SymbolCodec codec = scheme.getCodec();
        for (int i = 0; i < ITERATIONS; i++) {
            symbols[i] = symbols1.get(i);
            ciphers[i] = codec.encode(symbols[i]);
        }

        // Create striper spec from formula:
        // code1 == "byrange-A-B-...-Z-"
        // code2 == "byrange-AA-BB-...-ZZ-"
        // etc
        List<String> ranges = generateRanges(striperSpec.charAt(4) - '0');

        striper = RangeStriper.valueOf(scheme, ranges);
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void rangeFilterGroup(Blackhole blackhole) {
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(striper.getStripeIndex(ciphers[i], ciphers[i] == 0 ? symbols[i] : null));
        }
    }

    private static List<String> generateRanges(int count) {
        return IntStream.range(0, 26)
            .mapToObj(i -> String.join("", Collections.nCopies(count, String.valueOf((char) ('A' + i)))))
            .collect(Collectors.toList());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RangeStringStriperGroupBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
