/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
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
}
