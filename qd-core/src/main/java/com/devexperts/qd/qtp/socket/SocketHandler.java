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
package com.devexperts.qd.qtp.socket;

import java.io.IOException;
import java.net.Socket;

import com.devexperts.connector.proto.*;
import com.devexperts.io.ChunkPool;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.transport.stats.ConnectionStats;
import com.devexperts.util.*;

/**
 * The <code>SocketHandler</code> handles standard socket using blocking API.
 */
class SocketHandler extends AbstractTransportConnection implements AbstractMessageConnector.Joinable {
    private static final String VERBOSE = SystemProperties.getProperty("com.devexperts.qd.qtp.socket.verbose", null);

    /**
     * The <code>CloseListener</code> interface allows tracking of handler death.
     */
    public interface CloseListener {
        public void handlerClosed(SocketHandler handler);
    }

    private final Logging log;

    final AbstractMessageConnector connector;
    private final SocketSource socketSource;

    final ChunkPool chunkPool;
    final boolean verbose;

    private SocketReader reader;
    private SocketWriter writer;

    private volatile ThreadData threadData; // only defined when state == CONNECTED
    private volatile CloseListener closeListener;

    private volatile SocketState state = SocketState.NEW;

    SocketHandler(AbstractMessageConnector connector, SocketSource socketSource) {
        this.connector = connector;
        this.log = connector.getLogging();
        this.socketSource = socketSource;
        this.chunkPool = connector.getFactory().getChunkPool();
        this.verbose = VERBOSE != null && connector.getName().contains(VERBOSE);
    }

    public String getHost() {
        ThreadData threadData = this.threadData; // atomic read
        return threadData != null ? threadData.address.host : "";
    }

    public int getPort() {
        ThreadData threadData = this.threadData; // atomic read
        return threadData != null ? threadData.address.port : 0;
    }

    public MessageConnectorState getHandlerState() {
        return state.state;
    }

    public boolean isConnected() {
        return state == SocketState.CONNECTED;
    }

    ConnectionStats getActiveConnectionStats() {
        ThreadData threadData = this.threadData; // Atomic read.
        return threadData == null ? null : threadData.connectionStats;
    }

    public void setCloseListener(CloseListener listener) {
        this.closeListener = listener;
    }

    public synchronized void start() {
        if (state != SocketState.NEW)
            return; // handler was already concurrently closed -- will not start
        state = SocketState.STARTED;
        reader = new SocketReader(this);
        writer = new SocketWriter(this);
        int threadPriority = connector.getThreadPriority();
        reader.setPriority(threadPriority);
        writer.setPriority(threadPriority);
        reader.start();
        writer.start();
        notifyAll();
    }

    public void close() {
        closeSocketImpl(null);
    }

    @Override
    public void join() throws InterruptedException {
        reader.join();
        writer.join();
    }

    public void exitSocket(Throwable reason) {
        closeSocketImpl(reason);
    }

    public void stopConnector() {
        connector.stop();
    }

    private void closeSocketImpl(Throwable reason) {
        if (!makeClosed())
            return; // was already closed
        ThreadData threadData = this.threadData;
        if (threadData != null) {
            // Closed socket that was already connected
            this.threadData = null;
            cleanupThreadData(threadData, reason);
        }
        CloseListener listener = closeListener; // Atomic read.
        if (listener != null)
            listener.handlerClosed(this);
        connector.notifyMessageConnectorListeners();
    }

    @Override
    public String toString() {
        return socketSource.toString();
    }

    // ========== Internal API for SocketReader & SocketWriter ==========
    // These methods shall be called only by dedicated reader/writer sockets (threads).

    static class ThreadData {
        final Socket socket;
        final SocketAddress address;
        final ApplicationConnection<?> connection;
        final QDStats stats;
        final ConnectionStats connectionStats;

        ThreadData(SocketInfo socketInfo, ApplicationConnection<?> connection, QDStats stats, ConnectionStats connectionStats) {
            this.socket = socketInfo.socket;
            this.address = socketInfo.socketAddress;
            this.connection = connection;
            this.stats = stats;
            this.connectionStats = connectionStats;
        }
    }

    private void cleanupThreadData(ThreadData threadData, Throwable reason) {
        cleanupConnection(threadData.connection);
        cleanupStats(threadData.stats);
        connector.addClosedConnectionStats(threadData.connectionStats);
        cleanupSocket(threadData.socket, threadData.address, reason);
    }

    private void cleanupConnection(ApplicationConnection<?> connection) {
        try {
            connection.close();
        } catch (Throwable t) {
            log.error("Failed to close connection", t);
        }
    }

    private void cleanupStats(QDStats stats) {
        try {
            stats.close();
        } catch (Throwable t) {
            log.error("Failed to close stats", t);
        }
    }

