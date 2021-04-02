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
package com.dxfeed.event.candle;

import java.util.HashMap;
import java.util.Map;

/**
 * Type of the candle aggregation period constitutes {@link CandlePeriod} type together
 * its actual {@link CandlePeriod#getValue() value}.
 */
public enum CandleType {
    /**
     * Certain number of ticks.
     */
    TICK("t", 0),

    /**
     * Certain number of seconds.
     */
    SECOND("s", 1000L),

    /**
     * Certain number of minutes.
     */
    MINUTE("m", 60 * 1000L),

    /**
     * Certain number of hours.
     */
    HOUR("h", 60 * 60 * 1000L),

    /**
     * Certain number of days.
     */
    DAY("d", 24 * 60 * 60 * 1000L),

    /**
     * Certain number of weeks.
     */
    WEEK("w", 7 * 24 * 60 * 60 * 1000L),

    /**
     * Certain number of months.
     */
    MONTH("mo", 30 * 24 * 60 * 60 * 1000L),

    /**
     * Certain number of option expirations.
     */
    OPTEXP("o", 30 * 24 * 60 * 60 * 1000L),

    /**
     * Certain number of years.
     */
    YEAR("y", 365 * 24 * 60 * 60 * 1000L),

    /**
     * Certain volume of trades.
     */
    VOLUME("v", 0),

    /**
     * Certain price change, calculated according to the following rules:
     * <ol>
     *     <li>high(n) - low(n) = price range</li>
     *     <li>close(n) = high(n) or close(n) = low(n)</li>
     *     <li>open(n+1) = close(n)</li>
     * </ol>
     * where n is the number of the bar.
     */
    PRICE("p", 0),

    /**
     * Certain price change, calculated according to the following rules:
     * <ol>
     *     <li>high(n) - low(n) = price range</li>
     *     <li>close(n) = high(n) or close(n) = low(n)</li>
     *     <li>open(n+1) = close(n) + tick size, if close(n) = high(n)</li>
     *     <li>open(n+1) = close(n) - tick size, if close(n) = low(n)</li>
     * </ol>
     * where n is the number of the bar.
     */
    PRICE_MOMENTUM("pm", 0),

    /**
     * Certain price change, calculated according to the following rules:
     * <ol>
     *     <li>high(n+1) - high(n) = price range or low(n) - low(n+1) = price range</li>
     *     <li>close(n) = high(n) or close(n) = low(n)</li>
     *     <li>open(n+1) = high(n), if high(n+1) - high(n) = price range</li>
     *     <li>open(n+1) = low(n), if low(n) - low(n+1) = price range</li>
     * </ol>
     * where n is the number of the bar.
     */
    PRICE_RENKO("pr", 0);

    private final String string;
    private final long periodIntervalMillis;

    CandleType(String string, long periodIntervalMillis) {
        this.string = string;
        this.periodIntervalMillis = periodIntervalMillis;
    }

    /**
     * Returns candle type period in milliseconds as closely as possible.
     * Certain types like {@link #SECOND SECOND} and
     * {@link #DAY DAY} span a specific number of milliseconds.
     * {@link #MONTH}, {@link #OPTEXP} and {@link #YEAR}
     * are approximate. Candle type period of
     * {@link #TICK}, {@link #VOLUME}, {@link #PRICE},
     * {@link #PRICE_MOMENTUM} and {@link #PRICE_RENKO}
     * is not defined and this method returns {@code 0}.
     * @return aggregation period in milliseconds.
     */
    public long getPeriodIntervalMillis() {
        return periodIntervalMillis;
    }

    /**
     * Returns string representation of this candle type.
     * The string representation of candle type is the shortest unique prefix of the
     * lower case string that corresponds to its {@link #name() name}. For example,
     * {@link #TICK} is represented as {@code "t"}, while {@link #MONTH} is represented as
     * {@code "mo"} to distinguish it from {@link #MINUTE} that is represented as {@code "m"}.
     * @return string representation of this candle price type.
     */
    @Override
    public String toString() {
        return string;
    }

    private static final Map<String, CandleType> BY_STRING = new HashMap<>();

    static {
        for (CandleType type : values())
            BY_STRING.put(type.toString(), type);
    }

    /**
     * Parses string representation of candle type into object.
     * Any string that that is a prefix of candle type {@link #name()} can be parsed
     * (including the one that was returned by {@link #toString()})
     * and case is ignored for parsing.
     * @param s string representation of candle type.
     * @return candle type.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandleType parse(String s) {
        int n = s.length();
        if (n == 0)
            throw new IllegalArgumentException("Missing candle type");
        CandleType result = BY_STRING.get(s);
        // fast path to reverse toString result
        if (result != null)
            return result;
        // slow path for everything else
        for (CandleType type : values()) {
            String name = type.name();
            if (name.length() >= n && name.substring(0, n).equalsIgnoreCase(s))
                return type;
            // Ticks, Minutes, Seconds, etc
            if (s.endsWith("s") && name.equalsIgnoreCase(s.substring(0, n - 1)))
                return type;
        }
        throw new IllegalArgumentException("Unknown candle type: " + s);
    }
}
