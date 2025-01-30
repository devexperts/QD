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
package com.dxfeed.model;

import com.devexperts.annotation.Experimental;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.SubscriptionController;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.option.Series;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Represents an incremental transaction model for {@link IndexedEvent indexed events}.
 * Indexed event stream consists of snapshots and updates for these snapshots.
 * This model implements snapshot and transaction processing, subscription management, and listener notifications.
 *
 * <p>Designed to handle incremental transactions, this model ensures that users only see the list of events in
 * a consistent state. It delays processing incoming events that are part of an incomplete snapshot or an ongoing
 * transaction until the snapshot is complete or the transaction has concluded.
 *
 * <h3>Processing and notifications</h3>
 *
 * <p>This model notifies the user of received transactions through an installed
 * {@link Listener#eventsReceived(List, boolean) listener} and rids the user from writing finite-state machine
 * to handle the transaction logic. The model notifies the listener as soon as an event batch has been processed.
 *
 * <p>Transactions can represent snapshots or updates to these snapshots. In the case of a snapshot,
 * the {@code isSnapshot} flag is set to {@code true}, indicating that all state based on previously received
 * events should be cleared.
 *
 * <p>This model processes the snapshots as follows:
 * <ul>
 *     <li>Events that are marked for removal will be removed.</li>
 *     <li>Repeated{@link IndexedEvent#getIndex() indexes} will be conflated.</li>
 *     <li>{@link IndexedEvent#getEventFlags() Event flags} of events are set to zero.</li>
 * </ul>
 *
 * <p>If several snapshots follow each other, the model will notify each one separately.
 * However, the model cannot ensure this if snapshots were conflated higher up the communication channel e.g.,
 * on a multiplexor or inside <em>history buffer</em>.
 *
 * <p>The snapshot can also be empty or not provided for some events with some sources e.g., {@link Order}
 * with sources {@link OrderSource#REGIONAL REGIONAL} and {@link OrderSource#COMPOSITE COMPOSITE} because they're
 * synthetic sources.
 *
 * <p>This model does not modify transactions that are not a snapshot in any way. However, the model cannot
 * guarantee that no events have been conflated if this has occurred higher up the communication channel e.g.,
 * on a multiplexor or inside <em>history buffer</em>. In some cases this can be controlled, for example,
 * setting the system property {@code -Ddxscheme.fob=true} disables conflation for {@link Order} type events.
 *
 * <h3>Configuration</h3>
 *
 * <p>This model must be configured using the {@link Builder builder}, as most configuration settings cannot be changed
 * once the model is built. It requires configuration with a {@link Builder#withSymbol(String) symbol} and
 * {@link Builder#withSource(IndexedEventSource) source} for subscription,
 * {@link Builder#withListener(Listener) listener} for receiving notifications, and it must be
 * {@link Builder#withFeed(DXFeed) attached} to a {@link DXFeed feed} instance to begin operation.
 *
 * <p>Some configurations can be changed after the model is built. See {@link #getSubscriptionController()}
 * for direct control of the underlying {@link DXFeedSubscription subscription}.
 *
 * <p>This model supports subscription to only one symbol and source. Once the model is built, the symbol and source
 * cannot be changed. To work with multiple symbols/sources, create multiple instances of this model.
 *
 * <h3>Sample usage</h3>
 *
 * <pre>
 * {@code
 * IndexedEventTxModel<Order> model = IndexedEventTxModel.newBuilder(Order.class)
 *     .withFeed(DXFeed.getInstance())
 *     .withSymbol("AAPL")
 *     .withSource(OrderSource.ntv) // NASDAQ Total View. Source for the price level book.
 *     .withListener((events, isSnapshot) -> {
 *         System.out.println((isSnapshot ? "Snapshot" : "Update") + " | Number of events: " + events.size());
 *     })
 *     .build();
 * }
 * </pre>
 *
 * <h3>Resource management and closed models</h3>
 *
 * <p>Attached model is a potential memory leak. If the pointer to attached model is lost, then there is no way
 * to detach this model from the feed and the model will not be reclaimed by the garbage collector as long as the
 * corresponding feed is still used. Detached model can be reclaimed by the garbage collector, but detaching model
 * requires knowing the pointer to the feed at the place of the call, which is not always convenient.
 *
 * <p>The convenient way to detach model from the feed is to call its {@link #close close} method. Closed model
 * becomes permanently detached from all feeds, removes all its listeners and is guaranteed to be reclaimable by
 * the garbage collector as soon as all external references to it are cleared.
 *
 * <h3>Threads and locks</h3>
 *
 * <p>This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 *
 * <p>Notification on model changes are invoked from a separate thread via the executor.
 * Default executor for all models is configured with {@link DXEndpoint#executor(Executor) DXEndpoint.executor}
 * method. Each model can individually override its executor with
 * {@link Builder#withExecutor(Executor) Builder.withExecutor} method.
 * The corresponding {@link Listener#eventsReceived(List, boolean) eventsReceived} notification is guaranteed
 * to never be concurrent, even though it may happen from different threads if executor is multi-threaded.
 *
 * @param <E> the type of indexed events processed by this model.
 */
@Experimental
public final class IndexedEventTxModel<E extends IndexedEvent<?>> implements AutoCloseable {
    private final IndexedEventSubscriptionSymbol<String> symbol;
    private final TxEventProcessor<E> txEventProcessor;
    private final DXFeedSubscription<E> subscription;

    /**
     * The listener interface for receiving indexed events of the specified type {@code E}
     * from the {@link IndexedEventTxModel}.
     *
     * @param <E> the type of indexed events.
     */
    @FunctionalInterface
    public interface Listener<E extends IndexedEvent<?>> {
        /**
         * Invoked when a complete transaction (one or more) is received.
         *
         * @param events the list of received events representing one or more completed transactions.
         * @param isSnapshot {@code true} if the events form a snapshot; {@code false} otherwise.
         */
        public void eventsReceived(List<E> events, boolean isSnapshot);
    }

    private IndexedEventTxModel(Builder<E> builder) {
        Listener<E> listener = Objects.requireNonNull(builder.listener, "The 'listener' cannot be null");
        symbol = new IndexedEventSubscriptionSymbol<>(builder.symbol, builder.source);
        txEventProcessor = new TxEventProcessor<>(listener::eventsReceived);
        subscription = new DXFeedSubscription<>(builder.eventType);
        subscription.addEventListener(this::processEvents);
        subscription.setEventsBatchLimit(builder.eventsBatchLimit);
        subscription.setAggregationPeriod(builder.aggregationPeriod);
        subscription.setExecutor(builder.executor);
        if (builder.feed != null)
            subscription.attach(builder.feed);
        subscription.setSymbols(symbol);
    }

    /**
     * Factory method to create a new builder for this model.
     *
     * @param eventType the class type of indexed event.
     * @param <E> the type of indexed events processed by the model.
     * @return a new {@link Builder builder} instance.
     */
    public static <E extends IndexedEvent<?>> Builder<E> newBuilder(Class<E> eventType) {
        return new Builder<>(eventType);
    }

    /**
     * Returns the indexed event subscription symbol associated with this model.
     *
     * @return  the indexed event subscription symbol associated with this model.
     */
    public IndexedEventSubscriptionSymbol<String> getIndexedEventSubscriptionSymbol() {
        return symbol;
    }

    /**
     * Returns the symbol associated with this model.
     *
     * @return the symbol associated with this model.
     */
    public String getSymbol() {
        return symbol.getEventSymbol();
    }

    /**
     * Returns the source associated with this model.
     *
     * @return the source associated with this model.
     */
    public IndexedEventSource getSource() {
        return symbol.getSource();
    }

    /**
     * Returns the subscription controller for underlying subscription.
     *
     * @return the subscription controller for underlying subscription.
     */
    public SubscriptionController getSubscriptionController() {
        return subscription.getSubscriptionController();
    }

    /**
     * Returns whether this model is closed.
     *
     * @return {@code true} if this model is closed; {@code false} otherwise.
     * @see #close
     */
    public boolean isClosed() {
        return subscription.isClosed();
    }

    /**
     * Closes this model and makes it <i>permanently detached</i>.
     *
     * <p>This method ensures that the model can be safely garbage-collected
     * when all outside references to it are lost.
     */
    @Override
    public void close() {
        subscription.close();
    }

    // package-private access for testing purposes only.
    void processEvents(List<E> events) {
        txEventProcessor.processEvents(events);
    }

    /**
     * Builder class for {@link IndexedEventTxModel}.
     *
     * @param <E> the type of indexed events processed by the model being created.
     */
    public static final class Builder<E extends IndexedEvent<?>> {
        private final Class<E> eventType;
        private DXFeed feed;
        private String symbol;
        private IndexedEventSource source;
        private Listener<E> listener;
        private int eventsBatchLimit = DXFeedSubscription.OPTIMAL_BATCH_LIMIT;
        private TimePeriod aggregationPeriod;
        private Executor executor;

        /**
         * Constructs a new {@link Builder builder} for the specified event type.
         *
         * @param eventType the class type of indexed event.
         */
        public Builder(Class<E> eventType) {
            this.eventType = eventType;
        }

        /**
         * Sets the {@link DXFeed feed} for the model being created.
         *
         * <p>The {@link DXFeed feed} can also be attached later, after the model has been built using the
         * {@link #getSubscriptionController() subscription controller}.
         *
         * @param feed the {@link DXFeed feed}.
         * @return {@code this} builder.
         */
        public Builder<E> withFeed(DXFeed feed) {
            this.feed = feed;
            return this;
        }

        /**
         * Sets the subscription symbol and its source for the model being created.
         *
         * <p>This symbol and source cannot be added or changed after the model has been built.
         *
         * @param symbol the subscription symbol.
         * @return {@code this} builder.
         * @see #withSymbol(String)
         * @see #withSource(IndexedEventSource)
         */
        public Builder<E> withSymbol(IndexedEventSubscriptionSymbol<?> symbol) {
            this.symbol = symbol.getEventSymbol().toString();
            this.source = symbol.getSource();
            return this;
        }

        /**
         * Sets the subscription symbol for the model being created.
         *
         * <p>The symbol cannot be added or changed after the model has been built.
         *
         * @param symbol the subscription symbol.
         * @return {@code this} builder.
         */
        public Builder<E> withSymbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        /**
         * Sets the source to subscribe to.
         *
         * <p>Use the {@link IndexedEventSource#DEFAULT DEFAULT} value for {@code source} with events that do not
         * have multiple sources (like {@link Series}). For events with multiple sources (like {@link Order},
         * {@link AnalyticOrder}, {@link OtcMarketsOrder} and {@link SpreadOrder}), use an event-specific
         * source class (for example, {@link OrderSource}).
         *
         * <p>The source cannot be added or changed after the model has been built.
         *
         * @param source the specified source.
         * @return {@code this} builder.
         */
        public Builder<E> withSource(IndexedEventSource source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the listener for transaction notifications.
         * The notification is invoked from a separate thread via the {@link #withExecutor(Executor) executor}.
         *
         * <p>The listener cannot be added or changed after the model has been built.
         *
         * @param listener the model listener.
         * @return {@code this} builder.
         */
        public Builder<E> withListener(Listener<E> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Sets maximum number of events in the single notification of {@link Listener#eventsReceived eventsReceived}.
         *
         * <p>This value can be exceeded if the size of the received transaction exceeds the set limit.
         *
         * <p>This value can be change later, after the model has been built using the
         * {@link #getSubscriptionController() subscription controller}.
         *
         * @param eventsBatchLimit the notification events limit.
         */
        public Builder<E> withEventsBatchLimit(int eventsBatchLimit) {
            this.eventsBatchLimit = eventsBatchLimit;
            return this;
        }

        /**
         * Sets the aggregation period for data that limits the rate of data notifications.
         * For example, setting the value to "0.1s" limits notification to once every 100ms (at most 10 per second).
         *
         * <p>This value can be change later, after the model has been built using the
         * {@link #getSubscriptionController() subscription controller}.
         *
         * @param aggregationPeriod the aggregation period for data.
         */
        public Builder<E> withAggregationPeriod(TimePeriod aggregationPeriod) {
            this.aggregationPeriod = aggregationPeriod;
            return this;
        }

        /**
         * Sets the executor for processing listener notifications.
         *
         * <p>Default executor for all models is configured with
         * {@link DXEndpoint#executor(Executor) DXEndpoint.executor} method.
         *
         * <p>This executor can be change later, after the model has been built using the
         * {@link #getSubscriptionController() subscription controller}.
         *
         * @param executor the executor instance.
         * @return {@code this} builder.
         */
        public Builder<E> withExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds a new instance of {@link IndexedEventTxModel} with the provided configuration.
         *
         * @return the created model.
         */
        public IndexedEventTxModel<E> build() {
            return new IndexedEventTxModel<>(this);
        }
    }
}
