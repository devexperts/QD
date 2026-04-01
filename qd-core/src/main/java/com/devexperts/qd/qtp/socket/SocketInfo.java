/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.socket;

import com.devexperts.annotation.Experimental;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * This class stores a socket and its address provided by a {@link SocketSource}.
 *
 * <p>NOTE: The stored address may differ from the one in {@code socket.getRemoteSocketAddress()},
 * see {@link #getSocketAddress()} docs.
 *
 * <p>SocketInfo instance provides direct access to {@link #getInputStream() input}/{@link #getOutputStream() output}
 * streams of the associated socket.
 * Code using the provided SocketInfo as a standard socket replacement should use these IO streams directly,
 * because the future implementations can substitute some alternative streams if needed.
 */
@Experimental
public class SocketInfo {
    protected final Socket socket;
    protected final SocketAddress socketAddress;

    /**
     * Constructs a new instance of {@code SocketInfo} with the provided socket and socket address.
     *
     * @param socket the {@link Socket} instance representing the network socket.
     * @param socketAddress the {@link SocketAddress} instance representing the address of the socket.
     */
    public SocketInfo(Socket socket, SocketAddress socketAddress) {
        this.socket = socket;
        this.socketAddress = socketAddress;
    }

    /**
     * Gets the {@link Socket} .
     *
     * @return the {@link Socket} instance representing the underlying network connection.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Gets the socket address.
     *
     * <p>This address represents the target address of the connection evaluated by the SocketInfo provider and may
     * differ from the {@link Socket#getRemoteSocketAddress() remote address} of the socket.
     * For example, in case the socket is connected via HTTPS proxy, it would be the real destination address rather
     * than a proxy address.
     *
     * @return the {@link SocketAddress}
     */
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    /**
     * Returns an input stream that reads from the socket.
     *
     * @return an input stream that reads from the socket.
     * @throws IOException if an I/O error occurs when creating the input stream.
     */
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    /**
     * Returns an output stream that writes to the socket.
     *
     * @return an output stream that writes to the socket.
     * @throws IOException if an I/O error occurs when creating the output stream.
     */
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public String toString() {
        return "SocketInfo{socket=" + socket + ", socketAddress=" + socketAddress + '}';
    }

}
