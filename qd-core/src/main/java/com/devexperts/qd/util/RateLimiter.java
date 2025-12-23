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

import com.devexperts.annotation.Internal;
import com.devexperts.util.ConfigUtil;
import com.devexperts.util.TimePeriod;

import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token bucket rate limiter implementation for controlling throughput.
 * <p>
 * This implementation uses a token bucket algorithm to control the rate of operations:
 * <ul>
 * <li>Tokens are added to the bucket at a fixed rate (the configured rate limit)</li>
 * <li>Each operation consumes one or more tokens from the bucket</li>
 * <li>Operations are blocked when insufficient tokens are available</li>
 * <li>The bucket has a maximum capacity, allowing for controlled bursts</li>
 * </ul>
 * <p>
 * The bucket capacity is determined by the amortization limit parameter, which defines
 * how much "credit" can be accumulated during idle periods. This allows the system to
 * compensate for pauses by temporarily working at a maximum rate, up to the bucket capacity.
 * <p>
 * Example: With a rate of 100 tokens/sec and 2s amortization limit:
 * <ul>
 * <li>Tokens are added at 100/sec continuously</li>
 * <li>The bucket can hold up to 200 tokens (100 tokens/sec × 2 seconds)</li>
 * <li>After a 2-second idle period, 200 operations can be performed immediately</li>
 * <li>The long-term average rate cannot exceed 100 operations/sec</li>
 * </ul>
 * <p>
 * <b>Note:</b> This class is not thread-safe.
 */
@Internal
public class RateLimiter {
    public static final String CONFIG_UNLIMITED = "UNLIMITED";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final Pattern CONFIG_PATTERN =
        Pattern.compile("(?<rate>[kmgiKMGI.0-9]+)(;(?<bucket>[hmsHMS.0-9]+))?");
    private static final TimePeriod DEFAULT_AMORTIZATION_PERIOD = TimePeriod.valueOf("1s");

    /**
     * A rate limiter implementation that imposes no limits on operations.
     * This implementation never requires waiting and always reports maximum availability.
     */
    public static final RateLimiter UNLIMITED = new RateLimiter(Long.MAX_VALUE, 1, CONFIG_UNLIMITED) {

        @Override
        public boolean needWait() {
            return false;
        }

        @Override
        public long waitTime() {
            return 0;
        }

        @Override
        public long availableOrWait() {
            return Long.MAX_VALUE;
        }

        @Override
        public long available() {
            return Long.MAX_VALUE;
        }

        @Override
        public void consume(long tokens) {
            // No operation
        }

        @Override
        public void reportConsumed(long tokens) {
            // No operation
        }

        @Override
        public String toString() {
            return CONFIG_UNLIMITED;
        }
    };

