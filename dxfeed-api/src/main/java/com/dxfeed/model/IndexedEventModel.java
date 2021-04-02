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
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.model.market.OrderBookModel;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Model for a list of indexed events.
 * This class handles all snapshot and transaction logic of {@link IndexedEvent} class and
 * arranges incoming events into a {@link #getEventsList() list} ordered by
 * their {@link IndexedEvent#getSource() source} {@link IndexedEventSource#id() id}
 * and {@link IndexedEvent#getIndex() index}. Note, that {@link TimeSeriesEvent} extends {@link IndexedEvent} in
 * such a way, that this ordering also orders time-series event by {@link TimeSeriesEvent#getTime() time}. However,
 * there is a time-series aware class {@link TimeSeriesEventModel} that provides a more convenient way to
 * subscribe to time-series.
 *
 * <p>The model must be configured with {@link #setSymbol(Object) symbol} to subscribe for and
 * {@link #attach(DXFeed) attached} to a {@link DXFeed} instance to start operation.
 *
 * <p>The view of the current list of events is available via {@link #getEventsList() getEventsList} method.
 * Model change notifications are provided via {@link ObservableListModel#addListener(ObservableListModelListener) addListener}
 * method of this list. It accepts an instance of {@link ObservableListModelListener} class.
 *
 * <p>Users of this model only see the list of events in a consistent state. This model delays incoming events which
 * are part of incomplete snapshot or ongoing transaction until snapshot is complete or transaction has ended.
 * These pending events cannot be seen neither via list nor via listener calls, and so
 * {@link IndexedEvent#getEventFlags() eventFlags} of events in the model are set to zero.
 * The eventFlags are only used and must be taken into account when processing indexed events directly via low-level
 * {@link DXFeedSubscription} class.
 *
 * <p>Note, that it is not of much use to arrange {@link Order} events by their {@link Order#getIndex() index},
 * so there is a dedicated {@link OrderBookModel} that arranges buy and sell orders separately by their price.
 *
 * <h3>Sample usage</h3>
 *
 * <p>dxFeed API comes with a set of samples. {@code DXFeedTimeAndSales} sample is a very simple UI application
 * that shows how to use this model with {@code TimeAndSale} event, for example, concentrating your
 * effort on data representation logic, while delegating all the data-handling logic to this model.
 * It also showcases some of the advanced methods of this model like {@link #setSizeLimit(int) setSizeLimit}.
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
public class IndexedEventModel<E extends IndexedEvent<?>>
    extends AbstractIndexedEventModel<E, AbstractIndexedEventModel.Entry<E>>
{
    // ================================== private instance fields ==================================

    private final Events events = new Events();
    private final List<ObservableListModelListener<? super E>> listeners = new CopyOnWriteArrayList<>();
    private final ObservableListModelListener.Change<E> change = new ObservableListModelListener.Change<>(events);

    // ================================== public instance methods & constructor ==================================

    /**
     * Creates new model. This model is not attached to any feed, not subscribed to any symbol.
     * Use {@link #setSymbol} to specify subscription symbol and
     * {@link #attach} method to specify feed to start receiving events.
     *
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    public IndexedEventModel(Class<? extends E> eventType) {
        super(eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        super.close();
        listeners.clear();
    }

    /**
     * Returns the view of the current list of events in this model.
     * This method returns the reference to the same object on each invocation.
     * The resulting list is immutable. It reflects the current list of events
     * and is updated on arrival of new events. See
     * <a href="#threadsAndLocksSection">Threads and locks</a> section for
     * details on concurrency of these updates.
     *
     * @return the view of the current list of events in this model.
     */
    public ObservableListModel<E> getEventsList() {
        return events;
    }

    // ================================== private implementation details ==================================

    /**
     * {@inheritDoc}
     *
     * <p>This implementation creates an new instance of {@link AbstractIndexedEventModel.Entry}.
     */
    @Override
    protected Entry<E> createEntry() {
        return new Entry<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation {@link AbstractIndexedEventModel.Entry#commitChange() commits} all changes and invokes
     * {@link ObservableListModelListener#modelChanged(ObservableListModelListener.Change) ObservableListModelListener.modelChanged}
     * on all installed listeners.
     */
    @Override
    protected void modelChanged(List<Entry<E>> changedEntries) {
        changedEntries.forEach(Entry::commitChange);
        for (ObservableListModelListener<? super E> listener : listeners) {
            listener.modelChanged(change);
        }
    }

    private class Events extends AbstractList<E> implements ObservableListModel<E> {
        @Override
        public int size() {
            return IndexedEventModel.this.size();
        }

        @Override
        public E get(int index) {
            return IndexedEventModel.this.get(index);
        }

        @Override
        public Iterator<E> iterator() {
            return IndexedEventModel.this.listIterator();
        }

        @Override
        public ListIterator<E> listIterator() {
            return IndexedEventModel.this.listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            return IndexedEventModel.this.listIterator(index);
        }

        @Override
        public void addListener(ObservableListModelListener<? super E> listener) {
            if (IndexedEventModel.this.isClosed())
                return;
            listeners.add(listener);
        }

        @Override
        public void removeListener(ObservableListModelListener<? super E> listener) {
            listeners.remove(listener);
        }
    }
}
