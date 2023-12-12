/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.schedule;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.StreamCompression;
import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.DayUtil;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.LogUtil;
import com.devexperts.util.LongHashMap;
import com.devexperts.util.LongHashSet;
import com.devexperts.util.MathUtil;
import com.devexperts.util.QuickSort;
import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;
import com.dxfeed.ipf.InstrumentProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Schedule</b> class provides API to retrieve and explore trading schedules of different exchanges
 * and different classes of financial instruments. Each instance of schedule covers separate trading schedule
 * of some class of instruments, i.e. NYSE stock trading schedule or CME corn futures trading schedule.
 * Each schedule splits entire time scale into separate {@link Day days} that are aligned to the specific
 * trading hours of covered trading schedule.
 */
public final class Schedule {
    private static final Logging log = Logging.getLogging(Schedule.class);

    private static final String CACHE_LIMIT_PROPERTY = "com.dxfeed.schedule.cache";
    private static final String DOWNLOAD_PROPERTY = "com.dxfeed.schedule.download";
    private static final String DOWNLOAD_AUTO = "http://downloads.dxfeed.com/schedule/schedule.zip,1d";

    // ========== Instance Lookup ==========

    /**
     * Returns default schedule instance for specified instrument profile.
     *
     * @param profile instrument profile those schedule is requested
     * @return default schedule instance for specified instrument profile
     */
    public static Schedule getInstance(InstrumentProfile profile) {
        return getInstance(profile.getTradingHours());
    }

    /**
     * Returns default schedule instance for specified schedule definition.
     *
     * @param scheduleDefinition schedule definition of requested schedule
     * @return default schedule instance for specified schedule definition
     */
    public static Schedule getInstance(String scheduleDefinition) {
        int open = scheduleDefinition.indexOf('(', 0);
        int close = scheduleDefinition.indexOf(')', open + 1);
        if (open < 0 || close < 0)
            throw new IllegalArgumentException("broken schedule " + scheduleDefinition);
        int start = Math.max(scheduleDefinition.lastIndexOf(')', open - 1), scheduleDefinition.lastIndexOf(';', open - 1)) + 1;
        return getSchedule(scheduleDefinition.substring(start, close + 1));
    }

    /**
     * Returns schedule instance for specified instrument profile and trading venue.
     *
     * @param profile instrument profile those schedule is requested
     * @param venue trading venue those schedule is requested
     * @return schedule instance for specified instrument profile and trading venue
     */
    public static Schedule getInstance(InstrumentProfile profile, String venue) {
        String hours = profile.getTradingHours();
        for (int open = -1; (open = hours.indexOf('(', open + 1)) >= 0;) {
            int close = hours.indexOf(')', open + 1);
            if (close < 0)
                throw new IllegalArgumentException("broken schedule " + hours);
            int start = Math.max(hours.lastIndexOf(')', open - 1), hours.lastIndexOf(';', open - 1)) + 1;
            if (open - start == venue.length() && hours.regionMatches(start, venue, 0, venue.length()))
                return getSchedule(hours.substring(start, close + 1));
        }
        throw new NoSuchElementException("could not find schedule for trading venue " + venue + " in " + hours);
    }

    /**
     * Returns trading venues for specified instrument profile.
     *
     * @param profile instrument profile those trading venues are requested
     * @return trading venues for specified instrument profile
     */
    public static List<String> getTradingVenues(InstrumentProfile profile) {
        List<String> venues = new ArrayList<>();
        Matcher m = VENUE_PATTERN.matcher(profile.getTradingHours());
        while (m.find())
            venues.add(m.group(1));
        return venues;
    }

    private static final Pattern VENUE_PATTERN = Pattern.compile("(\\w*)\\([^()]*\\)");

    // ========== Updating Defaults ==========

    private static String downloadURL;
    private static long downloadPeriod;
    private static Thread downloadThread;

    /**
     * Downloads defaults using specified download config and optionally start periodic download.
     * The specified config can be one of the following:<ul>
     * <li>"" or null - stop periodic download
     * <li>URL   - download once from specified URL and stop periodic download
     * <li>URL,period   - start periodic download from specified URL
     * <li>"auto"   - start periodic download from default location
     * </ul>
     *
     * @param downloadConfig download config
     */
    public static void downloadDefaults(String downloadConfig) {
        if (downloadConfig == null)
            downloadConfig = "";
        downloadConfig = downloadConfig.trim();
        if (downloadConfig.equalsIgnoreCase("auto"))
            downloadConfig = DOWNLOAD_AUTO;
        String[] config = downloadConfig.split(",");
        synchronized (Schedule.class) {
            downloadURL = config[0].trim().length() > 0 ? config[0].trim() : null;
            downloadPeriod = config.length >= 2 ? Math.max(TimePeriod.valueOf(config[1]).getTime(), 0) : 0;
            LockSupport.unpark(downloadThread);
            if (downloadPeriod > 0) {
                downloadThread = new Thread(Schedule::runDownload, "ScheduleDownloader");
                downloadThread.setDaemon(true);
                downloadThread.start();
            } else
                downloadThread = null;
        }
        doDownload();
    }

