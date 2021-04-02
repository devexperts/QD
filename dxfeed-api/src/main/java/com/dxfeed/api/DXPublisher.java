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

import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;

import java.util.Collection;
import java.util.Set;

/**
 * Provides API for publishing of events to local or remote {@link DXFeed feeds}.
 *
 * <h3>Sample usage</h3>
 *
 * This section gives sample usage scenarios.
 *
 * <h4>Default singleton instance</h4>
 *
 * There is a singleton instance of the publisher that is returned by {@link #getInstance()} method.
 * It is created on the first use with default configuration properties that are explained in detail in
 * documentation for {@link DXEndpoint} class in the "Default properties" section. In particular,
 * you can provide a default address to connect using
 * "{@link DXEndpoint#DXPUBLISHER_ADDRESS_PROPERTY dxpublisher.address}"
 * system property or by putting it into
 * "{@link DXEndpoint#DXPUBLISHER_PROPERTIES_PROPERTY dxpublisher.properties}"
 * file on JVM classpath.
 *
 * <h4>Publish a single event</h4>
 *
 * The following code publishes a single quote for a "A:TEST" symbol:
 * <pre><tt>
 * {@link Quote Quote} quote = new Quote("A:TEST");
 * quote.setBidPrice(100);
 * quote.setAskPrice(101);
 * {@link DXPublisher DXPublisher}.{@link #getInstance() getInstance}().{@link #publishEvents(Collection) publishEvents}(Arrays.asList(quote));</tt></pre>
 *
 * <h4>Monitor subscription and publish profile for any test symbol</h4>
 *
 * The following code monitor subscription for {@link Profile} events and for any subscription
 * on the string symbols that end with ":TEST" string generates and publishes a profile.
 *
 * <pre><tt>
 * final {@link DXPublisher DXPublisher} publisher = {@link DXPublisher DXPublisher}.{@link #getInstance() getInstance}();
 * publisher.{@link #getSubscription(Class) getSubscription}({@link Profile Profile}.class).{@link ObservableSubscription#addChangeListener(ObservableSubscriptionChangeListener) addChangeListener}(new {@link ObservableSubscriptionChangeListener ObservableSubscriptionChangeListener}() {
 *     public void {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded}(Set&lt;?&gt; symbols) {
 *         List&lt;Profile&gt; events = new ArrayList&lt;Profile&gt;();
 *         for (Object symbol : symbols) {
 *             if (symbol instanceof String) {
 *                 String s = (String) symbol;
 *                 if (s.endsWith(":TEST")) {
 *                     Profile profile = new Profile(s);
 *                     profile.setDescription("Test symbol");
 *                     events.add(profile);
 *                 }
 *             }
 *         }
 *         publisher.{@link #publishEvents(Collection) publishEvents}(events);
 *     }
 *
 *     public void symbolsRemoved(Set&lt;?&gt; symbols) {
 *         // nothing to do here
 *     }
 *
 *     public void subscriptionClosed() {
 *         // nothing to do here
 *     }
 * });</tt></pre>
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 */
public abstract class DXPublisher {
    /**
     * Protected constructor for implementations of this class only.
     */
    protected DXPublisher() {}

    /**
     * Returns a default application-wide singleton instance of DXPublisher. Most applications use only a single
     * data-sink and should rely on this method to get one. This is a shortcut to
     * {@link DXEndpoint DXEndpoint}.{@link DXEndpoint#getInstance(DXEndpoint.Role) getInstance}({@link DXEndpoint.Role#PUBLISHER DXEndpoint.Role.PUBLISHER}).{@link DXEndpoint#getPublisher() getPublisher}().
     */
    public static DXPublisher getInstance() {
        return DXEndpoint.getInstance(DXEndpoint.Role.PUBLISHER).getPublisher();
    }

    /**
     * Publishes events to the corresponding feed. If the {@link DXEndpoint endpoint} of this publisher has
     * {@link DXEndpoint#getRole() role} of {@link DXEndpoint.Role#PUBLISHER} and it is connected, the
     * published events will be delivered to the remote endpoints. Local {@link DXEndpoint#getFeed() feed} will
     * always receive published events.
     *
     * <p> This method serializes all events into internal representation, so that the instance of the collection as
     * well as the instances of events can be reused after invocation of this method returns.
     *
     * <p> {@link DXFeed} instances that are connected to this publisher either locally or via network
     * receive published events if and only if they are subscribed to the corresponding symbols, or
     * they are subscribed via {@link WildcardSymbol#ALL WildcardSymbol.ALL}, or, in case of
     * {@link TimeSeriesEvent} type, they are subscribed via {@link DXFeedTimeSeriesSubscription} for
     * the corresponding symbol and time frame.
     *
     * <p> Published events are not stored and get immediately lost if there is no subscription.
     * Last published events of {@link LastingEvent} types are cached as long as subscription to
     * them is maintained via a specific event symbol ({@link WildcardSymbol#ALL WildcardSymbol.ALL} does not count)
     * and the cache is discarded as soon as subscription disappears.
     *
     * @param events the collection of events to publish.
     */
    public abstract void publishEvents(Collection<?> events);

    /**
     * Returns observable set of subscribed symbols for the specified event type.
     * Note, that subscription is represented by object symbols. Check the type of each symbol
     * in {@link ObservableSubscription} using {@code instanceof} operation.
     *
     * <p> The set of subscribed symbols contains {@link WildcardSymbol#ALL WildcardSymbol.ALL} if and
     * only if there is a subscription to this wildcard symbol.
     *
     * <p> If {@link DXFeedTimeSeriesSubscription} is used
     * to subscribe to time-service of the events of this type, then instances of
     * {@link TimeSeriesSubscriptionSymbol} class represent the corresponding subscription item.
     *
     * <p> The resulting observable subscription can generate repeated
     * {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded} notifications to
     * its listeners for the same symbols without the corresponding
     * {@link ObservableSubscriptionChangeListener#symbolsRemoved(Set) symbolsRemoved}
     * notifications in between them. It happens when subscription disappears, cached data is lost, and subscription
     * reappears again. On each {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded}
     * notification data provider shall {@link #publishEvents(Collection) publish} the most recent events for
     * the corresponding symbols.
     *
     * @param eventType the class of event.
     * @param <E> the type of event.
     * @return Observable subscription for the specified event type.
     */
    public abstract <E> ObservableSubscription<E> getSubscription(Class<E> eventType);
}
