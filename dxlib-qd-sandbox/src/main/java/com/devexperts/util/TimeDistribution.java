/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import com.devexperts.monitoring.Monitored;

/**
 * Distribution measurement for processing times, lock wait/held times, etc.
 * Time measurements are added to this class via {@link #addMeasurement(long) addMeasurement} method.
 * This class keeps time distribution with the following precisions:
 * <ul>
 *     <li>{@link Precision#HIGH HIGH} precision mode
 *         provides at least 1% precision for measurements up to 10 minutes and occupies around 18Kb of memory
 *         per instance;
 *     <li>{@link Precision#LOW LOW} precision mode
 *         provides at least 7% precision for measurements up to 10 seconds and occupies around 2Kb of memory
 *         per instance.
 * </ul>
 *
 * <p>The primary interface for this class is its {@link #toString()} method that returns nicely formatted and
 * concise description of distribution for all tracked intervals. Additional method to retrieve various
 * statistics are available.
 *
 * <h3>Thread-safety</h3>
 *
 * <p>This class is thread-safe with the following limitations.
 *
 * <p>{@link #copyFrom(TimeDistribution) copyFrom} and
 * {@link #copyFromDiff(TimeDistribution, TimeDistribution) copyFromDiff}
 * are not atomic and should not be called concurrently with each other or with other methods on an instance of
 * time distribution.
 *
 * <p>Reporting methods (like {@link #toString()}, {@link #getAverageNanos()}, etc)
 * that are called concurrently with {@link #addMeasurement(long) addMeasurement} may return inconsistent results.
 * When time distribution is continuously modified by {@link #addMeasurement(long) addMeasurement} method invocations,
 * create a copy of it with a {@link #TimeDistribution(TimeDistribution) copy constructor} or
 * {@link #copyFrom(TimeDistribution) copyFrom} method, and then use the resulting copy
 * (that is not being modified) with the reporting methods.
 */
public class TimeDistribution {
    private static final long US = 1000L;
    private static final long MS = 1000 * US;
    private static final long SEC = 1000 * MS;
    private static final long MIN = 60 * SEC;

    public enum Precision {
        /**
         * Provides at least 1% precision for measurements up to 10 minutes and occupies around 18Kb of memory
         * per instance of {@link TimeDistribution}.
         */
        HIGH(6, 10 * MIN),

        /**
         * Provides at least 7% precision for measurements up to 10 seconds and occupies around 2Kb of memory
         * per instance of {@link TimeDistribution}.
         */
        LOW(3, 10 * SEC);

        final int nBits;
        final int stripe;
        final int power1Base;

        final int bigCountIndex;
        final int bigSumSecsIndex;
        final int nTotal;

        Precision(int nBits, long bigLimit) {
            this.nBits = nBits;
            this.stripe = 1 << nBits;
            this.power1Base = 63 - nBits;
            this.bigCountIndex = getIndexUnlimited(bigLimit) + 1;
            this.bigSumSecsIndex = bigCountIndex + 1;
            this.nTotal = bigSumSecsIndex + 1;
        }

        int getIndexUnlimited(long nanos) {
            if (nanos <= 0)
                return 0;
            int power1 = power1Base - Long.numberOfLeadingZeros(nanos);
            if (power1 < 0)
                return (int) nanos;
            return (power1 << nBits) + (int) (nanos >> power1);
        }
    }

    private final Precision p;
    private final long[] counts;

    /**
     * Creates empty time distribution with a specified precision.
     * @param precision The required precision.
     */
    public TimeDistribution(Precision precision) {
        p = precision;
        counts = new long[p.nTotal];
    }

    /**
     * Creates time distribution as a copy of {@code other} distribution.
     */
    public TimeDistribution(TimeDistribution other) {
        this(other.p);
        copyFrom(other);
    }

    /**
     * Creates time distribution as a difference between {@code cur} and {@code old} distributions.
     */
    public TimeDistribution(TimeDistribution cur, TimeDistribution old) {
        this(cur.p);
        if (cur.p != old.p)
            throw new IllegalArgumentException("Different precision: " + cur.p + " and " + old.p);
        copyFromDiff(cur, old);
    }

