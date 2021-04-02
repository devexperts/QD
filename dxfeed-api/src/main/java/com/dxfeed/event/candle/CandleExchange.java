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

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Exchange attribute of {@link CandleSymbol} defines exchange identifier where data is
 * taken from to build the candles.
 *
 * <h3>Implementation details</h3>
 *
 * This attribute is encoded in a symbol string with
 * {@link MarketEventSymbols#getExchangeCode(String) MarketEventSymbols.getExchangeCode} and
 * {@link MarketEventSymbols#changeExchangeCode(String, char) changeExchangeCode} methods.
 */
public class CandleExchange implements CandleSymbolAttribute<CandleExchange>, Serializable {

    private static final long serialVersionUID = 0;

    /**
     * Composite exchange where data is taken from all exchanges.
     */
    public static final CandleExchange COMPOSITE = new CandleExchange('\0');

    /**
     * Default exchange is {@link #COMPOSITE}.
     */
    public static final CandleExchange DEFAULT = COMPOSITE;

    private final char exchangeCode;

    private CandleExchange(char exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    /**
     * Returns exchange code. It is {@code '\0'} for {@link #COMPOSITE} exchange.
     * @return exchange code.
     */
    public char getExchangeCode() {
        return exchangeCode;
    }


    /**
     * Returns string representation of this exchange.
     * It is the string {@code "COMPOSITE"} for {@link #COMPOSITE} exchange or
     * exchange character otherwise.
     * @return string representation of this exchange.
     */
    @Override
    public String toString() {
        return exchangeCode == '\0' ? "COMPOSITE" : "" + exchangeCode;
    }

    /**
     * Indicates whether this exchange attribute is the same as another one.
     * @return {@code true} if this exchange attribute is the same as another one.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CandleExchange && exchangeCode == ((CandleExchange) o).exchangeCode;
    }

    /**
     * Returns hash code of this exchange attribute.
     * @return hash code of this exchange attribute.
     */
    @Override
    public int hashCode() {
        return (int) exchangeCode;
    }

    /**
     * Returns candle event symbol string with this exchange set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this exchange set.
     */
    public String changeAttributeForSymbol(String symbol) {
        return MarketEventSymbols.changeExchangeCode(symbol, exchangeCode);
    }

    /**
     * Internal method that initializes attribute in the candle symbol.
     * @param candleSymbol candle symbol.
     * @throws IllegalStateException if used outside of internal initialization logic.
     */
    public void checkInAttributeImpl(CandleSymbol candleSymbol) {
        if (candleSymbol.exchange != null)
            throw new IllegalStateException("Already initialized");
        candleSymbol.exchange = this;
    }

    /**
     * Returns exchange attribute object that corresponds to the specified exchange code character.
     * @param exchangeCode exchange code character.
     * @return exchange attribute object.
     */
    public static CandleExchange valueOf(char exchangeCode) {
        return exchangeCode == '\0' ? COMPOSITE : new CandleExchange(exchangeCode);
    }

    /**
     * Returns exchange attribute object of the given candle symbol string.
     * The result is {@link #DEFAULT} if the symbol does not have exchange attribute.
     * @param symbol candle symbol string.
     * @return exchange attribute object of the given candle symbol string.
     */
    public static CandleExchange getAttributeForSymbol(String symbol) {
        return valueOf(MarketEventSymbols.getExchangeCode(symbol));
    }

    protected Object readResolve() throws ObjectStreamException {
        if (exchangeCode == '\0')
            return COMPOSITE;
        return this;
    }
}
