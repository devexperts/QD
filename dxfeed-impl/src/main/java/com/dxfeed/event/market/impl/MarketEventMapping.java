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
package com.dxfeed.event.market.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordMapping;
import com.dxfeed.event.market.MarketEventSymbols;

public abstract class MarketEventMapping extends RecordMapping {
    public static final char COMPOSITE = '\0';

    protected final char recordExchange;

    protected MarketEventMapping(DataRecord record) {
        super(record);
        recordExchange = MarketEventSymbols.getExchangeCode(record.getName());
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
}
