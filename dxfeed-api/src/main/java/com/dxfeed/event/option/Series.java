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
package com.dxfeed.event.option;

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.impl.XmlTimeAdapter;
import com.dxfeed.ipf.option.OptionSeries;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Series event is a snapshot of computed values that are available for all option series for
 * a given underlying symbol based on the option prices on the market.
 * It represents the most recent information that is available about the corresponding values on
 * the market at any given moment of time.
 *
 * <p>Series is an {@link IndexedEvent IndexedEvent} with multiple instances of event available for
 * each underlying symbol. Each series event instance corresponds to an {@link OptionSeries}
 * of the corresponding underlying. The correspondence between a series event instance and
 * an {@link OptionSeries} is established via {@link #getExpiration() expiration} property.
 * If case where there are multiple series at the same expiration day id, then series events are
 * are ordered by their {@link #getIndex() index} in the same order as the corresponding
 * {@link OptionSeries} are {@link OptionSeries#compareTo(OptionSeries) ordered} by their attributes.
 *
 * <h3>Properties</h3>
 *
 * {@code Series} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this series;
 * <li>{@link #getTime() time} - time of this series;
 * <li>{@link #getSequence() sequence} - sequence of this series;
 * <li>{@link #getExpiration() expiration} - day id of expiration;
 * <li>{@link #getVolatility() volatility} - implied volatility index for this series based on VIX methodology;
 * <li>{@link #getCallVolume() callVolume} - call options traded volume for a day;
 * <li>{@link #getPutVolume() putVolume} - put options traded volume for a day;
 * <li>{@link #getOptionVolume() optionVolume} - options traded volume  for a day;
 * <li>{@link #getPutCallRatio() putCallRatio} - ratio of put options traded volume to call options traded volume for a day;
 * <li>{@link #getForwardPrice() forwardPrice} - implied forward price for this option series;
 * <li>{@link #getDividend() dividend} - implied simple dividend return of the corresponding option series;
 * <li>{@link #getInterest() interest} - implied simple interest return of the corresponding option series.
 * </ul>
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Series data source provides a consistent view of the set of known series.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for series, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link IndexedEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of events.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code Series}.
 */
@XmlRootElement(name = "Series")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "sequence",
    "expiration", "volatility", "callVolume", "putVolume", "putCallRatio", "forwardPrice", "dividend", "interest"
})
public class Series extends MarketEvent implements IndexedEvent<String> {
    private static final long serialVersionUID = 1;

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +---------+----+----+----+----+----+----+----+
     * |         | SM |    | SS | SE | SB | RE | TX |
     * +---------+----+----+----+----+----+----+----+
     */

    private int eventFlags;

    private long index;
    private long timeSequence;
    private int expiration;
    private double volatility = Double.NaN;
    private double callVolume = Double.NaN;
    private double putVolume = Double.NaN;
    private double putCallRatio = Double.NaN;
    private double forwardPrice = Double.NaN;
    private double dividend = Double.NaN;
    private double interest = Double.NaN;

    /**
     * Creates new series event with default values.
     */
    public Series() {}

    /**
     * Creates new series event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Series(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns a source for this event.
     * This method always returns {@link IndexedEventSource#DEFAULT DEFAULT}.
     * @return a source for this event.
     */
    @Override
    @XmlTransient
    public IndexedEventSource getSource() {
        return IndexedEventSource.DEFAULT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventFlags() {
        return eventFlags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventFlags(int eventFlags) {
        this.eventFlags = eventFlags;
    }

    /**
     * Returns unique per-symbol index of this series.
     * @return unique per-symbol index of this series.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this series.
     * @param index unique per-symbol index of this series.
     */
    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    /**
     * Returns time and sequence of this series packaged into single long value.
     * This method is intended for efficient series time priority comparison.
     * @return time and sequence of this series.
     */
    @XmlTransient
    public long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of this series.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param timeSequence the time and sequence.
     * @see #getTimeSequence()
     */
    public void setTimeSequence(long timeSequence) {
        this.timeSequence = timeSequence;
    }

    /**
     * Returns time of this series.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of this series.
     */
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of this series.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time time of this series.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns sequence number of this series to distinguish series that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of this series.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number} of this series.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
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
     * Changes day id of expiration.
     * @param expiration day id of expiration.
     */
    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    /**
     * Returns implied volatility index for this series based on VIX methodology.
     * @return implied volatility index for this series based on VIX methodology.
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * Changes implied volatility index for this series based on VIX methodology.
     * @param volatility implied volatility index for this series based on VIX methodology.
     */
    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    /**
     * Returns call options traded volume for a day.
     * @return call options traded volume for a day.
     */
    public double getCallVolume() {
        return callVolume;
    }

    /**
     * Returns put options traded volume for a day.
     * @return put options traded volume for a day.
     */
    public double getPutVolume() {
        return putVolume;
    }

    /**
     * Returns options traded volume for a day.
     * @return options traded volume for a day.
     */
    public double getOptionVolume() {
        return Double.isNaN(putVolume) ? callVolume : Double.isNaN(callVolume) ? putVolume : putVolume + callVolume;
    }

    /**
     * Returns ratio of put options traded volume to call options traded volume for a day.
     * @return ratio of put options traded volume to call options traded volume for a day.
     */
    public double getPutCallRatio() {
        return putCallRatio;
    }

    /**
     * Returns implied forward price for this option series.
     * @return implied forward price for this option series.
     */
    public double getForwardPrice() {
        return forwardPrice;
    }

    /**
     * Changes implied forward price for this option series.
     * @param forwardPrice implied forward price for this option series.
     */
    public void setForwardPrice(double forwardPrice) {
        this.forwardPrice = forwardPrice;
    }

    /**
     * Returns implied simple dividend return of the corresponding option series.
     * See <a href="package-summary.html#model">the model section</a> for an explanation this simple dividend return \( Q(\tau) \).
     * @return implied simple dividend return of the corresponding option series.
     */
    public double getDividend() {
        return dividend;
    }

    /**
     * Changes implied simple dividend return of the corresponding option series.
     * See <a href="package-summary.html#model">the model section</a> for an explanation this simple dividend return \( Q(\tau) \).
     * @param dividend implied simple dividend return of the corresponding option series.
     */
    public void setDividend(double dividend) {
        this.dividend = dividend;
    }

    /**
     * Returns implied simple interest return of the corresponding option series.
     * See <a href="package-summary.html#model">the model section</a> for an explanation this simple interest return \( R(\tau) \).
     * @return implied simple interest return of the corresponding option series.
     */
    public double getInterest() {
        return interest;
    }

    /**
     * Changes implied simple interest return of the corresponding option series.
     * See <a href="package-summary.html#model">the model section</a> for an explanation this simple interest return \( R(\tau) \).
     * @param interest implied simple interest return of the corresponding option series.
     */
    public void setInterest(double interest) {
        this.interest = interest;
    }

    /**
     * Changes call options traded volume for a day.
     * @param callVolume call options traded volume for a day.
     */
    public void setCallVolume(double callVolume) {
        this.callVolume = callVolume;
    }

    /**
     * Changes put options traded volume for a day.
     * @param putVolume put options traded volume for a day.
     */
    public void setPutVolume(double putVolume) {
        this.putVolume = putVolume;
    }

    /**
     * Changes ratio of put options traded volume to call options traded volume for a day.
     * @param putCallRatio ratio of put options traded volume to call options traded volume for a day.
     */
    public void setPutCallRatio(double putCallRatio) {
        this.putCallRatio = putCallRatio;
    }

    /**
     * Returns string representation of this series event.
     * @return string representation of this series event.
     */
    @Override
    public String toString() {
        return "Series{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", index=0x" + Long.toHexString(index) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", expiration=" + DayUtil.getYearMonthDayByDayId(getExpiration()) +
            ", volatility=" + volatility +
            ", callVolume=" + callVolume +
            ", putVolume=" + putVolume +
            ", putCallRatio=" + putCallRatio +
            ", forwardPrice=" + forwardPrice +
            ", dividend=" + dividend +
            ", interest=" + interest +
            '}';
    }
}
