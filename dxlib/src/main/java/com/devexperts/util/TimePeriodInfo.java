/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object representing a time period range with min and max values (in milliseconds).
 *
 * <p>Serialized as JSON: {@code {"min":1.5,"max":2}} (seconds with decimals).
 *
 * <p>{@link #UNKNOWN} ({@code min < 0 && max < 0}) means "not set / unknown"
 * (no info available). Serializes as {@code {"min":-1,"max":-1}}.
 *
 * <p>{@link #EMPTY} ({@code min == 0 && max == 0}) means "known to be zero"
 * (e.g. zero duration / instant). Serializes as {@code {"min":0,"max":0}}.
 *
 * <h3>Wire format for UNKNOWN</h3>
 * <p>The wire sentinel for {@link #UNKNOWN} is {@code {"min":-1,"max":-1}}.
 * On parsing, any negative values in the JSON are treated as UNKNOWN (see {@link #valueOf(String)}).
 * Non-Java consumers that receive {@code -1} seconds should interpret it as
 * "no information available" rather than as a literal negative time period.
 */
public final class TimePeriodInfo {

    /**
     * Sentinel value meaning "not set / unknown". Has negative {@code min} and {@code max}.
     */
    public static final TimePeriodInfo UNKNOWN = new TimePeriodInfo(-1000, -1000);

    /**
     * Sentinel value meaning "known to be zero". Has {@code min == 0} and {@code max == 0}.
     */
    public static final TimePeriodInfo EMPTY = new TimePeriodInfo(0, 0);

    // Strict compact JSON no whitespace, "min" first and "max" second, optional unknown trailing fields ignored.
    // JSON number grammar per RFC 7159 §6. Trailing field values may be scalars, quoted strings,
    // single-level objects, or single-level arrays — enough for forward-compatibility with future additions.
    private static final String JSON_NUMBER = "-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?";
    private static final String JSON_VALUE =
        "(?:\"(?:[^\"\\\\]|\\\\.)*\"" +   // quoted string (with escapes)
        "|\\{[^{}]*\\}" +                  // single-level object
        "|\\[[^\\[\\]]*\\]" +              // single-level array
        "|[^,}]*)";                        // bare scalar (number / true / false / null)
    private static final Pattern VALUE_PATTERN = Pattern.compile(
        "\\{\"min\":(?<min>" + JSON_NUMBER + "),\"max\":(?<max>" + JSON_NUMBER + ")" +
        "(?:,\"[A-Za-z_][A-Za-z0-9_]*\":" + JSON_VALUE + ")*\\}");

    private final long min;
    private final long max;

    private TimePeriodInfo(long min, long max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Returns a {@code TimePeriodInfo} for the given bounds, reusing the shared
     * {@link #EMPTY} sentinel for {@code (0, 0)}.
     *
     * @param min minimum period in milliseconds (non-negative)
     * @param max maximum period in milliseconds (non-negative, {@code >= min})
     * @return cached or newly allocated info
     * @throws IllegalArgumentException if {@code min} or {@code max} is negative or {@code min > max}
     */
    public static TimePeriodInfo valueOf(long min, long max) {
        if (min < 0 || max < 0)
            throw new IllegalArgumentException("min and max must be non-negative, got: (" + min + "," + max + ")");
        if (min > max)
            throw new IllegalArgumentException("min (" + min + ") must be less than or equal to max (" + max + ")");
        if (min == 0 && max == 0)
            return EMPTY;
        return new TimePeriodInfo(min, max);
    }

    /**
     * Parses from JSON format: {@code {"min":1.5,"max":2.0}} (seconds with decimals).
     * Wire parsing is lenient: any malformed or semantically invalid input — null, empty,
     * unparseable, negative bounds, or {@code min > max} — is mapped to {@link #UNKNOWN}
     * rather than thrown. This keeps the sentinel {@code {"min":-1,"max":-1}} round-tripping
     * correctly and protects consumers from peer-side bugs in wire data.
     *
     * @param value JSON string to parse
     * @return parsed info, or {@link #UNKNOWN} if the string is null, empty, or invalid
     */
    public static TimePeriodInfo valueOf(String value) {
        if (value == null)
            return UNKNOWN;
        Matcher matcher = VALUE_PATTERN.matcher(value);
        if (!matcher.matches())
            return UNKNOWN;
        long min = Math.round(FastDoubleUtil.parseDouble(matcher.group("min")) * 1000);
        long max = Math.round(FastDoubleUtil.parseDouble(matcher.group("max")) * 1000);
        if (min < 0 || max < 0 || min > max)
            return UNKNOWN;
        return valueOf(min, max);
    }

    /**
     * Returns the minimum period in milliseconds.
     * For {@link #UNKNOWN}, returns a negative sentinel value.
     * Use {@link #isUnknown()} to check before using this value arithmetically.
     */
    public long getMin() {
        return min;
    }

    /**
     * Returns the maximum period in milliseconds.
     * For {@link #UNKNOWN}, returns a negative sentinel value.
     * Use {@link #isUnknown()} to check before using this value arithmetically.
     */
    public long getMax() {
        return max;
    }

    /**
     * Returns {@code true} if this is the {@link #UNKNOWN} sentinel value.
     */
    public boolean isUnknown() {
        return min < 0 && max < 0;
    }

    /**
     * Returns a new {@code TimePeriodInfo} that is the aggregate of this and the given info.
     * The result has the minimum of both mins and the maximum of both maxes.
     * {@link #UNKNOWN} is the identity element: {@code UNKNOWN.add(x)} returns {@code x},
     * {@code x.add(UNKNOWN)} returns {@code x}, {@code UNKNOWN.add(UNKNOWN)} returns {@code UNKNOWN}.
     *
     * @param other the info to aggregate with
     * @return aggregated info
     */
    public TimePeriodInfo add(TimePeriodInfo other) {
        if (this.isUnknown())
            return other;
        if (other.isUnknown())
            return this;
        long newMin = Math.min(this.min, other.min);
        long newMax = Math.max(this.max, other.max);
        if (newMin == this.min && newMax == this.max)
            return this;
        if (newMin == other.min && newMax == other.max)
            return other;
        return new TimePeriodInfo(newMin, newMax);
    }

    /**
     * Convenience overload that aggregates a single-period value.
     *
     * @param period period in milliseconds (must be non-negative)
     * @return aggregated info
     * @throws IllegalArgumentException if the period is negative
     */
    public TimePeriodInfo add(long period) {
        if (period < 0)
            throw new IllegalArgumentException("period must be non-negative: " + period);
        if (this.isUnknown())
            return valueOf(period, period);
        long newMin = Math.min(this.min, period);
        long newMax = Math.max(this.max, period);
        if (newMin == this.min && newMax == this.max)
            return this;
        return new TimePeriodInfo(newMin, newMax);
    }

    /**
     * Returns JSON format: {@code {"min":1.5,"max":2.0}} (seconds with decimals).
     * {@link #UNKNOWN} serializes as {@code {"min":-1,"max":-1}} — any negative values
     * on the wire are treated as UNKNOWN by {@link #valueOf(String)}.
     * All instances return valid JSON.
     */
    @Override
    public String toString() {
        return "{\"min\":" + FastDoubleUtil.formatDoubleScale(min / 1000.0, 3) + ",\"max\":" +
            FastDoubleUtil.formatDoubleScale(max / 1000.0, 3) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TimePeriodInfo))
            return false;
        TimePeriodInfo that = (TimePeriodInfo) o;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(min) + Long.hashCode(max);
    }
}
