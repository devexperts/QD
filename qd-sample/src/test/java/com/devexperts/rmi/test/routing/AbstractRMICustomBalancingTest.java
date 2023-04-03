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

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public abstract class AbstractRMICustomBalancingTest {
    private static final Logging log = Logging.getLogging(AbstractRMICustomBalancingTest.class);

    private static final String DEFAULT_BALANCING_DISABLED =
        "com.devexperts.rmi.impl.DefaultLoadBalancerFactory.disable";
    private ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    @Rule
    public Timeout globalTimeout= new Timeout(60, TimeUnit.SECONDS);
    ServerRoutingSide servers;
    ClientRoutingSide clients;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        TestRMILoadBalancerFactory.reset();
        disableDefaultBalancing();
    }

    @After
    public void tearDown() throws InterruptedException {
        enableDefaultBalancing();
        if (servers != null)
            servers.close();
        if (clients != null)
            clients.close();

        scheduler.shutdownNow();
        scheduler.awaitTermination(1, TimeUnit.SECONDS);
        await(TestRMILoadBalancerFactory::areClientBalancersClosed);
        await(TestRMILoadBalancerFactory::areServerBalancersClosed);
        TestRMILoadBalancerFactory.reset();
        ThreadCleanCheck.after();
    }

    void enableDefaultBalancing() {
        System.clearProperty(DEFAULT_BALANCING_DISABLED);
    }

    void disableDefaultBalancing() {
        System.setProperty(DEFAULT_BALANCING_DISABLED, "true");
    }

    static void assertRoute(List<EndpointId> actualRoute, EndpointId... expectedRoute) {
        log.info("assertRoute: expectedRoute = " + Arrays.toString(expectedRoute));
        log.info("assertRoute: actualRoute = " + actualRoute);

        assertEquals(Arrays.asList(expectedRoute), actualRoute);
    }

    protected static void await(Supplier<Boolean> test) throws InterruptedException {
        while (!test.get())
            Thread.sleep(1);
    }

    RMIServiceDescriptor getTargetDescriptor(RMIService<?> service, DifferentServices.CalculatorService impl) {
        //noinspection OptionalGetWithoutIsPresent
        return service.getDescriptors().stream()
            .filter(d -> d.getServiceId().equals(impl.getDescriptors().get(0).getServiceId()))
            .findFirst()
            .get();
    }

    Promise<BalanceResult> completeAsync(BalanceResult result) {
        Promise<BalanceResult> promise = new Promise<>();
        scheduler.schedule(() -> promise.complete(result), ThreadLocalRandom.current().nextInt(200, 500),
            TimeUnit.MILLISECONDS);
        return promise;
    }

    Promise<BalanceResult> completeAsync(Exception e) {
        Promise<BalanceResult> promise = new Promise<>();
        scheduler.schedule(() -> promise.completeExceptionally(e), ThreadLocalRandom.current().nextInt(200, 500),
            TimeUnit.MILLISECONDS);
        return promise;
    }

    RMIRequest<Double> createSumRequest() {
        return clients.clients[0].getClient().createRequest(null,
            DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
    }

    static void assertRejected(RMIRequest<?> request, String expectedReason) {
        assertRejected(request, expectedReason, null);
    }

    static void assertRejected(RMIRequest<?> request, String expectedReason,
        Class<? extends Throwable> expectedCauseClass)
    {
        try {
            request.getBlocking();
            fail("Request should have failed");
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.SERVICE_UNAVAILABLE, e.getType());
            assertNotNull(e.getCause());
            if (expectedCauseClass != null)
                assertEquals(expectedCauseClass, e.getCause().getClass());
            assertEquals(expectedReason, e.getCause().getMessage());
        }
    }

    @Nonnull
    AtomicReference<Promise<BalanceResult>> captureClientOrMuxBalancePromise() {
        AtomicReference<Promise<BalanceResult>> pendingPromise = new AtomicReference<>();
        TestRMILoadBalancerFactory.clientOrMuxBalancerBehavior = req -> {
            Promise<BalanceResult> promise = new Promise<>();
            pendingPromise.set(promise);
            return promise;
        };
        return pendingPromise;
    }

    @Nonnull
    AtomicReference<Promise<BalanceResult>> captureServerSideBalancePromise() {
        AtomicReference<Promise<BalanceResult>> pendingServerPromise = new AtomicReference<>();
        TestRMILoadBalancerFactory.serverBalancerBehavior = req -> {
            Promise<BalanceResult> promise = new Promise<>();
            pendingServerPromise.set(promise);
            return promise;
        };
        return pendingServerPromise;
    }

    static class RMITestException extends RuntimeException {}
}
