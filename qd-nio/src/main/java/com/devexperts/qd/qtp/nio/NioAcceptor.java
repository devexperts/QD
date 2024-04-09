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

import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.qtp.socket.SocketUtil;
import com.devexperts.util.LogUtil;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Accepting connections thread.
 */
class NioAcceptor extends NioWorkerThread {

    private enum State { NEW, CONNECTING, CONNECTED, CLOSED }

    private final ReconnectHelper reconnectHelper;
    private final ServerSocketChannel serverChannel;

    private volatile State state;

    NioAcceptor(NioCore core) throws IOException {
        super(core, "Acceptor");
        reconnectHelper = new ReconnectHelper(core.connector.getReconnectDelay());
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(true);
        state = State.NEW;
    }

    boolean isNew() {
        return state == State.NEW;
    }

    boolean isConnecting() {
        return state == State.CONNECTING;
    }

    boolean isConnected() {
        return state == State.CONNECTED;
    }

    @Override
    protected boolean isClosed() {
        return core.isClosed() || state == State.CLOSED;
    }

    @Override
    public void start() {
        if (!isNew()) {
            throw new IllegalStateException("incorrect current state: " + state);
        }
        state = State.CONNECTING;
        super.start();
    }

    protected void makeIteration() {
        if (isConnecting()) {
            try {
                reconnectHelper.sleepBeforeConnection();
                log.info("Trying to listen at " + LogUtil.hideCredentials(core.address));
                serverChannel.socket().bind(core.bindSocketAddress);
                //noinspection deprecation
                ServerSocketTestHelper.completePortPromise(core.connector.getName(), serverChannel.socket().getLocalPort());
                log.info("Listening at " + LogUtil.hideCredentials(core.address) +
                    (core.bindSocketAddress.getPort() == 0 ? " on port " + serverChannel.socket().getLocalPort() : ""));
                state = State.CONNECTED;
            } catch (InterruptedException e) {
                log.warn("Acceptor's connection binding process was interrupted");
                return;
            } catch (IOException e) {
                if (!core.isClosed()) {
                    log.error("Failed to listen at " + LogUtil.hideCredentials(core.address), e);
                    //noinspection deprecation
                    ServerSocketTestHelper.failPortPromise(core.connector.getName(), e);
                }
                return;
            }
        }

        SocketChannel channel;
        try {
            channel = serverChannel.accept();
            if (!core.connector.isNewConnectionAllowed()) {
                log.warn("Rejected client socket connection because of maxConnections limit: " +
                    LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(channel.socket())));
                closeClientSocket(channel);
                return;
            }
            log.info("Accepted client socket " + LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(channel.socket())));
        } catch (ClosedChannelException e) {
            state = State.CLOSED;
            log.warn("Acceptor's channel was closed");
            return;
        } catch (IOException e) {
            log.error("Failed to accept client socket", e);
            return;
        }

        try {
            channel.configureBlocking(false);
            Socket socket = channel.socket();
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(core.connector.getSocketTimeout());

            core.registerConnection(new NioConnection(core, channel));
        } catch (Throwable t) {
            log.error("Failed to configure accepted client socket", t);
            closeClientSocket(channel);
        }
    }

    void close() {
        try {
            serverChannel.close();
            state = State.CLOSED;
            log.info("Stopped listening at " + LogUtil.hideCredentials(core.address));
        } catch (Throwable t) {
            log.error("Failed to close server socket at " + LogUtil.hideCredentials(core.address), t);
        }
        interrupt();
    }

    private void closeClientSocket(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            log.error("Failed to close client socket", e);
        }
    }
}
