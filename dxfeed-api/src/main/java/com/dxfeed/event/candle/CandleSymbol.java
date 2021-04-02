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

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.MarketEventSymbols;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Symbol that should be used with {@link DXFeedSubscription} class
 * to subscribe for {@link Candle} events. {@code DXFeedSubscription} also accepts a string
 * representation of the candle symbol for subscription.
 *
 * <h3>String representation</h3>
 *
 * The string representation of the candle symbol consist of a {@link #getBaseSymbol() baseSymbol} followed by
 * an optional '&amp;' with an {@link #getExchange() exchange} code letter and followed by
 * an optional list of comma-separated key=value pairs in curly braces:
 *
 * <p>{@code <baseSymbol> [ '&' <exchange> ] [ '{' <key1>=<value1> [ ',' <key2>=<value2> ] ... '}' ]}
 *
 * <p>Properties of the candle symbol correspond to the keys in the string representation in the following way:
 *
 * <ul>
 * <li>Empty key corresponds to {@link #getPeriod() period} &mdash; aggregation period of this symbol.
 *     The period value is composed of an optional
 *     {@link CandlePeriod#getValue() value} which defaults to 1 when not specified, followed by
 *     a {@link CandlePeriod#getType() type} string which is defined by one of the
 *     {@link CandleType} enum values and can be abbreviated to first letters. For example, a daily candle of "IBM" base
 *     symbol can be specified as "IBM{=d}" and 15 minute candle on it as "IBM{=15m}". The shortest
 *     possible abbreviation for {@link CandleType#MONTH CandleType.MONTH} is "mo", so the monthly
 *     candle can be specified as "IBM{=mo}". When period is not specified, then the
 *     {@link CandlePeriod#TICK TICK} aggregation period is assumed as default. Note, that tick aggregation may
 *     not be available on the demo system which is limited to a subset of symbols and aggregation periods.
 * <li>"price" key corresponds to {@link #getPrice() price} &mdash; price type attribute of this symbol.
 *     The {@link CandlePrice} enum defines possible values with {@link CandlePrice#LAST LAST} being default.
 *     For legacy backwards-compatibility purposes, most of the price values cannot be abbreviated, so a one-minute candle
 *     of "EUR/USD" bid price shall be specified with "EUR/USD{=m,price=bid}" candle symbol string. However,
 *     the {@link CandlePrice#SETTLEMENT SETTLEMENT} can be abbreviated to "s", so a daily candle on
 *     "/ES" futures settlement prices can be specified with "/ES{=d,price=s}" string.
 * <li>"tho" key with a value of "true" corresponds to {@link #getSession() session} set to {@link CandleSession#REGULAR}
 *     which limits the candle to trading hours only, so a 133 tick candles on "GOOG" base symbol collected over
 *     trading hours only can be specified with "GOOG{=133t,tho=true}" string. Note, that the default daily candles for
 *     US equities are special for historical reasons and correspond to the way US equity exchange report their
 *     daily summary data. The volume the US equity default daily candle corresponds to the total daily traded volume,
 *     while open, high, low, and close correspond to the regular trading hours only.
 * <li>"a" key corresponds to {@link #getAlignment() alignment} &mdash; alignment attribute of this symbol.
 *     The {@link CandleAlignment} enum defines possible values with {@link CandleAlignment#MIDNIGHT MIDNIGHT} being default.
 *     The alignment values can be abbreviated to the first letter. So, a 1 hour candle on a symbol "AAPL" that starts
 *     at the regular trading session at 9:30 am ET can be specified with "AAPL{=h,a=s,tho=true}". Contrast that
 *     to the "AAPL{=h,tho=true}" candle that is aligned at midnight and thus starts at 9:00 am.
 * <li>"pl" key corresponds to {@link #getPriceLevel() price level} &mdash; price level attribute of this symbol.
 *     The {@link CandlePriceLevel} defines additional axis to split candles within particular price corridor in
 *     addition to {@link CandlePeriod} attribute with the default value {@code Double.NaN}. So a one-minute candles
 *     of "AAPL" with price level 0.1 shall be specified with "AAPL{=m,pl=0.1}".
 * </ul>
 *
 * Keys in the candle symbol are case-sensitive, while values are not. The {@link #valueOf(String)} method parses
 * any valid string representation into a candle symbol object.
 * The result of the candle symbol
 * {@link #toString()} method is always normalized: keys are ordered lexicographically, values are in lower-case
 * and are abbreviated to their shortest possible form.
 */
public class CandleSymbol implements Serializable {
    private static final long serialVersionUID = 0;

    private final String symbol;

    private transient String baseSymbol;

    transient CandleExchange exchange;
    transient CandlePrice price;
    transient CandleSession session;
    transient CandlePeriod period;
    transient CandleAlignment alignment;
    transient CandlePriceLevel priceLevel;

    private CandleSymbol(String symbol) {
        this.symbol = normalize(symbol);
        initTransientFields();
    }

    private CandleSymbol(String symbol, CandleSymbolAttribute<?> attribute) {
        this.symbol = normalize(changeAttribute(symbol, attribute));
        attribute.checkInAttributeImpl(this);
        initTransientFields();
    }

    private CandleSymbol(String symbol, CandleSymbolAttribute<?> attribute, CandleSymbolAttribute<?>... attributes) {
        this.symbol = normalize(changeAttributes(symbol, attribute, attributes));
        attribute.checkInAttributeImpl(this);
        for (CandleSymbolAttribute<?> a : attributes)
            a.checkInAttributeImpl(this);
        initTransientFields();
    }

    /**
     * Returns base market symbol without attributes.
     * @return base market symbol without attributes.
     */
    public String getBaseSymbol() {
        return baseSymbol;
    }

    /**
     * Returns exchange attribute of this symbol.
     * @return exchange attribute of this symbol.
     */
    public CandleExchange getExchange() {
        return exchange;
    }

    /**
     * Returns price type attribute of this symbol.
     * @return price type attribute of this symbol.
     */
    public CandlePrice getPrice() {
        return price;
    }

    /**
     * Returns session attribute of this symbol.
     * @return session attribute of this symbol.
     */
    public CandleSession getSession() {
        return session;
    }

    /**
     * Returns aggregation period of this symbol.
     * @return aggregation period of this symbol.
     */
    public CandlePeriod getPeriod() {
        return period;
    }

    /**
     * Returns alignment attribute of this symbol.
     * @return alignment attribute of this symbol.
     */
    public CandleAlignment getAlignment() {
        return alignment;
    }

    /**
     * Returns price level attribute of this symbol.
     * @return price level attribute of this symbol.
     */
    public CandlePriceLevel getPriceLevel() {
        return priceLevel;
    }

    /**
     * Returns string representation of this symbol.
     * The string representation can be transformed back into symbol object
     * using {@link #valueOf(String) valueOf(String)} method.
     *
     * @return string representation of this symbol.
     */
    @Override
    public String toString() {
        return symbol;
    }

    /**
     * Indicates whether this symbol is the same as another one.
     * @return {@code true} if this symbol is the same as another one.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CandleSymbol && symbol.equals(((CandleSymbol) o).symbol);
    }

    /**
     * Returns hash code of this symbol.
     * @return hash code of this symbol.
     */
    @Override
    public int hashCode() {
        return symbol.hashCode();
    }

    /**
     * Converts the given string symbol into the candle symbol object.
     * @param symbol the string symbol.
     * @return the candle symbol object.
     * @throws IllegalArgumentException if the string does not represent a valid symbol.
     */
    public static CandleSymbol valueOf(String symbol) {
        return new CandleSymbol(symbol);
    }

    /**
     * Converts the given string symbol into the candle symbol object with the specified attribute set.
     * @param symbol the string symbol.
     * @param attribute the attribute to set.
     * @return the candle symbol object.
     * @throws IllegalArgumentException if the string does not represent a valid symbol.
     */
    public static CandleSymbol valueOf(String symbol, CandleSymbolAttribute<?> attribute) {
        return new CandleSymbol(symbol, attribute);
    }

    /**
     * Converts the given string symbol into the candle symbol object with the specified attributes set.
     * @param symbol the string symbol.
     * @param attribute the attribute to set.
     * @param attributes more attributes to set.
     * @return the candle symbol object.
     * @throws IllegalArgumentException if the string does not represent a valid symbol.
     */
    public static CandleSymbol valueOf(String symbol, CandleSymbolAttribute<?> attribute, CandleSymbolAttribute<?>... attributes) {
        return new CandleSymbol(symbol, attribute, attributes);
    }

    //----------------------- private implementation details -----------------------

    private static String changeAttributes(String symbol, CandleSymbolAttribute<?> attribute, CandleSymbolAttribute<?>... attributes) {
        symbol = changeAttribute(symbol, attribute);
        for (CandleSymbolAttribute<?> a : attributes)
            symbol = changeAttribute(symbol, a);
        return symbol;
    }

    private static String changeAttribute(String symbol, CandleSymbolAttribute<?> attribute) {
        return attribute.changeAttributeForSymbol(symbol);
    }

    private static String normalize(String symbol) {
        symbol = CandlePrice.normalizeAttributeForSymbol(symbol);
        symbol = CandleSession.normalizeAttributeForSymbol(symbol);
        symbol = CandlePeriod.normalizeAttributeForSymbol(symbol);
        symbol = CandleAlignment.normalizeAttributeForSymbol(symbol);
        symbol = CandlePriceLevel.normalizeAttributeForSymbol(symbol);
        return symbol;
    }

    private void initTransientFields() {
        baseSymbol = MarketEventSymbols.getBaseSymbol(symbol);
        if (exchange == null)
            exchange = CandleExchange.getAttributeForSymbol(symbol);
        if (price == null)
            price = CandlePrice.getAttributeForSymbol(symbol);
        if (session == null)
            session = CandleSession.getAttributeForSymbol(symbol);
        if (period == null)
            period = CandlePeriod.getAttributeForSymbol(symbol);
        if (alignment == null)
            alignment = CandleAlignment.getAttributeForSymbol(symbol);
        if (priceLevel == null)
            priceLevel = CandlePriceLevel.getAttributeForSymbol(symbol);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientFields();
    }
}
