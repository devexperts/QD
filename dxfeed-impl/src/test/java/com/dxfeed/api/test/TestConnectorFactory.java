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
package com.dxfeed.api.test;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorFactory;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>The class provides facility for connector subclassing allowing simulation of network peculiarities in tests.
 * <p>
 * Example:
 * <pre>
 * Set<Integer> blockedPorts = SynchronizedIndexedSet.create();
 * TestConnectorFactory.addClientSocketConnectorForBlockedPorts(testId + ":", blockedPorts);
 * ...
 * blockedPorts.add(port_to_be_blocked);
 * feedEndpoint.connect(testId + ":" + address);
 * </pre>
 *
 * @deprecated For tests only!
 */
public class TestConnectorFactory implements MessageConnectorFactory {

    private static final ConcurrentHashMap<String, TestFactory> factories = new ConcurrentHashMap<>();

    /**
     * Single-Abstract-Method factory for test connectors for convenience.
     */
    public interface TestFactory {
        /**
         * Creates message connector for test purposes. Each factory is used only once and is removed thereafter.
         *
         * @param acf a delegate factory to be passed to underlying connector
         * @param address an address without prefix
         * @return message connector for test purposes
         */
        public MessageConnector create(ApplicationConnectionFactory acf, String address);
    }

    /**
     * Adds test factory for specified address prefix. The factory will be used only once and will be
     * immediately removed after that to prevent memory leak.
     *
     * @param prefix a matching prefix of the address, for one-time usage, presumably unique
     * @param testFactory a one-time factory for addresses starting with specified prefix
     */
    public static void add(String prefix, TestFactory testFactory) {
        factories.put(prefix, testFactory);
    }

    /**
     * Adds client socket connector for specified address prefix which ignores blocked ports.
     * Whenever added connector selects address with blocked ports - it fails and selects next address.
     * Note that blocked ports only affects future connections. Modifying it does not affect existing connections.
     *
     * @param prefix a matching prefix of the address, for one-time usage, presumably unique
     * @param blockedPorts a modifiable, thread-safe collection of blocked ports
     */
    public static void addClientSocketConnectorForBlockedPorts(String prefix, Set<Integer> blockedPorts) {
        add(prefix, (acf, address) -> {
            int portIndex = address.lastIndexOf(':');
            String host = address.substring(0, portIndex);
            int port = Integer.parseInt(address.substring(portIndex + 1));
            return new ClientSocketConnector(acf, host, port) {
                @Override
                protected Socket createSocket(String host, int port) throws IOException {
                    if (blockedPorts.contains(port))
                        throw new ConnectException("Port is blocked " + port);
                    return super.createSocket(host, port);
                }
            };
        });
    }

    /**
     * Removes test connector factory for specified address prefix.
     * @param prefix
     */
    public static void remove(String prefix) {
        factories.remove(prefix);
    }

    @Override
    public MessageConnector createMessageConnector(ApplicationConnectionFactory acf, String address) {
        for (String prefix : factories.keySet()) {
            if (address.startsWith(prefix)) {
                return factories.remove(prefix).create(acf, address.substring(prefix.length()));
            }
        }
        return null;
    }

    @Override
    public Class<? extends MessageConnector> getResultingClass() {
        return MessageConnector.class;
    }
}
