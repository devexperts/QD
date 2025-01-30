/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api;

import com.devexperts.annotation.Experimental;
import com.devexperts.util.TimePeriod;

import java.util.concurrent.Executor;

/**
 * An interface that provides a collection of methods for managing underlying subscription.
 */
@Experimental
public interface SubscriptionController {
    /**
     * Attaches underlying subscription to the specified feed.
     *
     * <p>Technically, subscription can be attached to multiple feeds at once,
     * but this is <b>rarely necessary and not recommended</b>.
     *
     * @param feed the feed to attach to.
     * @see DXFeedSubscription#attach(DXFeed)
     */
    public void attach(DXFeed feed);

    /**
     * Detaches underlying subscription from the specified feed.
     *
     * @param feed the feed to detach from.
     * @see DXFeedSubscription#detach(DXFeed)
     */
    public void detach(DXFeed feed);

    /**
     * Returns maximum number of events in the single notification of listener.
     * This is not a strict guarantee, some implementations may exceed the limit.
     *
     * @return the maximum number of events in the single notification of listener.
     * @see DXFeedSubscription#getEventsBatchLimit()
     */
    public int getEventsBatchLimit();

    /**
     * Sets maximum number of events in the single notification of listener.
     * This is not a strict guarantee, some implementations may exceed the limit.
     *
     * @param eventsBatchLimit the notification events limit.
     * @see DXFeedSubscription#setEventsBatchLimit(int)
     */
    public void setEventsBatchLimit(int eventsBatchLimit);

    /**
     * Returns the aggregation period for data for this underlying subscription.
     *
     * @return the aggregation period for data, represented as a {@link TimePeriod} object.
     * @see DXFeedSubscription#getAggregationPeriod()
     */
    public TimePeriod getAggregationPeriod();

    /**
     * Sets the aggregation period for data that limits the rate of data notifications.
     * For example, setting the value to "0.1s" limits notification to once every 100ms (at most 10 per second).
     * The new aggregation period will take effect during the next iteration of data notification.
     *
     * @param aggregationPeriod the aggregation period for data.
     * @see DXFeedSubscription#setAggregationPeriod(TimePeriod)
     */
    public void setAggregationPeriod(TimePeriod aggregationPeriod);

    /**
     * Returns executor for processing event notifications on this underlying subscription.
     *
     * @return executor for processing event notifications on this underlying subscription.
     *     or {@code null} if default executor is used.
     * @see DXFeedSubscription#getExecutor()
     */
    public Executor getExecutor();

    /**
     * Changes executor for processing event notifications on this underlying subscription.
     *
     * @param executor executor for processing event notifications on this underlying subscription.
     *     or {@code null} if default executor is used.
     * @see DXFeedSubscription#setExecutor(Executor)
     */
    public void setExecutor(Executor executor);
}
