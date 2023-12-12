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
package com.devexperts.util;

import com.devexperts.logging.Logging;

import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Utility class for parsing and formatting dates and times in ISO-compatible format.
 */
public class TimeFormat {

    /**
     * An instance of TimeFormat that corresponds to default timezone as returned by
     * {@link TimeZone#getDefault()} method.
     */
    public static final TimeFormat DEFAULT = getInstance(TimeZone.getDefault());

    /**
     * An instance of TimeFormat that corresponds to GMT timezone as returned by
     * {@link TimeZone#getTimeZone(String) TimeZone.getTimeZone("GMT")}.
     */
    public static final TimeFormat GMT = getInstance(TimeZone.getTimeZone("GMT"));

    /**
     * Returns TimeFormat instance for a specified timezone.
     * @param timezone timezone for TimeFormat.
     * @throws NullPointerException if timezone is null.
     */
    public static TimeFormat getInstance(TimeZone timezone) {
        // make defensive copy of timezone
        timezone = (TimeZone) timezone.clone();
        // create hierarchy of formats
        Formats formats = new Formats(timezone);
        TimeFormat fullIsoTF = new TimeFormat(timezone, formats, formats.fullIsoFmt, null, null, null);
        TimeFormat millisTimezoneTF = new TimeFormat(timezone, formats, formats.millisTimezoneFmt, null, null, fullIsoTF);
        TimeFormat timezoneTF = new TimeFormat(timezone, formats, formats.timezoneFmt, null, millisTimezoneTF, fullIsoTF);
        TimeFormat millisTF = new TimeFormat(timezone, formats, formats.millisFmt, millisTimezoneTF, null, fullIsoTF);
        return new TimeFormat(timezone, formats, formats.defaultFmt, timezoneTF, millisTF, fullIsoTF);
    }

    /**
     * Changes time zone for {@link #DEFAULT} instance on the current and new threads.
     * <b>This method is not thread-safe.</b> It is designed <b>for use in unit-tests only</b>.
     * Do not use this method in production code. It prints warning to the log when used.
     *
     * @deprecated For internal tests only. May be removed in future QD releases.
     */
    @Deprecated()
    public static void setDefaultTimeZone(TimeZone timezone) {
        Logging.getLogging(TimeFormat.class).warn(
            "Changing DEFAULT time zone to " + timezone.getID() + ". Do not use in production code.");
        DEFAULT.timezone = (TimeZone) timezone.clone();
        DEFAULT.formats.setTimeZone(DEFAULT.timezone);
    }

    // Add 1 extra day to accomodate time zone offset (up to 14 hours)
    private static final long MIN_TIME = (DayUtil.getDayIdByYearMonthDay(10101) - 1) * TimeUtil.DAY;
    private static final long MAX_TIME = (DayUtil.getDayIdByYearMonthDay(99991231) + 2) * TimeUtil.DAY;

    private static final char END_CHAR = '$';
    private static final char DATE_SEPARATOR = '-';
    private static final char TIME_SEPARATOR = ':';

    private static final char[] DATE_TIME_SEPARATORS;

    static {
        char[] dateTimeSeparators = {'-', ' ', 't', 'T'};
        Arrays.sort(dateTimeSeparators);
        DATE_TIME_SEPARATORS = dateTimeSeparators;
    }

    // ------------------------ instance ------------------------

    private TimeZone timezone;

    private final Formats formats;
    private final Format format;
    private final TimeFormat withTimeZone;
    private final TimeFormat withMillis;
    private final TimeFormat asFullIso;

    private TimeFormat(TimeZone timezone, Formats formats, Format format,
        TimeFormat withTimeZone, TimeFormat withMillis, TimeFormat asFullIso)
    {
        this.timezone = timezone;
        this.formats = formats;
        this.format = format;
        this.withTimeZone = withTimeZone == null ? this : withTimeZone;
        this.withMillis = withMillis == null ? this : withMillis;
        this.asFullIso = asFullIso == null ? this : asFullIso;
    }

    /**
     * Returns TimeFormat instance that also includes timezone into string when using {@link #format(Date) format} method.
     */
    public TimeFormat withTimeZone() {
        return withTimeZone;
    }

    /**
     * Returns TimeFormat instance that also includes milliseconds into string when using {@link #format(Date) format} method.
     */
    public TimeFormat withMillis() {
        return withMillis;
    }

