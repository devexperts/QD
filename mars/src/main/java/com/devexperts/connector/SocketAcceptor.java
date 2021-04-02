/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector;

import com.devexperts.util.LogUtil;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * SocketAcceptor provides {@link Socket} instances for {@link SocketHandler} by listening for incoming
 * connections on specified server port number. Accepts any number of incoming connections.
 */
class SocketAcceptor extends SocketController {

    private final SocketAddress address;

    private final Set<SocketHandler> handlers = new HashSet<>();

    private ServerSocket server_socket;

    /**
     * Creates new acceptor for specified parameters.
     *
     * @param address must be server-side address formatted as ":port".
     */
    SocketAcceptor(Connector connector, String address) throws ParseException {
        super(connector);
        this.address = SocketAddress.valueOf(address);
        if (this.address.getHost().length() != 0)
            throw new ParseException("Host name is present.", 0);
    }

    public String toString() {
        return "SocketAcceptor-" + LogUtil.hideCredentials(address) + ": " + STATE_NAMES[state];
    }

    synchronized void start() {
        if (state == CLOSED) {
            return;
        }
        if (state != NEW) {
            throw new IllegalStateException("Acceptor may be started only once.");
        }
        state = CONNECTING;
        createNewSocket();
    }

    private synchronized boolean isClosed() {
        return state == CLOSED;
    }

    private synchronized boolean makeClosed() {
        if (state == CLOSED)
            return false;
        state = CLOSED;
        for (Iterator<SocketHandler> it = handlers.iterator(); it.hasNext(); ) {
            SocketHandler handler = it.next();
            it.remove(); // To prevent ConcurrentModificationException.
            handler.close();
        }
        return true;
    }

    void close() {
        if (!makeClosed())
            return;
        cleanup(server_socket);
    }

    synchronized void handlerClosed(SocketHandler handler) {
        handlers.remove(handler);
    }

    Socket acquireSocket() {
        if (!ensureConnected())
            return null;
        while (!Thread.interrupted()) {
            try {
                synchronized (this) {
                    if (state == CLOSED)
                        return null;
                }
                Socket socket = server_socket.accept();
                synchronized (this) {
                    // Create new SocketHandler to accept next socket.
                    createNewSocket();
                }
                connector.log("Connection accepted " + LogUtil.hideCredentials(connector.getSocketAddress(socket)), null, null);
                return socket;
            } catch (SocketException e) {
                if (!isClosed()) {
                    if ("socket closed".equalsIgnoreCase(e.getMessage())) {
                        connector.log("Accepting failed " + LogUtil.hideCredentials(address), e, false, null);
                    } else {
                        connector.log("Accepting failed " + LogUtil.hideCredentials(address), e, null);
                    }
                }
            } catch (Throwable t) {
                connector.log("Accepting failed " + LogUtil.hideCredentials(address), t, null);
            }
        }
        return null;
    }

    private void createNewSocket() {
        SocketHandler handler = createNewSocket(address.getAddress());
        handlers.add(handler);
        handler.start();
    }

    private boolean ensureConnected() {
        boolean is_reconnection = false;
        while (!Thread.interrupted()) {
            synchronized (this) {
                if (state == CLOSED)
                    return false;
                if (state == CONNECTED)
                    return true;
            }
            // Here: state == CONNECTING, shall create server socket.
            ServerSocket server_socket = null;
            boolean connected = false;
            Throwable error = null;
            try {
                if (is_reconnection)
                    Thread.sleep(connector.getSkewedPeriod(connector.getReconnectionPeriod()));
                is_reconnection = true;
                connector.log("Listening " + LogUtil.hideCredentials(address), null, null);
                InetAddress bindAddress = address.getBind() == null || address.getBind().isEmpty() ? null : InetAddress.getByName(address.getBind());
                server_socket = new ServerSocket(address.getPort(), 50, bindAddress);
                synchronized (this) {
                    if (state == CLOSED)
                        return false;
                    state = CONNECTED;
                    this.server_socket = server_socket;
                    connected = true;
                    return true;
                }
            } catch (Throwable t) {
                error = t;
            } finally {
                if (!connected) {
                    cleanup(server_socket);
                    connector.log("Listening failed " + LogUtil.hideCredentials(address), error, null);
                }
            }
        }
        return false;
    }

    private void cleanup(ServerSocket server_socket) {
        if (server_socket != null)
            try {
                server_socket.close();
            } catch (Throwable t) {
                connector.log("Cleanup failed " + LogUtil.hideCredentials(address), t, null);
            }
    }
}
