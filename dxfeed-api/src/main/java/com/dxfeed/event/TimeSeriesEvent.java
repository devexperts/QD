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

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.TimeSeriesEventModel;

import java.util.Collection;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents time-series snapshots of some
 * process that is evolving in time or actual events in some external system
 * that have an associated time stamp and can be uniquely identified.
 * For example, {@link TimeAndSale} events represent the actual sales
 * that happen on a market exchange at specific time moments, while
 * {@link Candle} events represent snapshots of aggregate information
 * about trading over a specific time period.
 *
 * <p> Time-series events can be used with {@link DXFeedTimeSeriesSubscription}
 * to receive a time-series history of past events. Time-series events
 * are conflated based on unique {@link #getIndex() index} for each symbol.
 * Last indexed event for each symbol and index is always
 * delivered to event listeners on subscription, but intermediate (next-to-last) events for each
 * symbol+index pair are not queued anywhere, they are simply discarded as stale events.
 *
 * <p> Timestamp of an event is available via {@link #getTime() time} property
 * with a millisecond precision. Some events may happen multiple times per millisecond.
 * In this case they have the same {@link #getTime() time}, but different
 * {@link #getIndex() index}. An ordering of {@link #getIndex() index} is the same
 * as an ordering of {@link #getTime() time}. That is, an collection of time-series
 * events that is ordered by {@link #getIndex() index} is ascending order will be
 * also ordered by {@link #getTime() time} in ascending order.
 *
 * <p> Time-series events are a more specific subtype of {@link IndexedEvent}.
 * All general documentation and <a href="IndexedEvent.html#eventFlagsSection">Event Flags</a> section, in particular,
 * applies to time-series events. However, the time-series events never come from multiple sources for the
 * same symbol and their {@link #getSource() source} is always {@link IndexedEventSource#DEFAULT DEFAULT}.
 *
 * <p>Unlike a general {@link IndexedEvent} that is subscribed to via {@link DXFeedSubscription} using a plain symbol
 * to receive all events for all indices, time-series events are typically subscribed to using
 * {@link TimeSeriesSubscriptionSymbol} class to specific time range of the subscription. There is a dedicated
 * {@link DXFeedTimeSeriesSubscription} class that is designed to simplify the task of subscribing for
 * time-series events.
 *
 * <p>{@link TimeSeriesEventModel} class handles all the snapshot and transaction logic and conveniently represents
 * a list of current time-series events ordered by their {@link #getTime() time}.
 * It relies on the code of {@link AbstractIndexedEventModel} to handle this logic.
 * Use the source code of {@link AbstractIndexedEventModel} for clarification on transactions and snapshot logic.
 *
 * <p> Classes that implement this interface may also implement
 * {@link LastingEvent} interface, which makes it possible to
 * use {@link DXFeed#getLastEvent(LastingEvent) DXFeed.getLastEvent} method to
 * retrieve the last event for the corresponding symbol.
 *
 * <h3>Publishing time-series</h3>
 *
 * When publishing time-series event with {@link DXPublisher#publishEvents(Collection) DXPublisher.publishEvents}
 * method on incoming {@link TimeSeriesSubscriptionSymbol} the snapshot of currently known events for the
 * requested time range has to be published first.
 *
 * <p> A snapshot is published in the <em>descending</em> order of {@link #getIndex() index}
 * (which is the same as the descending order of {@link #getTime() time}), starting with
 * an event with the largest index and marking it with {@link #SNAPSHOT_BEGIN} bit in {@link #getEventFlags() eventFlags}.
 * All other event follow with default, zero {@link #getEventFlags() eventFlags}.
 * If there is no actual event at the time that was specified in subscription
 * {@link TimeSeriesSubscriptionSymbol#getFromTime() fromTime} property, then event with the corresponding time
 * has to be created anyway and published. To distinguish it from the actual event, it has to be marked
 * with {@link #REMOVE_EVENT} bit in {@link #getEventFlags() eventFlags}.
 * {@link #SNAPSHOT_END} bit in this event's {@link #getEventFlags() eventFlags}
 * is optional during publishing. It will be properly set on receiving end anyway. Note, that publishing
 * any event with time that is below subscription {@link TimeSeriesSubscriptionSymbol#getFromTime() fromTime}
 * also works as a legal indicator for the end of the snapshot.
 *
 * <p>Both {@link TimeAndSale} and {@link Candle} time-series events define a sequence property that is mixed
 * into an {@link #getIndex() index} property. It provides a way to distinguish different events at the same time.
 * For a snapshot end event the sequence has to be left at its default zero value. It means, that if there is
 * an actual event to be published at subscription {@link TimeSeriesSubscriptionSymbol#getFromTime() fromTime}
 * with non-zero sequence, then generation of an additional snapshot end event with the subscription
 * {@link TimeSeriesSubscriptionSymbol#getFromTime() fromTime} and zero sequence is still required.
 *
 * @param <T> type of the event symbol for this event type.
 */
public interface TimeSeriesEvent<T> extends IndexedEvent<T> {
    /**
     * Returns a source identifier for this event, which is always {@link IndexedEventSource#DEFAULT DEFAULT} for time-series events.
     * @return a source identifier for this event, which is always {@link IndexedEventSource#DEFAULT DEFAULT} for time-series events.
     */
    @Override
    public IndexedEventSource getSource();

    /**
     * Returns unique per-symbol index of this event.
     * Event indices are unique within event symbol.
     * Typically event index for a time series event includes
     * {@link #getTime() time} inside.
     *
     * <h3>Implementation notes</h3>
     *
     * The most common scheme for event indices is to set highest 32 bits of event index
     * to event timestamp in seconds. The lowest 32 bits are then split as follows.
     * Bits 22 to 31 encode milliseconds of time stamp, and bits 0 to 21 encode some
     * kind of a sequence number.
     *
     * <p> Ultimately, the scheme for event indices is specific for each even type.
     * The actual classes for specific event types perform the corresponding encoding.
     *
     * @return unique per-symbol index of this event.
     */
    @Override
    public long getIndex();

    /**
     * Returns unique per-symbol index of this event.
     *
     * @return unique per-symbol index of this event.
     * @deprecated Use {@link #getIndex()}
     */
    @Deprecated
    @XmlTransient
    public default long getEventId() {
        return getIndex();
    }

    /**
     * Returns timestamp of the event.
     * The timestamp is in milliseconds from midnight, January 1, 1970 UTC.
     *
     * @return timestamp of the event.
     *
     * @see System#currentTimeMillis()
     */
    public long getTime();

}
