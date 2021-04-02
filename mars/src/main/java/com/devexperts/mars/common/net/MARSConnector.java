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
package com.devexperts.mars.common.net;

import com.devexperts.connector.ConnectionAdapter;
import com.devexperts.connector.Connector;
import com.devexperts.mars.common.MARS;
import com.devexperts.mars.common.MARSAgent;
import com.devexperts.util.TimeUtil;

import java.net.Socket;

/**
 * MARS connector manages single set of communication links (defined as address string)
 * to send and receive MARS data over TCP/IP sockets.
 */
public class MARSConnector extends Connector {

    private final MARS mars;
    private final boolean receive;
    private final boolean send;

    private int read_buffer_limit;

    /**
     * Creates new MARS connector for specified {@link MARS} instance and communication link direction.
     *
     * @param mars instance to be serviced by this connector.
     * @param receive determines if this connector shall process data received from remote host.
     * @param send determines if this connector shall send data to remote host.
     */
    public MARSConnector(MARS mars, boolean receive, boolean send) {
        super();
        this.mars = mars;
        this.receive = receive;
        this.send = send;

        setHeartbeatPeriod((int) TimeUtil.MINUTE);

        if (send && !receive) // Data source connector - use large timeout to keep telnet clients.
            setHeartbeatTimeout((int) (6 * TimeUtil.HOUR)); // pure server socket - ignore read timeout
        else // Generic MARS connector - use adequate timeout.
            setHeartbeatTimeout(5 * getHeartbeatPeriod()); // 5 standard heartbeat periods.

        setReconnectionPeriod((int) (10 * TimeUtil.SECOND));
        setReadBufferLimit(1000000);
    }

    /**
     * Returns limit of read buffer at which connection is closed.
     */
    public int getReadBufferLimit() {
        return read_buffer_limit;
    }

    /**
     * Sets new limit of read buffer at which connection is closed.
     *
     * @throws IllegalArgumentException if specified limit is lower than 256 characters.
     */
    public void setReadBufferLimit(int read_buffer_limit) {
        if (read_buffer_limit < 256)
            throw new IllegalArgumentException("Read buffer limit is too low.");
        this.read_buffer_limit = read_buffer_limit;
    }

    protected ConnectionAdapter createConnectionAdapter(Socket socket) throws Throwable {
        return new MARSConnectionAdapter(receive ? mars : null, send ? new MARSAgent(mars) : null, read_buffer_limit, socket.getInputStream(), socket.getOutputStream());
    }
}
