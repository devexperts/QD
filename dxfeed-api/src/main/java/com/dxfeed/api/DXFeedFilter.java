/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api;

import com.devexperts.annotation.Experimental;
import com.devexperts.services.Services;
import com.dxfeed.event.EventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A filter for data or subscription based on DXFeed API coordinates
 * ({@link EventType event type} + subscription symbol).
 *
 * <p>This interface combines both filtering via {@link #accept} and classification via
 * {@link #getCategory} capabilities. A filter can be considered a classifier with a single category.
 *
 * <p>Paired methods are provided because subscription symbols and data symbols can diverge
 * for certain event types. For example, an {@code Order} subscription uses
 * {@link com.dxfeed.api.osub.IndexedEventSubscriptionSymbol IndexedEventSubscriptionSymbol}
 * with an explicit source, while the data event carries the source inside the {@code Order} object.
 * Similarly, a {@code Candle} subscription uses {@code CandleSymbol} with period/price-type attributes,
 * while the data event encodes these in the record name.
 *
 * <p>Instances are created via {@link #newBuilder(DXEndpoint)}. The default implementation is based on QD filters.
 *
 * <h3 id="dynamicFilterMechanismSection">Dynamic filter mechanism</h3>
 *
 * <p>Each {@code DXFeedFilter} instance is immutable after creation. If the filter content
 * can be dynamically updated (e.g. an underlying IPF-based filter), a new filter instance
 * will be created on each update. {@link DXFeedFilterTracker} can be used to track changes
 * and retrieve the latest filter instance. Register a {@link DXFeedFilterListener} via
 * {@link DXFeedFilterTracker#addListener} to receive update notifications.
 *
 * <h3>Threads and locks</h3>
 *
 * This class is thread-safe and can be used concurrently from multiple threads without external synchronization.
 */
@Experimental
public interface DXFeedFilter {

    /**
     * Tests whether the given subscription (event type + symbol) passes this filter.
     * Returns {@code true} if the subscription matches <em>any</em> of the configured categories.
     *
     * <p><b>Warning:</b> Performance may vary when the given {@code eventType} and
     * {@code symbol} map to multiple underlying data streams (e.g. {@code Order} with
     * source COMPOSITE, REGIONAL, or without an explicit source).
     *
     * @param eventType the event type class
     * @param symbol the subscription symbol ({@link String}, {@code CandleSymbol},
     *     {@link com.dxfeed.api.osub.IndexedEventSubscriptionSymbol IndexedEventSubscriptionSymbol}, etc.)
     * @return {@code true} if the subscription passes any configured category
     */
    public boolean accept(Class<? extends EventType<?>> eventType, Object symbol);

    /**
     * Tests whether the given data event passes this filter.
     * Returns {@code true} if the event matches <em>any</em> of the configured categories.
     * Uses the event's own type and symbol, resolving through data-path delegates.
     *
     * @param event the event instance
     * @return {@code true} if the event passes any configured category
     */
    public boolean acceptEvent(EventType<?> event);

    /**
     * Classifies the given subscription into a named category.
     * Evaluation proceeds in the order categories were added; the first matching
     * filter determines the category.
     *
     * <p><b>Warning:</b> Performance may vary when the given {@code eventType} and
     * {@code symbol} map to multiple underlying data streams (e.g. {@code Order} with
     * source COMPOSITE, REGIONAL, or without an explicit source).
     *
     * @param eventType the event type class
     * @param symbol the subscription symbol
     * @return the category name, or {@code null} if no category matches
     */
    @Nullable
    public String getCategory(Class<? extends EventType<?>> eventType, Object symbol);

    /**
     * Classifies the given data event into a named category.
     *
     * @param event the event instance
     * @return the category name, or {@code null} if no category matches
     */
    @Nullable
    public String getEventCategory(EventType<?> event);

    /**
     * Returns {@code true} if this filter is <b>dynamic</b>, that is it can update and send
     * the corresponding notification. Use {@link DXFeedFilterTracker#getCurrentFilter()} to get
     * the most recent instance of the dynamic filter.
     *
     * @return {@code true} if this filter is dynamic
     */
    public boolean isDynamic();

    /**
     * Returns a {@link DXFeedFilterTracker} for monitoring filter changes.
     *
     * <p>Each {@code DXFeedFilter} instance is immutable. When the underlying filter changes,
     * an updated {@code DXFeedFilter} instance is created. The tracker can be used to subscribe
     * for updates and receive the latest filter instance via {@link DXFeedFilterTracker#getCurrentFilter()}.
     *
     * @return the tracker (never null)
     */
    public DXFeedFilterTracker getTracker();

    /**
     * Creates a new {@link Builder} bound to the given {@link DXEndpoint}.
     *
     * @param endpoint the endpoint (required)
     * @return a new builder instance
     * @throws NullPointerException if {@code endpoint} is {@code null}
     */
    public static Builder newBuilder(DXEndpoint endpoint) {
        return new Builder(endpoint);
    }

    /**
     * Builder for constructing {@link DXFeedFilter} instances. Obtain an instance via
     * {@link DXFeedFilter#newBuilder(DXEndpoint)}.
     *
     * <p>The Builder is not thread-safe. Configure it on a single thread before calling
     * {@link #build()}.
     */
    @Experimental
    public final class Builder {

        private final DXEndpoint endpoint;
        private final LinkedHashMap<String, String> categories = new LinkedHashMap<>();

        Builder(DXEndpoint endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        }

        /**
         * Adds a named category with its QD filter expression.
         * Categories are evaluated in the order they are added; the first match wins.
         *
         * @param categoryName the non-empty category name
         * @param filterExpression the non-empty QD filter expression
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if any argument is empty, or a category with the same name has already been added
         */
        public Builder withCategory(String categoryName, String filterExpression) {
            Objects.requireNonNull(categoryName, "categoryName");
            Objects.requireNonNull(filterExpression, "filterExpression");
            if (categoryName.isEmpty())
                throw new IllegalArgumentException("categoryName must not be empty");
            if (filterExpression.isEmpty())
                throw new IllegalArgumentException("filterExpression must not be empty");
            if (categories.putIfAbsent(categoryName, filterExpression) != null)
                throw new IllegalArgumentException("Duplicate category name: " + categoryName);
            return this;
        }

        /**
         * Adds multiple named categories. Evaluation order is the iteration order of the
         * supplied map — callers must pass a map with deterministic iteration order
         * (e.g. {@link LinkedHashMap}) when order matters.
         *
         * @param categories map of category names to filter expressions
         * @return this builder
         * @throws IllegalArgumentException if any category name duplicates an already-added category
         */
        public Builder withCategories(Map<String, String> categories) {
            Objects.requireNonNull(categories, "categories");
            categories.forEach(this::withCategory);
            return this;
        }

        /**
         * Returns the endpoint this builder was created for.
         */
        public DXEndpoint getEndpoint() {
            return endpoint;
        }

        /**
         * Returns a copy of the configured categories preserving insertion order.
         */
        public Map<String, String> getCategories() {
            return new LinkedHashMap<>(categories);
        }

        /**
         * Builds the {@link DXFeedFilter} instance.
         *
         * @return a new filter instance
         * @throws IllegalArgumentException if filter cannot be created
         * @see DXFeedFilterFactory
         */
        public DXFeedFilter build() {
            if (categories.isEmpty())
                throw new IllegalArgumentException("No categories configured");
            for (DXFeedFilterFactory factory : Services.createServices(DXFeedFilterFactory.class, null)) {
                DXFeedFilter filter = factory.create(this);
                if (filter != null)
                    return filter;
            }
            throw new IllegalArgumentException(
                "No applicable " + DXFeedFilterFactory.class.getName() + " found");
        }
    }
}
