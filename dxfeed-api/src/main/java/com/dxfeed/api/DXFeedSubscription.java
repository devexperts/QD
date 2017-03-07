/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.devexperts.io.IOUtil;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.api.osub.*;
import com.dxfeed.event.EventType;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.MarketEvent;

/**
 * Subscription for a set of symbols and event types.
 *
 * <h3>Symbols and event types</h3>
 *
 * Symbols are represented by objects, simple keys are represented by {@link String} objects,
 * complex ones use specialized object as described in the corresponding event types.
 * However, every symbol has a unique string representation that can be used.
 * Each event is represented by a concrete instance of
 * event class. Event type is represented by a class literal. For example,
 * <ul>
 * <li>"SPY" is a symbol for SPDR S&amp;P 500 ETF,
 * <li>{@code Quote.class} is an event type for a best market quote.
 * </ul>
 * Events can be represented by any classes that annotated by an inheritable annotation {@link EventType}.
 * Common types of market events extend {@link MarketEvent MarketEvent} class
 * and reside in <a href="../event/market/package-summary.html"><code>com.dxfeed.event.market</code></a> package.
 *
 * <p> The set of subscribed symbols and the set of subscribed event types are maintained separately.
 * The subscription is considered to be interested in the cross-product of these sets. That is, subscription
 * is interested in any event whose type is in the set of subscribed event types and whose symbol is in the
 * set of subscribed symbols.
 *
 * <p> The set of event types is specified when subscription is created and cannot be changed afterward.
 * The set of symbols can be modified with
 * {@link #setSymbols(Collection) setSymbols}, {@link #addSymbols(Collection) addSymbols},
 * {@link #removeSymbols(Collection) removeSymbols}, and {@link #clear() clear} methods.
 * Each of {@code xxxSymbols} method exists in two version. One version accepts a {@link Collection}
 * of symbols and it recommended for bulk modifications of symbol set. The other version accepts
 * varargs arrays of symbols and is provided as a convenience method to set/add/remove one or few
 * symbols.
 *
 * <p>Symbols in a set are compared using their {@link Object#equals(Object) equals} method. All symbol objects
 * must properly support {@link Object#hashCode() hashCode} method. A set of symbols cannot contain two equal symbols.
 * If a symbol that is {@link #addSymbols(Collection) added} to a set of symbols is equal to the other one,
 * then the old symbol is removed from the set and the new symbol instance is retained. However,
 * the {@link ObservableSubscriptionChangeListener} (see below) in this case is notified on the change of
 * subscription only when new symbol object implements {@link FilteredSubscriptionSymbol} marker interface.
 *
 * <h3>Special symbol types and restrictions</h3>
 *
 * A single object {@link WildcardSymbol#ALL WildcardSymbol.ALL} represents subscription to all possible symbols
 * and have the effect of subscribing to all of them. See {@link WildcardSymbol} for more details. It
 * is always represented by a separate class (instead of a usual {@link String}) in order
 * to avoid potential resource consumption problems that may stem from an accidental subscription via wildcard.
 * All string symbols that start with {@link WildcardSymbol#RESERVED_PREFIX WildcardSymbol.RESERVED_PREFIX} ("*")
 * are reserved for the same reason. Subscription to the corresponding string does not have any effect
 * (no events will be received). Thus, it is safe to add user-specified strings to the subscription via
 * {@link #addSymbols(Object...) addSymbols} method without any prior validation of the user input.
 *
 * <p><b>NOTE:</b> Wildcard subscription can create extremely high network and CPU load for certain kinds of
 * high-frequency events like quotes. It requires a special arrangement on the side of upstream data provider and
 * is disabled by default in upstream feed configuration. Make that sure you have adequate resources and understand
 * the impact before using it. It can be used for low-frequency events only (like Forex quotes), because each instance
 * of {@code DXFeedSubscription} processes events in a single thread and there is no provision to load-balance wildcard
 * subscription amongst multiple threads.
 *
 * Contact your data provider for the corresponding configuration arrangement if needed.
 * <p> A special class of {@link TimeSeriesSubscriptionSymbol} symbols is used internally to represent a subscription
 * to time-series of events. Two instances of {@link TimeSeriesSubscriptionSymbol} objects are
 * {@link TimeSeriesSubscriptionSymbol#equals(Object) equal} when their
 * {@link TimeSeriesSubscriptionSymbol#getEventSymbol() underlying symbols} are equal, thus only one, most recently
 * added, instance of {@link TimeSeriesSubscriptionSymbol} will be kept in the set of subscribed symbols.
 *
 * <p>It is recommended to use {@link DXFeedTimeSeriesSubscription} convenience class for time-series subscriptions.
 *
 * <p>{@link Candle} events use {@link CandleSymbol} class to represent the complex structure of the candle symbol
 * and its attributes. However, both {@link String} and {@link CandleSymbol} objects can be used in subscription.
 * The corresponding strings will be converted to {@link CandleSymbol} using its {@link CandleSymbol#valueOf(String) valueOf}
 * method. Do not mix {@link String} and {@link CandleSymbol} subscription in a single {@code DXFeedSubscription} instance.
 *
 * <h3>Subscription listeners</h3>
 *
 * This class keeps a list of {@link DXFeedEventListener} instances that are notified on any events.
 * Event listeners are added with {@link #addEventListener(DXFeedEventListener) addEventListener} method.
 * <b>Event listeners must be installed before changing the set of subscribed symbols</b>.
 * When the set of subscribed symbols changes all registered event listeners receive update on the
 * last events for all newly added symbols.
 *
 * <p> This class keeps a set of {@link ObservableSubscriptionChangeListener} instances that are notified on any
 * change in subscription. These listeners are installed by {@link DXFeed} to keep
 * track of the subscription state and communicate subscription upstream to data providers.
 *
 * <h3>Detached and attached subscriptions</h3>
 *
 * Subscription that is created via constructor is <i>detached</i>. It is not attached
 * to any feed and thus it does not actually receive any events. Detached subscription still maintains
 * a set of symbols and a list of event listeners. Detached subscription can be attached to
 * any feed with {@link DXFeed#attachSubscription DXFeed.attachSubscription} method.
 *
 * <p> Subscription that is created via {@link DXFeed#createSubscription DXFeed.createSubscription}
 * is <i>attached</i> to the
 * corresponding feed. The feed tracks all changes in subscription by installing
 * {@link ObservableSubscriptionChangeListener} and invokes
 * {@link #processEvents processEvents} for all received events.
 * Subscription can be detached from the feed with
 * {@link DXFeed#detachSubscription DXFeed.detachSubscription} method.
 *
 * <p> Subscription can be attached to multiple feeds at the same time. In this case it receives events from
 * all feeds but there is no way distinguish which feed the corresponding event came from.
 *
 * <h3>Resource management and closed subscriptions</h3>
 *
 * Attached subscription is a potential memory leak. If the pointer to attached subscription is lost, then there is
 * no way to detach this subscription from the feed and the subscription will not be reclaimed by the garbage collector
 * as long as the corresponding feed is still used. Detached subscriptions can be reclaimed by the garbage collector,
 * but detaching subscription requires knowing the pointer to the feed at the place of the call, which is not always convenient.
 *
 * <p> The convenient way to detach subscription from the feed is to call its {@link #close close} method. Closed subscription
 * becomes permanently detached from all feeds, removes all its listeners and is guaranteed to be reclaimable by the garbage
 * collector as soon as all external references to it are cleared.
 *
 * <p> The other way is to close an associated feed. This cannot be done via a {@link DXFeed} instance, but it can be done
 * indirectly by closing the associated {@link DXEndpoint endpoint}.
 *
 * <h3>Serialization</h3>
 *
 * This class's serialized state includes only serializable listeners. {@link ObservableSubscriptionChangeListener}
 * that is installed by {@link DXFeed} when this subscription is attached is not serializable.
 * Thus, freshly deserialized instance of this class will be <i>detached</i>. It has to be attached
 * to the feed after deserialization in order for it to start receiving events.
 *
 * <h3><a name="threadsAndLocksSection">Threads and locks</a></h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * <p> Installed {@link DXFeedEventListener} instances are invoked from a separate thread via the executor.
 * Default executor for all subscriptions is configured with {@link DXEndpoint#executor(Executor) DXEndpoint.executor}
 * method. Each subscription can individually override its executor with {@link #setExecutor(Executor) setExecutor}
 * method.
 *
 * <p> Event listeners are invoked in a serial manner with respect to a given DXFeedSubscription instance.
 * That is, next event notification will not be performed until
 * {@link DXFeedEventListener#eventsReceived(List) DXFeedEventListener.eventsReceived} method for the previous
 * notification completes. It guarantees that the order of events in a given instance of {@code DXFeedSubscription}
 * is preserved.
 *
 * <p> This requirement on ordering limits concurrency of event processing. Effectively, each
 * subscription can use at most one CPU core to process its events. If there is a need to simultaneously
 * process events on a large number of symbols and this event processing is so resource-consuming, that
 * one CPU core is not enough to process all events in real-time, then it is advised to split symbols between multiple
 * {@code DXFeedSubscription} instances. If event processing is mostly CPU-bound, then the good rule of thumb
 * is to have as many {@code DXFeedSubscription} instances as there are CPU cores in the system.
 *
 * <p> However, multiple tasks can get submitted to the executor at the same time. In the current implementation,
 * at most two tasks are submitted at any time if
 * {@link DXEndpoint#DXFEED_AGGREGATION_PERIOD_PROPERTY DXFEED_AGGREGATION_PERIOD_PROPERTY} is used.
 * One task for immediate processing of data snapshots via {@link Executor#execute(Runnable) Executor.execute} method
 * and another task for delayed processing of data updates via
 * {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit) ScheduledExecutorService.schedule} method
 * if the executor implements {@link ScheduledExecutorService} interface.
 * At most one task is submitted at any time if this property is not used.
 *
 * <p> Installed {@link ObservableSubscriptionChangeListener} instances are notified on symbol set changes
 * while holding the lock on this subscription and in the same thread that changed the set of subscribed symbols
 * in this subscription.
 *
 * <p>Custom executor can be used by backend applications that do not need to immediately retrieve a collection
 * of events, but want to poll for new events at a later time, for example, from inside of a servlet request.
 * The following code pattern is suggested in this case to initialize subscription's executor:
 *
 * <pre><tt>
 * {@link ConcurrentLinkedQueue}&lt;{@link Runnable Runnable}&gt; taskQueue = <b>new</b> ConcurrentLinkedQueue&lt;&gt;();
 * subscription.{@link #setExecutor(Executor) setExecutor}(<b>new</b> {@link Executor}() {
 *     <b>public void</b> execute({@link Runnable Runnable} task) {
 *         taskQueue.{@link ConcurrentLinkedQueue#add add}(task);
 *     }
 * });
 * subscription.{@link #addEventListener(DXFeedEventListener) addEventListener}(...);
 * </tt></pre>
 *
 * When there is a time to poll for new events, the following code can be used:
 *
 * <pre><tt>
 * {@link Runnable Runnable} task;
 * <b>while</b> ((task = taskQueue.{@link ConcurrentLinkedQueue#poll poll}()) != <b>null</b>)
 *     task.run();
 * </tt></pre>
 *
 * and event listener's {@link DXFeedEventListener#eventsReceived(List) eventsReceived} method will be invoked
 * in this thread from inside of {@code task.run()} invocation.
 *
 * <p> This approach has a clear advantage with {@link LastingEvent LastingEvent} types.
 * If the above polling code is delayed or is executed only periodically, then incoming events have a chance
 * to get conflated and listener will receive only on the most recent event updates for processing.
 *
 * @param <E> the type of events.
  */
