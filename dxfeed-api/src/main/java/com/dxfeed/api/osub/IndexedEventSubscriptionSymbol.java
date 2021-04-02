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
package com.dxfeed.api.osub;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;

import java.io.Serializable;
import java.util.Set;

/**
 * Represents subscription to a specific source of indexed events.
 * This is symbol is observed by {@link ObservableSubscriptionChangeListener}
 * methods {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded}
 * and {@link ObservableSubscriptionChangeListener#symbolsRemoved(Set) symbolsRemoved}
 * when subscription to {@link IndexedEvent} is defined.
 *
 * <p>Instances of this class can be used with {@link DXFeedSubscription} to specify subscription
 * to a particular source of indexed events. By default, when subscribing to indexed events by
 * their event symbol object, the subscription is performed to all supported sources.
 *
 * <p>{@link TimeSeriesEvent} represents a special subtype of {@link IndexedEvent}.
 * There is a more specific {@link TimeSeriesSubscriptionSymbol} class
 * that is used when time-series subscription is observed.
 *
 * <h3>Equality and hash codes</h3>
 *
 * Indexed event subscription symbols are compared based on their {@link #getEventSymbol() eventSymbol} and
 * {@link #getSource() source}.
 *
 * @param <T> the type of the event symbol.
 */
public class IndexedEventSubscriptionSymbol<T> implements Serializable {
    private static final long serialVersionUID = 0;

    private final T eventSymbol;
    private final IndexedEventSource source;

    /**
     * Creates indexed event subscription symbol with a specified event symbol and source.
     * @param eventSymbol the event symbol.
     * @param source the source.
     * @throws NullPointerException if eventSymbol or source are null {@code null}.
     */
    public IndexedEventSubscriptionSymbol(T eventSymbol, IndexedEventSource source) {
        if (eventSymbol == null)
            throw new NullPointerException();
        if (source == null)
            throw new NullPointerException();
        this.eventSymbol = eventSymbol;
        this.source = source;
    }

    /**
     * Returns event symbol.
     * @return event symbol.
     */
    public final T getEventSymbol() {
        return eventSymbol;
    }

    /**
     * Returns indexed event source.
     * @return indexed event source.
     */
    public final IndexedEventSource getSource() {
        return source;
    }

    /**
     * Returns {@code true} if other object is {@code IndexedEventSubscriptionSymbol} with
     * the same {@code eventSymbol} and {@code sourceId}.
     * @return result of equality check.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IndexedEventSubscriptionSymbol))
            return false;
        IndexedEventSubscriptionSymbol<?> that = (IndexedEventSubscriptionSymbol<?>) o;
        return eventSymbol.equals(that.eventSymbol) && source.equals(that.source);
    }

    /**
     * Returns hash code.
     * @return hash code.
     */
    @Override
    public int hashCode() {
        return eventSymbol.hashCode() + source.hashCode() * 31;
    }

    /**
     * Returns string representation of this indexed event subscription symbol.
     * @return string representation of this indexed event subscription symbol.
     */
    @Override
    public String toString() {
        return eventSymbol + "{source=" + source + "}";
    }
}
