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

import java.util.HashMap;
import java.util.Map;

/**
 * Candle alignment attribute of {@link CandleSymbol} defines how candle are aligned with respect to time.
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
 * the corresponding {@link #toString() CandleAlignment.toString()}
 */
public enum CandleAlignment implements CandleSymbolAttribute<CandleAlignment> {
    /**
     * Align candles on midnight.
     */
    MIDNIGHT("m"),

    /**
     * Align candles on trading sessions.
     */
    SESSION("s");

    /**
     * Default alignment is {@link #MIDNIGHT}.
     */
    public static final CandleAlignment DEFAULT = MIDNIGHT;

    /**
     * The attribute key that is used to store the value of {@code CandleAlignment} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is "a".
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandleAlignment.toString()}
     */
    public static final String ATTRIBUTE_KEY = "a";

    private final String string;

    CandleAlignment(String string) {
        this.string = string;
    }

    /**
     * Returns candle event symbol string with this candle alignment set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this candle alignment set.
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
        if (candleSymbol.alignment != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.alignment = this;
    }

    /**
     * Returns string representation of this candle alignment.
     * The string representation of candle alignment "m" for {@link #MIDNIGHT}
     * and "s" for {@link #SESSION}.
     * @return string representation of this candle alignment.
     */
    @Override
    public String toString() {
        return string;
    }

    private static final Map<String, CandleAlignment> BY_STRING = new HashMap<String, CandleAlignment>();

    static {
        for (CandleAlignment align : values())
            BY_STRING.put(align.toString(), align);
    }

    /**
     * Parses string representation of candle alignment into object.
     * Any string that was returned by {@link #toString()} can be parsed
     * and case is ignored for parsing.
     * @param s string representation of candle alignment.
     * @return candle alignment.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandleAlignment parse(String s) {
        CandleAlignment result = BY_STRING.get(s);
        // fast path to reverse toString result
        if (result != null)
            return result;
        // slow path for different case
        for (CandleAlignment align : values()) {
            if (align.toString().equalsIgnoreCase(s))
                return align;
        }
        throw new IllegalArgumentException("Unknown candle alignment: " + s);
    }

    /**
     * Returns candle alignment of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have candle alignment attribute.
     * @param symbol candle symbol string.
     * @return candle alignment of the given candle symbol string.
     */
    public static CandleAlignment getAttributeForSymbol(String symbol) {
        String string = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return string == null ? DEFAULT : parse(string);
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle alignment attribute.
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the the candle alignment attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        try {
            CandleAlignment other = parse(a);
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
