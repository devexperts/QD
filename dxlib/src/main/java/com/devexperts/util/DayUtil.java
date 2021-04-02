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

/**
 * A collection of static utility methods for manipulation of int day id, that is the number
 * of days since Unix epoch of January 1, 1970.
 */
public class DayUtil {
    private static final int[] DAY_OF_YEAR = {0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};

    private DayUtil() {} // do not create

    /**
     * Returns day identifier for specified year, month and day in Gregorian calendar.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     * Month must be between 1 and 12 inclusive. Year and day might take arbitrary values assuming
     * proleptic Gregorian calendar. The value returned by this method for an arbitrary day value always
     * satisfies the following equality:
     * {@code getDayIdByYearMonthDay(year, month, day) == getDayIdByYearMonthDay(year, month, 0) + day}
     * @throws IllegalArgumentException when month is less than 1 or more than 12.
     */
    public static int getDayIdByYearMonthDay(int year, int month, int day) {
        if (month < 1 || month > 12)
            throw new IllegalArgumentException("invalid month " + month);
        int dayOfYear = DAY_OF_YEAR[month] + day - 1;
        if (month > 2 && year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
            dayOfYear++;
        return year * 365 + MathUtil.div(year - 1, 4) - MathUtil.div(year - 1, 100) + MathUtil.div(year - 1, 400) + dayOfYear - 719527;
    }

    /**
     * Returns day identifier for specified yyyymmdd integer in Gregorian calendar.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     * The yyyymmdd integer is equal to {@code yearSign * (abs(year) * 10000 + month * 100 + day)}, where year,
     * month, and day are in Gregorian calendar, month is between 1 and 12 inclusive, and day is counted from 1.
     * @throws IllegalArgumentException when month is less than 1 or more than 12.
     * @see #getDayIdByYearMonthDay(int year, int month, int day)
     */
    public static int getDayIdByYearMonthDay(int yyyymmdd) {
        if (yyyymmdd >= 0)
            return getDayIdByYearMonthDay(yyyymmdd / 10000, yyyymmdd / 100 % 100, yyyymmdd % 100);
        return getDayIdByYearMonthDay(-(-yyyymmdd / 10000), -yyyymmdd / 100 % 100, -yyyymmdd % 100);
    }

    /**
     * Returns yyyymmdd integer in Gregorian calendar for a specified day identifier.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     * The result is equal to {@code yearSign * (abs(year) * 10000 + month * 100 + day)}, where year,
     * month, and day are in Gregorian calendar, month is between 1 and 12 inclusive, and day is counted from 1.
     */
    public static int getYearMonthDayByDayId(int dayId) {
        int j = dayId + 2472632; // this shifts the epoch back to astronomical year -4800
        int g = MathUtil.div(j, 146097);
        int dg = j - g * 146097;
        int c = (dg / 36524 + 1) * 3 / 4;
        int dc = dg - c * 36524;
        int b = dc / 1461;
        int db = dc - b * 1461;
        int a = (db / 365 + 1) * 3 / 4;
        int da = db - a * 365;
        int y = g * 400 + c * 100 + b * 4 + a; // this is the integer number of full years elapsed since March 1, 4801 BC at 00:00 UTC
        int m = (da * 5 + 308) / 153 - 2; // this is the integer number of full months elapsed since the last March 1 at 00:00 UTC
        int d = da - (m + 4) * 153 / 5 + 122; // this is the number of days elapsed since day 1 of the month at 00:00 UTC
        int yyyy = y - 4800 + (m + 2) / 12;
        int mm = (m + 2) % 12 + 1;
        int dd = d + 1;
        int yyyymmdd = Math.abs(yyyy) * 10000 + mm * 100 + dd;
        return yyyy >= 0 ? yyyymmdd : -yyyymmdd;
    }
}
