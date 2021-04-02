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
package com.devexperts.connector;

/**
 * ConnectorMBean is a management interface for {@link Connector} class to use with JMX technology.
 */
public interface ConnectorMBean {

    /**
     * Returns heartbeat period (in milliseconds) or 0 if heartbeats are disabled.
     */
    public int getHeartbeatPeriod();

    /**
     * Sets new heartbeat period (in milliseconds) or 0 to disable heartbeats.
     *
     * @throws IllegalArgumentException if specified period is negative.
     */
    public void setHeartbeatPeriod(int heartbeat_period);

    /**
     * Returns heartbeat timeout (in milliseconds) or 0 if heartbeats are disabled.
     */
    public int getHeartbeatTimeout();

    /**
     * Sets new heartbeat timeout (in milliseconds) or 0 to disable heartbeats.
     *
     * @throws IllegalArgumentException if specified timeout is negative.
     */
    public void setHeartbeatTimeout(int heartbeat_timeout);

    /**
     * Returns reconnection period (in milliseconds) or 0 for immediate reeconnect.
     */
    public int getReconnectionPeriod();

    /**
     * Sets new reconnection period (in milliseconds) or 0 for immediate reconnect.
     *
     * @throws IllegalArgumentException if specified period is negative.
     */
    public void setReconnectionPeriod(int reconnection_period);

    /**
     * Returns skew factor in a range [0,1]. Skew factor is used to randomize (skew) waiting periods
     * for heartbeats and reconnects. Periods are randomized from their specified values toward 0
     * according to skew factor with 0 factor meaning no randomization and 1 factor meaning full randomization.
     */
    public double getSkewFactor();

    /**
     * Sets new skew factor in a range [0,1]. See {@link #getSkewFactor} for details.
     *
     * @throws IllegalArgumentException if specified skew factor is out of range.
     */
    public void setSkewFactor(double skew_factor);

    /**
     * Returns address string of this connector. The address string has format of "addr1[/addr2[/...]]",
     * where "addr1" may be either server-side address with format ":port" or client-side address with
     * format "host1:port1[,host2:port2[,...]]". Empty addresses are ignored.
     */
    public String getAddress();

    /**
     * Sets new address string for this connector, closes connections that were established for
     * addresses that are no longer in the address string, starts (if required) new connections
     * for new addresses, does nothing for connections those addresses have not changed.
     * See {@link #getAddress} for details.
     */
    public void setAddress(String address);

    /**
     * Starts this connector. Does nothing if connector is already started.
     */
    public void start();

    /**
     * Stops this connector and closes all open connections. Does nothing if connector is already stopped.
     */
    public void stop();

    /**
     * Restarts this connector by calling {@link #stop} and {@link #start} methods in succession.
     */
    public void restart();
}
