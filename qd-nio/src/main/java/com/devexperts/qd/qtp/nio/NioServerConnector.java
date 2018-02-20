/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.nio;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.monitoring.Monitored;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.SystemProperties;

/**
 * Server socket connector that uses scalable non-blocking socket API (java.nio).
 */
@MessageConnectorSummary(
    info = "TCP/IP server socket connector with scalable non-blocking API.",
    addressFormat = "nio:<port>"
)
public class NioServerConnector extends AbstractMessageConnector implements NioServerConnectorMBean {
    // We need at least two threads, because one of the threads is used to wait on selector if needed.
    private static final int MIN_THREAD_COUNT = 2;

    private static final int DEFAULT_THREAD_COUNT = // defaults to #processors + 1
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.nio.ThreadCount", // legacy property name
            Runtime.getRuntime().availableProcessors() + 1, MIN_THREAD_COUNT, Integer.MAX_VALUE);

    private static final String ANY_BIND_ADDRESS = "*";

    private int port;
    private String bindAddressString = ANY_BIND_ADDRESS;
    InetAddress bindAddress;

    private int socketTimeout = 5 * 60 * 1000;

    private volatile int readerThreads = DEFAULT_THREAD_COUNT;
    private volatile int writerThreads = DEFAULT_THREAD_COUNT;

    final AtomicReference<NioCore> core = new AtomicReference<>();

    /**
     * Creates new NIO server socket connector.
     *
     * @param factory application connection factory to use
     * @param port TCP port to use
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    public NioServerConnector(ApplicationConnectionFactory factory, int port) {
        super(factory);
        QDConfig.setDefaultProperties(this, NioServerConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, NioServerConnectorMBean.class, NioServerConnector.class.getName());
        this.port = port;
    }

    @Override
    public void start() {
        NioCore oldCore = core.getAndSet(null);
        if (oldCore != null && !oldCore.isClosed())
            return;
        NioCore newCore;
        try {
            newCore = new NioCore(this);
        } catch (Throwable t) {
            log.error("Failed to start connector", t);
            return;
        }
        if (core.compareAndSet(null, newCore)) {
            log.info("Starting NioServerConnector to " + LogUtil.hideCredentials(getAddress()));
            newCore.start();
        } else
            newCore.close();
    }

    @Override
    protected Joinable stopImpl() {
        NioCore oldCore = core.getAndSet(null);
        if (oldCore != null) {
            log.info("Stopping NioServerConnector");
            oldCore.close();
        }
        return oldCore;
    }

    @Override
    public boolean isActive() {
        return core.get() != null;
    }

    @Override
    public MessageConnectorState getState() {
        NioCore core = this.core.get();
        if (core == null || core.isClosed())
            return MessageConnectorState.DISCONNECTED;
        return core.isConnected() ? MessageConnectorState.CONNECTED : MessageConnectorState.CONNECTING;
    }

    @Override
    public int getConnectionCount() {
        NioCore core = this.core.get();
        return core == null ? 0 : core.getConnectionsCount();
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        NioCore core = this.core.get(); // Atomic read.
        if (core != null)
            for (NioConnection connection : core.connections.keySet()) {
                stats.addActiveConnectionCount(1);
                stats.addConnectionStats(connection.connectionStats);
            }
        return stats;
    }

    @Override
    public synchronized String getAddress() {
        return bindAddressString + ":" + port;
    }

    @Override
    public synchronized int getLocalPort() {
        return port;
    }

    @Override
    public synchronized void setLocalPort(int port) {
        if (this.port != port) {
            log.info("Setting localPort=" + port);
            this.port = port;
            reconfigure();
        }
    }

    @Override
    public synchronized String getBindAddr() {
        return bindAddressString;
    }

    @Override
    @MessageConnectorProperty("Network interface address to bind socket to")
    public synchronized void setBindAddr(String newBindAddress) throws UnknownHostException {
        if (newBindAddress == null || newBindAddress.isEmpty())
            newBindAddress = ANY_BIND_ADDRESS;
        if (!newBindAddress.equals(this.bindAddressString)) {
            log.info("Setting bindAddr=" + LogUtil.hideCredentials(newBindAddress));
            this.bindAddress = newBindAddress.equals(ANY_BIND_ADDRESS) ? null : InetAddress.getByName(newBindAddress);
            this.bindAddressString = newBindAddress;
            reconfigure();
        }
    }

    @Override
    public synchronized int getSocketTimeout() {
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
        NioCore nioCore = core.get();
        return nioCore == null ? NioPoolCounters.EMPTY : nioCore.getReaderPoolCounters();
    }

    @Monitored(name = "readerPool", description = "Reader threads pool counters", expand = true)
    public NioPoolCounters getReaderPoolCountersDelta() {
        NioCore nioCore = core.get();
        return nioCore == null ? NioPoolCounters.EMPTY : nioCore.getReaderPoolCountersDelta();
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
    public NioPoolCounters getWriterPoolCounters() {
        NioCore nioCore = core.get();
        return nioCore == null ? NioPoolCounters.EMPTY : nioCore.getWriterPoolCounters();
    }

    @Monitored(name = "writerPool", description = "Writer thread pool counters", expand = true)
    public NioPoolCounters getWriterPoolCountersDelta() {
        NioCore nioCore = core.get();
        return nioCore == null ? NioPoolCounters.EMPTY : nioCore.getWriterPoolCountersDelta();
    }

    @Override
    public void resetCounters() {
        NioCore nioCore = core.get();
        if (nioCore != null)
            nioCore.resetCounters();
    }

    @Override
    public synchronized void setStats(QDStats stats) {
        super.setStats(stats);
        stats.addMBean("NIOServerConnector", this);
    }
}
