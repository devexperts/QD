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
 * Underlying event is a snapshot of computed values that are available for an option underlying
 * symbol based on the option prices on the market.
 * It represents the most recent information that is available about the corresponding values on
 * the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Underlying} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this event;
 * <li>{@link #getTime() time} - timestamp of this event in milliseconds;
 * <li>{@link #getSequence() sequence} - sequence number of this event to distinguish events that have the same {@link #getTime() time};
 * <li>{@link #getVolatility() volatility} - 30-day implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getFrontVolatility() frontVolatility} - front month implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getBackVolatility() backVolatility} - 3back month implied volatility for this underlying based on VIX methodology;
 * <li>{@link #getCallVolume() callVolume} - call options traded volume for a day;
 * <li>{@link #getPutVolume() putVolume} - put options traded volume for a day;
 * <li>{@link #getOptionVolume() optionVolume} - options traded volume  for a day;
 * <li>{@link #getPutCallRatio() putCallRatio} - ratio of put options traded volume to call options traded volume for a day.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some Underlying sources provide a consistent view of the set of known Underlying events.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for Underlying, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing Underlying</h3>
 *
 * Publishing of Underlying events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Underlying}.
 */
@XmlRootElement(name = "Underlying")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "sequence", "volatility", "frontVolatility", "backVolatility",
    "callVolume", "putVolume", "putCallRatio"
})
public class Underlying extends MarketEvent implements TimeSeriesEvent<String>, LastingEvent<String> {
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
    private double volatility = Double.NaN;
    private double frontVolatility = Double.NaN;
    private double backVolatility = Double.NaN;
    private double callVolume = Double.NaN;
    private double putVolume = Double.NaN;
    private double putCallRatio = Double.NaN;

    /**
     * Creates new underlying event with default values.
     */
    public Underlying() {}

    /**
     * Creates new underlying event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Underlying(String eventSymbol) {
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
     * Returns 30-day implied volatility for this underlying based on VIX methodology.
     * @return 30-day implied volatility for this underlying based on VIX methodology.
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * Changes 30-day implied volatility for this underlying based on VIX methodology.
     * @param volatility 30-day implied volatility for this underlying based on VIX methodology.
     */
    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    /**
     * Returns front month implied volatility for this underlying based on VIX methodology.
     * @return front month implied volatility for this underlying based on VIX methodology.
     */
    public double getFrontVolatility() {
        return frontVolatility;
    }

    /**
     * Changes front month implied volatility for this underlying based on VIX methodology.
     * @param frontVolatility front month implied volatility for this underlying based on VIX methodology.
     */
    public void setFrontVolatility(double frontVolatility) {
        this.frontVolatility = frontVolatility;
    }

    /**
     * Returns back month implied volatility for this underlying based on VIX methodology.
     * @return back month implied volatility for this underlying based on VIX methodology.
     */
    public double getBackVolatility() {
        return backVolatility;
    }

    /**
     * Changes back month implied volatility for this underlying based on VIX methodology.
     * @param backVolatility back month implied volatility for this underlying based on VIX methodology.
     */
    public void setBackVolatility(double backVolatility) {
        this.backVolatility = backVolatility;
    }

    /**
     * Returns call options traded volume for a day.
     * @return call options traded volume for a day.
     */
    public double getCallVolume() {
        return callVolume;
    }

    /**
     * Changes call options traded volume for a day.
     * @param callVolume call options traded volume for a day.
     */
    public void setCallVolume(double callVolume) {
        this.callVolume = callVolume;
    }

    /**
     * Returns put options traded volume for a day.
     * @return put options traded volume for a day.
     */
    public double getPutVolume() {
        return putVolume;
    }

    /**
     * Changes put options traded volume for a day.
     * @param putVolume put options traded volume for a day.
     */
    public void setPutVolume(double putVolume) {
        this.putVolume = putVolume;
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
     * Changes ratio of put options traded volume to call options traded volume for a day.
     * @param putCallRatio ratio of put options traded volume to call options traded volume for a day.
     */
    public void setPutCallRatio(double putCallRatio) {
        this.putCallRatio = putCallRatio;
    }

    /**
     * Returns string representation of this underlying event.
     * @return string representation of this underlying event.
     */
    @Override
    public String toString() {
        return "Underlying{" + baseFieldsToString() + '}';
    }

    // ==================== protected access for inherited classes ====================

    protected String baseFieldsToString() {
        return getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", volatility=" + volatility +
            ", frontVolatility=" + frontVolatility +
            ", backVolatility=" + backVolatility +
            ", callVolume=" + callVolume +
            ", putVolume=" + putVolume +
            ", putCallRatio=" + putCallRatio;
    }
}
