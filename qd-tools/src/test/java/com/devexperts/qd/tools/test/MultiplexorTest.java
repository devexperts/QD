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
package com.devexperts.qd.tools.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.tools.Tools;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Trade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class MultiplexorTest {
    private static final Logging log = Logging.getLogging(MultiplexorTest.class);

    private final int randomPortDistributor = 10000 + (new Random().nextInt(1000) * 10) + 6; // port randomization
    private final int randomPortAgent = 10000 + (new Random().nextInt(1000) * 10) + 3; // port randomization
    private final int randomPortForward = 10000 + (new Random().nextInt(1000) * 10) + 9; // port randomization

    private Thread toolThread;
    private RMIEndpoint client;
    private RMIEndpoint server;
    private RMIEndpoint remoteServer;
    private DXPublisher publisher;
    private DXEndpoint dxClientEndpoint;

    private Quote quote = new Quote("IBM");
    private final List<RMIRequest<Integer>> requestList =
        Collections.synchronizedList(new ArrayList<>());
    private static final String SERVICE_NAME = "ManagementAccountService";
    private static final double ACCOUNT_SUM = 1000;
    private int count = 100;
    private int requestCount = 0;
    private volatile int requestCompletedCount = 0;
    private volatile boolean toolOk = true;
    private volatile int receiveQuoteCount = -1;
    private volatile int receiveRMIResponse = -1;

    private CountDownLatch readyToChange = new CountDownLatch(2);
    private CountDownLatch receivedCalculatorAdvert = new CountDownLatch(1);
    private CountDownLatch receivedAccountManagerAdvert = new CountDownLatch(1);
    private CountDownLatch muxUp = new CountDownLatch(1);
    private volatile boolean closing = false;
    private volatile boolean fail = false;
    private DXEndpoint dxServerEndpoint;

    @Test
    public void testForwarding() throws InterruptedException {
        log.info(" --- init mux --- ");
        toolThread = new Thread() {
            @Override
            public void run() {
                muxUp.countDown();
                toolOk = Tools.invoke("multiplexor",
                    ":" + randomPortDistributor, ":" + randomPortAgent,
                    "-s", "10","-R", "-F",  SERVICE_NAME, ":" + randomPortForward);
            }
        };
        toolThread.start();
        assertTrue(muxUp.await(10, TimeUnit.SECONDS));
        Thread.sleep(1000);
        initRemoteServer();
        initServer();
        initClient();
        log.info(" --- testing --- ");
        assertTrue(receivedCalculatorAdvert.await(10, TimeUnit.SECONDS));
        assertTrue(receivedAccountManagerAdvert.await(10, TimeUnit.SECONDS));
        startUpdateQuotes();
        long startTime = System.currentTimeMillis();
        while (toolOk && (requestCompletedCount != requestCount || requestCompletedCount != count + 1)) {
            Thread.sleep(10);
            if (System.currentTimeMillis() - startTime > 10000) {
                log.info("requestCount = " + requestCount + ", requestCompletedCount = " + requestCompletedCount);
                for (RMIRequest<Integer> request : requestList)
                    log.info("fail: requestState" + request.getState());
                fail();
            }
        }
        log.info(" --- Last two requests --- ");
        Calculator calc = client.getClient().getProxy(Calculator.class);
        assertEquals(server.getName() + ":" + 200, calc.mult(10, 20));
        assertEquals(server.getName() + ":" + 16, calc.plus(-1, 17));
        closing = true;
        assertFalse(fail);
    }

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        client = RMIEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.FEED)
            .withSide(RMIEndpoint.Side.CLIENT)
            .withName("Client")
            .build();
        client.getClient().setRequestSendingTimeout(30 * 1000);
        server = RMIEndpoint.newBuilder()
            .withName("Server")
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        remoteServer = RMIEndpoint.newBuilder()
            .withName("RemoteServer")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
    }

    @After
    public void tearDown() throws Exception {
        toolThread.interrupt();
        toolThread.join();
        assertTrue(toolOk);
        client.close();
        server.close();
        remoteServer.close();
        dxClientEndpoint.close();
        dxServerEndpoint.close();
        ThreadCleanCheck.after();
    }

    private void initClient() {
        log.info(" --- init client --- ");
        client.getClient().getService("*").addServiceDescriptorsListener(services -> {
            log.info("CLIENT RECEIVED ADVERTISEMENT: " + services);
            for (RMIServiceDescriptor service : services) {
                if (service.getDistance() == RMIService.UNAVAILABLE_METRIC)
                    continue;
                if (service.getServiceName().equals(SERVICE_NAME) && receivedAccountManagerAdvert.getCount() != 0) {
                    receivedAccountManagerAdvert.countDown();
                } else if (service.getServiceName().equals(Calculator.class.getName()) &&
                    receivedCalculatorAdvert.getCount() != 0)
                {
                    receivedCalculatorAdvert.countDown();
                } else {
                    if (!closing) {
                        fail();
                        fail = true;
                    }
                }
            }
        });
        dxClientEndpoint = client.getDXEndpoint();
        DXFeed feed = dxClientEndpoint.getFeed();
        DXFeedSubscription<MarketEvent> sub = feed.createSubscription(Quote.class, Trade.class);
        sub.addEventListener(events -> {
            for (final MarketEvent event : events) {
                log.info("received (" + receiveQuoteCount++ + ")= " + event);
                RMIRequest<Integer> req = null;
                if (event instanceof Quote) {
                    req = client.getClient().createRequest(null,
                        countUnits, ((Quote) event).getBidPrice(), System.currentTimeMillis());
                }
                assert req != null;
                req.setListener(request -> {
                    try {
                        assertEquals((int) (ACCOUNT_SUM / (double) request.getParameters()[0]),
                            request.getBlocking());
                    } catch (RMIException e) {
                        fail(e.getMessage());
                    }
                    log.info("At time T = " + request.getParameters()[1] + ", you could buy " +
                        request.getNonBlocking() + " units " + event.getEventSymbol() +
                        " at a price " + request.getParameters()[0]);
                    synchronized (requestList) {
                        log.info("received RMI = " + receiveRMIResponse++);
                        requestList.remove(request);
                        requestCompletedCount++;
                    }
                    readyToChange.countDown();
                });
                synchronized (requestList) {
                    requestList.add(req);
                    requestCount++;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    log.info("INTERRUPTED!");
                    fail(e.getMessage());
                }
                req.send();
                readyToChange.countDown();
            }
        });
        sub.addSymbols("IBM");

        client.connect("localhost:" + randomPortAgent);
        waitConnected(client);
    }

    private void waitConnected(RMIEndpoint rmiEndpoint) {
        Thread currentThread = Thread.currentThread();
        rmiEndpoint.addEndpointListener(e -> LockSupport.unpark(currentThread));
        while (!rmiEndpoint.isConnected())
            LockSupport.park();
    }

    private void initServer() {
        log.info(" --- init server --- ");
        server.getServer().export(new CalculatorImpl(server.getName()), Calculator.class);
        server.connect("localhost:"+randomPortDistributor);
        waitConnected(server);
        dxServerEndpoint = server.getDXEndpoint();
        publisher = dxServerEndpoint.getPublisher();
    }

    private void startUpdateQuotes() throws InterruptedException {
        log.info("Waiting for subscription");
        CountDownLatch subReceived = new CountDownLatch(1);
        publisher.getSubscription(Quote.class).addChangeListener(sub -> {
            if (sub.contains(quote.getEventSymbol()))
                subReceived.countDown();
        });
        subReceived.await(5, TimeUnit.SECONDS);
        quote.setBidPrice(100);
        log.info("Publishing " + quote);
        publisher.publishEvents(Collections.singletonList(quote));
        updateQuote();
    }

    private void updateQuote() throws InterruptedException {
        log.info("UPDATE QUOTE");
        int counter = 0;
        while (counter < count) {
            assertTrue("Failed update count = " + counter, readyToChange.await(10, TimeUnit.SECONDS));
            Random random = new Random();
            quote.setBidPrice(random.nextDouble() * 100);
            log.info("publish (" + counter + ") = " + quote);
            readyToChange = new CountDownLatch(2);
            publisher.publishEvents(Collections.singletonList(quote));
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            counter++;
        }
    }

    private void initRemoteServer() {
        log.info(" --- init remote server --- ");
        remoteServer.getServer().export(new ManagementAccountService());
        remoteServer.connect("localhost:"  + randomPortForward);
        waitConnected(remoteServer);
    }

    private RMIOperation<Integer> countUnits =
        RMIOperation.valueOf(SERVICE_NAME, int.class, "getCount", Double.class, Long.class);

    private class ManagementAccountService extends RMIService<Integer> {
        ManagementAccountService() {
            super(SERVICE_NAME);
        }

        @Override
        public void openChannel(RMITask<Integer> task) {
            task.setCancelListener(RMITask::cancel);
        }

        @Override
        public void processTask(RMITask<Integer> task) {
            log.info("processTask task = " + task);
            if (task.getOperation().equals(countUnits)) {
                double priceUnit = (Double) (task.getRequestMessage().getParameters().getObject()[0]);
                task.complete((int) (ACCOUNT_SUM / priceUnit));
            }
        }
    }

    private interface Calculator {
        String plus(int a, int b);
        String mult(int a, int b);
    }

    private static class CalculatorImpl implements Calculator {

        private final String endpointName;

        CalculatorImpl(String endpointName) {
            this.endpointName = endpointName;
        }

        @Override
        public String plus(int a, int b) {
            log.info("PLUS");
            return endpointName  + ":" + (a + b);
        }

        @Override
        public String mult(int a, int b) {
            log.info("MULT");
            return endpointName  + ":" + (a * b);
        }
    }
}
