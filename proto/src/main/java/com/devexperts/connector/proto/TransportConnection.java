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
package com.devexperts.connector.proto;

import com.devexperts.util.TypedKey;
import com.devexperts.util.TypedMap;

/**
 * Transport-layer of a single two-way byte-oriented connection.
 *
 * @see ApplicationConnection
 */
public interface TransportConnection {

    // ==================== Connection-local variables ====================

    /**
     * A key for an address of the remote host of this connection.
     * @see #variables()
     */
    public static TypedKey<String> REMOTE_HOST_ADDRESS_KEY = new TypedKey<>();

    /**
     * A key for an subject of this connection.
     * @see #variables()
     */
    public static TypedKey<Object> SUBJECT_KEY = new TypedKey<>();

    /**
     * Returns a map with connection-local variables.
     */
    public TypedMap variables();

    // ==================== Listener for ApplicationConnection methods ====================

    /**
     * Marks this connection for immediate restart when it is closed.
     */
    public void markForImmediateRestart();

    /**
     * Called when {@link ApplicationConnection} gets closed.
     */
    public void connectionClosed();

    /**
     * Called when {@link ApplicationConnection} gets some chunks ready to be sent.
     */
    public void chunksAvailable();

    /**
     * Called when {@link ApplicationConnection} gets ready to process more chunks.
     * <p/>
     * Note, that because of concurrency issues this method invocation may actually
     * occur before the {@link ApplicationConnection#processChunks(com.devexperts.io.ChunkList, Object)}
     * completes and returns {@code false}. However, in this case the {@link ApplicationConnection}
     * is considered to be ready to process more chunks.
     */
    public void readyToProcessChunks();
}
