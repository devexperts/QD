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
package com.dxfeed.ipf.option;

import com.devexperts.util.DayUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Series of call and put options with different strike sharing the same attributes of
 * expiration, last trading day, spc, multiplies, etc.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is <b>NOT</b> thread-safe and cannot be used from multiple threads without external synchronization.
 *
 * @param <T> The type of option instrument instances.
 */
public final class OptionSeries<T> implements Cloneable, Comparable<OptionSeries<T>> {
    int expiration;
    int lastTrade;
    double multiplier;
    double spc;
    String additionalUnderlyings;
    String mmy;
    String optionType;
    String expirationStyle;
    String settlementStyle;
    String cfi;

    private final SortedMap<Double, T> calls = new TreeMap<>();
    private final SortedMap<Double, T> puts = new TreeMap<>();

    private List<Double> strikes;

    OptionSeries() {
        additionalUnderlyings = "";
        mmy = "";
        optionType = "";
        expirationStyle = "";
        settlementStyle = "";
        cfi = "";
    }

    OptionSeries(OptionSeries<T> other) {
        this.expiration = other.expiration;
        this.lastTrade = other.lastTrade;
        this.multiplier = other.multiplier;
        this.spc = other.spc;
        this.additionalUnderlyings = other.additionalUnderlyings;
        this.mmy = other.mmy;
        this.optionType = other.optionType;
        this.expirationStyle = other.expirationStyle;
        this.settlementStyle = other.settlementStyle;
        this.cfi = other.cfi;
    }

    /**
     * Returns a shall copy of this option series.
     * Collections of calls and puts are copied, but option instrument instances are shared with original.
     * @return a shall copy of this option series.
     */
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public OptionSeries<T> clone() {
        OptionSeries<T> clone = new OptionSeries<>(this);
        clone.calls.putAll(calls);
        clone.puts.putAll(puts);
        return clone;
    }

    /**
     * Returns day id of expiration.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090117).
     * @return day id of expiration.
     */
    public int getExpiration() {
        return expiration;
    }

    /**
     * Returns day id of last trading day.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090116).
     * @return day id of last trading day.
     */
    public int getLastTrade() {
        return lastTrade;
    }

    /**
     * Returns market value multiplier.
     * Example: 100, 33.2.
     * @return market value multiplier.
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Returns shares per contract for options.
     * Example: 1, 100.
     * @return shares per contract for options.
     */
    public double getSPC() {
        return spc;
    }

    /**
     * Returns additional underlyings for options, including additional cash.
     * It shall use following format:
     * <pre>
     *     &lt;VALUE&gt; ::= &lt;empty&gt; | &lt;LIST&gt;
     *     &lt;LIST&gt; ::= &lt;AU&gt; | &lt;AU&gt; &lt;semicolon&gt; &lt;space&gt; &lt;LIST&gt;
     *     &lt;AU&gt; ::= &lt;UNDERLYING&gt; &lt;space&gt; &lt;SPC&gt; </pre>
     * the list shall be sorted by &lt;UNDERLYING&gt;.
     * Example: "SE 50", "FIS 53; US$ 45.46".
     * @return additional underlyings for options, including additional cash.
     */
    public String getAdditionalUnderlyings() {
        return additionalUnderlyings;
    }

    /**
     * Returns maturity month-year as provided for corresponding FIX tag (200).
     * It can use several different formats depending on data source:
     * <ul>
     * <li>YYYYMM – if only year and month are specified
     * <li>YYYYMMDD – if full date is specified
     * <li>YYYYMMwN – if week number (within a month) is specified
     * </ul>
     * @return  maturity month-year as provided for corresponding FIX tag (200).
     */
    public String getMMY() {
        return mmy;
    }

    /**
     * Returns type of option.
     * It shall use one of following values:
     * <ul>
     * <li>STAN = Standard Options
     * <li>LEAP = Long-term Equity AnticiPation Securities
     * <li>SDO = Special Dated Options
     * <li>BINY = Binary Options
     * <li>FLEX = FLexible EXchange Options
     * <li>VSO = Variable Start Options
     * <li>RNGE = Range
     * </ul>
     * @return type of option.
     */
    public String getOptionType() {
        return optionType;
    }

    /**
     * Returns expiration cycle style, such as "Weeklys", "Quarterlys".
     * @return expiration cycle style.
     */
    public String getExpirationStyle() {
        return expirationStyle;
    }

    /**
     * Returns settlement price determination style, such as "Open", "Close".
     * @return settlement price determination style.
     */
    public String getSettlementStyle() {
        return settlementStyle;
    }

    /**
     * Returns Classification of Financial Instruments code of this option series.
     * It shall use six-letter CFI code from ISO 10962 standard.
     * It is allowed to use 'X' extensively and to omit trailing letters (assumed to be 'X').
     * See <a href="http://en.wikipedia.org/wiki/ISO_10962">ISO 10962 on Wikipedia</a>.
     * It starts with "OX" as both {@link #getCalls() calls} and {@link #getPuts()} puts} are stored in a series.
     * @return CFI code.
     */
    public String getCFI() {
        return cfi;
    }

