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
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.test.NTU;
import com.devexperts.rmi.test.RMICommonTest;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class RMIRoutingTest {
    private static final Logging log = Logging.getLogging(RMIRoutingTest.class);

    private ServerRoutingSide servers;
    private ClientRoutingSide clients;
    private MuxRoutingSide muxs;

    private RMIEndpointImpl remoteServer;
    private RMIEndpointImpl muxClientOne;
    private RMIEndpointImpl muxClientTwo;
    private RMIEndpointImpl muxServer;
    private RMIEndpointImpl client;
    private RMIEndpointImpl server;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() {
        if (clients != null)
            clients.close();
        if (muxs != null)
            muxs.close();
        if (servers != null)
            servers.close();
        if (client != null)
            client.close();
        if (muxClientOne != null)
            muxClientOne.close();
        if (muxClientTwo != null)
            muxClientTwo.close();
        if (muxServer != null)
            muxServer.close();
        if (remoteServer != null)
            remoteServer.close();
        if (server != null)
            server.close();
        log.info(" ======================= // =====================");
        ThreadCleanCheck.after();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRouteInResponseMessage() {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(2);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        servers.export(0, DifferentServices.CALCULATOR_SERVICE);
        int serverPort = servers.connectAuto()[0];
        int[] muxPorts = muxs.connectServersAuto();
        muxs.connectClients((NTU.localHost(muxPorts[1])), (NTU.localHost(serverPort)));
        clients.connect(NTU.localHost(muxPorts[0]));


        RMIRequest<Double> sum = clients.clients[0].getClient().createRequest(
            null, DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
        sum.send();
        try {
            assertEquals(sum.getBlocking(), 3.2, 0);
        } catch (RMIException e) {
            log.info("Error result : " + sum.getResponseMessage());
            fail(e.getMessage());
        }
        assertRoute(-1, getListEndpointId(Arrays.asList(servers.servers[0],
            muxs.servers[1], muxs.servers[0])), sum.getResponseMessage().getRoute());

        //ErrorMessage
        RMIOperation<Double> op =
            RMIOperation.valueOf("CalculatorService", double.class, "Oops", double.class, double.class);
        sum = clients.clients[0].getClient().createRequest(null, op, 1.1, 2.1);
        sum.send();
        try {
            sum.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.OPERATION_NOT_PROVIDED)
                fail(e.getMessage());
        }
        assertRoute(-1, getListEndpointId(Arrays.asList(servers.servers[0],
            muxs.servers[1], muxs.servers[0])), sum.getResponseMessage().getRoute());
    }

    //----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void testBadAdvertise() throws InterruptedException {

        log.info("---- testBadAdvertise ----");
        final CountDownLatch advertiseLatch = new CountDownLatch(1);

        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(2);
        clients.clients[0].getClient().getService("*").addServiceDescriptorsListener(descriptors ->
            descriptors.stream()
                .filter(descriptor -> !descriptor.isAvailable())
                .forEach(descriptor -> advertiseLatch.countDown()));
        servers.export(0, DifferentServices.CALCULATOR_SERVICE);

        log.info("---- connect ----");
        int serverPort = servers.connectAuto()[0];
        int[] muxPorts = muxs.connectServersAuto();
        clients.connect(NTU.localHost(muxPorts[0]));
        muxs.connectClients(NTU.localHost(muxPorts[1]), NTU.localHost(serverPort));
        log.info("---- connect ----");

        //ResultMessage
        RMIRequest<Double> sum = clients.clients[0].getClient().createRequest(
            null, DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
        sum.send();
        assertRequest(sum, 3.2, Arrays.asList(servers.servers[0], muxs.servers[1], muxs.servers[0]), -1);
        log.info("-----------------------------------------------------");

        servers.disconnect();
        clients.clients[0].getClient().setRequestSendingTimeout(300);
        muxs.clients[0].getClient().setRequestSendingTimeout(300);
        sum = clients.clients[0].getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
        advertiseLatch.await(10, TimeUnit.SECONDS);
        sum.send();

        try {
            sum.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.UNKNOWN_SERVICE &&
                e.getType() != RMIExceptionType.REQUEST_SENDING_TIMEOUT)
            {
                fail(e.getType().toString());
            }
        }
    }

    //----------------------------------------------------------------

    private static class DistanceService extends RMIService<Integer> {

        private final int distance;

        private static final int ASSERT_FALSE_DISTANCE = Integer.MAX_VALUE / 2 + 1;

        protected DistanceService(int distance) {
            super("DistanceService");
            this.distance = distance;
        }

        @Override
        public void processTask(RMITask<Integer> task) {
            if (distance == ASSERT_FALSE_DISTANCE)
                fail();
            task.complete(distance);
        }

        @Override
        public boolean isAvailable() {
            return distance != 0;
        }
    }

    @Test
    public void testAnyDistanceOwnServer() throws InterruptedException {
        servers = new ServerRoutingSide(1);
        muxs = new MuxRoutingSide(2);
        clients = new ClientRoutingSide(1);
        log.info("--- testAnyDistanceOwnServer ---");
        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        final CountDownLatch start = new CountDownLatch(1);
        RMIService<?> service = clients.clients[0].getClient().getService("*");
        service.addServiceDescriptorsListener(descriptors -> {
            for (RMIServiceDescriptor descriptor : descriptors) {
                if (descriptor.getDistance() == 20) {
                    start.countDown();
                    return;
                }
            }
        });

        int serverPort = servers.connectAuto()[0];
        int [] muxPorts = muxs.connectServersAuto();
        clients.connect("(" + NTU.localHost(muxPorts[0]) + ")(" + NTU.localHost(muxPorts[1]) + ")");
        muxs.connectClients(NTU.localHost(serverPort) + "[weight=20]", NTU.localHost(serverPort));
        servers.export(0, new DistanceService(30));
        assertTrue(start.await(10, TimeUnit.SECONDS));
        RMIOperation<Integer> op = RMIOperation.valueOf("DistanceService", int.class, "method");
        RMIRequest<Integer> dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        int result = 0;
        try {
            result = dist.getBlocking();
            assertRoute(-1, Arrays.asList(servers.servers[0].getEndpointId(),
                muxs.servers[1].getEndpointId()), dist.getResponseMessage().getRoute());
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        log.info("---------------1---------------");

        dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        assertRequest(dist, result, Arrays.asList(servers.servers[0], muxs.servers[1]), -1);
    }

    @Test
    public void testSubjectRouting() {
        servers = new ServerRoutingSide(1);
        muxs = new MuxRoutingSide(1);
        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        RMICommonTest.SomeSubject trueSubject = new RMICommonTest.SomeSubject("true");
        RMICommonTest.SomeSubject falseSubject = new RMICommonTest.SomeSubject("false");
        servers.setSecurityController(new RMICommonTest.SomeSecurityController(trueSubject));

        int serverPort = servers.connectAuto()[0];
        int muxPort = muxs.connectServersAuto()[0];
        client.connect(NTU.localHost(muxPort));
        muxs.connectClients(NTU.localHost(serverPort));
        servers.export(
            new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"));
        client.setSecurityController(new RMICommonTest.SomeSecurityController(trueSubject));
        RMICommonTest.Summator summator = client.getClient().getProxy(RMICommonTest.Summator.class, "summator");

        try {
            assertEquals(summator.sum(256,458), 256 + 458);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        client.close();

        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        client.connect(NTU.localHost(muxPort));

        log.info("_____________________");

        client.setSecurityController(new RMICommonTest.SomeSecurityController(falseSubject));
        summator = client.getClient().getProxy(RMICommonTest.Summator.class, "summator");
        try {
            summator.sum(256, 458);
            fail();
        } catch (RMIException e) {
            assertSame(RMIExceptionType.SECURITY_VIOLATION, e.getType());
        }
        client.close();
    }


    //----------------------------------------------------------------

    @Test
    public void testReconnect() {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(1);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        int port = 62;
        int serverPort = servers.connectAuto()[0];
        int muxPort = muxs.connectServersAuto()[0];
        clients.connect(NTU.localHost(muxPort));
        muxs.connectClients(NTU.localHost(serverPort));

        servers.export(0, new DistanceService(30));

        RMIOperation<Integer> op = RMIOperation.valueOf("DistanceService", int.class, "method");
        RMIRequest<Integer> dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        try {
            assertEquals(dist.getBlocking(), Integer.valueOf(30));
        } catch (RMIException e) {
            fail(e.getMessage());
        }

        log.info("----------------------------------------- **** -----------------------------------------");
        clients.disconnect();
        muxs.disconnect();

        int newServerPort = servers.connectAuto()[0];
        int newMuxPort = muxs.connectServersAuto()[0];
        muxs.connectClients(NTU.localHost(newServerPort));
        clients.connect(NTU.localHost(newMuxPort));
        log.info("----------------------------------------- **** -----------------------------------------");
        dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        try {
            assertEquals(dist.getBlocking(), Integer.valueOf(30));
        } catch (RMIException e) {
            log.info("Error result : " + dist.getResponseMessage());
            fail(e.getMessage());
        }
    }

    //----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void testExportOneServiceManyTimes() throws InterruptedException {
        remoteServer = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("Server")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        muxClientOne = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("muxClient1")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        muxClientTwo = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("muxClient2")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        muxServer = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("muxServer")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        client = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("client")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();

        int port = 72;
        CountDownLatch unavailableLatch = new CountDownLatch(2);
        client.getClient().getService("*").addServiceDescriptorsListener(descriptors -> {
            descriptors.stream()
                .filter(descriptor -> descriptor.getDistance() == 20)
                .forEach(descriptor -> unavailableLatch.countDown());
            log.info("Client received descriptors: " + descriptors);
        });
        int remoteServerPort = NTU.connectServer(remoteServer);
        int muxServerPort = NTU.connectServer(muxServer);
        NTU.connect(muxClientOne, NTU.localHost(remoteServerPort));
        NTU.connect(muxClientTwo, NTU.localHost(remoteServerPort));
        NTU.connect(client, NTU.localHost(muxServerPort));

        remoteServer.getServer().export(DifferentServices.CALCULATOR_SERVICE);
        muxServer.getServer().export(muxClientOne.getClient().getService("*"));
        RMIRequest<Double> sum;
        sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.231, 2.123);
        assertRequest(sum, 3.354, Arrays.asList(remoteServer, muxServer),  -1);

        muxServer.getServer().export(muxClientTwo.getClient().getService("*"));
        sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 231d, 124d);
        assertRequest(sum, 355d, Arrays.asList(remoteServer, muxServer),  -1);

        muxClientOne.disconnect();
        log.info("------------------------------------------------------");
        sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.231, 2.125);
        assertTrue(unavailableLatch.await(10, TimeUnit.SECONDS));
        assertRequest(sum, 3.356, Arrays.asList(remoteServer, muxServer),  -1);
    }

    //----------------------------------------------------------------

    private static class EchoRequestService extends RMIService<List<String>> {

        private EchoRequestService() {
            super("EchoRequestService");
        }

        @Override
        public void processTask(RMITask<List<String>> task) {
            List<String> stringRoute = new ArrayList<>(task.getRequestMessage().getRoute().size());
            stringRoute.addAll(task.getRequestMessage().getRoute().stream()
                .map(EndpointId::toString)
                .collect(Collectors.toList()));
            task.complete(stringRoute);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testRequestRoute() {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(2);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        servers.export(0, new EchoRequestService());
        int serverPort = servers.connectAuto()[0];
        int[] muxPorts = muxs.connectServersAuto();
        muxs.connectClients(NTU.localHost(muxPorts[1]), NTU.localHost(serverPort));
        clients.connect(NTU.localHost(muxPorts[0]));

        RMIOperation<List> op = RMIOperation.valueOf("EchoRequestService", List.class, "method");
        RMIRequest<List> req = clients.clients[0].getClient().createRequest(null, op);
        assertRoute(-1, Collections.singletonList(clients.clients[0].getEndpointId()),
            req.getRequestMessage().getRoute());

        req.send();
        try {
            List<String> route = req.getBlocking();
            List<String> expectedRoute = Arrays.asList(clients.clients[0].getEndpointId().toString(),
                muxs.clients[0].getEndpointId().toString(), muxs.clients[1].getEndpointId().toString());
            for (int i = 0; i < route.size(); i++)
                assertEquals(expectedRoute.get(i), route.get(i));
        } catch (RMIException e) {
            log.info("Error result : " + req.getResponseMessage());
            fail(e.getMessage());
        }
    }

    //----------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testAdvertisedServices() {
        log.info("start");
        server = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        server.getServer().export(DifferentServices.CALCULATOR_SERVICE);
        server.getServer().export(new EchoRequestService());
        int port = NTU.connectServer(server, null, "services=CalculatorService,advertise=all");
        NTU.connect(client, NTU.localHost(port));
        RMIRequest<Double> sum = client.getClient().createRequest(
            null, DifferentServices.CalculatorService.PLUS, 1.231, 2.123);
        assertRequest(sum, 3.354, Collections.singletonList(server),  -1);
        log.info("middle");

        client.getClient().setRequestSendingTimeout(10);
        RMIOperation<List> op = RMIOperation.valueOf("EchoRequestService", List.class, "method");
        RMIRequest<List> req = client.getClient().createRequest(null, op);
        req.send();
        try {
            req.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.REQUEST_SENDING_TIMEOUT, e.getType());
        }
        log.info("finish");
    }

    //----------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testRequestLoop() throws InterruptedException {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(2);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        servers.export(0, new EchoRequestService());

        int serverPort = servers.connectAuto()[0];
        int[] muxPorts = muxs.connectServersAuto();
        clients.connect(NTU.localHost(muxPorts[0]));
        muxs.connectClients(
            "(" + NTU.localHost(muxPorts[1]) + ")(" + NTU.localHost(serverPort) + ")",
            NTU.localHost(muxPorts[0]));

        RMIOperation<List> op = RMIOperation.valueOf("EchoRequestService", List.class, "method");
        RMIRequest<List> req = clients.clients[0].getClient().createRequest(null, op);
        req.send();
        try {
            req.getBlocking();
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        log.info("--------------------------***--------------------------");
        CountDownLatch unavailableServiceLatch = new CountDownLatch(1);
        clients.clients[0].getClient().getService("EchoRequestService").addServiceDescriptorsListener(descriptors -> {
                for (RMIServiceDescriptor descriptor : descriptors) {
                    if (!descriptor.isAvailable()) {
                        unavailableServiceLatch.countDown();
                        return;
                    }
                }
            }
        );
        servers.disconnect();
        assertTrue(unavailableServiceLatch.await(10, TimeUnit.SECONDS));
    }

    //----------------------------------------------------------------

    @Test(timeout = 30000)
    public void testAdvertisementFilter_AllAdvertisement() throws InterruptedException {
        server = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        server.getServer().export(DifferentServices.CALCULATOR_SERVICE);
        CountDownLatch adReceived = new CountDownLatch(1);
        client.getClient().getService(DifferentServices.CALCULATOR_SERVICE.getServiceName())
            .addServiceDescriptorsListener(ds -> {
                System.out.println("Advertisements received: " + ds);
                adReceived.countDown();
            });
        int port = NTU.connectServer(server, null, "advertise=all");
        NTU.connect(client, NTU.localHost(port));
        RMIRequest<Double> sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS,
            1.231, 2.123);
        assertRequest(sum, 3.354, Collections.singletonList(server),  -1);
        adReceived.await();
    }

    //----------------------------------------------------------------

    @Test
    public void testAdvertisementFilter_NoAdvertisement() throws InterruptedException {
        server = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        server.getServer().export(DifferentServices.CALCULATOR_SERVICE);
        AtomicBoolean adReceived = new AtomicBoolean();
        client.getClient().getService(DifferentServices.CALCULATOR_SERVICE.getServiceName())
            .addServiceDescriptorsListener(ds -> {
                System.out.println("Advertisements received: " + ds);
                adReceived.set(true);
            });
        int port = NTU.connectServer(server, null, "advertise=none");
        NTU.connect(client, NTU.localHost(port));
        RMIRequest<Double> sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS,
            1.231, 2.123);
        assertRequest(sum, 3.354, Collections.singletonList(server),  -1);
        Thread.sleep(500);
        assertFalse(adReceived.get());
    }

    //----------------------------------------------------------------

    private void assertRequest(RMIRequest<?> sum, Object result, List<RMIEndpointImpl> route, int routeElements) {
        sum.send();
        try {
            assertEquals(sum.getBlocking(), result);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        assertRoute(routeElements, getListEndpointId(route), sum.getResponseMessage().getRoute());
    }

    private List<EndpointId> getListEndpointId(List<RMIEndpointImpl> endpoints) {
        return endpoints.stream()
            .map(RMIEndpointImpl::getEndpointId)
            .collect(Collectors.toList());
    }

    private void assertRoute(int routeElements, List<EndpointId> expectedRoute, List<EndpointId> actualRoute) {
        int elements = routeElements == -1 ? expectedRoute.size() : Math.min(routeElements, expectedRoute.size());
        log.info("assertRoute: routeElements = " + routeElements);
        log.info("assertRoute: expectedRoute = " + expectedRoute);
        log.info("assertRoute: actualRoute = " + actualRoute);

        assertEquals(elements, actualRoute.size());
        for (int i = 0; i < elements; i++)
            assertEquals(expectedRoute.get(i), actualRoute.get(i));
    }
}
