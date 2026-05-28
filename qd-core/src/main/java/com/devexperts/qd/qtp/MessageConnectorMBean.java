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

import com.devexperts.transport.stats.EndpointStats;

/**
 * Defines properties and operations that every {@link MessageConnector} exposes via JMX.
 *
 * @dgen.annotate method {}
 */
public interface MessageConnectorMBean {
    /**
     * Address string of connector.
     *
     * @return address string of this connector. It is
     * "[host1[:port1],host2[:port2],...]:port" for client socket connector and
     * ":port" for server socket connector.
     */
    public String getAddress();

    /**
     * Starts connector (connection is immediately established).
     * Does nothing if connector is already started.
     */
    public void start();

    /**
     * Stops connector (connection is immediately dropped).
     * Does nothing if connector is stopped.
     */
    public void stop();

    /**
     * Restarts connector (connection is immediately dropped and established again).
     * It basically does {@link #stop} and {@link #start} in sequence.
     */
    public void restart();

    /**
     * Reconnects connector. Opposing to the {@link #restart} method, reconnect should try to follow specified
     * reconnection policy (like choosing next node in cluster) if applicable.
     *
     * <p>Default behavior is just performing {@link #restart}.
     */
    public void reconnect();

    /**
     * True when connector is active (started).
     *
     * @return true if connector is started (not stopped)
     */
    public boolean isActive();

    /**
     * State of connector.
     *
     * @return message connector state.
     */
    public MessageConnectorState getState();

    /**
     * Name of connector.
     *
     * @return connector's short name for logging and monitoring
     */
    public String getName();

    /**
     * Sets connector's short name for logging and monitoring.
     *
     * @param name connector's new short name for logging and monitoring
     */
    public void setName(String name);

    /**
     * User login name
     *
     * Returns connector's user name for authorization.
     */
    public String getUser();

    /**
     * Sets connector's user name for authorization.
     *
     * @param user connector's user name for authorization.
     * @throws NullPointerException if user is null
     */
    public void setUser(String user);

    /**
     * User password for authorization.
     */
    public String getPassword();

    /**
     * Sets password for authorization.
     *
     * @param password password for authorization.
     * @throws NullPointerException if password is null
     */
    public void setPassword(String password);

    /**
     * Number of established connections
     */
    public int getConnectionCount();

    /**
     * Priority for threads associated with this connector
     */
    public int getThreadPriority();

    /**
     * Sets thread priority for all threads created by this connector.
     *
     * @param priority thread priority for all threads created by this connector
     */
    public void setThreadPriority(int priority);

    /**
     * Delay between reconnection attempts in milliseconds
     */
    public long getReconnectDelay();

    /**
     * Sets reconnection delay.
     *
     * @param delay reconnection delay
     */
    public void setReconnectDelay(long delay);

    /**
     * Endpoint statistics since last reset
     *
     * @return endpoint statistics string for this message connector since last
     * call to {@link #resetEndpointStats()}.
     */
    public String getEndpointStats();

    /**
     * Resets endpoint statistics
     */
    public void resetEndpointStats();

    /**
     * Retrieves endpoint statistics for this message connector since its creation
     */
    public EndpointStats retrieveCompleteEndpointStats();

    /**
     * Returns configured input field replacers.
     */
    public String getFieldReplacer();

    /**
     * Sets input field replacers.
     *
     * @param fieldReplacer field replacers specification.
     */
    public void setFieldReplacer(String fieldReplacer);

    // ========== Aggregation Period Management ==========
    // See <a href="../../../../com/dxfeed/api/DXEndpoint.html#aggregationPeriodSection">DXEndpoint, "Aggregation period"</a>
    // for the overview of three aggregation period mechanisms and their interactions.

    /**
     * Returns the current effective aggregation period info as JSON across all active connections.
     * Format: {@code {"min":1.5,"max":2.0}} (seconds with decimals),
     * or {@code {"min":-1,"max":-1}} if not available.
     */
    public default String getAggregationPeriodInfoStr() { return null; }

    /**
     * Returns the client-requested aggregation period string.
     * Returns {@code null} if not set.
     * Applicable for client-side connectors (propagated to all DistributorAdapters).
     */
    public default String getRequestedAggregationPeriod() { return null; }

    /**
     * Sets the requested aggregation period to send to the server via DESCRIBE_PROTOCOL.
     * Propagated to all active DistributorAdapter connections.
     *
     * @param requestedAggregationPeriod aggregation period string (e.g. "1s", "0.5s"),
     *        or {@code "undefined"}/{@code null}/empty to reset (server uses its default)
     */
    public default void setRequestedAggregationPeriod(String requestedAggregationPeriod) {}

    /**
     * Returns the default aggregation period for server-side connections, or {@code null} if not set.
     * Alias: {@link #getAggregationPeriod()}.
     */
    public default String getDefaultAggregationPeriod() { return null; }

    /**
     * Sets the default aggregation period applied when client doesn't specify one.
     * Propagated to all active AgentAdapter connections.
     *
     * @param defaultAggregationPeriod default aggregation period string (e.g. "1s"), or {@code null} to reset
     */
    public default void setDefaultAggregationPeriod(String defaultAggregationPeriod) {}

    /**
     * Returns the default aggregation period (alias for {@link #getDefaultAggregationPeriod()}).
     */
    public default String getAggregationPeriod() {
        return getDefaultAggregationPeriod();
    }

    /**
     * Returns the minimum aggregation period bound for server-side connections, or {@code null} if not set.
     */
    public default String getMinAggregationPeriod() { return null; }

    /**
     * Sets the minimum bound for aggregation period validation.
     * Client requests below this value will be clamped up.
     *
     * @param minAggregationPeriod minimum aggregation period string (e.g. "0.5s"), or {@code null} to reset
     */
    public default void setMinAggregationPeriod(String minAggregationPeriod) {}

    /**
     * Returns the maximum aggregation period bound for server-side connections, or {@code null} if not set.
     */
    public default String getMaxAggregationPeriod() { return null; }

    /**
     * Sets the maximum bound for aggregation period validation.
     * Client requests above this value will be clamped down.
     *
     * @param maxAggregationPeriod maximum aggregation period string (e.g. "5s"), or {@code null} to reset
     */
    public default void setMaxAggregationPeriod(String maxAggregationPeriod) {}

}
