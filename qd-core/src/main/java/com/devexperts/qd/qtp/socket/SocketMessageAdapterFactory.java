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

import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.stats.QDStats;

import java.io.IOException;
import java.net.Socket;

/**
 * This interface should be implemented by {@link MessageAdapter.Factory} in order to
 * run any custom authorization/authentication protocol with remote host. All socket connectors
 * check instance of {@link MessageAdapter.Factory} for implementation of
 * <code>SocketMessageAdapterFactory</code> and pass a corresponding socket to
 * {@link #createAdapterWithSocket} method on a freshly openned socket.
 */
public interface SocketMessageAdapterFactory extends MessageAdapter.Factory {
    /**
     * Runs custom authorization/authentication protocol over given <code>socket</code> and
     * returns {@link MessageAdapter} for communication with remote host.
     *
     * @param socket The socket.
     * @param stats <code>QDStats</code> for new message adapter.
     * @return New {@link MessageAdapter} or <code>null</code> if remote host cannot be
     *         authenticated/authorized without explicit reason to report via exception.
     * @throws SecurityException If remote host cannot be authorized/authenticated.
     * @throws IOException If there is a communication error with remote host.
     */
    public MessageAdapter createAdapterWithSocket(Socket socket, QDStats stats) throws SecurityException, IOException;
}
