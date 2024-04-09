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

import com.devexperts.io.ChunkPool;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main class consolidating NIO server connection logic.
 * <p/>
 * A new instance of this class is created each time the connector is started.
 */
class NioCore implements AbstractMessageConnector.Joinable {
    static long SELECT_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty(NioCore.class, "selectTimeout", ".1s")).getTime();

    final NioServerConnector connector;
    final ChunkPool chunkPool;
    final InetSocketAddress bindSocketAddress;
    final String address;

    private NioAcceptor acceptor;
    private final NioReader reader;
    private final NioWriter writer;
    private final NioValidator validator;

    final Set<NioConnection> connections = ConcurrentHashMap.newKeySet();

    private volatile boolean closed;

    NioCore(NioServerConnector connector) throws IOException {
        this.connector = connector;
        bindSocketAddress = new InetSocketAddress(connector.getBindInetAddress(), connector.getLocalPort());
        chunkPool = connector.getFactory().getChunkPool();
        address = connector.getAddress();

        reader = new NioReader(this, connector.getReaderThreads());
        writer = new NioWriter(this, connector.getWriterThreads());
        acceptor = new NioAcceptor(this);
        validator = new NioValidator(this);
    }

    boolean isConnected() {
        return acceptor.isConnected();
    }

    boolean isAccepting() {
        return acceptor.isConnected() || acceptor.isConnecting();
    }

    void start() {
        reader.start();
        writer.start();
        validator.start();
    }

    void close() {
        closed = true;
        reader.close();
        writer.close();
        acceptor.close();
        validator.interrupt();
    }

    @Override
    public void join() throws InterruptedException {
        reader.join();
        writer.join();
        acceptor.join();
        validator.join();
    }

    boolean startAcceptor() throws IOException {
        if (!closed) {
            if (acceptor.isNew()) {
                acceptor.start();
            } else if (!isAccepting()) {
                acceptor = new NioAcceptor(this);
                acceptor.start();
            }
            return true;
        }
        return false;
    }

    boolean stopAcceptor() {
        if (isAccepting()) {
            acceptor.close();
            return true;
        }
        return false;
    }

    void closeConnections() {
        for (NioConnection connection : connections) {
            connection.close();
        }
        connector.notifyMessageConnectorListeners();
    }

    List<Closeable> getConnections() {
        return new ArrayList<>(connections);
    }

    boolean isClosed() {
        return closed;
    }

    int getConnectionsCount() {
        return connections.size();
    }

    NioPoolCounters getReaderPoolCounters() {
        return reader.getCountersHolder().getCounters();
    }

    NioPoolCounters getReaderPoolCountersDelta() {
        return reader.getCountersHolder().getCountersDelta();
    }

    NioPoolCounters getWriterPoolCounters() {
        return writer.getCountersHolder().getCounters();
    }

    NioPoolCounters getWriterPoolCountersDelta() {
        return writer.getCountersHolder().getCountersDelta();
    }

    void resetCounters() {
        reader.getCountersHolder().resetCounters();
        writer.getCountersHolder().resetCounters();
    }

    void registerConnection(NioConnection connection) {
        connections.add(connection);
        reader.register(connection);
        if (closed) {
            connection.close();
        }
        connector.notifyMessageConnectorListeners();
    }

    void removeConnection(NioConnection connection) {
        connections.remove(connection);
    }

    void chunksAvailable(NioConnection connection) {
        if (closed) {
            connection.close();
        } else {
            writer.chunksAvailable(connection);
        }
    }

    void readyToProcess(NioConnection connection) {
        if (closed) {
            connection.close();
        } else {
            reader.readyToProcess(connection);
        }
    }

    @Override
    public String toString() {
        return connector.getName() + "-:" + bindSocketAddress.getPort() +
            (bindSocketAddress.getAddress().isAnyLocalAddress() ? "" :
                "[bindaddr=" + bindSocketAddress.getAddress().getHostAddress() + "]");
    }

}
