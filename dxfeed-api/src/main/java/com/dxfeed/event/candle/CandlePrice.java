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
import com.dxfeed.event.market.PriceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Price type attribute of {@link CandleSymbol} defines price that is used to build the candles.
 *
 * <h3>Implementation details</h3>
 *
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getAttributeStringByKey(String, String) MarketEventSymbols.getAttributeStringByKey},
 * {@link MarketEventSymbols#changeAttributeStringByKey(String, String, String) changeAttributeStringByKey}, and
 * {@link MarketEventSymbols#removeAttributeStringByKey(String, String) removeAttributeStringByKey} methods.
 * The key to use with these methods is available via
 * {@link #ATTRIBUTE_KEY} constant.
 * The value that this key shall be set to is equal to
 * the corresponding {@link #toString() CandlePrice.toString()}
 */
public enum CandlePrice implements CandleSymbolAttribute<CandlePrice> {
    /**
     * Last trading price.
     */
    LAST("last"),

    /**
     * Quote bid price.
     */
    BID("bid"),

    /**
     * Quote ask price.
     */
    ASK("ask"),

    /**
     * Market price defined as average between quote bid and ask prices.
     */
    MARK("mark"),

    /**
     * Official settlement price that is defined by exchange or last trading price otherwise.
     * It updates based on all {@link PriceType PriceType} values:
     * {@link PriceType#INDICATIVE}, {@link PriceType#PRELIMINARY}, and {@link PriceType#FINAL}.
     */
    SETTLEMENT("s");

    /**
     * Default price type is {@link #LAST}.
     */
    public static final CandlePrice DEFAULT = LAST;

    /**
     * The attribute key that is used to store the value of {@code CandlePrice} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is "price".
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandlePrice.toString()}
     */
    public static final String ATTRIBUTE_KEY = "price";

    private final String string;

    CandlePrice(String string) {
        this.string = string;
    }

    /**
     * Returns candle event symbol string with this candle price type set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this candle price type set.
     */
    public String changeAttributeForSymbol(String symbol) {
        return this == DEFAULT ?
            MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY) :
            MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, toString());
    }

    /**
     * Internal method that initializes attribute in the candle symbol.
     * @param candleSymbol candle symbol.
     * @throws IllegalStateException if used outside of internal initialization logic.
     */
    public void checkInAttributeImpl(CandleSymbol candleSymbol) {
        if (candleSymbol.price != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.price = this;
    }

    /**
     * Returns string representation of this candle price type.
     * The string representation of candle price type is a lower case string
     * that corresponds to its {@link #name() name}. For example,
     * {@link #LAST} is represented as "last".
     * @return string representation of this candle price type.
     */
    @Override
    public String toString() {
        return string;
    }

    private static final Map<String, CandlePrice> BY_STRING = new HashMap<String, CandlePrice>();

    static {
        for (CandlePrice price : values())
            BY_STRING.put(price.toString(), price);
    }

    /**
     * Parses string representation of candle price type into object.
     * Any string that was returned by {@link #toString()} can be parsed
     * and case is ignored for parsing.
     * @param s string representation of candle price type.
     * @return candle price type.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandlePrice parse(String s) {
        int n = s.length();
        if (n == 0)
            throw new IllegalArgumentException("Missing candle price");
        CandlePrice result = BY_STRING.get(s);
        // fast path to reverse toString result
        if (result != null)
            return result;
        // slow path for everything else
        for (CandlePrice price : values()) {
            String ps = price.toString();
            if (ps.length() >= n && ps.substring(0, n).equalsIgnoreCase(s))
                return price;
        }
        throw new IllegalArgumentException("Unknown candle price: " + s);
    }

    /**
     * Returns candle price type of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have candle price attribute.
     * @param symbol candle symbol string.
     * @return candle price of the given candle symbol string.
     */
    public static CandlePrice getAttributeForSymbol(String symbol) {
        String string = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return string == null ? DEFAULT : parse(string);
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle price type attribute.
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the the candle price type attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        try {
            CandlePrice other = parse(a);
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
