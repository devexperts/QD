/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.socket;

import com.devexperts.qd.qtp.AddressSyntaxException;
import com.devexperts.util.LogUtil;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketUtil {
    private SocketUtil() {} // to prevent accidental initialization

    public static String getAcceptedSocketAddress(Socket socket) {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "->" +
            socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
    }

    /**
     * Parses a list of comma-separated host names pairs with optional port and with support
     * of IPv6 numeric addresses in square brackets. Square brackets around addresses
     * are removed by this method.
     * @throws AddressSyntaxException if host name or address format is invalid.
     */
    public static List<SocketAddress> parseAddressList(String hostNames, int defaultPort) {
        List<SocketAddress> result = new ArrayList<>();
        for (String addressString : hostNames.split(",")) {
            String host = addressString;
            int port = defaultPort;
            int colonPos = addressString.lastIndexOf(':');
            if (colonPos >= 0 && colonPos > addressString.lastIndexOf(']')) {
                try {
                    port = Integer.parseInt(addressString.substring(colonPos + 1));
                    host = addressString.substring(0, colonPos);
                } catch (NumberFormatException e) {
                    throw new AddressSyntaxException("Failed to parse port from address \"" + LogUtil.hideCredentials(addressString) + "\"", e);
                }
            }
            if (host.startsWith("[")) {
                if (!host.endsWith("]"))
                    throw new AddressSyntaxException("An expected closing square bracket is not found in address \"" + LogUtil.hideCredentials(addressString) + "\"");
                host = host.substring(1, host.length() - 1);
            } else if (host.contains(":"))
                throw new AddressSyntaxException("IPv6 numeric address must be enclosed in square brackets in address \"" + LogUtil.hideCredentials(addressString) + "\"");
            result.add(new SocketAddress(host, port));
        }
        return result;
    }

    /**
     * Parses a list of comma-separated host names pairs with optional port and with support
     * of IPv6 numeric addresses in square brackets. Square brackets around addresses
     * are removed by this method.
     * @throws AddressSyntaxException if host name or address format is invalid.
     */
    public static List<SocketAddress> parseAddressList(String addresses) {
        int index = addresses.lastIndexOf(':');
        if (index > 0) {
            int port;
            try {
                port = Integer.parseInt(addresses.substring(index + 1));
            } catch (NumberFormatException e) {
                throw new AddressSyntaxException(
                    "Failed to parse default port from addresses \"" + LogUtil.hideCredentials(addresses) + "\"", e);
            }
            String host = addresses.substring(0, index);
            return parseAddressList(host, port);
        }
        throw new AddressSyntaxException("Failed to parse addresses \"" + LogUtil.hideCredentials(addresses) + "\"");
    }
}
