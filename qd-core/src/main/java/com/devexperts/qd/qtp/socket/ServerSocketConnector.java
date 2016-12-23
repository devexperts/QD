/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.socket;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.transport.stats.EndpointStats;

/**
 * The <code>ServerSocketConnector</code> handles standard server socket using blocking API.
 */
@MessageConnectorSummary(
	info = "Creates server TCP/IP socket connection.",
	addressFormat = ":<port>"
)
public class ServerSocketConnector extends AbstractMessageConnector implements ServerSocketConnectorMBean {
	private static final String BIND_ANY_ADDRESS = "*";

	protected int port;
	protected String bindAddrString = BIND_ANY_ADDRESS;
	protected InetAddress bindAddr;
	protected boolean useTls;

	protected final Set<SocketHandler> handlers = new HashSet<>();
	protected final SocketHandler.CloseListener closeListener = new SocketHandler.CloseListener() {
		@Override
		public void handlerClosed(SocketHandler handler) {
			ServerSocketConnector.this.handlerClosed(handler);
		}
	};

	protected volatile SocketAcceptor acceptor;

	/**
	 * Creates new server socket connector.
	 *
	 * @deprecated use {@link #ServerSocketConnector(com.devexperts.connector.proto.ApplicationConnectionFactory, int)}
	 * @param factory message adapter factory to use
	 * @param port TCP port to use
	 * @throws NullPointerException if {@code factory} is {@code null}
	 */
	@SuppressWarnings({"deprecation", "UnusedDeclaration"})
	@Deprecated
	public ServerSocketConnector(MessageAdapter.Factory factory, int port) {
		this(MessageConnectors.applicationConnectionFactory(factory), port);
	}

	/**
	 * Creates new server socket connector.
	 *
	 * @param factory application connection factory to use
	 * @param port TCP port to use
	 * @throws NullPointerException if {@code factory} is {@code null}
	 */
	public ServerSocketConnector(ApplicationConnectionFactory factory, int port) {
		super(factory);
		QDConfig.setDefaultProperties(this, ServerSocketConnectorMBean.class, MessageConnector.class.getName());
		QDConfig.setDefaultProperties(this, ServerSocketConnectorMBean.class, ServerSocketConnector.class.getName());
		this.port = port;
	}

	@Override
	public String getAddress() {
		return bindAddrString + ":" + port;
	}

	/**
	 * Changes local port and restarts connector
	 * if new port is different from the old one and the connector was running.
	 */
	@Override
	public synchronized void setLocalPort(int port) {
		if (this.port != port) {
			log.info("Setting localPort=" + port);
			this.port = port;
			reconfigure();
		}
	}

	@Override
	public int getLocalPort() {
		return port;
	}

	@Override
	public String getBindAddr() {
		return bindAddrString;
	}

	@Override
	@MessageConnectorProperty("Network interface address to bind socket to")
	public synchronized void setBindAddr(String bindAddrString) throws UnknownHostException {
		if (bindAddrString == null)
			bindAddrString = BIND_ANY_ADDRESS;
		if (!bindAddrString.equals(this.bindAddrString)) {
			log.info("Setting bindAddr=" + bindAddrString);
			this.bindAddr = bindAddrString.isEmpty() ? null : InetAddress.getByName(bindAddrString);
			this.bindAddrString = bindAddrString;
			reconfigure();
		}
	}

	public boolean getTls() {
		return useTls;
	}

	@MessageConnectorProperty("Use SSLServerSocketFactory")
	public synchronized void setTls(boolean useTls) {
		if (this.useTls != useTls) {
			log.info("Setting useTls=" + useTls);
			this.useTls = useTls;
			reconfigure();
		}
	}

	/**
	 * Sets stats for this connector. Stats should be of type {@link QDStats.SType#SERVER_SOCKET_CONNECTOR} or
	 * a suitable substitute. This method may be invoked only once.
	 *
	 * @throws IllegalStateException if already set.
	 */
	@Override
	public void setStats(QDStats stats) {
		super.setStats(stats);
		stats.addMBean("ServerSocketConnector", this);
	}

	@Override
	public boolean isActive() {
		return acceptor != null;
	}

	@Override
	public MessageConnectorState getState() {
		SocketAcceptor acceptor = this.acceptor;
		if (acceptor == null)
			return MessageConnectorState.DISCONNECTED;
		return acceptor.isConnected() ? MessageConnectorState.CONNECTED : MessageConnectorState.CONNECTING;
	}

	@Override
	public synchronized int getConnectionCount() {
		return handlers.size();
	}

	@Override
	public synchronized EndpointStats retrieveCompleteEndpointStats() {
		EndpointStats stats = super.retrieveCompleteEndpointStats();
		for (SocketHandler handler : handlers) {
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
		if (acceptor != null)
			return;
		log.info("Starting ServerSocketConnector to " + getAddress());
		// create default stats instance if specific one was not provided.
		if (getStats() == null)
			setStats(QDFactory.getDefaultFactory().createStats(QDStats.SType.SERVER_SOCKET_CONNECTOR, null));
		acceptor = new SocketAcceptor(this);
		acceptor.start();
	}

	@Override
	protected synchronized Joinable stopImpl() {
		SocketAcceptor acceptor = this.acceptor;
		if (acceptor == null)
			return null;
		log.info("Stopping ServerSocketConnector");
		// Note, that the order of below two invocations is important to handle concurrent stop during the
		// creation of ServerSocket that listens on the specified port.
		//      SocketAcceptor.close            modifies SocketAcceptor.closed field,
		// then SocketAcceptor.closeSocketImpl  modified SocketAcceptor.serverSocket field.
    	// SocketAcceptor.doWork method accesses the above fields in reverse order
		acceptor.close();
		acceptor.closeSocketImpl(null);
		this.acceptor = null;
		SocketHandler[] a = handlers.toArray(new SocketHandler[handlers.size()]);
		for (int i = a.length; --i >= 0;)
			a[i].close();
		return new Stopped(acceptor, a);
	}

	private static class Stopped implements Joinable {
		private final SocketAcceptor acceptor;
		private final SocketHandler[] a;

		Stopped(SocketAcceptor acceptor, SocketHandler[] a) {
			this.acceptor = acceptor;
			this.a = a;
		}

		@Override
		public void join() throws InterruptedException {
			acceptor.join();
			for (SocketHandler handler : a)
				handler.join();
		}
	}

	protected synchronized void addHandler(SocketHandler handler) {
		if (acceptor == null)
			handler.close(); // in case of close/connect race.
		else
			handlers.add(handler);
	}

	protected synchronized void handlerClosed(SocketHandler handler) {
		handlers.remove(handler);
	}
}
