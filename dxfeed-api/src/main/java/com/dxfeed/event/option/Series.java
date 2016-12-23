/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.event.option;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.ipf.option.OptionSeries;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

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
 * <li>{@link #getExpiration() expiration} - day id of expiration;
 * <li>{@link #getVolatility() volatility} - implied volatility index for this series based on VIX methodology;
 * <li>{@link #getPutCallRatio() putCallRatio} - ratio of put traded volume to call traded volume for a day;
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
public class Series extends MarketEvent implements IndexedEvent<String> {
    private static final long serialVersionUID = 1;

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +---------+----+----+----+----+----+----+----+
     * |         | SM |    | SS | SE | SB | RE | TX |
     * +---------+----+----+----+----+----+----+----+
     */

    private int eventFlags;

    private long index;
    private double volatility = Double.NaN;
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
     * Most significant 32 bits of index contain {@link #getExpiration() Expiration} value,
     * so changing {@link #setExpiration(int) Expiration} also changes index.
     * @return unique per-symbol index of this series.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this series.
     * Most significant 32 bits of index contain {@link #getExpiration() Expiration} value,
     * so changing index also changes {@link #getExpiration() Expiration}.
     * @param index unique per-symbol index of this series.
     */
    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    /**
     * Returns day id of expiration.
     * Example: {@link DayUtil#getDayIdByYearMonthDay DayUtil.getDayIdByYearMonthDay}(20090117).
     * Most significant 32 bits of {@link #getIndex() Index} contain day id of expiration,
     * so changing {@link #setIndex(long) Index} also changes day id of expiration.
     * @return day id of expiration.
     */
    public int getExpiration() {
        return (int) (index >> 32);
    }

    /**
     * Changes day id of expiration.
     * Most significant 32 bits of {@link #getIndex() Index} contain day id of expiration,
     * so changing day id of expiration also changes {@link #getIndex() Index}.
     * @param expiration day id of expiration.
     */
    public void setExpiration(int expiration) {
        this.index = ((long) expiration << 32) | (index & 0xFFFFFFFFL);
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
     * Returns ratio of put traded volume to call traded volume for a day.
     * @return ratio of put traded volume to call traded volume for a day.
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
     * Changes ratio of put traded volume to call traded volume for a day.
     * @param putCallRatio ratio of put traded volume to call traded volume for a day.
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
            ", expiration=" + DayUtil.getYearMonthDayByDayId(getExpiration()) +
            ", volatility=" + volatility +
            ", putCallRatio=" + putCallRatio +
            ", forwardPrice=" + forwardPrice +
            ", dividend=" + dividend +
            ", interest=" + interest +
            '}';
    }
}
