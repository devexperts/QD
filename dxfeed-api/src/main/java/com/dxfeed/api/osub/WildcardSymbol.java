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
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.market.TimeAndSale;

import java.io.Serializable;
import java.util.Collection;

/**
 * Represents [wildcard] subscription to all events of the specific event type.
 * The {@link WildcardSymbol#ALL WildcardSymbol.ALL} constant can be added to any
 * {@link DXFeedSubscription} instance with {@link DXFeedSubscription#addSymbols(Collection) addSymbols} method
 * to the effect of subscribing to all possible event symbols. The corresponding subscription will start
 * receiving all published events of the corresponding types.
 *
 * <p><b>NOTE:</b> Wildcard subscription can create extremely high network and CPU load for certain kinds of
 * high-frequency events like quotes. It requires a special arrangement on the side of upstream data provider and
 * is disabled by default in upstream feed configuration. Make that sure you have adequate resources and understand
 * the impact before using it. It can be used for low-frequency events only (like Forex quotes), because each instance
 * of {@link DXFeedSubscription} processes events in a single thread and there is no provision to load-balance wildcard
 * subscription amongst multiple threads. Contact your data provider for the corresponding configuration arrangement if needed.
 *
 * <p>You need to create {@link DXEndpoint} with {@link DXEndpoint#DXFEED_WILDCARD_ENABLE_PROPERTY DXFEED_WILDCARD_ENABLE_PROPERTY}
 * property in order to support subscription to this wildcard symbols via
 * {@link DXFeed} and to observe wildcard subscription via {@link DXPublisher}.
 *
 * <h3>Sample usage</h3>
 *
 * See {@link DXFeed} on how to create connection to the feed.
 * The following code creates listener that prints all {@link TimeAndSale} events that are
 * coming from the feed using wildcard subscription symbol:
 *
 * <pre><tt>
 * DXFeedSubscription&lt;{@link TimeAndSale TimeAndSale}&gt; sub = feed.{@link DXFeed#createSubscription(Class) createSubscription}({@link TimeAndSale TimeAndSale.class});
 * sub.{@link DXFeedSubscription#addEventListener addEventListener}(new DXFeedEventListener&lt;TimeAndSale&gt;() {
 *     public void eventsReceived(List&lt;TimeAndSale&gt; events) {
 *         for (TimeAndSale event : events)
 *             System.out.println(event);
 *     }
 * });
 * sub.{@link DXFeedSubscription#addSymbols(Object...) addSymbols}({@link WildcardSymbol#ALL WildcardSymbol.ALL});</tt></pre>
 *
 * <h3>Observing wildcard subscription</h3>
 *
 * Any instance of {@link ObservableSubscription} that is retrieved via
 * {@link DXPublisher#getSubscription(Class) DXPublisher.getSubscription} method can observe
 * {@link WildcardSymbol#ALL WildcardSymbol.ALL} object in its set of symbols if any feed consumer
 * subscribes to wildcard symbol. The recommended approach is to use {@code instanceof WildcardSymbol} check if support
 * of wildcard subscription is required.
 *
 * <h3>Limitations</h3>
 *
 * Do not mix {@code WildcardSymbol} subscription and subscription to other symbols in a single instance
 * of {@link DXFeedSubscription}. Doing so may result in a duplication of events and/or other implementation-specific
 * adverse effects.
 *
 * <p> Subscription via wildcard symbol for {@link LastingEvent} types does not count for the purpose of
 * {@link DXFeed#getLastEvent(LastingEvent) DXFeed.getLastEvent} and
 * {@link DXFeed#getLastEvents(Collection) DXFeed.getLastEvents} methods. Lasting events that are received
 * via wildcard subscription are not conflated in the usual way. They may incur significantly higher resource
 * requirements to process them and may get queued when network and CPU resources are inadequate.
 *
 * <h3>Future compatibility</h3>
 *
 * In the future this class may be extended with support for more specific wildcards in addition
 * to {@link #ALL ALL}. Data provides that publish events via {@link DXPublisher}, track subscription via
 * {@link DXPublisher#getSubscription(Class) DXPublisher.getSubscription} method, and plan to detect wildcard
 * subscription in order to start publishing all possible events, should use
 * {@code symbol instanceof WildcardSymbol} code
 * to detect wildcard symbols to be future-proof.
 */
public class WildcardSymbol implements Serializable {
    private static final long serialVersionUID = 0;

    /**
     * Symbol prefix that is reserved for wildcard subscriptions.
     * Any subscription starting with "*" is ignored with the exception of {@link WildcardSymbol}
     * subscription.
     */
    public static final String RESERVED_PREFIX = "*";

    /**
     * Represents [wildcard] subscription to all events of the specific event type.
     *
     * <p><b>NOTE:</b> Wildcard subscription can create extremely high network and CPU load for certain kinds of
     * high-frequency events like quotes. It requires a special arrangement on the side of upstream data provider and
     * is disabled by default in upstream feed configuration. Make that sure you have adequate resources and understand
     * the impact before using it. It can be used for low-frequency events only (like Forex quotes), because each instance
     * of {@link DXFeedSubscription} processes events in a single thread and there is no provision to load-balance wildcard
     * subscription amongst multiple threads.
     * Contact your data provider for the corresponding configuration arrangement if needed.
     *
     * @see WildcardSymbol
     */
    public static final WildcardSymbol ALL = new WildcardSymbol(RESERVED_PREFIX);

    private final String symbol;

    private WildcardSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns "*".
     */
    @Override
    public String toString() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof WildcardSymbol && symbol.equals(((WildcardSymbol) o).symbol);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}
