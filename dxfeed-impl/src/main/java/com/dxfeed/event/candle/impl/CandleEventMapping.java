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
package com.dxfeed.event.candle.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordMapping;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePrice;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.MarketEventSymbols;

public abstract class CandleEventMapping extends RecordMapping {
    private static final String TRADE_HISTORY = "TradeHistory";
    private static final String TRADE = "Trade.";
    private static final String CANDLE = "Candle";

    protected final CandlePeriod recordPeriod;
    protected final CandlePrice recordPrice;

    CandleEventMapping(DataRecord record) {
        super(record);
        String name = record.getName();
        checkValidName(name);
        recordPeriod = getRecordPeriod(name);
        recordPrice = getRecordPrice(name);
    }

    private static void checkValidName(String name) {
        if (name.equals(TRADE_HISTORY))
            return;
        if (name.startsWith(TRADE)) {
            if (MarketEventSymbols.hasAttributes(name))
                throw new IllegalArgumentException("Trade record does not support attributes: " + name);
            return;
        }
        if (name.startsWith(CANDLE)) {
            String bareName = CandlePrice.DEFAULT.changeAttributeForSymbol(CandlePeriod.DEFAULT.changeAttributeForSymbol(name));
            if (!bareName.equals(CANDLE))
                throw new IllegalArgumentException("Candle record supports only price and period attributes: " + name);
            return;
        }
        throw new IllegalArgumentException("Unsupported record name prefix: " + name);
    }

    private static CandlePeriod getRecordPeriod(String name) {
        if (name.equals(TRADE_HISTORY))
            return CandlePeriod.TICK;
        CandlePeriod period = CandlePeriod.getAttributeForSymbol(name);
        if (period != CandlePeriod.DEFAULT)
            return period;
        String baseName = MarketEventSymbols.getBaseSymbol(name);
        int i = baseName.indexOf('.');
        return i < 0 ? null : CandlePeriod.parse(baseName.substring(i + 1));
    }

    private static CandlePrice getRecordPrice(String name) {
        CandlePrice price = CandlePrice.getAttributeForSymbol(name);
        return price == CandlePrice.DEFAULT ? null : price;
    }

    /**
     * Returns period for this record or null if this record does not have a fixed period
     * and the period is specified in the symbol.
     */
    public final CandlePeriod getRecordPeriod() {
        return recordPeriod;
    }

    /**
     * Returns price type for this record or null if this record does not have a fixed price type
     * and the price type is specified in the symbol.
     */
    public final CandlePrice getRecordPrice() {
        return recordPrice;
    }

    @Override
    public final CandleSymbol getEventSymbolByQDSymbol(String qdSymbol) {
        if (recordPeriod == null)
            return CandleSymbol.valueOf(qdSymbol);
        if (recordPrice == null)
            return CandleSymbol.valueOf(qdSymbol, recordPeriod);
        return CandleSymbol.valueOf(qdSymbol, recordPeriod, recordPrice);
    }

    @Override
    public final String getQDSymbolByEventSymbol(Object symbol) {
        String s = symbol.toString();
        if (recordPeriod != null) {
            s = CandlePeriod.DEFAULT.changeAttributeForSymbol(s);
            if (recordPrice != null)
                s = CandlePrice.DEFAULT.changeAttributeForSymbol(s);
        }
        return s;
    }
}
