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
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.market.Quote;

import java.util.Collection;

/**
 * Represents up-to-date information about some
 * condition or state of an external entity that updates in real-time. For example,
 * a {@link Quote} is an up-to-date information about best bid and best offer for
 * a specific symbol.
 *
 * <p> Lasting events are conflated for
 * each symbol. Last event for each symbol is always delivered to event listeners on
 * subscription, but intermediate (next-to-last) events are not queued anywhere,
 * they are simply discarded as stale events. More recent events
 * represent an up-to-date information about some external entity.
 *
 * <p> Lasting events can be used with {@link DXFeed#getLastEvent(LastingEvent) DXFeed.getLastEvent}
 * and {@link DXFeed#getLastEvents(Collection) DXFeed.getLastEvents}
 * methods to retrieve last events for each symbol.
 *
 * <p> Note, that subscription to all lasting events of a specific type via
 * {@link WildcardSymbol#ALL WildcardSymbol.ALL} symbol object does not benefit from the above
 * advantages of lasting events.
 *
 * @param <T> type of the event symbol for this event type.
 */
public interface LastingEvent<T> extends EventType<T> {
}
