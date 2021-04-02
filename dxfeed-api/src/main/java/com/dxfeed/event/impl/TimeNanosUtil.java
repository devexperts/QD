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
package com.dxfeed.event.impl;

/**
 * A collection of static utility methods for manipulation of Java time in nanoseconds since Java epoch.
 */
public class TimeNanosUtil {
    /**
     * Number of nanoseconds in millisecond.
     */
    private static final long NANOS_IN_MILLIS = 1_000_000L;

    private TimeNanosUtil() {} // do not create this class

    /**
     * Returns time measured in nanoseconds since Java epoch from the time in milliseconds and its nano part.
     * The result of this method is {@code timeMillis * 1_000_000 + timeNanoPart}.
     * @param timeMillis time in milliseconds since Java epoch.
     * @param timeNanoPart nanoseconds part that shall lie within [0..999999] interval.
     * @return time measured in nanoseconds since Java epoch.
     */
    public static long getNanosFromMillisAndNanoPart(long timeMillis, int timeNanoPart) {
        return timeMillis * NANOS_IN_MILLIS + timeNanoPart;
    }

    /**
     * Returns time measured in milliseconds since Java epoch from the time in nanoseconds.
     * Idea is that nano part of time shall be within [0..999999] interval
     * so that the following equation always holds
     * {@code getMillisFromNanos(timeNanos) * 1_000_000 + getNanoPartFromNanos(timeNanos) == timeNanos}.
     * @param timeNanos time measured in nanoseconds since Java epoch
     * @return time measured in milliseconds since Java epoch.
     * @see #getNanoPartFromNanos(long)
     */
    public static long getMillisFromNanos(long timeNanos) {
        return Math.floorDiv(timeNanos, NANOS_IN_MILLIS);
    }

    /**
     * Returns nano part of time.
     * Idea is that nano part of time shall be within [0..999999] interval
     * so that the following equation always holds
     * {@code getMillisFromNanos(timeNanos) * 1_000_000 + getNanoPartFromNanos(timeNanos) == timeNanos}.
     * @param timeNanos time measured in nanoseconds since Java epoch
     * @return time measured in milliseconds since Java epoch.
     * @see #getMillisFromNanos(long)
     */
    public static int getNanoPartFromNanos(long timeNanos) {
        return (int) Math.floorMod(timeNanos, NANOS_IN_MILLIS);
    }

}
