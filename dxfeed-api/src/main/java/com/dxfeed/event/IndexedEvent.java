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

import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.option.Series;

/**
 * Represents an indexed collection of up-to-date information about some
 * condition or state of an external entity that updates in real-time. For example,
 * {@link Order} represents an order to buy or to sell some market instrument
 * that is currently active on a market exchange and multiple
 * orders are active for each symbol at any given moment in time.
 * {@link Candle} represent snapshots of aggregate information
 * about trading over a specific time period and there are multiple periods available.
 * The {@link Candle} is also an example of {@link TimeSeriesEvent} that is a more specific event type.
 *
 * <p> Index for each event is available via {@link #getIndex() index} property.
 * Indexed events retain information about multiple events per symbol based on the event index
 * and are conflated based on the event index. Last indexed event for each symbol and index is always
 * delivered to event listeners on subscription, but intermediate (next-to-last) events for each
 * symbol+index pair are not queued anywhere, they are simply discarded as stale events.
 * More recent events represent an up-to-date information about some external entity.
 *
 * <h3>Event flags, transactions and snapshots</h3>
 *
 * Some indexed event sources provide a consistent view of a set of events for a given symbol. Their updates
 * may incorporate multiple changes that have to be processed at the same time.
 * The corresponding information is carried in {@link #getEventFlags() eventFlags} property.
 * Some indexed events, like {@link Order}, support multiple sources of information for the
 * same symbol. The event source is available via {@link #getSource() source} property.
 * The value of {@link #getSource() source} property is always {@link IndexedEventSource#DEFAULT DEFAULT} for
 * time-series events and other singe-sourced events like {@link Series}, that do not support this feature.
 *
 * <p> The value of {@link #getEventFlags() eventFlags} property has several significant bits that are packed
 * into an integer in the following way:
 *
 * <pre><tt>
 *    31..7    6    5    4    3    2    1    0
 * +---------+----+----+----+----+----+----+----+
 * |         | SM |    | SS | SE | SB | RE | TX |
 * +---------+----+----+----+----+----+----+----+
 * </tt></pre>
 *
 * Each source updates its transactional state using these bits separately.
 * The state of each source has to be tracked separately in a map for each source.
 * However, event {@link #getIndex() index} is unique across the sources. This is achieved by allocating
 * an event-specific number of most significant bits of {@link #getIndex() index} for use as
 * a {@link #getSource() source} {@link IndexedEventSource#id() id}.
 *
 * <p> {@code TX} (bit 0) &mdash; {@link #TX_PENDING} is an indicator of pending transactional update.
 * It can be retrieved from {@code eventFlags} with the following piece of code:
 *
 * <p><code> boolean txPending = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.{@link #TX_PENDING TX_PENDING}) != 0;</code>
 *
 * <p>When {@code txPending} is {@code true} it means, that an ongoing transaction update that spans multiple events is
 * in process. All events with {@code txPending} {@code true} shall be put into a separate <em>pending list</em>
 * for each source id and should be processed later when an event for this source id with {@code txPending}
 * {@code false} comes.
 *
 * <p> {@code RE} (bit 1) &mdash; {@link #REMOVE_EVENT} is used to indicate that that the event with the
 * corresponding index has to be removed.
 *
 * <p><code> boolean removeEvent = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.{@link #REMOVE_EVENT REMOVE_EVENT}) != 0; </code>
 *
 * <p> {@code SB} (bit 2) &mdash; {@link #SNAPSHOT_BEGIN} is used to indicate when the loading of a snapshot starts.
 *
 * <p><code> boolean snapshotBegin = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.{@link #SNAPSHOT_BEGIN SNAPSHOT_BEGIN}) != 0; </code>
 *
 * <p> Snapshot load starts on new subscription and the first indexed event that arrives for each non-zero source id
 * on new subscription may have {@code snapshotBegin} set to {@code true}. It means, that an ongoing snapshot
 * consisting of multiple events is incoming. All events for this source id shall be put into a separate
 * <em>pending list</em> for each source id.
 *
 * <p> {@code SE} (bit 3) &mdash; {@link #SNAPSHOT_END} or {@code SS} (bit 4) &mdash; {@link #SNAPSHOT_SNIP}
 * are used to indicate the end of a snapshot.
 *
 * <p> <code> boolean snapshotEnd = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.{@link #SNAPSHOT_END SNAPSHOT_END}) != 0; </code>
 * <br><code> boolean snapshotSnip = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.{@link #SNAPSHOT_SNIP SNAPSHOT_SNIP}) != 0; </code>
 *
 * <p>The last event of a snapshot is marked with either {@code snapshotEnd} or {@code snapshotSnip}. At this time, all events
 * from a <em>pending list</em> for the corresponding source can be processed, unless {@code txPending} is also
 * set to {@code true}. In the later case, the processing shall be further delayed due to ongoing transaction.
 *
 * <p>The difference between {@code snapshotEnd} and {@code snapshotSnip} is the following.
 * {@code snapshotEnd} indicates that the data source had sent all the data pertaining to the subscription
 * for the corresponding indexed event, while {@code snapshotSnip} indicates that some limit on the amount
 * of data was reached and while there still might be more data available, it will not be provided.
 *
 * <p> {@code SM} (bit 6) &mdash; {@link #SNAPSHOT_MODE} is used to instruct dxFeed to use snapshot mode.
 * It is intended to be used only for publishing to activate (if not yet activated) snapshot mode.
 * The difference from {@link #SNAPSHOT_BEGIN} flag is that {@link #SNAPSHOT_MODE} only switches on snapshot mode
 * without starting snapshot synchronization protocol.
 *
 * <p> When a snapshot is empty or consists of a single event, then the event can have both {@code snapshotBegin}
 * and {@code snapshotEnd} or {@code snapshotSnip} flags. In case of an empty snapshot, {@code removeEvent} on this event is
 * also set to {@code true}.
 *
 * @param <T> type of the event symbol for this event type.
 */
