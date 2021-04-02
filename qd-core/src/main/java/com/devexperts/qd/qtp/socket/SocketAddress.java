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

public class SocketAddress {
    public final String host;
    public final int port;

    SocketAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof SocketAddress))
            return false;
        SocketAddress address = (SocketAddress) other;
        return (port == address.port) && host.equals(address.host);
    }

    @Override
    public int hashCode() {
        return host.hashCode() * 29 + port;
    }

    @Override
    public String toString() {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }
}
