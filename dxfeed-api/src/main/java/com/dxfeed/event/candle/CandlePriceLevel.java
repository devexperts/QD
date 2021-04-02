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

import com.dxfeed.event.market.MarketEventSymbols;

/**
 * Candle price level attribute of {@link CandleSymbol} defines how candles shall be aggregated in respect to
 * price interval. The negative or infinite values of price interval are treated as exceptional.
 * <ul>
 * <li>Price interval may be equal to zero. It means every unique price creates a particular candle
 * to aggregate all events with this price for the chosen {@link CandlePeriod}.
 * <li>Non-zero price level creates sequence of intervals starting from 0:
 * ...,[-pl;0),[0;pl),[pl;2*pl),...,[n*pl,n*pl+pl). Events aggregated by chosen {@link CandlePeriod} and price intervals.
 * </ul>
 *
 * <h3>Implementation details</h3>
 * <p>
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getAttributeStringByKey(String, String) MarketEventSymbols.getAttributeStringByKey},
 * {@link MarketEventSymbols#changeAttributeStringByKey(String, String, String) changeAttributeStringByKey}, and
 * {@link MarketEventSymbols#removeAttributeStringByKey(String, String) removeAttributeStringByKey} methods.
 * The key to use with these methods is available via
 * {@link #ATTRIBUTE_KEY} constant.
 * The value that this key shall be set to is equal to
 * the corresponding {@link #toString() CandlePriceLevel.toString()}
 */
public class CandlePriceLevel implements CandleSymbolAttribute<CandlePriceLevel> {

    /**
     * Default price level corresponds to {@code Double.NaN}
     */
    public static final CandlePriceLevel DEFAULT = new CandlePriceLevel(Double.NaN);

    /**
     * The attribute key that is used to store the value of {@code CandlePriceLevel} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is "pl".
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandlePriceLevel.toString()}
     */
    public static final String ATTRIBUTE_KEY = "pl";

    private final double value;

    private CandlePriceLevel(double value) {
        if (Double.isInfinite(value) || Double.compare(value, +0.0) < 0) // reject -0.0
            throw new IllegalArgumentException("Incorrect candle price level: " + value);
        this.value = value;
    }

    /**
     * Returns price level value. For example, the value of {@code 1} represents [0;1), [1;2) and so on intervals
     * to build candles.
     *
     * @return price level value.
     */
    public double getValue() {
        return value;
    }

    /**
     * Indicates whether this price level is the same as another one.
     * The same price level has the same {@link #getValue() value}.
     *
     * @return {@code true} if this price level is the same as another one.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CandlePriceLevel))
            return false;
        CandlePriceLevel that = (CandlePriceLevel) o;
        return Double.compare(value, that.value) == 0;
    }

    /**
     * Returns hash code of this price level.
     *
     * @return hash code of this price level.
     */
    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    /**
     * Returns string representation of this price level.
     * The string representation is composed of value.
     * This string representation can be converted back into object
     * with {@link #parse(String)} method.
     *
     * @return string representation of this price level.
     */
    @Override
    public String toString() {
        return value == (long) value ? Long.toString((long) value) : Double.toString(value);
    }

    /**
     * Returns candle event symbol string with this candle price level set.
     *
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this candle price level set.
     */
    public String changeAttributeForSymbol(String symbol) {
        return this == DEFAULT ?
            MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY) :
            MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, toString());
    }

    /**
     * Internal method that initializes attribute in the candle symbol.
     *
     * @param candleSymbol candle symbol.
     * @throws IllegalStateException if used outside of internal initialization logic.
     */
    public void checkInAttributeImpl(CandleSymbol candleSymbol) {
        if (candleSymbol.priceLevel != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.priceLevel = this;
    }

    /**
     * Parses string representation of candle price level into object.
     * Any string that was returned by {@link #toString()} can be parsed
     * and case is ignored for parsing.
     *
     * @param s string representation of candle price level.
     * @return candle price level.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandlePriceLevel parse(String s) {
        double value = Double.parseDouble(s);
        return valueOf(value);
    }

    /**
     * Returns candle price level with the given value.
     *
     * @param value candle price level value.
     * @return candle price level with the given value and type.
     */
    public static CandlePriceLevel valueOf(double value) {
        return Double.isNaN(value) ? DEFAULT : new CandlePriceLevel(value);
    }

    /**
     * Returns candle price level of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have candle price level attribute.
     *
     * @param symbol candle symbol string.
     * @return candle price level of the given candle symbol string.
     */
    public static CandlePriceLevel getAttributeForSymbol(String symbol) {
        String string = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return string == null ? DEFAULT : parse(string);
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle price level attribute.
     *
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the the candle price level attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        try {
            CandlePriceLevel other = parse(a);
            if (other == DEFAULT)
                MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY);
            if (!a.equals(other.toString()))
                return MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, other.toString());
            return symbol;
        } catch (IllegalArgumentException e) {
            return symbol;
        }
    }
}
