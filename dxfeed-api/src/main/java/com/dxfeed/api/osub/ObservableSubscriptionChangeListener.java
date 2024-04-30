/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.osub;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.FilteredSubscriptionSymbol;

import java.util.Collection;
import java.util.Set;

/**
 * The listener interface for receiving notifications on the changes of observed subscription.
 * All methods on this interface are invoked while holding a lock on {@link ObservableSubscription} instance,
 * thus all changes for a given subscription are synchronized with respect to each other.
 *
 * <h3>Decorated symbols</h3>
 *
 * The sets of symbols that are passed to {@link #symbolsAdded(Set)} and {@link #symbolsRemoved(Set)}
 * are decorated depending on the actual implementation class of {@link DXFeedSubscription}.
 * {@link DXFeedTimeSeriesSubscription} decorates original subscription symbols by wrapping them
 * into instances of {@link TimeSeriesSubscriptionSymbol} class.
 *
 * <h3>Equality of symbols and notifications</h3>
 *
 * Symbols are compared using their {@link Object#equals(Object) equals} method. When one symbol in subscription is
 * replaced by the other one that is equal to it, then the decision of whether to issue
 * {@link #symbolsAdded(Set) symbolsAdded} notification is up to implementation.
 *
 * <p>In particular, {@link DXFeedSubscription} uses its implementation of
 * {@link DXFeedSubscription#shallNotifyOnSymbolUpdate(Object, Object) shallNotifyOnSymbolUpdate} method
 * to figure out what to do in this case. Its default implementation is designed so that repeated
 * additions of the same {@link String} symbol do not result in notification, while
 * repeated additions of {@link FilteredSubscriptionSymbol} instances like {@link TimeSeriesSubscriptionSymbol} objects
 * get notification.
 *
 * <p>However, the implementation that is returned by
 * {@link DXPublisher#getSubscription(Class) DXPublisher.getSubscription} can generate repeated
 * {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded} notifications to
 * its listeners for the same symbols without the corresponding
 * {@link ObservableSubscriptionChangeListener#symbolsRemoved(Set) symbolsRemoved}
 * notifications in between them. It happens when subscription disappears, cached data is lost, and subscription
 * reappears again. On each {@link ObservableSubscriptionChangeListener#symbolsAdded(Set) symbolsAdded}
 * notification data provider shall {@link DXPublisher#publishEvents(Collection) publish} the most recent events for
 * the corresponding symbols.
 *
 * <h3>Wildcard symbols</h3>
 *
 * The set of symbols may contain {@link WildcardSymbol#ALL} object.
 * See {@link WildcardSymbol} for details.
 */
@FunctionalInterface
public interface ObservableSubscriptionChangeListener {
    /**
     * Invoked after a collection of symbols is added to a subscription.
     * Subscription's set of symbols already includes added symbols when this method is invoked.
     * The set of symbols is decorated.
     */
    public void symbolsAdded(Set<?> symbols);

    /**
     * Invoked after a collection of symbols is removed from a subscription.
     * Subscription's set of symbols already excludes removed symbols when this method is invoked.
     * The set of symbols is decorated.
     * Default implementation is empty.
     */
    public default void symbolsRemoved(Set<?> symbols) {}

    /**
     * Invoked after subscription is closed or when this listener is
     * {@link DXFeedSubscription#removeChangeListener removed} from the subscription.
     * {@link DXPublisher} {@link DXPublisher#getSubscription(Class) subscription} is considered to be closed
     * when the corresponding {@link DXEndpoint} is {@link DXEndpoint#close closed}.
     * Default implementation is empty.
     */
    public default void subscriptionClosed() {}

    /**
     * Invoked after the configuration of the subscription is changed.
     * This method is used for non-functional configuration settings (implementation specific).
     * Default implementation is empty.
     */
    public default void configurationChanged() {}
}
