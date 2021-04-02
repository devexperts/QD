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

import java.net.Socket;

/**
 * This class stores socket and its addres.
 *
 * The stored address may differ from the one in {@code socket.getRemoteSocketAddress()}
 * in case the socket is connected via HTTPS proxy. In this case it would be the real
 * destination addres rather than proxy address.
 */
class SocketInfo {
    public final Socket socket;
    public final SocketAddress socketAddress;

    SocketInfo(Socket socket, SocketAddress socketAddress) {
        this.socket = socket;
        this.socketAddress = socketAddress;
    }
}
