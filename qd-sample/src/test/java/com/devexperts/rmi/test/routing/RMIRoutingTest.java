/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test.routing;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.*;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.*;
import com.devexperts.rmi.test.NTU;
import com.devexperts.rmi.test.RMICommonTest;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
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
    public void testRouteInResponseMessage() throws InterruptedException {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(2);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);
        int port = 12;

        servers.export(0, DifferentServices.CALCULATOR_SERVICE);
        servers.connect(":" + NTU.port(port + 4));
        muxs.connectServers(":" + NTU.port(port), ":" + NTU.port(port + 2));
        muxs.connectClients((NTU.LOCAL_HOST + ":" + NTU.port(port + 2)), (NTU.LOCAL_HOST + ":" + NTU.port(port + 4)));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port));


//		//ResultMessage
        RMIRequest<Double> sum = clients.clients[0].getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
        sum.send();
        try {
            assertEquals(sum.getBlocking(), 3.2, 0);
        } catch (RMIException e) {
            log.info("Error result : " + sum.getResponseMessage());
            fail(e.getMessage());
        }
        assertRoute(-1, sum.getResponseMessage().getRoute(), getListEndpointId(Arrays.asList(servers.servers[0],
            muxs.servers[1], muxs.servers[0])));

        //ErrorMessage
        RMIOperation<Double> op = RMIOperation.valueOf("CalculatorService", double.class, "Oops", double.class, double.class);
        sum = clients.clients[0].getClient().createRequest(null, op, 1.1, 2.1);
        sum.send();
        try {
            sum.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.OPERATION_NOT_PROVIDED)
                fail(e.getMessage());
        }
        assertRoute(-1, sum.getResponseMessage().getRoute(), getListEndpointId(Arrays.asList(servers.servers[0],
            muxs.servers[1], muxs.servers[0])));
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
        int port = 32;
        servers.export(0, DifferentServices.CALCULATOR_SERVICE);

        log.info("---- connect ----");
        servers.connect(":" + NTU.port(port + 2));
        muxs.connectServers(":" + NTU.port(port), ":" + NTU.port(port + 1));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port));
        muxs.connectClients(NTU.LOCAL_HOST + ":" + NTU.port(port + 1), NTU.LOCAL_HOST + ":" + NTU.port(port + 2));
        log.info("---- connect ----");

        //ResultMessage
        RMIRequest<Double> sum = clients.clients[0].getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.1, 2.1);
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
            if (e.getType() != RMIExceptionType.UNKNOWN_SERVICE && e.getType() != RMIExceptionType.REQUEST_SENDING_TIMEOUT)
                fail(e.getType().toString());
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

        int port = 59;
        servers.connect(":" + NTU.port(port));
        muxs.connectServers(":" + NTU.port(port + 1), ":" + NTU.port(port + 2));
        clients.connect("(" + NTU.LOCAL_HOST + ":" + NTU.port(port + 1) + ")(" + NTU.LOCAL_HOST + ":" + NTU.port(port + 2) + ")");
        muxs.connectClients(NTU.LOCAL_HOST + ":" + NTU.port(port) + "[weight=20]", NTU.LOCAL_HOST + ":" + NTU.port(port));
        servers.export(0, new DistanceService(30));
        assertTrue(start.await(10, TimeUnit.SECONDS));
        RMIOperation<Integer> op = RMIOperation.valueOf("DistanceService", int.class, "method");
        RMIRequest<Integer> dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        int result = 0;
        try {
            result = dist.getBlocking();
            assertRoute(-1, Arrays.asList(servers.servers[0].getEndpointId(), muxs.servers[1].getEndpointId()), dist.getResponseMessage().getRoute());
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        log.info("---------------1---------------");

        dist = clients.clients[0].getClient().createRequest(null, op);
        dist.send();
        assertRequest(dist, result, Arrays.asList(servers.servers[0], muxs.servers[1]), -1);
    }

    @Test
    public void testSubjectRouting() throws InterruptedException {
        servers = new ServerRoutingSide(1);
        muxs = new MuxRoutingSide(1);
        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        RMICommonTest.SomeSubject trueSubject = new RMICommonTest.SomeSubject("true");
        RMICommonTest.SomeSubject falseSubject = new RMICommonTest.SomeSubject("false");
        servers.setSecurityController(new RMICommonTest.SomeSecurityController(trueSubject));
        int port = 49;
        servers.connect(":" + NTU.port(port));
        muxs.connectServers(":" + NTU.port(port + 1));
        client.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 1));
        muxs.connectClients(NTU.LOCAL_HOST + ":" + NTU.port(port));
        servers.export(new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"));
        client.setSecurityController(new RMICommonTest.SomeSecurityController(trueSubject));
        RMICommonTest.Summator summator = client.getClient().getProxy(RMICommonTest.Summator.class, "summator");

        try {
            assertEquals(summator.sum(256,458), 256 + 458);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        client.close();

        client = (RMIEndpointImpl) RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        client.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 1));

        log.info("_____________________");

        client.setSecurityController(new RMICommonTest.SomeSecurityController(falseSubject));
        summator = client.getClient().getProxy(RMICommonTest.Summator.class, "summator");
        try {
            summator.sum(256, 458);
            fail();
        } catch (RMIException e) {
            assertTrue(e.getType() == RMIExceptionType.SECURITY_VIOLATION);
        }
        client.close();
    }


    //----------------------------------------------------------------

    @Test
    public void testReconnect() throws InterruptedException {
        servers = new ServerRoutingSide(1);
        clients = new ClientRoutingSide(1);
        muxs = new MuxRoutingSide(1);

        log.info("servers = " + servers);
        log.info("muxs = " + muxs);
        log.info("clients = " + clients);

        int port = 62;
        servers.connect(":" + NTU.port(port));
        muxs.connectServers(":" + NTU.port(port + 1));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 1));
        muxs.connectClients(NTU.LOCAL_HOST + ":" + NTU.port(port));

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
        servers.connect(":" + NTU.port(port + 2));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 3));
        muxs.connect(new String[][]{{":" + NTU.port(port + 3)},{NTU.LOCAL_HOST + ":" + NTU.port(port + 2)}});
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
        NTU.connect(remoteServer, ":" + NTU.port(port));
        NTU.connect(muxServer, ":" + NTU.port(port + 1));
        NTU.connect(muxClientOne, NTU.LOCAL_HOST + ":" + NTU.port(port));
        NTU.connect(muxClientTwo, NTU.LOCAL_HOST + ":" + NTU.port(port));
        NTU.connect(client, NTU.LOCAL_HOST + ":" + NTU.port(port + 1));

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

        int port = 82;
        servers.export(0, new EchoRequestService());
        servers.connect(":" + NTU.port(port));
        muxs.connectServers(":" + NTU.port(port + 1), ":" + NTU.port(port + 2));
        muxs.connectClients(NTU.LOCAL_HOST + ":" + NTU.port(port + 2), NTU.LOCAL_HOST + ":" + NTU.port(port));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 1));

        RMIOperation<List> op = RMIOperation.valueOf("EchoRequestService", List.class, "method");
        RMIRequest<List> req = clients.clients[0].getClient().createRequest(null, op);
        assertRoute(-1, Collections.singletonList(clients.clients[0].getEndpointId()), req.getRequestMessage().getRoute());

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
        int port = 92;
        server.getServer().export(DifferentServices.CALCULATOR_SERVICE);
        server.getServer().export(new EchoRequestService());
        NTU.connect(server, ":" + NTU.port(port) + "[services=CalculatorService]");
        NTU.connect(client, NTU.LOCAL_HOST + ":" + NTU.port(port));
        RMIRequest<Double> sum = client.getClient().createRequest(null, DifferentServices.CalculatorService.PLUS, 1.231, 2.123);
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

        int port = 102;
        servers.connect(":" + NTU.port(port));
        muxs.connectServers(":" + NTU.port(port + 1), ":" + NTU.port(port + 2));
        clients.connect(NTU.LOCAL_HOST + ":" + NTU.port(port + 1));
        muxs.connectClients("(" + NTU.LOCAL_HOST + ":" + NTU.port(port + 2) + ")(" + NTU.LOCAL_HOST + ":" + NTU.port(port) + ")",
            NTU.LOCAL_HOST + ":" + NTU.port(port + 1));

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

    private void assertRequest(RMIRequest<?> sum, Object result, List<RMIEndpointImpl> route, int routeElements) {
        sum.send();
        try {
            assertEquals(sum.getBlocking(), result);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        assertRoute(routeElements, sum.getResponseMessage().getRoute(), getListEndpointId(route));
    }

    private List<EndpointId> getListEndpointId(List<RMIEndpointImpl> endpoints) {
        List<EndpointId> result = endpoints.stream()
            .map(RMIEndpointImpl::getEndpointId)
            .collect(Collectors.toList());
        return result;
    }

    private void assertRoute(int routeElements, List<EndpointId> expectedRoute, List<EndpointId> actualRoute) {
        int elements = routeElements == -1 ? expectedRoute.size() : Math.min(routeElements, expectedRoute.size());
        log.info("assertRoute: routeElements = " + routeElements);
        log.info("assertRoute: expectedRoute = " + expectedRoute);
        log.info("assertRoute: actualRoute = " + actualRoute);

        assertEquals(actualRoute.size(), actualRoute.size());
        for (int i = 0; i < elements; i++)
            assertEquals(expectedRoute.get(i), actualRoute.get(i));
    }
}
