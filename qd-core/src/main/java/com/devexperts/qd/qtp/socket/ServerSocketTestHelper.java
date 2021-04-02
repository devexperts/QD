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

import com.dxfeed.promise.Promise;

import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>The class provides facility for automated port allocation by system for server connectors in tests.
 * <p>
 * Usage:<ul>
 * <li>create promise for a connector name with {@link ServerSocketTestHelper#createPortPromise(String)}
 * <li>in connector address specify zero port and the chosen name
 * <li>promise will be completed with an assigned local port value as soon as specified connector perform socket binding.
 * <li>use retrieved port to configure a client connection of the test
 * </ul>
 * <p>
 * Example:
 * <pre>
 * Promise<Integer> serverPort = ServerSocketTestHelper.createPortPromise("connector-name");
 * ...
 * String serverConnectorAddress = ":0[name=connector-name]"
 * // configure server side using serverConnectorAddress
 * ...
 * String serverAddress = "localhost:" + serverPort.await(...);
 * // configure client side using serverAddress
 * </pre>
 *
 * @deprecated For tests only!
 */
@Deprecated
public class ServerSocketTestHelper {

    private static final ConcurrentHashMap<String, Promise<Integer>> promises = new ConcurrentHashMap<>();

    /**
     * Create a promise to retrieve a local port of specified server connector.
     * Connector will be identified by name. A name should be unique in the testing scope
     * or wrong connector matching may happen.
     *
     * @param connectorName - a name of connector to be auto configured
     * @return a promise object that will be filled with local port value by connector.
     */
    public static Promise<Integer> createPortPromise(String connectorName) {
        return promises.computeIfAbsent(connectorName, n -> {
            Promise<Integer> p = new Promise<>();
            p.whenDone(pz -> promises.remove(connectorName, pz));
            return p;
        });
    }

    /**
     * Fill a port promise associated with provided connector name (if it exists). The method is used by connectors
     * supporting this testing facility.
     *
     * @param connectorName - a name of promise to be completed
     * @param port - a local port value of connector
     */
    public static void completePortPromise(String connectorName, int port) {
        Promise<Integer> p = promises.get(connectorName);
        if (p != null) {
            p.complete(port);
        }
    }

    /**
     * Fail a port promise associated with provided connector name (if it exists). The method is used by connectors
     * supporting this testing facility.
     *
     * @param connectorName - a name of promise to be completed
     * @param exception - a cause of connection failure
     */
    public static void failPortPromise(String connectorName, Throwable exception) {
        Promise<Integer> p = promises.get(connectorName);
        if (p != null) {
            p.completeExceptionally(exception);
        }
    }

    private ServerSocketTestHelper() {
        // no instances
    }

}
