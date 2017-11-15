/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging;

import java.util.*;

import com.devexperts.util.TimeUtil;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.*;
import org.apache.logging.log4j.core.util.datetime.FastDateFormat;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.PerformanceSensitive;

/**
 * Converts and formats the event's date to the particular 'yyMMdd HHmmss.SSS' formatTime in a StringBuilder.
 * If date-time pattern differs from {@link #DEFAULT_PATTERN} one or {@link Constants#ENABLE_THREADLOCALS} is false
 * then will be used {@link DatePatternConverter}
 */
@Plugin(name = "dxFeedDatePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"d", "date"})
@PerformanceSensitive("allocation")
@Order(50)
@SuppressWarnings("unused") //called by Log4j2
public class DxFeedDatePatternConverter extends LogEventPatternConverter implements ArrayPatternConverter {
    private static final String DEFAULT_PATTERN = "yyMMdd HHmmss.SSS";
    private static final int DATE_LENGTH = 7;

    //works only in thread local mode
    private static class Formatter {
        private final char[] cachedBuffer = new char[DEFAULT_PATTERN.length()];
        private final FastDateFormat fastDateFormat;
        private final int[] dstOffsets = new int[25];
        private final TimeZone tz;

        private long midnightToday = 0;
        private long midnightTomorrow = 0;
        private char[] cachedDate;
        private long previousTime;

        private Formatter(String[] options) {
            this.tz = options.length > 1 && options[1] != null ? TimeZone.getTimeZone(options[1]) : TimeZone.getDefault();
            this.fastDateFormat = FastDateFormat.getInstance("yyMMdd ", tz);
        }

        void formatToBuffer(long timeMillis, StringBuilder destination) {
            if (previousTime != timeMillis) {
                formatTime(timeMillis);
                previousTime = timeMillis;
            }
            destination.append(cachedBuffer, 0, DEFAULT_PATTERN.length());
        }

        private void formatTime(long time) {
            int ms = getMillisSinceMidnight(time);
            int pos = DATE_LENGTH;

            int hourOfDay = ms / 3600000;
            int hours = hourOfDay + daylightSavingHour(hourOfDay);
            ms -= 3600000 * hourOfDay;

            int minutes = ms / 60000;
            ms -= 60000 * minutes;

            int seconds = ms / 1000;
            ms -= 1000 * seconds;

            // Hour
            int temp = hours / 10;
            cachedBuffer[pos++] = ((char) (temp + '0'));
            cachedBuffer[pos++] = ((char) (hours - 10 * temp + '0'));

            // Minute
            temp = minutes / 10;
            cachedBuffer[pos++] = ((char) (temp + '0'));
            cachedBuffer[pos++] = ((char) (minutes - 10 * temp + '0'));

            // Second
            temp = seconds / 10;
            cachedBuffer[pos++] = ((char) (temp + '0'));
            cachedBuffer[pos++] = ((char) (seconds - 10 * temp + '0'));
            cachedBuffer[pos++] = '.';

            // Millisecond
            temp = ms / 100;
            cachedBuffer[pos++] = ((char) (temp + '0'));

            ms -= 100 * temp;
            temp = ms / 10;
            cachedBuffer[pos++] = ((char) (temp + '0'));

            ms -= 10 * temp;
            cachedBuffer[pos] = ((char) (ms + '0'));
        }

        private int getMillisSinceMidnight(long currentTime) {
            if (currentTime >= midnightTomorrow || currentTime < midnightToday) {
                updateCachedDate(currentTime);
                System.arraycopy(cachedDate, 0, cachedBuffer, 0, DATE_LENGTH);
                midnightToday = getMidnight(currentTime, 0);
                midnightTomorrow = getMidnight(currentTime, 1);
                updateDaylightSavingHour();
            }
            return (int) (currentTime - midnightToday);
        }

        private long getMidnight(long time, int addDays) {
            Calendar cal = Calendar.getInstance(tz);
            cal.setTimeInMillis(time);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DATE, addDays);
            return cal.getTimeInMillis();
        }

        private void updateCachedDate(long currentTime) {
            StringBuilder result = fastDateFormat.format(currentTime, new StringBuilder());
            cachedDate = result.toString().toCharArray();
        }

        private void updateDaylightSavingHour() {
            Arrays.fill(dstOffsets, 0);
            if (tz.getOffset(midnightToday) != tz.getOffset(midnightToday + 3600000)) {
                for (int i = 0; i < dstOffsets.length; i++) {
                    long time = midnightToday + i * TimeUtil.HOUR;
                    dstOffsets[i] = (tz.getOffset(time) - tz.getRawOffset()) / 3600000;
                }
                if (dstOffsets[0] > dstOffsets[23]) { // clock is moved backwards.
                    // we obtain midnightTonight with Calendar.getInstance(TimeZone), so it already includes raw offset
                    for (int i = dstOffsets.length - 1; i >= 0; i--)
                        dstOffsets[i] -= dstOffsets[0]; //
                }
            }
        }

        private int daylightSavingHour(int hourOfDay) {
            return hourOfDay > 23 ? dstOffsets[23] : dstOffsets[hourOfDay];
        }
    }

    private final String[] options;
    private final ThreadLocal<Formatter> threadLocalFormatter = new ThreadLocal<>();
    //delegate converter, null if current converter has its own formatter
    private final DatePatternConverter delegate;

    private DxFeedDatePatternConverter(String[] options) {
        super("Date", "date");
        this.options = options == null ? null : Arrays.copyOf(options, options.length);
        this.delegate = isDefaultOptions(options) ? null : DatePatternConverter.newInstance(options);
    }

    private boolean isDefaultOptions(String[] options) {
        return options != null && options.length >= 1 && Constants.ENABLE_THREADLOCALS && options[0].equals(DEFAULT_PATTERN);
    }

    public static DxFeedDatePatternConverter newInstance(String[] options) {
        return new DxFeedDatePatternConverter(options);
    }

    private void format(Date date, StringBuilder toAppendTo) {
        format(date.getTime(), toAppendTo);
    }

    private void format(long timestampMillis, StringBuilder output) {
        if (delegate != null)
            delegate.format(timestampMillis, output);
        else
            getThreadLocalFormatter().formatToBuffer(timestampMillis, output);
    }

    private DxFeedDatePatternConverter.Formatter getThreadLocalFormatter() {
        DxFeedDatePatternConverter.Formatter result = threadLocalFormatter.get();
        if (result == null) {
            result = new Formatter(options);
            threadLocalFormatter.set(result);
        }
        return result;
    }

    @Override
    public void format(LogEvent event, StringBuilder output) {
        format(event.getTimeMillis(), output);
    }

    @Override
    public void format(Object obj, StringBuilder output) {
        if (obj instanceof Date)
            format((Date) obj, output);
        super.format(obj, output);
    }

    @Override
    public void format(StringBuilder toAppendTo, Object... objects) {
        for (Object obj : objects) {
            if (obj instanceof Date) {
                format(obj, toAppendTo);
                break;
            }
        }
    }
}
