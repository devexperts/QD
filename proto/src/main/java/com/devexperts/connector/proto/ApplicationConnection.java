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

import com.devexperts.io.ChunkList;
import com.devexperts.logging.Logging;

/**
 * Base class for specific application protocol layer of a single two-way byte-oriented connection.
 * <p/>
 * Implementations of this abstract class must be <i>thread-safe</i>.
 *
 * @param <F> {@link ApplicationConnectionFactory} that produces this type of application connection
 * (may be used to store global configuration)
 *
 * @see ApplicationConnectionFactory
 * @see TransportConnection
 */
public abstract class ApplicationConnection<F extends ApplicationConnectionFactory> {

    private static final int STATE_NEW = 0;
    private static final int STATE_WORKING = 1;
    private static final int STATE_CLOSED = 2;

    // ==================== Fields & constructors ====================

    protected final Logging log = Logging.getLogging(getClass());
    protected final F factory;
    protected final TransportConnection transportConnection;

    private volatile int state;

    /**
     * Creates new application connection. This method may not call any methods
     * on the transport connection, since transport connection may not be fully
     * initialized when this constructor is invoked. {@link #start()} method
     * invoked after construction when transport connection is fully initialized.
     *
     * @param factory the application connection factory.
     * @param transportConnection the corresponding transport connection.
     */
    @SuppressWarnings("unchecked")
    protected ApplicationConnection(F factory, TransportConnection transportConnection) {
        this.factory = (F) factory.clone();
        this.transportConnection = transportConnection;
    }

    /**
     * Starts this application connection.
     * This method invokes {@link #startImpl} if the application connection is not closed.
     */
    public final void start() {
        if (state != STATE_NEW)
            return;
        synchronized (this) {
            if (state != STATE_NEW)
                return;
            state = STATE_WORKING;
        }
        startImpl();
    }

    // ==================== Public API for transport layer ====================

    /**
     * Returns new portion of chunks for the transport layer to send or {@code null} if there are
     * no new chunks available for sending yet.
     *
     * <p>This method is normally invoked by the transport layer as a response to
     * {@link TransportConnection#chunksAvailable()} notification.
     *
     * <p>This method may be executed by at most one thread at a time.
     *
     * @param owner new owner for the retrieved chunk list
     * @return chunks to be sent by transport or {@code null}
     */
    public abstract ChunkList retrieveChunks(Object owner);

    /**
     * Processes new portion of chunks received by transport layer.
     *
     * <p>This method reports to the invoker whether it is ready to process more chunks.
     * If connection is ready, then transport layer is allowed to make new call as soon as new data is available.
     * If connection is not ready, then transport layer shall wait until
     * {@link TransportConnection#readyToProcessChunks} method is invoked.
     * <b>Note</b> that due to concurrency such notification might be received before this method returns;
     * in such cases it is assumed that connection is ready even if it returns {@code false}.
     *
     * <p>This method may be executed by at most one thread at a time.
     *
     * @param chunks chunks received by transport layer
     * @param owner owner of the chunk list
     * @throws NullPointerException if chunks is {@code null}
     * @return {@code true} if the connection is ready to process more chunks; {@code false} otherwise.
     */
    public abstract boolean processChunks(ChunkList chunks, Object owner);

    /**
     * This method is invoked by transport layer after completion of previous write to figure out when
     * the next call to this method must be scheduled. This method may be used by application connection
     * for various validation purposes, for example for timeout checking and heartbeat messages generation.
     *
     * <p>This method takes current time value as argument and returns the next time
     * when it should be invoked again.
     * <b>INVARIANT:</b> If the result of this method is less of equal to {@code currentTime},
     * then {@link TransportConnection#chunksAvailable()} must have been invoked before this method returns.
     *
     * <p>Transport layer may ignore the returning value of this method and invoke it earlier or a bit later.
     * Application connection implementations should process often calls of this method quickly.
     *
     * @param currentTime current time value as returned by {@link System#currentTimeMillis()}
     * @return next approximate time when this application connection shall be examined again.
     */
    public long examine(long currentTime) {
        // Default implementation does nothing.
        return Long.MAX_VALUE;
    }

    /**
     * Checks whether this application connection is closed.
     * @return {@code true} if this connection was {@link #close closed}
     */
    public boolean isClosed() {
        return state == STATE_CLOSED;
    }

    /**
     * Closes this application connection.
     * This method does nothing if the connection was already closed.
     */
    public final void close() {
        if (state == STATE_CLOSED)
            return;
        synchronized (this) {
            if (state == STATE_CLOSED)
                return;
            state = STATE_CLOSED;
        }
        try {
            closeImpl();
        } finally {
            notifyClosed();
        }
    }

    /**
     * Marks this connection for immediate restart when it is closed.
     */
    public void markForImmediateRestart() {
        transportConnection.markForImmediateRestart();
    }

    /**
     * Performs any additional actions at connection start.
     * This method may invoke any methods on transport connection.
     * This method is invoked from {@link #start()} method.
     */
    protected void startImpl() {}

    /**
     * Performs any additional actions at connection closing.
     * This method is invoked from {@link #close()} method by default.
     * {@link #notifyClosed() notify the transport connection} in this method.
     */
    protected void closeImpl() {}

    // ==================== Protected API for implementations ====================

    /**
     * Notifies transport connection that the application connection was closed.
     */
    protected void notifyClosed() {
        if (state != STATE_CLOSED)
            return;
        try {
            transportConnection.connectionClosed();
        } catch (Throwable t) {
            log.error("Unexpected error while notifying transport connection", t);
        }
    }

    /**
     * Notifies transport connection that there are more chunks available for sending.
     */
    protected void notifyChunksAvailable() {
        if (state != STATE_WORKING)
            return;
        try {
            transportConnection.chunksAvailable();
        } catch (Throwable t) {
            log.error("Unexpected error while notifying transport connection", t);
            close();
        }
    }

    /**
     * Notifies transport connection that the application connection is ready to process more bytes.
     */
    protected void notifyReadyToProcess() {
        if (state != STATE_WORKING)
            return;
        try {
            transportConnection.readyToProcessChunks();
        } catch (Throwable t) {
            log.error("Unexpected error while notifying transport connection", t);
            close();
        }
    }
}
