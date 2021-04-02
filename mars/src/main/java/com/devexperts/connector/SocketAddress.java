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

import java.io.Serializable;
import java.text.ParseException;

/**
 * Stores prepared (parsed or resolved) host-port pairs.
 */
public class SocketAddress implements Serializable {

    // ========== Static API ==========

    public static SocketAddress valueOf(String address) throws ParseException {
        if (address == null || address.trim().isEmpty())
            throw new ParseException("Address is empty.", 0);
        int commercial = address.lastIndexOf('@');
        int colon = address.indexOf(':', commercial + 1);
        int colon2 = address.indexOf(':', colon + 1);
        if (colon < 0)
            throw new ParseException("Port number is missing.", 0);
        int port;
        try {
            port = Integer.parseInt(address.substring(colon + 1, colon2 < 0 ? address.length() : colon2).trim());
            if (port <= 0 || port >= 65536)
                throw new ParseException("Port number is out of range.", 0);
        } catch (NumberFormatException e) {
            throw new ParseException("Port number is not an integer.", 0);
        }
        String spec = commercial < 0 ? null : trim(address.substring(0, commercial));
        String host = trim(address.substring(commercial + 1, colon));
        String bind = colon2 < 0 ? null : trim(address.substring(colon2 + 1));
        return new SocketAddress(spec, host, port, bind);
    }

    public static String formatAddress(String spec, String host, int port) {
        return formatAddress(spec, host, port, null);
    }

    public static String formatAddress(String spec, String host, int port, String bind) {
        spec = trim(spec);
        host = trim(host);
        bind = trim(bind);
        return (spec.isEmpty() ? "" : spec + "@") + (host + ":" + port) + (bind.isEmpty() ? "" : ":" + bind);
    }

    private static String trim(String s) {
        return s == null || s.trim().isEmpty() ? "" : s.trim();
    }

    // ========== Instance Fields and API ==========

    private final String address;
    private final String spec;
    private final String host;
    private final int port;
    private final String bind;

    public SocketAddress(String spec, String host, int port) {
        this(spec, host, port, null);
    }

    public SocketAddress(String spec, String host, int port, String bind) {
        this.address = formatAddress(spec, host, port, bind);
        this.spec = trim(spec);
        this.host = trim(host);
        this.port = port;
        this.bind = bind;
    }

    public String getAddress() {
        return address;
    }

    public String getSpec() {
        return spec;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getBind() {
        return bind;
    }

    public String toString() {
        return address;
    }

    public boolean equals(Object o) {
        return o instanceof SocketAddress && address.equals(((SocketAddress) o).address);
    }

    public int hashCode() {
        return address.hashCode();
    }
}
