/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import javax.xml.bind.annotation.XmlRootElement;

import com.devexperts.util.TimeFormat;
import com.dxfeed.event.LastingEvent;

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
 * <li>{@link #getLowLimitPrice() lowLimitPrice} - minimal (low) allowed price.
 * </ul>
 *
 * Bid corresponds to the best (maximal price) order to buy, while
 * ask corresponds to the best (minimal price) order to sell.
 *
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Profile}.
 */
@XmlRootElement(name = "Profile")
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
            ", lowLimitPrice=" + lowLimitPrice;
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
