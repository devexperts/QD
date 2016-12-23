/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.socket;

import java.net.UnknownHostException;

import com.devexperts.qd.qtp.MessageConnectorMBean;

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

	public void setBindAddr(String String) throws UnknownHostException;
}
