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

/**
 * ConnectionAdapter is used to mate single connection (network socket) with corresponding data structures.
 * It is created whenever new connection is established and is destroyed when that connection is closed.
 * Therefore, it is a stateful object with lifetime equal to that of the corresponding connection.
 */
public interface ConnectionAdapter {

    /**
     * Sets new listener to receive notifications about connection adapter state changes.
     * Called once immediately after connection adapter is created and before it is started.
     */
    public void setListener(ConnectionAdapterListener listener);

    /**
     * Starts this connection adapter. May allocate additional resources if required.
     * Called once after listener was set and before any read/write operations.
     */
    public void start();

    /**
     * Closes this connection adapter. Shall release all allocated resources.
     * May be called several times (though shall not) and even during read/write operations.
     */
    public void close();

    /**
     * Performs single blocking read operation from connection (network socket).
     * Called by dedicated thread in infinite loop until connection is closed.
     * <p>
     * <b>NOTE:</b> This method may block either during socket read itself or during data processing.
     * <p>
     * This method shall return number of bytes read from connection. If it returns negative
     * number, then it will be considered an EOF signal and connection will be closed.
     * <p>
     * This method generally shall pass through any exceptions to the caller. If any
     * exception is thrown, then it will be considered an error and connection will be closed.
     */
    public int readData() throws Throwable;

    /**
     * Performs single blocking write operation into connection (network socket).
     * Called by dedicated thread in infinite loop until connection is closed.
     * <p>
     * <b>NOTE:</b> This method may block <b>only</b> during socket write itself;
     * it <b>shall not</b> block while waiting for more data availability.
     * If this method finds that there is no data to write, it shall return immediately.
     * When new data become available, the corresponding listener shall be notified.
     * If this method writes only part of available data and wants to be immediately called again
     * to write remaining data, it shall notify corresponding listener that data is available
     * (it is allowed to do so directly from this method).
     * <p>
     * This method shall return number of bytes written into connection. If it returns negative
     * number, then it will be considered an EOF signal and connection will be closed.
     * <p>
     * This method generally shall pass through any exceptions to the caller. If any
     * exception is thrown, then it will be considered an error and connection will be closed.
     */
    public int writeData() throws Throwable;

    /**
     * Performs single blocking write operation into connection (network socket)
     * of special heartbeat packet as specified by communication protocol.
     * Called by the same dedicated thread which calls {@link #writeData} method
     * whenever it detects that no data was written into connection for specified
     * heartbeat period and that new heartbeat packet shall be written instead.
     * <p>
     * <b>NOTE:</b> This method may block <b>only</b> during socket write itself.
     * Actually, this method <b>shall not</b> perform any other actions except
     * writing heartbeat packet into socket. It shall not, in particular, check
     * for business data availability to write it instead.
     * When new data become available, the corresponding listener shall be notified.
     * <p>
     * This method shall return number of bytes written into connection. If it returns negative
     * number, then it will be considered an EOF signal and connection will be closed.<br>
     * <b>NOTE:</b> If this method returns 0 bytes (i.e. no heartbeat packet was written),
     * then it might be immediately called again in an infinite loop fashion until heartbeat
     * packet is written, or business data is available, or connection is closed. Therefore,
     * if heartbeats are not specified by communication protocol, then heartbeat period and timeout
     * both shall be configured to 0 as indication that no heartbeats are required.
     * <p>
     * This method generally shall pass through any exceptions to the caller. If any
     * exception is thrown, then it will be considered an error and connection will be closed.
     */
    public int writeHeartbeat() throws Throwable;
}
