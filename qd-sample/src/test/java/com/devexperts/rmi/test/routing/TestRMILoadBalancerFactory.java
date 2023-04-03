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

import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMILoadBalancer;
import com.devexperts.rmi.task.RMILoadBalancerFactory;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.services.ServiceProvider;
import com.dxfeed.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.junit.Assert.fail;

// Set order to be much greater than the order of the default factory
// We only enable this factory in certain tests by disabling the default one.
@ServiceProvider(order = 100000)
public class TestRMILoadBalancerFactory implements RMILoadBalancerFactory {
    private static final List<TestLoadBalancer> balancers = new CopyOnWriteArrayList<>();

    static volatile Function<RMIRequestMessage<?>, Promise<BalanceResult>> clientOrMuxBalancerBehavior = null;
    static volatile Function<RMIRequestMessage<?>, Promise<BalanceResult>> serverBalancerBehavior = null;
    // Behavior of all balancers, that can be set externally to simulate various errors
    // If clientOrMuxBalancerBehavior is set, it overrides this for client-side balancers
    // If serverBalancerBehavior is set, it overrides this for server-side balancers
    static volatile Function<RMIRequestMessage<?>, Promise<BalanceResult>> behavior = req -> null;

    static void reset() {
        balancers.clear();
        clientOrMuxBalancerBehavior = null;
        serverBalancerBehavior = null;
        behavior = req -> null;
    }

    static int balancersCount() {
        return balancers.size();
    }

    @Override
    public RMILoadBalancer createLoadBalancer(String serviceName) {
        TestLoadBalancer balancer = new TestLoadBalancer();
        balancers.add(balancer);
        return balancer;
    }

    static void assertNoServerBalancing() {
        assertNoBalancing(false);
    }

    private static void assertNoBalancing(boolean isClient) {
        List<TestLoadBalancer> didBalancing = balancers.stream()
            .filter(balancer -> balancer.isClient() == isClient)
            .filter(balancer -> balancer.balanceCount.get() > 0)
            .collect(Collectors.toList());
        if (!didBalancing.isEmpty()) {
            fail((isClient ? "Client" : "Server") + " balancers participated in balancing: " + didBalancing);
        }
    }

    static void assertBalancersClosed() {
        List<TestLoadBalancer> notClosed = balancers.stream()
            .filter(balancer -> !balancer.closed)
            .collect(Collectors.toList());
        if (!notClosed.isEmpty()) {
            fail("Some of the balancers were not closed: " + notClosed);
        }
    }

    static boolean areClientBalancersClosed() {
        return areBalancersClosed(true);
    }

    static boolean areServerBalancersClosed() {
        return areBalancersClosed(false);
    }

    private static boolean areBalancersClosed(boolean isClient) {
        List<TestLoadBalancer> notClosed = balancers.stream()
            .filter(balancer -> balancer.isClient() == isClient)
            .filter(balancer -> !balancer.closed)
            .collect(Collectors.toList());
        return notClosed.isEmpty();
    }

    private class TestLoadBalancer implements RMILoadBalancer {
        volatile boolean closed = false;
        final Map<RMIServiceId, RMIServiceDescriptor> descriptors = new ConcurrentHashMap<>();
        final AtomicInteger balanceCount = new AtomicInteger();

        @Override
        public void updateServiceDescriptor(@Nonnull RMIServiceDescriptor descriptor) {
            descriptors.put(descriptor.getServiceId(), descriptor);
        }

        @Nonnull
        @Override
        public Promise<BalanceResult> balance(@Nonnull RMIRequestMessage<?> request) {
            balanceCount.incrementAndGet();
            if (isClient() && clientOrMuxBalancerBehavior != null) {
                return clientOrMuxBalancerBehavior.apply(request);
            }
            if (!isClient() && serverBalancerBehavior != null) {
                return serverBalancerBehavior.apply(request);
            }
            return behavior.apply(request);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public String toString() {
            return "TestBalancer" + descriptors;
        }

        boolean isClient() {
            for (RMIServiceDescriptor descriptor : descriptors.values()) {
                if (descriptor.getDistance() == 0 && descriptor.getIntermediateNodes().isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
}
