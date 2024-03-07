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

import java.net.UnknownHostException;

/**
 * Management interface for {@link ServerSocketConnector}.
 *
 * @dgen.annotate method {}
 */
public interface ServerSocketConnectorMBean extends MessageConnectorMBean {

    /**
     * Local TCP/IP port
     */
    public int getLocalPort();

    public void setLocalPort(int port);

    /**
     * Network interface address to bind socket to
     */
    public String getBindAddr();

    public void setBindAddr(String bindAddress) throws UnknownHostException;

    /**
     * Max number of connections
     */
    public int getMaxConnections();

    public void setMaxConnections(int maxConnections);
}
