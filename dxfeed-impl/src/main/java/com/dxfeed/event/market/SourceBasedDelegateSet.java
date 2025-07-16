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
package com.dxfeed.event.market;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.SystemProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class SourceBasedDelegateSet<T extends MarketEvent, D extends SourceBasedDelegateImpl<T>>
    extends MarketEventDelegateSet<T, D>
{
    private static final boolean USE_UNITARY_ORDER_SOURCE =
        SystemProperties.getBooleanProperty(OrderBaseDelegateImpl.DXSCHEME_UNITARY_ORDER_SOURCE, false);
    private static final IndexerFunction.LongKey<List<? extends SourceBasedDelegateImpl<?>>> DELEGATES_BY_SOURCE_ID =
        value -> value.get(0).getSource().id();

    private final IndexedSet<Long, List<D>> subDelegatesBySource = IndexedSet.createLong(DELEGATES_BY_SOURCE_ID);
    private final IndexedSet<Long, List<D>> pubDelegatesBySource = IndexedSet.createLong(DELEGATES_BY_SOURCE_ID);

    SourceBasedDelegateSet(Class<T> eventType) {
        super(eventType);
    }

    @Override
    public void add(D delegate) {
        if (delegate.isSub())
            addToSubSet(delegate);
        if (delegate.isPub())
            addToPubSet(delegate);
        if (shouldAddDelegateToSuper(delegate))
            super.add(delegate);
    }

    @Override
    protected List<D> getRegularSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        return sourceId >= 0 ?
            getSubDelegatesBySourceId(sourceId) :
            super.getRegularSubDelegatesBySubscriptionSymbol(symbol, sourceId);
    }

    protected void addToSubSet(D delegate) {
        addToSet(subDelegatesBySource, delegate);
    }

    protected void addToPubSet(D delegate) {
        addToSet(pubDelegatesBySource, delegate);
    }

    protected List<D> getSubDelegatesBySourceId(int sourceId) {
        return getFromSet(subDelegatesBySource, sourceId);
    }

    protected List<D> getPubDelegatesBySourceId(int sourceId) {
        return getFromSet(pubDelegatesBySource, sourceId);
    }

    private static <T extends MarketEvent, D extends SourceBasedDelegateImpl<T>> void addToSet(
        IndexedSet<Long, List<D>> set, D delegate)
    {
        List<D> list = set.getByKey(delegate.getSource().id());
        if (list == null) {
            list = new ArrayList<>(1);
            list.add(delegate);
            set.add(list);
        } else {
            list.add(delegate);
        }
    }

    private static <T extends MarketEvent, D extends SourceBasedDelegateImpl<T>> List<D> getFromSet(
        IndexedSet<Long, List<D>> set, int sourceId)
    {
        List<D> list = set.getByKey(sourceId);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Determines whether a delegate with a special source should be added to the superclass based on the scheme
     * and source. Delegates with a special source that are added to the superclass are typically used when subscribing
     * to all possible sources, unless a specific source is specified when subscribing to an order.
     * Since there are "unitary" and "separate" sources, it is undesirable to add them all at the same time
     * when subscriptions on all sources. This does not affect the ability to explicitly specify some sources separately
     * or together; it only affects the behavior when subscribing to all sources (subscribe to order without a source).
     *
     * <p>This method evaluates whether the delegate's source is a "special" source and whether it is considered a
     * "unitary" or "separate" source. A "separate" source typically ends with "_ASK" or "_BID", indicating a specific
     * side of the order book. The method determines the preference between "unitary" and "separate"
     * sources based on the scheme and allows any non-special sources.
     *
     * @param delegate delegate to check.
     * @return {@code true} if the delegate should be added to the superclass, {@code false} otherwise.
     */
    private boolean shouldAddDelegateToSuper(D delegate) {
        if (!OrderSource.isSpecialSourceId(delegate.getSource().id()))
            return true;
        String source = delegate.getSource().toString();
        boolean isUnitarySource = !(source.endsWith("_BID") || source.endsWith("_ASK"));
        return USE_UNITARY_ORDER_SOURCE == isUnitarySource;
    }
}
