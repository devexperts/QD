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

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class OrderBaseDelegateSet<T extends OrderBase, D extends OrderBaseDelegateImpl<T>> extends MarketEventDelegateSet<T, D> {
    private static final IndexerFunction.LongKey<List<? extends OrderBaseDelegateImpl<?>>> DELEGATE_LIST_BY_SOURCE_ID = value -> value.get(0).getSource().id();

    private final IndexedSet<Long, List<D>> subDelegatesBySource = IndexedSet.createLong(DELEGATE_LIST_BY_SOURCE_ID);
    private final IndexedSet<Long, List<D>> pubDelegatesBySource = IndexedSet.createLong(DELEGATE_LIST_BY_SOURCE_ID);

    OrderBaseDelegateSet(Class<T> eventType) {
        super(eventType);
    }

    @Override
    public void add(D delegate) {
        if (delegate.isSub())
            addToSet(subDelegatesBySource, delegate);
        if (delegate.isPub())
            addToSet(pubDelegatesBySource, delegate);
        super.add(delegate);
    }

    @Override
    protected List<D> getRegularSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        return sourceId >= 0 ?
            getFromSet(subDelegatesBySource, sourceId) :
            super.getRegularSubDelegatesBySubscriptionSymbol(symbol, sourceId);
    }

    @Override
    public List<D> getPubDelegatesByEvent(T event) {
        return getFromSet(pubDelegatesBySource, event.getSource().id());
    }

    private static <T extends OrderBase, D extends OrderBaseDelegateImpl<T>> void addToSet(IndexedSet<Long, List<D>> set, D delegate) {
        List<D> list = set.getByKey(delegate.getSource().id());
        if (list == null) {
            list = new ArrayList<>(1);
            list.add(delegate);
            set.add(list);
        } else
            list.add(delegate);
    }

    private static <T extends OrderBase, D extends OrderBaseDelegateImpl<T>> List<D> getFromSet(IndexedSet<Long, List<D>> set, int sourceId) {
        List<D> list = set.getByKey(sourceId);
        return list == null ? Collections.<D>emptyList() : list;
    }
}