    /**
     * Returns TimeFormat instance that produces full ISO8610 string of "yyyy-MM-dd'T'HH:mm:ss.SSSX".
     */
    public TimeFormat asFullIso() {
        return asFullIso;
    }

    /**
     * Reads Date from String.
     * This method is designed to understand
     * <a href="http://en.wikipedia.org/wiki/ISO_8601">ISO 8601</a> formatted date and time.
     * It accepts the following formats:
     *
     * <ul>
     * <li>
     * <b><tt>0</tt></b> is parsed as zero time.
     * </li>
     * <li>
     * <b><tt>'-'&lt;time-period&gt;</tt></b> it is parsed as &lt;current time&gt; - &lt;time-period&gt;.
     * TimePeriod here is parsed by {@link com.devexperts.util.TimePeriod#valueOf(String)}.
     * </li>
     * <li>
     * <b><tt>&lt;long-value-in-milliseconds&gt;</tt></b> It should be positive and have at least 9 digits
     * (otherwise it could not be distinguished from date in format <tt>'yyyymmdd'</tt>).
     * Each date since 1970-01-03 can be represented in this form.
     * </li>
     * <li>
     * <b><tt>&lt;date&gt;[('T'|'t'|'-'|' ')&lt;time&gt;][&lt;timezone&gt;]</tt></b>
     * If time is missing it is supposed to be <tt>'00:00:00'</tt>.
     * </li>
     * <li>
     * <b><tt>['T'|'t']&lt;time&gt;[&lt;timezone&gt;]</tt></b>
     * In this case current date is used.
     * </li>
     * </ul>
     * Here
     * <ul>
     * <li>
     * <b><tt>&lt;date&gt;</tt></b> is one of
     *     <ul>
     *     <li><b><tt>yyyy-MM-dd</tt></b>
     *     <li><b><tt>yyyyMMdd</tt></b>
     *     </ul>
     * </li>
     * <li>
     * <b><tt>&lt;time&gt;</tt></b> is one of
     *     <ul>
     *     <li><b><tt>HH:mm:ss[.sss]</tt></b>
     *     <li><b><tt>HHmmss[.sss]</tt></b>
     *     <li><b><tt>HH:mm</tt></b>
     *     <li><b><tt>HHmm</tt></b>
     *     <li><b><tt>HH</tt></b>
     *     </ul>
     * </li>
     * <li>
     * <b><tt>&lt;timezone&gt;</tt></b> is one of
     *     <ul>
     *     <li><b><tt>[+-]HH:mm</tt></b>
     *     <li><b><tt>[+-]HHmm</tt></b>
     *     <li><b><tt>[+-]HH</tt></b>
     *     <li><b><tt>Z</tt></b> for UTC.
     *     <li>or any timezone that can be parsed by {@link SimpleDateFormat}.
     *     </ul>
     * </li>
     * </ul>
     *
     * @see SimpleDateFormat
     * @param value String value to parse.
     * @return Date parsed from <tt>value</tt>.
     * @throws InvalidFormatException if <tt>value</tt> has wrong format.
     * @throws NullPointerException if <tt>value == null</tt>.
     */
    public Date parse(String value) throws InvalidFormatException, NullPointerException {
        if (value == null)
            throw new NullPointerException("Value is null");
        String v = value.trim();
        if (v.isEmpty())
            throw new InvalidFormatException("Cannot parse date-time from empty string");
        if (v.equals("0"))
            return new Date(0);

        // fast path - matches date & time formats (with zone!) that are produced by this class
        if (hasStandardDate(v) && hasStandardTime(v) && hasStandardZone(v)) {
            boolean stdMillis = hasStandardMillis(v);
            Format fmt = stdMillis ? formats.millisTimezoneFmt : formats.timezoneFmt;
            if (v.length() == fmt.standardLength) {
                // Fast path validated (one of the standard formats recognized)
                long time = TimeUtil.DAY *
                    DayUtil.getDayIdByYearMonthDay(getNum2(v, 0) * 100 + getNum2(v, 2), getNum2(v, 4), getNum2(v, 6)) +
                    TimeUtil.HOUR * getNum2(v, 9) + TimeUtil.MINUTE * getNum2(v, 11) + TimeUtil.SECOND * getNum2(v, 13);
                if (stdMillis)
                    time += getNum3(v, 16);
                int n = v.length();
                long offset = TimeUtil.HOUR * getNum2(v, n - 4) + TimeUtil.MINUTE * getNum2(v, n - 2);
                if (v.charAt(n - 5) == '+') {
                    time -= offset;
                } else {
                    time += offset;
                }
                return new Date(time);
            }
        }

        return parseSlowImpl(v);
    }