    /**
     * Copies {@code other} distribution into this one.
     */
    public void copyFrom(TimeDistribution other) {
        for (int i = 0; i < p.nTotal; i++)
            AtomicArrays.INSTANCE.setVolatileLong(counts, i,
                AtomicArrays.INSTANCE.getVolatileLong(other.counts, i));
    }

    /**
     * Copies a difference between {@code cur} and {@code old} distributions into this one.
     */
    public void copyFromDiff(TimeDistribution cur, TimeDistribution old) {
        for (int i = 0; i < p.nTotal; i++)
            AtomicArrays.INSTANCE.setVolatileLong(counts, i,
                AtomicArrays.INSTANCE.getVolatileLong(cur.counts, i) -
                AtomicArrays.INSTANCE.getVolatileLong(old.counts, i));
    }

    /**
     * Adds measurements from other time distribution to this one.
     */
    public void add(TimeDistribution other) {
        for (int i = 0; i < p.nTotal; i++)
            AtomicArrays.INSTANCE.addAndGetLong(counts, i,
                AtomicArrays.INSTANCE.getVolatileLong(other.counts, i));
    }

    /**
     * Returns a total number of time intervals represented in this distribution.
     * It is equal to the number of times {@link #addMeasurement(long) addMeasurement} method was called on
     * an empty distribution.
     */
    @Monitored(name = "count", description = "Number of measurements")
    public long getCount() {
        long count = 0;
        for (int i = 0; i <= p.bigCountIndex; i++)
            count += AtomicArrays.INSTANCE.getVolatileLong(counts, i);
        return count;
    }

    private double getNanosAt(int i, double fraction) {
        if (i < p.bigCountIndex) {
            int power = i >> p.nBits;
            int offset = i & (p.stripe - 1);
            long start;
            long delta;
            if (power == 0) {
                start = offset;
                delta = 1;
            } else {
                start = (long) (p.stripe + offset) << (power - 1);
                delta = 1L << (power - 1);
            }
            return start + delta * fraction;
        }
        // big nanos here
        if (fraction == 0.0)
            return getNanosAt(p.bigCountIndex - 1, 1.0); // min is max precisely tracked
        if (fraction == 1.0)
            return Double.POSITIVE_INFINITY; // max is unknown
        // everything else is average
        long c = AtomicArrays.INSTANCE.getVolatileLong(counts, p.bigCountIndex);
        return c > 0 ? (long) (((double) AtomicArrays.INSTANCE.getVolatileLong(counts, p.bigSumSecsIndex) / c) * SEC) : 0;
    }

    /**
     * Computes percentile for a specified fraction of measurements from 0.0 (0%) to 1.0 (100%) in nanoseconds.
     */
    public long computePercentileNanos(double fraction) {
        if (fraction < 0 || fraction > 1)
            throw new IllegalArgumentException();
        double expectCount = getCount() * fraction;
        long count = 0;
        int maxI = 0;
        for (int i = 0; i <= p.bigCountIndex; i++) {
            long c = AtomicArrays.INSTANCE.getVolatileLong(counts, i);
            if (c > 0) {
                if (count + c > expectCount)
                    return (long) getNanosAt(i, (expectCount - count) / c);
                count += c;
                maxI = i;
            }
        }
        return (long) getNanosAt(maxI, 1);
    }

    /**
     * Returns sum measurement in nanoseconds (subject to counters precision).
     */
    public double getSumNanos() {
        double sum = 0;
        for (int i = 0; i <= p.bigCountIndex; i++) {
            long c = AtomicArrays.INSTANCE.getVolatileLong(counts, i);
            if (c > 0)
                sum += c * getNanosAt(i, 0.5);
        }
        return sum;
    }

    /**
     * Returns average measurement in nanoseconds.
     */
    @Monitored(name = "avg" , description = "Average measurement in nanoseconds")
    public long getAverageNanos() {
        long count = 0;
        double sum = 0;
        for (int i = 0; i <= p.bigCountIndex; i++) {
            long c = AtomicArrays.INSTANCE.getVolatileLong(counts, i);
            if (c > 0) {
                count += c;
                sum += c * getNanosAt(i, 0.5);
            }
        }
        return (long) (sum / count);
    }

