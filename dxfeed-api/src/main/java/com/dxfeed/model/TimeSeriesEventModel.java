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
package com.dxfeed.model;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.TimeSeriesEvent;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Model for a list of time-series events.
 * This class handles all snapshot and transaction logic of {@link TimeSeriesEvent} class and
 * arranges incoming events into a {@link #getEventsList() list} ordered by their {@link TimeSeriesEvent#getTime() time}.
 *
 * <p>The model must be configured with {@link #setSymbol(Object) symbol} and {@link #setFromTime(long) fromTime}
 * to subscribe and {@link #attach(DXFeed) attached} to a {@link DXFeed} instance to start operation.
 *
 * <p>The view of the current list of events is available via {@link #getEventsList() getEventsList} method.
 * Model change notifications are provided via {@link ObservableListModel#addListener(ObservableListModelListener) addListener}
 * method of this list. It accepts an instance of {@link ObservableListModelListener} class.
 *
 * <p>Users of this model only see the list of events in a consistent state. This model delays incoming events which
 * are part of incomplete snapshot or ongoing transaction until snapshot is complete or transaction has ended.
 * These pending events cannot be seen neither via list nor via listener calls, and so
 * {@link IndexedEvent#getEventFlags() eventFlags} of events in the model are set to zero.
 * The eventFlags are only used and must be taken into account when processing time-series events directly via low-level
 * {@link DXFeedSubscription} or {@link DXFeedTimeSeriesSubscription} classes.
 *
 * <h3>Sample usage</h3>
 *
 * <p>dxFeed API comes with a set of samples. {@code DXFeedCandleChart} sample is a very simple UI application
 * that shows how to use this model with {@code Candle} event to paint a simple candle chart, concentrating your
 * effort on data representation (painting) logic, while delegating all the data-handling logic to this model.
 *
 * <h3>Resource management and closed models</h3>
 *
 * Attached model is a potential memory leak. If the pointer to attached model is lost, then there is no way to detach
 * this model from the feed and the model will not be reclaimed by the garbage collector as long as the corresponding
 * feed is still used. Detached model can be reclaimed by the garbage collector, but detaching model requires knowing
 * the pointer to the feed at the place of the call, which is not always convenient.
 *
 * <p> The convenient way to detach model from the feed is to call its {@link #close close} method. Closed model
 * becomes permanently detached from all feeds, removes all its listeners and is guaranteed to be reclaimable by
 * the garbage collector as soon as all external references to it are cleared.
 *
 * <h3><a name="threadsAndLocksSection">Threads and locks</a></h3>
 *
 * This class is <b>not</b> tread-safe and requires external synchronization.
 * The only thread-safe methods are {@link #attach attach}, {@link #detach detach} and {@link #close close}.
 *
 * <p> You must query the state of {@link #attach(DXFeed) attached} model only from
 * inside of the notification invocations or from within the thread that performs
 * those notifications.
 *
 * <p> Installed {@link ObservableListModelListener} instances are invoked from a separate thread via the executor.
 * Default executor for all models is configured with {@link DXEndpoint#executor(Executor) DXEndpoint.executor}
 * method. Each model can individually override its executor with {@link #setExecutor(Executor) setExecutor}
 * method. The corresponding
 * {@link #modelChanged(List)  modelChanged}
 * notification is guaranteed to never be concurrent, even though it may happen from different
 * threads if executor is multi-threaded.
 *
 * <p>In practice, it means that is UI applications you <b>must</b>
 * install UI-thread-bound execution to your {@link DXEndpoint} via
 * {@link DXEndpoint#executor(Executor) DXEndpoint.executor} method, so that you
 * can freely use all methods of this model from UI thread.
 *
 * <p>Custom executor can be used by backend applications that do not need to immediately update this model on
 * arrival of new events, but want to update the model at a later time, for example, from inside of a servlet request.
 * This approach is explained with code samples in
 * <a href="../api/DXFeedSubscription.html#threadsAndLocksSection">Threads and locks</a>
 * section of {@link DXFeedSubscription} class documentation.
 *
 * @param <E> the type of events.
 */
public class TimeSeriesEventModel<E extends TimeSeriesEvent<?>> extends IndexedEventModel<E> {
    private Object eventSymbol;
    private long fromTime = Long.MAX_VALUE;

    /**
     * Creates new model. This model is not attached to any feed, not subscribed to any symbol.
     * Use {@link #setSymbol} to specify subscription symbol, {@link #setFromTime} to specify subscription time, and
     * {@link #attach} method to specify feed to start receiving events.
     *
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    public TimeSeriesEventModel(Class<? extends E> eventType) {
        super(eventType);
    }

    /**
     * Clears subscription symbol and sets {@link #setFromTime(long) fromTime} to {@link Long#MAX_VALUE},
     * subsequently, all events in this model are cleared.
     */
    @Override
    public void clear() {
        setSymbol(null);
        setFromTime(Long.MAX_VALUE);
    }

    /**
     * Returns model subscription symbol, or {@code null} is not subscribed
     * (this is a default value).
     * @return model subscription symbol.
     */
    @Override
    public Object getSymbol() {
        return eventSymbol;
    }

    /**
     * Changes symbol for this model to subscribe for.
     * @param symbol model subscription symbol, use {@code null} to unsubscribe.
     */
    @Override
    public void setSymbol(Object symbol) {
        this.eventSymbol = symbol;
        updateSub();
    }

    /**
     * Returns the time from which to subscribe for time-series, or {@link Long#MAX_VALUE} is not subscribed
     * (this is a default value).
     * @return the time from which to subscribe for time-series.
     */
    public long getFromTime() {
        return fromTime;
    }

    /**
     * Changes the time from which to subscribe for time-series, use {@link Long#MAX_VALUE} to unsubscribe.
     * @param fromTime the time from which to subscribe for time-series, use {@link Long#MAX_VALUE} to unsubscribe.
     */
    public void setFromTime(long fromTime) {
        this.fromTime = fromTime;
        updateSub();
    }

    private void updateSub() {
        super.setSymbol(eventSymbol == null || fromTime == Long.MAX_VALUE ? null :
            new TimeSeriesSubscriptionSymbol<>(eventSymbol, fromTime));
    }
}
