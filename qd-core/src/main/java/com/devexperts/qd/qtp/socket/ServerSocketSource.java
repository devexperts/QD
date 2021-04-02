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
package com.devexperts.qd.qtp.socket;

import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;

import java.net.Socket;

class ServerSocketSource extends SocketSource {
    private static final Logging log = Logging.getLogging(ServerSocketConnector.class);

    private final Socket socket;

    public ServerSocketSource(Socket socket) {
        this.socket = socket;
    }

    public SocketInfo nextSocket() {
        try {
            configureSocket(socket);
            return new SocketInfo(socket, new SocketAddress(socket.getInetAddress().getHostAddress(), socket.getPort()));
        } catch (Throwable t) {
            try {
                log.error("Failed to configure socket " + LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(socket)), t);
                socket.close();
            } catch (Throwable tt) {
                log.error("Failed to close socket " + LogUtil.hideCredentials(SocketUtil.getAcceptedSocketAddress(socket)), t);
            }
            return null;
        }
    }

    public String toString() {
        return SocketUtil.getAcceptedSocketAddress(socket);
    }
}
