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

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.impl.XmlCandleSymbolAdapter;
import com.dxfeed.impl.XmlTimeAdapter;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Candle event with open, high, low, close prices and other information for a specific period.
 * Candles are build with a specified {@link CandlePeriod} using a specified {@link CandlePrice} type
 * with a data taken from the specified {@link CandleExchange} from the specified {@link CandleSession}
 * with further details of aggregation provided by {@link CandleAlignment}.
 *
 * <p> Event symbol of the candle is represented with {@link CandleSymbol} class.
 * Since the {@code Candle} is a time-series event, it is typically subscribed to using
 * {@link DXFeedTimeSeriesSubscription} class that handles the necessarily wrapping
 * of the symbol into {@link TimeSeriesSubscriptionSymbol} to specify a subscription
 * time range.
 *
 * <h3>Properties</h3>
 *
 * {@code Candle} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - candle event symbol;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this candle;
 * <li>{@link #getTime() time} - timestamp of this candle in milliseconds;
 * <li>{@link #getSequence() sequence} - sequence number of this candle; distinguishes candles with same {@link #getTime() time};
 * <li>{@link #getCount() count} - total number of original trade (or quote) events in this candle;
 * <li>{@link #getOpen() open} - the first (open) price of this candle;
 * <li>{@link #getHigh() high} - the maximal (high) price of this candle;
 * <li>{@link #getLow() low} - the minimal (low) price of this candle;
 * <li>{@link #getClose() close} - the last (close) price of this candle;
 * <li>{@link #getVolume() volume} - total volume in this candle;
 * <li>{@link #getVolumeAsDouble() volumeAsDouble} - total volume in this candle as floating number with fractions;
 * <li>{@link #getVWAP() vwap} - volume-weighted average price (VWAP) in this candle;
 * <li>{@link #getBidVolume() bidVolume} - bid volume in this candle;
 * <li>{@link #getBidVolumeAsDouble() bidVolumeAsDouble} - bid volume in this candle as floating number with fractions;
 * <li>{@link #getAskVolume() askVolume} - bid volume in this candle;
 * <li>{@link #getAskVolumeAsDouble() askVolumeAsDouble} - bid volume in this candle as floating number with fractions;
 * <li>{@link #getImpVolatility() impVolatility} - implied volatility;
 * <li>{@link #getOpenInterest() openInterest} - open interest;
 * <li>{@link #getOpenInterestAsDouble()} () openInterestAsDouble} - open interest as floating number with fractions;
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some candle sources provide a consistent view of the set of known candles.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for candles, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing Candles</h3>
 *
 * Publishing of candle events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code TradeHistory} for tick candles
 * with {@link CandlePeriod#TICK CandlePeriod.TICK}, records {@code Trade.<period>} for
 * a certain set of popular periods, and QDS record {@code Candle} for arbitrary custom
 * periods, with a set of {@code Candle{<attributes>}} records for a popular combinations of custom
 * candle symbol attributes like {@link CandlePrice} for an efficient support of bid-ask charting.
 */
@XmlRootElement(name = "Candle")
@XmlType(propOrder = {
    "eventSymbol", "eventTime", "eventFlags", "index", "time", "sequence",
    "count", "open", "high", "low", "close", "volumeAsDouble", "VWAP",
    "bidVolumeAsDouble", "askVolumeAsDouble", "impVolatility", "openInterestAsDouble"
})
public class Candle implements TimeSeriesEvent<CandleSymbol>, LastingEvent<CandleSymbol> {
    private static final long serialVersionUID = 3;

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

    private CandleSymbol eventSymbol;
    private int eventFlags;
    private long eventTime;

    private long index;
    private long count;
    private double open = Double.NaN;
    private double high = Double.NaN;
    private double low = Double.NaN;
    private double close = Double.NaN;
    private double volume = Double.NaN;
    private double vwap = Double.NaN;
    private double bidVolume = Double.NaN;
    private double askVolume = Double.NaN;
    private double impVolatility = Double.NaN;
    private double openInterest = Double.NaN;

    /**
     * Creates new candle with default values.
     */
    public Candle() {}

    /**
     * Creates new candle with the specified candle event symbol.
     * @param eventSymbol candle event symbol.
     */
    public Candle(CandleSymbol eventSymbol) {
        this.eventSymbol = eventSymbol;
    }

    /**
     * Returns candle event symbol.
     * @return candle event symbol.
     */
    @Override
    @XmlJavaTypeAdapter(type=CandleSymbol.class, value=XmlCandleSymbolAdapter.class)
    @XmlSchemaType(name="string")
    public CandleSymbol getEventSymbol() {
        return eventSymbol;
    }

    /**
     * Changes candle event symbol.
     * @param eventSymbol candle event symbol.
     */
    @Override
    public void setEventSymbol(CandleSymbol eventSymbol) {
        this.eventSymbol = eventSymbol;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEventTime() {
        return eventTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
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
     * Returns unique per-symbol index of this candle event.
     * Candle index is composed of {@link #getTime() time} and {@link #getSequence() sequence}.
     * Changing either time or sequence changes event index.
     * @return unique index of this candle event.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this candle event.
     * Candle index is composed of {@link #getTime() time} and {@link #getSequence() sequence} and
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
     * Changes unique per-symbol index of this candle event.
     * @param index the event index.
     * @deprecated Use {@link #setIndex(long)}
     */
    public void setEventId(long index) {
        setIndex(index);
    }

    /**
     * Returns timestamp of the candle in milliseconds.
     * @return timestamp of the candle in milliseconds
     */
    @Override
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getTime() {
        return (index >> 32) * 1000 + ((index >> 22) & 0x3ff);
    }

    /**
     * Changes timestamp of the candle in milliseconds.
     * @param time timestamp of the candle in milliseconds.
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
     * Returns total number of original trade (or quote) events in this candle.
     * @return total number of original trade (or quote) events in this candle.
     */
    public long getCount() {
        return count;
    }

    /**
     * Changes total number of original trade (or quote) events in this candle.
     * @param count total number of original trade (or quote) events in this candle.
     */
    public void setCount(long count) {
        this.count = count;
    }

    /**
     * Returns the first (open) price of this candle.
     * @return the first (open) price of this candle.
     */
    public double getOpen() {
        return open;
    }

    /**
     * Changes the first (open) price of this candle.
     * @param open the first (open) price of this candle.
     */
    public void setOpen(double open) {
        this.open = open;
    }

    /**
     * Returns the maximal (high) price of this candle.
     * @return the maximal (high) price of this candle.
     */
    public double getHigh() {
        return high;
    }

    /**
     * Changes the maximal (high) price of this candle.
     * @param high the maximal (high) price of this candle.
     */
    public void setHigh(double high) {
        this.high = high;
    }

    /**
     * Returns the minimal (low) price of this candle.
     * @return the minimal (low) price of this candle.
     */
    public double getLow() {
        return low;
    }

    /**
     * Changes the minimal (low) price of this candle.
     * @param low the minimal (low) price of this candle.
     */
    public void setLow(double low) {
        this.low = low;
    }

    /**
     * Returns the last (close) price of this candle.
     * @return the last (close) price of this candle.
     */
    public double getClose() {
        return close;
    }

    /**
     * Changes the last (close) price of this candle.
     * @param close the last (close) price of this candle.
     */
    public void setClose(double close) {
        this.close = close;
    }

    /**
     * Returns total volume in this candle.
     * @return total volume in this candle.
     */
    @XmlTransient
    public long getVolume() {
        return (long) volume;
    }

    /**
     * Changes total volume in this candle.
     * @param volume total volume in this candle.
     */
    public void setVolume(long volume) {
        this.volume = volume;
    }

    /**
     * Returns total volume in this candle as floating number with fractions.
     * @return total volume in this candle as floating number with fractions.
     */
    @XmlElement(name = "volume")
    public double getVolumeAsDouble() {
        return volume;
    }

    /**
     * Changes total volume in this candle as floating number with fractions.
     * @param volume total volume in this candle as floating number with fractions.
     */
    public void setVolumeAsDouble(double volume) {
        this.volume = volume;
    }

    /**
     * Returns volume-weighted average price (VWAP) in this candle.
     * Total turnover in this candle can be computed with <code>getVWAP() * {@link #getVolume() getVolume}()</code>.
     * @return volume-weighted average price (VWAP) in this candle.
     */
    @XmlElement(name = "vwap") // all lower-case
    public double getVWAP() {
        return vwap;
    }

    /**
     * Changes volume-weighted average price (VWAP) in this candle.
     * @param vwap volume-weighted average price (VWAP) in this candle.
     */
    public void setVWAP(double vwap) {
        this.vwap = vwap;
    }

    /**
     * Returns bid volume in this candle.
     * @return bid volume in this candle.
     */
    @XmlTransient
    public long getBidVolume() {
        return (long) bidVolume;
    }

    /**
     * Changes bid volume in this candle.
     * @param bidVolume bid volume in this candle.
     */
    public void setBidVolume(long bidVolume) {
        this.bidVolume = bidVolume;
    }

    /**
     * Returns bid volume in this candle as floating number with fractions.
     * @return bid volume in this candle as floating number with fractions.
     */
    @XmlElement(name = "bidVolume")
    public double getBidVolumeAsDouble() {
        return bidVolume;
    }

    /**
     * Changes bid volume in this candle as floating number with fractions.
     * @param bidVolume bid volume in this candle as floating number with fractions.
     */
    public void setBidVolumeAsDouble(double bidVolume) {
        this.bidVolume = bidVolume;
    }

    /**
     * Returns ask volume in this candle.
     * @return ask volume in this candle.
     */
    @XmlTransient
    public long getAskVolume() {
        return (long) askVolume;
    }

    /**
     * Changes ask volume in this candle.
     * @param askVolume ask volume in this candle.
     */
    public void setAskVolume(long askVolume) {
        this.askVolume = askVolume;
    }

    /**
     * Returns ask volume in this candle as floating number with fractions.
     * @return ask volume in this candle as floating number with fractions.
     */
    @XmlElement(name = "askVolume")
    public double getAskVolumeAsDouble() {
        return askVolume;
    }

    /**
     * Changes ask volume in this candle as floating number with fractions.
     * @param askVolume ask volume in this candle as floating number with fractions.
     */
    public void setAskVolumeAsDouble(double askVolume) {
        this.askVolume = askVolume;
    }

    /**
     * Returns implied volatility.
     * @return implied volatility.
     */
    public double getImpVolatility() {
        return impVolatility;
    }

    /**
     * Changes implied volatility.
     * @param impVolatility implied volatility.
     */
    public void setImpVolatility(double impVolatility) {
        this.impVolatility = impVolatility;
    }

    /**
     * Returns open interest.
     * @return open interest.
     */
    @XmlTransient
    public long getOpenInterest() {
        return (long) openInterest;
    }

    /**
     * Changes open interest.
     * @param openInterest open interest.
     */
    public void setOpenInterest(long openInterest) {
        this.openInterest = openInterest;
    }

    /**
     * Returns open interest as floating number with fractions.
     * @return open interest in this candle as floating number with fractions.
     */
    @XmlElement(name = "openInterest")
    public double getOpenInterestAsDouble() {
        return openInterest;
    }

    /**
     * Changes open interest as floating number with fractions.
     * @param openInterest open interest as floating number with fractions.
     */
    public void setOpenInterestAsDouble(double openInterest) {
        this.openInterest = openInterest;
    }

    /**
     * Returns string representation of this candle.
     * @return string representation of this candle.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + baseFieldsToString() + "}";
    }

    String baseFieldsToString() {
        return eventSymbol +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", count=" + count +
            ", open=" + open +
            ", high=" + high +
            ", low=" + low +
            ", close=" + close +
            ", volume=" + volume +
            ", vwap=" + vwap +
            ", bidVolume=" + bidVolume +
            ", askVolume=" + askVolume +
            ", impVolatility=" + impVolatility +
            ", openInterest=" + openInterest +
            "";
    }
}
