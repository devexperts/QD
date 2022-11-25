/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.impl.TimeNanosUtil;
import com.dxfeed.impl.XmlTimeAdapter;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.IndexedEventModel;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import static com.dxfeed.event.market.TimeAndSale.ETH;
import static com.dxfeed.event.market.TimeAndSale.SIDE_MASK;
import static com.dxfeed.event.market.TimeAndSale.SIDE_SHIFT;
import static com.dxfeed.event.market.TimeAndSale.SPREAD_LEG;
import static com.dxfeed.event.market.TimeAndSale.TTE_MASK;
import static com.dxfeed.event.market.TimeAndSale.TTE_SHIFT;
import static com.dxfeed.event.market.TimeAndSale.TYPE_MASK;
import static com.dxfeed.event.market.TimeAndSale.TYPE_SHIFT;
import static com.dxfeed.event.market.TimeAndSale.VALID_TICK;

/**
 * Option Sale event represents a trade or another market event with the price
 * (for example, market open/close price, etc.) for each option symbol listed under the specified Underlying.
 * Option Sales are intended to provide information about option trades <b>in a continuous time slice</b> with
 * the additional metrics, like Option Volatility, Option Delta, and Underlying Price.
 *
 * <p>Option Sale events have unique {@link #getIndex() index} which can be used for later
 * correction/cancellation processing.
 *
 * <h3>Properties</h3>
 *
 * {@code OptionSale} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this option sale event;
 * <li>{@link #getTime() time} - time of the original event;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of event;
 * <li>{@link #getSequence() sequence}
 *      - sequence number of this event to distinguish events that have the same {@link #getTime() time}
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of this option sale event;
 * <li>{@link #getPrice() price} - price of this option sale event;
 * <li>{@link #getSize() size} - size of this option sale event;
 * <li>{@link #getBidPrice() bidPrice} - the current bid price on the market when this option sale event had occurred;
 * <li>{@link #getAskPrice() askPrice} - the current ask price on the market when this option sale event had occurred;
 * <li>{@link #getExchangeSaleConditions() exchangeSaleConditions} - sale conditions provided for this event by data feed;
 * <li>{@link #getAggressorSide() aggressorSide} - aggressor side of this option sale event;
 * <li>{@link #isSpreadLeg() spreadLeg} - whether this event represents a spread leg;
 * <li>{@link #isExtendedTradingHours() extendedTradingHours} - whether this event represents an extended trading hours sale;
 * <li>{@link #isValidTick() validTick} - whether this event represents a valid intraday tick;
 * <li>{@link #getType() type} - type of this option sale event;
 * <li>{@link #getUnderlyingPrice() underlyingPrice}
 *      - underlying price at the time when this option sale event had occurred;
 * <li>{@link #getVolatility() volatility} - Black-Scholes implied volatility of the event's option;
 * <li>{@link #getDelta() delta} - the event's option delta;
 * <li>{@link #getOptionSymbol()}  optionSymbol} - option symbol of this event's option.
 * </ul>
 *
 * <p>See <a href="package-summary.html#model">the model section</a> for a mathematical background on
 * the values in this event.
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Option sale data source provides a consistent view of the set of known option sales.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for option sale events, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>{@link IndexedEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list of current events.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code OptionSale}.
 */
@XmlRootElement(name = "OptionSale")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "timeNanoPart", "sequence",
    "exchangeCode", "price", "size", "bidPrice", "askPrice", "exchangeSaleConditions",
    "tradeThroughExempt", "aggressorSide", "spreadLeg", "extendedTradingHours", "validTick", "type",
    "underlyingPrice", "volatility", "delta", "optionSymbol"
})
public class OptionSale extends MarketEvent implements IndexedEvent<String> {
    private static final long serialVersionUID = 0;

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    /**
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     * <br>
     * <pre><tt>
     *    31..7    6    5    4    3    2    1    0
     * +--------+----+----+----+----+----+----+----+
     * |        | SM |    | SS | SE | SB | RE | TX |
     * +--------+----+----+----+----+----+----+----+
     * </tt></pre>
     */
    private int eventFlags;

