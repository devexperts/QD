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
 * Greeks event is a snapshot of the option price, Black-Scholes volatility and greeks.
 * It represents the most recent information that is available about the corresponding values on
 * the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Greeks} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this event;
 * <li>{@link #getTime() time} - timestamp of this event in milliseconds;
 * <li>{@link #getSequence() sequence} - sequence number of this event to distinguish events that have the same {@link #getTime() time};
 * <li>{@link #getPrice() price} - option market price;
 * <li>{@link #getVolatility() volatility} - Black-Scholes implied volatility of the option;
 * <li>{@link #getDelta() delta} - option delta;
 * <li>{@link #getGamma() gamma} -  option gamma;
 * <li>{@link #getTheta() theta} - option theta;
 * <li>{@link #getRho() rho} - option rho;
 * <li>{@link #getVega() vega} -  option vega.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some greeks sources provide a consistent view of the set of known greeks.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for greeks, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing Greeks</h3>
 *
 * Publishing of greeks events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Greeks}.
 */
@XmlRootElement(name = "Greeks")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "sequence", "price", "volatility", "delta", "gamma", "theta", "rho", "vega"
})
public class Greeks extends MarketEvent implements TimeSeriesEvent<String>, LastingEvent<String> {
    private static final long serialVersionUID = 1;

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
    private double volatility = Double.NaN;
    private double delta = Double.NaN;
    private double gamma = Double.NaN;
    private double theta = Double.NaN;
    private double rho = Double.NaN;
    private double vega = Double.NaN;

    /**
     * Creates new greeks event with default values.
     */
    public Greeks() {}

    /**
     * Creates new greeks event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Greeks(String eventSymbol) {
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
    @Override
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
    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    /**
     * Returns timestamp of the event in milliseconds.
     * @return timestamp of the event in milliseconds
     */
    @Override
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
     * Returns option market price.
     * @return option market price.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes option market price.
     * @param price option market price.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns Black-Scholes implied volatility of the option.
     * @return Black-Scholes implied volatility of the option.
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * Changes Black-Scholes implied volatility of the option.
     * @param volatility Black-Scholes implied volatility of the option.
     */
    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    /**
     * Return option delta. Delta is the first derivative of an option price by an underlying price.
     * @return option delta.
     */
    public double getDelta() {
        return delta;
    }

    /**
     * Changes option delta.
     * @param delta option delta.
     */
    public void setDelta(double delta) {
        this.delta = delta;
    }

    /**
     * Returns option gamma. Gamma is the second derivative of an option price by an underlying price.
     * @return option gamma.
     */
    public double getGamma() {
        return gamma;
    }

    /**
     * Changes option gamma.
     * @param gamma option gamma.
     */
    public void setGamma(double gamma) {
        this.gamma = gamma;
    }

    /**
     * Returns option theta. Theta is the first derivative of an option price by a number of days to expiration.
     * @return option theta.
     */
    public double getTheta() {
        return theta;
    }

    /**
     * Changes option theta.
     * @param theta option theta.
     */
    public void setTheta(double theta) {
        this.theta = theta;
    }

    /**
     * Returns option rho. Rho is the first derivative of an option price by percentage interest rate.
     * @return option rho.
     */
    public double getRho() {
        return rho;
    }

    /**
     * Changes option rho.
     * @param rho option rho.
     */
    public void setRho(double rho) {
        this.rho = rho;
    }

    /**
     * Returns option vega. Vega is the first derivative of an option price by percentage volatility.
     * @return option vega.
     */
    public double getVega() {
        return vega;
    }

    /**
     * Changes option vega.
     * @param vega option vega.
     */
    public void setVega(double vega) {
        this.vega = vega;
    }

    /**
     * Returns string representation of this greeks event.
     * @return string representation of this greeks event.
     */
    @Override
    public String toString() {
        return "Greeks{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", price=" + price +
            ", volatility=" + volatility +
            ", delta=" + delta +
            ", gamma=" + gamma +
            ", theta=" + theta +
            ", rho=" + rho +
            ", vega=" + vega +
            '}';
    }
}
