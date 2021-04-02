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

import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.FilteredSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;

import java.util.Set;

/**
 * Represents subscription to time-series of events.
 * This is symbol is observed by {@link ObservableSubscriptionChangeListener}
 * methods {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded}
 * and {@link ObservableSubscriptionChangeListener#symbolsRemoved(Set) symbolsRemoved}
 * when time-series subscription is created via {@link DXFeedTimeSeriesSubscription} class.
 *
 * <p>Instances of this class can be used with {@link DXFeedSubscription} to specify subscription
 * for time series events from a specific time. By default, subscribing to time-series events by
 * their event symbol object, the subscription is performed to a stream of new events as they happen only.
 *
 * <p>{@link TimeSeriesEvent} represents a special subtype of {@link IndexedEvent}.
 * The source identifier of a time-series event is always zero and thus
 * {@link #getSource() getSource} method for time-series subscription symbol always returns
 * {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <h3>Equality and hash codes</h3>
 *
 * This is a {@link FilteredSubscriptionSymbol}.
 * Time-series subscription symbols are compared based on their {@link #getEventSymbol() eventSymbol} only.
 * It means, that a set of time-series subscription symbols can contain at most one time-series subscription
 * for each event symbol.
 *
 * @param <T> the type of the event symbol.
 */
public class TimeSeriesSubscriptionSymbol<T> extends IndexedEventSubscriptionSymbol<T>
    implements FilteredSubscriptionSymbol
{
    private static final long serialVersionUID = 0;

    private final long fromTime;

    /**
     * Creates time-series subscription symbol with a specified event symbol and subscription time.
     * @param eventSymbol the event symbol.
     * @param fromTime the subscription time.
     * @throws NullPointerException if eventSymbol is {@code null}.
     */
    public TimeSeriesSubscriptionSymbol(T eventSymbol, long fromTime) {
        super(eventSymbol, IndexedEventSource.DEFAULT);
        this.fromTime = fromTime;
    }

    /**
     * Returns subscription time.
     * @return subscription time.
     */
    public final long getFromTime() {
        return fromTime;
    }

    /**
     * Returns {@code true} if other object is {@code TimeSeriesSubscriptionSymbol} with
     * the same {@code eventSymbol}.
     * @return result of equality check.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TimeSeriesSubscriptionSymbol))
            return false;
        TimeSeriesSubscriptionSymbol<?> that = (TimeSeriesSubscriptionSymbol<?>) o;
        return getEventSymbol().equals(that.getEventSymbol());
    }

    /**
     * Returns {@code eventSymbol} hash code.
     * @return {@code eventSymbol} hash code.
     */
    @Override
    public int hashCode() {
        return getEventSymbol().hashCode();
    }

    /**
     * Returns string representation of this time-series subscription symbol.
     * @return string representation of this time-series subscription symbol.
     */
    @Override
    public String toString() {
        return getEventSymbol() + "{fromTime=" + TimeFormat.DEFAULT.withMillis().format(fromTime) + "}";
    }
}
