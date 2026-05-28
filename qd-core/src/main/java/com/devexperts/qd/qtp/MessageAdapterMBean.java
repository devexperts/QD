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
package com.devexperts.qd.qtp;

/**
 * Management interface for {@link MessageAdapter}.
 */
public interface MessageAdapterMBean {
    public boolean isAlive();
    public void close();

    // ========== Aggregation Period Management ==========

    /**
     * Returns the current effective aggregation period info as JSON.
     * Format: {@code {"min":1.5,"max":2.0}} (seconds with decimals).
     */
    default String getAggregationPeriodInfoStr() { return null; }

    /**
     * Returns the client-requested aggregation period string.
     * Returns {@code null} if not set. Applicable for client-side adapters (DistributorAdapter).
     */
    default String getRequestedAggregationPeriod() { return null; }

    /**
     * Sets the requested aggregation period to send to the server.
     *
     * @param requestedAggregationPeriod aggregation period string (e.g. "1s"), or {@code "undefined"}/empty to reset
     */
    default void setRequestedAggregationPeriod(String requestedAggregationPeriod) {}

    /**
     * Returns the default aggregation period for server-side adapters (AgentAdapter).
     */
    default String getDefaultAggregationPeriod() { return null; }

    /**
     * Sets the default aggregation period applied when client doesn't specify one.
     */
    default void setDefaultAggregationPeriod(String defaultAggregationPeriod) {}

    /**
     * Returns the minimum aggregation period bound.
     */
    default String getMinAggregationPeriod() { return null; }

    /**
     * Sets the minimum bound for aggregation period validation.
     */
    default void setMinAggregationPeriod(String minAggregationPeriod) {}

    /**
     * Returns the maximum aggregation period bound.
     */
    default String getMaxAggregationPeriod() { return null; }

    /**
     * Sets the maximum bound for aggregation period validation.
     */
    default void setMaxAggregationPeriod(String maxAggregationPeriod) {}
}
