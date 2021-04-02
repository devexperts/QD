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
import com.dxfeed.schedule.SessionFilter;

/**
 * Session attribute of {@link CandleSymbol} defines trading that is used to build the candles.
 *
 * <h3>Implementation details</h3>
 *
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getAttributeStringByKey(String, String) MarketEventSymbols.getAttributeStringByKey},
 * {@link MarketEventSymbols#changeAttributeStringByKey(String, String, String) changeAttributeStringByKey}, and
 * {@link MarketEventSymbols#removeAttributeStringByKey(String, String) removeAttributeStringByKey} methods.
 *
 * <p> {@link #ANY} session is a default.
 * The key to use with these methods is available via
 * {@link #ATTRIBUTE_KEY} constant.
 * The value that this key shall be set to is equal to
 * the corresponding {@link #toString() CandleSession.toString()}
 */
public enum CandleSession implements CandleSymbolAttribute<CandleSession> {
    /**
     * All trading sessions are used to build candles.
     */
    ANY(SessionFilter.ANY, "false"),

    /**
     * Only regular trading session data is used to build candles.
     */
    REGULAR(SessionFilter.REGULAR, "true");

    /**
     * Default trading session is {@link #ANY}.
     */
    public static final CandleSession DEFAULT = ANY;

    /**
     * The attribute key that is used to store the value of {@code CandleSession} in
     * a symbol string using methods of {@link MarketEventSymbols} class.
     * The value of this constant is "tho", which is an abbreviation for "trading hours only".
     * The value that this key shall be set to is equal to
     * the corresponding {@link #toString() CandleSession.toString()}
     */
    public static final String ATTRIBUTE_KEY = "tho";

    private final SessionFilter sessionFilter;
    private final String string;

    CandleSession(SessionFilter sessionFilter, String string) {
        this.sessionFilter = sessionFilter;
        this.string = string;
    }

    /**
     * Returns session filter that corresponds to this session attribute.
     * @return session filter that corresponds to this session attribute.
     */
    public SessionFilter getSessionFilter() {
        return sessionFilter;
    }

    /**
     * Returns candle event symbol string with this session attribute set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this session attribute set.
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
        if (candleSymbol.session != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.session = this;
    }

    /**
     * Returns string representation of this candle session attribute.
     * The string representation of candle session attribute is a lower case string
     * that corresponds to its {@link #name() name}. For example,
     * {@link #ANY} is represented as "any".
     * @return string representation of this candle session attribute.
     */
    @Override
    public String toString() {
        return string;
    }

    /**
     * Parses string representation of candle session attribute into object.
     * Any string that was returned by {@link #toString()} can be parsed
     * and case is ignored for parsing.
     * @param s string representation of candle candle session attribute.
     * @return candle session attribute.
     * @throws IllegalArgumentException if the string representation is invalid.
     */
    public static CandleSession parse(String s) {
        int n = s.length();
        if (n == 0)
            throw new IllegalArgumentException("Missing candle session");
        for (CandleSession session : values()) {
            String ss = session.toString();
            if (ss.length() >= n && ss.substring(0, n).equalsIgnoreCase(s))
                return session;
        }
        throw new IllegalArgumentException("Unknown candle session: " + s);
    }

    /**
     * Returns candle session attribute of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have candle session attribute.
     * @param symbol candle symbol string.
     * @return candle session attribute of the given candle symbol string.
     */
    public static CandleSession getAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        return a != null && Boolean.parseBoolean(a) ? REGULAR : DEFAULT;
    }

    /**
     * Returns candle symbol string with the normalized representation of the candle session attribute.
     * @param symbol candle symbol string.
     * @return candle symbol string with the normalized representation of the the candle session attribute.
     */
    public static String normalizeAttributeForSymbol(String symbol) {
        String a = MarketEventSymbols.getAttributeStringByKey(symbol, ATTRIBUTE_KEY);
        if (a == null)
            return symbol;
        try {
            boolean b = Boolean.parseBoolean(a);
            if (!b)
                MarketEventSymbols.removeAttributeStringByKey(symbol, ATTRIBUTE_KEY);
            if (b && !a.equals(REGULAR.toString()))
                return MarketEventSymbols.changeAttributeStringByKey(symbol, ATTRIBUTE_KEY, REGULAR.toString());
            return symbol;
        } catch (IllegalArgumentException e) {
            return symbol;
        }
    }
}


