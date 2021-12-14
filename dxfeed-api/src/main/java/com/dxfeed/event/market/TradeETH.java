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

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.LastingEvent;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * TradeETH event is a snapshot of the price and size of the last trade during
 * extended trading hours and the extended trading hours day volume and day turnover.
 * This event is defined only for symbols (typically stocks and ETFs) with a designated
 * <b>extended trading hours</b>  (ETH, pre market and post market trading sessions).
 * It represents the most recent information that is available about
 * ETH last trade on the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code TradeETH} event has the following properties:
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * </ul>
 * Information about last trade which occurred in <b>extended trading hours</b>
 * <ul>
 * <li>{@link #getTime() time} - time of the last trade;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of the last trade;
 * <li>{@link #getSequence() sequence} - sequence of the last trade;
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of the last trade;
 * <li>{@link #getPrice() price} - price of the last trade;
 * <li>{@link #getChange() change} - change of the last trade;
 * <li>{@link #getSize() size} - size of the last trade as integer number (rounded toward zero);
 * <li>{@link #getSizeAsDouble() sizeAsDouble} - size of the last trade as floating number with fractions;
 * <li>{@link #getTickDirection() tickDirection} - tick direction of the last trade;
 * <li>{@link #isExtendedTradingHours() extendedTradingHours} - whether the last trade was in extended trading hours;
 * </ul>
 * Accumulated trade statistics for a current day - only <b>extended trading hours</b>
 * <ul>
 * <li>{@link #getDayId() dayId} - identifier of the current trading day;
 * <li>{@link #getDayVolume() dayVolume} - total volume traded for a day as integer number (rounded toward zero);
 * <li>{@link #getDayVolumeAsDouble() dayVolumeAsDouble} - total volume traded for a day as floating number with fractions;
 * <li>{@link #getDayTurnover() dayTurnover} - total turnover traded for a day;
 * </ul>
 *
 * <h3>Trading sessions</h3>
 *
 * The {@code TradeETH} event defines last trade {@link #getPrice() price} as officially defined
 * by the corresponding exchange for its <b>extended trading hours</b> (ETH).
 * It also includes {@link #getDayVolumeAsDouble() dayVolumeAsDouble} and {@link #getDayTurnover() dayTurnover}
 * <b>for the extended trading hours only</b> of the trading day identified by {@link #getDayId() dayId}.
 * This event is not defined for symbols that has no concept of ETH.
 *
 * <p>When the first trade of <b>regular trading hours</b> (RTH) happens, then {@code TradeETH} event is generated
 * with {@link #isExtendedTradingHours() extendedTradingHours} property set to {@code false}. Afterwards, during RTH,
 * {@code TradeETH} event is not updated and retains information about the last trade, volume and turnover of the pre market trading session.
 *
 * <p>When the first trade of <b>extended trading hours</b> (ETH) happens, then {@code TradeETH} event is generated
 * with {@link #isExtendedTradingHours() extendedTradingHours} property set to {@code true}. Afterwards, during ETH,
 * {@code TradeETH} event is updated on each trade with the last trade information from post market trading session
 * and total volume and turnover of the pre and post market trading session (excluding the volume and turnover of a regular trading session).
 *
 * Note, that during pre- and post-market sessions, {@link Trade} event also updates, but only its
 * {@link Trade#getDayVolumeAsDouble() dayVolumeAsDouble} and {@link Trade#getDayTurnover() dayTurnover} properties change
 * to reflect the overall official volume and turnover as reported by exchanges.
 * During post market trading session, exchanges may correct their official RTH last trading price, which results
 * in the update to {@link Trade} event.
 *
 * <h3>Volume and Turnover</h3>
 *
 * <p>Note that one can compute volume-weighted average price (VWAP) for extended trading hours by this formula:
 * <br><code>vwap = {@link #getDayTurnover() dayTurnover} / {@link #getDayVolumeAsDouble() dayVolumeAsDouble};</code>
 *
 * <h3>Daily reset</h3>
 *
 * Daily reset procedure that happens on a schedule during non-trading hours resets {@code TradeETH}
 * {@link #getDayVolumeAsDouble() dayVolumeAsDouble} and {@link #getDayTurnover() dayTurnover} to {@link Double#NaN NaN}
 * and sets {@link #getDayId() dayId} to the next trading day in preparation to the next day's pre-market trading session
 * (or for regular trading if there is no pre-market) while leaving all other properties intact.
 * They reflect information about the last known ETH trade until the next ETH trade happens.
 *
 * <h3>The most recent last trade price</h3>
 *
 * The most recent last trade price ("extended last price") in the market can be found by combining information from both
 * {@link Trade} and {@link TradeETH} events using {@link #isExtendedTradingHours() isExtendedTradingHours} method to figure out
 * which trading session had the most recent trade. The following piece of code finds the most
 * recent last trade price from the given {@link DXFeed feed} for a given {@code symbol},
 * assuming there is a {@link DXFeedSubscription subscription} for both {@link Trade} and {@link TradeETH} events
 * for the given {@code symbol}:
 *
 * <pre><tt>
 *     {@link Trade} trade = feed.{@link DXFeed#getLastEvent(LastingEvent) getLastEvent}(<b>new</b> {@link Trade#Trade(String) Trade}(symbol));
 *     {@link TradeETH} tradeEth = feed.{@link DXFeed#getLastEvent(LastingEvent) getLastEvent}(<b>new</b> {@link TradeETH#TradeETH(String) TradeETH}(symbol));
 *     <b>double</b> extLast = tradeEth.{@link #isExtendedTradingHours() isExtendedTradingHours}() ? tradeEth.{@link #getPrice() getPrice}() : trade.{@link Trade#getPrice() getPrice}();
 * </tt></pre>
 *
 * Note, that the above code works correctly for symbols that has no concept of ETH, too, because in this
 * case the {@link DXFeed#getLastEvent(LastingEvent) DXFeed.getLastEvent} leaves default values in {@code TradeETH}
 * event properties, which means that {@link #isExtendedTradingHours() extendedTradingHours} flag is {@code false}
 * and a regular {@link Trade#getPrice() Trade.getPrice} is used.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code TradeETH} and {@code TradeETH&X}
 * for regional exchange extended trade hours.
 * {@link #isExtendedTradingHours() extendedTradingHours} property is internally represented as a last bit of the "Flags" field of the record.
 * Regional records do not explicitly store a field for {@link #getExchangeCode() exchangeCode} property.
 */
@XmlRootElement(name = "TradeETH")
public class TradeETH extends TradeBase {
    private static final long serialVersionUID = 0;

    /**
     * Creates new trade with default values.
     */
    public TradeETH() {}

    /**
     * Creates new trade with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public TradeETH(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns whether last trade was in extended trading hours.
     * @return {@code true} if last trade was in extended trading hours.
     * @deprecated Use {@link #isExtendedTradingHours()} instead.
     */
    @XmlTransient
    @Deprecated
    public boolean isETH() {
        return isExtendedTradingHours();
    }

    /**
     * Changes whether last trade was in extended trading hours.
     * @param extendedTradingHours {@code true} if last trade was in extended trading hours.
     * @deprecated Use {@link #setExtendedTradingHours(boolean)} instead.
     */
    @Deprecated
    public void setETH(boolean extendedTradingHours) {
        setExtendedTradingHours(extendedTradingHours);
    }

    /**
     * Returns string representation of this trade event.
     * @return string representation of this trade event.
     */
    @Override
    public String toString() {
        return "TradeETH{" + baseFieldsToString() +
            "}";
    }
}