    /**
     * Creates a rate limiter from a configuration string.
     * <p>
     * Special cases:
     * <ul>
     * <li>{@code null}, empty string, or {@code "UNLIMITED"} (case-insensitive) - returns the {@link #UNLIMITED}
     * implementation</li>
     * </ul>
     * <p>
     * Configuration format: {@code <rate>[;<bucket>]}
     * <ul>
     * <li>{@code <rate>} - tokens per second, supports fractional values and unit prefixes (required):
     *     <ul>
     *     <li>SI prefixes (powers of 1000): k (10<sup>3</sup>), M (10<sup>6</sup>), G (10<sup>9</sup>)</li>
     *     <li>IEC prefixes (powers of 1024): Ki (2<sup>10</sup>), Mi (2<sup>20</sup>), Gi (2<sup>30</sup>)</li>
     *     </ul>
     * </li>
     * <li>{@code <bucket>} - bucket time period or amortization limit (optional, default: 1s). This represents
     *     the maximum accumulated pause that the rate limiter can compensate by working at maximum rate.
     * </li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     * <li>{@code "100"} - 100 tokens/sec, 1s bucket (100 tokens)</li>
     * <li>{@code "0.5;2s"} - 0.5 tokens/sec, 2s bucket (one token)</li>
     * <li>{@code "0.5k"} - 500 tokens/sec, 1s bucket (500 tokens)</li>
     * <li>{@code "0.5ki"} - 512 tokens/sec, 1s bucket (512 tokens)</li>
     * <li>{@code "1M"} - 1,000,000 tokens/sec, 1s bucket (1,000,000 tokens)</li>
     * <li>{@code "1Mi"} - 1,048,576 tokens/sec, 1s bucket (1,048,576 tokens)</li>
     * <li>{@code "1000;0.5s"} - 1,000 tokens/sec, 0.5s bucket (500 tokens)</li>
     * </ul>
     *
     * @param config the configuration string (may be null)
     * @return a new rate limiter configured according to the specification,
     *         or {@link #UNLIMITED} for null/empty/"UNLIMITED" strings
     * @throws IllegalArgumentException if the configuration format is invalid
     */
    public static RateLimiter valueOf(String config) {
        if (config == null || config.isEmpty() || CONFIG_UNLIMITED.equalsIgnoreCase(config)) {
            return RateLimiter.UNLIMITED;
        }
        Matcher matcher = CONFIG_PATTERN.matcher(config);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid rateLimiter config: " + config);
        }
        double rate = ConfigUtil.convertStringToObject(double.class, matcher.group("rate"));
        String bucket = matcher.group("bucket");
        TimePeriod amortizationPeriod = (bucket == null) ? DEFAULT_AMORTIZATION_PERIOD : TimePeriod.valueOf(bucket);
        return new RateLimiter(calculateBucketSize(rate, amortizationPeriod), amortizationPeriod.getNanos(), config);
    }

    /**
     * Creates a rate limiter with the specified rate limit and default amortization limit (1 second).
     *
     * @param rate the sustained rate limit in tokens per second
     * @return a new rate limiter with a default amortization limit (1 second)
     * @throws IllegalArgumentException if the rate limit is not positive
     */
    public static RateLimiter of(double rate) {
        long bucketSize = calculateBucketSize(rate, DEFAULT_AMORTIZATION_PERIOD);
        long refillPeriodNanos = DEFAULT_AMORTIZATION_PERIOD.getNanos();
        return new RateLimiter(bucketSize, refillPeriodNanos, calculateConfig(bucketSize, refillPeriodNanos));
    }

    /**
     * Creates a rate limiter with the specified parameters.
     *
     * @param rate the sustained rate limit in tokens per second
     * @param amortizationPeriod the time period for bucket capacity calculation
     * @return a new rate limiter with the specified parameters
     * @throws IllegalArgumentException if any parameter is not positive
     */
    public static RateLimiter of(double rate, TimePeriod amortizationPeriod) {
        long bucketSize = calculateBucketSize(rate, amortizationPeriod);
        long refillPeriodNanos = amortizationPeriod.getNanos();
        return new RateLimiter(bucketSize, refillPeriodNanos, calculateConfig(bucketSize, refillPeriodNanos));
    }

    /**
     * Creates a rate limiter with the specified token bucket parameters.
     *
     * @param bucketSize the maximum number of tokens the bucket can hold (burst capacity)
     * @param refillPeriodNanos the time period in nanoseconds for refilling the bucket to its full capacity
     * @return a new rate limiter with the specified bucket parameters
     * @throws IllegalArgumentException if bucketSize or refillPeriodNanos is not positive
     */
    public static RateLimiter ofBucket(long bucketSize, long refillPeriodNanos) {
        return new RateLimiter(bucketSize, refillPeriodNanos, calculateConfig(bucketSize, refillPeriodNanos));
    }

    static String calculateConfig(long bucketSize, long refillPeriodNanos) {
        return String.format(Locale.US, "%.6f;%.6fs",
            (double) bucketSize / refillPeriodNanos * NANOS_PER_SECOND,
            (double) refillPeriodNanos / NANOS_PER_SECOND);
    }

    static long calculateBucketSize(double rate, TimePeriod amortizationPeriod) {
        double capacityEstimate = (amortizationPeriod.getTime() / 1_000.0) * rate;
        if (capacityEstimate > Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Amortization limit would result in bucket size exceeding Long.MAX_VALUE tokens");
        }
        if (capacityEstimate < 1) {
            throw new IllegalArgumentException(
                "Amortization limit would result in bucket size to become less than one token.");
        }
        return (long) capacityEstimate;
    }

    private final String config;
    private final long bucketSize;
    private final long refillPeriodNanos;
    private final long maximumElapsedNanos;
    private final long minTokensThreshold;

    private long currentBucketSize;
    private long lastRefillBucketNanoTime;

    // package-private for testing
    RateLimiter(long bucketSize, long refillPeriodNanos, String config) {
        if (bucketSize <= 0)
            throw new IllegalArgumentException("Bucket size must be positive");
        if (refillPeriodNanos <= 0)
            throw new IllegalArgumentException("Refill period must be positive");
        this.bucketSize = bucketSize;
        this.refillPeriodNanos = refillPeriodNanos;
        this.config = config;
        currentBucketSize = 0;
        lastRefillBucketNanoTime = getCurrentNanoTime();
        maximumElapsedNanos = Long.MAX_VALUE / this.bucketSize;
        minTokensThreshold = Long.MIN_VALUE / this.refillPeriodNanos;
    }

    /**
     * Checks whether the current thread needs to wait to maintain the rate limit.
     *
     * @return {@code true} if the thread needs to wait, {@code false} otherwise
     */
    public boolean needWait() {
        return available() <= 0;
    }

    /**
     * Returns the number of milliseconds that the caller should wait to maintain the rate limit.
     * This method does not block the current thread.
     *
     * @return the number of milliseconds that the caller should wait, or 0 if no waiting is necessary
     */
    public long waitTime() {
        long waitNanoTime = waitNanoTime(1, getCurrentNanoTime());
        // Round up to milliseconds
        return waitNanoTime == Long.MAX_VALUE ? Long.MAX_VALUE : (waitNanoTime + 999_999) / 1_000_000;
    }

    /**
     * Waits if necessary until at least one token becomes available, then returns the number
     * of tokens available for immediate processing.
     * <p>
     * This method blocks if no tokens are currently available, waiting until the rate limiter
     * refills enough to provide at least one token. Once a token is available, it returns
     * the total number of tokens that can be consumed immediately.
     *
     * @return the number of tokens available for immediate processing (at least 1)
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public long availableOrWait() throws InterruptedException {
        long nanoTime = getCurrentNanoTime();
        long endNanoTime = nanoTime + waitNanoTime(1, nanoTime);
        long afterSleepNanoTime = sleep(nanoTime, endNanoTime);
        // Estimate that available tokens will be at least 1
        return available(afterSleepNanoTime);
    }

    /**
     * Returns the number of tokens available for processing at the specified time.
     *
     * @return the number of tokens available for processing at the specified time
     */
    public long available() {
        long available = available(getCurrentNanoTime());
        return available <= 0 ? 0 : available;
    }

    /**
     * Consumes the specified number of tokens from the rate limiter, blocking if necessary
     * until the tokens become available.
     *
     * @param tokens the number of tokens to consume (must be non-negative)
     * @throws IllegalArgumentException if tokens are negative
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void consume(long tokens) throws InterruptedException {
        checkTokenBounds(tokens);
        long nanoTime = getCurrentNanoTime();
        long endNanoTime = nanoTime + waitNanoTime(tokens, nanoTime);
        nanoTime = sleep(nanoTime, endNanoTime);
        reportConsumed(tokens, nanoTime);
    }

    /**
     * Updates the rate limiter after tokens have been processed.
     *
     * @param tokens the number of tokens that were processed (must be non-negative)
     * @throws IllegalArgumentException if tokens are negative
     */
    public void reportConsumed(long tokens) {
        checkTokenBounds(tokens);
        reportConsumed(tokens, getCurrentNanoTime());
    }

    @Override
    public String toString() {
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RateLimiter))
            return false;
        RateLimiter that = (RateLimiter) o;
        return bucketSize == that.bucketSize && refillPeriodNanos == that.refillPeriodNanos;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(bucketSize) + Long.hashCode(refillPeriodNanos);
    }

    @SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
    private void reportConsumed(long tokens, long nanoTime) {
        long elapsedNanos = nanoTime - lastRefillBucketNanoTime;
        if (elapsedNanos >= maximumElapsedNanos) {
            double available = availableTokens((double) elapsedNanos);
            if (available > bucketSize) {
                lastRefillBucketNanoTime = nanoTime;
                currentBucketSize = bucketSize - tokens;
            } else {
                if (currentBucketSize < Long.MIN_VALUE + tokens) {
                    lastRefillBucketNanoTime = nanoTime;
                    if (available < Long.MIN_VALUE + tokens) {
                        currentBucketSize = Long.MIN_VALUE;
                    } else {
                        currentBucketSize = (long) (available - tokens);
                    }
                } else {
                    currentBucketSize -= tokens;
                }
            }
            return;
        }
        long refillTokens = elapsedNanos * bucketSize / refillPeriodNanos;
        if (currentBucketSize > bucketSize - refillTokens) {
            lastRefillBucketNanoTime = nanoTime;
            currentBucketSize = bucketSize - tokens;
        } else {
            if (currentBucketSize < Long.MIN_VALUE + tokens) {
                lastRefillBucketNanoTime = nanoTime;
                if (currentBucketSize + refillTokens < Long.MIN_VALUE + tokens) {
                    currentBucketSize = Long.MIN_VALUE;
                } else {
                    currentBucketSize += refillTokens - tokens;
                }
            } else {
                currentBucketSize -= tokens;
            }
        }
    }

    @SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
    private long available(long nanoTime) {
        long elapsedNanos = nanoTime - lastRefillBucketNanoTime;
        if (elapsedNanos >= maximumElapsedNanos) {
            return (long) Math.min(bucketSize, availableTokens((double) elapsedNanos));
        }
        return Math.min(bucketSize, availableTokens(elapsedNanos));
    }

    private long waitNanoTime(long tokens, long nanoTime) {
        long available = available(nanoTime);
        if (available < minTokensThreshold + tokens) {
            double nanos =  tokensToNanos((double) available - tokens);
            return nanos <= Long.MIN_VALUE ? Long.MAX_VALUE : (long) -nanos;
        }
        long reserve = available - tokens;
        if (reserve >= 0) {
            return 0;
        }
        long nanos = tokensToNanos(reserve);
        return nanos == 0 ? 1 : -nanos;
    }

    private double availableTokens(double elapsedNanos) {
        return currentBucketSize + elapsedNanos * bucketSize / refillPeriodNanos;
    }

    private long availableTokens(long elapsedNanos) {
        return currentBucketSize + elapsedNanos * bucketSize / refillPeriodNanos;
    }

    private double tokensToNanos(double tokens) {
        return tokens * refillPeriodNanos / bucketSize;
    }

    private long tokensToNanos(long tokens) {
        return tokens * refillPeriodNanos / bucketSize;
    }

    private void checkTokenBounds(long tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("Tokens must be non-negative");
        }
    }

    private long sleep(long nanoTime, long endNanoTime) throws InterruptedException {
        while (endNanoTime - nanoTime > 0) {
            parkNanos(endNanoTime - nanoTime);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            nanoTime = getCurrentNanoTime();
        }
        return nanoTime;
    }

    /**
     * Returns current nano time. Package-private to allow overriding in tests.
     * @return current nano time from System.nanoTime()
     */
    long getCurrentNanoTime() {
        return System.nanoTime();
    }

    /**
     * Parks the current thread for the specified duration.
     * Package-private to allow overriding in tests.
     * @param nanos duration to park in nanoseconds
     */
    void parkNanos(long nanos) {
        LockSupport.parkNanos(nanos);
    }
}
