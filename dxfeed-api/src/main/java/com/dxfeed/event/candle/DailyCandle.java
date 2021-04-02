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

import javax.xml.bind.annotation.XmlRootElement;

/**
 * In previous versions this class has represented candle with an additional properties that are collected for
 * optionable indices, stocks, and futures on a daily basis, for example implied volatility or open interest.
 * All additional properties were moved to {@link Candle} class.
 *
 * @deprecated use {@link Candle} instead.
 */
@Deprecated
@XmlRootElement(name = "DailyCandle")
public class DailyCandle extends Candle {
    private static final long serialVersionUID = 0;

    /**
     * Creates new daily candle with default values.
     */
    public DailyCandle() {}

    /**
     * Creates new daily candle with the specified candle event symbol.
     * @param eventSymbol candle event symbol.
     */
    public DailyCandle(CandleSymbol eventSymbol) {
        super(eventSymbol);
    }

    /**
     * Returns string representation of this daily candle.
     * @return string representation of this daily candle.
     */
    @Override
    public String toString() {
        return "DailyCandle{" + baseFieldsToString() + "}";
    }
}
