/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterTracker;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * {@link DXFeedFilter} implementation based on {@link QDFilter}.
 * Bridges from DXFeed API coordinates to QD-level coordinates
 * using the {@link EventDelegateSet} infrastructure.
 */
class DXFeedFilterImpl implements DXFeedFilter {

    private final DXEndpointImpl endpoint;
    private final String[] categoryNames;
    private final QDFilter[] categoryFilters;
    private final DXFeedFilterTrackerImpl tracker;

    /**
     * Creates an initial filter instance from a builder.
     * Parses filter expressions and constructs a tracker.
     */
    DXFeedFilterImpl(DXFeedFilter.Builder builder) {
        this.endpoint = (DXEndpointImpl) builder.getEndpoint();
        Map<String, String> categories = builder.getCategories();
        String[] names = categories.keySet().toArray(new String[0]);
        String[] expressions = categories.values().toArray(new String[0]);

        QDFilter[] qdFilters = new QDFilter[expressions.length];
        QDFilter.Updated[] qdUpdated = new QDFilter.Updated[expressions.length];
        for (int i = 0; i < expressions.length; i++) {
            qdFilters[i] = CompositeFilters.valueOf(expressions[i], endpoint.getQDEndpoint().getScheme());
            qdUpdated[i] = qdFilters[i].getUpdated();
        }

        this.categoryNames = names;
        this.categoryFilters = qdFilters;
        this.tracker = new DXFeedFilterTrackerImpl(this, qdUpdated);
    }

    /**
     * Creates a filter from pre-built QDFilters.
     * Note: for testing with dynamic QDFilters that cannot be expressed as filter strings.
     */
    DXFeedFilterImpl(DXEndpointImpl endpoint, String[] categoryNames, QDFilter[] categoryFilters) {
        this.endpoint = endpoint;
        this.categoryNames = categoryNames;
        this.categoryFilters = categoryFilters;
        QDFilter.Updated[] qdUpdated = new QDFilter.Updated[categoryFilters.length];
        for (int i = 0; i < categoryFilters.length; i++) {
            qdUpdated[i] = categoryFilters[i].getUpdated();
        }
        this.tracker = new DXFeedFilterTrackerImpl(this, qdUpdated);
    }

    /**
     * Creates a filter instance from a previous one with updated filter content.
     */
    DXFeedFilterImpl(DXFeedFilterImpl previous, QDFilter[] newCategoryFilters) {
        this.endpoint = previous.endpoint;
        this.categoryNames = previous.categoryNames;
        this.categoryFilters = newCategoryFilters;
        this.tracker = previous.tracker;
    }

    @Override
    public boolean accept(Class<? extends EventType<?>> eventType, Object symbol) {
        return findSubscriptionCategory(eventType, symbol, true) >= 0;
    }

    @Override
    public boolean acceptEvent(EventType<?> event) {
        return findEventCategory(event, true) >= 0;
    }

    @Override
    @Nullable
    public String getCategory(Class<? extends EventType<?>> eventType, Object symbol) {
        int i = findSubscriptionCategory(eventType, symbol, false);
        return i >= 0 ? categoryNames[i] : null;
    }

    @Override
    @Nullable
    public String getEventCategory(EventType<?> event) {
        int i = findEventCategory(event, false);
        return i >= 0 ? categoryNames[i] : null;
    }

    @Override
    public boolean isDynamic() {
        return tracker.isDynamic();
    }

    @Override
    public DXFeedFilterTracker getTracker() {
        return tracker;
    }

    QDFilter[] getCategoryFilters() {
        return categoryFilters;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int findSubscriptionCategory(Class<? extends EventType<?>> eventType, Object symbol,
        boolean earlyReturn)
    {
        EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(eventType);
        if (delegateSet == null)
            return -1;

        // Mirror DXFeedImpl.toSubscription: TSS before IES (TSS extends IES).
        List<EventDelegate> delegates;
        Object eventSymbol;
        if (symbol instanceof TimeSeriesSubscriptionSymbol) {
            TimeSeriesSubscriptionSymbol<?> tss = (TimeSeriesSubscriptionSymbol<?>) symbol;
            eventSymbol = delegateSet.convertSymbol(tss.getEventSymbol());
            delegates = delegateSet.getTimeSeriesDelegatesByEventSymbol(eventSymbol);
        } else if (symbol instanceof IndexedEventSubscriptionSymbol) {
            IndexedEventSubscriptionSymbol<?> ies = (IndexedEventSubscriptionSymbol<?>) symbol;
            eventSymbol = delegateSet.convertSymbol(ies.getEventSymbol());
            delegates = delegateSet.getSubDelegatesBySubscriptionSymbol(eventSymbol, ies.getSource().id());
        } else {
            eventSymbol = delegateSet.convertSymbol(symbol);
            delegates = delegateSet.getSubDelegatesBySubscriptionSymbol(eventSymbol, -1);
        }
        return findCategory(delegates, eventSymbol, null, earlyReturn);
    }

    // EventDelegateSet/EventDelegate are raw at this seam — type-safety is preserved at the call sites.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int findEventCategory(EventType<?> event, boolean earlyReturn) {
        EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(event.getClass());
        if (delegateSet == null)
            return -1;
        return findCategory(delegateSet.getPubDelegatesByEvent(event), null, event, earlyReturn);
    }

    // Returns the smallest category index whose QDFilter accepts via any delegate.
    // When earlyReturn is true, returns immediately on the first match (any category).
    // Loop is delegate-outer, category-inner so per-delegate state is computed once for all categories.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int findCategory(List<EventDelegate> delegates, Object eventSymbol, EventType<?> event,
        boolean earlyReturn)
    {
        if (delegates.isEmpty())
            return -1;
        int best = categoryFilters.length;
        for (EventDelegate delegate : delegates) {
            String qdSymbol = (event != null) ?
                delegate.getQDSymbolByEvent(event) : delegate.getQDSymbolByEventSymbol(eventSymbol);
            int cipher = endpoint.encode(qdSymbol);
            DataRecord record = delegate.getRecord();
            QDContract contract = delegate.getContract();
            for (int i = 0; i < best; i++) {
                if (categoryFilters[i].accept(contract, record, cipher, qdSymbol)) {
                    if (earlyReturn)
                        return i;
                    best = i;
                    break;
                }
            }
            if (best == 0)
                break;
        }
        return best < categoryFilters.length ? best : -1;
    }
}
