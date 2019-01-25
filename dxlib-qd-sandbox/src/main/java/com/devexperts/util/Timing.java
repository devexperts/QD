/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The <code>Timing</code> class provides utility methods for fast operations
 * with time and dates. It accepts and returns time values in milliseconds
 * since midnight of January 1, 1970 UTC - the same concept as the one used
 * by {@link System#currentTimeMillis()} method and {@link Date java.util.Date} class.
 * <p/>
 * Beside conventional time, the <code>Timing</code> class also introduces
 * concept of 'day_id' - the day identifier, which is equal to number of days
 * passed since January 1, 1970 (that day has identifier of 0 and previous
 * days have negative identifiers). Note that any given day begins and ends
 * at different time depending on used time zone, however, its 'day_id' remains
 * the same since it is linked to its 'date' with disregard of time zone.
 * <p/>
 * Note that whenever the <code>Timing</code> class operates with time period
 * boundaries (such as day end or year start), it uses <i>inclusive</i> boundaries
 * (i.e. millisecond denoted by that boundary is part of corresponding time
 * period).
 */
public class Timing {
    // ========== private static stuff ==========

    private static final long DAY_LENGTH = 24 * 3600 * 1000;
    private static final int MAX_DAYS_SIZE = SystemProperties.getIntProperty(Timing.class, "cacheSize", 25000, 100, 100000);

    private static final Comparator<Day> DAY_USAGE_COMPARATOR = Comparator.comparingLong(day -> day.usage_counter);
    private static final IndexerFunction.IntKey<Day> DAY_INDEXER = day -> day.day_id;
    private static final IndexerFunction.IntKey<Day> YMD_INDEXER = day -> day.year_month_day_number;

    // ========== public static stuff ==========

    // Note: public instances below use US business schedule and US holidays calendar (because of compatibility issues)
    public static final Timing LOCAL = new Timing(TimeZone.getDefault());
    public static final Timing GMT = new Timing(TimeZone.getTimeZone("GMT"));
    public static final Timing EST = new Timing(TimeZone.getTimeZone("America/New_York"));
    public static final Timing CST = new Timing(TimeZone.getTimeZone("America/Chicago"));

    /**
     * The <code>Day</code> record holds all required data for a given day.
     */
    public static class Day {
        /**
         * Number of this day since January 1, 1970 in used TimeZone.
         */
        public final int day_id;

        /**
         * Time in UTC millis when this day starts, inclusive.
         */
        public final long day_start;

        /**
         * Time in UTC millis when this day ends, inclusive.
         */
        public final long day_end;

        /**
         * Time in UTC millis when this week starts, inclusive - points to beginning of preceding <b>Monday</b>.
         */
        public final long week_start;

        /**
         * Time in UTC millis when this week ends, inclusive - points to ending of following <b>Sunday</b>.
         */
        public final long week_end;

        /**
         * Time in UTC millis when this month starts, inclusive.
         */
        public final long month_start;

        /**
         * Time in UTC millis when this month ends, inclusive.
         */
        public final long month_end;

        /**
         * Time in UTC millis when this year starts, inclusive.
         */
        public final long year_start;

        /**
         * Time in UTC millis when this year ends, inclusive.
         */
        public final long year_end;

        /**
         * Year, month, and day numbers decimally packed in the following way:
         * <pre>year_month_day_number = year * 10000 + month * 100 + day</pre>
         * For example, September 28, 1977 has value 19770928.
         */
        public final int year_month_day_number;

        /**
         * Operating mode of this day in a business-vs-holiday classification.
         */
        public final BusinessSchedule.OperatingMode mode;

        // we don't need it volatile, since we don't really care about the preciseness and/or atomicity of this field
        private long usage_counter; // Copy of usage counter when this day was accessed last time.

        protected Day(int day_id, long day_start, long day_end, long week_start, long week_end,
            long month_start, long month_end, long year_start, long year_end,
            int year_month_day_number, BusinessSchedule.OperatingMode mode)
        {
            this.day_id = day_id;
            this.day_start = day_start;
            this.day_end = day_end;
            this.week_start = week_start;
            this.week_end = week_end;
            this.month_start = month_start;
            this.month_end = month_end;
            this.year_start = year_start;
            this.year_end = year_end;
            this.year_month_day_number = year_month_day_number;
            this.mode = mode;
        }

        /**
         * Determines if this day contains specified time in UTC millis.
         */
        public boolean contains(long time) {
            return time >= day_start && time <= day_end;
        }

        /**
         * Returns year number A.D.
         */
        public int yearNumber() {
            return year_month_day_number / 10000;
        }

        /**
         * Returns month number in the current year, starting with <b>1=January</b>, ending with <b>12=December</b>.
         */
        public int monthNumber() {
            return (year_month_day_number / 100) % 100;
        }

        /**
         * Returns day number in the current month, starting with 1 for 1st day of month.
         */
        public int dayNumber() {
            return year_month_day_number % 100;
        }

        /**
         * Returns day number in the current week, starting with <b>1=Monday</b>, ending with <b>7=Sunday</b>.
         */
        public int dayOfWeekNumber() {
            return Timing.dayOfWeekNumber(day_start, week_start);
        }

        /**
         * Determines if this day is a weekend (Saturday or Sunday).
         */
        public boolean isWeekend() {
            return mode.isWeekend();
        }

        /**
         * Determines if this day is a holiday from the list of exchange holidays.
         */
        public boolean isHoliday() {
            return mode.isHoliday();
        }

        /**
         * Determines if this day is a trading day - not a weekend and not a holiday.
         */
        public boolean isTrading() {
            return mode.isBusiness();
        }

        /**
         * Returns the "universal" milliseconds for this day. This magic long value, passed to the {@link Timing#getByTime(long)}
         * method, will produce the same day_id, regardless of the {@link Timing}'s time zone.
         */
        public long getGMTNoon() {
            return Timing.getGMTNoon(day_id);
        }

        public String toString() {
            return "#" + day_id + " (" + year_month_day_number + ")";
        }
    }

    /**
     * Creates <code>Timing</code> instance for specified calendar and business schedule.
     */
    public Timing(Calendar calendar, BusinessSchedule business_schedule) {
        this.calendar = (Calendar) calendar.clone();
        this.time_zone = (TimeZone) calendar.getTimeZone().clone();
        this.raw_offset = time_zone.getRawOffset();
        this.business_schedule = business_schedule;
    }

    /**
     * Creates <code>Timing</code> instance for specified timezone with default calendar and US business schedule.
     * The US business schedule is used because of compatibility issues and to simplify migration.
     */
    public Timing(TimeZone time_zone) {
        this(Calendar.getInstance(time_zone), BusinessSchedule.US);
    }

    /**
     * Returns copy of calendar used by this <code>Timing</code>.
     */
    public Calendar getCalendar() {
        return (Calendar) calendar.clone();
    }

    /**
     * Returns copy of timezone used by this <code>Timing</code>.
     */
    public TimeZone getTimeZone() {
        return (TimeZone) time_zone.clone();
    }

    /**
     * Returns <code>Day</code> record that corresponds to current moment of time.
     */
    public Day today() {
        return getByTime(System.currentTimeMillis());
    }

    /**
     * Returns corresponding <code>Day</code> record for specified
     * time in UTC millis (number of milliseconds elapsed since midnight,
     * January 1, 1970 UTC). Creates new record if no such record exist yet.
     * Counts usage of Day records, maintains cache of most recently used
     * records and gathers usage statistics.
     */
    public Day getByTime(long millis) {
        usage_counter.incrementAndGet();

        // Calculate approximate day_id.
        int day_id = dayId(millis + raw_offset);

        // Locate proper Day record.
        Day d = getCachedDay(day_id);
        if (d == null || millis > d.day_end)
            d = getCachedDay(day_id + 1);
        if (d == null || millis < d.day_start)
            d = getCachedDay(day_id - 1);

        while (d != null && millis > d.day_end)
            d = getCachedDay(d.day_id + 1);
        while (d != null && millis < d.day_start)
            d = getCachedDay(d.day_id - 1);

        if (d == null || !d.contains(millis))
            d = createCachedDay(millis);

        return d;
    }

    /**
     * Returns corresponding <code>Day</code> record for specified
     * day_id (number of this day since January 1, 1970 in used TimeZone).
     * Creates new record if no such record exist yet.
     * Counts usage of Day records, maintains cache of most recently used
     * records and gathers usage statistics.
     */
    public Day getById(int day_id) {
        usage_counter.incrementAndGet();

        Day d = getCachedDay(day_id);
        if (d == null) {
            // Locate proper Day record.
            d = createCachedDay(day_id * DAY_LENGTH - raw_offset + DAY_LENGTH / 2);
            while (d.day_id > day_id)
                d = createCachedDay(d.day_start - DAY_LENGTH / 2);
            while (d.day_id < day_id)
                d = createCachedDay(d.day_end + DAY_LENGTH / 2);
            if (day_id != d.day_id)
                throw new RuntimeException("Abnormal Day creation for day #" + day_id + ".");
        }
        return d;
    }

    /**
     * Returns corresponding <code>Day</code> record for specified year, month and day numbers.
     * Year, month, and day numbers shall be decimally packed in the following way:
     * <pre>year_month_day_number = year * 10000 + month * 100 + day</pre>
     * For example, September 28, 1977 has value 19770928.
     * <p/>
     * If specified date does not exist then this method returns Day record with
     * the lowest valid year_month_day_number that is greater than specified one.
     * <p/>
     * Creates new record if no such record exist yet.
     * Counts usage of Day records, maintains cache of most recently used
     * records and gathers usage statistics.
     */
    public Day getByYmd(int year_month_day_number) {
        usage_counter.incrementAndGet();

        // Try direct approach - works for fully correct cached days.
        Day d = ymds_cache.getByKey(year_month_day_number);
        if (d != null) {
            d.usage_counter = usage_counter.get();
            return d;
        }

        // Ignore dates in the deep past or future - don't want to work with overflows and undefined calendars.
        year_month_day_number = Math.min(Math.max(year_month_day_number, 10102), 999991230);

        // Disassemble and normalize (roughly) date.
        int year = year_month_day_number / 10000;
        int month = year_month_day_number / 100 % 100;
        int day = year_month_day_number % 100;
        if (day > 31) {
            month++;
            day = 1;
        } else if (day < 1)
            day = 1;
        if (month > 12) {
            year++;
            month = day = 1;
        } else if (month < 1)
            month = day = 1;

        // Try corrected date - works for partially correct cached days.
        d = ymds_cache.getByKey(year * 10000 + month * 100 + day);
        if (d != null) {
            d.usage_counter = usage_counter.get();
            return d;
        }

        // Calculate approximate day_id and locate proper Day record.
        int day_id = DayUtil.getDayIdByYearMonthDay(year, month, day);
        d = getCachedDay(day_id);
        if (d == null)
            d = createCachedDay(day_id * DAY_LENGTH - raw_offset + DAY_LENGTH / 2);
        while (d.year_month_day_number > year_month_day_number)
            d = createCachedDay(d.day_start - DAY_LENGTH / 2);
        while (d.year_month_day_number < year_month_day_number)
            d = createCachedDay(d.day_end + DAY_LENGTH / 2);

        return d;
    }

    /**
     * Returns the "universal" milliseconds for this day. This magic long value, passed to the {@link Timing#getByTime(long)}
     * method, will produce the same day_id for all popular time zones (except exotic ones).
     */
    public static long getGMTNoon(int day_id) {
        return day_id * DAY_LENGTH + DAY_LENGTH / 2;
    }

    /**
     * Returns specified day number in the specified week, starting with <b>1=Monday</b>, ending with <b>7=Sunday</b>.
     */
    public static int dayOfWeekNumber(long day_start, long week_start) {
        return (int) ((day_start - week_start + DAY_LENGTH / 2) / DAY_LENGTH) + 1;
    }


    // ========== System Stuff ==========

    public String toString() {
        return getStatistics();
    }

    /**
     * Returns gathered statistics in human-readable form.
     */
    public String getStatistics() {
        int days = days_cache.size();
        long uses = usage_counter.get();
        long hits = uses - creation_counter.get();
        double eff = hits <= 0 || uses <= 0 ? 0 : (10000 * hits + uses / 2) / uses / 100.0;
        return "Timing." + time_zone.getDisplayName(false, TimeZone.SHORT) +
            ": days = " + days + ", efficiency = " + hits + "/" + uses + " (" + eff + "%)";
    }

    // ========== Internal Timing Implementation ==========

    private final Calendar calendar; // Calendar used by Timing.
    private final TimeZone time_zone; // TimeZone where Timing works.
    private final int raw_offset; // time_zone.getRawOffset()
    private final BusinessSchedule business_schedule;

    private final Object lock = new Object();
    private final IndexedSet<Integer, Day> days_cache = IndexedSet.createInt(DAY_INDEXER);
    private final IndexedSet<Integer, Day> ymds_cache = IndexedSet.createInt(YMD_INDEXER);

    // Number of Day creations.
    private final AtomicLong creation_counter = new AtomicLong(0);

    // Number of Timing usages.
    private final AtomicLong usage_counter = new AtomicLong(0);

    /**
     * Returns cached <code>Day</code> record for specified day_id.
     * Updates day usage counter.
     */
    private Day getCachedDay(int day_id) {
        Day d = days_cache.getByKey(day_id);
        if (d != null)
            d.usage_counter = usage_counter.get();
        return d;
    }

    /**
     * Creates new <code>Day</code> record for specified time in
     * UTC millis and caches it (or retrieves cached one if present).
     * Updates day usage counter.
     */
    private Day createCachedDay(long millis) {
        synchronized (lock) {
            Day d = createDay(millis);
            Day old = days_cache.getByValue(d);
            if (old == null) {
                days_cache.put(d);
                ymds_cache.put(d);
            } else
                d = old;
            checkDaysCacheSize(d.day_id);
            d.usage_counter = usage_counter.get();
            return d;
        }
    }

    /**
     * Properly divides local millis by DAY_LENGTH to determine which day it is.
     */
    private static int dayId(long local_millis) {
        return (int) MathUtil.div(local_millis, DAY_LENGTH);
    }

    /**
     * Sets calendar to the start of the day and returns resulting time.
     */
    private static long dayStart(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, c.getActualMinimum(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, c.getActualMinimum(Calendar.MINUTE));
        c.set(Calendar.SECOND, c.getActualMinimum(Calendar.SECOND));
        c.set(Calendar.MILLISECOND, c.getActualMinimum(Calendar.MILLISECOND));
        return c.getTime().getTime();
    }

    /**
     * Sets calendar to the end of the day and returns resulting time.
     */
    private static long dayEnd(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, c.getActualMaximum(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, c.getActualMaximum(Calendar.MINUTE));
        c.set(Calendar.SECOND, c.getActualMaximum(Calendar.SECOND));
        c.set(Calendar.MILLISECOND, c.getActualMaximum(Calendar.MILLISECOND));
        return c.getTime().getTime();
    }

    /**
     * Creates new <code>Day</code> record for specified time
     * in UTC millis and gathers creation statistics.
     * Sets day usage counter.
     */
    // GuardedBy: lock
    private Day createDay(long millis) {
        creation_counter.incrementAndGet();

        Calendar c = calendar;
        c.setTimeInMillis(millis);

        int day_id = dayId(c.getTime().getTime() + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET));
        int year_month_day_number = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);

        long day_start = dayStart(c);
        long day_end = dayEnd(c);

        c.set(Calendar.DAY_OF_MONTH, c.getActualMinimum(Calendar.DAY_OF_MONTH));
        long month_start = dayStart(c);
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        long month_end = dayEnd(c);

        c.set(Calendar.DAY_OF_YEAR, c.getActualMinimum(Calendar.DAY_OF_YEAR));
        long year_start = dayStart(c);
        c.set(Calendar.DAY_OF_YEAR, c.getActualMaximum(Calendar.DAY_OF_YEAR));
        long year_end = dayEnd(c);

        c.setTimeInMillis(millis);
        int day_of_week = c.get(Calendar.DAY_OF_WEEK);
        c.add(Calendar.DAY_OF_YEAR, -(day_of_week == Calendar.SUNDAY ? 6 : day_of_week - Calendar.MONDAY));
        long week_start = dayStart(c);
        c.add(Calendar.DAY_OF_YEAR, 6);
        long week_end = dayEnd(c);

        return createDay(day_id, day_start, day_end, week_start, week_end, month_start, month_end, year_start, year_end, year_month_day_number);
    }

    /**
     * @deprecated This is a migration method only
     */
    protected Day createDay(int day_id, long day_start, long day_end, long week_start, long week_end, long month_start, long month_end, long year_start, long year_end, int year_month_day_number) {
        BusinessSchedule.OperatingMode mode = business_schedule.getOperatingMode(year_month_day_number, dayOfWeekNumber(day_start, week_start));

        return new Day(day_id, day_start, day_end, week_start, week_end,
            month_start, month_end, year_start, year_end,
            year_month_day_number, mode);
    }

    /**
     * Checks days_cache size, drops rarely used records if needed.
     * Always keeps record for specified day_id in the cache.
     */
    // GuardedBy: lock
    private void checkDaysCacheSize(int keep_day_id) {
        if (days_cache.size() <= MAX_DAYS_SIZE)
            return;
        Day[] a = days_cache.toArray(new Day[days_cache.size()]);
        QuickSort.sort(a, DAY_USAGE_COMPARATOR);
        for (int i = a.length / 2; --i >= 0;)
            if (a[i].day_id != keep_day_id) {
                days_cache.removeKey(a[i].day_id);
                ymds_cache.removeKey(a[i].year_month_day_number);
            }
    }
}
