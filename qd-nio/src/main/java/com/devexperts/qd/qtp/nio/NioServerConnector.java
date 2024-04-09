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
package com.devexperts.qd.qtp.nio;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.monitoring.Monitored;
import com.devexperts.qd.qtp.AbstractServerConnector;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Server socket connector that uses scalable non-blocking socket API (java.nio).
 */
@MessageConnectorSummary(
    info = "TCP/IP server socket connector with scalable non-blocking API.",
    addressFormat = "nio:<port>"
)
public class NioServerConnector extends AbstractServerConnector implements NioServerConnectorMBean {
    // We need at least two threads, because one of the threads is used to wait on selector if needed.
    private static final int MIN_THREAD_COUNT = 2;

    private static final int DEFAULT_THREAD_COUNT = // defaults to #processors + 1
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.nio.ThreadCount", // legacy property name
            Runtime.getRuntime().availableProcessors() + 1, MIN_THREAD_COUNT, Integer.MAX_VALUE);

    // TODO: Refactoring task is replace to SocketSource.SO_TIMEOUT
    private volatile int socketTimeout = Math.toIntExact(TimeUnit.MINUTES.toMillis(5));

    private volatile int readerThreads = DEFAULT_THREAD_COUNT;
    private volatile int writerThreads = DEFAULT_THREAD_COUNT;

    private volatile NioCore core;

    /**
     * Creates new NIO server socket connector.
     *
     * @param factory application connection factory to use
     * @param port    TCP port to use
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    public NioServerConnector(ApplicationConnectionFactory factory, int port) {
        super(factory);
        QDConfig.setDefaultProperties(this, NioServerConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, NioServerConnectorMBean.class, NioServerConnector.class.getName());
        this.port = port;
    }

    @Override
    protected synchronized void reconfigure() {
        getFactory().reinitConfiguration();
        if (isActive()) {
            boolean accepting = core.stopAcceptor();
            stopImpl();
            startImpl(accepting);
            notifyMessageConnectorListeners();
        }
    }

    protected void startImpl(boolean startAcceptor) {
        if (isClosed()) {
            return;
        }
        if (!isActive()) {
            log.info("Starting NioServerConnector to " + LogUtil.hideCredentials(getAddress()));
            try {
                if (!isCoreActive(core)) {
                    NioCore newCore = new NioCore(this);
                    newCore.start();
                    core = newCore;
                }
                active = true;
            } catch (Throwable t) {
                log.error("Failed to start connector", t);
            }
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
        log.info("Stopping NioServerConnector");
        cancelScheduledTask();
        NioCore core = this.core;
        core.close();
        active = false;
        return core;
    }

    @Override
    public boolean isAccepting() {
        NioCore core = this.core;
        return core != null && core.isAccepting();
    }

    @Override
    public MessageConnectorState getState() {
        NioCore core = this.core;
        if (!isActive()) {
            return MessageConnectorState.DISCONNECTED;
        }
        return core.isConnected() ? MessageConnectorState.CONNECTED : MessageConnectorState.CONNECTING;
    }

    @Override
    public int getConnectionCount() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getConnectionsCount() : 0;
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        NioCore core = this.core;
        if (isCoreActive(core)) {
            for (NioConnection connection : core.connections) {
                stats.addActiveConnectionCount(1);
                stats.addConnectionStats(connection.connectionStats);
            }
        }
        return stats;
    }

    @Override
    public int getSocketTimeout() {
        return socketTimeout;
    }

    @Override
    @MessageConnectorProperty("SO_TIMEOUT option value")
    public synchronized void setSocketTimeout(int socketTimeout) {
        if (this.socketTimeout != socketTimeout) {
            log.info("Setting socketTimeout=" + socketTimeout);
            this.socketTimeout = socketTimeout;
            reconfigure();
        }
    }

    @Override
    public int getReaderThreads() {
        return readerThreads;
    }

    @Override
    @MessageConnectorProperty("Number of reader threads in the pool")
    public synchronized void setReaderThreads(int readerThreads) {
        if (this.readerThreads != readerThreads) {
            if (readerThreads < MIN_THREAD_COUNT)
                throw new IllegalArgumentException();
            log.info("Setting readerThreads=" + readerThreads);
            this.readerThreads = readerThreads;
            reconfigure();
        }
    }

    @Override
    public NioPoolCounters getReaderPoolCounters() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getReaderPoolCounters() : NioPoolCounters.EMPTY;
    }

    @Monitored(name = "readerPool", description = "Reader threads pool counters", expand = true)
    public NioPoolCounters getReaderPoolCountersDelta() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getReaderPoolCountersDelta() : NioPoolCounters.EMPTY;
    }

    @Override
    public int getWriterThreads() {
        return writerThreads;
    }

    @Override
    @MessageConnectorProperty("Number of writer threads in a pool")
    public synchronized void setWriterThreads(int writerThreads) {
        if (this.writerThreads != writerThreads) {
            if (writerThreads < MIN_THREAD_COUNT)
                throw new IllegalArgumentException();
            log.info("Setting writerThreads=" + writerThreads);
            this.writerThreads = writerThreads;
            reconfigure();
        }
    }

    @Override
    protected boolean stopAcceptorInternal() {
        return core.stopAcceptor();
    }

    @Override
    protected void startAcceptorInternal() {
        if (isActive() && !isAccepting()) {
            try {
                if (core.startAcceptor()) {
                    log.info("Acceptor started for address: " + LogUtil.hideCredentials(getAddress()));
                }
            } catch (IOException e) {
                log.error("Failed to start acceptor", e);
            }
        }
    }

    @Override
    public NioPoolCounters getWriterPoolCounters() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getWriterPoolCounters() : NioPoolCounters.EMPTY;
    }

    @Monitored(name = "writerPool", description = "Writer thread pool counters", expand = true)
    public NioPoolCounters getWriterPoolCountersDelta() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getWriterPoolCountersDelta() : NioPoolCounters.EMPTY;
    }

    @Override
    public void resetCounters() {
        NioCore core = this.core;
        if (isCoreActive(core)) {
            core.resetCounters();
        }
    }

    @Override
    public void setStats(QDStats stats) {
        super.setStats(stats);
        stats.addMBean("NIOServerConnector", this);
    }

    protected boolean isNewConnectionAllowed() {
        int maxConnections = this.maxConnections;
        return maxConnections == 0 || getConnectionCount() < maxConnections;
    }

    @Override
    protected List<Closeable> getConnections() {
        NioCore core = this.core;
        return isCoreActive(core) ? core.getConnections() : Collections.emptyList();
    }

    boolean isCoreActive(NioCore core) {
        return core != null && !core.isClosed();
    }
}
