/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.test.routing;

import java.util.HashMap;
import java.util.Map;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.io.Marshalled;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.message.*;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.*;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(TraceRunner.class)
public class RMILoadBalancerTest {
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
    public void testUniformBalancing() throws NoSuchMethodException {
        Map<RMIServiceId, Integer> hits = new HashMap<>();
        int countServices = 10;
        int countClients = 100000;
        for (int i = 0; i < countServices; i++) {
            RMIServiceId serviceId = RMIServiceId.newServiceId(SERVICE_NAME);
            hits.put(serviceId, 0);
            loadBalancer.addService(RMIServiceDescriptor.createDescriptor(serviceId, i, null, null));
        }
        System.out.println(".");
        RMIRequestMessage<?> message;
        RMIServiceId id;
        for (int i = 0; i < countClients; i++) {
            message = new RMIRequestMessage<>(RMIRequestType.DEFAULT, (RMIOperation<?>) DifferentServices.CalculatorService.PLUS,
                Marshalled.forObject(new Object[] {1, 2}, DifferentServices.CalculatorService.PLUS.getParametersMarshaller()), RMIRoute.createRMIRoute(EndpointId.newEndpointId("RMI")), null);
            id = loadBalancer.pickServiceInstance(message);
            hits.put(id, hits.get(id) + 1);
        }
        System.out.println(hits.values());

        for (Integer hit : hits.values()) {
            assertTrue("hit = " + hit, hit < countClients / countServices * 2);
        }
    }

    public void testEmptyLoadBalancer() {
        RMIRequestMessage<?> message;
        RMIServiceId serviceId1 = RMIServiceId.newServiceId(SERVICE_NAME);
        RMIServiceId serviceId2 = RMIServiceId.newServiceId(SERVICE_NAME);
        message = new RMIRequestMessage<>(RMIRequestType.DEFAULT, (RMIOperation<?>) DifferentServices.CalculatorService.PLUS, Marshalled.forObject(null),
            Marshalled.forObject(new Object[] {1, 2}, DifferentServices.CalculatorService.PLUS.getParametersMarshaller()), RMIRoute.createRMIRoute(EndpointId.newEndpointId("RMI")));

        assertTrue(loadBalancer.isEmpty());
        assertEquals(loadBalancer.pickServiceInstance(message), null);

        loadBalancer.addService(RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, null));
        assertFalse(loadBalancer.isEmpty());
        assertEquals(loadBalancer.pickServiceInstance(message), serviceId1);

        loadBalancer.addService(RMIServiceDescriptor.createDescriptor(serviceId2, 10, null, null));
        assertFalse(loadBalancer.isEmpty());

        loadBalancer.removeService(RMIServiceDescriptor.createDescriptor(serviceId1, RMIService.UNAVAILABLE_METRIC, null, null));
        assertFalse(loadBalancer.isEmpty());
        assertEquals(serviceId2, loadBalancer.pickServiceInstance(message));

        loadBalancer.addService(RMIServiceDescriptor.createDescriptor(serviceId1, 10, null, null));
        assertFalse(loadBalancer.isEmpty());

        loadBalancer.removeService(RMIServiceDescriptor.createDescriptor(serviceId2, RMIService.UNAVAILABLE_METRIC, null, null));
        assertFalse(loadBalancer.isEmpty());
        assertEquals(loadBalancer.pickServiceInstance(message), serviceId1);

        loadBalancer.removeService(RMIServiceDescriptor.createDescriptor(serviceId1, RMIService.UNAVAILABLE_METRIC, null, null));
        assertTrue(loadBalancer.isEmpty());
        assertEquals(loadBalancer.pickServiceInstance(message), null);
    }
}
