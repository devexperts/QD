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

/**
 * Attribute of the {@link CandleSymbol}.
 * @param <A> the attribute class.
 */
public interface CandleSymbolAttribute<A> {
    /**
     * Returns candle event symbol string with this attribute set.
     * @param symbol original candle event symbol.
     * @return candle event symbol string with this attribute set.
     */
    public String changeAttributeForSymbol(String symbol);

    /**
     * Internal method that initializes attribute in the candle symbol.
     * @param candleSymbol candle symbol.
     * @throws IllegalStateException if used outside of internal initialization logic.
     */
    public void checkInAttributeImpl(CandleSymbol candleSymbol);
}
