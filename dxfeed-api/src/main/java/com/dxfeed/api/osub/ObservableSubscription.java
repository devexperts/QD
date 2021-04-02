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

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXPublisher;

import java.util.Set;

/**
 * Observable set of subscription symbols for the specific event type.
 *
 * @param <E> the type of events.
  */
public interface ObservableSubscription<E> {
    /**
     * Returns <code>true</code> if this subscription is closed.
     * The observable subscription that is returned as a result of
     * {@link DXPublisher#getSubscription(Class) DXPublisher.getSubscription}
     * is closed when the corresponding {@link DXEndpoint DXEndpoint} is closed.
     */
    public boolean isClosed();

    /**
     * Returns a set of event types for this subscription. The resulting set cannot be modified.
     * The observable subscription that is returned as a result of
     * {@link DXPublisher#getSubscription(Class) DXPublisher.getSubscription}
     * has a singleton set of event types.
     */
    public Set<Class<? extends E>> getEventTypes();

    /**
     * Returns <code>true</code> if this subscription contains the corresponding event type.
     * @see #getEventTypes()
     */
    public boolean containsEventType(Class<?> eventType);

    /**
     * Adds subscription change listener. This method does nothing if the given listener is already
     * installed as subscription change listener for this subscription or if subscription is closed.
     * Otherwise, it installs the
     * corresponding listener and immediately invokes {@link ObservableSubscriptionChangeListener#symbolsAdded}
     * on the given listener while holding the lock for this
     * subscription. This way the given listener synchronously receives existing subscription state and and
     * is synchronously notified on all changes in subscription afterwards.
     *
     * @param listener the subscription change listener.
     * @throws NullPointerException if listener is null.
     */
    public void addChangeListener(ObservableSubscriptionChangeListener listener);

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
    public void removeChangeListener(ObservableSubscriptionChangeListener listener);
}
