/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import com.devexperts.auth.AuthToken;
import com.devexperts.io.Marshalled;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelState;
import com.devexperts.rmi.task.RMIChannelSupport;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class RMICommonTest {
    private static final Logging log = Logging.getLogging(RMICommonTest.class);

    private RMIEndpoint server;
    private RMIEndpoint client;
    private RMIClientPort clientPort;

    private RMIEndpoint trueClient;
    private RMIEndpoint falseClient;

    static volatile boolean finish = false;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        finish = false;
    }

    @After
    public void tearDown() {
        finish = true;
        if (client != null)
            client.close();
        if (falseClient != null)
            falseClient.close();
        if (trueClient != null)
            trueClient.close();
        if (server != null)
            server.close();
        ThreadCleanCheck.after();
    }

    protected RMIEndpoint server() {
        if (server == null)
            server = RMIEndpoint.createEndpoint();
        return server;
    }

    protected RMIEndpoint client() {
        if (client == null) {
            client = RMIEndpoint.createEndpoint();
            client.getClient().setRequestRunningTimeout(20000); // to make sure tests don't run forever
        }
        return client;
    }

    private void initPorts() {
        clientPort = client().getClient().getPort(client.getSecurityController().getSubject());
    }

    private void connectDefault() {
        NTU.connectPair(server(), client());
        initPorts();
    }

    public interface Summator {
        int sum(int a, int b) throws RMIException;

        int getOperationsCount() throws RMIException;
    }

    public static class SummatorImpl implements Summator {
        private int k = 0;

        @Override
        public int sum(int a, int b) {
            k++;
            return a + b;
        }

        @Override
        public int getOperationsCount() {
            return k;
        }
    }

    //only RMIClient
    @Test
    public void testNullSubject() {
        connectDefault();
        server().getServer().export(new RMIServiceImplementation<>(new SummatorImpl(), Summator.class));
        RMIRequest<Integer> sum = client.getClient().getPort(Marshalled.NULL).createRequest(
            RMIOperation.valueOf(Summator.class, int.class, "sum", int.class, int.class), 25, 48);
        sum.send();
        try {
            assertEquals(sum.getBlocking(), Integer.valueOf(73));
            log.info(sum.getBlocking().toString());
        } catch (RMIException e) {
            fail(e.getType().name());
        }
    }

    // --------------------------------------------------

    //only for RMIClient
    @Test
    public void testReexporting() throws InterruptedException {
        final CountDownLatch exportLatch = new CountDownLatch(2);
        log.info(" ---- testReexporting ---- ");
        connectDefault();
        try {
            ConstNumber num = clientPort.getProxy(ConstNumber.class, "TwoFive");
            client.getClient().getService("*").addServiceDescriptorsListener(descriptors -> exportLatch.countDown());
            RMIService<ConstNumber> two = new RMIServiceImplementation<>(new Two(), ConstNumber.class, "TwoFive");
            RMIService<ConstNumber> five = new RMIServiceImplementation<>(new Five(), ConstNumber.class, "TwoFive");
            server.getServer().export(two);
            assertEquals(num.getValue(), 2);
            log.info("---");
            server.getServer().export(five);
            assertTrue(exportLatch.await(10, TimeUnit.SECONDS));
            long result = num.getValue();
            if (result != 2 && result != 5)
                fail();
            assertEquals(result, num.getValue());
            log.info("---");
            server.getServer().unexport(two);
            Thread.sleep(150);
            assertEquals(5, num.getValue());
        } catch (RMIException e) {
            fail(e.getMessage());
        }
    }

    public interface ConstNumber {
        long getValue() throws RMIException;
    }

    public static class Two implements ConstNumber {
        @Override
        public long getValue() {
            return 2;
        }
    }

    public static class Five implements ConstNumber {
        @Override
        public long getValue() {
            return 5;
        }
    }

    // --------------------------------------------------

    public interface InfiniteLooper {
        void loop() throws RMIException, InterruptedException;
    }

    public static class ServerDisconnectingInfiniteLooper implements RMICommonTest.InfiniteLooper {
        private final RMIEndpoint server;

        public ServerDisconnectingInfiniteLooper(RMIEndpoint server) {
            this.server = server;
        }

        @Override
        public void loop() {
            server.disconnect();
            NTU.waitCondition(10_000, 10, () -> finish);
        }
    }


    public static class SimpleInfiniteLooper implements InfiniteLooper {
        @Override
        public void loop() {
            NTU.waitCondition(10_000, 10, () -> finish);
        }
    }

    //only for RMIClient
    @Test
    public void testRequestRunningTimeout() {
        client().getClient().setRequestRunningTimeout(0);
        server().getServer().export(new RMIServiceImplementation<>(new SimpleInfiniteLooper(), InfiniteLooper.class));
        connectDefault();
        InfiniteLooper looper = clientPort.getProxy(InfiniteLooper.class);
        try {
            looper.loop();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.REQUEST_RUNNING_TIMEOUT) {
                fail(e.getMessage());
            } // else ok
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    // --------------------------------------------------

    static class CountingExecutorService extends AbstractExecutorService implements AutoCloseable {
        private final ExecutorService delegate;
        private int submissionsNumber = 0;

        CountingExecutorService(ExecutorService es) {
            delegate = es;
        }

        // :KLUDGE: only this method is used by RMI
        @Nonnull
        @Override
        public Future<?> submit(Runnable task) {
            submissionsNumber++;
            FutureTask<?> fut = new FutureTask<>(task, null);
            delegate.execute(fut);
            return fut;
        }

        @Override
        public void close() {
            shutdown();
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Nonnull
        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(@Nonnull Runnable command) {
            command.run();
        }

        public int getSubmissionsNumber() {
            return submissionsNumber;
        }
    }

    public interface Ping {
        void ping() throws RMIException;
    }

    public static class SimplePing implements Ping {
        @Override
        public void ping() {
        }
    }

    // only for request-channel-serverPort
    @Test
    public void testSpecificExecutors() {
        connectDefault();
        // Set custom executor and try
        RMIServiceImplementation<Ping> service;
        Ping proxy;
        try (CountingExecutorService currentExecutor = new CountingExecutorService(
            Executors.newSingleThreadExecutor(r -> new Thread(r, "RMICommonTest-CountingExecutorService-part-1"))))
        {
            server.getServer().setDefaultExecutor(currentExecutor);
            service = new RMIServiceImplementation<>(new SimplePing(), Ping.class);
            server.getServer().export(service);
            proxy = clientPort.getProxy(Ping.class);
            log.info("---------------- Running part 1 ----------------");
            pingAndCount(proxy, currentExecutor, 5);
        }

        // change executor and make sure processing goes to the new one
        try (CountingExecutorService currentExecutor = new CountingExecutorService(
            Executors.newSingleThreadExecutor(r -> new Thread(r, "RMICommonTest-CountingExecutorService-part-2"))))
        {
            server.getServer().setDefaultExecutor(currentExecutor);
            log.info("---------------- Running part 2 ----------------");
            pingAndCount(proxy, currentExecutor, 3);
        }

        // Specify explicit executor for the service
        try (CountingExecutorService currentExecutor = new CountingExecutorService(
            Executors.newSingleThreadExecutor(r -> new Thread(r, "RMICommonTest-CountingExecutorService-part-3"))))
        {
            service.setExecutor(currentExecutor);
            log.info("---------------- Running part 3 ----------------");
            pingAndCount(proxy, currentExecutor, 7);
        }
    }

    private static void pingAndCount(Ping proxy, CountingExecutorService currentExecutor, int n) {
        for (int i = 0; i < n; i++) {
            try {
                proxy.ping();
            } catch (RMIException e) {
                fail(e.getMessage());
            }
        }
        if (currentExecutor.getSubmissionsNumber() != n) {
            fail(currentExecutor.getSubmissionsNumber() + " vs " + n);
        }
    }

    // --------------------------------------------------

    //only for RMIClient
    @Test
    public void testConnectionAfterSendingRequest() {
        initPorts();
        server().getServer().export(DifferentServices.CALCULATOR_SERVICE);
        @SuppressWarnings("unchecked")
        RMIRequest<Double> sum = clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 12.1321, 352.561);
        sum.send();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        connectDefault();
        try {
            assertEquals(sum.getBlocking(), (Double) (12.1321 + 352.561));
        } catch (RMIException e) {
            fail(e.getMessage());
        }
    }

    // --------------------------------------------------

    //only RMIClient
    @Test
    public void testSubject() {
        initPorts();
        SomeSubject trueSubject = new SomeSubject("true");
        SomeSubject falseSubject = new SomeSubject("false");
        server().getServer().export(new RMIServiceImplementation<>(new SummatorImpl(), Summator.class, "summator"));
        server().setSecurityController(new SomeSecurityController(trueSubject));
        trueClient = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        int port = NTU.connectServer(server());
        NTU.connect(trueClient, NTU.localHost(port));
        trueClient.setSecurityController(new SomeSecurityController(trueSubject));
        Summator summator = trueClient.getClient().getProxy(Summator.class, "summator");

        try {
            assertEquals(summator.sum(256, 458), 256 + 458);
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        trueClient.close();

        falseClient = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        NTU.connect(falseClient, NTU.localHost(port));
        log.info("_____________________");
        falseClient.setSecurityController(new SomeSecurityController(falseSubject));
        summator = falseClient.getClient().getProxy(Summator.class, "summator");
        try {
            summator.sum(256, 458);
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.SECURITY_VIOLATION, e.getType());
        }
        falseClient.close();
    }

    public static class SomeSecurityController implements SecurityController {
        private final SomeSubject subject;

        public SomeSecurityController(SomeSubject subject) {
            this.subject = subject;
        }

        @Override
        public Object getSubject() {
            return subject;
        }

        @Override
        public void doAs(Object subject, Runnable action) throws SecurityException {
            if (subject instanceof SomeSubject && ((SomeSubject) subject).getCode().equals(this.subject.getCode()))
                action.run();
            else
                throw new SecurityException();
        }
    }

    public static class SomeSubject implements Serializable {
        private static final long serialVersionUID = -0L;
        private String code;

        public SomeSubject(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // --------------------------------------------------

    private static class BasicSecurityController implements SecurityController {

        @Override
        public Object getSubject() {
            return null;
        }

        @Override
        public void doAs(Object subject, Runnable action) throws SecurityException {
            if (subject instanceof AuthToken) {
                AuthToken token = (AuthToken) subject;
                if (token.getUser().equals("test") && token.getPassword().equals("demo")) {
                    action.run();
                } else {
                    throw new SecurityException();
                }
            } else {
                throw new SecurityException();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAuthorization() throws NoSuchMethodException {
        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        server.setSecurityController(new BasicSecurityController());
        int port = NTU.connectServer(server);
        server.getServer().export(new SummatorImpl(), Summator.class);

        QDEndpoint endpointClient = QDEndpoint.newBuilder().withName("QD_CLIENT").build().user("test").password("demo");
        client = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, endpointClient, null, null);
        NTU.connect(client, NTU.localHost(port));

        QDEndpoint endpointBadClient =
            QDEndpoint.newBuilder().withName("QD_BAD_CLIENT").build().user("test").password("test");
        falseClient = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, endpointBadClient, null, null);
        NTU.connect(falseClient, NTU.localHost(port));

        // Operation on good client goes through
        RMIOperation<Integer> operation =
            RMIOperation.valueOf(Summator.class, Summator.class.getMethod("sum", int.class, int.class));
        RMIRequest<Integer> request = client.getClient().createRequest(null, operation, 1, 23);
        request.send();
        try {
            assertEquals((int) request.getBlocking(), 24);
        } catch (RMIException e) {
            fail(e.getMessage());
        }

        // Operation on bad client returns security exception
        request = falseClient.getClient().createRequest(null, operation, 1, 23);
        request.send();
        try {
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.SECURITY_VIOLATION)
                fail(e.getMessage());
        }
    }


    // --------------------------------------------------

    private static final int LARGE_SIZE = 100_000;
    private static final int SMALL_SIZE = 100;

    // see com.devexperts.rmi.impl.MessageComposer.MAX_CONCURRENT_RMI_MESSAGES
    private static final int MAX_CONCURRENT_MESSAGES = 6;

    interface LargeRequestProcessor {
        public int process(String reqId, byte[] data) throws RMIException;
    }

    static class LargeRequestProcessorImpl implements LargeRequestProcessor {
        @Override
        public int process(String reqId, byte[] data) throws RMIException {
            log.info("processing request " + reqId + ", size = " + data.length);
            return Arrays.hashCode(data);
        }
    }

    static final RMIOperation<Integer> PROCESS_OP =
        RMIOperation.valueOf(LargeRequestProcessor.class, int.class, "process", String.class, byte[].class);

    private void connectShaped() {
        int port = NTU.connectServer(server, "shaped[outLimit=" + LARGE_SIZE + "]+");
        NTU.connect(client(), "shaped[outLimit=" + LARGE_SIZE + "]+" + NTU.localHost(port));
        initPorts();
    }

    /**
     * Check that pending and in-transition requests are correctly handled when the client connection is lost
     * (see QD-1283)
     */
    @Test
    public void testClientReconnect() throws Exception {
        server().getServer().export(new LargeRequestProcessorImpl(), LargeRequestProcessor.class);
        connectShaped();

        // pass a test request to ensure that all initial procedures related to service were performed
        RMIRequest<Integer> testReq = clientPort.createRequest(PROCESS_OP, "test", getRandomBytes(SMALL_SIZE));
        testReq.send();
        testReq.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        List<RMIRequest<Integer>> reqs = fillConnectionWithLargeRequests();

        // send stale request that shall wait for sending
        RMIRequest<Integer> staleReq = clientPort.createRequest(PROCESS_OP, "stale", getRandomBytes(SMALL_SIZE));
        staleReq.send();
        // sleep a bit and check that it's still waiting to send
        Thread.sleep(10);
        assertEquals(RMIRequestState.WAITING_TO_SEND, staleReq.getState());

        // disconnect abruptly and connect again
        NTU.disconnectClientAbruptly(((RMIEndpointImpl) client).getQdEndpoint(), true);

        // Sending/sent request should fail on disconnect if not finished yet
        for (RMIRequest<Integer> req : reqs) {
            assertTrue(NTU.waitCondition(60_000, 10, req::isCompleted));
        }
        // request that was in WAITING_TO_SEND state on disconnect shall be processed after reconnecting
        staleReq.getPromise().await(60_000, TimeUnit.MILLISECONDS);
        client.close();
    }

    //region Aux ChannelService
    @SuppressWarnings("unused")
    interface ChannelService {
        void startChannel();
        int getValue();
        void finishChannel();

    }
    private static class ChannelServiceImpl implements ChannelService, RMIChannelSupport<Object> {
        RMITask<?> task;
        final int value;

        ChannelServiceImpl(int value) {
            this.value = value;
        }

        @Override
        public void startChannel() {
            task = RMITask.current();
            task.setCancelListener(task1 -> {
                log.info("TASK CANCEL");
                task1.cancel();
            });
            task.suspend(task1 -> {
                log.info("SUSPEND CANCEL");
                task1.cancel();
            });
        }

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public void finishChannel() {
            RMITask.current().complete(null);
            task.complete(null);
        }

        @Override
        public void openChannel(RMITask<Object> task) {
            task.getChannel().addChannelHandler(this, ChannelService.class);
        }
    }

    private static final RMIOperation<Void> START_CHANNEL_OP =
        RMIOperation.valueOf(ChannelService.class, void.class, "startChannel");
    private static final RMIOperation<Integer> CHANNEL_GET_VALUE_OP =
        RMIOperation.valueOf(ChannelService.class, int.class, "getValue");
    private static final RMIOperation<Void> FINISH_CHANNEL_OP =
        RMIOperation.valueOf(ChannelService.class, void.class, "finishChannel");
    //endregion

    /**
     * Check that a new channel (not sent yet) with nested requests correctly handled after reconnect (see QD-1283)
     */
    @Test
    public void testClientNewChannelReconnect() throws Exception {
        server().getServer().export(new LargeRequestProcessorImpl(), LargeRequestProcessor.class);
        server().getServer().export(new ChannelServiceImpl(42), ChannelService.class);
        connectShaped();

        // pass a test request to ensure that all initial procedures related to service were performed
        RMIRequest<Integer> testReq = clientPort.createRequest(PROCESS_OP, "test", getRandomBytes(SMALL_SIZE));
        testReq.send();
        testReq.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        List<RMIRequest<Integer>> reqs = fillConnectionWithLargeRequests();

        // send stale request that shall wait for sending
        RMIRequest<Void> channelReq = clientPort.createRequest(START_CHANNEL_OP);
        channelReq.send();
        RMIChannel channel = channelReq.getChannel();
        RMIRequest<?> nestedReq = channel.createRequest(CHANNEL_GET_VALUE_OP);
        nestedReq.send();
        // sleep a bit and check that it's still waiting to send
        Thread.sleep(10);
        assertEquals(RMIRequestState.WAITING_TO_SEND, channelReq.getState());
        assertEquals(RMIRequestState.WAITING_TO_SEND, nestedReq.getState());

        // disconnect abruptly and connect again
        NTU.disconnectClientAbruptly(((RMIEndpointImpl) client).getQdEndpoint(), true);

        // Sending/sent request should fail on disconnect if not finished yet
        for (RMIRequest<Integer> req : reqs) {
            assertTrue(NTU.waitCondition(60_000, 10, req::isCompleted));
        }
        // request that was in WAITING_TO_SEND state on disconnect shall be processed after reconnecting
        nestedReq.getPromise().await(60_000, TimeUnit.MILLISECONDS);
        assertEquals(42, nestedReq.getBlocking());
        RMIRequest<Void> finishChannel = channel.createRequest(FINISH_CHANNEL_OP);
        finishChannel.send();
        finishChannel.getPromise().await(60_000, TimeUnit.MILLISECONDS);
        channelReq.getPromise().await(60_000, TimeUnit.MILLISECONDS);
        client.close();
    }

    /**
     * Check that an open (in progress) channel with nested requests correctly handled after reconnect (see QD-1283).
     */
    @Test
    public void testClientOpenChannelReconnect() throws Exception {
        server().getServer().export(new LargeRequestProcessorImpl(), LargeRequestProcessor.class);
        server().getServer().export(new ChannelServiceImpl(42), ChannelService.class);
        connectShaped();

        // open channel
        RMIRequest<Void> channelReq = clientPort.createRequest(START_CHANNEL_OP);
        channelReq.send();
        RMIChannel channel = channelReq.getChannel();
        assertTrue(NTU.waitCondition(60_000, 10, channel::isOpen));
        assertEquals(RMIRequestState.SENT, channelReq.getState());

        // pass a test request to ensure that all initial procedures related to service were performed
        RMIRequest<Integer> testReq = clientPort.createRequest(PROCESS_OP, "test", getRandomBytes(SMALL_SIZE));
        testReq.send();
        testReq.getPromise().await(10_000, TimeUnit.MILLISECONDS);

        List<RMIRequest<Integer>> reqs = fillConnectionWithLargeRequests();

        // send stale nested request that shall wait for sending
        RMIRequest<?> nestedReq = channel.createRequest(CHANNEL_GET_VALUE_OP);
        nestedReq.send();
        // sleep a bit and check that it's still waiting to send
        Thread.sleep(10);
        assertEquals(RMIRequestState.WAITING_TO_SEND, nestedReq.getState());

        // disconnect abruptly and connect again
        NTU.disconnectClientAbruptly(((RMIEndpointImpl) client).getQdEndpoint(), true);

        // Sending/sent request should fail on disconnect if not finished yet
        for (RMIRequest<Integer> req : reqs) {
            assertTrue(NTU.waitCondition(60_000, 10, req::isCompleted));
        }
        // nested request of an open channel that was in WAITING_TO_SEND state on disconnect shall fail on disconnect
        assertTrue(NTU.waitCondition(60_000, 10, nestedReq::isCompleted));
        assertEquals(RMIRequestState.FAILED, nestedReq.getState());
        assertEquals(RMIRequestState.FAILED, channelReq.getState());
        assertEquals(RMIChannelState.CLOSED, channel.getState());
        client.close();
    }


    private byte[] getRandomBytes(int size) {
        byte[] data = new byte[size];
        Random rnd = ThreadLocalRandom.current();
        rnd.nextBytes(data);
        return data;
    }

    /**
     * Send enough {@link LargeRequestProcessor} requests to fill output queue and wait all of them started sending.
     *
     * @return list of generated requests
     */
    private List<RMIRequest<Integer>> fillConnectionWithLargeRequests() {
        byte[] data = new byte[LARGE_SIZE];
        Random rnd = ThreadLocalRandom.current();

        ArrayList<RMIRequest<Integer>> reqs = new ArrayList<>();
        for (int i = 1; i <= MAX_CONCURRENT_MESSAGES; i++) {
            rnd.nextBytes(data);
            RMIRequest<Integer> req = clientPort.createRequest(PROCESS_OP, "large-" + i, data.clone());
            req.send();
            reqs.add(req);
            assertTrue(NTU.waitCondition(10_000, 10, () -> {
                RMIRequestState state = req.getState();
                return state != RMIRequestState.NEW && state != RMIRequestState.WAITING_TO_SEND;
            }));
        }
        return reqs;
    }
}
