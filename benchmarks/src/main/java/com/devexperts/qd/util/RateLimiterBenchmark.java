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
package com.devexperts.qd.util;

import com.devexperts.util.TimePeriod;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

//Benchmark                                       Mode  Cnt     Score     Error   Units
//RateLimiterBenchmark.agentScenario             thrpt    5   407.252 ±   6.682  ops/us
//RateLimiterBenchmark.full                      thrpt    5    43.050 ±   0.745  ops/us
//RateLimiterBenchmark.fullDouble                thrpt    5    33.848 ±   1.000  ops/us
//RateLimiterBenchmark.fullUnlimited             thrpt    5  1506.493 ± 344.850  ops/us
//RateLimiterBenchmark.netTestConsumerScenario   thrpt    5   354.526 ±  20.900  ops/us
//RateLimiterBenchmark.netTestPublisherScenario  thrpt    5    61.667 ±   0.777  ops/us

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class RateLimiterBenchmark {

    private RateLimiter fullUnlimited;
    private TestRateLimiter full;
    private TestRateLimiter netTestConsumerScenario;
    private TestRateLimiter netTestPublisherScenario;
    private TestRateLimiter agentScenario;
    private TestRateLimiter fullDouble;

    @Setup(Level.Trial)
    public void setup() {
        fullUnlimited = new TestRateLimiterUnlimited();
        full = new TestRateLimiter(1_000_000, TimePeriod.valueOf("1s"));
        netTestConsumerScenario = new TestRateLimiter(1_000_000, TimePeriod.valueOf("1s"));
        netTestPublisherScenario = new TestRateLimiter(1_000_000, TimePeriod.valueOf("1s"));
        agentScenario = new TestRateLimiter(1_000_000, TimePeriod.valueOf("1s"));
        fullDouble = new TestRateLimiter(Long.MAX_VALUE, TimePeriod.valueOf("1s"));
    }

    @Benchmark
    public void full() {
        full.tic_ms();
        full.reportConsumed(2L * full.available());
        full.parkNanos(full.waitTime() * 1_000_000);
    }

    @Benchmark
    public void fullDouble() {
        fullDouble.tic_ms();
        fullDouble.reportConsumed(2L * fullDouble.available());
        fullDouble.parkNanos(fullDouble.waitTime() * 1_000_000);
    }

    @Benchmark
    public void fullUnlimited() {
        fullUnlimited.reportConsumed(2L * fullUnlimited.available());
        fullUnlimited.parkNanos(fullUnlimited.waitTime() * 1_000_000);
    }

    @Benchmark
    public void netTestConsumerScenario() {
        netTestConsumerScenario.parkNanos(netTestConsumerScenario.waitTime() * 1_000_000);
        netTestConsumerScenario.reportConsumed(1L);
    }

    @Benchmark
    public void netTestPublisherScenario() throws InterruptedException {
        netTestPublisherScenario.tic_ms();
        netTestPublisherScenario.reportConsumed(netTestPublisherScenario.availableOrWait());
    }

    @Benchmark
    public void agentScenario() {
        agentScenario.tic_ms();
        agentScenario.reportConsumed(agentScenario.available());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(RateLimiterBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }

    private static class TestRateLimiterUnlimited extends RateLimiter {
        public long nanoTime = 0L;

        public TestRateLimiterUnlimited() {
            super(calculateBucketSize(Long.MAX_VALUE, TimePeriod.valueOf("1s")), TimePeriod.valueOf("1s").getNanos(),
                calculateConfig(calculateBucketSize(Long.MAX_VALUE, TimePeriod.valueOf("1s")),
                TimePeriod.valueOf("1s").getNanos()));
        }

        @Override
        public long waitTime() {
            return 0L;
        }

        @Override
        public void reportConsumed(long tokens) {
            // No operation
        }

        @Override
        public void consume(long tokens) {
            // No operation
        }

        @Override
        public long availableOrWait() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean needWait() {
            return false;
        }

        @Override
        public long available() {
            return Long.MAX_VALUE;
        }

        @Override
        public String toString() {
            return CONFIG_UNLIMITED;
        }

        @Override
        public long getCurrentNanoTime() {
            return nanoTime;
        }

        @Override
        public void parkNanos(long nanos) {
            nanoTime += nanos;
        }
    }

    private static class TestRateLimiter extends RateLimiter {
        private static final int SIZE = 128;
        private final byte[] randomNumbers;
        private byte index = 0;
        private long nanoTime = 0;

        {
            Random random = new Random(31);
            randomNumbers = new byte[SIZE];
            for (int i = 0; i < SIZE; i++) {
                randomNumbers[i] = (byte) random.nextInt(256);
            }
        }

        public TestRateLimiter(long rateLimit, TimePeriod amortizationLimit) {
            super(calculateBucketSize(rateLimit, amortizationLimit), amortizationLimit.getNanos(),
                calculateConfig(calculateBucketSize(rateLimit, amortizationLimit), amortizationLimit.getNanos()));
        }

        @Override
        public long getCurrentNanoTime() {
            return nanoTime;
        }

        @Override
        public void parkNanos(long nanos) {
            nanoTime += nanos;
        }

        public void tic_ns() {
            nanoTime += randomNumbers[index++ & 0x7F];
        }

        public void tic_us() {
            nanoTime += randomNumbers[index++ & 0x7F] * 1_000;
        }

        public void tic_ms() {
            nanoTime += randomNumbers[index++ & 0x7F] * 1_000_000;
        }
    }
}
