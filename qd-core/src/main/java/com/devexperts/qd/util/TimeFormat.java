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
package com.devexperts.qd.util;

import com.devexperts.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Utility class for parsing and formatting dates and times.
 * @deprecated Use {@link com.devexperts.util.TimeFormat}
 */
public class TimeFormat {
    /**
     * @deprecated Use {@link com.devexperts.util.TimeFormat#DEFAULT}
     */
    public static final TimeFormat DEFAULT = new TimeFormat(TimeZone.getDefault());

    /**
     * @deprecated Use {@link com.devexperts.util.TimeFormat#GMT}
     */
    public static final TimeFormat GMT = new TimeFormat(TimeUtil.getTimeZoneGmt());

    private final com.devexperts.util.TimeFormat instance;

    /**
     * Creates new TimeFormat with specified timezone.
     * @param timezone default timezone for this TimeFormat.
     * @throws NullPointerException if timezone is null.
     * @deprecated Use {@link com.devexperts.util.TimeFormat#getInstance(TimeZone)}
     */
    public TimeFormat(TimeZone timezone) {
        instance = com.devexperts.util.TimeFormat.getInstance(timezone);
    }

    /**
     * Reads Date from String.
     * @see SimpleDateFormat
     * @param value String value to parse.
     * @return Date parsed from <tt>value</tt>.
     * @throws InvalidFormatException if <tt>value</tt> has wrong format.
     * @throws NullPointerException if <tt>value == null</tt>.
     * @deprecated Use {@link com.devexperts.util.TimeFormat#parse(String)}
     */
    public Date parseDateTime(String value) throws InvalidFormatException, NullPointerException {
        try {
            return instance.parse(value);
        } catch (com.devexperts.util.InvalidFormatException e) {
            throw new InvalidFormatException(e.getMessage(), e);
        }
    }

    /**
     * Converts Date into String, formatted like <tt>yyyyMMdd-HHmmssZ</tt>.
     * Time zone is formatted according to RFC 822 format (for example "+0300").
     * @param time Date to format.
     * @return String representation of time.
     * @throws NullPointerException if time is null.
     * @deprecated Use {@link com.devexperts.util.TimeFormat} methods
     * {@link com.devexperts.util.TimeFormat#withTimeZone() withTimeZone}().{@link com.devexperts.util.TimeFormat#format(Date) format}(time)
     */
    public String formatDateTime(Date time) throws NullPointerException {
        return instance.withTimeZone().format(time);
    }

    /**
     * Converts Date into String, formatted like <tt>yyyyMMdd-HHmmss</tt>.
     * Unlike {@link #formatDateTime}, it doesn't declare time zone and
     * formats time in current time zone.
     * @param time Date to format.
     * @return String representation of time.
     * @throws NullPointerException if time is null.
     * @deprecated Use {@link com.devexperts.util.TimeFormat} method
     * {@link com.devexperts.util.TimeFormat#format(Date) format}(time)
     */
    public String formatDateTimeWithoutTimeZone(Date time) throws NullPointerException {
        return instance.format(time);
    }

    /**
     * Converts Date into String, formatted like <tt>yyyyMMdd-HHmmss.SSSZ</tt>.
     * Time zone is formatted according to RFC 822 format (for example "+0300").
     * @param time Date to format.
     * @return String representation of time.
     * @throws NullPointerException if time is null.
     * @deprecated Use {@link com.devexperts.util.TimeFormat} methods
     * {@link com.devexperts.util.TimeFormat#withTimeZone() withTimeZone}().{@link com.devexperts.util.TimeFormat#withMillis() withMillis}().{@link com.devexperts.util.TimeFormat#format(Date) format}(time)
     */
    public String formatDateTimeWithMillis(Date time) throws NullPointerException {
        return instance.withTimeZone().withMillis().format(time);
    }

    /**
     * Converts Date into String, formatted like <tt>yyyyMMdd-HHmmss.SSS</tt>.
     * Unlike {@link #formatDateTime}, it doesn't declare time zone and
     * formats time in current time zone.
     * @param time Date to format.
     * @return String representation of time.
     * @throws NullPointerException if time is null.
     * @deprecated Use {@link com.devexperts.util.TimeFormat} methods
     * {@link com.devexperts.util.TimeFormat#withMillis() withMillis}().{@link com.devexperts.util.TimeFormat#format(Date) format}(time)
     */
    public String formatDateTimeWithMillisWithoutTimeZone(Date time) throws NullPointerException {
        return instance.withMillis().format(time);
    }

    /**
     * Returns default timezone of current TimeFormat.
     * @return default timezone of current TimeFormat.
     */
    public TimeZone getTimeZone() {
        return instance.getTimeZone();
    }
}
