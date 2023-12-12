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
package com.devexperts.rmi.impl;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.test.NTU;
import com.devexperts.rmi.test.TestThreadPool;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(TraceRunner.class)
public class RunningTaskMemoryLeakTest {
    private static final Logging log = Logging.getLogging(RunningTaskMemoryLeakTest.class);
    private final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor =
        new TestThreadPool(5, RunningTaskMemoryLeakTest.class.getSimpleName(), exceptions);

    private RMIEndpointImpl server;
    private RMIEndpointImpl client;
    private CountDownLatch oneTaskStarted;
    private DummySupplier proxy;

    private AtomicInteger lastRequestId;
    private List<RMIConnection> serverConnections;
    private List<RMIConnection> clientConnections;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        lastRequestId = new AtomicInteger(0);
        server = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("Server")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        client = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("client")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        serverConnections = null;
        clientConnections = null;
        server.getServer().setDefaultExecutor(executor);
        client.getClient().setDefaultExecutor(executor);
        client.getClient().setRequestRunningTimeout(10_000);
        oneTaskStarted = new CountDownLatch(1);
        server.getServer().export(new DummySupplierImpl(), DummySupplier.class);
        proxy = client.getClient().getProxy(DummySupplier.class);
    }

    @After
    public void tearDown() {
        client.close();
        server.close();
        executor.shutdown();
        assertTrue(exceptions.toString(), exceptions.isEmpty());
        ThreadCleanCheck.after();
    }

    @Test
    public void testSuccessful() {
        connect();
        List<Promise<String>> promises = doRequests(1000, 0, DummySupplier.OK);
        promises.forEach(promise -> assertNotNull("Result expected", promise.await(1000, TimeUnit.MILLISECONDS)));
        awaitRunningTasks();
    }

    @Test
    public void testExceptional() {
        connect();
        List<Promise<String>> promises = doRequests(10, 0, DummySupplier.EXCEPTION);
        promises.forEach(promise -> {
            try {
                promise.await(5, TimeUnit.SECONDS);
                fail("Exception expected, but '" + promise.getResult() + "'received");
            } catch (PromiseException e) {
                Throwable exception = e.getCause();
                assertNotNull("Exception expected, but '" + promise.getResult() + "' received", exception);
                assertEquals("Error", exception.getMessage());
            }
        });
        awaitRunningTasks();
    }

    @Test
    public void testClientCancel() throws InterruptedException {
        connect();
        List<Promise<String>> promises = doRequests(1000, Long.MAX_VALUE, DummySupplier.FAIL);
        awaitStart();
        promises.forEach(promise -> RMIRequest.of(promise).cancelOrAbort());
        awaitRunningTasks();
    }

    @Test
    public void testClientCancelWithConfirmation() throws InterruptedException {
        connect();
        List<Promise<String>> promises = doRequests(1000, Long.MAX_VALUE, DummySupplier.FAIL);
        awaitAllSent(promises);
        promises.forEach(promise -> RMIRequest.of(promise).cancelWithConfirmation());
        promises.forEach(promise -> {
            try {
                promise.await(5, TimeUnit.SECONDS);
                fail("Exception expected, but '" + promise.getResult() + "' received");
            } catch (PromiseException e) {
                assertNotNull("Exception expected, but '" + promise.getResult() + "' received", e.getCause());
            }
        });
        awaitRunningTasks();
    }

    @Test
    public void testClientDisconnect() throws InterruptedException {
        connect();
        testDisconnect(client);
    }

    @Test
    public void testServerDisconnect() throws InterruptedException {
        connect();
        testDisconnect(server);
    }

    private void testDisconnect(RMIEndpoint endpoint) throws InterruptedException {
        // send long-working requests and await that all are sent to the server
        List<Promise<String>> promises = doRequests(1000, Long.MAX_VALUE, DummySupplier.FAIL);
        awaitAllSent(promises);
        endpoint.disconnect();
        // all requests should properly fail
        promises.forEach(promise -> {
            try {
                promise.await(5, TimeUnit.SECONDS);
                fail("Expected a PromiseException, but '" + promise.getResult() + "' received");
            } catch (PromiseException e) {
                assertNotNull("Exception expected, but '" + promise.getResult() + "' received", e.getCause());
            }
        });
        awaitRunningTasks();
        assertFalse("Completed task on server", checkServerConnections(RunningTaskMemoryLeakTest::hasCompletedTask));
        assertFalse("Completed task on client", checkClientConnections(RunningTaskMemoryLeakTest::hasCompletedTask));
    }

    private void connect() {
        int port = NTU.connectServer(server);
        NTU.connect(client, NTU.localHost(port));
        NTU.waitCondition(10_000, 100, () -> client.isConnected());
        // capture initially open connections
        serverConnections = getConnections(server);
        clientConnections = getConnections(client);
    }

    /**
     * @param qty number of requests
     * @param timeout pause in milliseconds before sending response
     * @param outcome 0 = ok, 1 = fail, 2 = exception
     */
    private List<Promise<String>> doRequests(int qty, long timeout, int outcome) {
        return Stream.generate(() -> proxy.getPromise(lastRequestId.incrementAndGet(), timeout, outcome))
            .limit(qty)
            .collect(Collectors.toList());
    }

    private void awaitRunningTasks() {
        NTU.waitCondition(10_000, 100, () ->
            !checkClientConnections(RunningTaskMemoryLeakTest::hasRunningTask) &&
            !checkServerConnections(RunningTaskMemoryLeakTest::hasRunningTask)
        );
        assertFalse("Running task on server", checkServerConnections(RunningTaskMemoryLeakTest::hasRunningTask));
        assertFalse("Running task on client", checkClientConnections(RunningTaskMemoryLeakTest::hasRunningTask));
    }

    private boolean checkClientConnections(Predicate<RMIConnection> test) {
        return connectionsAnyMatch(getConnections(client), test) || connectionsAnyMatch(clientConnections, test);
    }

    private boolean checkServerConnections(Predicate<RMIConnection> test) {
        return connectionsAnyMatch(getConnections(server), test) || connectionsAnyMatch(serverConnections, test);
    }

    /**
     * Await that some request started processing on server
     */
    private void awaitStart() throws InterruptedException {
        long timeToStart = 10_000;
        if (!oneTaskStarted.await(timeToStart, TimeUnit.MILLISECONDS)) {
            fail("Server didn't receive any task in " + timeToStart + "ms");
        }
    }

    /**
     * Await that all specified requests was sent to the server
     *
     * @param promises - promises corresponding to requests to be checked
     */
    private void awaitAllSent(List<Promise<String>> promises) throws InterruptedException {
        int timeout = 10_000;
        ArrayDeque<RMIRequest<?>> remaining = promises.stream()
            .map(RMIRequest::of)
            .collect(Collectors.toCollection(() -> new ArrayDeque<>(promises.size())));

        long now = System.currentTimeMillis();
        long deadline = now + timeout;
        while (now < deadline) {
            // check all remaining elements once
            int size = remaining.size();
            for (int i = 0; i < size; i++) {
                RMIRequest<?> request = remaining.remove();
                RMIRequestState state = request.getState();
                if (!state.isCompleted() && state != RMIRequestState.SENT)
                    remaining.add(request);
            }
            if (remaining.isEmpty())
                return;
            Thread.sleep(10);
            now = System.currentTimeMillis();
        }
        fail(remaining.size() + " of " + promises.size() + " requests was not sent in " + timeout + " ms");
    }

    private boolean connectionsAnyMatch(Collection<RMIConnection> connections, Predicate<RMIConnection> test) {
        if (connections == null)
            return false;
        return connections.stream().anyMatch(test);
    }

    private List<RMIConnection> getConnections(RMIEndpointImpl endpoint) {
        List<RMIConnection> res = new ArrayList<>();
        endpoint.concurrentConnectionsIterator().forEachRemaining(res::add);
        return res;
    }

    static boolean hasRunningTask(RMIConnection connection) {
        return connection.tasksManager.hasRunningTask();
    }

    static boolean hasCompletedTask(RMIConnection connection) {
        return connection.tasksManager.completedTaskSize() > 0;
    }

    private interface DummySupplier {

        static final int OK = 0;
        static final int FAIL = 1;
        static final int EXCEPTION = 2;

        // outcome: 0 = ok, 1 = fail, 2 = exception
        Promise<String> getPromise(int id, long millis, int outcome);
    }

    private class DummySupplierImpl implements DummySupplier {

        @Override
        public Promise<String> getPromise(int id, long millis, int outcome) {
            oneTaskStarted.countDown();
            sleep(millis, id);
            if (outcome == EXCEPTION) {
                throw new RuntimeException("Error");
            }
            return outcome == OK ? Promise.completed("Ok") : Promise.failed(new RuntimeException("Fail"));
        }

        private void sleep(long millis, int id) {
            log.trace("Started " + id);
            RMITask<String> current = RMITask.current(String.class);
            try {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < millis) {
                    Thread.sleep(100);
                    if (current.getState().isCompletedOrCancelling()) {
                        log.info("Cancelled " + id + ": " + current);
                        return;
                    }
                }
            } catch (InterruptedException ignore) {
                log.info("Interrupted " + id + ": " + current);
            }
        }
    }
}
