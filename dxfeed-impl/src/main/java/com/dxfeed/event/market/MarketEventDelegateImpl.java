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
package com.dxfeed.event.market;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.dxfeed.api.impl.EventDelegate;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.api.impl.EventDelegateSet;
import com.dxfeed.event.market.impl.MarketEventMapping;

import java.util.EnumSet;

public abstract class MarketEventDelegateImpl<T extends MarketEvent> extends EventDelegate<T> {

    protected MarketEventDelegateImpl(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        super(record, contract, flags);
    }

    @Override
    public abstract MarketEventMapping getMapping();

    @Override
    public EventDelegateSet<T, ? extends EventDelegate<T>> createDelegateSet() {
        return new MarketEventDelegateSet<>(getEventType());
    }

    /**
     * Exchange code of this delegate (record exchange code by default).
     * It is overriden in OrderByQuoteXXXDelegates.
     */
    public char getExchangeCode() {
        return getMapping().getRecordExchange();
    }

    @Override
    // Works on string symbols, too, so DelegateSet.convertSymbol call is optional
    public final String getQDSymbolByEventSymbol(Object symbol) {
        String s = symbol.toString();
        return getExchangeCode() == MarketEventMapping.COMPOSITE ? s : MarketEventSymbols.changeExchangeCode(s, MarketEventMapping.COMPOSITE);
    }

    @Override
    public final String getEventSymbolByQDSymbol(String qdSymbol) {
        return getExchangeCode() == MarketEventMapping.COMPOSITE ? qdSymbol :
            MarketEventSymbols.changeExchangeCode(qdSymbol, getExchangeCode());
    }
}