    private long index;
    private long timeSequence;
    private int timeNanoPart;
    private char exchangeCode;
    private double price = Double.NaN;
    private double size = Double.NaN;
    private double bidPrice = Double.NaN;
    private double askPrice = Double.NaN;
    private String exchangeSaleConditions;
    private int flags; // flags as in TimeAndSale with the same semantics. Don't mix with other features.
    private double underlyingPrice = Double.NaN;
    private double volatility = Double.NaN;
    private double delta = Double.NaN;
    private String optionSymbol;

    /**
     * Creates new option sale event with default values.
     */
    public OptionSale() {
    }

    /**
     * Creates new option sale event with the specified event symbol.
     * @param eventSymbol event symbol
     */
    public OptionSale(String eventSymbol) {
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
     * Returns unique per-symbol index of this option sale event.
     * @return unique per-symbol index of this option sale event.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this option sale event.
     * @param index unique per-symbol index of this option sale event.
     */
    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    /**
     * Returns time and sequence of this event packaged into single long value.
     * This method is intended for efficient option sale time priority comparison.
     * @return time and sequence of this option sale event.
     */
    @XmlTransient
    public long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of this event.
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
     * Returns time of this option sale event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of this option sale event.
     */
    @XmlJavaTypeAdapter(type=long.class, value= XmlTimeAdapter.class)
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of this option sale event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time time of this option sale event.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns timestamp of the original event in nanoseconds.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @return timestamp of the original event in nanoseconds.
     */
    @XmlTransient
    public long getTimeNanos() {
        return TimeNanosUtil.getNanosFromMillisAndNanoPart(getTime(), timeNanoPart);
    }

    /**
     * Changes timestamp of event.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @param timeNanos timestamp in nanoseconds.
     */
    public void setTimeNanos(long timeNanos) {
        setTime(TimeNanosUtil.getMillisFromNanos(timeNanos));
        timeNanoPart = TimeNanosUtil.getNanoPartFromNanos(timeNanos);
    }

    /**
     * Returns microseconds and nanoseconds time part of this event.
     * @return microseconds and nanoseconds time part of this event.
     */
    public int getTimeNanoPart() {
        return timeNanoPart;
    }

    /**
     * Changes microseconds and nanoseconds time part of this event.
     * @param timeNanoPart microseconds and nanoseconds time part of this event.
     */
    public void setTimeNanoPart(int timeNanoPart) {
        this.timeNanoPart = timeNanoPart;
    }

    /**
     * Returns sequence number of this event to distinguish option sale events that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of this option sale event.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number} of this option sale event.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if the sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns exchange code of this option sale event.
     * @return exchange code of this option sale event.
     */
    public char getExchangeCode() {
        return exchangeCode;
    }

    /**
     * Changes exchange code of this option sale event.
     * @param exchangeCode exchange code of this option sale event.
     */
    public void setExchangeCode(char exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    /**
     * Returns price of this option sale event.
     * @return price of this option sale event.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes price of this option sale event.
     * @param price price of this option sale event.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns size of this option sale event.
     * @return size of this option sale event.
     */
    public double getSize() {
        return size;
    }

    /**
     * Changes size of this option sale event.
     * @param size size of this option sale event.
     */
    public void setSize(double size) {
        this.size = size;
    }

    /**
     * Returns the current bid price on the market when this option sale event had occurred.
     * @return the current bid price on the market when this option sale event had occurred.
     */
    public double getBidPrice() {
        return bidPrice;
    }

    /**
     * Changes the current bid price on the market when this option sale event had occurred.
     * @param bidPrice the current bid price on the market when this option sale event had occurred.
     */
    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }

    /**
     * Returns the current ask price on the market when this option sale event had occurred.
     * @return the current ask price on the market when this option sale event had occurred.
     */
    public double getAskPrice() {
        return askPrice;
    }

    /**
     * Changes the current ask price on the market when this option sale event had occurred.
     * @param askPrice the current ask price on the market when option and sale event had occurred.
     */
    public void setAskPrice(double askPrice) {
        this.askPrice = askPrice;
    }

    /**
     * Returns sale conditions provided for this event by data feed.
     * This field format is specific for every particular data feed.
     * @return sale conditions.
     */
    public String getExchangeSaleConditions() {
        return exchangeSaleConditions;
    }

    /**
     * Changes sale conditions provided for this event by data feed.
     * @param exchangeSaleConditions sale conditions.
     */
    public void setExchangeSaleConditions(String exchangeSaleConditions) {
        this.exchangeSaleConditions = exchangeSaleConditions;
    }

    /**
     * Returns TradeThroughExempt flag of this option sale event.
     * @return TradeThroughExempt flag of this option sale event.
     */
    public char getTradeThroughExempt() {
        return (char) Util.getBits(flags, TTE_MASK, TTE_SHIFT);
    }

    /**
     * Changes TradeThroughExempt flag of this option sale event.
     * @param tradeThroughExempt TradeThroughExempt flag of this option sale event.
     */
    public void setTradeThroughExempt(char tradeThroughExempt) {
        Util.checkChar(tradeThroughExempt, TTE_MASK, "tradeThroughExempt");
        flags = Util.setBits(flags, TTE_MASK, TTE_SHIFT, tradeThroughExempt);
    }

    /**
     * Returns aggressor side of this option sale event.
     * @return aggressor side of this option sale event.
     */
    public Side getAggressorSide() {
        return Side.valueOf(Util.getBits(flags, SIDE_MASK, SIDE_SHIFT));
    }

    /**
     * Changes aggressor side of this option sale event.
     * @param side aggressor side of this option sale event.
     */
    public void setAggressorSide(Side side) {
        flags = Util.setBits(flags, SIDE_MASK, SIDE_SHIFT, side.getCode());
    }

    /**
     * Returns whether this event represents a spread leg.
     * @return {@code true} if this event represents a spread leg.
     */
    public boolean isSpreadLeg() {
        return (flags & SPREAD_LEG) != 0;
    }

    /**
     * Changes whether this event represents a spread leg.
     * @param spreadLeg {@code true} if this event represents a spread leg.
     */
    public void setSpreadLeg(boolean spreadLeg) {
        flags = spreadLeg ? flags | SPREAD_LEG : flags & ~SPREAD_LEG;
    }

    /**
     * Returns whether this event represents an extended trading hours sale.
     * @return {@code true} if this event represents an extended trading hours sale.
     */
    public boolean isExtendedTradingHours() {
        return (flags & ETH) != 0;
    }

    /**
     * Changes whether this event represents an extended trading hours sale.
     * @param extendedTradingHours {@code true} if this event represents an extended trading hours sale.
     */
    public void setExtendedTradingHours(boolean extendedTradingHours) {
        flags = extendedTradingHours ? flags | ETH : flags & ~ETH;
    }

    /**
     * Returns whether this event represents a valid intraday tick.
     * Note, that a correction for a previously distributed valid tick represents a new valid tick itself,
     * but a cancellation of a previous valid tick does not.
     * @return {@code true} if this event represents a valid intraday tick.
     */
    public boolean isValidTick() {
        return (flags & VALID_TICK) != 0;
    }

    /**
     * Changes whether this event represents a valid intraday tick.
     * @param validTick {@code true} if this event represents a valid intraday tick.
     */
    public void setValidTick(boolean validTick) {
        flags = validTick ? flags | VALID_TICK : flags & ~VALID_TICK;
    }

    /**
     * Returns type of this option sale event.
     * @return type of this option sale event.
     */
    public TimeAndSaleType getType() {
        return TimeAndSaleType.valueOf(Util.getBits(flags, TYPE_MASK, TYPE_SHIFT));
    }

    /**
     * Changes type of this option sale event.
     * @param type type of this option sale event.
     */
    public void setType(TimeAndSaleType type) {
        flags = Util.setBits(flags, TYPE_MASK, TYPE_SHIFT, type.getCode());
    }

    /**
     * Returns whether this is a new event (not cancellation or correction).
     * It is {@code true} for newly created option sale event.
     * @return {@code true} if this is a new event (not cancellation or correction).
     */
    @XmlTransient
    public boolean isNew() {
        return getType() == TimeAndSaleType.NEW;
    }

    /**
     * Returns whether this is a correction of a previous event.
     * It is {@code false} for newly created option sale event.
     * @return {@code true} if this is a correction of a previous event
     */
    @XmlTransient
    public boolean isCorrection() {
        return getType() == TimeAndSaleType.CORRECTION;
    }

    /**
     * Returns whether this is a cancellation of a previous event.
     * It is {@code false} for newly created option sale event.
     * @return {@code true} if this is a cancellation of a previous event
     */
    @XmlTransient
    public boolean isCancel() {
        return getType() == TimeAndSaleType.CANCEL;
    }

    /**
     * Returns underlying price at the time of this option sale event.
     * @return underlying price at the time of this option sale event.
     */
    public double getUnderlyingPrice() {
        return underlyingPrice;
    }

    /**
     * Changes underlying price at the time of this option sale event.
     * @param underlyingPrice underlying price at the time of this option sale event.
     */
    public void setUnderlyingPrice(double underlyingPrice) {
        this.underlyingPrice = underlyingPrice;
    }

    /**
     * Returns Black-Scholes implied volatility of the option at the time of this option sale event.
     * @return Black-Scholes implied volatility of the option at the time of this option sale event.
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * Changes Black-Scholes implied volatility of the option at the time of this option sale event.
     * @param volatility Black-Scholes implied volatility of the option at the time of this option sale event.
     */
    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    /**
     * Return option delta at the time of this option sale event.
     * Delta is the first derivative of an option price by an underlying price.
     * @return option delta at the time of this option sale event.
     */
    public double getDelta() {
        return delta;
    }

    /**
     * Changes option delta at the time of this option sale event.
     * @param delta option delta at the time of this option sale event.
     */
    public void setDelta(double delta) {
        this.delta = delta;
    }

    /**
     * Returns option symbol of this event.
     * @return option symbol of this event.
     */
    public String getOptionSymbol() {
        return optionSymbol;
    }

    /**
     * Changes option symbol of this event.
     * @param optionSymbol option symbol of this event.
     */
    public void setOptionSymbol(String optionSymbol) {
        this.optionSymbol = optionSymbol;
    }

    /**
     * Returns string representation of this option sale event.
     * @return string representation of this option sale event.
     */
    @Override
    public String toString() {
        return "OptionSale{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
            ", index=0x" + Long.toHexString(index) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", timeNanoPart=" + timeNanoPart +
            ", sequence=" + getSequence() +
            ", exchange=" + Util.encodeChar(exchangeCode) +
            ", price=" + price +
            ", size=" + size +
            ", bid=" + bidPrice +
            ", ask=" + askPrice +
            ", ESC='" + exchangeSaleConditions + "'" +
            ", TTE=" + Util.encodeChar(getTradeThroughExempt()) +
            ", side=" + getAggressorSide() +
            ", spread=" + isSpreadLeg() +
            ", ETH=" + isExtendedTradingHours() +
            ", validTick=" + isValidTick() +
            ", type=" + getType() +
            ", underlyingPrice=" + underlyingPrice +
            ", volatility=" + volatility +
            ", delta=" + delta +
            ", optionSymbol='" + optionSymbol + "'" +
            "}";
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @return flags.
     */
    int getFlags() {
        return flags;
    }

    /**
     * Changes implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @param flags flags.
     */
    void setFlags(int flags) {
        this.flags = flags;
    }

}
