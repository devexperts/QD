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

import com.devexperts.qd.QDContract;
import com.dxfeed.api.impl.EventDelegateSet;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.candle.impl.CandleEventMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CandleEventDelegateSet<T extends Candle, D extends CandleEventDelegateImpl<T>> extends EventDelegateSet<T, D> {
    private static final CandlePeriod MINUTE = CandlePeriod.valueOf(1, CandleType.MINUTE);

    private final Map<Object, List<D>> regularDelegatesByDescriptor = new HashMap<>();
    private final Map<Object, List<D>> timeSeriesDelegatesByDescriptor = new HashMap<>();
    private final Map<Object, List<D>> lastingDelegatesByDescriptor = new HashMap<>();
    private final Map<Object, List<D>> publishableDelegatesByDescriptor = new HashMap<>();

    CandleEventDelegateSet(Class<T> eventType) {
        super(eventType);
    }

    @Override
    public void add(D delegate) {
        CandleEventMapping m = delegate.getMapping();
        Object descriptor = getRecordDescriptor(m.getRecordPeriod(), m.getRecordPrice());
        if (delegate.isSub()) {
            Map<Object, List<D>> map;
            map = delegate.isTimeSeries() ? timeSeriesDelegatesByDescriptor : regularDelegatesByDescriptor;
            if (map.put(descriptor, Collections.<D>singletonList(delegate)) != null)
                throw new IllegalArgumentException("Only one delegate for descriptor " + descriptor + " is supported: " + delegate);
        }
        if (delegate.isLastingEvent() && delegate.getContract() == QDContract.TICKER) {
            List<D> delegates = lastingDelegatesByDescriptor.get(descriptor);
            if (delegates == null)
                lastingDelegatesByDescriptor.put(descriptor, delegates = new ArrayList<>(2));
            delegates.add(delegate);
        }
        if (delegate.isPub()) {
            List<D> delegates = publishableDelegatesByDescriptor.get(descriptor);
            if (delegates == null)
                publishableDelegatesByDescriptor.put(descriptor, delegates = new ArrayList<>(2));
            delegates.add(delegate);
        }
        super.add(delegate);
    }

    @Override
    public void completeConstruction() {
        fixMap(timeSeriesDelegatesByDescriptor);
        fixMap(regularDelegatesByDescriptor);
        fixMap(lastingDelegatesByDescriptor);
        fixMap(publishableDelegatesByDescriptor);
        super.completeConstruction();
    }

    @Override
    public Object convertSymbol(Object symbol) {
        if (symbol instanceof CandleSymbol)
            return symbol;
        if (symbol instanceof String)
            return CandleSymbol.valueOf((String) symbol);
        if (symbol instanceof WildcardSymbol)
            return symbol;
        throw new IllegalArgumentException("Candle symbol must have either String, CandleSymbol, or WildcardSymbol class");
    }

    @Override
    protected List<D> getRegularSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        return getFromMapByDescriptor(regularDelegatesByDescriptor, getSymbolDescriptor((CandleSymbol) symbol));
    }

    @Override
    public List<D> getTimeSeriesDelegatesByEventSymbol(Object symbol) {
        return getFromMapByDescriptor(timeSeriesDelegatesByDescriptor, getSymbolDescriptor((CandleSymbol) symbol));
    }

    @Override
    public D getLastingDelegateByEventSymbol(Object symbol) {
        List<D> list = getFromMapByDescriptor(lastingDelegatesByDescriptor, getSymbolDescriptor((CandleSymbol) symbol));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<D> getPubDelegatesByEvent(T event) {
        return getFromMapByDescriptor(publishableDelegatesByDescriptor, getSymbolDescriptor(event.getEventSymbol()));
    }

    // ---- private static helpers ----

    private static Object getSymbolDescriptor(CandleSymbol symbol) {
        return MINUTE.equals(symbol.getPeriod()) ? symbol.getPrice() : symbol.getPeriod();
    }

    private static <T extends Candle, D extends CandleEventDelegateImpl<T>> void fixMap(Map<Object, List<D>> map) {
        for (CandlePrice price : CandlePrice.values())
            if (!map.containsKey(price))
                map.put(price, map.get(MINUTE));
    }

    private static <T extends Candle, D extends CandleEventDelegateImpl<T>> List<D> getFromMapByDescriptor(Map<Object, List<D>> map, Object descriptor) {
        List<D> result = map.get(descriptor);
        if (result != null)
            return result;
        result = map.get(null);
        if (result != null)
            return result;
        return Collections.emptyList();
    }

    private static Object getRecordDescriptor(CandlePeriod period, CandlePrice price) {
        if (price != null) {
            if (MINUTE.equals(period))
                return price;
            throw new IllegalArgumentException("Record price is supported only for " + MINUTE + " period");
        }
        return period;
    }
}