public interface IndexedEvent<T> extends EventType<T> {
    /* ========== AHTUNG ==========
     * The constants below must be synchronized with similar constants in QD NG EventFlag class!!!
     */

    /**
     * Bit mask to get transaction pending indicator from the value of {@link #getEventFlags() eventFlags} property.
     *
     * <p><code> boolean txPending = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.TX_PENDING) != 0; </code>
     *
     * <p>See "Event Flags" section.
     */
    public static final int TX_PENDING = 0x01;

    /**
     * Bit mask to get removal indicator from the value of {@link #getEventFlags() eventFlags} property.
     *
     * <p><code> boolean removeEvent = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.REMOVE_EVENT) != 0; </code>
     *
     * <p>See "Event Flags" section.
     */
    public static final int REMOVE_EVENT = 0x02;

    /**
     * Bit mask to get snapshot begin indicator from the value of {@link #getEventFlags() eventFlags} property.
     *
     * <p><code> boolean snapshotBegin = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.SNAPSHOT_BEGIN) != 0; </code>
     *
     * <p>See "Event Flags" section.
     */
    public static final int SNAPSHOT_BEGIN = 0x04;

    /**
     * Bit mask to get snapshot end indicator from the value of {@link #getEventFlags() eventFlags} property.
     *
     * <p><code> boolean snapshotEnd = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.SNAPSHOT_END) != 0; </code>
     *
     * <p>See "Event Flags" section.
     */
    public static final int SNAPSHOT_END = 0x08;

    /**
     * Bit mask to get snapshot snip indicator from the value of {@link #getEventFlags() eventFlags} property.
     *
     * <p><code> boolean snapshotSnip = (event.{@link #getEventFlags() getEventFlags}() &amp; IndexedEvent.SNAPSHOT_SNIP) != 0; </code>
     *
     * <p>See "Event Flags" section.
     */
    public static final int SNAPSHOT_SNIP = 0x10;

    /**
     * Bit mask to set snapshot mode indicator into the value of {@link #setEventFlags(int) eventFlags} property.
     * This flag is intended for publishing only.
     * See "Event Flags" section.
     */
    public static final int SNAPSHOT_MODE = 0x40;

    /**
     * Returns source of this event.
     * @return source of this event.
     */
    public IndexedEventSource getSource();

    /**
     * Returns transactional event flags.
     * See "Event Flags" section.
     * @return transactional event flags.
     */
    public int getEventFlags();

    /**
     * Changes transactional event flags.
     * See "Event Flags" section.
     * @param eventFlags transactional event flags.
     * @throws IllegalArgumentException if unsupported event flag is set in {@code eventFlags}.
     */
    public void setEventFlags(int eventFlags);

    /**
     * Returns unique per-symbol index of this event.
     *
     * @return unique per-symbol index of this event.
     */
    public long getIndex();

    /**
     * Changes unique per-symbol index of this event.
     *
     * @param index unique per-symbol index of this event.
     */
    public void setIndex(long index);
}
