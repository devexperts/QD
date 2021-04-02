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

import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.net.Socket;
import java.net.SocketException;

abstract class SocketSource {
    private static final String SO_TIMEOUT_PROPERTY = "com.devexperts.qd.qtp.socket.soTimeout";
    private static final long SO_TIMEOUT = TimePeriod.valueOf(SystemProperties.getProperty(SO_TIMEOUT_PROPERTY, "5m")).getTime();

    static {
        if (SO_TIMEOUT < 0 || SO_TIMEOUT > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid " + SO_TIMEOUT_PROPERTY);
    }

    public void markForImmediateRestart() {}

    public abstract SocketInfo nextSocket() throws InterruptedException;

    protected static void configureSocket(Socket socket) throws SocketException{
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        if (SO_TIMEOUT > 0)
            socket.setSoTimeout((int) SO_TIMEOUT);
    }
}
