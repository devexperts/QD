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
package com.devexperts.rmi.test.routing;

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
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This test checks load balancing when advertisement messages do not reach the client
 * (due to [advertise=none] filter configured on the connection)
 */
@RunWith(TraceRunner.class)
public class RMICustomBalancingNoAdTest extends AbstractRMICustomBalancingTest {
    private DifferentServices.CalculatorService serviceImpl0;
    private DifferentServices.CalculatorService serviceImpl1;

    @Test
    public void testPendingBalanceRequestCancelByTimeoutOnServer() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server does not complete the request.
        // Timeout happens on the client, request should be aborted on the server
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);

        clients.clients[0].getClient().setRequestRunningTimeout(5);
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.REQUEST_RUNNING_TIMEOUT, sum.getException().getType());
        await(() -> pendingServerPromise.get().isCancelled());
    }

    @Test
    public void testPendingBalanceRequestCancelByDisconnectOnServer() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server does not complete the request.
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);

        clients.clients[0].disconnect();
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.DISCONNECTION, sum.getException().getType());
        await(() -> pendingServerPromise.get().isCancelled());
    }

    @Test
    public void testPendingBalanceRequestPromiseCancel() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server cancels the promise.
        // Request should be aborted on the client
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);
        pendingServerPromise.get().cancel();

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, sum.getException().getType());
    }

    @Test
    public void testServerSideReject() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server rejects the request.
        // Request should be aborted on the client.
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);
        pendingServerPromise.get().complete(BalanceResult.reject(null, null));

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertRejected(sum, "Request rejected by load balancer");
    }

    @Test
    public void testServerSideRejectViaException() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server rejects the request by completing the promise
        // exceptionally.
        // Request should be aborted on the client.
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();
        await(() -> pendingServerPromise.get() != null);
        pendingServerPromise.get().completeExceptionally(new RMITestException());

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertRejected(sum, null);
    }

    @Test
    public void testServerSideRejectViaReturningNull() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server returns null.
        // Request should be aborted on the client.
        TestRMILoadBalancerFactory.serverBalancerBehavior = req -> null;

        RMIRequest<Double> sum = createSumRequest();
        sum.send();

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertRejected(sum, "Load balancer returned null");
    }

    @Test
    public void testServerSideRejectViaThrowingException() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server throws an exception.
        // Request should be aborted on the client.
        TestRMILoadBalancerFactory.serverBalancerBehavior = req -> {
            throw new RMITestException();
        };

        RMIRequest<Double> sum = createSumRequest();
        sum.send();

        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertRejected(sum, null);
    }

    @Test
    public void testCleanupAfterClose() throws InterruptedException {
        configure();

        // Route the request to the server and then close the server's endpoints. The promise should be cancelled,
        // the request should be
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();

        await(() -> pendingServerPromise.get() != null);
        assertFalse(pendingServerPromise.get().isCancelled());
        servers.close();
        await(() -> pendingServerPromise.get().isCancelled());
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.DISCONNECTION, sum.getException().getType());
        await(TestRMILoadBalancerFactory::areServerBalancersClosed);
    }

    @Test
    public void testCleanupAfterDisconnect() throws InterruptedException {
        configure();

        // Route the request to the server. Balancer on the server creates but doesn't complete a promise
        // Disconnect should abort the request and cancel the promise
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = captureServerSideBalancePromise();

        RMIRequest<Double> sum = createSumRequest();
        sum.send();

        await(() -> pendingServerPromise.get() != null);
        assertFalse(pendingServerPromise.get().isCancelled());
        servers.disconnect();
        await(() -> pendingServerPromise.get().isCancelled());
        await(() -> sum.getState() == RMIRequestState.FAILED);
        assertEquals(RMIExceptionType.DISCONNECTION, sum.getException().getType());
    }


    private void configure() throws InterruptedException {
        servers = new ServerRoutingSide(2);
        clients = new ClientRoutingSide(1);
        int[] serverPorts = servers.connectAuto("advertise=none", "advertise=none");
        clients.connect("(" + NTU.localHost(serverPorts[0]) + ")(" + NTU.localHost(serverPorts[1]) + ")");

        serviceImpl0 = new DifferentServices.CalculatorService();
        servers.export(0, serviceImpl0);
        serviceImpl1 = new DifferentServices.CalculatorService();
        servers.export(1, serviceImpl1);

        // Wait until the balancers are created
        await(() -> TestRMILoadBalancerFactory.balancersCount() == 2);

        RMIService<?> clientService = clients.clients[0].getClient().getService(serviceImpl0.getServiceName());
        List<RMIServiceDescriptor> descriptors = clientService.getDescriptors();
        assertTrue(descriptors.isEmpty());
        TestRMILoadBalancerFactory.behavior = req -> Promise.completed(BalanceResult.route(req.getTarget()));
    }
}
