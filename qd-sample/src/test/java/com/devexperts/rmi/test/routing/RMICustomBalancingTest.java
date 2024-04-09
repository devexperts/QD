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
package com.devexperts.rmi.test.routing;

import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.test.NTU;
import com.devexperts.test.TraceRunner;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.devexperts.rmi.task.BalanceResult.route;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class RMICustomBalancingTest extends AbstractRMICustomBalancingTest {
    private DifferentServices.CalculatorService serviceImpl0;
    private DifferentServices.CalculatorService serviceImpl1;

    @Override
    @After
    public void tearDown() throws InterruptedException {
        TestRMILoadBalancerFactory.assertNoServerBalancing();
        super.tearDown();
    }

    @Test
    public void testRouting() throws InterruptedException, RMIException {
        configure();

        // Instruct client load balancers to route requests to the first server
        RMIService<?> service = clients.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        RMIServiceDescriptor target = getTargetDescriptor(service, serviceImpl0);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> Promise.completed(route(target.getServiceId()));
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(), servers.servers[0].getEndpointId());

        // Do the same but asynchronously
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> completeAsync(route(target.getServiceId()));
        sum = createSumRequest();
        sum.send();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(), servers.servers[0].getEndpointId());

        // Disconnect the first server and wait until the client forgets about it
        servers.servers[0].disconnect();
        await(() -> service.getDescriptors().size() == 1);

        // Instruct the balancer to route everything to the second server
        RMIServiceDescriptor secondTarget = getTargetDescriptor(service, serviceImpl1);
        assertNotNull(secondTarget);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior =
            req -> completeAsync(route(secondTarget.getServiceId()));

        sum = createSumRequest();
        sum.send();
        sum.getBlocking();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(), servers.servers[1].getEndpointId());
        TestRMILoadBalancerFactory.assertNoServerBalancing();
    }

    @Test
    public void testRequestSentBeforeConnection() throws InterruptedException, RMIException {
        servers = new ServerRoutingSide(2);
        clients = new ClientRoutingSide(1);

        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);
        pendingPromise.get().cancel();
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.SERVICE_UNAVAILABLE, sum.getException().getType());

        connect();

        RMIRequest<Double> sum2 = createSumRequest();
        sum2.send();
        await(() -> pendingPromise.get() != null);

        RMIService<?> service = clients.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        RMIServiceDescriptor target = getTargetDescriptor(service, serviceImpl0);

        pendingPromise.get().complete(route(target.getServiceId()));

        sum2.getBlocking();
        assertEquals(3.2, sum2.getBlocking(), 0.00001);
        assertRoute(sum2.getResponseMessage().getRoute(), servers.servers[0].getEndpointId());
        TestRMILoadBalancerFactory.assertNoServerBalancing();
    }

    @Test
    public void testPendingBalanceRequestCancel() throws InterruptedException {
        configure();

        // Instruct the balancer to return an incomplete promise.
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);

        assertEquals(RMIRequestState.WAITING_TO_SEND, sum.getState());

        sum.cancelOrAbort();
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, sum.getException().getType());
        assertTrue(pendingPromise.get().isCancelled());
    }

    @Test
    public void testPendingBalanceRequestCancelByTimeout() throws InterruptedException {
        configure();
        clients.clients[0].getClient().setRequestSendingTimeout(500);

        // Instruct the balancer to return an incomplete promise.
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.REQUEST_SENDING_TIMEOUT, sum.getException().getType());
        assertTrue(pendingPromise.get().isCancelled());
    }

    @Test
    public void testPendingBalanceRequestsRebalancing() throws InterruptedException, RMIException {
        configure();
        // Configure unlimited timeout so that any host computer slowness does not affect this test
        clients.clients[0].getClient().setRequestSendingTimeout(Long.MAX_VALUE);

        RMIService<?> service = clients.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        RMIServiceDescriptor target = getTargetDescriptor(service, serviceImpl0);

        // Instruct the balancer to return an incomplete promise.
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);

        // Now balance request is pending. We unexport the service from the first server, wait until its descriptor
        // is gone and then complete the balance promise with the id on the first server. This should lead to the
        // request being queued.
        servers.servers[0].getServer().unexport(serviceImpl0);
        await(() -> service.getDescriptors().size() == 1);
        pendingPromise.get().complete(route(target.getServiceId()));
        pendingPromise.set(null);

        // Export the service again. This should lead to the request being balanced again
        servers.servers[0].getServer().export(serviceImpl0);
        await(() -> service.getDescriptors().size() == 2);
        await(() -> pendingPromise.get() != null);
        pendingPromise.get().complete(route(target.getServiceId()));
        assertEquals(3.2, sum.getBlocking(), 0.00001);
    }

    @Test
    public void testClosedConnectionRequestsRebalancing() throws InterruptedException, RMIException {
        configure();
        // Configure unlimited timeout so that any host computer slowness does not affect this test
        clients.clients[0].getClient().setRequestSendingTimeout(Long.MAX_VALUE);

        RMIService<?> service = clients.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        BalanceResult target0 = route(getTargetDescriptor(service, serviceImpl0).getServiceId());
        BalanceResult target1 = route(getTargetDescriptor(service, serviceImpl1).getServiceId());

        AtomicInteger balanceAttempt = new AtomicInteger(0);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> {
            try {
                int attempt = balanceAttempt.getAndIncrement();
                if (attempt == 0) {
                    // both connections are alive
                    assertEquals(1, clients.getConnectionsCount(0, 0));
                    assertEquals(1, clients.getConnectionsCount(0, 1));
                    // async close of the first connection
                    new Thread(() -> servers.servers[0].disconnect()).start();
                    // wait till the first connection is closed on the client side
                    await(() -> clients.getConnectionsCount(0, 0) == 0);
                    assertEquals(1, clients.getConnectionsCount(0, 1));
                    // route the request to the closed connection
                    return Promise.completed(target0);
                }

                // the second connection is alive, route to it
                assertEquals(0, clients.getConnectionsCount(0, 0));
                assertEquals(1, clients.getConnectionsCount(0, 1));
                return Promise.completed(target1);
            } catch (InterruptedException e) {
                fail(e.getMessage());
                return null;
            }
        };

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> sum.getState() != RMIRequestState.WAITING_TO_SEND);
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertEquals(2, balanceAttempt.get());
    }

    // Balancer lifecycle when servers are closed and disconnected
    @Test
    public void testBalancerLifecycle1() throws InterruptedException {
        configure();

        // Closing of all balancers after disconnect is checked in tearDown()
        // Here we check that client and server balancers are closed after last service disappears
        assertFalse(TestRMILoadBalancerFactory.areServerBalancersClosed());

        servers.close();
        await(TestRMILoadBalancerFactory::areServerBalancersClosed);
    }

    // Balancer lifecycle when clients are disconnected
    @Test
    public void testBalancerLifecycle2() throws InterruptedException {
        configure();

        // Closing of all balancers after disconnect is checked in tearDown()
        // Here we check that the client balancers are closed after client disconnects
        assertFalse(TestRMILoadBalancerFactory.areClientBalancersClosed());

        clients.close();
        await(TestRMILoadBalancerFactory::areClientBalancersClosed);
    }

    @Test
    public void testReject() throws InterruptedException {
        configure();

        // Sync
        TestRMILoadBalancerFactory.behavior = req -> Promise.completed(BalanceResult.reject(null, null));
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertRejected(sum, "Request rejected by load balancer");

        // Async
        TestRMILoadBalancerFactory.behavior = req -> completeAsync(BalanceResult.reject(null, null));
        sum = createSumRequest();
        sum.send();
        assertRejected(sum, "Request rejected by load balancer");
    }

    @Test
    public void testCustomBalancerIllBehavior() throws InterruptedException {
        configure();

        // 1. A balancer returns null, the request should be rejected
        TestRMILoadBalancerFactory.behavior = req -> null;
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertRejected(sum, "Load balancer returned null");

        // 2. A balancer throws an exception, the result should be rejected
        TestRMILoadBalancerFactory.behavior = req -> {
            throw new UnsupportedOperationException();
        };
        sum = createSumRequest();
        sum.send();
        assertRejected(sum, null);

        // 3. A balancer returns a promise completed with exception
        TestRMILoadBalancerFactory.behavior = req -> Promise.failed(new RMITestException());
        sum = createSumRequest();
        sum.send();
        assertRejected(sum, null, RMITestException.class);
    }

    @Test
    public void testCustomBalancerIllBehaviorAsync() throws InterruptedException {
        configure();

        // A balancer returns a promise completed asynchronously with exception
        TestRMILoadBalancerFactory.behavior = req -> completeAsync(new RMITestException());
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertRejected(sum, null, RMITestException.class);
    }

    private void configure() throws InterruptedException {
        servers = new ServerRoutingSide(2);
        clients = new ClientRoutingSide(1);
        connect();
    }

    private void connect() throws InterruptedException {
        int[] serverPorts = servers.connectAuto();
        clients.connect("(" + NTU.localHost(serverPorts[0]) + ")(" + NTU.localHost(serverPorts[1]) + ")");

        serviceImpl0 = new DifferentServices.CalculatorService();
        servers.export(0, serviceImpl0);
        serviceImpl1 = new DifferentServices.CalculatorService();
        servers.export(1, serviceImpl1);
        clients.waitForServices(2, DifferentServices.CALCULATOR_SERVICE.getServiceName());
    }



}
