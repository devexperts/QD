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
package com.dxfeed.webservice;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores original symbols to use them in output events exactly as subscribed.
 *
 * <p>For example "AAA{=m}" and "AAA{=1m}" internally would be resolved into the same {@link CandleSymbol},
 * but we need to return symbols as they were specified in subscription.
 */
public class EventSymbolMap implements Serializable {
    private static final long serialVersionUID = 0;

    private final Map<CandleSymbol, String> candleSymbols = new ConcurrentHashMap<>();

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
