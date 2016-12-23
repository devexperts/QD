/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.socket;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.SystemProperties;

/**
 * The <code>ClientSocketConnector</code> handles standard client socket using blocking API.
 */
@MessageConnectorSummary(
	info = "Connects to some host using TCP/IP client socket.",
	addressFormat = "<host>:<port>"
)
public class ClientSocketConnector extends AbstractMessageConnector
	implements SocketHandler.CloseListener, ClientSocketConnectorMBean
{

	protected String host;
	protected int port;
	protected String proxyHost = SystemProperties.getProperty("https.proxyHost", "");
	protected int proxyPort = SystemProperties.getIntProperty("https.proxyPort", 80);
	protected boolean useTls;
	protected TrustManager trustManager;

	protected volatile SocketHandler handler;
	protected ClientSocketSource socketSource;

	/**
	 * Creates new client socket connector.
	 *
	 * @deprecated use {@link #ClientSocketConnector(com.devexperts.connector.proto.ApplicationConnectionFactory, String, int)}
	 * @param factory message adapter factory to use
	 * @param host host to connect to
	 * @param port TCP port to connect to
	 * @throws NullPointerException if {@code factory} or {@code host} is {@code null}
	 */
	@SuppressWarnings({"deprecation", "UnusedDeclaration"})
	@Deprecated
	public ClientSocketConnector(MessageAdapter.Factory factory, String host, int port) {
		this(MessageConnectors.applicationConnectionFactory(factory), host, port);
	}

	/**
	 * Creates new client socket connector.
	 *
	 * @param factory application connection factory factory to use
	 * @param host host to connect to
	 * @param port TCP port to connect to
	 * @throws NullPointerException if {@code factory} or {@code host} is {@code null}
	 */
	public ClientSocketConnector(ApplicationConnectionFactory factory, String host, int port) {
		super(factory);
		if (host == null)
			throw new NullPointerException();
		QDConfig.setDefaultProperties(this, ClientSocketConnectorMBean.class, MessageConnector.class.getName());
		QDConfig.setDefaultProperties(this, ClientSocketConnectorMBean.class, ClientSocketConnector.class.getName());
		this.host = host;
		this.port = port;
	}

	@Override
	public String getAddress() {
		return host + ":" + port;
	}

	@Override
	public String getHost() {
		return host;
	}

	/**
	 * Changes connection host string and restarts connector
	 * if new host string is different from the old one and the connector was running.
	 */
	@Override
	public synchronized void setHost(String host) {
		if (!host.equals(this.host)) { // also checks for null
			log.info("Setting host=" + host);
			this.host = host;
			reconfigure();
		}
	}

	@Override
	public int getPort() {
		return port;
	}

	/**
	 * Changes connection port and restarts connector
	 * if new port is different from the old one and the connector was running.
	 */
	@Override
	public synchronized void setPort(int port) {
		if (port != this.port) {
			log.info("Setting port=" + port);
			this.port = port;
			reconfigure();
		}
	}

	@Override
	public String getProxyHost() {
		return proxyHost;
	}

	@Override
	@MessageConnectorProperty("HTTP proxy host name")
	public synchronized void setProxyHost(String proxyHost) {
		if (!proxyHost.equals(this.proxyHost)) { // also checks for null
			log.info("Setting proxyHost=" + proxyHost);
			this.proxyHost = proxyHost;
			reconfigure();
		}
	}

	@Override
	public int getProxyPort() {
		return proxyPort;
	}

	@Override
	@MessageConnectorProperty("HTTP proxy port")
	public synchronized void setProxyPort(int proxyPort) {
		if (proxyPort != this.proxyPort) {
			log.info("Setting proxyPort=" + proxyPort);
			this.proxyPort = proxyPort;
			reconfigure();
		}
	}

	public boolean getTls() {
		return useTls;
	}

	@MessageConnectorProperty("Use SSLSocketFactory")
	public synchronized void setTls(boolean useTls) {
		if (this.useTls != useTls) {
			log.info("Setting useTls=" + useTls);
			this.useTls = useTls;
			reconfigure();
		}
	}

	public TrustManager getTrustManager() {
		return trustManager;
	}

	/**
	 * Sets the custom {@link TrustManager trust manager} to be used by {@link SSLSocketFactory}.
	 * This property has effect only when the connector is configured to use {@link #setTls(boolean) TLS}.
	 *
	 * @param trustManager trust manager to use instead of the default one, or {@code null} in order to use default one.
	 */
	public void setTrustManager(TrustManager trustManager) {
		if (this.trustManager != trustManager) {
			log.info("Setting trustManager=" + trustManager);
			this.trustManager = trustManager;
			reconfigure();
		}
	}

	/**
	 * Sets stats for this connector. Stats should be of type {@link QDStats.SType#CLIENT_SOCKET_CONNECTOR} or
	 * a suitable substitute. This method may be invoked only once.
	 * @throws IllegalStateException if already set.
	 */
	@Override
	public void setStats(QDStats stats) {
		super.setStats(stats);
		stats.addMBean("ClientSocketConnector", this);
	}

	@Override
	public boolean isActive() {
		return handler != null;
	}

	@Override
	public MessageConnectorState getState() {
		SocketHandler handler = this.handler;
		return handler != null ? handler.getHandlerState() : MessageConnectorState.DISCONNECTED;
	}

	@Override
	public int getConnectionCount() {
		SocketHandler handler = this.handler;
		return handler != null && handler.isConnected() ? 1 : 0;
	}

	@Override
	public EndpointStats retrieveCompleteEndpointStats() {
		EndpointStats stats = super.retrieveCompleteEndpointStats();
		SocketHandler handler = this.handler; // Atomic read.
		if (handler != null) {
			ConnectionStats connectionStats = handler.getActiveConnectionStats(); // Atomic read.
			if (connectionStats != null) {
				stats.addActiveConnectionCount(1);
				stats.addConnectionStats(connectionStats);
			}
		}
		return stats;
	}

	@Override
	public synchronized void start() {
		if (handler != null)
			return;
		log.info("Starting ClientSocketConnector to " + getAddress());
		// create default stats instance if specific one was not provided.
		if (getStats() == null)
			setStats(QDFactory.getDefaultFactory().createStats(QDStats.SType.CLIENT_SOCKET_CONNECTOR, null));
		if (socketSource == null)
			socketSource = new ClientSocketSource(this);
		handler = new SocketHandler(this, socketSource);
		handler.setCloseListener(this);
		handler.start();
	}

	@Override
	protected synchronized Joinable stopImpl() {
		socketSource = null; // forget all reconnection times. Next start immediately connects
		SocketHandler handler = this.handler;
		this.handler = null; // Clear before actual close to avoid recursion.
		if (handler != null) {
			log.info("Stopping ClientSocketConnector");
			handler.close();
		}
		return handler;
	}

	@Override
	public synchronized void handlerClosed(SocketHandler handler) {
		if (handler != this.handler)
			return;
		this.handler = null;
		start();
	}

	@Override
	public String getCurrentHost() {
		SocketHandler handler = this.handler;
		return handler != null ? handler.getHost() : "";
	}

	@Override
	public int getCurrentPort() {
		SocketHandler handler = this.handler;
		return handler != null ? handler.getPort() : 0;
	}
}
