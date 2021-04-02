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
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.impl.AbstractIndexedList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * <p>Abstract model for a list of indexed events.
 * This class handles all snapshot and transaction logic of {@link IndexedEvent} class and
 * arranges incoming events into a list ordered by their {@link IndexedEvent#getSource() source} {@link IndexedEventSource#id() id}
 * and {@link IndexedEvent#getIndex() index}. Note, that {@link TimeSeriesEvent} extends {@link IndexedEvent} in
 * such a way, that this ordering also orders time-series event by {@link TimeSeriesEvent#getTime() time}. However,
 * there is a time-series aware class {@link TimeSeriesEventModel} that provides a more convenient way to
 * subscribe to time-series.
 *
 * <p>This abstract class provides only <b>protected</b> methods to access the underlying list of events:
 * {@link #size() size}, {@link #get(int) get}, {@link #listIterator() listIterator},
 * and {@link #entryListIterator() entryListIterator},
 * because it is designed for extension and is not designed for a direct use.
 * For a concrete implementation use {@link IndexedEventModel} class.
 *
 * <p>The model must be configured with {@link #setSymbol(Object) symbol} to subscribe for and
 * {@link #attach(DXFeed) attached} to a {@link DXFeed} instance to start operation.
 *
 * <p>Model change notifications are provided by an abstract {@link #modelChanged(List) modelChanged} method
 * that must be overridden in concrete implementation classes.
 * This class also provides abstract {@link #createEntry() createEntry} method that must be overridden to return a
 * fresh instance of an {@link Entry} class that is used inside this class to wrap incoming events and
 * may be augmented with an additional event-related information if needed.
 *
 * <p>Users of this model only see the list of events in a consistent state. This model delays incoming events which
 * are part of incomplete snapshot or ongoing transaction until snapshot is complete or transaction has ended.
 * These pending events cannot be seen neither via get methods nor via {@link #modelChanged(List) modelChanged} calls,
 * and so {@link IndexedEvent#getEventFlags() eventFlags} of events in the model are set to zero.
 * The eventFlags are only used and must be taken into account when processing indexed events directly via low-level
 * {@link DXFeedSubscription} class.
 *
 * <h3>Resource management and closed models</h3>
 * <p>
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
 * <p>This class is <b>not</b> tread-safe and requires external synchronization.
 * The only thread-safe methods are {@link #attach attach}, {@link #detach detach} and {@link #close close}.
 *
 * <p> You must query the state of {@link #attach(DXFeed) attached} model only from
 * inside of the notification invocations or from within the thread that performs
 * those notifications.
 *
 * <p> Notification on model changes are invoked from a separate thread via the executor.
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
 * @param <E> the type of events (entry values).
 * @param <N> the type of concrete entries in the model.
 */
public abstract class AbstractIndexedEventModel<E extends IndexedEvent<?>, N extends AbstractIndexedEventModel.Entry<E>>
    implements AutoCloseable
{
    // ================================== private instance fields ==================================

    private final DXFeedSubscription<E> subscription;

    // A list of events to be processed immediately (tx pending events are in Source.pendingEvents)
    private final ArrayList<E> processEventsNow = new ArrayList<>();

    // A list of entries that were changed and not processed yet
    // INVARIANT: All elements of this list have Entry.changed true when added here
    private final ArrayList<N> changedEntries = new ArrayList<>();

    // An element for each source, typically just one or very few, ordered by sourceId
    @SuppressWarnings("unchecked")
    private Source[] sources = (Source[]) new AbstractIndexedEventModel.Source[1];
    private int nSources;

    private Object symbol;
    private int sizeLimit = Integer.MAX_VALUE;

    // ================================== public instance methods & constructor ==================================

    /**
     * Creates new model. This model is not attached to any feed, not subscribed to any symbol.
     * Use {@link #setSymbol} to specify subscription symbol and
     * {@link #attach} method to specify feed to start receiving events.
     *
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    protected AbstractIndexedEventModel(Class<? extends E> eventType) {
        subscription = new DXFeedSubscription<>(eventType);
        subscription.addEventListener(new Listener());
    }

    /**
     * Creates new model attached to the specified feed. It is not subscribed to any symbol.
     * Use {@link #setSymbol} to specify subscription symbol.
     *
     * @param feed      feed to attach to.
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    protected AbstractIndexedEventModel(DXFeed feed, Class<? extends E> eventType) {
        this(eventType);
        attach(feed);
    }

    /**
     * Attaches model to the specified feed.
     *
     * @param feed feed to attach to.
     */
    public void attach(DXFeed feed) {
        feed.attachSubscription(subscription);
    }

    /**
     * Detaches model from the specified feed.
     *
     * @param feed feed to detach from.
     */
    public void detach(DXFeed feed) {
        feed.detachSubscription(subscription);
    }

    protected boolean isClosed() {
        return subscription.isClosed();
    }

    /**
     * Closes this model and makes it <i>permanently detached</i>.
     *
     * <p> This method ensures that model can be safely garbage-collected when all outside references to it are lost.
     */
    @Override
    public void close() {
        subscription.close();
    }

    /**
     * Returns executor for processing event notifications on this model.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     *
     * @return executor for processing event notifications on this model,
     *         or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public Executor getExecutor() {
        return subscription.getExecutor();
    }

    /**
     * Changes executor for processing event notifications on this model.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     *
     * @param executor executor for processing event notifications on this model,
     *                 or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public void setExecutor(Executor executor) {
        subscription.setExecutor(executor);
    }

    /**
     * Clears subscription symbol and, subsequently, all events in this model.
     * This is a shortcut for <code>{@link #setSymbol(Object) setSymbol}(<b>null</b>)</code>.
     */
    public void clear() {
        setSymbol(null);
    }

    /**
     * Returns model subscription symbol, or {@code null} is not subscribed
     * (this is a default value).
     *
     * @return model subscription symbol.
     */
    public Object getSymbol() {
        return symbol;
    }


    /**
     * Changes symbol for this model to subscribe for.
     *
     * @param symbol model subscription symbol, use {@code null} to unsubscribe.
     */
    public void setSymbol(Object symbol) {
        if (Objects.equals(this.symbol, symbol))
            return;
        if (this.symbol != null) {
            for (int i = 0; i < nSources; i++) {
                sources[i].clearImpl();
            }
            notifyChanged(true);
        }
        this.symbol = symbol;
        if (symbol != null) {
            subscription.setSymbols(symbol);
        } else {
            subscription.clear();
        }
    }

    /**
     * Returns size limit of this model.
     * It is equal to {@link Integer#MAX_VALUE} by default (no limit).
     *
     * @return size limit of this model.
     */
    public int getSizeLimit() {
        return sizeLimit;
    }

    /**
     * Changes size limit of this model. When size limit is exceed, the
     * first entries from this model are dropped.
     *
     * @param sizeLimit size limit of this model.
     * @throws IllegalArgumentException if {@code sizeLimit} is negative.
     */
    public void setSizeLimit(int sizeLimit) {
        if (sizeLimit < 0)
            throw new IllegalArgumentException();
        this.sizeLimit = sizeLimit;
        enforceSizeLimit();
    }

    // ================================== protected method to access the list ==================================

    /**
     * Returns the number of elements in this model.
     * It is limited by {@link #getSizeLimit() sizeLimit} property.
     *
     * @return the number of elements in this model.
     */
    protected int size() {
        // It is O(nSources) now. todo: make it O(log(nSources))
        int size = 0;
        for (int i = 0; i < nSources; i++) {
            size += sources[i].size();
        }
        return size;
    }

    /**
     * Returns the element at the specified position in this model.
     *
     * @param index index of the element to return.
     * @return the element at the specified position in this model.
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (<tt>index &lt; 0 || index &gt;= size()</tt>).
     */
    protected E get(int index) {
        // It is O(nSources) now. todo: make it O(log(nSources))
        if (index < 0)
            throw new IndexOutOfBoundsException();
        int localIndex = index;
        for (int sourceIndex = 0; sourceIndex < nSources; sourceIndex++) {
            Source source = sources[sourceIndex];
            int localSize = source.size();
            if (localIndex < localSize)
                return source.get(localIndex).value;
            localIndex -= localSize;
        }
        throw new IndexOutOfBoundsException();
    }

    /**
     * Returns a list iterator over the elements in this model (in proper sequence).
     *
     * @return a list iterator over the elements in this model (in proper sequence).
     */
    @SuppressWarnings("unchecked")
    protected ListIterator<E> listIterator() {
        return (ListIterator<E>) new Itr(false, 0, 0, 0);
    }

    /**
     * Returns a list iterator over the elements in this model (in proper
     * sequence), starting at the specified position in the model.
     *
     * @param index index of the first element to be returned from the
     *              list iterator (by a call to {@link ListIterator#next next}).
     * @return a list iterator over the elements in this model (in proper
     *         sequence), starting at the specified position in the model.
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   ({@code index < 0 || index > size()}).
     */
    @SuppressWarnings("unchecked")
    protected ListIterator<E> listIterator(int index) {
        // It is O(nSources) now. todo: make it O(log(nSources))
        if (index < 0)
            throw new IndexOutOfBoundsException();
        int localIndex = index;
        for (int sourceIndex = 0; sourceIndex < nSources; sourceIndex++) {
            Source source = sources[sourceIndex];
            int localSize = source.size();
            if (localIndex < localSize)
                return (ListIterator<E>) new Itr(false, sourceIndex, localIndex, index);
            localIndex -= localSize;
        }
        if (localIndex == 0)
            return new Itr(false, nSources, 0, index);
        throw new IndexOutOfBoundsException();
    }

    /**
     * Returns a list iterator over the <b>entries</b> in this model (in proper sequence).
     *
     * @return a list iterator over the <b>entries</b> in this model (in proper sequence).
     */
    @SuppressWarnings("unchecked")
    protected ListIterator<N> entryListIterator() {
        return (ListIterator<N>) new Itr(true, 0, 0, 0);
    }

    // ================================== protected for override ==================================

    /**
     * Creates new concrete entry to represent an event in this model.
     *
     * @return new entry to represent an event in this model.
     */
    protected abstract N createEntry();

    /**
     * Invoked on the change of this model. All the entries in the {@code changedEntries} list
     * have {@link Entry#isChanged() changed} flag set to {@code true} and the new values of
     * incoming events are available via {@link Entry#getNewValue() Entry.getNewValue} method.
     * The changed flags is cleared
     * after return from this method by {@link Entry#commitChange Entry.commitChange} method, which can also
     * be invoked during this method, if needed.
     *
     * @param changedEntries the list of changed entries.
     */
    protected abstract void modelChanged(List<N> changedEntries);

    /**
     * Returns {@code true} when this event ends the snapshot. This implementations
     * return true when either {@link IndexedEvent#SNAPSHOT_END SNAPSHOT_END} or {@link IndexedEvent#SNAPSHOT_SNIP SNAPSHOT_SNIP}
     * flag is set in {@link IndexedEvent#getEventFlags() eventFlags}.
     */
    protected boolean isSnapshotEnd(E event) {
        return (event.getEventFlags() & (IndexedEvent.SNAPSHOT_END | IndexedEvent.SNAPSHOT_SNIP)) != 0;
    }

    // ================================== private implementation details ==================================

    private void notifyChanged(boolean trimToSize) {
        if (changedEntries.isEmpty())
            return;
        try {
            modelChanged(changedEntries);
        } finally {
            // cleanup internal state even if listeners crash
            for (Entry<E> entry : changedEntries) {
                entry.commitChange();
            }
            changedEntries.clear();
            if (trimToSize)
                changedEntries.trimToSize(); // Don't need memory we held for snapshot -- let GC do its work
        }
    }

    private Source getSourceById(int sourceId) {
        int i = 0;
        int j = nSources;
        while (i < j) {
            int m = (i + j) >> 1;
            Source mSource = sources[m];
            int mSourceId = mSource.sourceId;
            if (sourceId < mSourceId) {
                j = m;
            } else if (sourceId > mSourceId) {
                i = m + 1;
            } else {
                return mSource;
            }
        }
        if (nSources == sources.length)
            sources = Arrays.copyOf(sources, sources.length << 1);
        System.arraycopy(sources, i, sources, i + 1, nSources - i);
        Source source = new Source(sourceId);
        sources[i] = source;
        nSources++;
        return source;
    }

    private boolean processSnapshotAndTx(List<E> events) {
        Object expectedEventSymbol = getSymbol();
        if (expectedEventSymbol == null)
            return false; // not subscribed anymore -- ignore buffered left-overs
        boolean trimToSize = false;
        Source source = null;
        for (E event : events) {
            if (!expectedEventSymbol.equals(event.getEventSymbol()))
                continue;
            int sourceId = event.getSource().id();
            if (source == null || source.sourceId != sourceId)
                source = getSourceById(sourceId);
            trimToSize |= source.processSnapshotAndTx(event);
        }
        return trimToSize;
    }

    private void processEventsNow() {
        Source source = null;
        for (E event : processEventsNow) {
            int sourceId = event.getSource().id();
            if (source == null || source.sourceId != sourceId)
                source = getSourceById(sourceId);
            source.processEvent(event);
        }
        enforceSizeLimit();
    }

    private void enforceSizeLimit() {
        int size = size();
        int sourceIndex = 0;
        while (size > sizeLimit) {
            while (sources[sourceIndex].isEmpty()) {
                sourceIndex++;
            }
            sources[sourceIndex].removeImpl(0);
            size--;
        }
    }

    private class Source extends AbstractIndexedList<N> {
        int sourceId;
        boolean snapshotPart;
        boolean snapshotFull;
        boolean tx;
        final ArrayList<E> pendingEvents = new ArrayList<>();

        Source(int sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        protected long getIndex(N entry) {
            return entry.newValue.getIndex();
        }

        @Override
        protected void removed(N entry) {
            makeChanged(entry);
            entry.newValue = null;
        }

        // returns true when snapshot was processed as a hint to deallocate memory that was used to store snapshot events
        private boolean processSnapshotAndTx(E event) {
            int eventFlags = event.getEventFlags();
            tx = (eventFlags & IndexedEvent.TX_PENDING) != 0; // txPending
            if ((eventFlags & IndexedEvent.SNAPSHOT_BEGIN) != 0) { // snapshotBegin
                snapshotPart = true;
                snapshotFull = false;
                pendingEvents.clear(); // remove any unprocessed leftovers on new snapshot
            }
            // Process snapshot end after snapshot begin was received
            if (snapshotPart && isSnapshotEnd(event)) {
                snapshotPart = false;
                snapshotFull = true;
            }
            if (tx || snapshotPart) {
                // defer processing of this event while snapshot in progress or tx pending
                pendingEvents.add(event);
                return false; // return -- do not process event right now
            }
            // will need to trim temp data structures to size when finished processing snapshot
            boolean trimToSize = snapshotFull;
            // process deferred "snapshot end" by removing previous events from this source
            if (snapshotFull) {
                clearImpl();
                snapshotFull = false;
            }
            // process deferred orders (before processing this event)
            if (!pendingEvents.isEmpty()) {
                processEventsNow.addAll(pendingEvents);
                pendingEvents.clear();
                if (trimToSize)
                    pendingEvents.trimToSize(); // Don't need memory we held for snapshot -- let GC do its work
            }
            // add to process this event right now
            processEventsNow.add(event);
            return trimToSize;
        }

        private void makeChanged(N entry) {
            if (entry.changed)
                return;
            entry.changed = true;
            entry.newValue = entry.value;
            changedEntries.add(entry);
        }

        void processEvent(E event) {
            boolean remove = (event.getEventFlags() & IndexedEvent.REMOVE_EVENT) != 0;
            if (!remove) // potentially adding new element, grow array in advance (if needed) for simplicity
                growIfNeededImpl();
            long index = event.getIndex();
            int a = findIndex(index);
            if (a < 0 && remove)
                return; // nothing to do on remove on non-existing
            // insert new entry if not found
            N entry;
            if (a >= 0) { // found entry -- use
                entry = get(a);
            } else { // not found -- insert new one
                insertImpl(-a - 1, entry = createEntry());
            }
            makeChanged(entry);
            if (remove) {
                removeImpl(a); // remove existing entry ("removed" method will set null value)
            } else {
                event.setEventFlags(0); // cleanup the flags in the stored event
                entry.newValue = event;
            }
        }

    }

    private class Listener implements DXFeedEventListener<E> {
        @Override
        public void eventsReceived(List<E> events) {
            boolean trimToSize = processSnapshotAndTx(events);
            if (!processEventsNow.isEmpty()) {
                processEventsNow();
                processEventsNow.clear();
                if (trimToSize)
                    processEventsNow.trimToSize(); // Don't need memory we held for snapshot -- let GC do its work
            }
            notifyChanged(trimToSize);
        }
    }

    @SuppressWarnings("rawtypes")
    private class Itr implements ListIterator {
        final boolean entryIterator;
        int sourceIndex;
        int localIndex;
        int index;

        Itr(boolean entryIterator, int sourceIndex, int localIndex, int index) {
            this.entryIterator = entryIterator;
            this.sourceIndex = sourceIndex;
            this.localIndex = localIndex;
            this.index = index;
            initNext();
        }

        private void initNext() {
            while (sourceIndex < nSources && localIndex >= sources[sourceIndex].size()) {
                sourceIndex++;
                localIndex = 0;
            }
        }

        private void initPrevious() {
            while (localIndex < 0 && sourceIndex > 0) {
                sourceIndex--;
                localIndex = sources[sourceIndex].size() - 1;
            }
        }

        private Object current() {
            Source source = sources[sourceIndex];
            Entry<E> entry = source.get(localIndex);
            return entryIterator ? entry : entry.value;
        }

        @Override
        public boolean hasNext() {
            return sourceIndex < nSources;
        }

        @Override
        public Object next() {
            if (sourceIndex >= nSources)
                throw new NoSuchElementException();
            Object result = current();
            localIndex++;
            index++;
            initNext();
            return result;
        }

        @Override
        public boolean hasPrevious() {
            return index > 0;
        }

        @Override
        public Object previous() {
            if (index <= 0)
                throw new NoSuchElementException();
            localIndex--;
            index--;
            initPrevious();
            return current();
        }

        @Override
        public int nextIndex() {
            return index;
        }

        @Override
        public int previousIndex() {
            return index - 1;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(Object o) {
            throw new UnsupportedOperationException();
        }
    }

    // ================================== public entry class ==================================

    /**
     * Represents an internal entry in the model. It can be extended to add additional state to the entry.
     *
     * @param <V> the type of events.
     * @see AbstractIndexedEventModel#createEntry()
     */
    public static class Entry<V> {
        /**
         * Current value of the entry, before the most recent change.
         * {@code null} if the element was removed from the model.
         *
         * @see AbstractIndexedEventModel#modelChanged(List)
         */
        V value;

        /**
         * New value of the entry, after the most recent change,
         * {@code null} if the element is removed from the model.
         * This value is available (initialized) only when {@link #changed} is {@code true}.
         *
         * @see AbstractIndexedEventModel#modelChanged(List)
         */
        V newValue;

        /**
         * Whether the value was updated and in the most recent change.
         */
        boolean changed;

        /**
         * Constructs entry with {@code null} value.
         */
        public Entry() {}

        /**
         * Constructs entry with a specified value.
         *
         * @param value the value.
         */
        public Entry(V value) {
            this.value = value;
            this.newValue = value;
        }

        /**
         * Returns current values of the entry.
         * During {@link AbstractIndexedEventModel#modelChanged(List) AbstractIndexedEventModel.modelChanged}
         * it is the value before the change, until {@link #commitChange()} method is invoked.
         *
         * @return current values of the entry.
         */
        public V getValue() {
            return value;
        }

        /**
         * Returns new value of the entry, after the most recent change,
         * {@code null} if the element is removed from the model.
         * It can differ from {@link #getValue() value} only during
         * {@link AbstractIndexedEventModel#modelChanged(List) AbstractIndexedEventModel.modelChanged} method
         * invocation and until {@link #commitChange()} method is invoked.
         *
         * @return new value of the entry, after the most recent change.
         */
        public V getNewValue() {
            return newValue;
        }

        /**
         * Returns {@code true} if the value was updated and in the most recent change.
         * It is set to {@code true} only during
         * {@link AbstractIndexedEventModel#modelChanged(List) AbstractIndexedEventModel.modelChanged} method
         * invocation and until {@link #commitChange()} method is invoked.
         *
         * @return {@code true} if the value was updated and in the most recent change.
         */
        public boolean isChanged() {
            return changed;
        }

        /**
         * Commits the most recent change by copying {@link #getNewValue() newValue} to current
         * {@link #getValue() value} and clearing {@link #isChanged() changed} flag, if
         * it was not cleared yet.
         */
        public void commitChange() {
            if (!changed)
                return;
            value = newValue;
            changed = false;
        }
    }
}
