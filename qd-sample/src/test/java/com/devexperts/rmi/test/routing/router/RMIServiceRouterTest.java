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
package com.devexperts.rmi.test.routing.router;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.rmi.impl.ServiceRouter;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(TraceRunner.class)
public class RMIServiceRouterTest {

    private RMIServiceId serviceId = RMIServiceId.newServiceId("test");
    private ServiceRouter<RMIService<?>> serviceRouter =
        ServiceRouter.createRouter(EndpointId.newEndpointId("server"), serviceId);

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() {
        ThreadCleanCheck.after();
    }

    @Test
    public void testMinDistance() {
        RMIServiceDescriptor first = RMIServiceDescriptor.createDescriptor(serviceId, 10, null, null);
        RMIServiceDescriptor second = RMIServiceDescriptor.createDescriptor(serviceId, 20, null, null);
        RMIServiceDescriptor third = RMIServiceDescriptor.createDescriptor(serviceId, 5, null, null);
        RMIServiceDescriptor fourth = RMIServiceDescriptor.createDescriptor(serviceId, 15, null, null);

        RMIService<Object> firstService = new RMIService<Object>("first") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> secondService = new RMIService<Object>("second") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> thirdService = new RMIService<Object>("third") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> fourthService = new RMIService<Object>("fourth") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };

        serviceRouter.updateDescriptor(first, first.getDistance(), firstService);
        checkFirst(first, firstService);

        serviceRouter.updateDescriptor(second, second.getDistance(), secondService);
        checkFirst(first, firstService);

        serviceRouter.updateDescriptor(third, third.getDistance(), thirdService);
        checkFirst(third, thirdService);

        serviceRouter.updateDescriptor(fourth, fourth.getDistance(), fourthService);
        checkFirst(third, thirdService);
    }

    @Test
    public void testRemove() {
        RMIServiceDescriptor first = RMIServiceDescriptor.createDescriptor(serviceId, 10, null, null);
        RMIServiceDescriptor second = RMIServiceDescriptor.createDescriptor(serviceId, 20, null, null);
        RMIServiceDescriptor third = RMIServiceDescriptor.createDescriptor(serviceId, 5, null, null);

        RMIService<Object> firstService = new RMIService<Object>("first") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> secondService = new RMIService<Object>("second") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> thirdService = new RMIService<Object>("third") {
            @Override
            public void processTask(RMITask<Object> task) {
            }
        };


        serviceRouter.updateDescriptor(first, first.getDistance(), firstService);
        checkFirst(first, firstService);

        serviceRouter.updateDescriptor(third, third.getDistance(), thirdService);
        checkFirst(third, thirdService);

        serviceRouter.updateDescriptor(second, second.getDistance(), secondService);
        checkFirst(third, thirdService);

        serviceRouter.removeDescriptor(third, thirdService);
        checkFirst(first, firstService);


        serviceRouter.removeDescriptor(second, secondService);
        checkFirst(first, firstService);

        serviceRouter.removeDescriptor(first, firstService);
        checkFirst(null, firstService);

        serviceRouter.updateDescriptor(second, second.getDistance(), secondService);
        checkFirst(second, secondService);
    }

    @Test
    public void testUpdateOneRef() {
        RMIServiceDescriptor first = RMIServiceDescriptor.createDescriptor(serviceId, 10, null, null);
        RMIServiceDescriptor second = RMIServiceDescriptor.createDescriptor(serviceId, 20, null, null);


        RMIService<Object> firstService = new RMIService<Object>("first") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };
        RMIService<Object> secondService = new RMIService<Object>("second") {
            @Override
            public void processTask(RMITask<Object> task) {

            }
        };

        serviceRouter.updateDescriptor(first, first.getDistance(), firstService);
        serviceRouter.updateDescriptor(second, second.getDistance(), secondService);
        checkFirst(first, firstService);

        first = RMIServiceDescriptor.createDescriptor(serviceId, 25, null, null);
        serviceRouter.updateDescriptor(first, first.getDistance(), firstService);
        checkFirst(second, secondService);



        first = RMIServiceDescriptor.createDescriptor(serviceId, 10, null, null);
        serviceRouter.updateDescriptor(first, first.getDistance(), firstService);
        checkFirst(first, firstService);
    }

    private void checkFirst(RMIServiceDescriptor entity, RMIService<?> service) {
        assertEquals(entity, serviceRouter.pickFirstDescriptor());
        if (entity != null)
            assertEquals(service, serviceRouter.pickRandom());
    }
}