    private Date parseSlowImpl(String original) throws InvalidFormatException {
        // '-'<TimePeriod>
        if (original.charAt(0) == '-') {
            String v = original.substring(1);
            TimePeriod p = TimePeriod.valueOf(v);
            return new Date(System.currentTimeMillis() - p.getTime());
        }

        // single long value in millis
        if (longValueInMillis(original)) {
            try {
                return new Date(Long.parseLong(original));
            } catch (NumberFormatException e) {
                badDateTime(original);
            }
        }

        // common case
        String v = original + END_CHAR;

        StringBuilder buffer = new StringBuilder();
        int pos;

        boolean timeShouldFollow = true;
        boolean parsedDate = false;
        if (Character.toUpperCase(v.charAt(0)) != 'T') {
            // try to parse date
            pos = tryParseDate(v, buffer);
            if (pos > 0) {
                // parsed date
                parsedDate = true;
                if (Arrays.binarySearch(DATE_TIME_SEPARATORS, v.charAt(pos)) < 0) {
                    timeShouldFollow = false;
                } else {
                    pos++;
                }
            }
        } else {
            pos = 1;
        }

        if (!parsedDate) {
            // use current date
            buffer.append(formats.dateFmt.get().format(new Date()));
        }
        buffer.append('-');

        boolean millis = false;

        if (timeShouldFollow) {
            // -------------- time --------------
            // HH
            pos = copyDigits(buffer, v, pos, 2, v);
            boolean separator = (v.charAt(pos) == TIME_SEPARATOR);
            if (separator)
                pos++;
            if (!isDigit(v.charAt(pos))) {
                if (separator)
                    badDateTime(original);
                // time is 'hh'
                buffer.append("0000"); // mm:ss
            } else {
                // mm
                pos = copyDigits(buffer, v, pos, 2, v);
                boolean secondsShouldFollow = false;
                char c = v.charAt(pos);
                if (c == TIME_SEPARATOR) {
                    if (!separator)
                        badDateTime(original);
                    secondsShouldFollow = true;
                    pos++;
                    c = v.charAt(pos);
                } else {
                    if (separator && isDigit(c))
                        badDateTime(original);
                }
                if (!isDigit(c)) {
                    if (secondsShouldFollow)
                        badDateTime(original);
                    buffer.append("00"); // ss
                } else {
                    // ss
                    pos = copyDigits(buffer, v, pos, 2, v);
                    if (v.charAt(pos) == '.') {
                        // SSS
                        pos++;
                        buffer.append('.');
                        pos = copyDigits(buffer, v, pos, 3, v);
                        millis = true;
                    }
                }
            }
        } else {
            // use 00:00:00 time
            buffer.append("000000");
        }

        // zone
        Format fmt;
        if (v.charAt(pos) == END_CHAR) {
            fmt = millis ? formats.millisFmt : formats.defaultFmt;
        } else {
            String tz = v.substring(pos, v.length() - 1);
            // convert ISO8601 timezone to RFC822 timezone that SimpleDateFormat parses with 'Z' specification
            if (tz.equals("Z")) {
                buffer.append("+0000");
            } else if (tz.length() == 3 && (tz.charAt(0) == '+' || tz.charAt(0) == '-')) {
                buffer.append(tz).append("00");
            } else if (tz.length() == 6  && (tz.charAt(0) == '+' || tz.charAt(0) == '-') && tz.charAt(3) == ':') {
                buffer.append(tz.substring(0, 3)).append(tz.substring(4, 6));
            } else {
                buffer.append(tz);
            }
            fmt = millis ? formats.millisTimezoneFmt : formats.timezoneFmt;
        }

        // try to parse good value
        ParsePosition parsePos = new ParsePosition(0);
        String goodValue = buffer.toString();

        // NOTE: DateFormat.parse method breaks set time-zone, so it needs to be reset to good value after parsing
        DateFormat dateFormat = fmt.get();
        Date res = dateFormat.parse(goodValue, parsePos);
        dateFormat.setTimeZone(fmt.getTimeZone());

        if ((parsePos.getIndex() != goodValue.length())) {
            // check that there is no extra symbols left unparsed
            badDateTime(original);
        }
        if (parsePos.getErrorIndex() != -1) {
            // check that everything was parsed successfully.
            badDateTime(original);
        }
        return res;
    }

