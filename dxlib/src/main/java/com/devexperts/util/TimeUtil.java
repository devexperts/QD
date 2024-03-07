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
package com.devexperts.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.TimeZone;

/**
 * A collection of static utility methods for manipulation of Java long time.
 * @see System#currentTimeMillis()
 */
public class TimeUtil {
    private TimeUtil() {} // do not create this class

    /**
     * Number of milliseconds in a second.
     */
    public static final long SECOND = 1000;

    /**
     * Number of milliseconds in a minute.
     */
    public static final long MINUTE = 60 * SECOND;

    /**
     * Number of milliseconds in an hour.
     */
    public static final long HOUR = 60 * MINUTE;

    /**
     * Number of milliseconds in an day.
     */
    public static final long DAY = 24 * HOUR;

    /**
     * Returns correct number of seconds with proper handling negative values and overflows.
     * Idea is that number of milliseconds shall be within [0..999] interval
     * so that the following equation always holds
     * {@code getSecondsFromTime(timeMillis) * 1000L + getMillisFromTime(timeMillis) == timeMillis}
     * as as long the time in seconds fits into <b>int</b>.
     * @see #getMillisFromTime(long)
     */
    public static int getSecondsFromTime(long timeMillis) {
        return timeMillis >= 0 ? (int) Math.min(timeMillis / SECOND, Integer.MAX_VALUE) :
            (int) Math.max((timeMillis + 1) / SECOND - 1, Integer.MIN_VALUE);
    }

    /**
     * Returns correct number of milliseconds with proper handling negative values.
     * Idea is that number of milliseconds shall be within [0..999] interval
     * so that the following equation always holds
     * {@code getSecondsFromTime(timeMillis) * 1000L + getMillisFromTime(timeMillis) == timeMillis}
     * as as long the time in seconds fits into <b>int</b>.
     * @see #getSecondsFromTime(long)
     */
    public static int getMillisFromTime(long timeMillis) {
        return (int) Math.floorMod(timeMillis, SECOND);
    }

    /**
     * Returns GMT time-zone.
     * @return GMT time-zone.
     */
    public static TimeZone getTimeZoneGmt() {
        return TimeZone.getTimeZone("GMT");
    }

    // Allow default Java behavior of TimeZone#getTimeZone(String)
    private static final boolean SKIP_TIME_ZONE_VALIDATION =
        SystemProperties.getBooleanProperty(TimeUtil.class, "skipTimeZoneValidation", false);

    /**
     * Gets the {@code TimeZone} for the given ID similar same as {@link TimeZone#getTimeZone(String)} but
     * throwing {@link IllegalArgumentException} if the time-zone cannot be parsed. Also note that shortened
     * custom IDs are not allowed, e.g. "GMT-8:00" instead of "GMT-08:00" would throw an exception.
     *
     * @param tz the ID for a <code>TimeZone</code>, either an abbreviation
     *     such as "PST", a full name such as "America/Los_Angeles", or a custom ID such as "GMT-08:00".
     *     Note that the support of abbreviations is for compatibility only and full names should be used.
     * @return the specified {@code TimeZone}.
     * @throws IllegalArgumentException if the given ID cannot be understood.
     */
    public static TimeZone getTimeZone(String tz) {
        TimeZone timeZone = TimeZone.getTimeZone(tz);
        if (!SKIP_TIME_ZONE_VALIDATION && !timeZone.getID().equals(tz))
            throw new IllegalArgumentException("Unknown time-zone: " + tz);
        return timeZone;
    }

    /**
     * Calculating the daily time relative to the current timestamp, taking into account the time zone.
     * If the current time is less than {@code localTime} result will be {@code localTime} in current day.
     * If the current time is more or equal than {@code localTime} result will be {@code localTime} in the next day in that time.
     * All results are calculated for the {@code zoneId} time zone.
     * <p>We have two special cases of season time shifting:
     * <ul>
     *   <li>Transition day from summer to winter contains additional hour but result is predictable
     *   and will correspond with time in that timezone.</li>
     *   <li>Transition day from winter to summer has less than 24 hours per day and
     *   if {@code localTime} points to nonexistent time the nearest exist time in future will be chosen</li>
     * </ul>
     *
     * @param currentTime current epoch millis
     * @param localTime desired daily time in LocalTime format
     * @param zoneId time zone for calculating daily time
     * @return the next daily time for the specified timezone in unix format in ms
     */
    public static long computeDailyTime(long currentTime, LocalTime localTime, ZoneId zoneId) {
        LocalDate localDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTime), zoneId).toLocalDate();
        ZoneRules zoneRules = zoneId.getRules();
        long computed = computeDayTime(localDate, localTime, zoneRules);
        if (computed > currentTime) {
            return computed;
        }
        return computeDayTime(localDate.plusDays(1), localTime, zoneRules);
    }

    private static long computeDayTime(LocalDate localDate, LocalTime localTime, ZoneRules zoneRules) {
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
        List<ZoneOffset> validOffsets = zoneRules.getValidOffsets(localDateTime);
        if (validOffsets.isEmpty()) {
            // forward jump of time (aka from 02:00 to 03:00) - take the moment of jump as proper time
            return zoneRules.getTransition(localDateTime).getInstant().toEpochMilli();
        }
        // either normal day with 1 offset or backward jump of time (aka from 03:00 to 02:00) with 2 offsets
        // take first offset so that proper time occurs during first passage of local time
        return localDateTime.toInstant(validOffsets.get(0)).toEpochMilli();
    }

    /**
     * Parse the string representing the local time in string format
     * {@link java.time.format.DateTimeFormatter#ISO_LOCAL_TIME}. A null or empty string interpreted as null.
     * @param localTime in string format.
     * @return LocalTime or null
     * @throws java.time.format.DateTimeParseException if the text cannot be parsed
     */
    public static LocalTime parseLocalTime(String localTime) {
        return localTime == null || localTime.isEmpty() ? null : LocalTime.parse(localTime);
    }
}
