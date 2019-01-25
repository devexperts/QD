/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.socket;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.util.LogUtil;

/**
 * Implements load-balancing algorithm for {@link ClientSocketConnector} by resolving host
 * names to IP address and returning resulting addresses in a random order.
 * Note: this class is not thread-safe.
 */
class ClientSocketSource extends SocketSource {
    private static final Logging log = Logging.getLogging(ClientSocketConnector.class);

    // ============ performance note ============
    // Extracted from getLocalAddresses() for improving performance.
    // This is especially important for tests (get local addresses once)
    private static class LocalAddressesCache {
        static final Set<String> LOCAL_ADDRESSES = new HashSet<>();

        static {
            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                if (networkInterfaces != null) {
                    for (NetworkInterface ni : Collections.list(networkInterfaces)) {
                        for (InetAddress ia : Collections.list(ni.getInetAddresses()))
                            LOCAL_ADDRESSES.add(ia.getHostAddress());
                    }
                }
            } catch (SocketException e) {
                log.warn("Cannot acquire a list of local address, will work without local address priority", e);
            }
        }
    }

    private final ClientSocketConnector connector;
    private final ReconnectHelper resolveHelper;
    private final String hostNames;
    private final int port;

    private final List<SocketAddress> parsedAddresses;

    private final Map<SocketAddress, ReconnectHelper> reconnectHelpers = new WeakHashMap<>();
    private final List<SocketAddress> resolvedAddresses = new ArrayList<>();
    private int currentAddress;

    ClientSocketSource(ClientSocketConnector connector) {
        this.connector = connector;
        this.resolveHelper = new ReconnectHelper(connector.getReconnectDelay());
        this.hostNames = connector.getHost();
        this.port = connector.getPort();
        this.parsedAddresses = SocketUtil.parseAddressList(hostNames, port);
    }

    @Override
    public void markForImmediateRestart() {
        resolveHelper.reset();
        reconnectHelpers.clear();
    }

    @Override
    public SocketInfo nextSocket() throws InterruptedException {
        SocketAddress address = nextAddress();
        if (address == null)
            return null;
        ReconnectHelper reconnectHelper = reconnectHelpers.get(address);
        if (reconnectHelper == null)
            reconnectHelpers.put(address, reconnectHelper = new ReconnectHelper(connector.getReconnectDelay()));

        reconnectHelper.sleepBeforeConnection();
        log.info("Connecting to " + LogUtil.hideCredentials(address));

        Socket socket = null;
        try {
            String proxyHost = connector.getProxyHost();
            if (proxyHost.length() <= 0) {
                // connect directly
                socket = new Socket(address.host, address.port);
                configureSocket(socket);
            } else {
                // connect via HTTPS proxy
                int proxyPort = connector.getProxyPort();
                log.info("Using HTTPS proxy: " + LogUtil.hideCredentials(proxyHost) + ":" + proxyPort);
                socket = new Socket(proxyHost, proxyPort);
                configureSocket(socket);
                String connectRequest = "CONNECT " + address.host + ":" + address.port + " HTTP/1.0\r\n\r\n";
                socket.getOutputStream().write(connectRequest.getBytes());
                InputStream input = socket.getInputStream();
                String response = readLine(input);
                if (response == null)
                    throw new SocketException("HTTPS proxy closed connection");
                String[] parts = response.split("[ \t]+");
                if (parts.length < 2 || !parts[0].equals("HTTP/1.0") && !parts[0].equals("HTTP/1.1") || !parts[1].equals("200"))
                    throw new IOException("Unexpected response from HTTPS proxy: '" + response + "'");
                for (String line; (line = readLine(input)) != null && line.length() > 0;) {} // skip HTTP header
            }
        } catch (Throwable t) {
            log.error("Failed to connect to " + LogUtil.hideCredentials(address), t);
            if (socket != null)
                try {
                    socket.close();
                } catch (Throwable tt) {
                    log.error("Failed to close socket " + LogUtil.hideCredentials(address), tt);
                }
            return null;
        }
        log.info("Connected to " + LogUtil.hideCredentials(address));
        return new SocketInfo(socket, address);
    }

    private static String readLine(InputStream in) throws IOException {
        int c = in.read();
        if (c < 0)
            return null;
        StringBuilder sb = new StringBuilder();
        for (; c >= 0 && c != '\n'; c = in.read())
            if (c != '\r')
                sb.append((char) c);
        return sb.toString();
    }

    private SocketAddress nextAddress() throws InterruptedException {
        currentAddress++;
        if (currentAddress >= resolvedAddresses.size()) {
            resolveHelper.sleepBeforeConnection();
            resolveAddresses();
            currentAddress = 0;
        }
        if (resolvedAddresses.isEmpty())
            return null;
        return resolvedAddresses.get(currentAddress);
    }

    public String toString() {
        return LogUtil.hideCredentials(hostNames) + (port == 0 ? "" : ":" + port);
    }

    private void resolveAddresses() {
        // resolve addresses
        Set<SocketAddress> addresses = new HashSet<>();
        for (SocketAddress parsedAddress : parsedAddresses) {
            // Resolve all host addresses
            log.info("Resolving IPs for " + LogUtil.hideCredentials(parsedAddress.host));
            try {
                InetAddress[] temp = InetAddress.getAllByName(parsedAddress.host);
                for (InetAddress inetAddress : temp)
                    addresses.add(new SocketAddress(inetAddress.getHostAddress(), parsedAddress.port));
            } catch (UnknownHostException e) {
                // We may reside under HTTPS proxy without having access to DNS server.
                // In this case let the proxy try to resolve the required address later.
                // Otherwise we will just get another UnknownHostException later anyway.
                log.warn("Failed to resolve IPs for " + LogUtil.hideCredentials(parsedAddress.host));
                addresses.add(new SocketAddress(parsedAddress.host, parsedAddress.port));
            }
        }

        // Prepare a final list of resolved addresses
        resolvedAddresses.clear();
        resolvedAddresses.addAll(addresses);

        // Special shuffle for multi-address case
        if (resolvedAddresses.size() > 1)
            shuffleResolvedAddresses();
    }

    private void shuffleResolvedAddresses() {
        // shuffle addresses randomly first
        Collections.shuffle(resolvedAddresses);

        // Then make sure that all local addresses go before remote ones -- bubble up all local address
        for (int i = 0, n = 0; i < resolvedAddresses.size(); i++)
            if (LocalAddressesCache.LOCAL_ADDRESSES.contains(resolvedAddresses.get(i).host)) {
                if (i > n)
                    resolvedAddresses.add(n, resolvedAddresses.remove(i));
                n++;
            }
    }
}
