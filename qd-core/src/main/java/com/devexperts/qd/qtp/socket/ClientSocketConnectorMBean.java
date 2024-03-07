/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.socket;

import com.devexperts.qd.qtp.MessageConnectorMBean;

/**
 * Management interface for {@link ClientSocketConnector}.
 *
 * @dgen.annotate method {}
 */
public interface ClientSocketConnectorMBean extends MessageConnectorMBean {

    public void setAddress(String addresses);

    /**
     * Host name of IP address.
     * @deprecated To be removed, use {@link #getAddress()} instead.
     */
    @Deprecated
    public String getHost();

    /**
     * Port number of IP address.
     * @deprecated To be removed, use {@link #getAddress()} instead.
     */
    @Deprecated
    public int getPort();

    /**
     * HTTP proxy host name
     */
    public String getProxyHost();

    public void setProxyHost(String host);

    /**
     * HTTP proxy port
     */
    public int getProxyPort();

    public void setProxyPort(int port);

    /**
     * Current IP address of connection when connecting to the cluster
     */
    public String getCurrentHost();

    public int getCurrentPort();

    /**
     * List of addresses (host:port) for all active connections
     */
    public String[] getCurrentAddresses();

    public ConnectOrder getConnectOrder();

    public void setConnectOrder(ConnectOrder connectOrder);

    /**
     * Symbol striper for connector, auto (for system default), or null (default)
     */
    public String getStripe();

    public void setStripe(String stripeConfig);

    public String getRestoreTime();

    public void setRestoreTime(String restoreTime);

    public String restoreNow();

    /**
     * Restore connection graceful with delay in TimePeriod format
     */
    public String restoreGracefully(String gracefulDelay);
}
