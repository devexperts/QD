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
package com.devexperts.qd.qtp;

import java.net.UnknownHostException;

/**
 * Management interface for {@link AbstractMessageConnector}.
 *
 * @dgen.annotate method {}
 */
public interface ServerConnectorMBean extends MessageConnectorMBean {
    /**
     * Local TCP/IP port
     */
    int getLocalPort();

    void setLocalPort(int port);

    /**
     * Network interface address to bind socket to
     */
    String getBindAddr();

    void setBindAddr(String bindAddress) throws UnknownHostException;

    /**
     * Max number of connections
     */
    int getMaxConnections();

    void setMaxConnections(int maxConnections);

    /**
     * Checks whether the connection can be accepted
     */
    boolean isAccepting();

    /**
     * Stop accepting new connections
     */
    void stopAcceptor();

    /**
     * Stop connector gracefully within period of time
     */
    String stopGracefully(String period);

    /**
     * Gracefully close the percentage of connections within period of time
     */
    String closeConnections(double percent, String period, boolean stopAcceptor);
}
