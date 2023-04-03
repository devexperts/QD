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
package com.devexperts.rmi.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.samplecert.SampleCert;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIExecutionTask;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.test.RMICommonTest.InfiniteLooper;
import com.devexperts.rmi.test.RMICommonTest.ServerDisconnectingInfiniteLooper;
import com.devexperts.rmi.test.RMICommonTest.Summator;
import com.devexperts.rmi.test.RMICommonTest.SummatorImpl;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class RMIFunctionalityTest {

    private static final Logging log = Logging.getLogging(RMIFunctionalityTest.class);
    private static final int MAX_CONCURRENT_MESSAGES = 6;

    private RMIEndpoint server;
    private RMIEndpoint client;

    // to shutdown after test
    private final List<ExecutorService> executorServices = new ArrayList<>();

    private final ChannelLogic channelLogic;
    private final InitFunction initPortForOneWaySanding;

    @Parameterized.Parameters(name = "type={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            {TestType.REGULAR},
            {TestType.CLIENT_CHANNEL},
            {TestType.SERVER_CHANNEL}
        });
    }

    public RMIFunctionalityTest(TestType type) {
        server = RMIEndpoint.newBuilder()
            .withName("Server")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        client = RMIEndpoint.newBuilder()
            .withName("Client")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        client.getClient().setRequestRunningTimeout(20000); // to make sure tests don't run forever
        channelLogic = new ChannelLogic(type, client, server, null);
        switch (type) {
            case REGULAR:
                initPortForOneWaySanding = () ->
                    channelLogic.clientPort =
                        client.getClient().getPort(Subject.getSubject(AccessController.getContext()));
                break;
            case CLIENT_CHANNEL:
                initPortForOneWaySanding = () -> {
                    channelLogic.request = client.getClient().getPort(Subject.getSubject(AccessController.getContext()))
                        .createRequest(new RMIRequestMessage<>(RMIRequestType.DEFAULT, TestService.OPERATION));
                    channelLogic.clientPort = channelLogic.request.getChannel();
                    channelLogic.request.send();
                    channelLogic.initServerPort();
                };
                break;
            case SERVER_CHANNEL:
            default:
                initPortForOneWaySanding = () -> {
                    channelLogic.initServerPort();
                    channelLogic.clientPort = channelLogic.testService.awaitChannel();
                };
                break;
        }
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        RMICommonTest.finish = false;
    }

    @After
    public void tearDown() {
        RMICommonTest.finish = true;
        if (client != null)
            client.close();
        if (server != null)
            server.close();
        executorServices.forEach(ExecutorService::shutdown);
        ThreadCleanCheck.after();
    }

    private void connectDefault() {
        NTU.connectPair(server, client);
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    private void connectWith(String with) {
        int port = NTU.connectServer(server, with + (with.equalsIgnoreCase("tls") ? "[isServer=true]+" : "+"));
        NTU.connect(client, with + "+" + NTU.localHost(port));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    // --------------------------------------------------

    private void implTestSummator(int scale) {
        Summator summator = channelLogic.clientPort.getProxy(Summator.class, "summator");
        Random rnd = new Random(123514623655723586L);
        final int n = 100;
        int i = 0;
        try {
            for (; i < n; i++) {
                int a = rnd.nextInt();
                int b = rnd.nextInt();
                int c = a + b;
                long time = System.currentTimeMillis();
                int summatorSum = summator.sum(a, b);
                if (System.currentTimeMillis() - time > 1000)
                    log.info("i = " + i + "; very loooooong");
                assertEquals(c, summatorSum);
            }
            assertEquals(summator.getOperationsCount(), n * scale);
        } catch (RMIException e) {
            fail(e.getMessage());
        } catch (Exception e) {
            fail("i = " + i + "; " + e.getMessage());
        }
    }

    @Test
    public void testSummator() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
            channelLogic);
        connectDefault();
        implTestSummator(1);
    }

    @Test
    public void testWithTLSAndCustomTrustManager() {
        Properties props = System.getProperties();
        SampleCert.init();
        final boolean[] checked = new boolean[1];
        client.setTrustManager(new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                checked[0] = true;
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        });
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
            channelLogic);

        connectWith("tls");

        implTestSummator(1);
        client.disconnect();
        client.setTrustManager(null);
        assertTrue(checked[0]);
        System.setProperties(props);
    }

    @Test
    public void testWithTLSDefaultSettings() {
        Properties props = System.getProperties();
        try {
            SampleCert.init();
            NTU.exportServices(server.getServer(),
                new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
                channelLogic);
            //
            int port = NTU.connectServer(server, "tls[isServer]+");
            NTU.connect(client, NTU.localHost(port) + "[tls=true]");
            try {
                channelLogic.initPorts();
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            implTestSummator(1);
            server.disconnect();
            client.disconnect();
        } finally {
            System.setProperties(props);
        }
    }

    @Test
    public void testWithLowTLSversion() {
        assumeTLSv12v13supported();
        Properties props = System.getProperties();
        try {
            SampleCert.init();
            NTU.exportServices(server.getServer(),
                new RMIServiceImplementation<>(new SummatorImpl(), Summator.class,
                    "summator"), channelLogic);
            System.getProperties().setProperty("com.devexperts.connector.codec.ssl.protocols", "TLSv1.2");
            int port = NTU.connectServer(server, "tls[isServer,protocols=TLSv1.3;TLSv1.2]+");
            NTU.connect(client, "tls+" + NTU.localHost(port));
            try {
                channelLogic.initPorts();
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            implTestSummator(1);
            server.disconnect();
            client.disconnect();
        } finally {
            System.setProperties(props);
        }
    }

    @Test
    public void testWithTLSVersionsMismatch() throws InterruptedException {
        if (channelLogic.type != TestType.REGULAR)
            return;
        assumeTLSv12v13supported();
        Properties props = System.getProperties();
        try {
            SampleCert.init();
            System.setProperty("com.devexperts.connector.codec.ssl.protocols", "TLSv1.2");
            CountDownLatch connectedVersion = new CountDownLatch(2);
            CountDownLatch notConnectedVersion = new CountDownLatch(2);
            client.addEndpointListener(endpoint ->
                (endpoint.isConnected() ? connectedVersion : notConnectedVersion).countDown());
            server.addEndpointListener(endpoint ->
                (endpoint.isConnected() ? connectedVersion : notConnectedVersion).countDown());
            NTU.exportServices(server.getServer(),
                new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
                channelLogic);
            client.getClient().setRequestSendingTimeout(1000);
            int port = NTU.connectServer(server, "tls[isServer,protocols=TLSv1.3]+");
            NTU.connect(client, "tls+" + NTU.localHost(port));
            try {
                channelLogic.initPorts();
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            assertTrue(connectedVersion.await(10, TimeUnit.SECONDS));
            Summator summator =
                channelLogic.clientPort.getProxy(Summator.class, "summator");
            Random rnd = new Random(123514623655723586L);
            try {
                int a = rnd.nextInt();
                int b = rnd.nextInt();
                summator.sum(a, b);
                fail();
            } catch (RMIException e) {
                assertEquals(RMIExceptionType.REQUEST_SENDING_TIMEOUT, e.getType());
            } catch (Exception e) {
                fail();
            }
            assertTrue(notConnectedVersion.await(10, TimeUnit.SECONDS));
            server.disconnect();
            client.disconnect();
        } finally {
            System.setProperties(props);
        }
    }

    private static void assumeTLSv12v13supported() {
        try {
            Set<String> protocols =
                new HashSet<>(Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getProtocols()));
            assumeTrue("TLSv1.3 & TLSv1.2 supported", protocols.contains("TLSv1.3") && protocols.contains("TLSv1.2"));
        } catch (NoSuchAlgorithmException e) {
            assumeNoException("No default SSLContext", e);
        }
    }


    @Test
    public void testWithSSL() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
            channelLogic);
        int port = NTU.connectServer(server, "ssl[isServer=true," + SampleCert.KEY_STORE_CONFIG + "]+");
        NTU.connect(client, "ssl[" + SampleCert.TRUST_STORE_CONFIG + "]+" + NTU.localHost(port));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        implTestSummator(1);
    }

    @Test
    public void testWithZLIB() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
            channelLogic);
        connectWith("zlib");
        implTestSummator(1);
    }

    @Test
    public void testWithXOR() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"),
            channelLogic);
        connectWith("xor");
        implTestSummator(1);
    }

    // --------------------------------------------------

    @Test
    public void testServerDisconnect() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new ServerDisconnectingInfiniteLooper(server), InfiniteLooper.class),
            channelLogic);
        connectDefault();
        InfiniteLooper looper = channelLogic.clientPort.getProxy(InfiniteLooper.class);
        try {
            looper.loop();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.DISCONNECTION) {
                fail(e.getMessage());
            }
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    // --------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void testCancelOrAbort() {
        log.info(" ---- testCancelOrAbort ---- ");
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SimpleInfiniteLooper(),
            InfiniteLooper.class), channelLogic);
        channelLogic.clientPort = client.getClient().getPort(Subject.getSubject(AccessController.getContext()));
        connectDefault();
        final int n = 1000;
        RMIRequest<Void>[] requests = (RMIRequest<Void>[]) new RMIRequest[n];
        RMIOperation<Void> operation = null;
        try {
            operation = RMIOperation
                .valueOf(InfiniteLooper.class, InfiniteLooper.class.getMethod("loop"));
        } catch (NoSuchMethodException e) {
            fail(e.getMessage());
        }
        for (int i = 0; i < n; i++) {
            requests[i] = channelLogic.clientPort.createRequest(operation);
        }
        for (int i = n - 1; i >= 0; i--) {
            requests[i].send();
        }
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                requests[i].cancelWithConfirmation();
            }
            requests[i].cancelOrAbort();
        }

        int cancelled = 0;
        for (int i = 0; i < n; i++) {
            try {
                requests[i].getBlocking();
            } catch (RMIException e) {
                switch (e.getType()) {
                    case CANCELLED_BEFORE_EXECUTION:
                        cancelled++;
                        // ok;
                        continue;
                    case CANCELLED_DURING_EXECUTION:
                        // ok;
                        continue;
                    default:
                        i = n - 1;
                        fail(e.getType() + " " + e.getMessage());
                }
            } finally {
                if (i == n - 1) {
                    log.info("Cancelled requests: " + cancelled);
                    log.info("Aborted requests: " + (n - cancelled));
                    log.info("Total: " + n);
                }
            }
            fail();
        }
        log.info("Cancelled requests: " + cancelled);
        log.info("Aborted requests: " + (n - cancelled));
        log.info("Total: " + n);
        server.disconnect();
        client.disconnect();
    }

    // --------------------------------------------------

    public static class CompletingPing implements RMICommonTest.Ping {
        final Queue<WeakReference<RMITask>> rmiTasks = new ConcurrentLinkedQueue<>();
        boolean done = false;

        @Override
        public synchronized void ping() {
            RMITask task = RMITask.current();
            if (task != null)
                rmiTasks.add(new WeakReference<>(task));
            done = true;
            notifyAll();
        }

        public synchronized void waitForCompletion() {
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
            }
        }
    }

    @Test
    public void testOneWaySending() throws InterruptedException {
        CompletingPing impl = new CompletingPing();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, RMICommonTest.Ping.class),
            channelLogic);
        NTU.connectPair(server, client);
        initPortForOneWaySanding.apply();
        RMIOperation<Void> operation;
        try {
            operation = RMIOperation.valueOf(RMICommonTest.Ping.class, RMICommonTest.Ping.class.getMethod("ping"));
        } catch (NoSuchMethodException e) {
            fail(e.getMessage());
            return;
        }
        RMIRequest<Void> request = channelLogic.clientPort.createRequest(operation);
        request.send();
        impl.waitForCompletion();
    }

    // --------------------------------------------------

    private static final int LARGE_SIZE = 100_000;
    private static final int SMALL_SIZE = 100;

    interface LargeRequestProcessor {
        public int process(String reqId, byte[] data) throws RMIException;
    }

    @Test
    public void testLargeRequests() throws Exception {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(
                (reqId, data) -> {
                    log.info("processing request " + reqId + ", size = " + data.length);
                    return Arrays.hashCode(data);
                },
                LargeRequestProcessor.class),
            channelLogic);

        int port = NTU.connectServer(server, "shaped[outLimit=" + LARGE_SIZE + "]+");
        NTU.connect(client, "shaped[outLimit=" + LARGE_SIZE + "]+" + NTU.localHost(port));
        setSingleThreadExecutorForLargeMethods();
        channelLogic.initPorts();

        RMIOperation<Integer> processOp =
            RMIOperation.valueOf(LargeRequestProcessor.class,
                LargeRequestProcessor.class.getMethod("process", String.class, byte[].class));

        byte[] smallData = new byte[SMALL_SIZE];
        byte[] largeData = new byte[LARGE_SIZE];
        Random rnd = new Random();

        // pass a test request to ensure that all initial procedures related to service were performed
        rnd.nextBytes(smallData);
        RMIRequest<Integer> testReq = channelLogic.clientPort.createRequest(processOp, "test", smallData.clone());
        testReq.send();
        testReq.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        // send large request first and wait it is started sending
        rnd.nextBytes(largeData);
        RMIRequest<Integer> largeReq = channelLogic.clientPort.createRequest(processOp, "large", largeData.clone());
        largeReq.send();
        assertTrue(NTU.waitCondition(10_000, 10, () -> {
            RMIRequestState state = largeReq.getState();
            return state != RMIRequestState.NEW && state != RMIRequestState.WAITING_TO_SEND;
        }));

        rnd.nextBytes(smallData);
        RMIRequest<Integer> smallReq = channelLogic.clientPort.createRequest(processOp, "small", smallData.clone());
        smallReq.send();
        smallReq.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.hashCode(smallData), (int) smallReq.getBlocking());
        assertEquals(RMIRequestState.SENDING, largeReq.getState());

        largeReq.cancelOrAbort();
    }

    // --------------------------------------------------

    @SuppressWarnings("unused")
    public static interface LargeResultGenerator {
        public byte[] getResult(boolean isLarge);
    }

    @Test
    public void testLargeResponses() throws Exception {
        CountDownLatch processingStarted = new CountDownLatch(1);
        Random rnd = new Random();

        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(
                isLarge -> {
                    processingStarted.countDown();
                    String s = isLarge ? "large" : "small";
                    log.info("generating " + s + " response ...");
                    RMITask.current().setCancelListener(
                        task -> log.info(s + " response completed! [" + task.getState() + "]"));
                    byte[] result = new byte[isLarge ? LARGE_SIZE : SMALL_SIZE];
                    rnd.nextBytes(result);
                    return result;
                },
                LargeResultGenerator.class), channelLogic);

        int port = NTU.connectServer(server, "shaped[outLimit=" + LARGE_SIZE + "]+");
        NTU.connect(client, "shaped[outLimit=" + LARGE_SIZE + "]+" + NTU.localHost(port));
        setSingleThreadExecutorForLargeMethods();
        channelLogic.initPorts();

        RMIOperation<byte[]> getResOp = RMIOperation.valueOf(LargeResultGenerator.class,
            LargeResultGenerator.class.getMethod("getResult", boolean.class));

        RMIRequest<byte[]> largeRequest = channelLogic.clientPort.createRequest(getResOp, true);
        largeRequest.send();

        assertTrue(processingStarted.await(10_000, TimeUnit.MILLISECONDS));

        RMIRequest<byte[]> smallRequest = channelLogic.clientPort.createRequest(getResOp, false);
        smallRequest.send();
        smallRequest.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        assertFalse("Received large response before small", largeRequest.isCompleted());

        largeRequest.cancelOrAbort();
    }

    // --------------------------------------------------

    @Test
    public void testLargeRequestCancellations() throws Exception {
        log.info(" ---- testLargeRequestCancellations ---- ");
        CountDownLatch processingStarted = new CountDownLatch(1);
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(
                (reqId, data) -> {
                    log.info("processing request " + reqId + " ...");
                    processingStarted.countDown();
                    if (data.length >= LARGE_SIZE) // make sure we have time to cancel request
                        NTU.waitCondition(5_000, 10, () -> RMITask.current().getState().isCompletedOrCancelling());
                    return Arrays.hashCode(data);
                }, LargeRequestProcessor.class),
            channelLogic);

        int speedLimit = LARGE_SIZE * MAX_CONCURRENT_MESSAGES;
        int port = NTU.connectServer(server, "shaped[outLimit=" + speedLimit + "]+");
        NTU.connect(client, "shaped[outLimit=" + speedLimit + "]+" + NTU.localHost(port));
        setSingleThreadExecutorForLargeMethods();
        channelLogic.initPorts();
        RMIOperation<Integer> processOp = RMIOperation.valueOf(LargeRequestProcessor.class,
            LargeRequestProcessor.class.getMethod("process", String.class, byte[].class));

        byte[] largeData = new byte[LARGE_SIZE];
        ArrayList<RMIRequest<Integer>> requests = new ArrayList<>();

        Random rnd = new Random();
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++) {
            rnd.nextBytes(largeData);
            RMIRequest<Integer> request =
                channelLogic.clientPort.createRequest(processOp, "large-" + i, largeData.clone());
            request.send();
            requests.add(request);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }

        // wait a first large request arrived to server
        assertTrue(processingStarted.await(10_000, TimeUnit.MILLISECONDS));

        // cancel all requests once they are sent
        ArrayList<RMIRequest<Integer>> requestsToCancel = new ArrayList<>(requests);
        assertTrue(NTU.waitCondition(10_000, 10, () -> {
            for (Iterator<RMIRequest<Integer>> it = requestsToCancel.iterator(); it.hasNext(); ) {
                RMIRequest<Integer> request = it.next();
                RMIRequestState state = request.getState();
                if (state == RMIRequestState.SENT || state == RMIRequestState.SENDING) {
                    log.info("Cancelling " + request.getParameters()[0]);
                    request.cancelWithConfirmation();
                    it.remove();
                } else if (state != RMIRequestState.WAITING_TO_SEND) {
                    fail("Unexpected state " + state + " for request " + request.getParameters()[0]);
                }
            }
            return requestsToCancel.isEmpty();
        }));

        for (RMIRequest<Integer> request : requests) {
            try {
                request.getBlocking();
                fail("Request " + request.getParameters()[0] + " succeeded");
            } catch (RMIException e) {
                log.info("Cancelled " + request.getParameters()[0] + ": " + e.getType());
                if (e.getType() != RMIExceptionType.CANCELLED_BEFORE_EXECUTION &&
                    e.getType() != RMIExceptionType.CANCELLED_DURING_EXECUTION)
                {
                    System.err.println("=== Unexpected exception ===");
                    e.printStackTrace(System.err);
                    fail("TYPE = " + e.getType());
                }
            }
        }
    }

    private ExecutorService newTestLocalExecutor(int size, final String name) {
        ExecutorService executorService = Executors.newFixedThreadPool(size, r -> new Thread(r, name));
        executorServices.add(executorService);
        return executorService;
    }

    private void setSingleThreadExecutorForLargeMethods() {
        if (channelLogic.type != TestType.SERVER_CHANNEL) {
            server.getServer().setDefaultExecutor(newTestLocalExecutor(1, "server-large-single"));
            client.getClient().setDefaultExecutor(newTestLocalExecutor(8, "client-large-pool"));
        } else {
            server.getServer().setDefaultExecutor(newTestLocalExecutor(8, "server-large-pool"));
            client.getClient().setDefaultExecutor(newTestLocalExecutor(1, "client-large-single"));
        }

    }

    // --------------------------------------------------

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WaitNotInLoop"})
    @Test
    public void testImplementationOperation() {
        log.info("testImplementationOperation()");
        Family implFed = new Fedorovi();
        Family implIva = new House().getFamily();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new Sam(), Human.class, "Person"),
            channelLogic);
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(implFed, Family.class, "Fed"),
            channelLogic);
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(implIva, Family.class, "Iva"),
            channelLogic);

        connectDefault();

        Human person = channelLogic.clientPort.getProxy(Human.class, "Person");
        Family fed = channelLogic.clientPort.getProxy(Family.class, "Fed");
        Family iva = channelLogic.clientPort.getProxy(Family.class, "Iva");

        assertEquals(person.getName(), "Sam");
        assertEquals(person.hashCode(), "Sam".hashCode());
        assertNotEquals("my name is Sam", person.toString());
        assertEquals(person.toString("test"), "test my name is Sam");


        assertEquals(fed.children(), 2);
        assertEquals(fed.childrenName()[0], "Alina");

        assertEquals(iva.children(), 0);
        assertNull(iva.childrenName());

        synchronized (iva) {
            try {
                iva.wait(100);
            } catch (Throwable e) {
                fail(e.getMessage());
            }
        }
    }

    private interface Human {
        String getName();

        public int hashCode();

        public boolean equals(Object obj);

        public String toString(String str);
    }

    public interface Family {
        public int children();

        public String[] childrenName();

    }

    @SuppressWarnings("EqualsAndHashcode")
    public static class Sam implements Human, Serializable {
        private static final long serialVersionUID = -0L;
        String name = "Sam";

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return "my name is Sam";
        }

        @Override
        public String toString(String str) {
            return str + " " + toString();
        }
    }

    @SuppressWarnings("EqualsAndHashcode")
    private static class Fedorovi implements Family {

        Human[] children = {new Human() {
            @Override
            public String getName() {
                return "Alina";
            }

            @Override
            public int hashCode() {
                return "Alina".hashCode();
            }

            @Override
            public String toString(String str) {
                return str + " " + toString();
            }
        }, new Human() {
            @Override
            public String getName() {
                return "Rita";
            }

            @Override
            public int hashCode() {
                return "Rita".hashCode();
            }

            @Override
            public String toString(String str) {
                return str + " " + toString();
            }
        }};

        @Override
        public synchronized int children() {
            return children.length;
        }

        @Override
        public String[] childrenName() {
            return new String[] {children[0].getName(), children[1].getName()};
        }
    }

    public static class House {

        @SuppressWarnings("unused")
        private static class Ivanovi implements Family {

            @Override
            public synchronized int children() {
                return 0;
            }

            public String[] parentsName() {
                return new String[] {"Ivan", "Anna"};
            }

            @Override
            public String[] childrenName() {
                return null;
            }
        }

        public Family getFamily() {
            return new House.Ivanovi();
        }
    }

    // --------------------------------------------------
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testCancelExecutionTaskBeforeRunning() {
        Executor executor = command -> {
            if (command instanceof RMIExecutionTask &&
                !((RMIExecutionTask) command).getTask().getOperation().equals(TestService.OPERATION))
                ((RMIExecutionTask) command).getTask().complete(50.0);
            command.run();
        };
        client.getClient().setDefaultExecutor(executor);
        server.getServer().setDefaultExecutor(executor);
        NTU.exportServices(server.getServer(), DifferentServices.CALCULATOR_SERVICE, channelLogic);
        connectDefault();
        server.getServer().setDefaultExecutor(executor);
        @SuppressWarnings("unchecked")
        RMIRequest<Double> request =
            channelLogic.clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 25.36, 24.2);
        request.send();
        try {
            assertEquals(request.getBlocking(), (Double) 50.0);
        } catch (RMIException e) {
            fail();
        }
    }
}
