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
package com.dxfeed.api;

import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.TimeSeriesEvent;

import java.util.Objects;

/**
 * Extends {@link DXFeedSubscription} to conveniently subscribe to time-series of
 * events for a set of symbols and event types. This class decorates symbols
 * that are passed to {@code xxxSymbols} methods in {@link DXFeedSubscription}
 * by wrapping them into {@link TimeSeriesSubscriptionSymbol} instances with
 * the current value of {@link #getFromTime() fromTime} property.
 * While {@link #getSymbols() getSymbols} method returns original (undecorated)
 * symbols, any installed {@link ObservableSubscriptionChangeListener} will see
 * decorated ones.
 *
 * <p> Only events that implement {@link TimeSeriesEvent} interface can be
 * subscribed to with {@code DXFeedTimeSeriesSubscription}.
 *
 * <h3>From time</h3>
 *
 * The value of {@link #getFromTime() fromTime} property defines the time-span of
 * events that are subscribed to. Only events that satisfy
 * {@code event.getEventTime() >= thisSubscription.getFromTime()} are looked for.
 *
 * <p> The value {@code fromTime} is initially set to {@link Long#MAX_VALUE Long.MAX_VALUE}
 * with a special meaning that no events will be received until {@code fromTime} is
 * changed with {@link #setFromTime(long) setFromTime} method.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * @param <E> the type of events.
 */
public class DXFeedTimeSeriesSubscription<E extends TimeSeriesEvent> extends DXFeedSubscription<E> {
    private static final long serialVersionUID = 0;

    private long fromTime = Long.MAX_VALUE;

    /**
     * Creates <i>detached</i> time-series subscription for a single event type.
     *
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    public DXFeedTimeSeriesSubscription(Class<? extends E> eventType) {
        super(eventType);
    }

    /**
     * Creates <i>detached</i> time-series subscription for the given list of event types.
     *
     * @param eventTypes the list of event types.
     * @throws IllegalArgumentException if the list of event types is empty.
     * @throws NullPointerException if any event type is null.
     */
    public DXFeedTimeSeriesSubscription(Class<? extends E>... eventTypes) {
        super(eventTypes);
    }

    /**
     * Returns the earliest timestamp from which time-series of events shall be received.
     * The timestamp is in milliseconds from midnight, January 1, 1970 UTC.
     *
     * @return the earliest timestamp from which time-series of events shall be received.
     *
     * @see System#currentTimeMillis()
     */
    public synchronized long getFromTime() {
        return fromTime;
    }

    /**
     * Sets the earliest timestamp from which time-series of events shall be received.
     * The timestamp is in milliseconds from midnight, January 1, 1970 UTC.
     *
     * @param fromTime the timestamp.
     *
     * @see System#currentTimeMillis()
     */
    public synchronized void setFromTime(long fromTime) {
        if (this.fromTime != fromTime) {
            this.fromTime = fromTime;
            setSymbols(getSymbols());
        }
    }

    /**
     * Decorates the specified symbol after it was received from the {@code DXFeedSubscription} client code
     * before it goes to installed {@link ObservableSubscriptionChangeListener} instances.
     * This implementation wraps symbol into {@link TimeSeriesSubscriptionSymbol} with a current
     * {@link #getFromTime() fromTime} value.
     *
     * @param symbol the symbol to decorate.
     * @throws NullPointerException if symbol is null.
     * @return decorated symbol.
     */
    @Override
    protected Object decorateSymbol(Object symbol) {
        return new TimeSeriesSubscriptionSymbol<Object>(symbol, fromTime);
    }

    /**
     * Undoes the decoration of the specified symbol doing the reverse operation to {@link #decorateSymbol(Object)}.
     * This implementation throws {@code ClassCastException} is symbol is not an instance of
     * {@link TimeSeriesSubscriptionSymbol} or returns its
     * {@link TimeSeriesSubscriptionSymbol#getEventSymbol() eventSymbol} otherwise.
     *
     * @param symbol the symbol to undecorate.
     * @throws NullPointerException if symbol is null.
     * @return undecorated symbol or null if symbol is not an instance of {@link TimeSeriesSubscriptionSymbol}.
     */
    @Override
    protected Object undecorateSymbol(Object symbol) {
        Objects.requireNonNull(symbol);
        if (symbol instanceof TimeSeriesSubscriptionSymbol) {
            return ((TimeSeriesSubscriptionSymbol<?>) symbol).getEventSymbol();
        }
        return null;
    }
}
