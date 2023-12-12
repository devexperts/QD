/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api;

import com.devexperts.logging.Logging;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.option.Series;
import com.dxfeed.model.IndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;
import com.dxfeed.promise.Promises;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Main entry class for dxFeed API (<b>read it first</b>).
 *
 * <h3>Sample usage</h3>
 *
 * This section gives sample usage scenarios.
 *
 * <h4>Default singleton instance</h4>
 *
 * There is a singleton instance of the feed that is returned by {@link #getInstance()} method.
 * It is created on the first use with default configuration properties that are explained in detail in
 * documentation for {@link DXEndpoint} class in the
 * "<a href="DXEndpoint.html#defaultPropertiesSection">Default properties</a>"
 *
 * <p>In particular,
 * you can provide a default address to connect and credentials  using
 * "{@link DXEndpoint#DXFEED_ADDRESS_PROPERTY dxfeed.address}",
 * "{@link DXEndpoint#DXFEED_USER_PROPERTY dxfeed.user}", and
 * "{@link DXEndpoint#DXFEED_PASSWORD_PROPERTY dxfeed.password}"
 * system properties or by putting them into
 * "{@link DXEndpoint#DXFEED_PROPERTIES_PROPERTY dxfeed.properties}"
 * file on JVM classpath. dxFeed API samples come with a ready-to-use "<b>dxfeed.properties</b>"
 * file that contains an address of dxFeed demo feed at "<b>demo.dxfeed.com:7300</b>" and
 * demo access credentials.
 *
 * <h4>Subscribe for single event type</h4>
 *
 * The following code creates listener that prints mid price for each quote
 * and subscribes for quotes on SPDR S&amp;P 500 ETF symbol:
 * <pre><tt>
 * {@link DXFeedSubscription DXFeedSubscription}&lt;{@link Quote Quote}&gt; sub = {@link DXFeed DXFeed}.{@link #getInstance() getInstance}().{@link #createSubscription(Class) createSubscription}({@link Quote Quote.class});
 * sub.{@link DXFeedSubscription#addEventListener addEventListener}(new {@link DXFeedEventListener DXFeedEventListener}&lt;{@link Quote Quote}&gt;() {
 *     public void eventsReceived({@link List List}&lt;{@link Quote Quote}&gt; quotes) {
 *         for ({@link Quote Quote} quote : quotes)
 *             System.out.println("Mid = " + (quote.{@link Quote#getBidPrice getBidPrice}() + quote.{@link Quote#getAskPrice getAskPrice}()) / 2);
 *     }
 * });
 * sub.{@link DXFeedSubscription#addSymbols(Object...) addSymbols}("SPY");</tt></pre>
 *
 * Note, that order of calls is important here. By attaching listeners first and then setting
 * subscription we ensure that the current quote gets received by the listener. See
 * {@link DXFeedSubscription#addSymbols(Object...) DXFeedSubscription.addSymbols} for details.
 * If a set of symbols is changed first, then {@link DXFeedSubscription#addEventListener(DXFeedEventListener) sub.addEventListener}
 * raises an {@link IllegalStateException} to protected from hard-to-catch bugs with potentially missed events.
 *
 * <h4>Subscribe for multiple event types</h4>
 *
 * The following code creates listener that prints each received event and
 * subscribes for quotes and trades on SPDR S&amp;P 500 ETF symbol:
 * <pre><tt>
 * {@link DXFeedSubscription DXFeedSubscription}&lt;{@link MarketEvent MarketEvent}&gt; sub = {@link DXFeed DXFeed}.{@link #getInstance() getInstance}().&lt;{@link MarketEvent MarketEvent}&gt;{@link #createSubscription(Class[]) createSubscription}({@link Quote Quote.class}, {@link Trade Trade.class});
 * sub.{@link DXFeedSubscription#addEventListener addEventListener}(new {@link DXFeedEventListener DXFeedEventListener}&lt;{@link MarketEvent MarketEvent}&gt;() {
 *     public void eventsReceived({@link List List}&lt;{@link MarketEvent MarketEvent}&gt; events) {
 *         for ({@link MarketEvent MarketEvent} event : events)
 *             System.out.println(event);
 *     }
 * });
 * sub.{@link DXFeedSubscription#addSymbols(Object...) addSymbols}("SPY");</tt></pre>
 *
 * <h4>Subscribe for event and query periodically its last value</h4>
 *
 * The following code subscribes for trades on SPDR S&amp;P 500 ETF symbol and
 * prints last trade every second.
 *
 * <pre><tt>
 * {@link DXFeedSubscription DXFeedSubscription}&lt;{@link Trade Trade}&gt; sub = {@link DXFeed DXFeed}.{@link #getInstance() getInstance}().{@link #createSubscription(Class) createSubscription}({@link Trade Trade.class});
 * sub.{@link DXFeedSubscription#addSymbols(Object...) addSymbols}("SPY");
 * while (true) {
 *     System.out.println(feed.{@link #getLastEvent getLastEvent}(new Trade("SPY")));
 *     Thread.sleep(1000);
 * }</tt></pre>
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * <h3>Implementation details</h3>
 *
 * dxFeed API is implemented on top of QDS. dxFeed API classes itself are in "<b>dxfeed-api.jar</b>", but
 * their implementation is in "<b>qds.jar</b>". You need have "<b>qds.jar</b>" in your classpath
 * in order to use dxFeed API.
 */
public abstract class DXFeed {
    /**
     * Protected constructor for implementations of this class only.
     */
    protected DXFeed() {}

    /**
     * Returns a default application-wide singleton instance of feed. Most applications use only a single
     * data-source and should rely on this method to get one. This is a shortcut to
     * {@link DXEndpoint DXEndpoint}.{@link DXEndpoint#getInstance() getInstance}().{@link DXEndpoint#getFeed() getFeed}().
     */
    public static DXFeed getInstance() {
        return DXEndpoint.getInstance().getFeed();
    }

    /**
     * Creates new subscription for a single event type that is <i>attached</i> to this feed.
     * For multiple event types in one subscription use
     * {@link #createSubscription(Class[])}  createSubscription(Class... eventTypes)}
     * This method creates new {@link DXFeedSubscription} and invokes {@link #attachSubscription}.
     *
     * @param eventType the class of event types.
     * @param <E> the type of events.
     *
     * @see DXFeedSubscription#DXFeedSubscription(Class)
     * @see #attachSubscription(DXFeedSubscription)
     */
    public final <E> DXFeedSubscription<E> createSubscription(Class<? extends E> eventType) {
        DXFeedSubscription<E> subscription = new DXFeedSubscription<>(eventType);
        attachSubscription(subscription);
        return subscription;
    }

    /**
     * Creates new subscription for multiple event types that is <i>attached</i> to this feed.
     * For a single event type use {@link #createSubscription(Class) createSubscrtiption(Class eventType)}
     * This method creates new {@link DXFeedSubscription} and invokes {@link #attachSubscription}.
     *
     * @param eventTypes the classes of event types.
     * @param <E> the type of events.
     *
     * @see DXFeedSubscription#DXFeedSubscription(Class[])
     * @see #attachSubscription(DXFeedSubscription)
     */
    @SafeVarargs
    public final <E> DXFeedSubscription<E> createSubscription(Class<? extends E>... eventTypes) {
        DXFeedSubscription<E> subscription = new DXFeedSubscription<>(eventTypes);
        attachSubscription(subscription);
        return subscription;
    }

    /**
     * Creates new time series subscription for a single event type that is <i>attached</i> to this feed.
     * For multiple event types in one subscription use
     * {@link #createTimeSeriesSubscription(Class[])}  createTimeSeriesSubscription(Class... eventTypes)}
     * This method creates new {@link DXFeedTimeSeriesSubscription} and invokes {@link #attachSubscription}.
     *
     * @param eventType the class of event types.
     * @param <E> the type of events.
     *
     * @see DXFeedTimeSeriesSubscription#DXFeedTimeSeriesSubscription(Class)
     * @see #attachSubscription(DXFeedSubscription)
     */
    public final <E extends TimeSeriesEvent<?>> DXFeedTimeSeriesSubscription<E> createTimeSeriesSubscription(Class<? extends E> eventType) {
        DXFeedTimeSeriesSubscription<E> subscription = new DXFeedTimeSeriesSubscription<>(eventType);
        attachSubscription(subscription);
        return subscription;
    }

    /**
     * Creates new time series subscription for multiple event types that is <i>attached</i> to this feed.
     * For a single event type use {@link #createTimeSeriesSubscription(Class) createTimeSeriesSubscription(Class eventType)}
     * This method creates new {@link DXFeedTimeSeriesSubscription} and invokes {@link #attachSubscription}.
     *
     * @param eventTypes the classes of event types.
     * @param <E> the type of events.
     *
     * @see DXFeedTimeSeriesSubscription#DXFeedTimeSeriesSubscription(Class[])
     * @see #attachSubscription(DXFeedSubscription)
     */
    @SafeVarargs
    public final <E extends TimeSeriesEvent<?>> DXFeedTimeSeriesSubscription<E> createTimeSeriesSubscription(Class<? extends E>... eventTypes) {
        DXFeedTimeSeriesSubscription<E> subscription = new DXFeedTimeSeriesSubscription<>(eventTypes);
        attachSubscription(subscription);
        return subscription;
    }

    /**
     * Attaches the given subscription to this feed. This method does nothing if the
     * corresponding subscription is already attached to this feed.
     *
     * <p> This feed publishes data to the attached subscription.
     * Application can attach {@link DXFeedEventListener} via
     * {@link DXFeedSubscription#addEventListener} to get notified about data changes
     * and can change its data subscription via {@link DXFeedSubscription} methods.
     *
     * <h3>Implementation notes</h3>
     *
     * This method adds a non-serializable {@link ObservableSubscriptionChangeListener} for the given subscription
     * via {@link DXFeedSubscription#addChangeListener} method.
     * Attachment is lost when subscription is serialized and deserialized.
     *
     * @param subscription the subscription.
     * @throws NullPointerException if the subscription is null.
     *
     * @see DXFeedSubscription
     */
    public abstract void attachSubscription(DXFeedSubscription<?> subscription);

    /**
     * Detaches the given subscription from this feed. This method does nothing if the
     * corresponding subscription is not attached to this feed.
     *
     * <h3>Implementation notes</h3>
     *
     * This method removes {@link ObservableSubscriptionChangeListener} from the given subscription
     * via {@link DXFeedSubscription#removeChangeListener} method.
     *
     * @param subscription the subscription.
     * @throws NullPointerException if the subscription is null.
     *
     * @see DXFeedSubscription
     */
    public abstract void detachSubscription(DXFeedSubscription<?> subscription);

    /**
     * Detaches the given subscription from this feed and clears data delivered to this subscription
     * by publishing empty events. This method does nothing if the
     * corresponding subscription is not attached to this feed.
     *
     * @param subscription the subscription.
     * @throws NullPointerException if the subscription is null.
     *
     * @see #detachSubscription(DXFeedSubscription)
     */
    public abstract void detachSubscriptionAndClear(DXFeedSubscription<?> subscription);

    /**
     * Returns the last event for the specified event instance.
     * This method works only for event types that implement {@link LastingEvent} marker interface.
     * This method <b>does not</b> make any remote calls to the uplink data provider.
     * It just retrieves last received event from the local cache of this feed.
     * The events are stored in the cache only if there is some
     * attached {@link DXFeedSubscription} that is subscribed to the corresponding symbol and event type.
     * {@link WildcardSymbol#ALL WildcardSymbol.ALL} subscription does not count for that purpose.
     *
     * <p>Use {@link #getLastEventPromise getLastEventPromise} method
     * if an event needs to be requested in the absence of subscription.
     *
     * <p> This method fills in the values for the last event into the {@code event} argument.
     * If the last event is not available for any reason (no subscription, no connection to uplink, etc).
     * then the event object is not changed.
     * This method always returns the same {@code event} instance that is passed to it as an argument.
     *
     * <p>This method provides no way to distinguish a case when there is no subscription from the case when
     * there is a subscription, but the event data have not arrived yet. It is recommened to use
     * {@link #getLastEventIfSubscribed(Class, Object) getLastEventIfSubscribed} method
     * instead of this {@code getLastEvent} method to fail-fast in case when the subscription was supposed to be
     * set by the logic of the code, since {@link #getLastEventIfSubscribed(Class, Object) getLastEventIfSubscribed}
     * method returns null when there is no subscription.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (never fills in the event).
     *
     * @param event the event.
     * @param <E> the type of event.
     * @return the same event.
     * @throws NullPointerException if the event is null.
     */
    public abstract <E extends LastingEvent<?>> E getLastEvent(E event);

    /**
     * Returns the last events for the specified list of event instances.
     * This is a bulk version of {@link #getLastEvent(LastingEvent) getLastEvent} method.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role.
     *
     * @param events the collection of events.
     * @param <E> the type of event.
     * @return the same collection of events.
     * @throws NullPointerException if the collection or any event in it is null.
     */
    public <E extends LastingEvent<?>> Collection<E> getLastEvents(Collection<E> events) {
        events.forEach(this::getLastEvent);
        return events;
    }

    /**
     * Requests the last event for the specified event type and symbol.
     * This method works only for event types that implement {@link LastingEvent} marker interface.
     * This method requests the data from the the uplink data provider,
     * creates new event of the specified {@code eventType},
     * and {@link Promise#complete(Object) completes} the resulting promise with this event.
     *
     * <p> This method is designed for retrieval of a snapshot only.
     * Use {@link DXFeedSubscription} if you need event updates in real time.
     *
     * <p>The promise is {@link Promise#cancel() cancelled} when the the underlying {@link DXEndpoint} is
     * {@link DXEndpoint#close() closed}.
     * If the event is not available for any transient reason (no subscription, no connection to uplink, etc),
     * then the resulting promise completes when the issue is resolved, which may involve an arbitrarily long wait.
     * Use {@link Promise#await(long, TimeUnit)} method to specify timeout while waiting for promise to complete.
     * If the event is permanently not available (not supported), then the promise
     * {@link Promise#completeExceptionally(Throwable) completes exceptionally} with {@link IllegalArgumentException}.
     *
     * <p>Use the following pattern of code to acquire multiple events (either for multiple symbols and/or multiple
     * events) and wait with a single timeout for all of them:
     * <pre><tt>
     * {@link List List}&lt;{@link Promise Promise}&lt;?&gt;&gt; promises = <b>new</b> {@link ArrayList ArrayList}&lt;{@link Promise Promise}&lt;?&gt;&gt;();
     * // iterate the following line for all events and/or symbols that are needed
     * promises.{@link List#add add}(feed.<u>getLastEventPromise</u>(eventType, symbol));
     * // combine the list of promises into one with Promises utility method and wait
     * {@link Promises Promises}.{@link Promises#allOf(Collection) allOf}(promises).{@link Promise#awaitWithoutException(long, TimeUnit) awaitWithoutException}(timeout, unit);
     * // now iterate the promises to retrieve results
     * <b>for</b> ({@link Promise Promise}&lt;?&gt; promise : promises)
     *     doSomethingWith(promise.{@link Promise#getResult() getResult}()); // result is null if this event was not found
     * </tt></pre>
     *
     * <p>There is a bulk version of this method that works much faster for a single event type and multiple symbols.
     * See {@link #getLastEventsPromises(Class, Collection) getLastEventsPromises}.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (promise completes exceptionally).
     *
     * <h3>Threads</h3>
     *
     * Use {@link Promise#whenDone(PromiseHandler) Promise.whenDone} method on the resulting promise to receive
     * notification when the promise becomes {@link Promise#isDone() done}. This notification is invoked
     * from inside this {@link DXEndpoint DXEndpoint} {@link DXEndpoint#executor(Executor) executor} thread.
     *
     * @param eventType the event type.
     * @param symbol the symbol.
     * @param <E> the type of event.
     * @return the promise for the result of the request.
     * @throws NullPointerException if the eventType or symbol are null.
     */
    public abstract <E extends LastingEvent<?>> Promise<E> getLastEventPromise(Class<E> eventType, Object symbol);

    /**
     * Returns the last event for the specified event type and symbol if there is a subscription for it.
     * This method works only for event types that implement {@link LastingEvent} marker interface.
     * This method <b>does not</b> make any remote calls to the uplink data provider.
     * It just retrieves last received event from the local cache of this feed.
     * The events are stored in the cache only if there is some
     * attached {@link DXFeedSubscription} that is subscribed to the corresponding event type and symbol.
     * The subscription can also be permanently defined using {@link DXEndpoint DXEndpoint} properties.
     * {@link WildcardSymbol#ALL WildcardSymbol.ALL} subscription does not count for that purpose.
     * If there is no subscription, then this method returns null.
     *
     * <p>If there is a subscription, but the event has not arrived from the uplink data provider,
     * this method returns an non-initialized event object: its {@link EventType#getEventSymbol() eventSymbol}
     * property is set to the requested symbol, but all the other properties have their default values.
     *
     * <p>Use {@link #getLastEventPromise getLastEventPromise} method
     * if an event needs to be requested in the absence of subscription.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (always returns null).
     *
     * @param eventType the event type.
     * @param symbol the symbol.
     * @param <E> the type of event.
     * @return the event or null if there is no subscription for the specified event type and symbol.
     * @throws NullPointerException if the event type or symbol are null.
     */
    public abstract <E extends LastingEvent<?>> E getLastEventIfSubscribed(Class<E> eventType, Object symbol);

    /**
     * Requests the last events for the specified event type and a collection of symbols.
     * This method works only for event types that implement {@link LastingEvent} marker interface.
     * This method requests the data from the the uplink data provider,
     * creates new events of the specified {@code eventType},
     * and {@link Promise#complete(Object) completes} the resulting promises with these events.
     *
     * <p>This is a bulk version of {@link #getLastEventPromise(Class, Object) getLastEventPromise(eventType, symbol)} method.
     *
     * <p>The promise is {@link Promise#cancel() cancelled} when the the underlying {@link DXEndpoint} is
     * {@link DXEndpoint#close() closed}.
     * If the event is not available for any transient reason (no subscription, no connection to uplink, etc),
     * then the resulting promise completes when the issue is resolved, which may involve an arbitrarily long wait.
     * Use {@link Promise#await(long, TimeUnit)} method to specify timeout while waiting for promise to complete.
     * If the event is permanently not available (not supported), then the promise
     * {@link Promise#completeExceptionally(Throwable) completes exceptionally} with {@link IllegalArgumentException}.
     *
     * <p>Use the following pattern of code to acquire multiple events (either for multiple symbols and/or multiple
     * events) and wait with a single timeout for all of them:
     * <pre><tt>
     * {@link List List}&lt;{@link Promise Promise}&lt;?&gt;&gt; promises = feed.<u>getLastEventsPromises</u>(eventType, symbols);
     * // combine the list of promises into one with Promises utility method and wait
     * {@link Promises Promises}.{@link Promises#allOf(Collection) allOf}(promises).{@link Promise#awaitWithoutException(long, TimeUnit) awaitWithoutException}(timeout, unit);
     * // now iterate the promises to retrieve results
     * <b>for</b> ({@link Promise Promise}&lt;?&gt; promise : promises)
     *     doSomethingWith(promise.{@link Promise#getResult() getResult}()); // result is null if this event was not found
     * </tt></pre>
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (promise completes exceptionally).
     *
     * <h3>Threads</h3>
     *
     * Use {@link Promise#whenDone(PromiseHandler) Promise.whenDone} method on the resulting promises to receive
     * notification when the promise becomes {@link Promise#isDone() done}. This notification is invoked
     * from inside this {@link DXEndpoint DXEndpoint} {@link DXEndpoint#executor(Executor) executor} thread.
     *
     * @param eventType the event type.
     * @param symbols the collection of symbols.
     * @param <E> the type of event.
     * @return the list of promises for the result of the requests, one item in list per symbol.
     * @throws NullPointerException if the eventType or symbols are null.
     */
    public abstract <E extends LastingEvent<?>> List<Promise<E>> getLastEventsPromises(Class<E> eventType, Collection<?> symbols);

    /**
     * Requests a list of indexed events for the specified event type, symbol, and source.
     * This method works only for event types that implement {@link IndexedEvent} interface.
     * This method requests the data from the the uplink data provider,
     * creates a list of events of the specified {@code eventType},
     * and {@link Promise#complete(Object) completes} the resulting promise with this list.
     * The events are ordered by {@link IndexedEvent#getIndex() index} in the list.
     *
     * <p> This method is designed for retrieval of a snapshot only.
     * Use {@link IndexedEventModel} if you need a list of indexed events that updates in real time.
     *
     * <p>The promise is {@link Promise#cancel() cancelled} when the the underlying {@link DXEndpoint} is
     * {@link DXEndpoint#close() closed}.
     * If the events are not available for any transient reason (no subscription, no connection to uplink, etc),
     * then the resulting promise completes when the issue is resolved, which may involve an arbitrarily long wait.
     * Use {@link Promise#await(long, TimeUnit)} method to specify timeout while waiting for promise to complete.
     * If the events are permanently not available (not supported), then the promise
     * {@link Promise#completeExceptionally(Throwable) completes exceptionally} with {@link IllegalArgumentException}.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (promise completes exceptionally).
     *
     * <h3>Event source</h3>
     *
     * Use the {@link IndexedEventSource#DEFAULT DEFAULT} value for {@code source} with events that do not
     * have multiple sources (like {@link Series}). For events with multiple sources (like {@link Order},
     * {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder}), use an event-specific
     * source class (for example, {@link OrderSource}).
     * This method does not support <em>synthetic</em> sources of orders (orders that are automatically
     * generated from {@link Quote} events).
     *
     * <p>This method does not accept an instance of {@link IndexedEventSubscriptionSymbol} as a {@code symbol}.
     * The later class is designed for use with {@link DXFeedSubscription} and to observe source-specific subscription
     * in {@link DXPublisher}.
     *
     * <h3>Event flags and consistent snapshot</h3>
     *
     * This method completes promise only when a consistent snapshot of indexed events has been received from
     * the data feed. The {@link IndexedEvent#getEventFlags() eventFlags} property of the events in the resulting list
     * is always zero.
     *
     * <p>Note, that the resulting list <em>should not</em> be used with
     * {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents} method, because the later expects
     * events in a different order and with an appropriate flags set. See documentation on a specific event class
     * for details on how they should be published.
     *
     * <h3>Threads</h3>
     *
     * Use {@link Promise#whenDone(PromiseHandler) Promise.whenDone} method on the resulting promise to receive
     * notification when the promise becomes {@link Promise#isDone() done}. This notification is invoked
     * from inside this {@link DXEndpoint DXEndpoint} {@link DXEndpoint#executor(Executor) executor} thread.
     *
     * @param eventType the event type.
     * @param symbol the symbol.
     * @param source the source.
     * @param <E> the type of event.
     * @return the promise for the result of the request.
     * @throws NullPointerException if the eventType or symbol are null.
     */
    public abstract <E extends IndexedEvent<?>> Promise<List<E>> getIndexedEventsPromise(Class<E> eventType,
        Object symbol, IndexedEventSource source);

    /**
     * Returns a list of indexed events for the specified event type, symbol, and source
     * if there is a subscription for it.
     * This method works only for event types that implement {@link IndexedEvent} interface.
     * This method <b>does not</b> make any remote calls to the uplink data provider.
     * It just retrieves last received events from the local cache of this feed.
     * The events are stored in the cache only if there is some
     * attached {@link DXFeedSubscription} that is subscribed to the corresponding event type, symbol, and source.
     * The subscription can also be permanently defined using {@link DXEndpoint DXEndpoint} properties.
     * If there is no subscription, then this method returns null.
     * Otherwise, it creates a list of events of the specified {@code eventType} and returns it.
     * The events are ordered by {@link IndexedEvent#getIndex() index} in the list.
     *
     * <p>If there is a subscription, but the events have not arrived from the uplink data provider,
     * this method returns an empty list.
     *
     * <p>Use {@link #getIndexedEventsPromise getIndexedEventsPromise} method
     * if events need to be requested in the absence of subscription.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (always returns null).
     *
     * <h3>Event source</h3>
     *
     * Use the {@link IndexedEventSource#DEFAULT DEFAULT} value for {@code source} with events that do not
     * have multiple sources (like {@link Series}). For events with multiple sources (like {@link Order},
     * {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder}), use an event-specific
     * source class (for example, {@link OrderSource}).
     * This method does not support <em>synthetic</em> sources of orders (orders that are automatically
     * generated from {@link Quote} events).
     *
     * <p>This method does not accept an instance of {@link IndexedEventSubscriptionSymbol} as a {@code symbol}.
     * The later class is designed for use with {@link DXFeedSubscription} and to observe source-specific subscription
     * in {@link DXPublisher}.
     *
     * <h3>Event flags and consistent snapshot</h3>
     *
     * This method returns a list of events that are currently in the cache without any wait or delay
     * and it <b>does not</b> guarantee that a consistent snapshot of events is returned.
     * See {@link IndexedEvent} documentation for details.
     * The {@link IndexedEvent#getEventFlags() eventFlags} property of the events in the resulting list
     * is always zero regardless. Use {@link #getIndexedEventsPromise getIndexedEventsPromise} method
     * if a consistent snapshot of events needs to be requested.
     *
     * <p>Note, that the resulting list <em>should not</em> be used with
     * {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents} method, because the later expects
     * events in a different order and with an appropriate flags set. See documentation on a specific event class
     * for details on how they should be published.
     *
     * @param eventType the event type.
     * @param symbol the symbol.
     * @param source the source.
     * @param <E> the type of event.
     * @return the list of events or null if there is no subscription for the specified event type, symbol, and source.
     * @throws NullPointerException if the eventType or symbol are null.
     */
    public abstract <E extends IndexedEvent<?>> List<E> getIndexedEventsIfSubscribed(Class<E> eventType,
        Object symbol, IndexedEventSource source);

    /**
     * Requests time series of events for the specified event type, symbol, and a range of time.
     * This method works only for event types that implement {@link TimeSeriesEvent} interface.
     * This method requests the data from the the uplink data provider,
     * creates a list of events of the specified {@code eventType},
     * and {@link Promise#complete(Object) completes} the resulting promise with this list.
     * The events are ordered by {@link TimeSeriesEvent#getTime() time} in the list.
     *
     * <p> This method is designed for retrieval of a snapshot only.
     * Use {@link TimeSeriesEventModel} if you need a list of time-series events that updates in real time.
     *
     * <p>The range and depth of events that are available with this service is typically constrained by
     * upstream data provider.
     *
     * <p>The promise is {@link Promise#cancel() cancelled} when the the underlying {@link DXEndpoint} is
     * {@link DXEndpoint#close() closed}.
     * If events are not available for any transient reason (no subscription, no connection to uplink, etc),
     * then the resulting promise completes when the issue is resolved, which may involve an arbitrarily long wait.
     * Use {@link Promise#await(long, TimeUnit)} method to specify timeout while waiting for promise to complete.
     * If events are permanently not available (not supported), then the promise
     * {@link Promise#completeExceptionally(Throwable) completes exceptionally} with {@link IllegalArgumentException}.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (promise completes exceptionally).
     *
     * <p>This method does not accept an instance of {@link TimeSeriesSubscriptionSymbol} as a {@code symbol}.
     * The later class is designed for use with {@link DXFeedSubscription} and to observe time-series subscription
     * in {@link DXPublisher}.
     *
     * <h3>Event flags</h3>
     *
     * This method completes promise only when a consistent snapshot of time series has been received from
     * the data feed. The {@link IndexedEvent#getEventFlags() eventFlags} property of the events in the resulting list
     * is always zero.
     *
     * <p>Note, that the resulting list <em>should not</em> be used with
     * {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents} method, because the later expects
     * events in a different order and with an appropriate flags set. See documentation on a specific event class
     * for details on how they should be published.
     *
     * <h3>Threads</h3>
     *
     * Use {@link Promise#whenDone(PromiseHandler) Promise.whenDone} method on the resulting promise to receive
     * notification when the promise becomes {@link Promise#isDone() done}. This notification is invoked
     * from inside this {@link DXEndpoint DXEndpoint} {@link DXEndpoint#executor(Executor) executor} thread.
     *
     * @param eventType the event type.
     * @param symbol the symbol
     * @param fromTime the time, inclusive, to request events from (see {@link TimeSeriesEvent#getTime() TimeSeriesEvent.getTime}).
     * @param toTime the time, inclusive, to request events to (see {@link TimeSeriesEvent#getTime() TimeSeriesEvent.getTime}).
     *               Use {@link Long#MAX_VALUE Long.MAX_VALUE} to retrieve events without an upper limit on time.
     * @param <E> the type of event.
     * @return the promise for the result of the request.
     * @throws NullPointerException if the eventType or symbol are null.
     */
    public abstract <E extends TimeSeriesEvent<?>> Promise<List<E>> getTimeSeriesPromise(Class<E> eventType,
        Object symbol, long fromTime, long toTime);

    /**
     * Returns time series of events for the specified event type, symbol, and a range of time
     * if there is a subscription for it.
     * This method <b>does not</b> make any remote calls to the uplink data provider.
     * It just retrieves last received events from the local cache of this feed.
     * The events are stored in the cache only if there is some
     * attached {@link DXFeedSubscription} that is subscribed to the corresponding event type, symbol, and time.
     * The subscription can also be permanently defined using {@link DXEndpoint DXEndpoint} properties.
     * If there is no subscription, then this method returns null.
     * Otherwise, it creates a list of events of the specified {@code eventType} and returns it.
     * The events are ordered by {@link TimeSeriesEvent#getTime() time} in the list.
     *
     * <p>If there is a subscription, but the events have not arrived from the uplink data provider,
     * this method returns an empty list.
     *
     * <p>Use {@link #getTimeSeriesPromise getTimeSeriesPromise} method
     * if events need to be requested in the absence of subscription.
     *
     * <p>Note, that this method does not work when {@link DXEndpoint} was created with
     * {@link DXEndpoint.Role#STREAM_FEED STREAM_FEED} role (always returns null).
     *
     * <p>This method does not accept an instance of {@link TimeSeriesSubscriptionSymbol} as a {@code symbol}.
     * The later class is designed for use with {@link DXFeedSubscription} and to observe time-series subscription
     * in {@link DXPublisher}.
     *
     * <h3>Event flags and consistent snapshot</h3>
     *
     * This method returns a list of events that are currently in the cache without any wait or delay
     * and it <b>does not</b> guarantee that a consistent snapshot of events is returned.
     * See {@link IndexedEvent} documentation for details.
     * The {@link IndexedEvent#getEventFlags() eventFlags} property of the events in the resulting list
     * is always zero regardless. Use {@link #getTimeSeriesPromise getTimeSeriesPromise} method
     * if a consistent snapshot of events needs to be requested.
     *
     * <p>Note, that the resulting list <em>should not</em> be used with
     * {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents} method, because the later expects
     * events in a different order and with an appropriate flags set. See documentation on a specific event class
     * for details on how they should be published.
     *
     * @param eventType the event type.
     * @param symbol the symbol
     * @param fromTime the time, inclusive, to return events from (see {@link TimeSeriesEvent#getTime() TimeSeriesEvent.getTime}).
     * @param toTime the time, inclusive, to return events to (see {@link TimeSeriesEvent#getTime() TimeSeriesEvent.getTime}).
     *               Use {@link Long#MAX_VALUE Long.MAX_VALUE} to retrieve events without an upper limit on time.
     * @param <E> the type of event.
     * @return the list of events or null if there is no subscription for the specified event type, symbol, and time range.
     * @throws NullPointerException if the eventType or symbol are null.
     */
    public abstract <E extends TimeSeriesEvent<?>> List<E> getTimeSeriesIfSubscribed(Class<E> eventType,
        Object symbol, long fromTime, long toTime);

    //----------------------- protected API for subclasses -----------------------

    /**
     * Processes received events. This methods invokes {@link DXFeedEventListener#eventsReceived} on all installed
     * event listeners. This is a protected method for use by {@code DXFeed} implementation classes only.
     *
     * @param events the list of received events.
     * @param <E> the type of events.
     */
    protected static <E> void processEvents(DXFeedSubscription<E> subscription, List<E> events) {
        subscription.processEvents(events);
    }

}
