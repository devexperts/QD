/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordMapping;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.OrderSource;

public abstract class MarketEventMapping extends RecordMapping {
    public static final char COMPOSITE = '\0';
    public static final char SOURCE_ID_SEPARATOR = '#';

    protected final char recordExchange;
    protected final OrderSource recordSource;

    protected MarketEventMapping(DataRecord record) {
        super(record);
        recordExchange = MarketEventSymbols.getExchangeCode(record.getName());
        String name = record.getName();
        int i = name.lastIndexOf(SOURCE_ID_SEPARATOR);
        String sourceName = i < 0 ? "" : name.substring(i + 1);
        if (sourceName.length() > 4) // catch usages of builtin sources aka DEFAULT or COMPOSITE_BID
            throw new IllegalArgumentException("Invalid source name: " + sourceName);
        recordSource = i < 0 ? OrderSource.DEFAULT : OrderSource.valueOf(sourceName);
    }

    public final char getRecordExchange() {
        return recordExchange;
    }

    @Override
    public final String getQDSymbolByEventSymbol(Object symbol) {
        String s = symbol.toString();
        return recordExchange == COMPOSITE ? s : MarketEventSymbols.changeExchangeCode(s, COMPOSITE);
    }

    @Override
    public final String getEventSymbolByQDSymbol(String qdSymbol) {
        return recordExchange == COMPOSITE ? qdSymbol : MarketEventSymbols.changeExchangeCode(qdSymbol, recordExchange);
    }

    public final int getRecordSourceId() {
        return getRecordSource().id();
    }

    public final OrderSource getRecordSource() {
        return recordSource;
    }
}
