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
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

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
        System.out.println(".");
        RMIRequestMessage<?> message;
        for (int i = 0; i < countClients; i++) {
            message = new RMIRequestMessage<>(RMIRequestType.DEFAULT,
                (RMIOperation<?>) DifferentServices.CalculatorService.PLUS,
                Marshalled.forObject(new Object[] {1, 2},
                    DifferentServices.CalculatorService.PLUS.getParametersMarshaller()),
                RMIRoute.createRMIRoute(EndpointId.newEndpointId("RMI")), null);
            Promise<BalanceResult> decision = loadBalancer.balance(message);
            assertNotNull(decision.await().getTarget());
            hits.put(decision.getResult().getTarget(), hits.get(decision.getResult().getTarget()) + 1);
        }
        System.out.println(hits.values());

        for (Integer hit : hits.values()) {
            assertTrue("hit = " + hit, hit < countClients / countServices * 2);
        }
    }

    @Test
    public void testEmptyLoadBalancer() {
        RMIRequestMessage<?> message;
        RMIServiceId serviceId1 = RMIServiceId.newServiceId(SERVICE_NAME);
        RMIServiceId serviceId2 = RMIServiceId.newServiceId(SERVICE_NAME);
        message = new RMIRequestMessage<>(RMIRequestType.DEFAULT,
            (RMIOperation<?>) DifferentServices.CalculatorService.PLUS,
            Marshalled.forObject(null),
            Marshalled.forObject(new Object[] {1, 2},
                DifferentServices.CalculatorService.PLUS.getParametersMarshaller()),
            RMIRoute.createRMIRoute(EndpointId.newEndpointId("RMI")));

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
}
