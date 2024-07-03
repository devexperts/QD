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
package com.dxfeed.event.market;

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.impl.TimeNanosUtil;
import com.dxfeed.impl.XmlTimeAdapter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Base class for common fields of {@link Trade} and {@link TradeETH} events.
 * Trade events represent the most recent information that is available about the last trade on the market
 * at any given moment of time.
 *
 * <p>{@link Trade} event represents last trade information for <b>regular trading hours</b> (RTH)
 * with an official volume and turnover <b>for the whole trading day</b> identified by {@link #getDayId() dayId} field.
 *
 * <p>{@link TradeETH} event is defined only for symbols (typically stocks and ETFs) with a designated
 * <b>extended trading hours</b> (ETH, pre market and post market trading sessions). It represents
 * last trade price during ETH and also accumulated volume and turnover during ETH for current trading day.
 */
@XmlType(propOrder = {
    "time", "timeNanoPart", "sequence", "exchangeCode", "price", "change", "sizeAsDouble",
    "dayId", "dayVolumeAsDouble", "dayTurnover", "tickDirection", "extendedTradingHours"
})
public abstract class TradeBase extends MarketEvent implements LastingEvent<String> {
    private static final long serialVersionUID = 1;

    // ========================= public static =========================

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..4     3    2    1    0
     * +--------+----+----+----+----+
     * |        |  Direction   | ETH|
     * +--------+----+----+----+----+
     */

    // DIRECTION values are taken from Direction enum.
    static final int DIRECTION_MASK = 7;
    static final int DIRECTION_SHIFT = 1;

    static final int ETH = 1;

    // ========================= instance =========================

    private long timeSequence;
    private int timeNanoPart;
    private char exchangeCode;
    private double price = Double.NaN;
    private double change = Double.NaN;
    private double size = Double.NaN;
    private int dayId;
    private double dayVolume = Double.NaN;
    private double dayTurnover = Double.NaN;
    private int flags;

    /**
     * Creates new trade with default values.
     */
    TradeBase() {}

    /**
     * Creates new trade with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    TradeBase(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns time and sequence of last trade packaged into single long value.
     * @return time and sequence of last trade.
     */
    @XmlTransient
    public long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of last trade.
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
     * Returns time of the last trade.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of the last trade.
     */
    @XmlJavaTypeAdapter(type=long.class, value=XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of the last trade.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time time of the last trade.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns time of the last trade in nanoseconds.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of the last trade in nanoseconds.
     */
    @XmlTransient
    public long getTimeNanos() {
        return TimeNanosUtil.getNanosFromMillisAndNanoPart(getTime(), timeNanoPart);
    }

    /**
     * Changes time of the last trade.
     * Time is measured in nanoseconds between the current time and midnight, January 1, 1970 UTC.
     * @param timeNanos time of the last trade in nanoseconds.
     */
    public void setTimeNanos(long timeNanos) {
        setTime(TimeNanosUtil.getMillisFromNanos(timeNanos));
        timeNanoPart = TimeNanosUtil.getNanoPartFromNanos(timeNanos);
    }

    /**
     * Changes microseconds and nanoseconds time part of the last trade.
     * @param timeNanoPart microseconds and nanoseconds time part of the last trade.
     */
    public void setTimeNanoPart(int timeNanoPart) {
        this.timeNanoPart = timeNanoPart;
    }

    /**
     * Returns microseconds and nanoseconds time part of the last trade.
     * @return microseconds and nanoseconds time part of the last trade.
     */
    public int getTimeNanoPart() {
        return timeNanoPart;
    }

    /**
     * Returns sequence number of the last trade to distinguish trades that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of the last trade.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence() sequence number} of the last trade.
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
     * Returns exchange code of the last trade.
     * @return exchange code of the last trade.
     */
    public char getExchangeCode() {
        return exchangeCode;
    }

    /**
     * Changes exchange code of the last trade.
     * @param exchangeCode exchange code of the last trade.
     */
    public void setExchangeCode(char exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    /**
     * Returns price of the last trade.
     * @return price of the last trade.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Changes price of the last trade.
     * @param price price of the last trade.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns size of the last trade as integer number (rounded toward zero).
     * @return size of the last trade as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getSize() {
        return (long) size;
    }

    /**
     * Changes size of the last trade as integer number (rounded toward zero).
     * @param size size of the last trade as integer number (rounded toward zero).
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Returns size of the last trade as floating number with fractions.
     * @return size of the last trade as floating number with fractions.
     */
    @XmlElement(name = "size")
    public double getSizeAsDouble() {
        return size;
    }

    /**
     * Changes size of the last trade as floating number with fractions.
     * @param size size of the last trade as floating number with fractions.
     */
    public void setSizeAsDouble(double size) {
        this.size = size;
    }

    /**
     * Returns identifier of the current trading day.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @return identifier of the current trading day.
     */
    public int getDayId() {
        return dayId;
    }

    /**
     * Changes identifier of the current trading day.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @param dayId identifier of the current trading day.
     */
    public void setDayId(int dayId) {
        this.dayId = dayId;
    }

    /**
     * Returns total volume traded for a day as integer number (rounded toward zero).
     * @return total volume traded for a day as integer number (rounded toward zero).
     */
    @XmlTransient
    public long getDayVolume() {
        return (long) dayVolume;
    }

    /**
     * Changes total volume traded for a day as integer number (rounded toward zero).
     * @param dayVolume total volume traded for a day as integer number (rounded toward zero).
     */
    public void setDayVolume(long dayVolume) {
        this.dayVolume = dayVolume;
    }

    /**
     * Returns total volume traded for a day as floating number with fractions.
     * @return total volume traded for a day as floating number with fractions.
     */
    @XmlElement(name = "dayVolume")
    public double getDayVolumeAsDouble() {
        return dayVolume;
    }

    /**
     * Changes total volume traded for a day as floating number with fractions.
     * @param dayVolume total volume traded for a day as floating number with fractions.
     */
    public void setDayVolumeAsDouble(double dayVolume) {
        this.dayVolume = dayVolume;
    }

    /**
     * Returns total turnover traded for a day.
     * Day VWAP can be computed with <code>getDayTurnover() / {@link #getDayVolume getDayVolume}()</code>.
     * @return total turnover traded for a day.
     */
    public double getDayTurnover() {
        return dayTurnover;
    }

    /**
     * Changes total turnover traded for a day.
     * @param dayTurnover total turnover traded for a day.
     */
    public void setDayTurnover(double dayTurnover) {
        this.dayTurnover = dayTurnover;
    }

    /**
     * Returns tick direction of the last trade.
     * @return tick direction of the last trade.
     */
    public Direction getTickDirection() {
        return Direction.valueOf(Util.getBits(flags, DIRECTION_MASK, DIRECTION_SHIFT));
    }

    /**
     * Changes tick direction of the last trade.
     * @param direction tick direction of the last trade.
     */
    public void setTickDirection(Direction direction) {
        flags = Util.setBits(flags, DIRECTION_MASK, DIRECTION_SHIFT, direction.getCode());
    }

    /**
     * Returns whether last trade was in extended trading hours.
     * @return {@code true} if last trade was in extended trading hours.
     */
    public boolean isExtendedTradingHours() {
        return (flags & ETH) != 0;
    }

    /**
     * Changes whether last trade was in extended trading hours.
     * @param extendedTradingHours {@code true} if last trade was in extended trading hours.
     */
    public void setExtendedTradingHours(boolean extendedTradingHours) {
        flags = extendedTradingHours ? flags | ETH : flags & ~ETH;
    }

    /**
     * Returns change of the last trade.
     * @return change of the last trade.
     */
    public double getChange() {
        return change;
    }

    /**
     * Changes change of the last trade.
     * @param change price of the last trade.
     */
    public void setChange(double change) {
        this.change = change;
    }

    /**
     * Returns string representation of this trade event.
     * @return string representation of this trade event.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + baseFieldsToString() + "}";
    }

    // ========================= package private access for Trade and TradeETH =========================

    /**
     * Returns string representation of this trade event's fields.
     * @return string representation of this trade event's fields.
     */
    String baseFieldsToString() {
        return getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", timeNanoPart=" + timeNanoPart +
            ", sequence=" + getSequence() +
            ", exchange=" + Util.encodeChar(exchangeCode) +
            ", price=" + price +
            ", change=" + change +
            ", size=" + size +
            ", day=" + DayUtil.getYearMonthDayByDayId(dayId) +
            ", dayVolume=" + dayVolume +
            ", dayTurnover=" + dayTurnover +
            ", direction=" + getTickDirection() +
            ", ETH=" + isExtendedTradingHours();
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
