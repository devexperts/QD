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
package com.dxfeed.event.market;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.impl.TimeNanosUtil;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * Time and Sale represents a trade or other market event with price, like market open/close price, etc.
 * Time and Sales are intended to provide information about trades <b>in a continuous time slice</b>
 * (unlike {@link Trade} events which are supposed to provide snapshot about the <b>current last</b> trade).
 *
 * <p> Time and Sale events have unique {@link #getIndex() index} which can be used for later
 * correction/cancellation processing.
 *
 * <h3>Properties</h3>
 *
 * {@code TimeAndSale} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getEventFlags() eventFlags} - transactional event flags;
 * <li>{@link #getIndex() index} - unique per-symbol index of this time and sale event;
 * <li>{@link #getTime() time} - timestamp of the original event;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of event;
 * <li>{@link #getSequence() sequence} - sequence number of this event to distinguish events that have the same
 *                                       {@link #getTime() time};
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of this time and sale event;
 * <li>{@link #getPrice() price} - price of this time and sale event;
 * <li>{@link #getSize() size} - size of this time and sale event as integer number (rounded toward zero);
 * <li>{@link #getSizeAsDouble() sizeAsDouble} - size of this time and sale event as floating number with fractions;
 * <li>{@link #getBidPrice() bidPrice} - the current bid price on the market when this time and sale event had occurred;
 * <li>{@link #getAskPrice() askPrice} - the current ask price on the market when this time and sale event had occurred;
 * <li>{@link #getExchangeSaleConditions() exchangeSaleConditions} - sale conditions provided for this event by data feed;
 * <li>{@link #getAggressorSide() aggressorSide} - aggressor side of this time and sale event;
 * <li>{@link #isSpreadLeg() spreadLeg} - whether this event represents a spread leg;
 * <li>{@link #isExtendedTradingHours() extendedTradingHours} - whether this event represents an extended trading hours sale;
 * <li>{@link #isValidTick() validTick} - whether this event represents a valid intraday tick;
 * <li>{@link #getType() type} - type of this time and sale event.
 * </ul>
 *
 * <h3><a name="eventFlagsSection">Event flags, transactions and snapshots</a></h3>
 *
 * Some time and sale sources provide a consistent view of the set of known time and sales
 * for a given time range when used with {@link DXFeedTimeSeriesSubscription}.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * The logic behind this property is detailed in {@link IndexedEvent} class documentation.
 * Multiple event sources for the same symbol are not supported for time and sales, thus
 * {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p> Regular subscription via {@link DXFeedSubscription} produces a stream of time and
 * sale events as they happen and their {@link #getEventFlags() eventFlags} are always zero.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list current of time-series events order by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <h3>Publishing Time and Sales</h3>
 *
 * Publishing of time and sales events follows the general rules explained in {@link TimeSeriesEvent} class
 * documentation.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code TimeAndSale}.
 */
@XmlRootElement(name = "TimeAndSale")
@XmlType(propOrder = {
    "eventFlags", "index", "time", "timeNanoPart", "sequence", "exchangeCode", "price", "sizeAsDouble",
    "bidPrice", "askPrice", "exchangeSaleConditions", "tradeThroughExempt", "aggressorSide", "spreadLeg",
    "extendedTradingHours", "validTick", "type", "buyer", "seller"
})
public class TimeAndSale extends MarketEvent implements TimeSeriesEvent<String> {
    private static final long serialVersionUID = 3;

    // ========================= public static =========================

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..16   15...8    7    6    5    4    3    2    1    0
     * +--------+--------+----+----+----+----+----+----+----+----+
     * |        |   TTE  |    |  Side   | SL | ETH| VT |  Type   |
     * +--------+--------+----+----+----+----+----+----+----+----+
     */

    // TTE (TradeThroughExempt) values are ASCII chars in [0, 255].
    static final int TTE_MASK = 0xff;
    static final int TTE_SHIFT = 8;

    // SIDE values are taken from Side enum.
    static final int SIDE_MASK = 3;
    static final int SIDE_SHIFT = 5;

    static final int SPREAD_LEG = 1 << 4;
    static final int ETH = 1 << 3;
    static final int VALID_TICK = 1 << 2;

    // TYPE values are taken from TimeAndSaleType enum.
    static final int TYPE_MASK = 3;
    static final int TYPE_SHIFT = 0;

    // ========================= instance =========================

    /*
     * EventFlags property has several significant bits that are packed into an integer in the following way:
     *    31..7    6    5    4    3    2    1    0
     * +--------+----+----+----+----+----+----+----+
     * |        | SM |    | SS | SE | SB | RE | TX |
     * +--------+----+----+----+----+----+----+----+
     */

    private int eventFlags;

    private long index;
    private int timeNanoPart;
    private char exchangeCode;
    private double price = Double.NaN;
    private double size = Double.NaN;
    private double bidPrice = Double.NaN;
    private double askPrice = Double.NaN;
    private String exchangeSaleConditions;
    private int flags;
    private String buyer;
    private String seller;

    /**
     * Creates new time and sale event with default values.
     */
    public TimeAndSale() {}

    /**
     * Creates new time and sale event with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public TimeAndSale(String eventSymbol) {
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
     * Returns unique per-symbol index of this time and sale event.
     * Time and sale index is composed of {@link #getTime() time} and {@link #getSequence() sequence}.
     * Changing either time or sequence changes event index.
     * @return unique index of this time and sale event.
     */
    @Override
    public long getIndex() {
        return index;
    }

    /**
     * Changes unique per-symbol index of this time and sale event.
     * Time and sale index is composed of {@link #getTime() time} and {@link #getSequence() sequence}
     * and invocation of this method changes time and sequence.
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
     * Changes unique per-symbol index of this time and sale event.
     * @param index the event index.
     * @deprecated Use {@link #setIndex(long)}
     */
    @Deprecated
    public void setEventId(long index) {
        setIndex(index);
    }

    /**
     * Returns timestamp of the original event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return timestamp of the original event.
     */
    @Override
    public long getTime() {
        return (index >> 32) * 1000 + ((index >> 22) & 0x3ff);
    }

    /**
     * Changes timestamp of event in milliseconds.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time timestamp in milliseconds.
     * @see #getTime()
     */
    public void setTime(long time) {
        index = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
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
     * Changes microseconds and nanoseconds time part of event.
     * @param timeNanoPart microseconds and nanoseconds time part of event.
     */
    public void setTimeNanoPart(int timeNanoPart) {
        this.timeNanoPart = timeNanoPart;
    }

    /**
     * Returns microseconds and nanoseconds time part of event.
     * @return microseconds and nanoseconds time part of event.
     */
    public int getTimeNanoPart() {
        return timeNanoPart;
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
     * Returns exchange code of this time and sale event.
     * @return exchange code of this time and sale event.
     */
    public char getExchangeCode() {
        return exchangeCode;
    }

    /**
     * Changes exchange code of this time and sale event.
     * @param exchangeCode exchange code of this time and sale event.
     */
    public void setExchangeCode(char exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    /**
     * Returns price of this time and sale event.
     * @return price of this time and sale event.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes price of this time and sale event.
     * @param price price of this time and sale event.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns size of this time and sale event as integer number (rounded toward zero).
     * @return size of this time and sale event as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getSize() {
        return (long) size;
    }

    /**
     * Changes size of this time and sale event as integer number (rounded toward zero).
     * @param size size of this time and sale event as integer number (rounded toward zero).
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Returns size of this time and sale event as floating number with fractions.
     * @return size of this time and sale event as floating number with fractions.
     */
    @XmlElement(name = "size")
    public double getSizeAsDouble() {
        return size;
    }

    /**
     * Changes size of this time and sale event as floating number with fractions.
     * @param size size of this time and sale event as floating number with fractions.
     */
    public void setSizeAsDouble(double size) {
        this.size = size;
    }

    /**
     * Returns the current bid price on the market when this time and sale event had occurred.
     * @return the current bid price on the market when this time and sale event had occurred.
     */
    public double getBidPrice() {
        return bidPrice;
    }

    /**
     * Changes the current bid price on the market when this time and sale event had occurred.
     * @param bidPrice the current bid price on the market when this time and sale event had occurred.
     */
    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }

    /**
     * Returns the current ask price on the market when this time and sale event had occurred.
     * @return the current ask price on the market when this time and sale event had occurred.
     */
    public double getAskPrice() {
        return askPrice;
    }

    /**
     * Changes the current ask price on the market when this time and sale event had occurred.
     * @param askPrice the current ask price on the market when this time and sale event had occurred.
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
     * Returns TradeThroughExempt flag of this time and sale event.
     * @return TradeThroughExempt flag of this time and sale event.
     */
    public char getTradeThroughExempt() {
        return (char) Util.getBits(flags, TTE_MASK, TTE_SHIFT);
    }

    /**
     * Changes TradeThroughExempt flag of this time and sale event.
     * @param tradeThroughExempt TradeThroughExempt flag of this time and sale event.
     */
    public void setTradeThroughExempt(char tradeThroughExempt) {
        Util.checkChar(tradeThroughExempt, TTE_MASK, "tradeThroughExempt");
        flags = Util.setBits(flags, TTE_MASK, TTE_SHIFT, tradeThroughExempt);
    }

    /**
     * Returns aggressor side of this time and sale event.
     * @return aggressor side of this time and sale event.
     */
    public Side getAggressorSide() {
        return Side.valueOf(Util.getBits(flags, SIDE_MASK, SIDE_SHIFT));
    }

    /**
     * Changes aggressor side of this time and sale event.
     * @param side aggressor side of this time and sale event.
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
     * Returns type of this time and sale event.
     * @return type of this time and sale event.
     */
    public TimeAndSaleType getType() {
        return TimeAndSaleType.valueOf(Util.getBits(flags, TYPE_MASK, TYPE_SHIFT));
    }

    /**
     * Changes type of this time and sale event.
     * @param type type of this time and sale event.
     */
    public void setType(TimeAndSaleType type) {
        flags = Util.setBits(flags, TYPE_MASK, TYPE_SHIFT, type.getCode());
    }

    /**
     * Returns whether this is a new event (not cancellation or correction).
     * It is {@code true} for newly created time and sale event.
     * @return {@code true} if this is a new event (not cancellation or correction).
     */
    @XmlTransient
    public boolean isNew() {
        return getType() == TimeAndSaleType.NEW;
    }

    /**
     * Marks this event as a new event (not cancellation or correction).
     * This is a default state for newly created time and sale event.
     * This method makes {@link #isCancel()} and {@link #isCorrection()} false.
     * @deprecated use {@link #setType setType(TimeAndSaleType.NEW)} instead.
     */
    @Deprecated
    public void setNew() {
        setType(TimeAndSaleType.NEW);
    }

    /**
     * Returns whether this is a correction of a previous event.
     * It is {@code false} for newly created time and sale event.
     * @return {@code true} if this is a correction of a previous event
     */
    @XmlTransient
    public boolean isCorrection() {
        return getType() == TimeAndSaleType.CORRECTION;
    }

    /**
     * Marks this is event as a correction of a previous event.
     * This method makes {@link #isNew()} and {@link #isCancel()} false.
     * @deprecated use {@link #setType setType(TimeAndSaleType.CORRECTION)} instead.
     */
    @Deprecated
    public void setCorrection() {
        setType(TimeAndSaleType.CORRECTION);
    }

    /**
     * Returns whether this is a cancellation of a previous event.
     * It is {@code false} for newly created time and sale event.
     * @return {@code true} if this is a cancellation of a previous event
     */
    @XmlTransient
    public boolean isCancel() {
        return getType() == TimeAndSaleType.CANCEL;
    }

    /**
     * Marks this event as a cancellation of a previous event.
     * This method makes {@link #isNew()} and {@link #isCorrection()} false.
     * @deprecated use {@link #setType setType(TimeAndSaleType.CANCEL)} instead.
     */
    @Deprecated
    public void setCancel() {
        setType(TimeAndSaleType.CANCEL);
    }

    /**
     * Returns buyer of this time and sale event.
     * @return buyer of this time and sale event.
     */
    public String getBuyer() {
        return buyer;
    }

    /**
     * Changes buyer of this time and sale event.
     * @param buyer buyer of this time and sale event.
     */
    public void setBuyer(String buyer) {
        this.buyer = buyer;
    }

    /**
     * Returns seller of this time and sale event.
     * @return seller of this time and sale event.
     */
    public String getSeller() {
        return seller;
    }

    /**
     * Changes seller of this time and sale event.
     * @param seller seller of this time and sale event.
     */
    public void setSeller(String seller) {
        this.seller = seller;
    }

    /**
     * Returns string representation of this time and sale event.
     * @return string representation of this time and sale event.
     */
    @Override
    public String toString() {
        return "TimeAndSale{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", eventFlags=0x" + Integer.toHexString(getEventFlags()) +
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
            (buyer == null ? "" : ", buyer='" + buyer + "'") +
            (seller == null ? "" : ", seller='" + seller + "'") +
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
