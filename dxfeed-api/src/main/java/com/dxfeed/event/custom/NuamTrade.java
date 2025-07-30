/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.custom;

import com.devexperts.annotation.Experimental;
import com.devexperts.util.TimeFormat;
import com.dxfeed.event.market.Trade;
import com.dxfeed.impl.XmlTimeAdapter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Represents an extension of {@link Trade} for the symbols traded on the Nuam Exchange.
 * It is the new Regional Holding that integrates the Santiago, Lima, and Colombia Stock Exchanges into a single market.
 *
 * <p>It includes the most recent information about Nuam trade statistics published after every trade for
 * the current trading day such as number of trades, volume-weighted average price, etc.
 *
 * <h3>Properties</h3>
 * <p>
 * {@code NuamTrade} event has the following properties:
 *
 * <ul>
 * <li>{@link #getTradeStatTime() tradeStatTime} - time when trade statistics information was updated;
 * <li>{@link #getLastSignificantPrice() lastSignificantPrice} - price of the last trade reported as significant
 * by the exchange;
 * <li>{@link #getLastPriceForAll() lastPriceForAll} - price of the last trade (for all deals) reported by the exchange;
 * <li>{@link #getNumberOfTrades() numberOfTrades} - number of trades reported by the exchange;
 * <li>{@link #getVWAP() vwap} - volume-weighted average price (VWAP) reported by the exchange;
 * </ul>
 *
 * <h3>Nuam trade statistics details</h3>
 * All extended fields beyond the {@link Trade} represent Nuam trade statistics.
 * These statistics are sent by the exchange in a separate message following each trade message.
 * For this reason, these fields are updated independently of last trade information.
 * Use {@link #getTradeStatTime() tradeStatTime} and {@link #getTime() time} to distinguish
 * between last trade and trade statistics updates.
 *
 * <p>All Nuam trade statistics fields are populated using exchange data from Trade statistics messages.
 * While {@link #getPrice() price} of {@link Trade} is expected to be identical to either
 * {@link #getLastPriceForAll() lastPriceForAll} or {@link #getLastSignificantPrice() lastSignificantPrice},
 * we added both fields to separate values from the exchange from our business logic of Trade event.
 *
 * <h3>Implementation details</h3>
 * <p>
 * This event is implemented on top of QDS record {@code NuamTrade}.
 */
@Experimental
@XmlRootElement(name = "NuamTrade")
@XmlType(propOrder = {
    "tradeStatTime", "lastSignificantPrice", "lastPriceForAll", "numberOfTrades", "VWAP"
})
public class NuamTrade extends Trade {
    private static final long serialVersionUID = 0;

    private long tradeStatTime;
    private double lastSignificantPrice = Double.NaN;
    private double lastPriceForAll = Double.NaN;
    private int numberOfTrades;
    private double vwap = Double.NaN;

    /**
     * Creates new Nuam Trade event with default values.
     */
    public NuamTrade() {}

    /**
     * Creates new Nuam trade event with the specified event symbol.
     *
     * @param eventSymbol event symbol.
     */
    public NuamTrade(String eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns time when trade statistics information was updated. It can be used to distinguish
     * between last trade and trade statistics updates.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @return time when trade statistics information was updated.
     */
    @XmlJavaTypeAdapter(type = long.class, value = XmlTimeAdapter.class)
    @XmlSchemaType(name = "dateTime")
    public long getTradeStatTime() {
        return tradeStatTime;
    }

    /**
     * Changes time when trade statistics information was updated.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @param tradeStatTime time when trade statistics information was updated.
     */
    public void setTradeStatTime(long tradeStatTime) {
        this.tradeStatTime = tradeStatTime;
    }

    /**
     * Returns price of the last trade reported as significant by the exchange.
     * For example, a trade cannot be considered significant if its price below 1000 USD.
     *
     * @return price of the last trade reported as significant by the exchange.
     */
    public double getLastSignificantPrice() {
        return lastSignificantPrice;
    }

    /**
     * Changes price of the last trade reported as significant by the exchange.
     *
     * @param lastSignificantPrice price of the last trade reported as significant by the exchange.
     */
    public void setLastSignificantPrice(double lastSignificantPrice) {
        this.lastSignificantPrice = lastSignificantPrice;
    }

    /**
     * Returns price of the last trade (for all deals) reported by the exchange.
     * Unlike {@link #getLastSignificantPrice() lastSignificantPrice}, it represents the last trade price without
     * any conditions.
     *
     * @return price of the last trade reported as significant by the exchange
     */
    public double getLastPriceForAll() {
        return lastPriceForAll;
    }

    /**
     * Changes price of the last trade (for all deals) reported by the exchange.
     *
     * @param lastPriceForAll price of the last trade (for all deals) reported by the exchange.
     */
    public void setLastPriceForAll(double lastPriceForAll) {
        this.lastPriceForAll = lastPriceForAll;
    }

    /**
     * Returns number of trades reported by the exchange.
     *
     * @return number of trades reported by the exchange.
     */
    public int getNumberOfTrades() {
        return numberOfTrades;
    }

    /**
     * Sets number of trades reported by the exchange.
     *
     * @param numberOfTrades number of trades reported by the exchange.
     */
    public void setNumberOfTrades(int numberOfTrades) {
        this.numberOfTrades = numberOfTrades;
    }

    /**
     * Returns volume-weighted average price (VWAP) reported by the exchange.
     *
     * @return volume-weighted average price (VWAP) reported by the exchange.
     */
    @XmlElement(name = "vwap") // all lower-case
    public double getVWAP() {
        return vwap;
    }

    /**
     * Sets volume-weighted average price (VWAP) reported by the exchange.
     *
     * @param vwap volume-weighted average price (VWAP) reported by the exchange.
     */
    public void setVWAP(double vwap) {
        this.vwap = vwap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", tradeStatTime=").append(TimeFormat.DEFAULT.withMillis().format(getTradeStatTime()))
            .append(", lastSignificantPrice=").append(lastSignificantPrice)
            .append(", lastPriceForAll=").append(lastPriceForAll)
            .append(", numberOfTrades=").append(numberOfTrades)
            .append(", vwap=").append(vwap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getFlags() {
        return super.getFlags();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setFlags(int flags) {
        super.setFlags(flags);
    }
}
