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
package com.dxfeed.api.impl;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;

import java.util.Set;

class DXPublisherObservableSubscriptionImpl<T> implements ObservableSubscription<T> {
    private final DXFeedSubscription<T> innerSubscription;

    DXPublisherObservableSubscriptionImpl(DXFeedSubscription<T> innerSubscription) {
        this.innerSubscription = innerSubscription;
    }

    @Override
    public boolean isClosed() {
        return innerSubscription.isClosed();
    }

    @Override
    public Set<Class<? extends T>> getEventTypes() {
        return innerSubscription.getEventTypes();
    }

    @Override
    public boolean containsEventType(Class<?> eventType) {
        return innerSubscription.containsEventType(eventType);
    }

    @Override
    public void addChangeListener(ObservableSubscriptionChangeListener listener) {
        innerSubscription.addChangeListener(listener);
    }

    @Override
    public synchronized void removeChangeListener(ObservableSubscriptionChangeListener listener) {
        innerSubscription.removeChangeListener(listener);
    }
}
