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
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DXEndpointListenerTest {
    private static final Logging log = Logging.getLogging(DXEndpointListenerTest.class);
    private static final int AWAIT_TIMEOUT = 10_000;

    private volatile CountDownLatch pass;
    private volatile DXEndpoint.State expectedState;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        ThreadCleanCheck.after();
    }

    @Test
    public void testSimpleListener() throws InterruptedException {
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
        pass = new CountDownLatch(1);
        expectedState = DXEndpoint.State.CONNECTED;
        endpoint.addStateChangeListener(evt -> {
            logChangeEvent(evt);
            if (evt.getNewValue() == expectedState)
                pass.countDown();
        });
        endpoint.connect(":0");
        await("CONNECTED state reached", pass);
        pass = new CountDownLatch(1);
        expectedState = DXEndpoint.State.NOT_CONNECTED;
        endpoint.disconnect();
        await("NOT_CONNECTED state reached", pass);
        pass = new CountDownLatch(1);
        expectedState = DXEndpoint.State.CLOSED;
        endpoint.close();
        await("CLOSED state reached", pass);
    }

    @Test
    public void testDisconnectFromListener() throws InterruptedException {
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
        endpoint.addStateChangeListener(evt -> {
            logChangeEvent(evt);
            if (evt.getNewValue() == DXEndpoint.State.CONNECTED) {
                endpoint.disconnect();
            }
        });
        endpoint.connect(":0");
        endpoint.awaitNotConnected();
        endpoint.close();
    }

    @Test
    public void testInnerAwaitNotConnected() throws InterruptedException {
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withProperty(DXEndpoint.DXFEED_THREAD_POOL_SIZE_PROPERTY, "4")
            .build();
        pass = new CountDownLatch(1);
        CountDownLatch innerWaitSucceded = new CountDownLatch(1);
        endpoint.addStateChangeListener(evt -> {
            logChangeEvent(evt);
            if (evt.getNewValue() == DXEndpoint.State.CONNECTED) {
                pass.countDown();
                log.info("Inner wait for disconnect...");
                try {
                    endpoint.awaitNotConnected();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                innerWaitSucceded.countDown();
            }
        });
        endpoint.connect(":0");
        await("CONNECTED state reached", pass);
        log.info("Disconnecting...");
        endpoint.disconnect();
        await("NOT_CONNECTED witnessed by inner wait", innerWaitSucceded);
        log.info("Closing...");
        endpoint.close();
    }

    @Test
    public void testInnerAwaitClosed() throws InterruptedException {
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withProperty(DXEndpoint.DXFEED_THREAD_POOL_SIZE_PROPERTY, "4")
            .build();
        pass = new CountDownLatch(1);
        CountDownLatch innerWaitSucceded = new CountDownLatch(1);
        endpoint.addStateChangeListener(evt -> {
            logChangeEvent(evt);
            if (evt.getNewValue() == DXEndpoint.State.CONNECTED) {
                pass.countDown();
            }
            if (evt.getNewValue() == DXEndpoint.State.NOT_CONNECTED) {
                pass.countDown();
                log.info("Inner wait for disconnect...");
                try {
                    endpoint.closeAndAwaitTermination();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                innerWaitSucceded.countDown();
            }
        });
        endpoint.connect(":0");
        // It's important to get connected state here.
        // Fast disconnect may cause that listener will never be notified by current contract.
        await("CONNECTED state reached", pass);
        pass = new CountDownLatch(1);
        log.info("Disconnecting...");
        endpoint.disconnect();
        await("NOT_CONNECTED state reached", pass);
        log.info("Waiting inner close...");
        await("Closed by inner wait", innerWaitSucceded);
        assertEquals(DXEndpoint.State.CLOSED, endpoint.getState());
    }

    private void await(String message, CountDownLatch pass) throws InterruptedException {
        assertTrue(message, pass.await(AWAIT_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private void logChangeEvent(PropertyChangeEvent evt) {
        log.info("****** " + evt.getPropertyName() + ": " + evt.getOldValue() + " -> " + evt.getNewValue());
    }
}
