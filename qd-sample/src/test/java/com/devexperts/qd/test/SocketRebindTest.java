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
package com.devexperts.qd.test;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.nio.NioServerConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class SocketRebindTest {

    private static final Logging log = Logging.getLogging(SocketRebindTest.class);
    private ServerSocket serverSocket;
    private MessageConnector connector;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws IOException {
        if (serverSocket != null)
            serverSocket.close();
        if (connector != null && connector.isActive())
            connector.stop();
        ThreadCleanCheck.after();
    }

    @Test
    public void testRebind() throws IOException {
        implTestRebind(false);
    }

    @Test
    public void testRebindNio() throws IOException {
        assumeFalse("Known issue: QD-1137",
            System.getProperty("os.name").toLowerCase().startsWith("mac") &&
            System.getProperty("java.version").startsWith("1.8")
        );
        implTestRebind(true);
    }

    private void implTestRebind(boolean nio) throws IOException {
        ApplicationConnectionFactory factory = MessageConnectors.applicationConnectionFactory(stats -> null);

        // Bind a server port
        serverSocket = new ServerSocket(0);
        final int port = serverSocket.getLocalPort();

        log.info("Bound server port " + port);

        connector = nio ? new NioServerConnector(factory, port) : new ServerSocketConnector(factory, port);
        connector.setReconnectDelay(50);

        final Thread testThread = Thread.currentThread();
        connector.addMessageConnectorListener(c -> LockSupport.unpark(testThread));

        String connectorName = UUID.randomUUID().toString();
        //noinspection deprecation
        Promise<Integer> p = ServerSocketTestHelper.createPortPromise(connectorName);
        connector.setName(connectorName);

        // start connector and wait for a failed attempt
        connector.start();
        awaitException(p);

        // await a second failed attempt (may miss some, it's ok)
        //noinspection deprecation
        p = ServerSocketTestHelper.createPortPromise(connectorName);
        awaitException(p);

        // free port and wait for successful connection
        serverSocket.close();
        serverSocket = null;
        assertTrue("Successful connect expected",
            waitCondition(10_000, 10, () -> connector.getState() == MessageConnectorState.CONNECTED));

        connector.stop();
        assertTrue(waitCondition(10_000, 10, () -> !connector.isActive()));
    }

    private void awaitException(Promise<Integer> p) {
        try {
            p.await(10_000, TimeUnit.MILLISECONDS);
        } catch (Throwable ignore) {
        }
        assertTrue("Missed connection exception", !p.hasResult() && !p.isCancelled());
    }

    private boolean waitCondition(long timeout, long pollPeriod, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeout;
        while (!condition.getAsBoolean()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(pollPeriod));
            if (System.currentTimeMillis() > deadline)
                return condition.getAsBoolean();
        }
        return true;
    }
}
