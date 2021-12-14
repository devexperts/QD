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

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.impl.XmlTimeAdapter;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Theo price is a snapshot of the theoretical option price computation that is
 * periodically performed by <a href="http://www.devexperts.com/en/products/price.html">dxPrice</a>
 * model-free computation.
 * It represents the most recent information that is available about the corresponding
 * values at any given moment of time.
 * The values include first and second order derivative of the price curve by price, so that
 * the real-time theoretical option price can be estimated on real-time changes of the underlying
 * price in the vicinity.
 *
 * <h3>Properties</h3>
 *
 * {@code TheoPrice} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this event;
 * <li>{@link #getTime() time} - timestamp of this event in milliseconds;
 * <li>{@link #getSequence() sequence} - sequence number of this event to distinguish events that have the same {@link #getTime() time};
 * <li>{@link #getPrice() price} - theoretical price;
 * <li>{@link #getUnderlyingPrice() underlyingPrice} - underlying price at the time of theo price computation;
 * <li>{@link #getDelta() delta} - delta of the theoretical price;
 * <li>{@link #getGamma() gamma} -  gamma of the theoretical price;
 * <li>{@link #getDividend() dividend} - implied simple dividend return of the corresponding option series;
 * <li>{@link #getInterest() interest} - implied simple interest return of the corresponding option series.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some TheoPrice sources provide a consistent view of the set of known TheoPrice.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for TheoPrice, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing TheoPrice</h3>
 *
 * Publishing of TheoPrice events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code TheoPrice}.
 */
@XmlRootElement(name = "TheoPrice")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "sequence", "price", "underlyingPrice", "delta", "gamma", "dividend", "interest"
})
public class TheoPrice extends MarketEvent implements TimeSeriesEvent<String>, LastingEvent<String> {
    private static final long serialVersionUID = 0;

    // ========================= public static =========================

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    // ========================= instance =========================

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +---------+----+----+----+----+----+----+----+
     * |         | SM |    | SS | SE | SB | RE | TX |
     * +---------+----+----+----+----+----+----+----+
     */

    private int eventFlags;

    private long index;
    private double price = Double.NaN;
    private double underlyingPrice = Double.NaN;
    private double delta = Double.NaN;
    private double gamma = Double.NaN;
    private double dividend = Double.NaN;
    private double interest = Double.NaN;

    /**
     * Creates new theo price event with default values.
     */
    public TheoPrice() {}

    /**
     * Creates new theo price event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public TheoPrice(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * {@inheritDoc}
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
     * Returns unique per-symbol index of this event.
     * The index is composed of {@link #getTime() time} and {@link #getSequence() sequence}.
     * Changing either time or sequence changes event index.
     * @return unique index of this event.
     */
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this event.
     * The index is composed of {@link #getTime() time} and {@link #getSequence() sequence} and
     * invocation of this method changes time and sequence.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param index the event index.
     * @see #getIndex()
     */
    public void setIndex(long index) {
        this.index = index;
    }

    /**
     * Returns timestamp of the event in milliseconds.
     * @return timestamp of the event in milliseconds
     */
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getTime() {
        return (index >> 32) * 1000 + ((index >> 22) & 0x3ff);
    }

    /**
     * Changes timestamp of the event in milliseconds.
     * @param time timestamp of the event in milliseconds.
     * @see #getTime()
     */
    public void setTime(long time) {
        index = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns sequence number of this event to distinguish events that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     */
    public int getSequence() {
        return (int) index & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number} of this event.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        index = (index & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns theoretical option price.
     * @return theoretical option price.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes theoretical option price.
     * @param price theoretical option price.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns underlying price at the time of theo price computation.
     * @return underlying price at the time of theo price computation.
     */
    public double getUnderlyingPrice() {
        return underlyingPrice;
    }

    /**
     * Changes underlying price at the time of theo price computation.
     * @param underlyingPrice underlying price at the time of theo price computation.
     */
    public void setUnderlyingPrice(double underlyingPrice) {
        this.underlyingPrice = underlyingPrice;
    }

    /**
     * Returns delta of the theoretical price.
     * Delta is the first derivative of the theoretical price by the underlying price.
     * @return delta of the theoretical price.
     */
    public double getDelta() {
        return delta;
    }

    /**
     * Changes delta of the theoretical price.
     * @param delta delta of the theoretical price.
     */
    public void setDelta(double delta) {
        this.delta = delta;
    }

    /**
     * Returns gamma of the theoretical price.
     * Gamma is the second derivative of the theoretical price by the underlying price.
     * @return gamma of the theoretical price.
     */
    public double getGamma() {
        return gamma;
    }

    /**
     * Changes gamma of the theoretical price.
     * @param gamma gamma of the theoretical price.
     */
    public void setGamma(double gamma) {
        this.gamma = gamma;
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

    @Override
    public String toString() {
        return "TheoPrice{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", price=" + price +
            ", underlyingPrice=" + underlyingPrice +
            ", delta=" + delta +
            ", gamma=" + gamma +
            ", dividend=" + dividend +
            ", interest=" + interest +
            '}';
    }
}
