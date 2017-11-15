/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.http;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.io.ChunkPool;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

@MessageConnectorSummary(
    info = "Connects to some url by http or https protocol.",
    addressFormat = "<url>"
)
public class HttpConnector extends AbstractMessageConnector
    implements HttpConnectorMBean
{
    /**
     * Connection id parameters might be specified in URL to distinguish multiple
     * connections that work in a single HTTP session (for example use address like
     * "http://localhost:9090/sample/QDServlet?connection-id=qdc0").
     * Each connection should be assigned a unique id, otherwise disaster happens.
     */
    public static final String CONNECTION_ID_PARAMETER = "connection-id";

    /**
     * Default connection ID that is used when {@link #CONNECTION_ID_PARAMETER} request
     * property is not specified.
     */
    public static final String DEFAULT_CONNECTION_ID = "qdc";

    public static final int DEFAULT_FETCH_COUNT = 5;

    public static final long DEFAULT_FETCH_DELAY = 1000;

    public static final long DEFAULT_UPDATE_DELAY = 5000;

    /**
     * Internal header that is used to indicate establishment of new connection.
     */
    static final String NEW_CONNECTION_HTTP_PROPERTY = "X-NewConnection";

    /**
     * Internal header that indicates that more messages are pending delivery.
     */
    static final String MORE_MESSAGES_HTTP_PROPERTY = "X-MoreMessages";

    final ChunkPool chunkPool;

    private String address;
    private int fetchCount = DEFAULT_FETCH_COUNT;
    private long fetchDelay = DEFAULT_FETCH_DELAY;
    private long updateDelay = DEFAULT_UPDATE_DELAY;
    private String proxyHost = SystemProperties.getProperty("http.proxyHost", "");
    private int proxyPort = SystemProperties.getIntProperty("http.proxyPort", -1);
    private ReconnectHelper reconnectHelper; // created when needed

    private boolean file;

    protected volatile AbstractConnectionHandler<?> handler;

    /**
     * Creates new HTTP socket connector.
     *
     * @param factory application connection factory to use
     * @param address address HTTP address to connect to
     * @throws NullPointerException if {@code factory} or {@code address} is {@code null}
     */
    public HttpConnector(ApplicationConnectionFactory factory, String address) {
        super(factory);
        if (address == null)
            throw new NullPointerException();
        QDConfig.setDefaultProperties(this, HttpConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, HttpConnectorMBean.class, HttpConnector.class.getName());
        this.address = address;
        chunkPool = factory.getChunkPool();
    }

    @Override
    public synchronized String getAddress() {
        return address;
    }

    @Override
    public synchronized void setAddress(String address) {
        if (!address.equals(this.address)) {  // also checks for null
            log.info("Setting address=" + LogUtil.hideCredentials(address));
            this.address = address;
            reconfigure();
        }
    }

    @Override
    public synchronized int getFetchCount() {
        return fetchCount;
    }

    @Override
    @MessageConnectorProperty("Number of times to use fetchDelay after sending piece of data")
    public synchronized void setFetchCount(int fetchCount) {
        if (fetchCount != this.fetchCount) {
            log.info("Setting fetchCount=" + fetchCount);
            this.fetchCount = fetchCount;
            reconfigure();
        }
    }

    @Override
    public synchronized long getFetchDelay() {
        return fetchDelay;
    }

    @Override
    @MessageConnectorProperty("Frequency (in ms) of updating data when data was sent recently")
    public synchronized void setFetchDelay(long fetchDelay) {
        if (fetchDelay != this.fetchDelay) {
            log.info("Setting fetchDelay=" + fetchDelay);
            this.fetchDelay = fetchDelay;
            reconfigure();
        }
    }

    @Override
    public synchronized long getUpdateDelay() {
        return updateDelay;
    }

    @Override
    @MessageConnectorProperty("Frequency (in ms) of checking if new data is available")
    public synchronized void setUpdateDelay(long updateDelay) {
        if (updateDelay != this.updateDelay) {
            log.info("Setting updateDelay=" + updateDelay);
            this.updateDelay = updateDelay;
            reconfigure();
        }
    }

    @Override
    public synchronized String getProxyHost() {
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
    public synchronized int getProxyPort() {
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

    @Override
    public synchronized boolean isFile() {
        return file;
    }

    @Override
    @MessageConnectorProperty("Assume plain file on this URL instead of QDS servlet")
    public synchronized void setFile(boolean file) {
        if (this.file != file) {
            log.info("Setting file=" + LogUtil.hideCredentials(file));
            this.file = file;
            reconfigure();
        }
    }

    @Override
    public synchronized void start() {
        if (handler != null)
            return;
        log.info("Starting HttpConnector to " + LogUtil.hideCredentials(getAddress()));
        // create default stats instance if specific one was not provided.
        if (getStats() == null)
            setStats(QDFactory.getDefaultFactory().createStats(QDStats.SType.HTTP_CONNECTOR, null));
        if (file) {
            AbstractConnectionHandler.Factory factory = Services.createService(AbstractConnectionHandler.Factory.class, null, null);
            handler = factory != null ? factory.createHandler("file", this) : null;
            if (handler == null)
                throw new IllegalArgumentException("Cannot find file connection handler");
        } else {
            if (reconnectHelper == null)
                reconnectHelper = new ReconnectHelper(getReconnectDelay());
            else
                reconnectHelper.setReconnectDelay(getReconnectDelay());
            handler = new HttpConnectorHandler(this, reconnectHelper);
        }
        handler.start();
    }

    @Override
    protected synchronized Joinable stopImpl() {
        AbstractConnectionHandler<?> handler = this.handler;
        this.handler = null; // Clear before actual close to avoid recursion.
        if (handler != null) {
            log.info("Stopping HttpConnector");
            handler.close();
        }
        return handler;
    }

    protected synchronized void handlerClosed(HttpConnectorHandler handler) {
        if (handler != this.handler)
            return;
        this.handler = null;
        start();
    }

    /**
     * Sets stats for this connector. Stats should be of type {@link QDStats.SType#HTTP_CONNECTOR} or
     * a suitable substitute. This method may be invoked only once.
     * @throws IllegalStateException if already set.
     */
    @Override
    public synchronized void setStats(QDStats stats) {
        super.setStats(stats);
        stats.addMBean("HttpConnector", this);
    }

    @Override
    public boolean isActive() {
        return handler != null;
    }

    @Override
    public MessageConnectorState getState() {
        AbstractConnectionHandler<?> handler = this.handler;
        return handler != null ? handler.getHandlerState() : MessageConnectorState.DISCONNECTED;
    }

    @Override
    public int getConnectionCount() {
        return getState() == MessageConnectorState.CONNECTED ? 1 : 0;
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        AbstractConnectionHandler<?> handler = this.handler; // Atomic read.
        if (handler != null && handler.getHandlerState() == MessageConnectorState.CONNECTED) {
            stats.addActiveConnectionCount(1);
            stats.addConnectionStats(handler.getConnectionStats());
        }
        return stats;
    }
}
