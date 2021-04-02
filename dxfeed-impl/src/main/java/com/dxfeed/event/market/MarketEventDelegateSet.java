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

import com.dxfeed.api.impl.EventDelegateSet;

import java.util.ArrayList;
import java.util.List;

class MarketEventDelegateSet<T extends MarketEvent, D extends MarketEventDelegateImpl<T>> extends EventDelegateSet<T, D> {
    private final List<List<D>> timeSeriesDelegatesByExchangeCode = newListOfLists();
    private final List<List<D>> subDelegatesByExchangeCode = newListOfLists();
    private final List<List<D>> pubDelegatesByExchangeCode = newListOfLists();
    private final List<D> lastingDelegateByExchangeCode = new ArrayList<>();

    MarketEventDelegateSet(Class<T> eventType) {
        super(eventType);
    }

    @Override
    public void add(D delegate) {
        if (delegate.isSub()) {
            if (delegate.isTimeSeries())
                addToListOfLists(timeSeriesDelegatesByExchangeCode, delegate);
            else
                addToListOfLists(subDelegatesByExchangeCode, delegate);
            if (delegate.isLastingEvent())
                addToList(lastingDelegateByExchangeCode, delegate);
        }
        if (delegate.isPub())
            addToListOfLists(pubDelegatesByExchangeCode, delegate);
        super.add(delegate);
    }

    @Override
    protected List<D> getRegularSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        return getFromList(subDelegatesByExchangeCode, (String) symbol);
    }

    @Override
    public List<D> getTimeSeriesDelegatesByEventSymbol(Object symbol) {
        return getFromList(timeSeriesDelegatesByExchangeCode, (String) symbol);
    }

    @Override
    public D getLastingDelegateByEventSymbol(Object symbol) {
        return getFromList(lastingDelegateByExchangeCode, (String) symbol);
    }

    @Override
    public List<D> getPubDelegatesByEvent(T event) {
        return getFromList(pubDelegatesByExchangeCode, event.getEventSymbol());
    }

    // ---- private static helpers ----

    private static <D extends MarketEventDelegateImpl<?>> List<List<D>> newListOfLists() {
        // make sure composite (zero) item is always initialized to non-null empty list
        List<List<D>> result = new ArrayList<>();
        result.add(new ArrayList<D>(1));
        return result;
    }

    private static <T extends MarketEvent, D extends MarketEventDelegateImpl<T>> void addToListOfLists(List<List<D>> list, D delegate) {
        char exchangeCode = delegate.getExchangeCode();
        while (list.size() <= exchangeCode)
            list.add(null);
        if (list.get(exchangeCode) == null)
            list.set(exchangeCode, new ArrayList<D>(1));
        list.get(exchangeCode).add(delegate);
    }

    private static <T extends MarketEvent, D extends MarketEventDelegateImpl<T>> void addToList(List<D> list, D delegate) {
        char exchangeCode = delegate.getExchangeCode();
        while (list.size() <= exchangeCode)
            list.add(null);
        if (list.set(exchangeCode, delegate) != null)
            throw new IllegalArgumentException("only one delegate of this type is supported " + delegate);
    }

    private static <T> T getFromList(List<T> list, String eventSymbol) {
        char exchangeCode = MarketEventSymbols.getExchangeCode(eventSymbol);
        T result = null;
        if (exchangeCode < list.size())
            result = list.get(exchangeCode);
        if (result == null)
            result = list.get(0); // use composite delegate by default
        return result;
    }
}
