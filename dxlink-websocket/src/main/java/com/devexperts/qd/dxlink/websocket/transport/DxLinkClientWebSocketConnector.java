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
package com.devexperts.qd.dxlink.websocket.transport;

import com.devexperts.connector.proto.AbstractTransportConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

/**
 * The <code>DxLinkClientWebSocketConnector</code> handles standard client WebSocket.
 */
@MessageConnectorSummary(
    info = "Connects to some host using the dxLink protocol and the WebSocket client.",
    addressFormat = "<ws or wss>://<host>[:<port>]/<path>?<query>" //like in RFC6455
)
public class DxLinkClientWebSocketConnector extends AbstractMessageConnector
    implements WebSocketTransportConnection.CloseListener, DxLinkClientWebSocketConnectorMBean
{
    protected String address;
    protected String proxyHost = SystemProperties.getProperty("https.proxyHost", "");
    protected int proxyPort = SystemProperties.getIntProperty("https.proxyPort", 80);
    private volatile WebSocketTransportConnection transportConnection;
    private final ReconnectHelper reconnectHelper;

    /**
     * Creates new client WebSocket connector.
     *
     * @param factory application connection factory to use
     * @param url     url to connect to
     * @throws NullPointerException if {@code factory} or {@code url} is {@code null}
     */
    public DxLinkClientWebSocketConnector(ApplicationConnectionFactory factory, String url) {
        super(factory);
        if (url == null)
            throw new NullPointerException();
        QDConfig.setDefaultProperties(this, DxLinkClientWebSocketConnectorMBean.class,
            MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, DxLinkClientWebSocketConnectorMBean.class,
            DxLinkClientWebSocketConnector.class.getName());
        this.reconnectHelper =  new ReconnectHelper(getReconnectDelay());
        this.address = url;
    }

    @Override
    public String getAddress() {
        return this.address;
    }

    /**
     * Changes connection host string and restarts connector if new host string is different from the
     * old one and the connector was running.
     */
    @Override
    public void setAddress(String address) {
        if (!address.equals(this.address)) { // also checks for null
            log.info("Setting address=" + LogUtil.hideCredentials(address));
            this.address = address;
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
            log.info("Setting proxyHost=" + LogUtil.hideCredentials(proxyHost));
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

    /**
     * Sets stats for this connector. Stats should be of type
     * {@link QDStats.SType#CLIENT_SOCKET_CONNECTOR} or a suitable substitute. This method may be
     * invoked only once.
     *
     * @throws IllegalStateException if already set.
     */
    @Override
    public void setStats(QDStats stats) {
        super.setStats(stats);
        stats.addMBean("DxLinkClientWebSocketConnector", this);
    }

    @Override
    public boolean isActive() {
        return transportConnection != null;
    }

    @Override
    public MessageConnectorState getState() {
        WebSocketTransportConnection handler = this.transportConnection;
        return handler != null ? handler.getHandlerState() : MessageConnectorState.DISCONNECTED;
    }

    @Override
    public int getConnectionCount() {
        WebSocketTransportConnection handler = this.transportConnection;
        return handler != null && handler.isConnected() ? 1 : 0;
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        WebSocketTransportConnection handler = this.transportConnection; // Atomic read.
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
        if (transportConnection != null || isClosed())
            return;
        log.info("Starting DxLinkClientWebSocketConnector to " + LogUtil.hideCredentials(getAddress()));
        // create default stats instance if specific one was not provided.
        if (getStats() == null)
            setStats(QDFactory.getDefaultFactory().createStats(QDStats.SType.CLIENT_SOCKET_CONNECTOR, null));
        reconnectHelper.setReconnectDelay(getReconnectDelay()); // update reconnect delay
        transportConnection = new WebSocketTransportConnection(this, address);
        transportConnection.setCloseListener(this);
        transportConnection.start();
    }

    @Override
    protected synchronized Joinable stopImpl() {
        return stopImpl(true);
    }

    @Override
    protected synchronized void restartImpl(boolean fullStop) {
        stopImpl(fullStop);
        start();
    }

    @Override
    public synchronized void handlerClosed(AbstractTransportConnection transportConnection) {
        if (transportConnection != this.transportConnection)
            return;
        this.transportConnection = null;
        start();
    }

    private Joinable stopImpl(boolean fullStop) {
        if (fullStop)
            reconnectHelper.reset();
        WebSocketTransportConnection transportConnection = this.transportConnection;
        this.transportConnection = null; // Clear before actual close to avoid recursion.
        if (transportConnection != null) {
            log.info("Stopping DxLinkClientWebSocketConnector");
            transportConnection.close();
        }
        return transportConnection;
    }

    public ReconnectHelper getReconnectHelper() {
        return reconnectHelper;
    }
}

