/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.socket;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import javax.net.ssl.*;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.ReconnectHelper;
import com.devexperts.util.SystemProperties;

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
    private final boolean isTls;
    private final SSLSocketFactory sslSocketFactory;

    private final List<SocketAddress> parsedAddresses;

    private final Map<SocketAddress, ReconnectHelper> reconnectHelpers = new WeakHashMap<>();
    private final List<SocketAddress> resolvedAddresses = new ArrayList<>();
    private int currentAddress;

    ClientSocketSource(ClientSocketConnector connector) {
        this.connector = connector;
        this.resolveHelper = new ReconnectHelper(connector.getReconnectDelay());
        this.hostNames = connector.getHost();
        this.port = connector.getPort();
        this.isTls = connector.getTls();
        this.sslSocketFactory = isTls ? initSslSocketFactory() : null;
        this.parsedAddresses = SocketUtil.parseAddressList(hostNames, port);
    }

    private SSLSocketFactory initSslSocketFactory() {
        try {
            TrustManager trustManager = connector.getTrustManager();
            if (trustManager == null)
                return (SSLSocketFactory) SSLSocketFactory.getDefault();

            SSLContext context = SSLContext.getInstance("TLS");

            String keyStoreUrl = SystemProperties.getProperty("javax.net.ssl.keyStore", null);
            String keyStorePassword = SystemProperties.getProperty("javax.net.ssl.keyStorePassword", null);
            String keyStoreProvider = SystemProperties.getProperty("javax.net.ssl.keyStoreProvider", null);
            String keyStoreType = SystemProperties.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());

            KeyStore keyStore = getKeyStore(keyStoreType, keyStoreProvider, keyStoreUrl, keyStorePassword);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword == null ? null : keyStorePassword.toCharArray());

            context.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{trustManager}, null);
            return context.getSocketFactory();
        } catch (Throwable t) {
            log.error("Failed to configure SSL socket factory; connector will not start!");
            throw new IllegalArgumentException(t);
        }
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
        String addressStr = address + (isTls ? " (using TLS)" : "");
        ReconnectHelper reconnectHelper = reconnectHelpers.get(address);
        if (reconnectHelper == null)
            reconnectHelpers.put(address, reconnectHelper = new ReconnectHelper(connector.getReconnectDelay()));

        reconnectHelper.sleepBeforeConnection();
        log.info("Connecting to " + addressStr);

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
                log.info("Using HTTPS proxy: " + proxyHost + ":" + proxyPort);
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
            if (isTls)
                socket = sslSocketFactory.createSocket(socket, address.host, address.port, true);
        } catch (Throwable t) {
            log.error("Failed to connect to " + addressStr, t);
            if (socket != null)
                try {
                    socket.close();
                } catch (Throwable tt) {
                    log.error("Failed to close socket " + addressStr, tt);
                }
            return null;
        }
        log.info("Connected to " + addressStr);
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

    private static KeyStore getKeyStore(String type, String provider, String url, String password) throws GeneralSecurityException, IOException {
        KeyStore result = provider == null ?
            KeyStore.getInstance(type) :
            KeyStore.getInstance(type, provider);
        result.load(url == null ? null : new URLInputStream(url), password == null ? null : password.toCharArray());
        return result;
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
        return hostNames + (port == 0 ? "" : ":" + port);
    }

    private void resolveAddresses() {
        // resolve addresses
        Set<SocketAddress> addresses = new HashSet<>();
        for (SocketAddress parsedAddress : parsedAddresses) {
            // Resolve all host addresses
            if (log != null)
                log.info("Resolving IPs for " + parsedAddress.host);
            try {
                InetAddress[] temp = InetAddress.getAllByName(parsedAddress.host);
                for (InetAddress inetAddress : temp)
                    addresses.add(new SocketAddress(inetAddress.getHostAddress(), parsedAddress.port));
            } catch (UnknownHostException e) {
                // We may reside under HTTPS proxy without having access to DNS server.
                // In this case let the proxy try to resolve the required address later.
                // Otherwise we will just get another UnknownHostException later anyway.
                if (log != null)
                    log.warn("Failed to resolve IPs for " + parsedAddress.host);
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
