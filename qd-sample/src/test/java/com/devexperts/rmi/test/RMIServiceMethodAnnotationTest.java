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
package com.devexperts.rmi.test;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIServiceInterface;
import com.devexperts.rmi.RMIServiceMethod;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class RMIServiceMethodAnnotationTest {
    private static final int TIMEOUT_MS = 10000;
    private TestThreadPool executor;
    private RMIEndpoint server;
    private RMIEndpoint client;

    private static final int N_REQS = 4;

    private List<Throwable> exceptions = new ArrayList<>();
    private final CyclicBarrier doOneWayBarrier = new CyclicBarrier(N_REQS + 1);

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
        if (executor != null)
            executor.shutdown();
        ThreadCleanCheck.after();
        assertTrue(exceptions.isEmpty());
    }

    @Test
    public void testAnnotations() throws RMIException {
        executor = new TestThreadPool(N_REQS, "RMIServiceMethodAnnotationTest", exceptions);
        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        server.getServer().setDefaultExecutor(executor);
        client = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        client.getClient().setRequestRunningTimeout(TIMEOUT_MS);
        client.getClient().setRequestSendingTimeout(TIMEOUT_MS);
        NTU.connectPair(server, client);
        server.getServer().export(new AServiceImpl(), AService.class);
        AService proxy = client.getClient().getProxy(AService.class);
        // make sure one request invocations do not wait response and it is null
        for (int i = 0; i < N_REQS; i++) {
            Promise<String> promise = proxy.doOneWay(i);
            assertNull(promise.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        awaitDoOneBarrier();
        // make sure that two-way invocations wait response
        for (int i = 0; i < N_REQS; i++) {
            Promise<String> promise = proxy.doDefault(i);
            assertEquals("doDefault" + i, promise.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        for (int i = 0; i < N_REQS; i++) {
            Promise<String> promise = proxy.doDefault2(i);
            assertEquals("doDefault2" + i, promise.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        for (int i = 0; i < N_REQS; i++) {
            Promise<String> promise = proxy.doNamed(i);
            assertEquals("doNamed" + i, promise.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        // make request to named method to make sure it responds on other name
        RMIOperation<String> otherOp = RMIOperation.valueOf("AService", String.class, "other", int.class);
        for (int i = 0; i < N_REQS; i++) {
            RMIRequest<String> request = client.getClient().createRequest(null, otherOp, i);
            request.send();
            assertEquals("doNamed" + i, request.getBlocking());
        }

    }

    @RMIServiceInterface(name = "AService")
    interface AService {
        public Promise<String> doDefault(int x);

        @RMIServiceMethod(type = RMIRequestType.DEFAULT)
        public Promise<String> doDefault2(int x);

        @RMIServiceMethod(type = RMIRequestType.ONE_WAY)
        public Promise<String> doOneWay(int x);

        @RMIServiceMethod(name = "other")
        public Promise<String> doNamed(int x);
    }

    class AServiceImpl implements AService {
        @Override
        public Promise<String> doDefault(int x) {
            return Promise.completed("doDefault" + x);
        }

        @Override
        public Promise<String> doDefault2(int x) {
            return Promise.completed("doDefault2" + x);
        }

        @Override
        public Promise<String> doOneWay(int x) {
            awaitDoOneBarrier();
            return Promise.completed("doOneWay" + x);
        }

        @Override
        public Promise<String> doNamed(int x) {
            return Promise.completed("doNamed" + x);
        }
    }

    private void awaitDoOneBarrier() {
        try {
            doOneWayBarrier.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
