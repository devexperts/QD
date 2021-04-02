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

import java.io.Serializable;
import java.util.GregorianCalendar;

/**
 * <b>DayRange</b> represents a certain number of days from starting day (inclusive) to ending day (exclusive).
 * The bounds are defined as calendar dates omitting time of day or timezone details.
 */
public class DayRange implements Serializable {
    private static final long serialVersionUID = 0L;

    private static final int WEEK_EPOCH = DayUtil.getDayIdByYearMonthDay(19700105);
    private static final int EARLIEST_DAY_ID = DayUtil.getDayIdByYearMonthDay(0, 1, 1);

    private final int startDayId;
    private final int endDayId;

    private DayRange(int startDayId, int endDayId) {
        this.startDayId = startDayId;
        this.endDayId = endDayId;
    }

    public boolean containsDayId(int dayId) {
        return dayId >= startDayId && dayId < endDayId;
    }

    public int getStartDayId() {
        return startDayId;
    }

    public int getStartYmd() {
        return DayUtil.getYearMonthDayByDayId(startDayId);
    }

    public int getEndDayId() {
        return endDayId;
    }

    public int getEndYmd() {
        return DayUtil.getYearMonthDayByDayId(endDayId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DayRange dayRange = (DayRange) o;

        if (startDayId != dayRange.startDayId) return false;
        if (endDayId != dayRange.endDayId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = startDayId;
        result = 31 * result + endDayId;
        return result;
    }

    @Override
    public String toString() {
        return "DayRange{" + getStartYmd() + ", " + getEndYmd() + "}";
    }

    /**
     * Returns day range aligned to a period of specified number of weeks which contains specified day identifier.
     * The weekly periods are aligned on Mondays and are counted from Monday, January 5, 1970.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     *
     * The day identifier must belong to common era (Anno Domini) - see {@link GregorianCalendar#AD}.
     * The number of weeks must be between 1 and 1000.
     *
     * @param dayId day identifier to search for
     * @param numberOfWeeks number of weeks in a day range duration
     * @return properly aligned day range for specified day identifier and duration
     * @throws IllegalArgumentException if dayId or numberOfWeeks are out of allowed range
     */
    public static DayRange getWeekRangeByDayId(int dayId, int numberOfWeeks) {
        if (dayId < EARLIEST_DAY_ID)
            throw new IllegalArgumentException("Incorrect dayId = " + dayId);
        if (numberOfWeeks <= 0 || numberOfWeeks > 1000)
            throw new IllegalArgumentException("Incorrect numberOfWeeks = " + numberOfWeeks);

        int periodDurationInDays = 7 * numberOfWeeks;
        int startDayId = dayId - MathUtil.rem(dayId - WEEK_EPOCH, periodDurationInDays);
        int endDayId = startDayId + periodDurationInDays;
        return new DayRange(startDayId, endDayId);
    }

    /**
     * Returns day range aligned to a period of specified number of months which contains specified day identifier.
     * The monthly periods are aligned on calendar months and are counted from January 1, 1970.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     *
     * The day identifier must belong to common era (Anno Domini) - see {@link GregorianCalendar#AD}.
     * The number of months must be between 1 and 1000.
     *
     * @param dayId day identifier to search for
     * @param numberOfMonths number of months in a day range duration
     * @return properly aligned day range for specified day identifier and duration
     * @throws IllegalArgumentException if dayId or numberOfWeeks are out of allowed range
     */
    public static DayRange getMonthRangeByDayId(int dayId, int numberOfMonths) {
        if (dayId < EARLIEST_DAY_ID)
            throw new IllegalArgumentException("Incorrect dayId = " + dayId);
        if (numberOfMonths <= 0 || numberOfMonths > 1000)
            throw new IllegalArgumentException("Incorrect numberOfMonths = " + numberOfMonths);

        int yyyymmdd = DayUtil.getYearMonthDayByDayId(dayId);
        // monthId is analogue of dayId for months, counted from 1970-01-01
        int monthId = (yyyymmdd / 10000 - 1970) * 12 + (yyyymmdd / 100 % 100 - 1);
        int startMonthId = monthId - MathUtil.rem(monthId, numberOfMonths);
        int endMonthId = startMonthId + numberOfMonths;
        int startDayId = DayUtil.getDayIdByYearMonthDay(MathUtil.div(startMonthId, 12) + 1970, MathUtil.rem(startMonthId, 12) + 1, 1);
        int endDayId = DayUtil.getDayIdByYearMonthDay(MathUtil.div(endMonthId, 12) + 1970, MathUtil.rem(endMonthId, 12) + 1, 1);
        return new DayRange(startDayId, endDayId);
    }

    /**
     * Returns day range aligned to a period of specified number of years which contains specified day identifier.
     * The yearly periods are aligned on calendar years and are counted from January 1, 1970.
     * The day identifier is defined as the number of days since Unix epoch of January 1, 1970.
     *
     * The day identifier must belong to common era (Anno Domini) - see {@link GregorianCalendar#AD}.
     * The number of years must be between 1 and 1000.
     *
     * @param dayId day identifier to search for
     * @param numberOfYears number of years in a day range duration
     * @return properly aligned day range for specified day identifier and duration
     * @throws IllegalArgumentException if dayId or numberOfWeeks are out of allowed range
     */
    public static DayRange getYearRangeByDayId(int dayId, int numberOfYears) {
        if (dayId < EARLIEST_DAY_ID)
            throw new IllegalArgumentException("Incorrect dayId = " + dayId);
        if (numberOfYears <= 0 || numberOfYears > 1000)
            throw new IllegalArgumentException("Incorrect numberOfYears = " + numberOfYears);

        int year = DayUtil.getYearMonthDayByDayId(dayId) / 10000;
        int startYear = year - MathUtil.rem(year - 1970, numberOfYears);
        int endYear = startYear + numberOfYears;
        int startDayId = DayUtil.getDayIdByYearMonthDay(startYear, 1, 1);
        int endDayId = DayUtil.getDayIdByYearMonthDay(endYear, 1, 1);
        return new DayRange(startDayId, endDayId);
    }
}
