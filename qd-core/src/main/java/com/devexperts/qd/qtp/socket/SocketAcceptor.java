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

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.QTPWorkerThread;
import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.util.LogUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import javax.net.ServerSocketFactory;

class SocketAcceptor extends QTPWorkerThread {
    private static final Logging log = Logging.getLogging(ServerSocketConnector.class);

    private final ServerSocketConnector connector;
    private final int port;
    private final InetAddress bindAddress;
    private final String address;

    private final ReconnectHelper reconnectHelper;

    private volatile ServerSocket serverSocket;

    SocketAcceptor(ServerSocketConnector connector) {
        super(connector.getName() + "-" + (connector.getTls() ? "tls+" : "") + ":" + connector.getLocalPort() +
            (connector.getBindInetAddress() == null || connector.getBindInetAddress().isAnyLocalAddress() ? "" :
                "[bindaddr=" + connector.getBindInetAddress().getHostAddress() + "]") +
            "-Acceptor");
        this.connector = connector;
        port = connector.getLocalPort();
        bindAddress = connector.getBindInetAddress();
        address = connector.getAddress();
        reconnectHelper = new ReconnectHelper(connector.getReconnectDelay());
    }

    public boolean isConnected() {
        return serverSocket != null;
    }

    @Override
    protected void doWork() throws InterruptedException, IOException {
        while (!isClosed()) {
            ServerSocket serverSocket = this.serverSocket;
            if (serverSocket == null) {
                if (isClosed())
                    return; // ServerSocketConnector interrupts thread before closing socket. Socket is closed here...
                reconnectHelper.sleepBeforeConnection();
                log.info("Trying to listen at " + LogUtil.hideCredentials(address));
                try {
                    serverSocket = this.serverSocket =
                        ServerSocketFactory.getDefault().createServerSocket(port, 0, bindAddress);
                    //noinspection deprecation
                    ServerSocketTestHelper.completePortPromise(connector.getName(), serverSocket.getLocalPort());
                } catch (Throwable t) {
                    log.error("Failed to listen at " + LogUtil.hideCredentials(address), t);
                    //noinspection deprecation
                    ServerSocketTestHelper.failPortPromise(connector.getName(), t);
                    continue; // retry listening again
                }
                log.info("Listening at " + LogUtil.hideCredentials(address) +
                    (port == 0 ? " on port " + serverSocket.getLocalPort() : ""));
                connector.notifyMessageConnectorListeners();
                // the following code handle concurrent close of the acceptor thread while socket was being created
                // Note, that volatile this.serverSocket was assigned first,
                //       then volatile this.closed is checked by isClosed() method
                // ServerSocketConnector.stop method accesses the above fields in reverse order
                if (isClosed())
                    return;
            }
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (SocketTimeoutException e) {
                // handle spurious accept timeouts (see QD-1410 and JDK-8237858)
                log.warn("Unexpected SocketTimeoutException on Socket.accept() was ignored.");
                continue;
            }
            if (!connector.isNewConnectionAllowed()) {
                log.warn("Rejected client socket connection because of maxConnections limit: " +
                    LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(socket)));
                socket.close();
                continue;
            }

            log.info("Accepted client socket connection: " +
                LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(socket)));
            SocketHandler handler = new SocketHandler(connector, new ServerSocketSource(socket));
            connector.addHandler(handler);
            handler.setCloseListener(connector.closeListener);
            handler.start();
            // we could have been concurrently closed even before handler was added to the list.
            // We need to close this handler so that it is not "lost" in such case
            if (isClosed())
                handler.close();
        }
    }

    @Override
    protected void handleShutdown() {
        connector.stop();
    }

    @Override
    protected void handleClose(Throwable reason) {
        closeSocketImpl(reason);
    }

    private synchronized ServerSocket takeSocket() {
        ServerSocket result = serverSocket;
        serverSocket = null;
        return result;
    }

    protected void closeSocketImpl(Throwable reason) {
        closeServerSocket(takeSocket(), reason);
    }

    private void closeServerSocket(ServerSocket serverSocket, Throwable reason) {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                if (reason == null)
                    log.info("Stopped listening at " + LogUtil.hideCredentials(address));
                else
                    log.error("Stopped listening at " + LogUtil.hideCredentials(address), reason);
            } catch (Throwable t) {
                log.error("Failed to close server socket " + LogUtil.hideCredentials(address), t);
            }
        }
    }
}
