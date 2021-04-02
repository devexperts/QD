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

/**
 * Value class for period of time with support for ISO8601 duration format.
 */
public class TimePeriod implements Serializable {
    private static final long serialVersionUID = 0;

    /**
     * Time-period of zero.
     */
    public static final TimePeriod ZERO = new TimePeriod(0);

    /**
     * Time-period of "infinity" (time of {@link Long#MAX_VALUE}).
     */
    public static final TimePeriod UNLIMITED = new TimePeriod(Long.MAX_VALUE);

    /**
     * Input representation of unlimited interval
     */
    private static final String UNLIMITED_STR = "inf";

    /**
     * Returns <code>TimePeriod</code> with <tt>value</tt> milliseconds.
     *
     * @param value value in milliseconds
     * @return <code>TimePeriod</code> with <tt>value</tt> milliseconds.
     */
    public static TimePeriod valueOf(long value) {
        return value == 0 ? ZERO : (value == Long.MAX_VALUE ? UNLIMITED : new TimePeriod(value));
    }

    /**
     * Returns <code>TimePeriod</code> represented with a given string.
     *
     * Allowable format is ISO8601 duration, but there are some simplifications and modifications available:
     * <ul>
     * <li> Letters are case insensitive.
     * <li> Letters "P" and "T" can be omitted.
     * <li> Letter "S" can be also omitted. In this case last number will be supposed to be seconds.
     * <li> Number of seconds can be fractional. So it is possible to define duration accurate within milliseconds.
     * <li> Every part can be omitted. It is supposed that it's value is zero then.
     * <li> String "inf" recognized as unlimited period.
     * </ul>
     *
     * @param value string representation
     * @return <code>TimePeriod</code> represented with a given string.
     * @throws InvalidFormatException if cannot parse <tt>value</tt>
     */
    public static TimePeriod valueOf(String value) throws InvalidFormatException {
        return valueOf(parse(value));
    }

    // value in milliseconds
    private final long value;

    protected TimePeriod(long value) {
        this.value = value;
    }

    protected static long parse(String value) {
        try {
            if (UNLIMITED_STR.equalsIgnoreCase(value))
                return UNLIMITED.getTime();
            boolean metAnyPart = false;
            value = value.toUpperCase() + '#';
            long res = 0;
            int i = 0;
            if (value.charAt(i) == 'P') {
                i++;
            }
            int j = i;
            while (Character.isDigit(value.charAt(j))) {
                j++;
            }
            if (value.charAt(j) == 'D') {
                res += Integer.parseInt(value.substring(i, j));
                metAnyPart = true;
                j++;
                i = j;
                while (Character.isDigit(value.charAt(j))) {
                    j++;
                }
            }
            res *= 24;
            if (value.charAt(j) == 'T') {
                if (i != j) {
                    throw new InvalidFormatException("Wrong time period format.");
                }
                j++;
                i = j;
                while (Character.isDigit(value.charAt(j))) {
                    j++;
                }
            }
            if (value.charAt(j) == 'H') {
                res += Integer.parseInt(value.substring(i, j));
                metAnyPart = true;
                j++;
                i = j;
                while (Character.isDigit(value.charAt(j))) {
                    j++;
                }
            }
            res *= 60;
            if (value.charAt(j) == 'M') {
                res += Integer.parseInt(value.substring(i, j));
                metAnyPart = true;
                j++;
                i = j;
                while (Character.isDigit(value.charAt(j))) {
                    j++;
                }
            }
            res *= 60 * 1000;
            if (value.charAt(j) == '.') {
                j++;
                while (Character.isDigit(value.charAt(j))) {
                    j++;
                }
            }
            if (i != j) {
                res += Math.round(Double.parseDouble(value.substring(i, j)) * 1000);
                metAnyPart = true;
            }
            boolean good = ((value.charAt(j) == 'S') && (j == value.length() - 2) && (i != j)) ||
                ((value.charAt(j) == '#') && (j == value.length() - 1));
            good &= metAnyPart;
            if (!good) {
                throw new InvalidFormatException("Wrong time period format.");
            }
            return res;
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Wrong time period format.");
        }
    }

    public String toString() {
        long v = value;
        long millis = v % 1000;
        v = v / 1000;
        long secs = v % 60;
        v = v / 60;
        long mins = v % 60;
        v = v / 60;
        long hours = v % 24;
        v = v / 24;
        long days = v;
        StringBuilder result = new StringBuilder();
        result.append('P');
        if (days > 0) {
            result.append(days).append("D");
        }
        result.append('T');
        if (hours > 0) {
            result.append(hours).append("H");
        }
        if (mins > 0) {
            result.append(mins).append("M");
        }
        if (millis > 0) {
            result.append((secs * 1000 + millis) / 1000d);
        } else {
            result.append(secs);
        }
        result.append("S");
        return result.toString();
    }

    /**
     * Returns value in milliseconds.
     *
     * @return value in milliseconds
     */
    public long getTime() {
        return value;
    }

    public int getSeconds() {
        return (int) Math.floorDiv(value, 1_000);
    }

    /**
     * Returns value in nanoseconds.
     *
     * @return value in nanoseconds
     */
    public long getNanos() {
        return 1_000_000 * value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TimePeriod that = (TimePeriod) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return (int) value;
    }
}