    /**
     * Returns a sorted map of all calls from strike to a corresponding option instrument.
     * @return a sorted map of all calls from strike to a corresponding option instrument.
     */
    public SortedMap<Double, T> getCalls() {
        return calls;
    }

    /**
     * Returns a sorted map of all puts from strike to a corresponding option instrument.
     * @return a sorted map of all puts from strike to a corresponding option instrument.
     */
    public SortedMap<Double, T> getPuts() {
        return puts;
    }

    /**
     * Returns a list of all strikes in ascending order.
     * @return list of all strikes in ascending order.
     */
    public List<Double> getStrikes() {
        if (strikes == null) {
            TreeSet<Double> strikesSet = new TreeSet<>(calls.keySet());
            strikesSet.addAll(puts.keySet());
            strikes = new ArrayList<>(strikesSet);
        }
        return strikes;
    }

    /**
     * Returns n strikes the are centered around a specified strike value.
     * @param n the maximal number of strikes to return.
     * @param strike the center strike.
     * @return n strikes the are centered around a specified strike value.
     * @throws IllegalArgumentException when {@code n < 0}.
     */
    public List<Double> getNStrikesAround(int n, double strike) {
        if (n < 0)
            throw new IllegalArgumentException();
        List<Double> strikes = getStrikes();
        int i = Collections.binarySearch(strikes, strike);
        if (i < 0)
            i = -i - 1;
        int from = Math.max(0, i - n / 2);
        int to = Math.min(strikes.size(),  from + n);
        return strikes.subList(from, to);
    }

    /**
     * Compares this option series to another one by its attributes.
     * Expiration takes precedence in comparison.
     * @param o another option series to compare with.
     * @return result of comparison.
     */
    @Override
    public int compareTo(OptionSeries<T> o) {
        if (expiration < o.expiration)
            return -1;
        if (expiration > o.expiration)
            return 1;
        if (lastTrade < o.lastTrade)
            return -1;
        if (lastTrade > o.lastTrade)
            return 1;
        int i = Double.compare(multiplier, o.multiplier);
        if (i != 0)
            return i;
        i = Double.compare(spc, o.spc);
        if (i != 0)
            return i;
        i = additionalUnderlyings.compareTo(o.additionalUnderlyings);
        if (i != 0)
            return i;
        i = mmy.compareTo(o.mmy);
        if (i != 0)
            return i;
        i = optionType.compareTo(o.optionType);
        if (i != 0)
            return i;
        i = expirationStyle.compareTo(o.expirationStyle);
        if (i != 0)
            return i;
        i = settlementStyle.compareTo(o.settlementStyle);
        if (i != 0)
            return i;
        return cfi.compareTo(o.cfi);
    }

    /**
     * Indicates whether some other object is equal to this option series by its attributes.
     * @param  o another object to compare with.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OptionSeries)) return false;
        OptionSeries<?> that = (OptionSeries<?>) o;
        return expiration == that.expiration &&
            lastTrade == that.lastTrade &&
            Double.compare(that.multiplier, multiplier) == 0 &&
            Double.compare(that.spc, spc) == 0 &&
            additionalUnderlyings.equals(that.additionalUnderlyings) &&
            expirationStyle.equals(that.expirationStyle) &&
            mmy.equals(that.mmy) &&
            optionType.equals(that.optionType) &&
            cfi.equals(that.cfi) &&
            settlementStyle.equals(that.settlementStyle);
    }

    /**
     * Returns a hash code value for this option series.
     * @return a hash code value.
     */
    @Override
    public int hashCode() {
        int result;
        long temp;
        result = expiration;
        result = 31 * result + lastTrade;
        temp = multiplier != +0.0d ? Double.doubleToLongBits(multiplier) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = spc != +0.0d ? Double.doubleToLongBits(spc) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + additionalUnderlyings.hashCode();
        result = 31 * result + mmy.hashCode();
        result = 31 * result + optionType.hashCode();
        result = 31 * result + expirationStyle.hashCode();
        result = 31 * result + settlementStyle.hashCode();
        result = 31 * result + cfi.hashCode();
        return result;
    }

    void addOption(boolean isCall, double strike, T option) {
        SortedMap<Double, T> map = isCall ? calls : puts;
        if (map.put(strike, option) == null)
            strikes = null; // clear cached strikes list
    }

    /**
     * Returns a string representation of this series.
     * @return a string representation of this series.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("expiration=").append(DayUtil.getYearMonthDayByDayId(expiration));
        if (lastTrade != 0)
            sb.append(", lastTrade=").append(DayUtil.getYearMonthDayByDayId(lastTrade));
        if (multiplier != 0)
            sb.append(", multiplier=").append(multiplier);
        if (spc != 0)
            sb.append(", spc=").append(spc);
        if (additionalUnderlyings.length() > 0)
            sb.append(", additionalUnderlyings=").append(additionalUnderlyings);
        if (mmy.length() > 0)
            sb.append(", mmy=").append(mmy);
        if (optionType.length() > 0)
            sb.append(", optionType=").append(optionType);
        if (expirationStyle.length() > 0)
            sb.append(", expirationStyle=").append(expirationStyle);
        if (settlementStyle.length() > 0)
            sb.append(", settlementStyle=").append(settlementStyle);
        sb.append(", cfi=").append(cfi);
        return sb.toString();
    }
}