public class DXFeedSubscription<E> implements Serializable, ObservableSubscription<E> {
    private static final long serialVersionUID = 0;

    // closed state
    private volatile boolean closed;

    // initialized during construction in init() method
    private transient EventTypeSet eventTypeSet;
    private transient IndexerFunction<Object, Object> eventSymbolIndexer;
    private transient IndexedSet<Object, Object> symbols;
    private transient ObservableSubscriptionChangeListener changeListeners;
    private transient volatile Executor executor;
    private transient volatile DXFeedEventListener<E> eventListeners; // fires without synchronization

    // initialized on first use
    private transient Set<?> undecoratedSymbols;

    /**
     * Creates <i>detached</i> subscription for a single event type.
     *
     * @param eventType the event type.
     * @throws NullPointerException if event type is null.
     */
    public DXFeedSubscription(Class<? extends E> eventType) {
        init(eventType);
    }

    /**
     * Creates <i>detached</i> subscription for the given list of event types.
     *
     * @param eventTypes the list of event types.
     * @throws IllegalArgumentException if the list of event types is empty.
     * @throws NullPointerException if any event type is null.
     */
    @SafeVarargs
    public DXFeedSubscription(Class<? extends E>... eventTypes) {
        init(eventTypes);
    }

    /**
     * Attaches subscription to the specified feed.
     *
     * @param feed feed to attach to.
     */
    public void attach(DXFeed feed) {
        feed.attachSubscription(this);
    }

