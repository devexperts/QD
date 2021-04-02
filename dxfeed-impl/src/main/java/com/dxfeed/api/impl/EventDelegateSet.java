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
package com.dxfeed.api.impl;

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.LastingEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EventDelegateSet<T extends EventType<?>, D extends EventDelegate<T>> {
    protected final Class<T> eventType;
    protected final List<D> allSubDelegates = new ArrayList<>();
    protected final List<D> allPubDelegates = new ArrayList<>();
    protected final List<D> allTimeSeriesDelegates = new ArrayList<>();
    protected final List<D> allWildcardDelegates = new ArrayList<>();
    protected final List<D> allLastingDelegates = new ArrayList<>();
    protected boolean complete;

    public EventDelegateSet(Class<T> eventType) {
        this.eventType = eventType;
    }

    public Class<T> eventType() {
        return eventType;
    }

    public void add(D delegate) {
        if (complete)
            throw new IllegalStateException();
        if (delegate.isSub()) {
            if (delegate.isTimeSeries())
                allTimeSeriesDelegates.add(delegate);
            else
                allSubDelegates.add(delegate);
            if (delegate.isLastingEvent())
                allLastingDelegates.add(delegate);
        }
        if (delegate.isPub())
            allPubDelegates.add(delegate);
        if (delegate.isWildcard())
            allWildcardDelegates.add(delegate);
    }

    public void completeConstruction() {
        complete = true;
    }

    /**
     * Converts this symbol to a class that is used by this set of event delegates.
     */
    public Object convertSymbol(Object symbol) {
        return symbol;
    }

    /**
     * This method is used to prepare subscription to the given regular (non time series) symbol in {@link DXFeed}.
     * @param symbol subscription symbol.
     * @param sourceId source identifier or -1 if it was not explicitly specified via {@link IndexedEventSubscriptionSymbol}.
     * @throws ClassCastException if symbol class is not supported.
     */
    public List<D> getSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        if (symbol instanceof WildcardSymbol)
            return allWildcardDelegates;
        if (symbol.toString().startsWith(WildcardSymbol.RESERVED_PREFIX))
            return Collections.emptyList(); // don't support subscription to symbols starting with "*"
        return getRegularSubDelegatesBySubscriptionSymbol(symbol, sourceId);
    }

    protected List<D> getRegularSubDelegatesBySubscriptionSymbol(Object symbol, int sourceId) {
        return sourceId <= 0 ? allSubDelegates : Collections.<D>emptyList();
    }

    /**
     * This method is used to prepare subscription to the given time series symbol in {@link DXFeed}.
     * @throws ClassCastException if symbol class is not supported.
     */
    public List<D> getTimeSeriesDelegatesByEventSymbol(Object symbol) {
        return allTimeSeriesDelegates;
    }

    /**
     * This method is used by {@link DXFeed#getLastEvent(LastingEvent)}
     */
    public D getLastingDelegateByEventSymbol(Object symbol) {
        return allLastingDelegates.isEmpty() ? null : allLastingDelegates.get(0);
    }

    /**
     * This method is used by {@link DXPublisher#publishEvents(Collection)}
     */
    public List<D> getPubDelegatesByEvent(T event) {
        return allPubDelegates;
    }

    /**
     * This method is used to find records for {@link DXPublisher#getSubscription(Class)}
     */
    public List<D> getAllPubDelegates() {
        return allPubDelegates;
    }
}