    private void cleanupSocket(Socket socket, SocketAddress address, Throwable reason) {
        try {
            socket.close();
            if (reason == null || reason instanceof IOException && socketSource instanceof ServerSocketSource)
                log.info("Disconnected from " + LogUtil.hideCredentials(address) +
                    (reason == null ? "" :
                        " because of " + (reason.getMessage() == null ? reason.toString() : reason.getMessage())));
            else
                log.error("Disconnected from " + LogUtil.hideCredentials(address), reason);
        } catch (Throwable t) {
            log.error("Error occurred while disconnecting from " + LogUtil.hideCredentials(address), t);
        }
    }

    ThreadData initThreadData() throws InterruptedException {
        if (!makeConnecting())
            return waitConnected(); // somebody is already connecting -- wait for it
        connector.notifyMessageConnectorListeners();

        // Connect in this thread
        // Create socket
        SocketInfo socketInfo = socketSource.nextSocket();
        if (socketInfo == null)
            return null;
        Socket socket = socketInfo.socket;
        variables().set(MessageConnectors.SOCKET_KEY, socket);
        variables().set(REMOTE_HOST_ADDRESS_KEY, socketInfo.socketAddress.host);
        ConnectionStats connectionStats = new ConnectionStats();

        // Create stats
        QDStats stats;
        try {
            // Create stats in try/catch block, so that we clean up socket if anything happens)
            stats = connector.getStats().getOrCreate(QDStats.SType.CONNECTIONS).create(QDStats.SType.CONNECTION,
                "host=" + JMXNameBuilder.quoteKeyPropertyValue(socketInfo.socketAddress.host) + "," +
                "port=" + socketInfo.socketAddress.port + "," +
                "localPort=" + socket.getLocalPort());
            if (stats == null)
                throw new NullPointerException("Stats were not created");
        } catch (Throwable t) {
            log.error("Failed to configure socket " + LogUtil.hideCredentials(socketInfo.socketAddress), t);
            connector.addClosedConnectionStats(connectionStats);
            cleanupSocket(socket, socketInfo.socketAddress, null);
            return null;
        }
        variables().set(MessageConnectors.STATS_KEY, stats);

        // Create adapter (in its own try/catch block)
        ApplicationConnection<?> connection = null;
        Throwable failureReason = null;
        try {
            ApplicationConnectionFactory acf = connector.getFactory();
            connection = acf.createConnection(this);
        } catch (Throwable t) {
            failureReason = t;
        }
        if (connection == null) {
            log.error("Failed to create connection on socket " + LogUtil.hideCredentials(socketInfo.socketAddress), failureReason);
            cleanupStats(stats);
            connector.addClosedConnectionStats(connectionStats);
            cleanupSocket(socket, socketInfo.socketAddress, null);
            return null;
        }

        connection.start();

        // Everything is ready to notify other threads
        ThreadData threadData = new ThreadData(socketInfo, connection, stats, connectionStats);
        boolean connected = makeConnected(threadData);

        // If we have not connected, then thread data was not stored and we have to clean it up ourselves
        if (!connected) {
            cleanupThreadData(threadData, failureReason);
            return null;
        }
        connector.notifyMessageConnectorListeners();
        return threadData;
    }

    private synchronized boolean makeConnecting() {
        if (state == SocketState.STARTED) {
            state = SocketState.CONNECTING;
            notifyAll();
            return true;
        }
        return false;
    }

    private synchronized boolean makeConnected(ThreadData threadData) {
        if (state == SocketState.CONNECTING) {
            state = SocketState.CONNECTED;
            this.threadData = threadData;
            notifyAll();
            return true;
        }
        return false;
    }

    private synchronized ThreadData waitConnected() throws InterruptedException {
        while (state == SocketState.CONNECTING)
            wait();
        if (state == SocketState.CONNECTED)
            return threadData;
        if (state == SocketState.STOPPED)
            return null;
        throw new IllegalStateException();
    }

    private synchronized boolean makeClosed() {
        if (state == SocketState.STOPPED)
            return false;
        if (state != SocketState.NEW) {
            reader.close();
            writer.close();
        }
        state = SocketState.STOPPED;
        notifyAll();
        return true;
    }

    // ========== Internal helper ==========

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static String verboseBytesToString(String prefix, byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" ").append(length).append(" bytes [");
        int i;
        for (i = 0; i < length && i < 16; i++) {
            int b = bytes[offset + i] & 0xff;
            sb.append(i == 0 ? "" : " ");
            sb.append(HEX[b >> 4]);
            sb.append(HEX[b & 0xf]);
        }
        if (i < length)
            sb.append("...");
        sb.append("]");
        return sb.toString();
    }

    // ========== TransportConnection implementation ==========


    @Override
    public void markForImmediateRestart() {
        socketSource.markForImmediateRestart();
    }

    @Override
    public void connectionClosed() {
        close();
    }

    @Override
    public void chunksAvailable() {
        writer.chunksAvailable();
    }

    @Override
    public void readyToProcessChunks() {
        reader.readyToProcess();
    }
}
