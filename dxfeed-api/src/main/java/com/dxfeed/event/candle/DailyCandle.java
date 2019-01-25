/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Candle with an additional properties that are collected for optionable indices, stocks, and futures on a
 * daily basis. See {@link Candle}.
 *
 * <h3>Properties</h3>
 *
 * {@code DailyCandle} event has the following properties in addition to {@link Candle}
 *
 * <ul>
 * <li>{@link #getOpenInterest() openInterest} - open interest;
 * </ul>
 */
@XmlRootElement(name = "DailyCandle")
public class DailyCandle extends Candle {
    private static final long serialVersionUID = 0;

    private long openInterest;

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
     * Returns open interest.
     * @return open interest.
     */
    public long getOpenInterest() {
        return openInterest;
    }

    /**
     * Changes open interest.
     * @param openInterest open interest.
     */
    public void setOpenInterest(long openInterest) {
        this.openInterest = openInterest;
    }

    /**
     * Returns string representation of this daily candle.
     * @return string representation of this daily candle.
     */
    @Override
    public String toString() {
        return "DailyCandle{" + baseFieldsToString() +
            ", openInterest=" + openInterest +
            "}";
    }
}