    private static void runDownload() {
        long next = 0;
        while (true) {
            try {
                long time = System.currentTimeMillis();
                long newNext;
                synchronized (Schedule.class) {
                    if (downloadURL == null || downloadPeriod == 0 || downloadThread != Thread.currentThread())
                        return;
                    newNext = time + (long) (downloadPeriod * (0.95 + 0.1 * Math.random()));
                }
                if (next == 0)
                    next = newNext;
                if (time < next) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(next - time));
                    continue;
                }
                next = newNext;
                doDownload();
            } catch (Throwable t) {
                log.error("Unexpected error", t);
            }
        }
    }

    private static void doDownload() {
        String url = downloadURL; // Atomic read.
        if (url == null)
            return;
        byte[] data;
        try {
            data = URLInputStream.readBytes(url);
        } catch (Throwable t) {
            log.error("Failed to download schedule defaults from " + LogUtil.hideCredentials(url), t);
            return;
        }
        try (InputStream in = StreamCompression.detectCompressionByHeaderAndDecompress(new ByteArrayInput(data))) {
            String result = setDefaults(in);
            log.info("Downloaded schedule defaults from " + LogUtil.hideCredentials(url) + " - they are " + result);
        } catch (Throwable t) {
            log.error("Unexpected error", t);
        }
    }

    /**
     * Sets shared defaults that are used by individual schedule instances.
     *
     * @param data content of default data
     * @throws IOException  If an I/O error occurs
     */
    public static void setDefaults(byte[] data) throws IOException {
        try (InputStream in = StreamCompression.detectCompressionByHeaderAndDecompress(new ByteArrayInput(data))) {
            setDefaults(in);
        }
    }

    // ========== Instance API ==========

    /**
     * Returns session that contains specified time.
     * This method will throw {@link IllegalArgumentException} if specified time
     * falls outside of valid date range from 0001-01-02 to 9999-12-30.
     *
     * @param time the time to search for
     * @return session that contains specified time
     * @throws IllegalArgumentException if specified time falls outside of valid date range
     */
    public Session getSessionByTime(long time) {
        return getDayByTime(time).getSessionByTime(time);
    }

    /**
     * Returns day that contains specified time.
     * This method will throw {@link IllegalArgumentException} if specified time
     * falls outside of valid date range from 0001-01-02 to 9999-12-30.
     *
     * @param time the time to search for
     * @return day that contains specified time
     * @throws IllegalArgumentException if specified time falls outside of valid date range
     */
    public Day getDayByTime(long time) {
        checkRange("time", time, MIN_TIME, MAX_TIME);
        usageCounter.incrementAndGet();
        // Calculate approximate dayId and locate proper Day.
        Day d = getDay((int) MathUtil.div(time + rawOffset - dayOffset, DAY_LENGTH));
        while (d.getStartTime() > time)
            d = getDay(d.getDayId() - 1);
        while (d.getEndTime() <= time)
            d = getDay(d.getDayId() + 1);
        return d;
    }

    /**
     * Returns day for specified day identifier.
     * This method will throw {@link IllegalArgumentException} if specified day identifier
     * falls outside of valid date range from 0001-01-02 to 9999-12-30.
     *
     * @param dayId day identifier to search for
     * @return day for specified day identifier
     * @throws IllegalArgumentException if specified day identifier falls outside of valid date range
     * @see Day#getDayId()
     */
    public Day getDayById(int dayId) {
        checkRange("dayId", dayId, MIN_ID, MAX_ID);
        usageCounter.incrementAndGet();
        return getDay(dayId);
    }

    /**
     * Returns day for specified year, month and day numbers.
     * Year, month, and day numbers shall be decimally packed in the following way:
     * <pre>YearMonthDay = year * 10000 + month * 100 + day</pre>
     * For example, September 28, 1977 has value 19770928.
     * <p>
     * If specified day does not exist then this method returns day with
     * the lowest valid YearMonthDay that is greater than specified one.
     * This method will throw {@link IllegalArgumentException} if specified year, month and day numbers
     * fall outside of valid date range from 0001-01-02 to 9999-12-30.
     *
     * @param yearMonthDay year, month and day numbers to search for
     * @return day for specified year, month and day numbers
     * @throws IllegalArgumentException if specified year, month and day numbers fall outside of valid date range
     * @see Day#getYearMonthDay()
     */
    @SuppressWarnings("UnusedDeclaration")
    public Day getDayByYearMonthDay(int yearMonthDay) {
        checkRange("yearMonthDay", yearMonthDay, MIN_YMD, MAX_YMD);
        usageCounter.incrementAndGet();

        // Try direct approach - works for fully correct cached days.
        Day d = ymdCache.getByKey(yearMonthDay);
        if (d != null) {
            d.usageCounter = usageCounter.get();
            return d;
        }

        // Disassemble and normalize (roughly) date.
        int year = yearMonthDay / 10000;
        int month = yearMonthDay / 100 % 100;
        int day = yearMonthDay % 100;
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
        d = ymdCache.getByKey(year * 10000 + month * 100 + day);
        if (d != null) {
            d.usageCounter = usageCounter.get();
            return d;
        }

        // Calculate approximate dayId and locate proper Day.
        d = getDay(DayUtil.getDayIdByYearMonthDay(year, month, day));
        while (d.getYearMonthDay() > yearMonthDay)
            d = getDay(d.getDayId() - 1);
        while (d.getYearMonthDay() < yearMonthDay)
            d = getDay(d.getDayId() + 1);

        return d;
    }

    /**
     * Returns session that is nearest to the specified time and that is accepted by specified filter.
     * This method will throw {@link IllegalArgumentException} if specified time
     * falls outside of valid date range from 0001-01-02 to 9999-12-30.
     * If no sessions acceptable by specified filter are found within one year this method will throw {@link NoSuchElementException}.
     * <p>
     * To find nearest trading session of any type use this code:
     * <pre>session = schedule.getNearestSessionByTime(time, SessionFilter.TRADING);</pre>
     * To find nearest regular trading session use this code:
     * <pre>session = schedule.getNearestSessionByTime(time, SessionFilter.REGULAR);</pre>
     *
     * @param time the time to search for
     * @param filter the filter to test sessions
     * @return session that is nearest to the specified time and that is accepted by specified filter
     * @throws IllegalArgumentException if specified time falls outside of valid date range
     * @throws NoSuchElementException if no such day was found within one year
     */
    public Session getNearestSessionByTime(long time, SessionFilter filter) {
        Session session = findNearestSessionByTime(time, filter);
        if (session != null)
            return session;
        throw new NoSuchElementException("could not find session nearest to " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time) + " for " + filter);
    }

    /**
     * Returns session that is nearest to the specified time and that is accepted by specified filter.
     * This method will throw {@link IllegalArgumentException} if specified time
     * falls outside of valid date range from 0001-01-02 to 9999-12-30.
     * If no sessions acceptable by specified filter are found within one year this method will return <b>null</b>.
     * <p>
     * To find nearest trading session of any type use this code:
     * <pre>session = schedule.findNearestSessionByTime(time, SessionFilter.TRADING);</pre>
     * To find nearest regular trading session use this code:
     * <pre>session = schedule.findNearestSessionByTime(time, SessionFilter.REGULAR);</pre>
     *
     * @param time the time to search for
     * @param filter the filter to test sessions
     * @return session that is nearest to the specified time and that is accepted by specified filter
     * @throws IllegalArgumentException if specified time falls outside of valid date range
     */
    public Session findNearestSessionByTime(long time, SessionFilter filter) {
        Session session = getSessionByTime(time);
        if (filter.accept(session))
            return session;
        Session prev = session.findPrevSession(filter);
        Session next = session.findNextSession(filter);
        if (prev == null)
            return next;
        if (next == null)
            return prev;
        return time - prev.getEndTime() < next.getStartTime() - time ? prev : next;
    }

    /**
     * Returns name of this schedule.
     *
     * @return name of this schedule
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the string used to define this schedule instance (see {@link Schedule#getSchedule})
     *
     * @return schedule definition string
     * @see Schedule#getSchedule
     */
    public String getDefinition() {
        return def;
    }

    /**
     * Returns time zone in which this schedule is defined.
     *
     * @return time zone in which this schedule is defined
     */
    public TimeZone getTimeZone() {
        return (TimeZone) calendar.getTimeZone().clone();
    }

    public String toString() {
        return "Schedule{" + def + "}";
    }

    // ========== Implementation Details ==========

    private static final int CACHE_LIMIT = SystemProperties.getIntProperty(CACHE_LIMIT_PROPERTY, 25000, 100, 100000);
    private static final int CACHE_RETAIN = CACHE_LIMIT - CACHE_LIMIT / 4;
    private static final long DAY_LENGTH = 24 * 3600 * 1000;
    private static final int MIN_YMD = 10103;
    private static final int MAX_YMD = 99991229;
    private static final int MIN_ID = DayUtil.getDayIdByYearMonthDay(MIN_YMD / 10000, MIN_YMD / 100 % 100, MIN_YMD % 100);
    private static final int MAX_ID = DayUtil.getDayIdByYearMonthDay(MAX_YMD / 10000, MAX_YMD / 100 % 100, MAX_YMD % 100);
    private static final long MIN_TIME = MIN_ID * DAY_LENGTH;
    private static final long MAX_TIME = MAX_ID * DAY_LENGTH + DAY_LENGTH;

    /**
     * Checks date range and throws exception with proper error message.
     */
    private static void checkRange(String name, long value, long min, long max) {
        if (value < min || value > max)
            throw new IllegalArgumentException("specified " + name + " falls outside of valid date range (from 0001-01-02 to 9999-12-30): " + value);
    }

    /**
     * Returns ordinal day number in the week starting with <b>1=Monday</b> and ending with <b>7=Sunday</b>.
     */
    static int dayOfWeek(int dayId) {
        return (dayId % 7 + 10) % 7 + 1; // 1=Monday, 7=Sunday, protected from overflows and negative numbers
    }

    private static final Comparator<Day> USAGE_COMPARATOR = (d1, d2) -> Long.compare(d1.usageCounter, d2.usageCounter);

    private static final class TimeDef {
        final int day;
        final int hour;
        final int minute;
        final int second;

        TimeDef(TimeDef source, int dayShift) {
            day = source.day + dayShift;
            hour = source.hour;
            minute = source.minute;
            second = source.second;
        }

        // Expects "[+-]*hhmm(ss)?"
        TimeDef(String scheduleDefinition, String def) {
            int d = 0;
            int i = 0;
            for (; i < def.length(); i++)
                if (def.charAt(i) == '+')
                    d++;
                else if (def.charAt(i) == '-')
                    d--;
                else
                    break;
            day = d;
            if (def.length() != i + 4 && def.length() != i + 6)
                throw new IllegalArgumentException("unmatched data in " + def + " in " + scheduleDefinition);
            hour = parse2(def, i);
            minute = parse2(def, i + 2);
            second = def.length() > i + 4 ? parse2(def, i + 4) : 0;
            if (hour == 24 && minute == 0 && second == 0) {
                log.warn("Deprecated time spec " + def + " in " + scheduleDefinition + ". Preferred spec: +0000");
            } else if (hour >= 24 || minute >= 60 || second >= 60) {
                throw new IllegalArgumentException("illegal time " + def + " in " + scheduleDefinition);
            }
        }

        private int parse2(String s, int pos) {
            return (s.charAt(pos) - '0') * 10 + (s.charAt(pos + 1) - '0');
        }

        long offset() {
            return day * DAY_LENGTH + hour * 3600000 + minute * 60000 + second * 1000;
        }

        long get(Calendar calendar, long localTime) {
            calendar.setTimeInMillis(localTime);
            calendar.add(Calendar.DAY_OF_YEAR, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, second);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime().getTime();
        }

        public int compareTo(TimeDef other) {
            if (day != other.day)
                return day - other.day;
            if (hour != other.hour)
                return hour - other.hour;
            if (minute != other.minute)
                return minute - other.minute;
            return second - other.second;
        }

        public String toString() {
            return day + ":" + hour + ":" + minute + ":" + second;
        }
    }

    private static final class SessionDef {
        final SessionType type;
        final TimeDef start;
        final TimeDef end;

        SessionDef(SessionType type, TimeDef start, TimeDef end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }

        Session create(Day day, Calendar calendar, long time, long startShift, long endShift) {
            long start = this.start.get(calendar, time) + startShift;
            long end = this.end.get(calendar, time) + endShift;
            if (start > end)
                throw new IllegalArgumentException("start=" + start + " > end=" + end);
            return new Session(day, type, start, end);
        }

        boolean isTooShort(Calendar calendar, long time, long earlyClose) {
            return start.get(calendar, time) >= end.get(calendar, time) - earlyClose;
        }

        public String toString() {
            return type + "(" + start + "," + end + ")";
        }
    }

    private static final class DayDef {
        private static final Pattern GROUP_SEARCH = Pattern.compile("([a-zA-Z_]*)([^a-zA-Z_]+)");
        private static final Pattern MINUTE_SEARCH = Pattern.compile("([+-]*[0-9]{4})/?([+-]*[0-9]{4})");
        private static final Pattern MINUTE_MATCH = Pattern.compile("(" + MINUTE_SEARCH.pattern() + ")*");
        private static final Pattern SECOND_SEARCH = Pattern.compile("([+-]*[0-9]{6})/?([+-]*[0-9]{6})");
        private static final Pattern SECOND_MATCH = Pattern.compile("(" + SECOND_SEARCH.pattern() + ")*");

        final TimeDef dayStart;
        final TimeDef dayEnd;
        final TimeDef resetTime;
        final SessionDef[] sessions;

        DayDef(String scheduleDefinition, TimeDef dayStart, TimeDef dayEnd, TimeDef resetTime, String def) {
            List<SessionDef> s = new ArrayList<>();
            Matcher m = GROUP_SEARCH.matcher(def);
            int matched = 0;
            while (m.find()) {
                if (matched == m.start())
                    matched = m.end();
                String key = m.group(1);
                if (key.equalsIgnoreCase("rt")) {
                    resetTime = new TimeDef(scheduleDefinition, m.group(2));
                } else {
                    SessionType type =
                        key.equalsIgnoreCase("d") ? SessionType.NO_TRADING :
                        key.equalsIgnoreCase("p") ? SessionType.PRE_MARKET :
                        key.equalsIgnoreCase("r") ? SessionType.REGULAR :
                        key.equalsIgnoreCase("a") ? SessionType.AFTER_MARKET :
                        key.equalsIgnoreCase("") ? SessionType.REGULAR :
                        null;
                    if (type == null)
                        throw new IllegalArgumentException("unknown session type in " + def + " in " + scheduleDefinition);
                    Matcher mm =
                        MINUTE_MATCH.matcher(m.group(2)).matches() ? MINUTE_SEARCH.matcher(m.group(2)) :
                        SECOND_MATCH.matcher(m.group(2)).matches() ? SECOND_SEARCH.matcher(m.group(2)) :
                        null;
                    if (mm == null)
                        throw new IllegalArgumentException("unmatched data in " + def + " in " + scheduleDefinition);
                    while (mm.find()) {
                        TimeDef td1 = new TimeDef(scheduleDefinition, mm.group(1));
                        TimeDef td2 = new TimeDef(scheduleDefinition, mm.group(2));
                        if (td1.compareTo(td2) > 0) {
                            throw new IllegalArgumentException(
                                "illegal session period " + mm.group() + " in " + def + " in " + scheduleDefinition);
                        }
                        if (type == SessionType.NO_TRADING) {
                            dayStart = td1;
                            dayEnd = td2;
                        } else {
                            s.add(new SessionDef(type, td1, td2));
                        }
                        type = SessionType.REGULAR;
                    }
                }
            }
            if (matched != def.length())
                throw new IllegalArgumentException("unmatched data in " + def + " in " + scheduleDefinition);
            if (resetTime == null)
                resetTime = dayStart;
            if (resetTime.compareTo(dayStart) < 0 || resetTime.compareTo(dayEnd) >= 0)
                throw new IllegalArgumentException("illegal reset time " + resetTime + " for " + dayStart + " and " + dayEnd + " in " + scheduleDefinition);
            this.dayStart = dayStart;
            this.dayEnd = dayEnd;
            this.resetTime = resetTime;
            if (s.isEmpty()) {
                s.add(new SessionDef(SessionType.NO_TRADING, dayStart, dayEnd));
            } else {
                for (int i = 1; i < s.size(); i++) // Note: size() changes dynamically in fillGap()
                    fillGap(scheduleDefinition, s, i, s.get(i - 1).end, s.get(i).start);
                fillGap(scheduleDefinition, s, 0, dayStart, s.get(0).start);
                fillGap(scheduleDefinition, s, s.size(), s.get(s.size() - 1).end, dayEnd);
            }
            sessions = s.toArray(new SessionDef[s.size()]);
        }

        private static void fillGap(String scheduleDefinition, List<SessionDef> s, int index, TimeDef start, TimeDef end) {
            if (start.compareTo(end) > 0)
                throw new IllegalArgumentException("illegal session order at " + index + " for " + start + " and " + end + " in " + scheduleDefinition);
            if (start.compareTo(end) < 0)
                s.add(index, new SessionDef(SessionType.NO_TRADING, start, end));
        }

        boolean isTrading() {
            for (SessionDef session : sessions) {
                if (session.type != SessionType.NO_TRADING)
                    return true;
            }
            return false;
        }
    }

    // Generic strategy pattern is "name[params]", where name is alpha string and params shall start with non-alpha.
    private static final Pattern STRATEGY_PATTERN = Pattern.compile("([a-zA-Z_]+)([^a-zA-Z_].*)?");
    private static final Pattern SDS_EC_PATTERN = Pattern.compile("ec([0-9]{4})");
    private static final Pattern HDS_JNTD_PATTERN = Pattern.compile("jntd([0-9])");

    private static Matcher getStrategy(String def, Map<String, String> props, String key, String name, Pattern pattern) {
        String value = props.get(key);
        if (value == null || value.isEmpty())
            return null;
        Matcher m = STRATEGY_PATTERN.matcher(value);
        if (m.matches()) {
            if (!m.group(1).equals(name)) {
                // In case of unknown strategy - log it and ignore it as if none were specified. For compatibility.
                // Once several different strategies are supported - this warning shall be moved elsewhere
                // or method signature shall be changed to avoid misreporting of "unknown" strategy.
                log.warn("Unknown " + key + " strategy " + m.group(1) + " for " + def);
                return null;
            }
            Matcher result = pattern.matcher(value);
            if (result.matches())
                return result;
            // If strategy does not match specified pattern - fall through to throw exception below.
        }
        // If property does not match neither generic strategy pattern nor specific strategy pattern - throw exception.
        throw new IllegalArgumentException("broken " + key + " strategy for " + def);
    }

    final String def;
    private String name;
    private Calendar calendar;
    private long rawOffset;
    private long dayOffset;

    private LongHashSet holidays;
    private LongHashSet shortdays;
    private long earlyClose;
    private int joinNextTradingDay;
    private DayDef[] weekDays;
    private LongHashMap<DayDef> specialDays;

    private final Object lock = new Object();
    private final IndexedSet<Integer, Day> idCache = IndexedSet.createInt(Day::getDayId);
    private final IndexedSet<Integer, Day> ymdCache = IndexedSet.createInt(Day::getYearMonthDay);

    private final AtomicLong creationCounter = new AtomicLong();
    private final AtomicLong usageCounter = new AtomicLong();

    private Schedule(String def) {
        this(def, DEFAULTS); // Atomic volatile read.
    }

    private Schedule(String def, Defaults defaults) {
        if (!VENUE_PATTERN.matcher(def).matches())
            throw new IllegalArgumentException("broken schedule " + def);
        this.def = def;
        init(defaults);
    }

    private void init(Defaults defaults) {
        String venue = def.substring(0, def.indexOf('('));
        Map<String, String> props = new HashMap<>();
        if (defaults.venues.containsKey(venue))
            props.putAll(defaults.venues.get(venue));
        props.putAll(readProps(def.substring(def.indexOf('(') + 1, def.length() - 1)));
        String name = props.get("name");
        if (name == null)
            name = venue;
        String tz = props.get("tz");
        if (tz == null || tz.isEmpty())
            throw new IllegalArgumentException("missing time zone for " + def);
        TimeZone timeZone;
        try {
            timeZone = TimeUtil.getTimeZone(tz);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown time zone for " + def + ": " + tz);
        }
        LongHashSet holidays = readDays("holiday", defaults.holidays, props.get("hd"));
        LongHashSet shortdays = readDays("short", defaults.shortdays, props.get("sd"));
        Matcher ec = getStrategy(def, props, "sds", "ec", SDS_EC_PATTERN);
        long earlyClose = ec == null ? 0 : new TimeDef(def, ec.group(1)).offset();
        Matcher jntd = getStrategy(def, props, "hds", "jntd", HDS_JNTD_PATTERN);
        int joinNextTradingDay = jntd == null ? 0 : Integer.parseInt(jntd.group(1));
        String td = props.get("td");
        if (td == null)
            td = "12345";
        String de = props.get("de");
        if (de == null)
            de = "+0000";
        String rt = props.get("rt");

        TimeDef dayEnd = new TimeDef(def, de);
        TimeDef dayStart = new TimeDef(dayEnd, -1);
        TimeDef resetTime = rt == null ? null : new TimeDef(def, rt);
        DayDef[] weekDays = new DayDef[9];
        for (int i = 0; i <= 8; i++) {
            String s = props.get(String.valueOf(i));
            if (s != null)
                weekDays[i] = new DayDef(def, dayStart, dayEnd, resetTime, s);
        }
        if (weekDays[8] == null) {
            DayDef d0 = weekDays[0];
            weekDays[8] = d0 == null ? new DayDef(def, dayStart, dayEnd, resetTime, "") : new DayDef(def, d0.dayStart, d0.dayEnd, d0.resetTime, "");
        }
        for (int i = 1; i <= 7; i++)
            if (weekDays[i] == null)
                if ((weekDays[i] = weekDays[td.indexOf('0' + i) >= 0 ? 0 : 8]) == null)
                    throw new IllegalArgumentException("incomplete schedule for " + def);

        LongHashMap<DayDef> specialDays = new LongHashMap<>();
        for (Map.Entry<String, String> e : props.entrySet())
            if (e.getKey().length() == 8 && e.getKey().matches("\\d{8}"))
                specialDays.put(Integer.parseInt(e.getKey()), new DayDef(def, dayStart, dayEnd, resetTime, e.getValue()));
        if (specialDays.isEmpty())
            specialDays = EMPTY_MAP;

        synchronized (lock) {
            this.name = name;
            this.calendar = Calendar.getInstance(timeZone);
            this.rawOffset = calendar.getTimeZone().getRawOffset();
            this.dayOffset = dayStart.offset();
            this.holidays = holidays;
            this.shortdays = shortdays;
            this.earlyClose = earlyClose;
            this.joinNextTradingDay = joinNextTradingDay;
            this.weekDays = weekDays;
            this.specialDays = specialDays;

            idCache.clear();
            ymdCache.clear();

            checkEarlyClose();
        }
    }

    private void checkEarlyClose() {
        if (earlyClose == 0)
            return;
        for (PrimitiveIterator.OfLong it = shortdays.longIterator(); it.hasNext();) {
            long sd = it.nextLong();
            if (holidays.contains(sd))
                continue;
            if (isTooShortForEarlyClose((int) sd))
                return;
        }
    }

    private boolean isTooShortForEarlyClose(int yearMonthDay) {
        int dayId = DayUtil.getDayIdByYearMonthDay(yearMonthDay);
        DayDef dayDef = getDayDef(dayId, yearMonthDay);
        int lastRegular = getLastRegularSessionIndex(dayDef);
        if (lastRegular < dayDef.sessions.length) {
            SessionDef session = dayDef.sessions[lastRegular];
            long time = getTimeByDayId(dayId);
            if (session.isTooShort(calendar, time, earlyClose)) {
                log.warn("Last regular session of short day " + yearMonthDay +
                    " in " + def + " is too short for early close strategy. Day won't be shortened.");
                return true;
            }
        }
        return false;
    }

    private Day getDay(int dayId) {
        Day day = idCache.getByKey(dayId);
        if (day == null)
            synchronized (lock) {
                day = idCache.getByKey(dayId);
                if (day == null) {
                    checkCacheSize();
                    day = createDay(dayId);
                    idCache.put(day);
                    ymdCache.put(day);
                }
            }
        day.usageCounter = usageCounter.get();
        return day;
    }

    // GuardedBy: lock
    private void checkCacheSize() {
        if (idCache.size() < CACHE_LIMIT)
            return;
        Day[] days = idCache.toArray(new Day[idCache.size()]);
        QuickSort.sort(days, USAGE_COMPARATOR);
        for (int i = days.length - CACHE_RETAIN; --i >= 0;) {
            idCache.removeKey(days[i].getDayId());
            ymdCache.removeKey(days[i].getYearMonthDay());
        }
    }

    private long getTimeByDayId(int dayId) {
        return dayId * DAY_LENGTH - rawOffset + DAY_LENGTH / 2;
    }

    private void addTradingDaySessions(Day day, DayDef def, long time, boolean shortday, List<Session> sessions) {
        int lastRegular = (shortday && earlyClose != 0) ? getLastRegularSessionIndex(def) : def.sessions.length;
        if (lastRegular < def.sessions.length) {
            SessionDef session = def.sessions[lastRegular];
            if (session.isTooShort(calendar, time, earlyClose))
                // last regular session is too short for early close short day strategy - switch off it
                lastRegular = def.sessions.length;
        }
        long shift = -earlyClose;
        for (int i = 0; i < def.sessions.length; i++) {
            SessionDef session = def.sessions[i];
            if (i < lastRegular)
                sessions.add(session.create(day, calendar, time, 0, 0));
            else if (i == lastRegular)
                sessions.add(session.create(day, calendar, time, 0, shift));
            else if (i < def.sessions.length - 1)
                sessions.add(session.create(day, calendar, time, shift, shift));
            else
                sessions.add(session.create(day, calendar, time, shift, 0));
        }
        if (lastRegular == def.sessions.length - 1) {
            long endTime = sessions.get(sessions.size() - 1).getEndTime();
            sessions.add(new Session(day, SessionType.NO_TRADING, endTime, endTime - shift));
        }
    }

    private int getEarliestTradingHolidayOffset(int dayId, int maxOffset) {
        int earliestOffset = -1;
        for (int offset = 1; offset <= maxOffset; offset++) {
            int yearMonthDay = DayUtil.getYearMonthDayByDayId(dayId - offset);
            boolean isHoliday = holidays.contains(yearMonthDay);
            boolean isTrading = getDayDef(dayId - offset, yearMonthDay).isTrading();
            if (!isHoliday && isTrading) {
                // found true trading day - abort farther search and return best result
                return earliestOffset;
            }
            if (isHoliday && isTrading) {
                // found candidate - update best result and look for even better one
                earliestOffset = offset;
            }
        }
        return earliestOffset;
    }

    private int getNextTradingDayOffset(int dayId, int maxOffset) {
        for (int offset = 0; offset <= maxOffset; offset++) {
            int yearMonthDay = DayUtil.getYearMonthDayByDayId(dayId + offset);
            if (!holidays.contains(yearMonthDay) && getDayDef(dayId + offset, yearMonthDay).isTrading()) {
                return offset;
            }
        }
        return -1;
    }

    private DayDef getDayDef(int dayId, int yearMonthDay) {
        DayDef dayDef = specialDays.get(yearMonthDay);
        if (dayDef == null)
            dayDef = weekDays[dayOfWeek(dayId)];
        return dayDef;
    }

    private void createJntdDays(int startDayId, int endDayId) {
        int startYearMonthDay = DayUtil.getYearMonthDayByDayId(startDayId);
        int endYearMonthDay = DayUtil.getYearMonthDayByDayId(endDayId);
        DayDef startDef = getDayDef(startDayId, startYearMonthDay);
        DayDef endDef = getDayDef(endDayId, endYearMonthDay);

        // create all initial empty days
        long startTime = startDef.dayStart.get(calendar, getTimeByDayId(startDayId));
        for (int dayId = startDayId; dayId < endDayId; dayId++) {
            int yearMonthDay = DayUtil.getYearMonthDayByDayId(dayId);
            Day day = new Day(this, dayId, yearMonthDay, holidays.contains(yearMonthDay), shortdays.contains(yearMonthDay), startTime);
            day.setSessions(Collections.singletonList(new Session(day, SessionType.NO_TRADING, startTime, startTime)));
            idCache.put(day);
            ymdCache.put(day);
        }

        // create final trading day
        boolean isShortday = shortdays.contains(endYearMonthDay);
        long resetTime = startDef.resetTime.get(calendar, getTimeByDayId(startDayId));
        Day endDay = new Day(this, endDayId, endYearMonthDay, false, isShortday, resetTime);

        ArrayList<Session> sessions = new ArrayList<>();
        addTradingDaySessions(endDay, startDef, getTimeByDayId(startDayId), false, sessions);
        for (int dayId = startDayId + 1; dayId < endDayId; dayId++) {
            DayDef dayDef = getDayDef(dayId, DayUtil.getYearMonthDayByDayId(dayId));
            long time = getTimeByDayId(dayId);
            sessions.add(new Session(endDay, SessionType.NO_TRADING,
                dayDef.dayStart.get(calendar, time), dayDef.dayEnd.get(calendar, time)));
        }
        addTradingDaySessions(endDay, endDef, getTimeByDayId(endDayId), isShortday, sessions);
        sessions.trimToSize();
        endDay.setSessions(Collections.unmodifiableList(sessions));

        idCache.put(endDay);
        ymdCache.put(endDay);
    }

    // GuardedBy: lock
    private Day createDay(int dayId) {
        creationCounter.incrementAndGet();

        if (joinNextTradingDay != 0) {
            int tradingOffset = getNextTradingDayOffset(dayId, joinNextTradingDay);
            if (tradingOffset >= 0) {
                int endDayId = dayId + tradingOffset;
                int earliestOffset = getEarliestTradingHolidayOffset(endDayId, joinNextTradingDay);
                if (earliestOffset >= tradingOffset) {
                    createJntdDays(endDayId - earliestOffset, endDayId);
                    return idCache.getByKey(dayId);
                }
            }
        }

        long time = getTimeByDayId(dayId);
        int yearMonthDay = DayUtil.getYearMonthDayByDayId(dayId);
        boolean isHoliday = holidays.contains(yearMonthDay);
        boolean isShortDay = shortdays.contains(yearMonthDay);
        DayDef dayDef = getDayDef(dayId, yearMonthDay);
        Day day = new Day(this, dayId, yearMonthDay, isHoliday, isShortDay, dayDef.resetTime.get(calendar, time));
        if (isHoliday) {
            day.setSessions(Collections.singletonList(
                new Session(day, SessionType.NO_TRADING, dayDef.dayStart.get(calendar, time), dayDef.dayEnd.get(calendar, time))));
        } else {
            ArrayList<Session> sessions = new ArrayList<>();
            addTradingDaySessions(day, dayDef, time, isShortDay, sessions);
            sessions.trimToSize();
            day.setSessions(Collections.unmodifiableList(sessions));
        }
        return day;
    }

    private static int getLastRegularSessionIndex(DayDef def) {
        for (int i = def.sessions.length - 1; i >= 0; i--)
            if (def.sessions[i].type == SessionType.REGULAR)
                return i;
        return def.sessions.length;
    }

    private static final LongHashSet EMPTY_SET = new LongHashSet();
    private static final LongHashMap<DayDef> EMPTY_MAP = new LongHashMap<>();

    private static class Defaults {
        long date;

        final Map<String, LongHashSet> holidays = new HashMap<>();
        final Map<String, LongHashSet> shortdays = new HashMap<>();
        final Map<String, Map<String, String>> venues = new HashMap<>();

        Defaults() {}
    }

    private static volatile Defaults DEFAULTS = new Defaults();

    private static final SynchronizedIndexedSet<String, Schedule> SCHEDULES = SynchronizedIndexedSet.create((IndexerFunction<String, Schedule>) schedule -> schedule.def);

    private static Schedule getSchedule(String def) {
        Schedule schedule = SCHEDULES.getByKey(def);
        if (schedule == null)
            schedule = SCHEDULES.putIfAbsentAndGet(new Schedule(def));
        return schedule;
    }

    static {
        try (InputStream in = Schedule.class.getResourceAsStream("schedule.properties")) {
            if (in != null)
                setDefaults(in);
        } catch (Throwable t) {
            log.error("Unexpected error", t);
        }
        downloadDefaults(SystemProperties.getProperty(DOWNLOAD_PROPERTY, null));
    }

    private static String setDefaults(InputStream in) throws IOException {
        Defaults newDef = new Defaults();
        //noinspection IOResourceOpenedButNotSafelyClosed
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String line; (line = br.readLine()) != null;) {
            while (line.endsWith("\\")) {
                line = line.substring(0, line.length() - 1);
                String nextLine = br.readLine();
                if (nextLine == null)
                    break;
                line = line + nextLine;
            }
            if (line.startsWith("date=")) {
                newDef.date = TimeFormat.GMT.parse(line.substring("date=".length())).getTime();
                continue;
            }
            int dot = line.indexOf('.');
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || dot <= 0 || eq <= 0 || dot > eq)
                continue;
            String key = line.substring(0, eq);
            String subkey = key.substring(dot + 1);
            String value = line.substring(eq + 1);
            if (key.startsWith("hd.")) {
                if (newDef.holidays.put(subkey, readDays("holiday", newDef.holidays, value)) != null)
                    throw new IllegalArgumentException("duplicate holiday list " + line);
            } else if (key.startsWith("sd.")) {
                if (newDef.shortdays.put(subkey, readDays("short", newDef.shortdays, value)) != null)
                    throw new IllegalArgumentException("duplicate short day list " + line);
            } else if (key.startsWith("tv.")) {
                if (newDef.venues.put(subkey, readProps(value)) != null)
                    throw new IllegalArgumentException("duplicate venue " + line);
            }
        }
        // Validate each venue using simple un-cached schedule
        for (String venue : newDef.venues.keySet()) {
            new Schedule(venue + "(0=)", newDef);
        }
        Defaults oldDef = DEFAULTS; // Atomic volatile read.
        if (newDef.date < oldDef.date)
            return "older than current - ignored";
        DEFAULTS = newDef; // Atomic volatile write.
        if (newDef.holidays.equals(oldDef.holidays) && newDef.shortdays.equals(oldDef.shortdays) && newDef.venues.equals(oldDef.venues))
            return "identical to current";
        for (Schedule schedule : SCHEDULES)
            try {
                schedule.init(newDef);
            } catch (Throwable t) {
                log.error("Unexpected error", t);
            }
        return "newer than current - applied";
    }

    private static LongHashSet readDays(String dayType, Map<String, LongHashSet> refs, String list) {
        if (list == null || list.isEmpty())
            return EMPTY_SET;
        LongHashSet days = new LongHashSet();
        LongHashSet ref = null;
        for (String s : list.split(",")) {
            if (s.isEmpty())
                continue;
            boolean minus = s.startsWith("-");
            boolean intersect = s.startsWith("*");
            if (minus || intersect)
                s = s.substring(1);
            ref = refs.get(s);
            if (ref == null) {
                try {
                    int d = Integer.parseInt(s);
                    if (minus) {
                        days.remove(d);
                    } else if (intersect) {
                        days.removeIf(day -> day != d);
                    } else {
                        days.add(d);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("cannot find " + dayType +
                        " day list " + s + " when parsing " + list);
                }
            } else {
                if (minus) {
                    days.removeAll(ref);
                } else if (intersect) {
                    days.retainAll(ref);
                } else {
                    days.addAll(ref);
                }
            }
        }
        if (days.isEmpty())
            return EMPTY_SET;
        if (ref != null && ref.equals(days))
            return ref;
        return days;
    }

    private static Map<String, String> readProps(String props) {
        Map<String, String> m = new HashMap<>();
        for (String s : props.split(";")) {
            String[] ss = s.split("=", -1);
            for (int i = 0; i < ss.length - 1; i++)
                m.put(ss[i], ss[ss.length - 1]);
        }
        return m;
    }
}
