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
package com.devexperts.qd.util;

import com.devexperts.util.TimeUtil;

/**
 * Conversion methods between long time millis and time sequence. Time sequence is defined as a long value that
 * has time in seconds in bits [63..32] and remaining milliseconds in bits [31..22].
 * Time millis is defined as in {@link System#currentTimeMillis()} method.
 */
public class TimeSequenceUtil {
    private TimeSequenceUtil() {} // do not create

    /**
     * Returns time sequence from time millis. Time sequence is defined as a long value that
     * has time in seconds in bits [63..32] and remaining milliseconds in bits [31..22].
     * Time millis is defined as in {@link System#currentTimeMillis()} method.
     */
    public static long getTimeSequenceFromTimeMillis(long timeMillis) {
        int seconds = TimeUtil.getSecondsFromTime(timeMillis);
        int millis = TimeUtil.getMillisFromTime(timeMillis);
        return getTimeSequenceFromTimeSecondsAndSequence(seconds, millis << 22);

    }

    /**
     * Returns time millis from time sequence. Time sequence is defined as a long value that
     * has time in seconds in bits [63..32] and remaining milliseconds in bits [31..22].
     * Time millis is defined as in {@link System#currentTimeMillis()} method.
     */
    public static long getTimeMillisFromTimeSequence(long timeSequence) {
        int seconds = getTimeSecondsFromTimeSequence(timeSequence);
        int millis = (getSequenceFromTimeSequence(timeSequence) >> 22) & 0x3ff;
        return seconds * TimeUtil.SECOND + millis;
    }

    /**
     * Converts time seconds and sequence ints into a time sequence long.
     * @param timeSeconds time value in seconds.
     * @param sequence sequence value with bits [31..22] containing millis
     * @return time sequence.
     */
    public static long getTimeSequenceFromTimeSecondsAndSequence(int timeSeconds, int sequence) {
        return ((long) timeSeconds << 32) | (sequence & 0xffffffffL);
    }

    /**
     * Returns time in seconds from time sequence value.
     * @param timeSequence time sequence.
     * @return time in seconds.
     */
    public static int getTimeSecondsFromTimeSequence(long timeSequence) {
        return (int) (timeSequence >> 32);
    }

    /**
     * Returns sequence from time sequence value.
     * @param timeSequence time sequence.
     * @return sequence.
     */
    public static int getSequenceFromTimeSequence(long timeSequence) {
        return (int) timeSequence;
    }
}
