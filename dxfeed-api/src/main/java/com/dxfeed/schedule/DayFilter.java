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
package com.dxfeed.schedule;

import java.io.Serializable;

/**
 * A filter for days used by various search methods.
 * This class provides predefined filters for certain Day attributes,
 * although users can create their own filters to suit their needs.
 * <p>
 * Please note that days can be either trading or non-trading, and this distinction can be
 * either based on rules (e.g. weekends) or dictated by special occasions (e.g. holidays).
 * Different filters treat this distinction differently - some accept only trading days,
 * some only non-trading, and some ignore type of day altogether.
 */
public class DayFilter implements Serializable {
    private static final long serialVersionUID = 0;

    /** Accepts any day - useful for pure calendar navigation. */
    public static final DayFilter ANY = new DayFilter(0, null, null, null);
    /** Accepts trading days only - those with <code>({@link Day#isTrading()} == true)</code>. */
    public static final DayFilter TRADING = new DayFilter(0, null, null, true);
    /** Accepts non-trading days only - those with <code>({@link Day#isTrading()} == false)</code>. */
    public static final DayFilter NON_TRADING = new DayFilter(0, null, null, false);
    /** Accepts holidays only - those with <code>({@link Day#isHoliday()} == true)</code>. */
    public static final DayFilter HOLIDAY = new DayFilter(0, true, null, null);
    /** Accepts short days only - those with <code>({@link Day#isShortDay()} == true)</code>. */
    public static final DayFilter SHORT_DAY = new DayFilter(0, null, true, null);

    /** Accepts Mondays only - those with <code>({@link Day#getDayOfWeek()} == 1)</code>. */
    public static final DayFilter MONDAY = new DayFilter(1 << 1, null, null, null);
    /** Accepts Tuesdays only - those with <code>({@link Day#getDayOfWeek()} == 2)</code>. */
    public static final DayFilter TUESDAY = new DayFilter(1 << 2, null, null, null);
    /** Accepts Wednesdays only - those with <code>({@link Day#getDayOfWeek()} == 3)</code>. */
    public static final DayFilter WEDNESDAY = new DayFilter(1 << 3, null, null, null);
    /** Accepts Thursdays only - those with <code>({@link Day#getDayOfWeek()} == 4)</code>. */
    public static final DayFilter THURSDAY = new DayFilter(1 << 4, null, null, null);
    /** Accepts Fridays only - those with <code>({@link Day#getDayOfWeek()} == 5)</code>. */
    public static final DayFilter FRIDAY = new DayFilter(1 << 5, null, null, null);
    /** Accepts Saturdays only - those with <code>({@link Day#getDayOfWeek()} == 6)</code>. */
    public static final DayFilter SATURDAY = new DayFilter(1 << 6, null, null, null);
    /** Accepts Sundays only - those with <code>({@link Day#getDayOfWeek()} == 7)</code>. */
    public static final DayFilter SUNDAY = new DayFilter(1 << 7, null, null, null);
    /** Accepts week-days only - those with <code>({@link Day#getDayOfWeek()} &lt;= 5)</code>. */
    public static final DayFilter WEEK_DAY = new DayFilter(0x3E, null, null, null);
    /** Accepts weekends only - those with <code>({@link Day#getDayOfWeek()} &gt;= 6)</code>. */
    public static final DayFilter WEEK_END = new DayFilter(0xC0, null, null, null);

    /** Bitwise mask of required days (2 for Monday, 4 for Tuesday, 128 for Sunday), <code>0</code> if not relevant. */
    protected final int dayOfWeekMask;
    /** Required holiday flag, <code>null</code> if not relevant. */
    protected final Boolean holiday;
    /** Required short day flag, <code>null</code> if not relevant. */
    protected final Boolean shortDay;
    /** Required trading flag, <code>null</code> if not relevant. */
    protected final Boolean trading;

    /**
     * Creates filter with specified conditions.
     * <p>
     * The <code>dayOfWeekMask</code> is a bitwise mask with individual bits for each day of week.
     * For the day of week number N the N'th bit is used - the Day will be accepted if corresponding bit is set.
     * If no bits is set (if mask is zero) then day of week attribute is ignored (any value is accepted).
     * <p>
     * The boolean parameters specify what value corresponding attributes should have.
     * If some parameter is <code>null</code> then corresponding attribute is ignored (any value is accepted).
     *
     * @param dayOfWeekMask bitwise mask of required days (2 for Monday, 4 for Tuesday, 128 for Sunday), <code>0</code> if not relevant
     * @param holiday required holiday flag, <code>null</code> if not relevant
     * @param shortDay required short day flag, <code>null</code> if not relevant
     * @param trading required trading flag, <code>null</code> if not relevant
     */
    public DayFilter(int dayOfWeekMask, Boolean holiday, Boolean shortDay, Boolean trading) {
        this.dayOfWeekMask = dayOfWeekMask;
        this.holiday = holiday;
        this.shortDay = shortDay;
        this.trading = trading;
    }

    /**
     * Tests whether or not the specified day is an acceptable result.
     *
     * @param day the day to be tested
     * @return <code>true</code> if specified day is accepted
     */
    public boolean accept(Day day) {
        return (dayOfWeekMask == 0 || (dayOfWeekMask & (1 << day.getDayOfWeek())) != 0) &&
            (holiday == null || holiday == day.isHoliday()) &&
            (shortDay == null || shortDay == day.isShortDay()) &&
            (trading == null || trading == day.isTrading());
    }

    public int hashCode() {
        return dayOfWeekMask +
            (holiday == null ? 0 : holiday.hashCode() * 23) +
            (shortDay == null ? 0 : shortDay.hashCode() * 29) +
            (trading == null ? 0 : trading.hashCode() * 239);
    }

    public boolean equals(Object object) {
        if (!(object instanceof DayFilter))
            return false;
        DayFilter filter = (DayFilter) object;
        return dayOfWeekMask == filter.dayOfWeekMask && holiday == filter.holiday && shortDay == filter.shortDay && trading == filter.trading;
    }

    public String toString() {
        return "DayFilter(" + dayOfWeekMask + ", " + holiday + ", " + shortDay + ", " + trading + ")";
    }
}
