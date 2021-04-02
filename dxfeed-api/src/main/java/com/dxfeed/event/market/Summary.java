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

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.dxfeed.event.LastingEvent;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Summary information snapshot about the trading session including session highs, lows, etc.
 * It represents the most recent information that is available about the trading session in
 * the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Summary} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getDayId() dayId} - identifier of the day that this summary represents;
 * <li>{@link #getDayOpenPrice() dayOpenPrice} - the first (open) price for the day;
 * <li>{@link #getDayHighPrice() dayHighPrice} - the maximal (high) price for the day;
 * <li>{@link #getDayLowPrice() dayLowPrice} - the minimal (low) price for the day;
 * <li>{@link #getDayClosePrice() dayClosePrice} - the last (close) price for the day;
 * <li>{@link #getDayClosePriceType() dayClosePriceType} - the price type of the last (close) price for the day;
 * <li>{@link #getPrevDayId() prevDayId} - identifier of the previous day that this summary represents;
 * <li>{@link #getPrevDayClosePrice() prevDayClosePrice} - the last (close) price for the previous day;
 * <li>{@link #getPrevDayClosePriceType() prevDayClosePriceType} - the price type of the last (close) price for the previous day;
 * <li>{@link #getPrevDayVolume() prevDayVolume} - total volume traded for the previous day;
 * <li>{@link #getOpenInterest() openInterest} - open interest of the symbol as the number of open contracts.
 * </ul>
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code Summary} and {@code Summary&X}
 * for regional exchange trading session summaries.
 */
@XmlRootElement(name = "Summary")
@XmlType(propOrder = {
    "dayId", "dayOpenPrice", "dayHighPrice", "dayLowPrice", "dayClosePrice", "dayClosePriceType",
    "prevDayId", "prevDayClosePrice", "prevDayClosePriceType", "prevDayVolume", "openInterest"
})
public class Summary extends MarketEvent implements LastingEvent<String> {
    private static final long serialVersionUID = 0;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..4     3    2    1    0
     * +--------+----+----+----+----+
     * |        |  Close  |PrevClose|
     * +--------+----+----+----+----+
     */

    // PRICE_TYPE values are taken from PriceType enum.
    static final int DAY_CLOSE_PRICE_TYPE_MASK = 3;
    static final int DAY_CLOSE_PRICE_TYPE_SHIFT = 2;

    // PRICE_TYPE values are taken from PriceType enum.
    static final int PREV_DAY_CLOSE_PRICE_TYPE_MASK = 3;
    static final int PREV_DAY_CLOSE_PRICE_TYPE_SHIFT = 0;

    // ========================= instance =========================

    private int dayId;
    private double dayOpenPrice = Double.NaN;
    private double dayHighPrice = Double.NaN;
    private double dayLowPrice = Double.NaN;
    private double dayClosePrice = Double.NaN;
    private int prevDayId;
    private double prevDayClosePrice = Double.NaN;
    private double prevDayVolume = Double.NaN;
    private long openInterest;
    private int flags;

    /**
     * Creates new summary with default values.
     */
    public Summary() {}

    /**
     * Creates new summary with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Summary(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns identifier of the day that this summary represents.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @return identifier of the day that this summary represents.
     */
    public int getDayId() {
        return dayId;
    }

    /**
     * Changes identifier of the day that this summary represents.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @param dayId identifier of the day that this summary represents.
     */
    public void setDayId(int dayId) {
        this.dayId = dayId;
    }

    /**
     * Returns the first (open) price for the day.
     * @return the first (open) price for the day.
     */
    public double getDayOpenPrice() {
        return dayOpenPrice;
    }

    /**
     * Changes the first (open) price for the day.
     * @param dayOpenPrice the first (open) price for the day.
     */
    public void setDayOpenPrice(double dayOpenPrice) {
        this.dayOpenPrice = dayOpenPrice;
    }

    /**
     * Returns the maximal (high) price for the day.
     * @return the maximal (high) price for the day.
     */
    public double getDayHighPrice() {
        return dayHighPrice;
    }

    /**
     * Changes the maximal (high) price for the day.
     * @param dayHighPrice the maximal (high) price for the day.
     */
    public void setDayHighPrice(double dayHighPrice) {
        this.dayHighPrice = dayHighPrice;
    }

    /**
     * Returns the minimal (low) price for the day.
     * @return the minimal (low) price for the day.
     */
    public double getDayLowPrice() {
        return dayLowPrice;
    }

    /**
     * Changes the minimal (low) price for the day.
     * @param dayLowPrice the minimal (low) price for the day.
     */
    public void setDayLowPrice(double dayLowPrice) {
        this.dayLowPrice = dayLowPrice;
    }

    /**
     * Returns the last (close) price for the day.
     * @return the last (close) price for the day.
     */
    public double getDayClosePrice() {
        return dayClosePrice;
    }

    /**
     * Changes the last (close) price for the day.
     * @param dayClosePrice the last (close) price for the day.
     */
    public void setDayClosePrice(double dayClosePrice) {
        this.dayClosePrice = dayClosePrice;
    }

    /**
     * Returns the price type of the last (close) price for the day.
     * @return the price type of the last (close) price for the day.
     */
    public PriceType getDayClosePriceType() {
        return PriceType.valueOf(Util.getBits(flags, DAY_CLOSE_PRICE_TYPE_MASK, DAY_CLOSE_PRICE_TYPE_SHIFT));
    }

    /**
     * Changes the price type of the last (close) price for the day.
     * @param type the price type of the last (close) price for the day.
     */
    public void setDayClosePriceType(PriceType type) {
        flags = Util.setBits(flags, DAY_CLOSE_PRICE_TYPE_MASK, DAY_CLOSE_PRICE_TYPE_SHIFT, type.getCode());
    }

    /**
     * Returns identifier of the previous day that this summary represents.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @return identifier of the previous day that this summary represents.
     */
    public int getPrevDayId() {
        return prevDayId;
    }

    /**
     * Changes identifier of the previous day that this summary represents.
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @param prevDayId identifier of the previous day that this summary represents.
     */
    public void setPrevDayId(int prevDayId) {
        this.prevDayId = prevDayId;
    }

    /**
     * Returns the last (close) price for the previous day.
     * @return the last (close) price for the previous day.
     */
    public double getPrevDayClosePrice() {
        return prevDayClosePrice;
    }

    /**
     * Changes the last (close) price for the previous day.
     * @param prevDayClosePrice the last (close) price for the previous day.
     */
    public void setPrevDayClosePrice(double prevDayClosePrice) {
        this.prevDayClosePrice = prevDayClosePrice;
    }

    /**
     * Returns the price type of the last (close) price for the previous day.
     * @return the price type of the last (close) price for the previous day.
     */
    public PriceType getPrevDayClosePriceType() {
        return PriceType.valueOf(Util.getBits(flags, PREV_DAY_CLOSE_PRICE_TYPE_MASK, PREV_DAY_CLOSE_PRICE_TYPE_SHIFT));
    }

    /**
     * Changes the price type of the last (close) price for the previous day.
     * @param type the price type of the last (close) price for the previous day.
     */
    public void setPrevDayClosePriceType(PriceType type) {
        flags = Util.setBits(flags, PREV_DAY_CLOSE_PRICE_TYPE_MASK, PREV_DAY_CLOSE_PRICE_TYPE_SHIFT, type.getCode());
    }

    /**
     * Returns total volume traded for the previous day.
     * @return total volume traded for the previous day.
     */
    public double getPrevDayVolume() {
        return prevDayVolume;
    }

    /**
     * Changes total volume traded for the previous day.
     * @param prevDayVolume total volume traded for the previous day.
     */
    public void setPrevDayVolume(double prevDayVolume) {
        this.prevDayVolume = prevDayVolume;
    }

    /**
     * Returns open interest of the symbol as the number of open contracts.
     * @return open interest of the symbol as the number of open contracts.
     */
    public long getOpenInterest() {
        return openInterest;
    }

    /**
     * Changes open interest of the symbol as the number of open contracts.
     * @param openInterest open interest of the symbol as the number of open contracts.
     */
    public void setOpenInterest(long openInterest) {
        this.openInterest = openInterest;
    }

    /**
     * Returns string representation of this summary event.
     * @return string representation of this summary event.
     */
    @Override
    public String toString() {
        return "Summary{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", day=" + DayUtil.getYearMonthDayByDayId(dayId) +
            ", dayOpen=" + dayOpenPrice +
            ", dayHigh=" + dayHighPrice +
            ", dayLow=" + dayLowPrice +
            ", dayClose=" + dayClosePrice +
            ", dayCloseType=" + getDayClosePriceType() +
            ", prevDay=" + DayUtil.getYearMonthDayByDayId(prevDayId) +
            ", prevDayClose=" + prevDayClosePrice +
            ", prevDayCloseType=" + getPrevDayClosePriceType() +
            ", prevDayVolume=" + prevDayVolume +
            ", openInterest=" + openInterest +
            '}';
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
