/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.webservice;

import java.io.Serializable;
import java.util.*;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;

public class EventSymbolMap implements Serializable {
    private static final long serialVersionUID = 0;

    private final Map<CandleSymbol, String> candleSymbols = new HashMap<>();

    public Object resolveEventSymbolMapping(Class<?> eventType, String eventSymbol) {
        if (!Candle.class.isAssignableFrom(eventType))
            return eventSymbol;
        return resolveCandleSymbol(eventSymbol);
    }

    public List<?> resolveEventSymbolMappings(Class<?> eventType, List<String> symbols) {
        if (!Candle.class.isAssignableFrom(eventType))
            return symbols;
        List<Object> result = new ArrayList<>();
        for (String symbol : symbols)
            result.add(resolveCandleSymbol(symbol));
        return result;
    }

    public void cleanupEventSymbolMapping(Class<?> eventType, DXFeedSubscription<?> sub) {
        if (eventType != Candle.class)
            return;
        if (sub == null)
            candleSymbols.clear();
        else
            candleSymbols.keySet().retainAll(sub.getSymbols());
    }

    private Object resolveCandleSymbol(String eventSymbol) {
        CandleSymbol candleSymbol = CandleSymbol.valueOf(eventSymbol);
        candleSymbols.put(candleSymbol, eventSymbol);
        return candleSymbol;
    }

    public String get(Object symbolObject) {
        if (symbolObject instanceof String)
            return (String) symbolObject;
        String s = candleSymbols.get(symbolObject);
        return s == null ? symbolObject.toString() : s;
    }
}