    /**
     * Detaches subscription from the specified feed.
     *
     * @param feed feed to detach from.
     */
    public void detach(DXFeed feed) {
        feed.detachSubscription(this);
    }

    /**
     * Returns <code>true</code> if this subscription is closed.
     *
     * @see #close
     */
    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes this subscription and makes it <i>permanently detached</i>.
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances by invoking
     * {@link ObservableSubscriptionChangeListener#subscriptionClosed subscriptionClosed}
     * while holding the lock for this subscription. This method clears lists of all installed
     * event listeners and subscription change listeners and makes sure that no more listeners
     * can be added.
     *
     * <p> This method ensures that subscription can be safely garbage-collected when all outside references
     * to it are lost.
     */
    public synchronized void close() {
        if (closed)
            return;
        closed = true;
        eventListeners = null;
        if (changeListeners != null) {
            changeListeners.subscriptionClosed();
            changeListeners = null;
        }
    }

    /**
     * Returns a set of subscribed event types. The resulting set cannot be modified.
     */
    @Override
    public Set<Class<? extends E>> getEventTypes() {
        return eventTypeSet.asSet();
    }

    /**
     * Returns <code>true</code> if this subscription contains the corresponding event type.
     * @see #getEventTypes()
     */
    @Override
    public boolean containsEventType(Class<?> eventType) {
        return eventTypeSet.contains(eventType);
    }

    /**
     * Clears the set of subscribed symbols. This implementation calls
     * <pre><tt>{@link #setSymbols setSymbols}(Collections.EMPTY_LIST)</tt></pre>
     */
    public void clear() {
        setSymbols(Collections.EMPTY_LIST);
    }

    /**
     * Returns a set of subscribed symbols. The resulting set cannot be modified. The contents of the resulting set
     * are undefined if the set of symbols is changed after invocation of this method, but the resulting set is
     * safe for concurrent reads from any threads. The resulting set maybe either a snapshot of the set of
     * the subscribed symbols at the time of invocation or a weakly consistent view of the set.
     */
    public Set<?> getSymbols() {
        if (undecoratedSymbols == null)
            undecoratedSymbols = new UndecoratedSymbols();
        return undecoratedSymbols;
    }

    /**
     * Changes the set of subscribed symbols so that it contains just the symbols from the specified collection.
     * To conveniently set subscription for just one or few symbols you can use
     * {@link #setSymbols(Object...) setSymbols(Object... symbols)} method.
     * All registered event listeners will receive update on the last events for all
     * newly added symbols.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method,
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the collection of symbols.
     */
    public void setSymbols(Collection<?> symbols) {
        setSymbolsImpl(decorateSymbols(symbols));
    }

    /**
     * Changes the set of subscribed symbols so that it contains just the symbols from the specified array.
     * This is a convenience method to set subscription to one or few symbols at a time.
     * When setting subscription to multiple
     * symbols at once it is preferable to use
     * {@link #setSymbols(Collection) setSymbols(Collection&lt;?&gt; symbols)} method.
     * All registered event listeners will receive update on the last events for all
     * newly added symbols.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method,
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the array of symbols.
     */
    public void setSymbols(Object... symbols) {
        setSymbolsImpl(decorateSymbols(symbols));
    }

    /**
     * Adds the specified collection of symbols to the set of subscribed symbols.
     * To conveniently add one or few symbols you can use
     * {@link #addSymbols(Object...) addSymbols(Object... symbols)} method.
     * All registered event listeners will receive update on the last events for all
     * newly added symbols.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method,
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the collection of symbols.
     */
    public void addSymbols(Collection<?> symbols) {
        if (symbols.isEmpty())
            return;
        addSymbolsImpl(decorateSymbols(symbols));
    }

    /**
     * Adds the specified array of symbols to the set of subscribed symbols.
     * This is a convenience method to subscribe to one or few symbols at a time.
     * When subscribing to multiple
     * symbols at once it is preferable to use
     * {@link #addSymbols(Collection) addSymbols(Collection&lt;?&gt; symbols)} method.
     * All registered event listeners will receive update on the last events for all
     * newly added symbols.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the array of symbols.
     */
    public void addSymbols(Object... symbols) {
        if (symbols.length == 0)
            return; // no symbols -- nothing to do
        if (symbols.length == 1)
            addSymbolImpl(decorateSymbol(symbols[0])); // shortcut to optimized one symbol case
        else
            addSymbolsImpl(decorateSymbols(symbols)); // multiple symbols
    }

    /**
     * Adds the specified symbol to the set of subscribed symbols.
     * This is a convenience method to subscribe to one symbol at a time that
     * has a return fast-path for a case when the symbol is already in the set.
     * When subscribing to multiple
     * symbols at once it is preferable to use
     * {@link #addSymbols(Collection) addSymbols(Collection&lt;?&gt; symbols)} method.
     * All registered event listeners will receive update on the last events for all
     * newly added symbols.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbol the symbol.
     */
    public void addSymbols(Object symbol) {
        addSymbolImpl(decorateSymbol(symbol));
    }

    /**
     * Removes the specified collection of symbols from the set of subscribed symbols.
     * To conveniently remove one or few symbols you can use
     * {@link #removeSymbols(Object...) removeSymbols(Object... symbols)} method.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     * <p>
     * This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method,
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the collection of symbols.
     */
    public void removeSymbols(Collection<?> symbols) {
        if (symbols.isEmpty())
            return;
        removeSymbolsImpl(decorateSymbols(symbols));
    }

    /**
     * Removes the specified array of symbols from the set of subscribed symbols.
     * This is a convenience method to remove one or few symbols at a time.
     * When removing multiple
     * symbols at once it is preferable to use
     * {@link #removeSymbols(Collection) removeSymbols(Collection&lt;?&gt; symbols)} method.
     *
     * <h3>Implementation notes</h3>
     *
     * This method notifies
     * all installed {@link ObservableSubscriptionChangeListener} instances of any resulting changes in the set of
     * subscribed symbols while holding the lock for this subscription.
     *
     * <p> This implementation decorates symbols via protected {@link #decorateSymbol(Object)} method,
     * that can be overridden in subclasses of {@code DXFeedSubscription}. Installed
     * {@code ObservableSubscriptionChangeListener} instances receive decorated symbols.
     *
     * @param symbols the array of symbols.
     */
    public void removeSymbols(Object... symbols) {
        if (symbols.length == 0)
            return;
        removeSymbolsImpl(decorateSymbols(symbols));
    }

    /**
     * Returns executor for processing event notifications on this subscription.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     * @return executor for processing event notifications on this subscription,
     *         or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Changes executor for processing event notifications on this subscription.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     * @param executor executor for processing event notifications on this subscription,
     *         or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Adds listener for events.
     * Event lister can be added only when subscription is not producing any events.
     * The subscription must be either empty
     * (its set of {@link #getSymbols() symbols} is empty) or not {@link #attach(DXFeed) attached} to any feed
     * (its set of change listeners is empty).
     *
     * This method does nothing if this subscription is closed.
     * @param listener the event listener.
     * @throws NullPointerException if listener is null.
     * @throws IllegalStateException if subscription is attached and is not empty.
     */
    public synchronized void addEventListener(DXFeedEventListener<E> listener) {
        if (listener == null)
            throw new NullPointerException();
        if (closed)
            return;
        if (changeListeners != null && !symbols.isEmpty())
            throw new IllegalStateException("Cannot add event listener to non-empty attached subscription. Add event listeners first");
        if (eventListeners == null)
            eventListeners = listener;
        else
            eventListeners = new EventListeners<>(addListener(eventListeners, listener));
    }

    /**
     * Removes listener for events.
     * @param listener the event listener.
     * @throws NullPointerException if listener is null.
     */
    public synchronized void removeEventListener(DXFeedEventListener<E> listener) {
        if (listener == null)
            throw new NullPointerException();
        if (eventListeners == null)
            return;
        if (eventListeners == listener)
            eventListeners = null;
        else if (eventListeners instanceof EventListeners)
            eventListeners = ((EventListeners<E>) eventListeners).remove(listener);
    }

    /**
     * Adds subscription change listener. This method does nothing if the given listener is already
     * installed as subscription change listener for this subscription or if subscription is closed.
     * Otherwise, it installs the
     * corresponding listener and immediately invokes {@link ObservableSubscriptionChangeListener#symbolsAdded}
     * on the given listener while holding the lock for this
     * subscription. This way the given listener synchronously receives existing subscription state and and
     * is synchronously notified on all changes in subscription afterwards.
     *
     * <p>Whenever a symbol in a set of subscribed symbols is replaced by the other symbol that is
     * {@link #equals(Object) equal} to the old one, the decision on whether to notify installed listeners
     * about the change is based on the result of
     * {@link #shallNotifyOnSymbolUpdate(Object, Object) shallNotifyOnSymbolUpdate} method.
     *
     * @param listener the subscription change listener.
     * @throws NullPointerException if listener is null.
     */
    @Override
    public synchronized void addChangeListener(ObservableSubscriptionChangeListener listener) {
        if (listener == null)
            throw new NullPointerException();
        if (closed)
            return;
        if (changeListeners == null)
            changeListeners = listener;
        else if (changeListeners == listener)
            return;
        else {
            if (changeListeners instanceof ChangeListeners && findListener((ChangeListeners) changeListeners, listener) >= 0)
                return;
            changeListeners = new ChangeListeners(addListener(changeListeners, listener));
        }
        // notify new listener on a set of symbols
        if (!symbols.isEmpty())
            listener.symbolsAdded(symbols);
    }

    /**
     * Removes subscription change listener. This method does nothing if the given listener was not
     * installed or was already removed as subscription change listener for this subscription.
     * Otherwise it removes the corresponding listener and immediately invokes
     * {@link ObservableSubscriptionChangeListener#subscriptionClosed} on the given listener while
     * holding the lock for this subscription.
     *
     * @param listener the subscription change listener.
     * @throws NullPointerException if listener is null.
     */
    @Override
    public synchronized void removeChangeListener(ObservableSubscriptionChangeListener listener) {
        if (listener == null)
            throw new NullPointerException();
        if (changeListeners == null)
            return;
        if (changeListeners.equals(listener))
            changeListeners = null;
        else if (changeListeners instanceof ChangeListeners) {
            ChangeListeners oldListeners = (ChangeListeners) changeListeners;
            changeListeners = oldListeners.remove(listener);
            if (changeListeners == oldListeners)
                return;
        } else
            return;
        // notify listener on close
        listener.subscriptionClosed();
    }

    //----------------------- package-private API for DXFeed -----------------------

    /**
     * Processes received events. This methods invokes {@link DXFeedEventListener#eventsReceived} on all installed
     * event listeners. This is a package-private method for use by {@link DXFeed} class only.
     * @param events the list of received events.
     */
    void processEvents(List<E> events) {
        DXFeedEventListener<E> eventListeners = this.eventListeners; // atomic volatile read
        if (eventListeners != null)
            eventListeners.eventsReceived(events);
    }

    //----------------------- protected API for subclasses -----------------------

    /**
     * Decorates the specified symbol after it was received from the {@code DXFeedSubscription} client code
     * before it goes to installed {@link ObservableSubscriptionChangeListener} instances.
     * This method can be overridden in subclasses. See {@link DXFeedTimeSeriesSubscription} for an example.
     * This implementation throws {@code NullPointerException} if {@code symbol} is null
     * or returns {@code symbol} otherwise.
     *
     * @param symbol the symbol to decorate.
     * @throws NullPointerException if symbol is null.
     * @return decorated symbol
     */
    protected Object decorateSymbol(Object symbol) {
        if (symbol == null)
            throw new NullPointerException();
        return symbol;
    }

    /**
     * Undoes the decoration of the specified symbol doing the reverse operation to {@link #decorateSymbol(Object)}.
     * This method can be overridden in subclasses. See {@link DXFeedTimeSeriesSubscription} for an example.
     * This implementation throws {@code NullPointerException} if {@code symbol} is null
     * or returns {@code symbol} otherwise.
     *
     * @param symbol the symbol to undecorate.
     * @throws NullPointerException if symbol is null.
     * @return undecorated symbol
     */
    protected Object undecorateSymbol(Object symbol) {
        if (symbol == null)
            throw new NullPointerException();
        return symbol;
    }

    //----------------------- private implementation details -----------------------

    private static void writeCompactCollection(ObjectOutput out, Collection<?> collection) throws IOException {
        IOUtil.writeCompactInt(out, collection.size());
        for (Object o : collection)
            out.writeObject(o);
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] readCompactCollection(ObjectInput in) throws IOException, ClassNotFoundException {
        int n = IOUtil.readCompactInt(in);
        T[] a = (T[]) new Object[n];
        for (int i = 0; i < n; i++)
            a[i] = (T) in.readObject();
        return a;
    }

    private void init(Class<? extends E> eventType) {
        eventTypeSet = new SingletonEventTypeSet(eventType);
        initRest();
    }

    @SafeVarargs
    private final void init(Class<? extends E>... eventTypes) {
        if (eventTypes.length == 1) {
            init(eventTypes[0]);
        } else {
            this.eventTypeSet = new MultipleEventTypes(eventTypes);
            initRest();
        }
    }

    @SuppressWarnings("unchecked")
    private void initRest() {
        eventSymbolIndexer = getClass() == DXFeedSubscription.class ? IndexerFunction.DEFAULT : this::undecorateSymbol;
        symbols = IndexedSet.create(eventSymbolIndexer);
    }

    private IndexedSet<Object, Object> decorateSymbols(Collection<?> symbols) {
        return symbols.stream().map(this::decorateSymbol).collect(IndexedSet.collector(eventSymbolIndexer));
    }

    private IndexedSet<Object, Object> decorateSymbols(Object... symbols) {
        return Arrays.stream(symbols).map(this::decorateSymbol).collect(IndexedSet.collector(eventSymbolIndexer));
    }

    private synchronized void setSymbolsImpl(IndexedSet<Object, Object> added) {
        IndexedSet<Object, Object> removed = IndexedSet.create(eventSymbolIndexer);
        for (Iterator<Object> it = symbols.iterator(); it.hasNext(); ) {
            Object oldSymbol = it.next();
            if (!added.containsValue(oldSymbol)) {
                it.remove();
                removed.add(oldSymbol);
            }
        }
        addAndNotify(added, removed);
    }

    private synchronized void addSymbolsImpl(IndexedSet<Object, Object> added) {
        addAndNotify(added, null);
    }

    private void addAndNotify(IndexedSet<Object, Object> added, IndexedSet<Object, Object> removed) {
        // was there and "the same" -- don't process it
        added.removeIf(o -> !putSymbol(o));
        if (added.isEmpty() && (removed == null || removed.isEmpty()))
            return;
        if (changeListeners != null) {
            if (removed != null && !removed.isEmpty())
                changeListeners.symbolsRemoved(removed);
            if (!added.isEmpty())
                changeListeners.symbolsAdded(added);
        }
    }

    private synchronized void addSymbolImpl(Object symbol) {
        if (!putSymbol(symbol))
            return; // was there and "the same" -- nothing to do
        if (changeListeners != null)
            changeListeners.symbolsAdded(Collections.singleton(symbol));
    }

    // returns false when "the same" symbol was already in the symbols set
    private boolean putSymbol(Object symbol) {
        Object oldSymbol = symbols.put(symbol);
        return shallNotifyOnSymbolUpdate(symbol, oldSymbol);
    }

    /**
     * Compares newly added symbol with the old one that was present before for
     * the purpose of notifying {@link #addChangeListener(ObservableSubscriptionChangeListener) installed}
     * {@link ObservableSubscriptionChangeListener} about the change.
     * This method returns {@code false} if the new symbol shall be considered <em>the same</em> one and
     * no notification is needed. The attached {@link ObservableSubscriptionChangeListener listeners} get
     * {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded} notification for all
     * symbols that this method returns {@code true} for.
     *
     * <p> This implementation compares instances of {@link FilteredSubscriptionSymbol} by their reference
     * (this implementation returns {@code true} when {@code symbol != oldSymbol}), because their equals and hashCode do
     * not take input account their filter part. Notification for all other types of symbols (like {@link String}) is
     * optimized away (this implementation returns {@code true} only when {@code oldSymbol == null}).
     *
     * @param symbol the recently added symbol to this subscription (not null).
     * @param oldSymbol the previous symbol from the set of symbol based on equal/hashCode search in a hash set or
     *                  {@code null} if it did not present before.
     * @return {@code true} if listeners shall be notified on the change in the set of subscribed symbols.
     */
    protected boolean shallNotifyOnSymbolUpdate(@Nonnull Object symbol, @Nullable Object oldSymbol) {
        return symbol instanceof FilteredSubscriptionSymbol ? symbol != oldSymbol : oldSymbol == null;
    }

    private synchronized void removeSymbolsImpl(IndexedSet<Object, Object> removed) {
        // remove symbols by key (regardless of identify)
        for (Iterator<Object> it = removed.concurrentIterator(); it.hasNext(); ) {
            Object symbol = it.next();
            Object oldSymbol = symbols.removeValue(symbol);
            if (oldSymbol == null)
                removed.remove(symbol); // was not there -- nothing to remove
            else if (oldSymbol != symbol)
                removed.add(oldSymbol); // replace with actually removed identity
        }
        if (removed.isEmpty())
            return;
        if (changeListeners != null)
            changeListeners.symbolsRemoved(removed);
    }

    private synchronized void writeObject(ObjectOutputStream out) throws IOException {
        writeCompactCollection(out, eventTypeSet.asSet());
        writeCompactCollection(out, symbols);
        writeCompactCollection(out, getSerializable(eventListeners));
        writeCompactCollection(out, getSerializable(changeListeners));
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int n = IOUtil.readCompactInt(in);
        Class<? extends E>[] eventTypes = (Class<? extends E>[]) new Class<?>[n];
        for (int i = 0; i < n; i++)
            eventTypes[i] = (Class<? extends E>) in.readObject();
        init(eventTypes);
        Collections.addAll(symbols, readCompactCollection(in));
        eventListeners = new EventListeners<E>(DXFeedSubscription.<EventListeners<E>>readCompactCollection(in)).simplify();
        changeListeners = new ChangeListeners(DXFeedSubscription.<ObservableSubscriptionChangeListener>readCompactCollection(in)).simplify();
    }

    private abstract class EventTypeSet {
        protected EventTypeSet() {}

        abstract boolean contains(Class<?> eventType);
        abstract Set<Class<? extends E>> asSet();
    }

    private class SingletonEventTypeSet extends EventTypeSet {
        private final Class<? extends E> eventType;
        private Set<Class<? extends E>> set;

        SingletonEventTypeSet(Class<? extends E> eventType) {
            if (eventType == null)
                throw new NullPointerException();
            this.eventType = eventType;
        }

        @Override
        boolean contains(Class<?> eventType) {
            return this.eventType == eventType;
        }

        @Override
        Set<Class<? extends E>> asSet() {
            if (set == null)
                set = Collections.singleton(eventType);
            return set;
        }
    }

    private class MultipleEventTypes extends EventTypeSet {
        private final Class<? extends E>[] eventTypes;
        private Set<Class<? extends E>> set;

        @SuppressWarnings("unchecked")
        MultipleEventTypes(Class<? extends E>... eventTypes) {
            if (eventTypes.length == 0)
                throw new IllegalArgumentException();
            for (Class<? extends E> eventType : eventTypes)
                if (eventType == null)
                    throw new NullPointerException();
            int n = eventTypes.length;
            this.eventTypes = (Class<E>[]) new Class<?>[n];
            System.arraycopy(eventTypes, 0, this.eventTypes, 0, n);
        }

        @Override
        boolean contains(Class<?> eventType) {
            return asSet().contains(eventType);
        }

        @Override
        @SuppressWarnings("unchecked")
        Set<Class<? extends E>> asSet() {
            if (set == null)
                set = Collections.unmodifiableSet(new IndexedSet<Class<? extends E>, Class<? extends E>>(Arrays.asList(eventTypes)));
            return set;
        }
    }

    private class UndecoratedSymbols extends AbstractSet<Object> {
        UndecoratedSymbols() {}

        @Nonnull
        @Override
        public Iterator<Object> iterator() {
            return new UndecoratedIterator(symbols.concurrentIterator());
        }

        @Override
        public int size() {
            return symbols.size();
        }

        @Override
        public boolean contains(Object o) {
            return symbols.containsKey(o);
        }
    }

    private class UndecoratedIterator implements Iterator<Object> {
        private final Iterator<Object> it;

        UndecoratedIterator(Iterator<Object> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public Object next() {
            return undecorateSymbol(it.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    //----------------------- private listener lists -----------------------

    private static Collection<Object> getSerializable(Object oneOrList) {
        if (oneOrList instanceof Serializable)
            return Collections.singletonList(oneOrList);
        if (oneOrList instanceof ListenerList) {
            Collection<Object> result = new ArrayList<>();
            for (Object o : ((ListenerList<?>) oneOrList).a)
                if (o instanceof Serializable)
                    result.add(o);
            return result;
        }
        return Collections.emptyList();
    }

    static <L> Object[] addListener(L oldList, L newListener) {
        int n = oldList instanceof ListenerList ? ((ListenerList<?>) oldList).a.length : 1;
        Object[] a = new Object[n + 1];
        if (oldList instanceof ListenerList)
            System.arraycopy(((ListenerList<?>) oldList).a, 0, a, 0, n);
        else
            a[0] = oldList;
        a[n] = newListener;
        return a;
    }

    static <L> int findListener(ListenerList<L> oldList, L newListener) {
        for (int i = 0; i < oldList.a.length; i++)
            if (oldList.a[i] == newListener)
                return i;
        return -1;
    }

    static <L> Object[] removeListenerAt(ListenerList<L> oldList, int removeIndex) {
        Object[] a = new Object[oldList.a.length - 1];
        System.arraycopy(oldList.a, 0, a, 0, removeIndex);
        System.arraycopy(oldList.a, removeIndex + 1, a, removeIndex, oldList.a.length - removeIndex - 1);
        return a;
    }

    private abstract static class ListenerList<L> {
        final Object[] a;

        protected ListenerList(Object[] a) {
            this.a = a;
        }

        abstract L create(Object[] a);

        @SuppressWarnings({"unchecked"})
        L simplify() {
            return a.length == 0 ? null : a.length == 1 ? (L) a[0] : (L) this;
        }

        @SuppressWarnings({"unchecked"})
        L remove(L listener) {
            int i = findListener(this, listener);
            return i < 0 ? (L) this : a.length == 2 ? (L) a[1 - i] : create(removeListenerAt(this, i)) ;
        }
    }

    private static class EventListeners<E> extends ListenerList<DXFeedEventListener<E>> implements DXFeedEventListener<E> {
        EventListeners(Object[] a) {
            super(a);
        }

        @Override
        DXFeedEventListener<E> create(Object[] a) {
            return new EventListeners<>(a);
        }

        @Override
        @SuppressWarnings({"unchecked"})
        public void eventsReceived(List<E> events) {
            for (Object listener : a)
                ((DXFeedEventListener<E>) listener).eventsReceived(events);
        }
    }

    private static class ChangeListeners extends ListenerList<ObservableSubscriptionChangeListener> implements ObservableSubscriptionChangeListener {
        ChangeListeners(Object[] a) {
            super(a);
        }

        @Override
        ObservableSubscriptionChangeListener create(Object[] a) {
            return new ChangeListeners(a);
        }

        @Override
        public void symbolsAdded(Set<?> symbols) {
            for (Object listener : a)
                ((ObservableSubscriptionChangeListener) listener).symbolsAdded(symbols);
        }

        @Override
        public void symbolsRemoved(Set<?> symbols) {
            for (Object listener : a)
                ((ObservableSubscriptionChangeListener) listener).symbolsRemoved(symbols);
        }

        @Override
        public void subscriptionClosed() {
            for (Object listener : a)
                ((ObservableSubscriptionChangeListener) listener).subscriptionClosed();
        }
    }
}
