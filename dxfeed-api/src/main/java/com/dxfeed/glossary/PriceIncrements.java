/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.glossary;

import com.devexperts.util.MathUtil;

import java.io.Serializable;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Represents rules for valid price quantization for a given instrument on a certain exchange.
 * These rules are defined as a set of price ranges with associated price increments.
 * Each price increment defines what price values are valid for corresponding price range -
 * valid prices shall be divisible by corresponding price increment.
 * <p>
 * All price ranges shall be mutually exclusive and they shall cover entire space from 0 to infinity.
 * Therefore all ranges can be represented as a sequence of numbers where increments are interleaved
 * with range limits, with extreme limits (0 and infinity) omitted for short.
 * Negative space (from negative infinity to 0) uses symmetrical price ranges.
 * <p>
 * There is a special value {@link #EMPTY} which is used to represent unknown or undefined rules.
 * This value has empty textual representation and is considered to have sole increment with value 0.
 * <p>
 * See {@link #getText} and {@link #getPriceIncrements} for details about used formats and representations.
 *
 * <p><b>NOTE:</b>This class produces precise results for decimal numbers with at most 14
 * significant digits and at most 14 digits after decimal point.
 */
public class PriceIncrements implements Serializable {
    private static final long serialVersionUID = 0;

    // ========== Static API ==========

    /**
     * Empty price increments - it has empty text and sole increment with value 0.
     */
    public static final PriceIncrements EMPTY = new PriceIncrements("");

    private static final int MAX_ATTEMPTS = 5;
    private static final int CACHE_SIZE = 256;
    private static final PriceIncrements[] cache = new PriceIncrements[CACHE_SIZE];

    /**
     * Returns an instance of price increments for specified textual representation.
     * See {@link #getText} for format specification.
     *
     * @throws IllegalArgumentException if text uses wrong format or contains invalid values
     */
    public static PriceIncrements valueOf(String text) {
        if (isEmptyPriceIncrements(text)) {
            return EMPTY;
        }

        int hashCode = text.hashCode();
        int position = Math.abs(hashCode % CACHE_SIZE);
        int index = position;
        int limit = MAX_ATTEMPTS;
        PriceIncrements pi;
        do {
            pi = cache[index];
            if (pi == null) {
                // use separate var to strong guaranty of returned value for all env
                cache[index] = pi = new PriceIncrements(text);
                return pi;
            }
            if (pi.text.hashCode() == hashCode && pi.text.equals(text)) {
                return pi;
            }
        } while (limit-- > 0 && (index = ((index + 1) % CACHE_SIZE)) != position);

        // use separate var to strong guaranty of returned value for all env
        cache[index] = pi = new PriceIncrements(text);
        return pi;
    }

    /**
     * Returns an instance of price increments for specified single increment.
     *
     * @throws IllegalArgumentException if increment uses invalid value
     */
    public static PriceIncrements valueOf(double increment) {
        if (!Double.isFinite(increment) || increment < 0)
            throw new IllegalArgumentException("increment is not a finite positive number");
        if (increment == 0)
            return EMPTY;
        return valueOf(AdditionalUnderlyings.formatDouble(increment));
    }

    /**
     * Returns an instance of price increments for specified internal representation.
     * See {@link #getPriceIncrements} for details about internal representation.
     *
     * @throws IllegalArgumentException if data contains invalid values
     */
    public static PriceIncrements valueOf(double[] increments) {
        return valueOf(format(increments));
    }

    // ========== Instance API ==========

    private final String text;
    private transient volatile double[] increments;
    private transient volatile double[] precisions;

    private PriceIncrements(String text) {
        this.text = text;
        this.increments = parse(text);
    }

    private double[] cacheIncrements() {
        double[] increments = this.increments; // Atomic read.
        if (increments == null)
            this.increments = increments = parse(text);
        return increments;
    }

    private double[] cachePrecisions() {
        double[] precisions = this.precisions; // Atomic read.
        if (precisions == null)
            this.precisions = precisions = computePrecisions(cacheIncrements());
        return precisions;
    }

    /**
     * Returns textual representation of price increments in the format:
     * <pre>
     * TEXT ::= "" | LIST
     * LIST ::= INCREMENT | RANGE "; " LIST
     * RANGE ::= INCREMENT " " UPPER_LIMIT
     * </pre>
     * Where INCREMENT is a price increment in the given price range and UPPER_LIMIT is the upper bound of that range.
     * All ranges are listed in the ascending order of upper limits and the last range is considered to extend toward
     * infinity and is therefore specified without upper limit. All increments and limits are finite positive numbers.
     * The case with empty text is a special stub used for {@link #EMPTY} value, it uses sole increment with value 0.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns internal representation of price increments as a single array of double values.
     * This array specifies all numbers from textual representation (see {@link #getText}) in the same order.
     * Therefore numbers at even positions are increments and numbers at odd positions are upper limits.
     * The array always has odd length - the infinite upper limit of last range is always omitted and
     * the first increment (for price range adjacent to 0) is always included even for {@link #EMPTY} value.
     */
    public double[] getPriceIncrements() {
        return cacheIncrements().clone();
    }

    /**
     * Returns first price increment (for price range adjacent to 0), usually the smallest one.
     * Returns 0 for {@link #EMPTY} price increments.
     */
    public double getPriceIncrement() {
        return cacheIncrements()[0];
    }

    /**
     * Returns price increment which shall be applied to the specified price.
     * If price is Not-a-Number (NaN) then first price increment is returned.
     * If price is a breakpoint between two ranges then minimum of upward and downward increments is returned.
     * This method is equivalent to calling {@link #getPriceIncrement(double, int) getPriceIncrement(price, 0)}.
     */
    public double getPriceIncrement(double price) {
        return getPriceIncrement(price, 0);
    }

    /**
     * Returns price increment which shall be applied to the specified price in the specified direction.
     * If price is Not-a-Number (NaN) then first price increment is returned.
     * If price is a breakpoint between two ranges and direction is 0 then minimum of upward and downward increments is returned.
     */
    public double getPriceIncrement(double price, int direction) {
        if (price < 0) {
            price = -price;
            direction = -direction;
        }
        if (Double.isNaN(price))
            return getPriceIncrement();
        double[] increments = cacheIncrements();
        int i = 1;
        while (i < increments.length && price > increments[i] + Math.min(increments[i - 1], increments[i + 1]) * RELATIVE_EPS)
            i += 2;
        if (direction < 0 || i >= increments.length || price < increments[i] - Math.min(increments[i - 1], increments[i + 1]) * RELATIVE_EPS)
            return increments[i - 1];
        if (direction > 0)
            return increments[i + 1];
        return Math.min(increments[i - 1], increments[i + 1]);
    }

    /**
     * Returns first price precision (for price range adjacent to 0), usually the largest one.
     * Returns 0 for {@link #EMPTY} price increments.
     */
    public int getPricePrecision() {
        return (int) cachePrecisions()[0];
    }

    /**
     * Returns price precision for the price range which contains specified price.
     * Price precision is a number of decimal digits after decimal point that are needed
     * to represent all valid prices in the given price range.
     * This method returns price precision in the interval [0, 18] inclusive.
     * If price is Not-a-Number (NaN) then first price precision is returned.
     * If price is a breakpoint between two ranges then precision of lower range is returned.
     */
    public int getPricePrecision(double price) {
        if (price < 0)
            price = -price;
        if (Double.isNaN(price))
            return getPricePrecision();
        double[] precisions = cachePrecisions();
        int i = 1;
        while (i < precisions.length && price > precisions[i])
            i += 2;
        return (int) precisions[i - 1];
    }

    /**
     * Returns specified price rounded to nearest valid value.
     * If price is Not-a-Number (NaN) then NaN is returned.
     * If appropriate price increment is 0 then specified price is returned as is.
     * This method is equivalent to calling {@link #roundPrice(double, int) roundPrice(price, 0)}.
     */
    public double roundPrice(double price) {
        return roundPrice(price, 0);
    }

    /**
     * Returns specified price rounded in the specified direction to nearest value
     * that is valid according to price increment rules.
     * If price is Not-a-Number (NaN) then NaN is returned.
     * If appropriate price increment is 0 then specified price is returned as is.
     * If direction is 0 then price is rounded to nearest valid value.
     */
    public double roundPrice(double price, int direction) {
        if (Double.isNaN(price))
            return price;
        double increment = getPriceIncrement(price, direction);
        if (increment == 0)
            return price;
        if (direction < 0)
            return floor(price, increment);
        if (direction > 0)
            return ceil(price, increment);
        return halfEven(price, increment);
    }

    /**
     * Returns specified price rounded according to specified rounding mode to nearest value
     * that is valid according to price increment rules.
     * If price is Not-a-Number (NaN) then NaN is returned.
     * If appropriate price increment is 0 then specified price is returned as is.
     */
    public double roundPrice(double price, RoundingMode mode) {
        if (Double.isNaN(price))
            return price;
        if (mode == RoundingMode.UP)
            mode = price >= 0 ? RoundingMode.CEILING : RoundingMode.FLOOR;
        else if (mode == RoundingMode.DOWN)
            mode = price >= 0 ? RoundingMode.FLOOR : RoundingMode.CEILING;
        int direction = mode == RoundingMode.CEILING ? 1 : mode == RoundingMode.FLOOR ? -1 : 0;
        double increment = getPriceIncrement(price, direction);
        if (increment == 0)
            return price;
        switch (mode) {
        case CEILING:
            return ceil(price, increment);
        case FLOOR:
            return floor(price, increment);
        case HALF_UP:
            return price >= 0 ? halfCeil(price, increment) : halfFloor(price, increment);
        case HALF_DOWN:
            return price >= 0 ? halfFloor(price, increment) : halfCeil(price, increment);
        case HALF_EVEN:
            return halfEven(price, increment);
        case UNNECESSARY:
            if (halfEven(price, increment) != price)
                throw new ArithmeticException("price is not round " + price);
            return price;
        default:
            throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    /**
     * Returns specified price incremented in the specified direction
     * by appropriate increment and then rounded to nearest valid value.
     * If price is Not-a-Number (NaN) then NaN is returned.
     * If appropriate price increment is 0 then specified price is returned as is.
     * This method is equivalent to calling {@link #incrementPrice(double, int, double) incrementPrice(price, direction, 0)}.
     *
     * @throws IllegalArgumentException if direction is 0
     */
    public double incrementPrice(double price, int direction) {
        return incrementPrice(price, direction, 0);
    }

    /**
     * Returns specified price incremented in the specified direction by the maximum of
     * specified step and appropriate increment, and then rounded to nearest valid value.
     * If price is Not-a-Number (NaN) then NaN is returned.
     * If both step and appropriate price increment are 0 then specified price is returned as is.
     * Note that step must be positive even for negative directions.
     *
     * @throws IllegalArgumentException if direction is 0 or step uses invalid value
     */
    public double incrementPrice(double price, int direction, double step) {
        if (direction == 0)
            throw new IllegalArgumentException("direction is 0");
        if (!Double.isFinite(step) || step < 0)
            throw new IllegalArgumentException("step is not a finite positive number");
        double delta = Math.max(getPriceIncrement(price, direction), step);
        return roundPrice(direction > 0 ? price + delta : price - delta);
    }

    public int hashCode() {
        return text.hashCode();
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof PriceIncrements && text.equals(((PriceIncrements) obj).text);
    }

    public String toString() {
        return text;
    }

    // ========== Internal Implementation ==========
    private static final int MAXIMUM_PRECISION = 18;
    private static final double MINIMUM_INCREMENT = 1e-18;
    private static final double RELATIVE_EPS = 1e-6;

    private static boolean isEmptyPriceIncrements(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        boolean point = false; // exist point in number
        boolean nil = false; // exist 0 in number
        int length = text.length();
        char ch = text.charAt(0);

        for (int i = ch == '-' || ch == '+' ? 1 : 0; i < length; i++) {
            ch = text.charAt(i);
            if (ch == '0') {
                nil = true;
            } else if (ch == '.' && !point) {
                point = true;
            } else {
                return false;
            }
        }
        return nil;
    }

    private static String format(double[] increments) {
        if (increments == null || increments.length == 0 || increments.length == 1 && increments[0] == 0)
            return "";
        if ((increments.length & 1) == 0)
            throw new IllegalArgumentException("Increments length is even, increments: " + Arrays.toString(increments));
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < increments.length; n += 2) {
            double inc = increments[n];
            if (!Double.isFinite(inc) || inc < MINIMUM_INCREMENT) {
                throw new IllegalArgumentException("Increment is not a finite positive number, inc: " + inc + ", " +
                    "increments: " + Arrays.toString(increments));
            }
            AdditionalUnderlyings.formatDouble(sb, inc);
            if (n + 1 < increments.length) {
                double limit = increments[n + 1];
                if (!Double.isFinite(limit) || limit < MINIMUM_INCREMENT) {
                    throw new IllegalArgumentException("Limit is not a finite positive number, limit: " + limit +
                        ", " + "increments: " + Arrays.toString(increments));
                }
                if (n > 0 && limit <= increments[n - 1] + 2 * MINIMUM_INCREMENT) {
                    throw new IllegalArgumentException("Increments are not ordered properly, increments: " +
                        Arrays.toString(increments));
                }
                sb.append(" ");
                AdditionalUnderlyings.formatDouble(sb, limit);
                sb.append("; ");
            }
        }
        return sb.toString();
    }

    private static double[] parse(String text) {
        if (text == null || text.isEmpty()) {
            return new double[] {0};
        }
        int count = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == ';') {
                count++;
            }
        }
        double[] increments = new double[2 * count + 1];
        int n = 0;
        for (int i = 0;;) {
            int k = text.indexOf(';', i);
            if (k < 0) {
                if (text.charAt(i) == ' ' || text.charAt(text.length() - 1) == ' ')
                    throw new IllegalArgumentException("inappropriate use of separators");
                double inc = AdditionalUnderlyings.parseDouble(text, i, text.length());
                if (!Double.isFinite(inc) || inc < 0 || n > 0 && inc < MINIMUM_INCREMENT)
                    throw new IllegalArgumentException("increment is not a finite positive number");
                increments[n++] = inc;
                break;
            }
            if (k >= text.length() - 2 || text.charAt(k + 1) != ' ')
                throw new IllegalArgumentException("inappropriate use of separators");
            int j = text.indexOf(' ', i);
            if (j <= i || j >= k - 1 || j < text.lastIndexOf(' ', k - 1))
                throw new IllegalArgumentException("inappropriate use of separators");
            double inc = AdditionalUnderlyings.parseDouble(text, i, j);
            if (!Double.isFinite(inc) || inc < MINIMUM_INCREMENT)
                throw new IllegalArgumentException("increment is not a finite positive number");
            double limit = AdditionalUnderlyings.parseDouble(text, j + 1, k);
            if (!Double.isFinite(limit) || limit < MINIMUM_INCREMENT)
                throw new IllegalArgumentException("limit is not a finite positive number");
            if (n > 1 && limit <= increments[n - 1] + 2 * MINIMUM_INCREMENT)
                throw new IllegalArgumentException("increments are not ordered properly");
            increments[n++] = inc;
            increments[n++] = limit;
            i = k + 2;
        }
        if (n != increments.length)
            throw new IllegalArgumentException("inappropriate use of separators");
        return increments;
    }

    private static double[] computePrecisions(double[] increments) {
        double[] precisions = new double[increments.length];
        for (int i = 0; i < increments.length; i += 2)
            precisions[i] = computePrecision(increments[i]);
        for (int i = 1; i < increments.length; i += 2)
            precisions[i] = roundDecimal(increments[i] + Math.min(increments[i - 1], increments[i + 1]) * RELATIVE_EPS);
        return precisions;
    }

    private static int computePrecision(double value) {
        for (int i = 0; i < MAXIMUM_PRECISION; i++) {
            double round = Math.floor(value + 0.5);
            double eps = Math.abs(value * RELATIVE_EPS);
            if (round >= value - eps && round <= value + eps)
                return i;
            value *= 10;
        }
        return MAXIMUM_PRECISION;
    }

    private double ceil(double price, double increment) {
        return roundDecimal(Math.ceil(price / increment - RELATIVE_EPS) * increment);
    }

    private double floor(double price, double increment) {
        return roundDecimal(Math.floor(price / increment + RELATIVE_EPS) * increment);
    }

    private double halfCeil(double price, double increment) {
        return roundDecimal(Math.floor(price / increment + 0.5 + RELATIVE_EPS) * increment);
    }

    private double halfFloor(double price, double increment) {
        return roundDecimal(Math.ceil(price / increment - 0.5 - RELATIVE_EPS) * increment);
    }

    private double halfEven(double price, double increment) {
        double scaled = price / increment;
        double res = Math.floor(scaled);
        double rem = scaled - res;
        if (rem >= 0.5 + RELATIVE_EPS || rem > 0.5 - RELATIVE_EPS && Math.floor(res / 2) * 2 != res) {
            res++;
        }
        return roundDecimal(res * increment);
    }

    private static double roundDecimal(double value) {
        return MathUtil.roundPrecision(value, 15, RoundingMode.HALF_UP);
    }
}
