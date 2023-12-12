/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample;

import com.devexperts.connector.proto.Configurable;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory;
import com.devexperts.qd.stats.QDStats;

import java.io.IOException;
import java.net.Socket;
import java.util.EnumSet;

public class SampleServer {
    static final String AUTH_TOKEN_STRING = "SAMPLE";
    static final byte[] AUTH_TOKEN_BYTES = AUTH_TOKEN_STRING.getBytes();

    private static final Logging log = Logging.getLogging(SampleServer.class);

    public static void main(String[] args) {
        initServer(args.length <= 0 ? ":5555" : args[0], 1235);
    }

    /**
     * Creates server at specified address.
     * @param address address to start the server at.
     * @param jmxHtmlPort HTML JMX port.
     */
    public static void initServer(String address, int jmxHtmlPort) {
        DataScheme scheme = SampleScheme.getInstance();
        QDEndpoint endpoint = QDEndpoint.newBuilder()
            .withName("server")
            .withScheme(scheme)
            .withContracts(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .withProperties(Sample.getMonitoringProps(jmxHtmlPort))
            .build();
        endpoint.getStream().setEnableWildcards(true);

        endpoint.addConnectors(MessageConnectors.createMessageConnectors(
            new ServerAdapterFactory(endpoint), address, endpoint.getRootStats()));
        endpoint.startConnectors();

        Thread generator = new SampleGeneratorThread(endpoint);
        generator.start();
    }

    private static class ServerAdapterFactory extends AgentAdapter.Factory
        implements SocketMessageAdapterFactory
    {
        boolean customAuth;

        public boolean isCustomAuth() {
            return customAuth;
        }

        @Configurable
        public void setCustomAuth(boolean auth) {
            this.customAuth = auth;
        }

        ServerAdapterFactory(QDEndpoint endpoint) {
            super(endpoint, null);
        }

        @Override
        public MessageAdapter createAdapterWithSocket(Socket socket, QDStats stats) throws SecurityException, IOException {
            if (customAuth) {
                byte[] bytes = new byte[AUTH_TOKEN_BYTES.length];
                int n = 0;
                int result;
                while (n < bytes.length) {
                    result = socket.getInputStream().read(bytes, n, bytes.length - n);
                    if (result < 0)
                        throw new IOException("EOF instead of auth token");
                    n += result;
                }
                String s = new String(bytes);
                log.debug("Auth token received: " + s);
                if (!AUTH_TOKEN_STRING.equals(s))
                    throw new SecurityException("Invalid auth token -- access denied.");
            }
            return createAdapter(stats);
        }
    }
}
