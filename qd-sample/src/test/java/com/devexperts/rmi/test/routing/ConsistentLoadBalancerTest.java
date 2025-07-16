/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test.routing;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.io.Marshalled;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.ConsistentLoadBalancer;
import com.devexperts.rmi.task.RMILoadBalancer;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class ConsistentLoadBalancerTest {
    private static final String SERVICE_NAME = "test";
    private final RMILoadBalancer loadBalancer = new ConsistentLoadBalancer();

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() {
        ThreadCleanCheck.after();
    }

    @Test
    public void testUniformBalancing() {
        Map<RMIServiceId, Integer> hits = new HashMap<>();
        int countServices = 10;
        int countClients = 100000;
        for (int i = 0; i < countServices; i++) {
            RMIServiceId serviceId = RMIServiceId.newServiceId(SERVICE_NAME);
            hits.put(serviceId, 0);
            loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId, i, null, null));
        }
        for (int i = 0; i < countClients; i++) {
            Promise<BalanceResult> decision = loadBalancer.balance(createTestMessage());
            assertNotNull(decision.await().getTarget());
            hits.put(decision.getResult().getTarget(), hits.get(decision.getResult().getTarget()) + 1);
        }
        // System.out.println(hits.values());

        for (Integer hit : hits.values()) {
            assertTrue("hit = " + hit, hit < countClients / countServices * 2);
        }
    }

    @Test
    public void testStabilityBalancing() {
        int countServices = 10;
        int countClients = 100;
        List<RMIServiceDescriptor> descriptors = new ArrayList<>();
        Map<String, String> props = Collections.singletonMap(RMIServiceDescriptor.SERVICE_CAPACITY_PROPERTY, "10");
        for (int i = 0; i < countServices; i++) {
            RMIServiceId serviceId = RMIServiceId.newServiceId(SERVICE_NAME);
            RMIServiceDescriptor descriptor = RMIServiceDescriptor.createDescriptor(serviceId, 0, null, props);
            loadBalancer.updateServiceDescriptor(descriptor);
            descriptors.add(descriptor);
        }
        List<RMIRequestMessage<?>> messages =
            IntStream.range(0, countClients).mapToObj(i -> createTestMessage()).collect(Collectors.toList());

        List<RMIServiceId> targets = balance(messages);
        // clear all
        descriptors.forEach(d -> loadBalancer.updateServiceDescriptor(d.toUnavailableDescriptor()));
        // register again
        descriptors.forEach(loadBalancer::updateServiceDescriptor);
        List<RMIServiceId> newTargets = balance(messages);
        assertEquals(targets, newTargets);
    }

    private List<RMIServiceId> balance(List<RMIRequestMessage<?>> messages) {
        List<RMIServiceId> targets = new ArrayList<>();
        for (RMIRequestMessage<?> message : messages) {
            Promise<BalanceResult> decision = loadBalancer.balance(message);
            RMIServiceId target = decision.await().getTarget();
            assertNotNull(target);
            targets.add(target);
        }
        return targets;
    }

    @Test
    public void testServiceDecommissioningUsingCapacity() {
        int countClients = 100;
        RMIServiceId serviceId1 = RMIServiceId.newServiceId(SERVICE_NAME);
        RMIServiceDescriptor descriptor1 = RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, null);
        RMIServiceId serviceId2 = RMIServiceId.newServiceId(SERVICE_NAME);
        RMIServiceDescriptor descriptor2 = RMIServiceDescriptor.createDescriptor(serviceId2, 10, null, null);

        loadBalancer.updateServiceDescriptor(descriptor1);
        loadBalancer.updateServiceDescriptor(descriptor2);

        RMIRequestMessage<?> message;
        List<RMIRequestMessage<?>> messages =
            IntStream.range(0, countClients).mapToObj(i -> createTestMessage()).collect(Collectors.toList());
        // expect messages assigned to both services
        assertEquals(2, balance(messages).stream().distinct().count());

        // decommission service 1
        Map<String, String> noCapacityProps =
            Collections.singletonMap(RMIServiceDescriptor.SERVICE_CAPACITY_PROPERTY, "0");
        loadBalancer.updateServiceDescriptor(
            RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, noCapacityProps));
        // all shall be routed to service 2
        balance(messages).forEach(s -> assertEquals(serviceId2, s));

        // statically routed shall still pass
        message = createTestMessage().changeTargetRoute(serviceId1, null);
        assertEquals(serviceId1, loadBalancer.balance(message).await().getTarget());
    }

    @Test
    public void testEmptyLoadBalancer() {
        RMIRequestMessage<?> message;
        RMIServiceId serviceId1 = RMIServiceId.newServiceId(SERVICE_NAME);
        RMIServiceId serviceId2 = RMIServiceId.newServiceId(SERVICE_NAME);
        message = createTestMessage();

        BalanceResult decision = loadBalancer.balance(message).await();
        assertNull(decision.getTarget());
        assertFalse(decision.isReject());

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, null));
        assertEquals(serviceId1, loadBalancer.balance(message).await().getTarget());

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId2, 10, null, null));

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId1,
            RMIService.UNAVAILABLE_METRIC, null, null));
        assertEquals(serviceId2, loadBalancer.balance(message).await().getTarget());

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, null));

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId2,
            RMIService.UNAVAILABLE_METRIC, null, null));
        assertEquals(serviceId1, loadBalancer.balance(message).await().getTarget());

        loadBalancer.updateServiceDescriptor(RMIServiceDescriptor.createDescriptor(serviceId1,
            RMIService.UNAVAILABLE_METRIC, null, null));
        assertNull(loadBalancer.balance(message).await().getTarget());
        assertFalse(decision.isReject());
    }

    @Test
    @Ignore
    public void testSecureRandomStability() {
        // It is verified that SecureRandom.getInstance("SHA1PRNG") returns the same sequence of numbers for
        // the same seed across different JVMs and different OSes (checked MacOS and Linux).
        Supplier<SecureRandom> sha1PRNG = () -> {
            try {
                return SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        };
        Random r = new Random();
        byte[] seed = new byte[20];
        for (int i = 0; i < 100; i++) {
            //r.nextBytes(seed);
            Arrays.fill(seed, (byte) i);
            SecureRandom r1 = sha1PRNG.get();
            r1.setSeed(seed);
            List<Integer> randoms = IntStream.range(0, 100).mapToObj(i1 -> r1.nextInt()).collect(Collectors.toList());
            SecureRandom r2 = sha1PRNG.get();
            r2.setSeed(seed);
            List<Integer> randoms2 = IntStream.range(0, 100).mapToObj(i1 -> r2.nextInt()).collect(Collectors.toList());
            assertEquals(randoms, randoms2);
            //System.out.println(randoms.subList(0, 5));
        }
    }

    private static RMIRequestMessage<?> createTestMessage() {
        return new RMIRequestMessage<>(RMIRequestType.DEFAULT,
            (RMIOperation<?>) DifferentServices.CalculatorService.PLUS,
            Marshalled.forObject(new Object[]{1, 2},
                DifferentServices.CalculatorService.PLUS.getParametersMarshaller()),
            RMIRoute.createRMIRoute(EndpointId.newEndpointId("RMI")), null);
    }
}
