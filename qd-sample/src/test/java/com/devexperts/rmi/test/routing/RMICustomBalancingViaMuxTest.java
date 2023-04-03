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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.devexperts.rmi.task.BalanceResult.route;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This test checks load balancing plugged into a multiplexer. Advertisement messages do not reach the client
 * (due to [advertise=none] filter configured on the connection)
 */
@RunWith(TraceRunner.class)
public class RMICustomBalancingViaMuxTest extends AbstractRMICustomBalancingTest {
    private DifferentServices.CalculatorService serviceImpl0;
    private DifferentServices.CalculatorService serviceImpl1;
    private MuxRoutingSide muxes;

    @Override
    @After
    public void tearDown() throws InterruptedException {
        muxes.close();
        super.tearDown();
    }

    @Test
    public void testRouting() throws InterruptedException, RMIException {
        configure();

        // Route to the first server
        RMIService<?> service = muxes.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        await(() -> service.getDescriptors().size() == 2);
        RMIServiceDescriptor target = getTargetDescriptor(service, serviceImpl0);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> Promise.completed(route(target.getServiceId()));
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(),
            servers.servers[0].getEndpointId(), muxes.servers[0].getEndpointId());

        // Do the same but asynchronously
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> completeAsync(route(target.getServiceId()));
        sum = createSumRequest();
        sum.send();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(),
            servers.servers[0].getEndpointId(), muxes.servers[0].getEndpointId());

        // Disconnect the first server and wait until mux forgets about it
        servers.servers[0].disconnect();
        await(() -> service.getDescriptors().size() == 1);

        // Attempt to route the request to the disconnected server
        sum = createSumRequest();
        sum.send();
        assertRejected(sum, "Load balancer selected an unreachable service " + target.getServiceId());

        // Instruct the balancer to route everything to the second server
        RMIServiceDescriptor secondTarget = getTargetDescriptor(service, serviceImpl1);
        assertNotNull(secondTarget);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior =
            req -> completeAsync(route(secondTarget.getServiceId()));

        sum = createSumRequest();
        sum.send();
        sum.getBlocking();
        assertEquals(3.2, sum.getBlocking(), 0.00001);
        assertRoute(sum.getResponseMessage().getRoute(),
            servers.servers[1].getEndpointId(), muxes.servers[0].getEndpointId());
        TestRMILoadBalancerFactory.assertNoServerBalancing();
    }

    @Test
    public void testCancelRequest() throws InterruptedException {
        configure();

        // Instruct the balancer to return an incomplete promise.
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);

        assertEquals(RMIRequestState.SENT, sum.getState());

        sum.cancelOrAbort();
        await(() -> sum.getState() == RMIRequestState.FAILED);
        // Actually there is no execution, but this is the problem of QD RMI currently, see javadoc for cancelOrAbort().
        assertEquals(RMIExceptionType.CANCELLED_DURING_EXECUTION, sum.getException().getType());
        await(() -> pendingPromise.get().isCancelled());
    }

    @Test
    public void testCancelRequestByTimeout() throws InterruptedException {
        configure();
        clients.clients[0].getClient().setRequestRunningTimeout(500);

        // Instruct the balancer to return an incomplete promise.
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.REQUEST_RUNNING_TIMEOUT, sum.getException().getType());
        await(() -> pendingPromise.get().isCancelled());
    }

    @Test
    public void testMuxBalancersClosed() throws InterruptedException, RMIException {
        configure();

        routeToFirstServer();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        sum.getBlocking();

        assertFalse(TestRMILoadBalancerFactory.areClientBalancersClosed());

        muxes.close();
        await(TestRMILoadBalancerFactory::areClientBalancersClosed);
    }

    private void routeToFirstServer() throws InterruptedException {
        RMIService<?> service = muxes.clients[0].getClient().getService(
            DifferentServices.CALCULATOR_SERVICE.getServiceName());
        await(() -> service.getDescriptors().size() == 2);
        RMIServiceDescriptor target = getTargetDescriptor(service, serviceImpl0);
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> Promise.completed(route(target.getServiceId()));
    }

    @Test
    public void testPendingBalancePromiseCancelledWhenMuxClosed() throws InterruptedException {
        configure();

        // Route to the first server
        AtomicReference<Promise<BalanceResult>> pendingPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingPromise.get() != null);
        assertFalse(TestRMILoadBalancerFactory.areClientBalancersClosed());

        muxes.close();
        await(TestRMILoadBalancerFactory::areClientBalancersClosed);
        await(() -> pendingPromise.get().isCancelled());
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.DISCONNECTION, sum.getException().getType());
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
        assertRejected(sum, null); // exceptions do not propagate from server to client
    }

    @Test
    public void testCustomBalancerIllBehaviorAsync() throws InterruptedException {
        configure();

        // A balancer returns a promise completed asynchronously with exception
        TestRMILoadBalancerFactory.behavior = req -> completeAsync(new RMITestException());
        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        assertRejected(sum, null); // exceptions do not propagate from server to client
    }

    @Test
    public void testPendingBalanceRequestPromiseCancel() throws InterruptedException {
        configure();

        // Balancer on the mux cancels the promise. Request should be aborted on the client
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureClientOrMuxBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);
        pendingServerPromise.get().cancel();

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, sum.getException().getType());
    }

    private void configure() throws InterruptedException {
        servers = new ServerRoutingSide(2);

        // Clients use default balancing
        enableDefaultBalancing();
        clients = new ClientRoutingSide(1);

        disableDefaultBalancing();
        muxes = new MuxRoutingSide(1);

        int[] serverPorts = servers.connectAuto();
        muxes.connectClients("(" + NTU.localHost(serverPorts[0]) + ")(" + NTU.localHost(serverPorts[1]) + ")");

        int[] muxPorts = muxes.connectServersAuto("advertise=none");
        clients.connect(NTU.localHost(muxPorts[0]));

        serviceImpl0 = new DifferentServices.CalculatorService();
        servers.export(0, serviceImpl0);
        serviceImpl1 = new DifferentServices.CalculatorService();
        servers.export(1, serviceImpl1);

        await(() -> TestRMILoadBalancerFactory.balancersCount() == 4); // 1 on each server, 2 in mux

        RMIService<?> clientService = clients.clients[0].getClient().getService(serviceImpl0.getServiceName());
        List<RMIServiceDescriptor> descriptors = clientService.getDescriptors();
        assertTrue(descriptors.isEmpty());
        TestRMILoadBalancerFactory.behavior = req -> Promise.completed(BalanceResult.route(req.getTarget()));
    }


}
