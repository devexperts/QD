/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.socket;

import com.devexperts.qd.qtp.MessageConnectorMBean;

/**
 * Management interface for {@link ClientSocketConnector}.
 *
 * @dgen.annotate method {}
 */
public interface ClientSocketConnectorMBean extends MessageConnectorMBean {
    /**
     * Host name of IP address
     */
    public String getHost();

    public void setHost(String host);

    /**
     * TCP/IP port
     */
    public int getPort();

    public void setPort(int port);

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
}