    private int copyDigits(StringBuilder buffer, String v, int pos, int n, String value) {
        for (int i = 0; i < n; i++) {
            char c = v.charAt(pos);
            if (!isDigit(c))
                badDateTime(value);
            buffer.append(c);
            pos++;
        }
        return pos;
    }

    private static boolean hasStandardDate(String v) {
        // yyyyMMdd
        if (v.length() < 8)
            return false;
        for (int i = 0; i < 8; i++) {
            if (!isDigit(v.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean hasStandardTime(String v) {
        // xxxxxxxx-HHmmss
        if (v.length() < 15 || v.charAt(8) != '-')
            return false;
        for (int i = 9; i < 15; i++) {
            if (!isDigit(v.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean hasStandardMillis(String v) {
        // xxxxxxxx-xxxxxx.SSS
        if (v.length() < 19 || v.charAt(15) != '.')
            return false;
        for (int i = 16; i < 19; i++) {
            if (!isDigit(v.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean hasStandardZone(String v) {
        // [+/-]ZZZZ
        int n = v.length();
        if (n < 5)
            return false;
        char c = v.charAt(n - 5);
        if (c != '+' && c != '-')
            return false;
        for (int i = n - 4; i < n; i++) {
            if (!isDigit(v.charAt(i)))
                return false;
        }
        return true;
    }

    private static int getNum2(String v, int i) {
        return (v.charAt(i) - '0') * 10 + v.charAt(i + 1) - '0';
    }

    private static int getNum3(String v, int i) {
        return (v.charAt(i) - '0') * 100 + getNum2(v, i + 1);
    }

    private static boolean longValueInMillis(String v) {
        if (v.length() < 9)
            return false;
        for (int i = 0; i < v.length(); i++) {
            if (!isDigit(v.charAt(i)))
                return false;
        }
        return true;
    }

    private static void badDateTime(String value) throws InvalidFormatException {
        throw new InvalidFormatException("Cannot parse date-time from string \"" + value + "\"");
    }

    private int tryParseDate(String value, StringBuilder buffer) {
        int pos = 0;
        StringBuilder date = new StringBuilder(8);

        for (int i = 0; i < 4; i++) { // yyyy
            char c = value.charAt(pos++);
            if (!isDigit(c))
                return 0;
            date.append(c);
        }
        boolean separator = (value.charAt(pos) == DATE_SEPARATOR);
        if (separator)
            pos++;

        for (int i = 0; i < 2; i++) { // MM
            char c = value.charAt(pos++);
            if (!isDigit(c))
                return 0;
            date.append(c);
        }
        if (separator) {
            if (value.charAt(pos) != DATE_SEPARATOR)
                return 0;
            pos++;
        }
        for (int i = 0; i < 2; i++) { // dd
            char c = value.charAt(pos++);
            if (!isDigit(c))
                return 0;
            date.append(c);
        }
        buffer.append(date);
        return pos;
    }

    private static boolean isDigit(char c) {
        return (c >= '0') && (c <= '9');
    }

    /**
     * Converts {@link Date} object into string according to the format like <tt>yyyyMMdd-HHmmss</tt>.
     * When {@link #withTimeZone()} was used to acquire this {@link TimeFormat} instance,
     * then time zone is also included and formatted according to RFC 822 format (for example "+0300").
     * When {@link #withMillis()} was used to acquire this {@link TimeFormat} instance,
     * the milliseconds are also included as <tt>.sss</tt>.
     *
     * <p> When {@code time.getTime() == 0} this method returns string "0".
     *
     * <p> When {@code time} yyyyMMdd representation is outside [00010101, 99991231] range this method returns string
     * representing {@code time.getTime()} long value.
     *
     * @param time date and time to format.
     * @return string representation of data and time.
     * @throws NullPointerException if time is null.
     */
    public String format(Date time) throws NullPointerException {
        return format(time.getTime());
    }

    /**
     * This is a shortcut for {@link #format(Date) format}(new {@link Date#Date(long) Date}(time)).
     */
    public String format(long time) {
        if (time == 0)
            return "0";
        return formatFast(time);
    }

    private String formatFast(long time) {
        if (time < MIN_TIME || time > MAX_TIME)
            return Long.toString(time);

        long key = format.withMillis ? time : MathUtil.div(time, 1000);
        String cached = format.getCachedResult(key);
        if (cached != null)
            return cached;

        long minute = MathUtil.div(time, TimeUtil.MINUTE);
        char[] cachedMinute = format.getCachedMinute(minute);
        if (cachedMinute != null) { // Fast path
            // Minute cache is used only with offsets divisible by minutes, so offset value is not needed here
            int secondsAndMillis = (int) (time - minute * TimeUtil.MINUTE);
            char[] chars = cachedMinute.clone();
            int offset = format.secondsPos;
            offset = putNum2(chars, offset, secondsAndMillis / 1000);
            if (format.withMillis) {
                offset++; // skip '.'
                putNum3(chars, offset, secondsAndMillis % 1000);
            }
            String s = new String(chars);
            format.setCachedResult(key, s);
            return s;
        }

        int timeOffset = format.getTimeZone().getOffset(time);
        int ymd = DayUtil.getYearMonthDayByDayId((int) MathUtil.div(time + timeOffset, TimeUtil.DAY));
        int dayTime = (int) MathUtil.rem(time + timeOffset, TimeUtil.DAY);
        if (ymd < 10101 || ymd > 99991231)
            return Long.toString(time);

        char[] chars = new char[format.standardLength];
        int offset = 0;

        offset = putNum2(chars, offset, (ymd / 1000000) % 100);
        offset = putNum2(chars, offset, (ymd / 10000) % 100);
        if (format.withIso)
            chars[offset++] = '-';
        offset = putNum2(chars, offset, (ymd / 100) % 100);
        if (format.withIso)
            chars[offset++] = '-';
        offset = putNum2(chars, offset, ymd % 100);
        chars[offset++] = format.withIso ? 'T' : '-';
        offset = putNum2(chars, offset, (dayTime / (int) TimeUtil.HOUR) % 24);
        if (format.withIso)
            chars[offset++] = ':';
        offset = putNum2(chars, offset, (dayTime / (int) TimeUtil.MINUTE) % 60);
        if (format.withIso)
            chars[offset++] = ':';
        offset = putNum2(chars, offset, (dayTime / (int) TimeUtil.SECOND) % 60);
        if (format.withMillis) {
            chars[offset++] = '.';
            offset = putNum3(chars, offset, dayTime % 1000);
        }
        if (format.withZone) {
            if (format.withIso && timeOffset == 0) {
                chars[offset++] = 'Z';
            } else {
                chars[offset++] = timeOffset >= 0 ? '+' : '-';
                offset = putNum2(chars, offset, (Math.abs(timeOffset) / (int) TimeUtil.HOUR) % 24);
                if (format.withIso)
                    chars[offset++] = ':';
                offset = putNum2(chars, offset, (Math.abs(timeOffset) / (int) TimeUtil.MINUTE) % 60);
            }
        }

        String s = new String(chars, 0, offset);
        format.setCachedResult(key, s);
        if (timeOffset % TimeUtil.MINUTE == 0)
            format.setCachedMinute(minute, (chars.length == offset) ? chars : Arrays.copyOf(chars, offset));
        return s;
    }

    private static int putNum2(char[] chars, int offset, int v) {
        chars[offset] = (char) ('0' + (v / 10));
        chars[offset + 1] = (char) ('0' + (v % 10));
        return offset + 2;
    }

    private static int putNum3(char[] chars, int offset, int v) {
        chars[offset] = (char) ('0' + (v / 100));
        return putNum2(chars, offset + 1, v % 100);
    }

    /**
     * Returns timezone of this TimeFormat instance.
     * @return timezone of this TimeFormat instance.
     */
    public TimeZone getTimeZone() {
        return (TimeZone) timezone.clone();
    }

    private static class Format {
        private static final int CACHE_SIZE = 1 << 8; // shall be a power of 2
        // never matches any cached result
        private static final CacheEntry RESULT_STUB = new CacheEntry(Long.MIN_VALUE, null);

        private static final int MINUTES_CACHE_SIZE = 1 << 8; // shall be a power of 2
        // never matches any valid minute entry
        private static final MinuteCacheEntry MINUTE_STUB = new MinuteCacheEntry(Long.MIN_VALUE, null);

        final int standardLength;
        final int secondsPos;
        final ThreadLocal<DateFormat> threadLocal = new ThreadLocal<>();
        final DateFormat masterFormat;
        final boolean withMillis;
        final boolean withZone;
        final boolean withIso;
        final CacheEntry[] cache; // cache of exact formatting results (key is a millis or second value)
        final MinuteCacheEntry[] minutesCache;

        Format(String format, int extraLength, int secondsPos,
            TimeZone timezone, boolean withMillis, boolean withZone, boolean withIso)
        {
            standardLength = format.length() + extraLength;
            this.secondsPos = secondsPos;
            masterFormat = new SimpleDateFormat(format);
            masterFormat.setTimeZone(timezone);
            this.withMillis = withMillis;
            this.withZone = withZone;
            this.withIso = withIso;
            cache = new CacheEntry[CACHE_SIZE];
            minutesCache = new MinuteCacheEntry[MINUTES_CACHE_SIZE];
            clearCache();
        }

        DateFormat get() {
            DateFormat format = threadLocal.get();
            if (format == null) {
                format = (DateFormat) masterFormat.clone();
                threadLocal.set(format);
            }
            return format;
        }

        TimeZone getTimeZone() {
            return masterFormat.getTimeZone();
        }

        void setTimeZone(TimeZone timezone) {
            masterFormat.setTimeZone(timezone);
            threadLocal.set(null);
            clearCache();
        }

        void clearCache() {
            Arrays.fill(cache, RESULT_STUB);
            Arrays.fill(minutesCache, MINUTE_STUB);
        }

        public String getCachedResult(long key) {
            int bucket = (int) key & (CACHE_SIZE - 1);
            CacheEntry entry = cache[bucket];
            if (entry.getKey() == key)
                return entry.getValue();
            return null;
        }

        public void setCachedResult(long key, String value) {
            int bucket = (int) key & (CACHE_SIZE - 1);
            cache[bucket] = new CacheEntry(key, value);
        }

        public char[] getCachedMinute(long minute) {
            int bucket = (int) minute & (MINUTES_CACHE_SIZE - 1);
            MinuteCacheEntry cachedMinute = minutesCache[bucket];
            if (cachedMinute.getMinute() == minute)
                return cachedMinute.getTemplate();
            return null;
        }

        public void setCachedMinute(long minute, char[] template) {
            int bucket = (int) minute & (MINUTES_CACHE_SIZE - 1);
            minutesCache[bucket] = new MinuteCacheEntry(minute, template);
        }
    }

    private static class Formats {

        private static int SECS_POS_NOISO = "yyyyMMdd-HHmm".length();
        private static int SECS_POS_ISO = "yyyy-MM-ddTHH:mm:".length();

        final Format dateFmt;
        final Format defaultFmt;
        final Format timezoneFmt;
        final Format millisFmt;
        final Format millisTimezoneFmt;
        final Format fullIsoFmt;

        Formats(TimeZone timezone) {
            dateFmt = new Format("yyyyMMdd", 0, -1, timezone, false, false, false);
            defaultFmt = new Format("yyyyMMdd-HHmmss", 0, SECS_POS_NOISO, timezone, false, false, false);
            timezoneFmt = new Format("yyyyMMdd-HHmmssZ", 4, SECS_POS_NOISO, timezone, false, true, false);
            millisFmt = new Format("yyyyMMdd-HHmmss.SSS", 0, SECS_POS_NOISO, timezone, true, false, false);
            millisTimezoneFmt = new Format("yyyyMMdd-HHmmss.SSSZ", 4, SECS_POS_NOISO, timezone, true, true, false);
            fullIsoFmt = new Format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", 3 - 2, SECS_POS_ISO, timezone, true, true, true);
        }

        void setTimeZone(TimeZone timezone) {
            dateFmt.setTimeZone(timezone);
            defaultFmt.setTimeZone(timezone);
            timezoneFmt.setTimeZone(timezone);
            millisFmt.setTimeZone(timezone);
            millisTimezoneFmt.setTimeZone(timezone);
            fullIsoFmt.setTimeZone(timezone);
        }
    }

    @ThreadSafe
    static class MinuteCacheEntry {
        private final long minute;
        private final char[] template;

        public MinuteCacheEntry(long minute, char[] template) {
            this.minute = minute;
            this.template = template;
        }

        public long getMinute() {
            return minute;
        }

        public char[] getTemplate() {
            return template;
        }
    }

    @ThreadSafe
    static class CacheEntry {
        private final long key;
        private final String value;

        public CacheEntry(long key, String value) {
            this.key = key;
            this.value = value;
        }

        public long getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }
}
