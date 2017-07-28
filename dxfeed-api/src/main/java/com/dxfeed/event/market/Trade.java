/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Trade event is a snapshot of the price and size of the last trade during regular trading hours
 * and an overall day volume and day turnover.
 * It represents the most recent information that is available about the regular last trade price on
 * the market at any given moment of time.
 *
 * <h3>Properties</h3>
 *
 * {@code Trade} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getTime() time} - time of the last trade;
 * <li>{@link #getTimeNanoPart() timeNanoPart} - microseconds and nanoseconds time part of the last trade;
 * <li>{@link #getSequence() sequence} - sequence of the last trade;
 * <li>{@link #getExchangeCode() exchangeCode} - exchange code of the last trade;
 * <li>{@link #getPrice() price} - price of the last trade;
 * <li>{@link #getSize() size} - size of the last trade;
 * <li>{@link #getDayVolume() dayVolume} - total volume traded for a day;
 * <li>{@link #getDayTurnover() dayTurnover} - total turnover traded for a day;
 * <li>{@link #getTickDirection() tickDirection} - tick direction of the last trade;
 * <li>{@link #isExtendedTradingHours() extendedTradingHours} - whether the last trade was in extended trading hours.
 * </ul>
 *
 * <h3>Trading sessions</h3>
 *
 * The {@code Trade} event defines last trade {@link #getPrice() price} as officially defined
 * by the corresponding exchange for its <b>regular trading hours</b> (RTH).
 * It also include an official exchange {@link #getDayVolume() dayVolume} and {@link #getDayTurnover() dayTurnover}
 * <b>for the whole trading day</b>. So, {@code Trade} event captures all the official numbers
 * that are typically reported by exchange.
 *
 * <p>Trades that happen in <b>extended trading hours</b> (ETH, pre-market and post-market trading sessions),
 * which are typically defined for stocks and ETFs, do not update last trade {@link #getTime() time},
 * {@link #getExchangeCode() exchangeCode}, {@link #getPrice() price}, {@link #getSize() size},
 * and {@link #getTickDirection() tickDirection} in the {@code Trade} event, but they do update
 * {@link #getDayVolume() dayVolume} and {@link #getDayTurnover() dayTurnover}.
 *
 * <p>During extended trading hours a {@link TradeETH} event is generated on each trade with its
 * {@link TradeETH#isExtendedTradingHours() extendedTradingHours} property set to {@code true}.</p>
 *
 * <h3>Volume and Turnover</h3>
 *
 * <p>The volume and turnover are included into the {@code Trade} record instead
 * of {@link Summary} record, because both volume and turnover typically update with each trade.
 * This solution avoids generation of multiple events on each trade during regular trading hours.
 * {@link Summary} event is generated during the trading day only when new highs
 * or lows are reached or other properties change.
 *
 * <p>Note that one can compute volume-weighted average price (VWAP) for a day by this formula:
 * <br><code>vwap = {@link #getDayTurnover() dayTurnover} / {@link #getDayVolume() dayVolume};</code>
 *
 * <h3>Daily reset</h3>
 *
 * Daily reset procedure that happens on a schedule during non-trading hours resets {@code Trade}
 * {@link #getDayVolume() dayVolume} to zero and {@link #getDayTurnover() dayTurnover} to {@link Double#NaN NaN}
 * in preparation to the next day's pre-market trading session
 * (or for regular trading if there is no pre-market) while leaving all other properties intact.
 * They reflect information about the last known RTH trade until the next RTH trade happens.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS records {@code Trade} and {@code Trade&X}
 * for regional exchange trades.
 * Regional records do not explicitly store a field for {@link #getExchangeCode() exchangeCode} property.
 */
@XmlRootElement(name = "Trade")
public class Trade extends TradeBase {
    private static final long serialVersionUID = 0;

    /**
     * Creates new trade with default values.
     */
    public Trade() {}

    /**
     * Creates new trade with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Trade(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns string representation of this trade event.
     * @return string representation of this trade event.
     */
    @Override
    public String toString() {
        return "Trade{" + baseFieldsToString() +
            "}";
    }
}
