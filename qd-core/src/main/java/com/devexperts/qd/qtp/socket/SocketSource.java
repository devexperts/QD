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
import javax.annotation.Nullable;

/**
 * SocketSource encapsulates a network address resolution and load-balancing logic for the connector.
 *
 * <p>Usually SocketSource is bound to a series of connections with the same "scope" inside a connector.
 * For example, client-side connectors use a separate SocketSource instance for connections assigned to a particular
 * stripe.
 */
@Experimental
public interface SocketSource {

    /**
     * Retrieves the next available socket information for establishing a connection.
     * This method may block the calling thread if a socket is not immediately available
     * (for example, if the socket source policy requires a cool-down period between reconnection attempts).
     * Returned socket should be ready to use by an associated connection protocol.
     * If some initial handshake communication is required
     * (like for a {@link ClientSocketConnector} with configured proxy),
     * it should be successfully performed before the method is returned.
     *
     * @return the next {@link SocketInfo} instance containing the socket and its corresponding address.
     *   May return {@code null} if the current attempt has failed or the {@link #close()} was invoked concurrently.
     * @throws InterruptedException if the thread is interrupted while waiting for a socket to become available.
     */
    public SocketInfo getSocket() throws InterruptedException;

    /**
     * Handle closing of a previously returned socket.
     * Invoked when the connector releases a socket returned by the socket source.
     *
     * @implSpec It's expected that the implementation will handle the socket shutdown and finally close the socket.
     * <p>NOTE: Socket maybe be already closed before the method is invoked, and implementation shall support it.
     *
     * @param socketInfo SocketInfo object previously returned by the socket source
     * @param reason an optional exception that caused the socket closing
     */
    public default void closeSocket(SocketInfo socketInfo, @Nullable Throwable reason) throws IOException {
        socketInfo.socket.close();
    }

    /**
     * Marks the socket source for an immediate restart, signaling that connections
     * associated with this socket source should be re-established as
     * soon as possible.
     *
     * <p>This method is particularly useful in scenarios where the current state
     * of connections managed by the socket source is no longer valid
     * or requires a refresh, such as in response to network topology changes
     * or application logic that mandates reinitialization.
     *
     * <p>Calling this method does not immediately terminate or restart any
     * existing connections; it merely flags the socket source for
     * a restart process to occur at the earliest opportunity, and next invocation of {@link #getSocket()} should
     * return a socket without artificial delay.
     *
     * @implSpec implementation of the method shall be safe for asynchronous invocation
     */
    public default void markForImmediateRestart() {}

    /**
     * Checks if the current connection (last invocation of {@link #getSocket}) is preferred by this socket source
     * policy and prepares reconnecting if not.
     *
     * <p>This method is used by connectors supporting a
     * {@link ClientSocketConnectorMBean#restoreNow() restoring connection} to a preferred counter-party.
     * If method returns {@code true}, the connector will reestablish connection and expect that next invocation of
     * {@link #getSocket()} returns a socket connected to a preferred address.
     *
     * <p>The exact implementation of the check and reset logic may depend on the specific
     * requirements of the socket source implementation.
     *
     * @implSpec implementation of the method shall be safe for asynchronous invocation
     *
     * @param socketInfo optional {@link SocketInfo} corresponding to the current established connection
     * @return {@code true} if the current connection needs to be reestablished; {@code false} otherwise.
     */
    public default boolean shouldRestoreConnection(@Nullable SocketInfo socketInfo) { return false; }

    /**
     * Closes this socket source and releases any resources associated with it.
     *
     * <p>After calling this method, the socket source is considered terminated,
     * and any attempts to retrieve sockets with {@link #getSocket()} should return {@code null}.
     *
     * <p>If the socket source is already closed, subsequent calls to this method have no effect.
     *
     * @implSpec implementation of the method shall be safe for asynchronous invocation.
     * Specifically, close method may be invoked concurrently with {@link #getSocket}, {@link #closeSocket} or
     * {@link #shouldRestoreConnection} invocation. Stale connections may invoke {@link #closeSocket} for an already
     * closed socket source.
     */
    public default void close() {}
}
