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
package com.dxfeed.event;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;

import java.io.Serializable;

/**
 * Marks all event types that can be received via dxFeed API.
 * Events are considered instantaneous, non-persistent, and unconflateable
 * (each event is individually delivered) unless they implement one of interfaces
 * defined in this package to further refine their meaning.
 *
 * <p>Event types are POJOs (plain old java objects) that follow bean naming convention with
 * getters and setters for their properties.
 * All event types are serializable, because they are transferred over network from publishers to
 * data feed consumers. However, they are using custom serialization format for this purpose.
 *
 * @param <T> type of the event symbol for this event type.
 * @see com.dxfeed.api.DXFeed
 */
public interface EventType<T> extends Serializable {
    /**
     * Returns event symbol that identifies this event type in {@link DXFeedSubscription subscription}.
     * @return event symbol.
     */
    public T getEventSymbol();

    /**
     * Changes event symbol that identifies this event type in {@link DXFeedSubscription subscription}.
     * @param eventSymbol event symbol.
     */
    public void setEventSymbol(T eventSymbol);

    /**
     * Returns time when event was created or zero when time is not available.
     *
     * <p>This event time is available only when the corresponding {@link DXEndpoint} is created
     * with {@link DXEndpoint#DXENDPOINT_EVENT_TIME_PROPERTY DXENDPOINT_EVENT_TIME_PROPERTY} and
     * the data source has embedded event times. This is typically true only for data events
     * that are read from historical tape files and from {@link com.dxfeed.ondemand.OnDemandService OnDemandService}.
     * Events that are coming from a network connections do not have an embedded event time information and
     * this method will return zero for them, meaning that event was received just now.
     *
     * @implSpec
     * Default implementation returns 0.
     *
     * @return the difference, measured in milliseconds,
     * between the event creation time and midnight, January 1, 1970 UTC or zero when time is not available.
     */
    public default long getEventTime() { return 0; }

    /**
     * Changes event creation time.
     *
     * @implSpec
     * Default implementation does nothing.
     *
     * @param eventTime the difference, measured in milliseconds,
     *                  between the event creation time and midnight, January 1, 1970 UTC.
     */
    public default void setEventTime(long eventTime) {}
}
