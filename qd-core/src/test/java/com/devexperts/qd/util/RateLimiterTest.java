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
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RateLimiterTest {

    private static final long NANOS_PER_MICROSECOND = TimeUnit.MICROSECONDS.toNanos(1);
    private static final long NANOS_PER_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long START_TIME = 0L; // arbitrary start time
    private static final Field fieldRefillPeriodNanos;
    private static final Field fieldBucketSize;

    static {
        try {
            fieldRefillPeriodNanos = RateLimiter.class.getDeclaredField("refillPeriodNanos");
            fieldRefillPeriodNanos.setAccessible(true);
            fieldBucketSize = RateLimiter.class.getDeclaredField("bucketSize");
            fieldBucketSize.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private RateLimiter rateLimiter;
    private long currentNanos;

    /**
     * Testable RateLimiter that allows controlling time.
     */
    private class TestableRateLimiter extends RateLimiter {
        private final boolean interrupt;

        TestableRateLimiter(long rateLimit) {
            this(rateLimit, TimePeriod.valueOf("1s"), false);
        }

        TestableRateLimiter(long rateLimit, TimePeriod amortizationLimit) {
            this(rateLimit, amortizationLimit, false);
        }

        TestableRateLimiter(long rateLimit, TimePeriod amortizationLimit, boolean interrupt) {
            super(calculateBucketSize(rateLimit, amortizationLimit), amortizationLimit.getNanos(),
                calculateConfig(calculateBucketSize(rateLimit, amortizationLimit), amortizationLimit.getNanos()));
            this.interrupt = interrupt;
        }

        @Override
        long getCurrentNanoTime() {
            return currentNanos;
        }

        @Override
        void parkNanos(long nanos) {
            if (interrupt) {
                Thread.currentThread().interrupt();
            } else {
                currentNanos += nanos;
            }
        }
    }

    @Before
    public void setUp() {
        currentNanos = START_TIME;
    }

    @Test
    public void testAvailableOrWaitFirstBasicFlow() throws InterruptedException {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token
        assertState(1L, 0);

        long tokens = rateLimiter.availableOrWait();
        assertState(0L, 1);

        rateLimiter.reportConsumed(tokens);
        assertState(1L, 0);
    }

    @Test
    public void testAvailableOrWaitSecondBasicFlow() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token
        assertState(1L, 0);

        rateLimiter.parkNanos(NANOS_PER_MILLISECOND);
        assertState(0L, 1);

        long available = rateLimiter.available();
        rateLimiter.reportConsumed(available);
        assertState(1L, 0);
    }

    @Test
    public void testTokenRefillAndTimeAdvancement() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token
        assertState(1L, 0);

        // Consume one token
        rateLimiter.reportConsumed(1);
        assertState(2L, 0);

        // Advance time by half the required period
        currentNanos += 1_000_000L; // 1 ms
        assertState(1L, 0);

        // Advance time to exactly when token becomes available
        currentNanos += 1_000_000L; // another 1 ms = total 2 ms
        assertState(0L, 1);

        // elapsedNanos >= maximumElapsedNanos
        currentNanos += Long.MAX_VALUE / 2;
        assertState(0L, 1000);

        rateLimiter.reportConsumed(1);
        assertState(0L, 999);
    }

    @Test
    public void testMaximumElapsedTimeAndOverflowHandling() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token

        currentNanos += 2_000_000_000; // more than max bucket
        rateLimiter.reportConsumed(1);
        assertEquals(0L, rateLimiter.waitTime());

        currentNanos += Long.MAX_VALUE / 1000; // elapsedNanos >= MAX_NANOS
        rateLimiter.reportConsumed(1);
        assertEquals(0L, rateLimiter.waitTime());

        currentNanos += 5000; // full bucket
        rateLimiter.reportConsumed(1);
        assertEquals(0L, rateLimiter.waitTime());

        // overflowSubtract newBucketSize
        rateLimiter.reportConsumed(Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, rateLimiter.waitTime());
    }

    @Test
    public void testLargeTokenConsumptionWithoutOverflowBucketSize() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token

        currentNanos += 2_000_000_000_000L; // more than max bucket
        rateLimiter.reportConsumed(2_000_000);

        rateLimiter.reportConsumed(Long.MAX_VALUE - 1_000_000L);
        assertState(Long.MAX_VALUE, 0);
    }

    @Test
    public void testLargeTokenConsumptionWithOverflowBucketSize() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token

        currentNanos += 2_000_000_000_000L; // more than max bucket
        rateLimiter.reportConsumed(2_000_000);

        currentNanos += 2_000_000_000_000L;
        rateLimiter.reportConsumed(Long.MAX_VALUE - 1_000_000L);
        assertState(Long.MAX_VALUE, 0);
    }

    @Test
    public void testOverflowBucketSize() {
        rateLimiter = new TestableRateLimiter(Long.MAX_VALUE);

        currentNanos += 500_000_000L;
        rateLimiter.reportConsumed(Long.MAX_VALUE);
        currentNanos += 500_000_000L;
        rateLimiter.reportConsumed(Long.MAX_VALUE);
        currentNanos += 500_000_000L;
        rateLimiter.reportConsumed(Long.MAX_VALUE);

        assertState(1000, 0);
    }

    @Test
    public void testWaitNanoTimeLessThenOneToken() {
        rateLimiter = new TestableRateLimiter(10_000_000_000L);
        assertEquals(1L, rateLimiter.waitTime());
    }

    @Test
    public void testMaxRateAndDouble() {
        rateLimiter = new TestableRateLimiter(1_000_000);

        rateLimiter.parkNanos(NANOS_PER_MILLISECOND);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        rateLimiter.parkNanos(NANOS_PER_MILLISECOND);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        assertEquals(0, rateLimiter.waitTime());
    }

    @Test
    public void testHighRateOverdraftWithLongTimePeriods() {
        rateLimiter = new TestableRateLimiter(1_000_000);

        rateLimiter.parkNanos(NANOS_PER_MICROSECOND);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        rateLimiter.parkNanos(NANOS_PER_MICROSECOND);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        rateLimiter.parkNanos(NANOS_PER_MICROSECOND);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        rateLimiter.parkNanos(120_000_000_000L);
        rateLimiter.reportConsumed(2L * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);

        rateLimiter.parkNanos(120_000_000_000L);
        rateLimiter.reportConsumed(2 * rateLimiter.available());
        rateLimiter.parkNanos(rateLimiter.waitTime() * 1_000_000);
    }

    @Test
    public void testBucketCapacity() {
        // Small bucket size to test capacity limits
        rateLimiter = new TestableRateLimiter(1000, TimePeriod.valueOf("0.003s")); // bucket size = 3

        // Should have 0 tokens available initially (bucket fills over time)
        assertEquals(0, rateLimiter.available());

        // Let time pass to exceed bucket capacity
        currentNanos += NANOS_PER_SECOND; // 1 second = 1000 tokens worth of time

        // Should still be limited by bucket capacity
        assertEquals(3, rateLimiter.available());
    }

    @Test
    public void testMultipleTokenConsumptionScaling() {
        rateLimiter = new TestableRateLimiter(1000); // 1 ms per token

        // Consume 5 tokens
        rateLimiter.reportConsumed(5);

        // Should need to wait 5x longer
        assertTrue(rateLimiter.needWait());
        assertEquals(6L, rateLimiter.waitTime()); // 5_000_000 nanos = 5 ms

        // After appropriate time, should be available
        currentNanos += 6_000_000L;
        assertFalse(rateLimiter.needWait());
    }

    @Test
    public void testBlockingConsumeWithOverflowScenarios() throws InterruptedException {
        rateLimiter = new TestableRateLimiter(1000);

        rateLimiter.consume(4000000000000000000L);
        assertState(Long.MAX_VALUE, 0);
    }

    @Test
    public void testConsumeThrow() throws InterruptedException {
        rateLimiter = new TestableRateLimiter(1000, TimePeriod.valueOf("1s"), true);

        // Consume one token to trigger blocking
        rateLimiter.reportConsumed(1);

        long tmp = currentNanos;
        assertThrows("Should throw InterruptedException",
            InterruptedException.class, () -> rateLimiter.consume(1));

        assertEquals(tmp, currentNanos);
    }

    @Test
    public void testAvailableTokensOverTime() {
        rateLimiter = new TestableRateLimiter(1000, TimePeriod.valueOf("0.01s")); // bucket size = 10

        // Initially empty bucket - 0 tokens (bucket fills over time)
        assertEquals(0, rateLimiter.available());
        assertState(1, 0);

        // After 1 ms (1_000_000 nanos), should have 1 token
        long tmp = currentNanos;
        currentNanos = tmp + 1_000_000L;
        assertEquals(1, rateLimiter.available());
        assertState(0, 1);

        // After 10 ms (10_000_000 nanos), should have 10 tokens (bucket full)
        currentNanos = tmp + 10_000_000L;
        assertEquals(10, rateLimiter.available());
        assertState(0, 10);

        // After 1 second, should still have only 10 tokens (bucket capacity limit)
        currentNanos = tmp + NANOS_PER_SECOND;
        assertState(0, 10);
    }

    @Test
    public void testProcessedWithZeroTokens() throws InterruptedException {
        rateLimiter = new TestableRateLimiter(1000);
        assertState(1, 0);

        rateLimiter.consume(0);
        rateLimiter.reportConsumed(0);
        assertState(1, 0);

        currentNanos = NANOS_PER_SECOND;
        rateLimiter.consume(0);
        rateLimiter.reportConsumed(0);
        assertState(0, 1000);
    }

    @Test
    public void testProcessedWithNegativeTokens() {
        rateLimiter = new TestableRateLimiter(1000, TimePeriod.valueOf("100s"));

        assertThrows("Should throw IllegalArgumentException for negative tokens",
            IllegalArgumentException.class, () -> rateLimiter.reportConsumed(-1));
    }

    @Test
    public void testUnlimitedRateLimiter() throws InterruptedException {
        rateLimiter = RateLimiter.UNLIMITED;
        assertState(0L, Long.MAX_VALUE);

        rateLimiter.consume(Long.MAX_VALUE);
        rateLimiter.reportConsumed(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, rateLimiter.availableOrWait());

        assertState(0L, Long.MAX_VALUE);
    }

    @Test
    public void testAvailableOrWaitWhenTokensAvailable() throws InterruptedException {
        rateLimiter = new TestableRateLimiter(1000);
        currentNanos += 5_000_000L; // 5 ms = 5 tokens
        long currentNanoTime = rateLimiter.getCurrentNanoTime();

        long available = rateLimiter.availableOrWait();

        assertEquals(5, available);
        assertEquals(currentNanoTime, rateLimiter.getCurrentNanoTime());
    }

    @Test
    public void testParkNanos() {
        RateLimiter limiter = RateLimiter.valueOf("1000");

        long startTime = System.nanoTime();
        limiter.parkNanos(1_000_000);
        long actualTime = System.nanoTime() - startTime;

        assertTrue(1 < actualTime && actualTime < 1_000_000_000);
    }

    @Test
    public void testConstructorValidation() {
        rateLimiter = RateLimiter.valueOf("1000");
        assertConfig(NANOS_PER_SECOND, 1000);

        rateLimiter = RateLimiter.valueOf("1.1");
        assertConfig(NANOS_PER_SECOND, 1); // rounds down

        rateLimiter = RateLimiter.valueOf("999.9");
        assertConfig(NANOS_PER_SECOND, 999); // rounds down

        rateLimiter = RateLimiter.valueOf("1k");
        assertConfig(NANOS_PER_SECOND, 1000);

        rateLimiter = RateLimiter.valueOf("0.5k");
        assertConfig(NANOS_PER_SECOND, 500);

        rateLimiter = RateLimiter.valueOf("1.5K");
        assertConfig(NANOS_PER_SECOND, 1500);

        rateLimiter = RateLimiter.of(1500);
        assertConfig(NANOS_PER_SECOND, 1500);

        rateLimiter = RateLimiter.of(0.5, TimePeriod.valueOf("2s"));
        assertConfig(2 * NANOS_PER_SECOND, 1);

        rateLimiter = RateLimiter.of(1500, TimePeriod.valueOf(500L));
        assertConfig(NANOS_PER_SECOND / 2, 750);

        rateLimiter = RateLimiter.ofBucket(1500, NANOS_PER_SECOND);
        assertConfig(NANOS_PER_SECOND, 1500);

        rateLimiter = RateLimiter.valueOf("2m");
        assertConfig(NANOS_PER_SECOND, 2000000);

        rateLimiter = RateLimiter.valueOf("12.7M");
        assertConfig(NANOS_PER_SECOND, 12700000, 5);

        rateLimiter = RateLimiter.valueOf("1000;2s");
        assertConfig(2 * NANOS_PER_SECOND, 2000);
        assertEquals(rateLimiter, RateLimiter.valueOf(rateLimiter.toString()));

        rateLimiter = RateLimiter.valueOf("500;3s");
        assertConfig(3 * NANOS_PER_SECOND, 1500);
        assertEquals(rateLimiter, RateLimiter.valueOf(rateLimiter.toString()));

        rateLimiter = RateLimiter.valueOf("2000;0.5s");
        assertConfig(NANOS_PER_SECOND / 2, 1000);

        assertEquals(rateLimiter, RateLimiter.valueOf(rateLimiter.toString()));
        assertEquals(rateLimiter.hashCode(), RateLimiter.valueOf(rateLimiter.toString()).hashCode());
        assertNotEquals(RateLimiter.UNLIMITED, rateLimiter);
        assertFalse(rateLimiter.equals(new Object()));

        assertEquals(RateLimiter.UNLIMITED, RateLimiter.valueOf(""));
        assertEquals(RateLimiter.UNLIMITED, RateLimiter.valueOf("unlimited"));
        assertEquals(RateLimiter.UNLIMITED, RateLimiter.valueOf("UNLIMITED"));
        assertEquals(RateLimiter.UNLIMITED, RateLimiter.valueOf(null));
        assertEquals(RateLimiter.UNLIMITED, RateLimiter.valueOf(RateLimiter.UNLIMITED.toString()));


        long bucketSize = RateLimiter.calculateBucketSize(Long.MAX_VALUE, TimePeriod.valueOf("1s"));
        long refillPeriodNanos = TimePeriod.valueOf("1s").getNanos();
        rateLimiter = new RateLimiter(bucketSize, refillPeriodNanos,
            RateLimiter.calculateConfig(bucketSize, refillPeriodNanos));
        assertConfig(NANOS_PER_SECOND, Long.MAX_VALUE);

        assertThrows("Should throw IllegalArgumentException for rate limit 0",
            IllegalArgumentException.class, () -> new TestableRateLimiter(0));
        assertThrows("Should throw IllegalArgumentException for negative rate limit",
            IllegalArgumentException.class, () -> new TestableRateLimiter(-1));
        assertThrows("Should throw IllegalArgumentException for zero refill period",
            IllegalArgumentException.class, () -> RateLimiter.ofBucket(1, 0));
        assertThrows("Should throw IllegalArgumentException for negative bucket size",
            IllegalArgumentException.class, () -> RateLimiter.ofBucket(-1, 1));
        assertThrows("Should throw IllegalArgumentException for bucket size to become less than one token",
            IllegalArgumentException.class, () -> RateLimiter.of(0.5));
        assertThrows("Should throw IllegalArgumentException for amortisation limit more then Long.MAX_VALUE",
            IllegalArgumentException.class, () -> RateLimiter.of(Long.MAX_VALUE, TimePeriod.valueOf("1.001S")));
        assertThrows("Should throw IllegalArgumentException for non-numeric rate",
            IllegalArgumentException.class, () -> RateLimiter.valueOf("invalid"));
        assertThrows("Should throw IllegalArgumentException for invalid suffix",
            IllegalArgumentException.class, () -> RateLimiter.valueOf("1000x"));
        assertThrows("Should throw IllegalArgumentException for invalid time period",
            IllegalArgumentException.class, () -> RateLimiter.valueOf("1000;invalid"));
    }

    // Helper methods

    private void assertState(long expectedWaitTime, long expectedAvailable) {
        assertEquals(expectedWaitTime, rateLimiter.waitTime());
        assertEquals(expectedWaitTime > 0, rateLimiter.needWait());
        assertEquals(expectedAvailable, rateLimiter.available());
    }

    private void assertConfig(long expectedRefillPeriodNanos, long expectedBucketSize) {
        assertConfig(expectedRefillPeriodNanos, expectedBucketSize, 0);
    }

    private void assertConfig(long expectedRefillPeriodNanos, long expectedBucketSize, long delta) {
        try {
            assertEquals(expectedRefillPeriodNanos, fieldRefillPeriodNanos.getLong(rateLimiter), delta);
            assertEquals(expectedBucketSize, fieldBucketSize.getLong(rateLimiter), delta);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