    /**
     * Returns minimum measurement in nanoseconds.
     * It is the same as {@link #computePercentileNanos(double) computePercentileNanos(0.0)}.
     */
    @Monitored(name = "min" , description = "Minimum measurement in nanoseconds")
    public long getMinNanos() {
        return computePercentileNanos(0.0);
    }

    /**
     * Returns 10th percentile of measurements in nanoseconds.
     * It is the same as {@link #computePercentileNanos(double) computePercentileNanos(0.1)}.
     */
    @Monitored(name = "lower" , description = "10th percentile of measurements in nanoseconds")
    public long getLowerNanos() {
        return computePercentileNanos(0.1);
    }

    /**
     * Returns median of measurements in nanoseconds.
     * It is the same as {@link #computePercentileNanos(double) computePercentileNanos(0.5)}.
     */
    @Monitored(name = "median" , description = "Median of measurements in nanoseconds")
    public long getMedianNanos() {
        return computePercentileNanos(0.5);
    }

    /**
     * Returns 90th percentile of measurements in nanoseconds.
     * It is the same as {@link #computePercentileNanos(double) computePercentileNanos(0.9)}.
     */
    @Monitored(name = "upper" , description = "90th percentile of measurements in nanoseconds")
    public long getUpperNanos() {
        return computePercentileNanos(0.9);
    }

    /**
     * Returns maximum measurement in nanoseconds.
     * It is the same as {@link #computePercentileNanos(double) computePercentileNanos(1.0)}.
     */
    @Monitored(name = "max" , description = "Maximum measurement in nanoseconds")
    public long getMaxNanos() {
        return computePercentileNanos(1.0);
    }

    /**
     * Adds time measurement to this distribution.
     * @param nanos measured time in nanoseconds.
     */
    public void addMeasurement(long nanos) {
        int index = p.getIndexUnlimited(nanos);
        if (index < p.bigCountIndex) {
            AtomicArrays.INSTANCE.addAndGetLong(counts, index, 1);
        } else {
            AtomicArrays.INSTANCE.addAndGetLong(counts, p.bigCountIndex, 1);
            AtomicArrays.INSTANCE.addAndGetLong(counts, p.bigSumSecsIndex, (nanos + SEC/2) / SEC);
        }
    }

    /**
     * Returns string representation of this time distribution.
     * The {@link #getCount() number} of measurements,
     * {@link #getAverageNanos() average},
     * {@link #getMinNanos() minimum},
     * {@link #getLowerNanos() 10th percentile},
     * {@link #getMedianNanos() median},
     * {@link #getUpperNanos() 90th percentile}, and
     * {@link #getMaxNanos() maximum} are reported.
     */
    public String toString() {
        long c = getCount();
        return c == 0 ? "0" : c + ", avg=" + formatNanos(getAverageNanos()) + ";" +
            " min=" + formatNanos(getMinNanos()) +
            " [" + formatNanos(getLowerNanos()) + " - " + formatNanos(getMedianNanos()) + " - " + formatNanos(getUpperNanos()) + "]" +
            " max=" + formatNanos(getMaxNanos());
    }

    /**
     * Formats time period in nanoseconds in the same format as in {@link #toString()}
     * taking into account limited precision of time distribution.
     * @param nanos time period in nanoseconds.
     * @return string representation of time period.
     */
    public static String formatNanos(long nanos) {
        if (Double.isNaN(nanos))
            return "NaN";
        if (nanos == Long.MAX_VALUE)
            return "inf";
        if (nanos == Long.MIN_VALUE)
            return "-inf";
        String s = "";
        if (nanos < 0) { // just in case(!)
            s = "-";
            nanos = -nanos;
        }
        if (nanos >= MIN)
            return s + fmt((double) nanos / MIN) + "min";
        if (nanos >= SEC)
            return s + fmt((double) nanos / SEC) + "s";
        if (nanos >= MS)
            return s + fmt((double) nanos / MS) + "ms";
        if (nanos >= US)
            return s + fmt((double) nanos / US) + "us";
        return s + nanos + "ns";
    }

    private static String fmt(double x) {
        if (x >= 100)
            x = Math.floor(x + 0.5);
        else if (x >= 10)
            x = Math.floor(x * 10 + 0.5) / 10;
        else
            x = Math.floor(x * 100 + 0.5) / 100;
        if (x == (int) x)
            return Integer.toString((int) x);
        return Double.toString(x);
    }
}
