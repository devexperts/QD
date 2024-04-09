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

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.codec.CodecFactory;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.qd.qtp.AbstractServerConnector;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The <code>ServerSocketConnector</code> handles standard server socket using blocking API.
 */
@MessageConnectorSummary(
    info = "Creates server TCP/IP socket connection.",
    addressFormat = ":<port>"
)
public class ServerSocketConnector extends AbstractServerConnector implements ServerSocketConnectorMBean {

    protected volatile boolean useTls;

    protected final Set<SocketHandler> handlers = new HashSet<>();
    protected final SocketHandler.CloseListener closeListener = this::handlerClosed;

    protected volatile SocketAcceptor acceptor;

    /**
     * Creates new server socket connector.
     *
     * @param factory message adapter factory to use
     * @param port TCP port to use
     * @throws NullPointerException if {@code factory} is {@code null}
     * @deprecated use {@link #ServerSocketConnector(ApplicationConnectionFactory, int)}
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

    public boolean getTls() {
        return useTls;
    }

    @MessageConnectorProperty(
        value = "Use SSLConnectionFactory",
        deprecated = "Use tls or ssl codec in address string. For example tls+<address>"
    )
    public synchronized void setTls(boolean useTls) {
        if (this.useTls != useTls) {
            if (useTls) {
                CodecFactory sslCodecFactory = Services.createService(
                    CodecFactory.class, null, "com.devexperts.connector.codec.ssl.SSLCodecFactory");
                if (sslCodecFactory == null) {
                    log.error("SSLCodecFactory is not found. Using the SSL protocol is not supported");
                    return;
                }
                ApplicationConnectionFactory factory = sslCodecFactory.createCodec("ssl", getFactory());
                factory.setConfiguration(ConfigurationKey.create("isServer", String.class), "true");
                setFactory(factory);
            } else {
                CodecConnectionFactory sslFactory = (CodecConnectionFactory) getFactory();
                if (!sslFactory.getClass().getSimpleName().contains("SSLCodecFactory")) {
                    log.error("SSLCodecFactory not found. SSL protocol is not used");
                    return;
                }
                setFactory(sslFactory.getDelegate());
            }
            log.info("Setting useTls=" + useTls);
            this.useTls = useTls;
            reconfigure();
        }
        log.warn("WARNING: DEPRECATED use \"setTls()\" method from program or \"tls\" property from address string. " +
            "Use tls or ssl codec in address string. For example tls+<address>");
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
    public boolean isAccepting() {
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
            ConnectionStats connectionStats = handler.getActiveConnectionStats();
            if (connectionStats != null) {
                stats.addActiveConnectionCount(1);
                stats.addConnectionStats(connectionStats);
            }
        }
        return stats;
    }

    @Override
    protected synchronized void reconfigure() {
        getFactory().reinitConfiguration();
        if (isActive()) {
            boolean needStartAcceptor = stopAcceptorInternal();
            closeSocketHandlers();
            startImpl(needStartAcceptor);
            notifyMessageConnectorListeners();
        }
    }

    protected void startImpl(boolean startAcceptor) {
        if (isClosed()) {
            return;
        }
        if (!isActive()) {
            log.info("Starting ServerSocketConnector to " + LogUtil.hideCredentials(getAddress()));
            active = true;
        }
        if (startAcceptor) {
            cancelScheduledTask();
            startAcceptorInternal();
        }
    }

    @Override
    protected synchronized Joinable stopImpl() {
        if (!isActive()) {
            return null;
        }
        log.info("Stopping ServerSocketConnector");
        cancelScheduledTask();
        SocketAcceptor acceptor = stopServerAcceptor();
        SocketHandler[] socketHandlers = closeSocketHandlers();
        active = false;
        return new Stopped(acceptor, socketHandlers);
    }

    private SocketAcceptor stopServerAcceptor() {
        SocketAcceptor acceptor = this.acceptor;
        if (acceptor == null) {
            return null;
        }
        log.info("Stopping ServerSocketConnector");
        // Note, that the order of below two invocations is important to handle concurrent stop during the
        // creation of ServerSocket that listens on the specified port.
        //      SocketAcceptor.close            modifies SocketAcceptor.closed field,
        // then SocketAcceptor.closeSocketImpl  modified SocketAcceptor.serverSocket field.
        // SocketAcceptor.doWork method accesses the above fields in reverse order
        acceptor.close();
        acceptor.closeSocketImpl(null);
        this.acceptor = null;
        return acceptor;
    }

    @Override
    protected boolean stopAcceptorInternal() {
        return stopServerAcceptor() != null;
    }

    @Override
    protected void startAcceptorInternal() {
        if (isActive() && !isAccepting()) {
            acceptor = new SocketAcceptor(this);
            acceptor.start();
            log.info("Acceptor started for address: " + LogUtil.hideCredentials(getAddress()));
        }
    }

    private SocketHandler[] closeSocketHandlers() {
        SocketHandler[] socketHandlers = handlers.toArray(new SocketHandler[0]);
        for (SocketHandler handler : socketHandlers) {
            handler.close();
        }
        return socketHandlers;
    }

    private static class Stopped implements Joinable {
        private final SocketAcceptor acceptor;
        private final SocketHandler[] socketHandlers;

        Stopped(SocketAcceptor acceptor, SocketHandler[] socketHandlers) {
            this.acceptor = acceptor;
            this.socketHandlers = socketHandlers;
        }

        @Override
        public void join() throws InterruptedException {
            if (acceptor != null) {
                acceptor.join();
            }
            for (SocketHandler handler : socketHandlers) {
                handler.join();
            }
        }
    }

    protected synchronized void addHandler(SocketHandler handler) {
        if (!isAccepting()) {
            handler.close(); // in case of close/connect race.
        } else {
            handlers.add(handler);
        }
    }

    protected synchronized void handlerClosed(SocketHandler handler) {
        handlers.remove(handler);
    }

    protected boolean isNewConnectionAllowed() {
        int maxConnections = this.maxConnections;
        return maxConnections == 0 || getConnectionCount() < maxConnections;
    }

    @Override
    protected synchronized List<Closeable> getConnections() {
        return new ArrayList<>(handlers);
    }
}
