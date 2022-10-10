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

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.WideDecimal;
import com.dxfeed.event.LastingEvent;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * Profile information snapshot that contains security instrument description.
 * It represents the most recent information that is available about the traded security
 * on the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Profile} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getDescription() description} - description of the security instrument;
 * <li>{@link #getShortSaleRestriction() shortSaleRestriction} - short sale restriction of the security instrument;
 * <li>{@link #getTradingStatus() tradingStatus} - trading status of the security instrument;
 * <li>{@link #getStatusReason() statusReason} - description of the reason that trading was halted;
 * <li>{@link #getHaltStartTime() haltStartTime} - starting time of the trading halt interval;
 * <li>{@link #getHaltEndTime() haltEndTime} - ending time of the trading halt interval;
 * <li>{@link #getHighLimitPrice() highLimitPrice} - maximal (high) allowed price;
 * <li>{@link #getLowLimitPrice() lowLimitPrice} - minimal (low) allowed price;
 * <li>{@link #getHigh52WeekPrice() high52WeekPrice} - maximal (high) price in last 52 weeks;
 * <li>{@link #getLow52WeekPrice() low52WeekPrice} - minimal (low) price in last 52 weeks;
 * <li>{@link #getBeta() beta} - the correlation coefficient of the instrument to the S&amp;P500 index;
 * <li>{@link #getEarningsPerShare() earningsPerShare} - earnings per share;
 * <li>{@link #getDividendFrequency() dividendFrequency} - Frequency of cash dividends payments per year (calculated);
 * <li>{@link #getExDividendAmount() exDividendAmount} - the amount of the last paid dividend;
 * <li>{@link #getExDividendDayId() exDividendDayId} - identifier of the ex-dividend date;
 * <li>{@link #getShares() shares} - shares outstanding;
 * <li>{@link #getFreeFloat() freeFloat} - the number of shares that are available to the public for trade.
 * </ul>
 *
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Profile}.
 */
@XmlRootElement(name = "Profile")
@XmlType(propOrder = {
    "description", "shortSaleRestriction", "tradingStatus", "statusReason",
    "haltStartTime", "haltEndTime", "highLimitPrice", "lowLimitPrice",
    "high52WeekPrice", "low52WeekPrice",
    "beta", "earningsPerShare", "dividendFrequency",
    "exDividendAmount", "exDividendDayId",
    "shares", "freeFloat"
})
public class Profile extends MarketEvent implements LastingEvent<String> {
    private static final long serialVersionUID = 0;

    // ========================= private static =========================

    /*
     * Flags property has several significant bits that are packed into an integer in the following way:
     *   31..4     3    2    1    0
     * +--------+----+----+----+----+
     * |        |   SSR   |  Status |
     * +--------+----+----+----+----+
     */

    // SSR values are taken from ShortSaleRestriction enum.
    static final int SSR_MASK = 3;
    static final int SSR_SHIFT = 2;

    // STATUS values are taken from TradingStatus enum.
    static final int STATUS_MASK = 3;
    static final int STATUS_SHIFT = 0;

    // ========================= instance =========================

    private String description;
    private String statusReason;
    private long haltStartTime;
    private long haltEndTime;
    private double highLimitPrice = Double.NaN;
    private double lowLimitPrice = Double.NaN;
    private double high52WeekPrice = Double.NaN;
    private double low52WeekPrice = Double.NaN;
    private double beta = Double.NaN;
    private double earningsPerShare = Double.NaN;
    private double dividendFrequency = Double.NaN;
    private double exDividendAmount = Double.NaN;
    private int exDividendDayId;
    private double shares = Double.NaN;
    private double freeFloat = Double.NaN;
    private int flags;

    /**
     * Creates new profile with default values.
     */
    public Profile() {}

    /**
     * Creates new profile with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Profile(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns description of the security instrument.
     * @return description of the security instrument.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Changes description of the security instrument.
     * @param description description of the security instrument.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns short sale restriction of the security instrument.
     * @return short sale restriction of the security instrument.
     */
    public ShortSaleRestriction getShortSaleRestriction() {
        return ShortSaleRestriction.valueOf(Util.getBits(flags, SSR_MASK, SSR_SHIFT));
    }

    /**
     * Changes short sale restriction of the security instrument.
     * @param restriction short sale restriction of the security instrument.
     */
    public void setShortSaleRestriction(ShortSaleRestriction restriction) {
        flags = Util.setBits(flags, SSR_MASK, SSR_SHIFT, restriction.getCode());
    }

    /**
     * Returns short sale restriction status of the security instrument.
     * @return {@code true} if short sale of the security instrument is restricted.
     */
    @XmlTransient
    public boolean isShortSaleRestricted() {
        return getShortSaleRestriction() == ShortSaleRestriction.ACTIVE;
    }

    /**
     * Changes short sale restriction status of the security instrument.
     * @param isShortSaleRestricted {@code true} if short sale of the security instrument is restricted.
     * @deprecated use {@link #setShortSaleRestriction(ShortSaleRestriction) setShortSaleRestriction} instead.
     */
    @Deprecated
    public void setShortSaleRestricted(boolean isShortSaleRestricted) {
        setShortSaleRestriction(isShortSaleRestricted ? ShortSaleRestriction.ACTIVE : ShortSaleRestriction.UNDEFINED);
    }

    /**
     * Returns trading status of the security instrument.
     * @return trading status of the security instrument.
     */
    public TradingStatus getTradingStatus() {
        return TradingStatus.valueOf(Util.getBits(flags, STATUS_MASK, STATUS_SHIFT));
    }

    /**
     * Changes trading status of the security instrument.
     * @param status trading status of the security instrument.
     */
    public void setTradingStatus(TradingStatus status) {
        flags = Util.setBits(flags, STATUS_MASK, STATUS_SHIFT, status.getCode());
    }

    /**
     * Returns trading halt status of the security instrument.
     * @return {@code true} if trading of the security instrument is halted.
     */
    @XmlTransient
    public boolean isTradingHalted() {
        return getTradingStatus() == TradingStatus.HALTED;
    }

    /**
     * Changes trading halt status of the security instrument.
     * @param isTradingHalted {@code true} if trading of the security instrument is halted.
     * @deprecated use {@link #setTradingStatus(TradingStatus) setTradingStatus} instead.
     */
    @Deprecated
    public void setTradingHalted(boolean isTradingHalted) {
        setTradingStatus(isTradingHalted ? TradingStatus.HALTED : TradingStatus.UNDEFINED);
    }

    /**
     * Returns description of the reason that trading was halted.
     * @return description of the reason that trading was halted.
     */
    public String getStatusReason() {
        return statusReason;
    }

    /**
     * Changes description of the reason that trading was halted.
     * @param statusReason description of the reason that trading was halted.
     */
    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    /**
     * Returns starting time of the trading halt interval.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return starting time of the trading halt interval.
     */
    public long getHaltStartTime() {
        return haltStartTime;
    }

    /**
     * Changes starting time of the trading halt interval.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param haltStartTime starting time of the trading halt interval.
     */
    public void setHaltStartTime(long haltStartTime) {
        this.haltStartTime = haltStartTime;
    }

    /**
     * Returns ending time of the trading halt interval.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return ending time of the trading halt interval.
     */
    public long getHaltEndTime() {
        return haltEndTime;
    }

    /**
     * Changes ending time of the trading halt interval.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param haltEndTime ending time of the trading halt interval.
     */
    public void setHaltEndTime(long haltEndTime) {
        this.haltEndTime = haltEndTime;
    }

    /**
     * Returns the maximal (high) allowed price.
     * @return the maximal (high) allowed price.
     */
    public double getHighLimitPrice() {
        return highLimitPrice;
    }

    /**
     * Changes the maximal (high) allowed price.
     * @param highLimitPrice the maximal (high) allowed price.
     */
    public void setHighLimitPrice(double highLimitPrice) {
        this.highLimitPrice = highLimitPrice;
    }

    /**
     * Returns the minimal (low) allowed price.
     * @return the minimal (low) allowed price.
     */
    public double getLowLimitPrice() {
        return lowLimitPrice;
    }

    /**
     * Changes the minimal (low) allowed price.
     * @param lowLimitPrice the minimal (low) allowed price.
     */
    public void setLowLimitPrice(double lowLimitPrice) {
        this.lowLimitPrice = lowLimitPrice;
    }

    /**
     * Returns the maximal (high) price in last 52 weeks.
     * @return the maximal (high) price in last 52 weeks.
     */
    public double getHigh52WeekPrice() {
        return high52WeekPrice;
    }

    /**
     * Changes the maximal (high) price in last 52 weeks.
     * @param high52WeekPrice the maximal (high) price in last 52 weeks.
     */
    public void setHigh52WeekPrice(double high52WeekPrice) {
        this.high52WeekPrice = high52WeekPrice;
    }

    /**
     * Returns the minimal (low) price in last 52 weeks.
     * @return the minimal (low) price in last 52 weeks.
     */
    public double getLow52WeekPrice() {
        return low52WeekPrice;
    }

    /**
     * Changes the minimal (low) price in last 52 weeks.
     * @param low52WeekPrice the minimal (low) price in last 52 weeks.
     */
    public void setLow52WeekPrice(double low52WeekPrice) {
        this.low52WeekPrice = low52WeekPrice;
    }

    /**
     * Returns the correlation coefficient of the instrument to the S&amp;P500 index.
     * @return the correlation coefficient of the instrument to the S&amp;P500 index.
     */
    public double getBeta() {
        return beta;
    }

    /**
     * Changes the correlation coefficient of the instrument to the S&amp;P500 index.
     * @param beta the correlation coefficient of the instrument to the S&amp;P500 index
     */
    public void setBeta(double beta) {
        this.beta = beta;
    }

    /**
     * Returns earnings per share (the company’s profits divided by the number of shares).
     * @return earnings per share
     */
    public double getEarningsPerShare() {
        return earningsPerShare;
    }

    /**
     * Changes Earnings per share (the company’s profits divided by the number of shares).
     * @param earningsPerShare earnings per share
     */
    public void setEarningsPerShare(double earningsPerShare) {
        this.earningsPerShare = earningsPerShare;
    }

    /**
     * Returns frequency of cash dividends payments per year (calculated).
     * @return Frequency of cash dividends payments per year
     */
    public double getDividendFrequency() {
        return dividendFrequency;
    }

    /**
     * Changes frequency of cash dividends payments per year.
     * @param dividendFrequency frequency of cash dividends payments per year
     */
    public void setDividendFrequency(double dividendFrequency) {
        this.dividendFrequency = dividendFrequency;
    }

    /**
     * Returns the amount of the last paid dividend.
     * @return the amount of the last paid dividend
     */
    public double getExDividendAmount() {
        return exDividendAmount;
    }

    /**
     * Changes the amount of the last paid dividend.
     * @param exDividendAmount the amount of the last paid dividend
     */
    public void setExDividendAmount(double exDividendAmount) {
        this.exDividendAmount = exDividendAmount;
    }

    /**
     * Returns identifier of the day of the last dividend payment (ex-dividend date).
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @return the identifier of the day of the last dividend payment
     */
    public int getExDividendDayId() {
        return exDividendDayId;
    }

    /**
     * Changes identifier of the day of the last dividend payment (ex-dividend date).
     * Identifier of the day is the number of days passed since January 1, 1970.
     * @param exDividendDayId identifier of the day of the last dividend payment
     */
    public void setExDividendDayId(int exDividendDayId) {
        this.exDividendDayId = exDividendDayId;
    }

    /**
     * Returns the number of shares outstanding.
     * @return shares outstanding
     */
    public double getShares() {
        return shares;
    }

    /**
     * Changes the number of shares outstanding.
     * @param shares shares outstanding.
     */
    public void setShares(double shares) {
        this.shares = shares;
    }

    /**
     * Returns free-float - the number of shares outstanding that are available to the public for trade.
     * @return free-float
     */
    public double getFreeFloat() {
        return freeFloat;
    }

    /**
     * Changes free-float - the number of shares outstanding that are available to the public for trade
     * @param freeFloat the number of shares outstanding that are available to the public for trade
     */
    public void setFreeFloat(double freeFloat) {
        this.freeFloat = freeFloat;
    }

    /**
     * Returns string representation of this profile event.
     * @return string representation of this profile event.
     */
    @Override
    public String toString() {
        return "Profile{" + baseFieldsToString() +
            "}";
    }

    // ==================== protected access for inherited classes ====================

    protected String baseFieldsToString() {
        return getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", description='" + description + "'" +
            ", SSR=" + getShortSaleRestriction() +
            ", status=" + getTradingStatus() +
            ", statusReason='" + statusReason + "'" +
            ", haltStartTime=" + TimeFormat.DEFAULT.format(haltStartTime) +
            ", haltEndTime=" + TimeFormat.DEFAULT.format(haltEndTime) +
            ", highLimitPrice=" + highLimitPrice +
            ", lowLimitPrice=" + lowLimitPrice +
            ", high52WeekPrice=" + high52WeekPrice +
            ", low52WeekPrice=" + low52WeekPrice +
            ", beta=" + beta +
            ", earningsPerShare=" + earningsPerShare +
            ", dividendFrequency=" + dividendFrequency +
            ", exDividendAmount=" + exDividendAmount +
            ", exDividendDay=" + DayUtil.getYearMonthDayByDayId(exDividendDayId) +
            ", shares=" + WideDecimal.toString(WideDecimal.composeWide(shares)) +
            ", freeFloat=" + WideDecimal.toString(WideDecimal.composeWide(freeFloat));
    }

    // ========================= protected access for delegate =========================

    /**
     * Returns implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @return flags.
     */
    protected int getFlags() {
        return flags;
    }

    /**
     * Changes implementation-specific flags.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @param flags flags.
     */
    protected void setFlags(int flags) {
        this.flags = flags;
    }
}
