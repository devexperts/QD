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
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestListener;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelState;
import com.devexperts.rmi.task.RMIChannelSupport;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.task.RMITaskCancelListener;
import com.devexperts.rmi.task.RMITaskState;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class RMIChannelTest {
    private static final Logging log = Logging.getLogging(RMIChannelTest.class);

    private static final int REQUEST_RUNNING_TIMEOUT = 10_000;

    private RMIEndpointImpl server;
    private RMIEndpointImpl client;
    private RMIEndpointImpl privateEndpoint;
    private RMIEndpointImpl remoteEndpoint;

    private List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
    private final TestThreadPool executor = new TestThreadPool(10, "RMIChannelTest", exceptions);
    private static Thread activeServerThread;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        server = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("Server")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        client = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("client")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        privateEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("privateClient")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        remoteEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("remoteServer")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        server.getServer().setDefaultExecutor(executor);
        client.getClient().setDefaultExecutor(executor);
        privateEndpoint.getClient().setDefaultExecutor(executor);
        remoteEndpoint.getServer().setDefaultExecutor(executor);
        client.getClient().setRequestRunningTimeout(REQUEST_RUNNING_TIMEOUT);
        privateEndpoint.getClient().setRequestRunningTimeout(REQUEST_RUNNING_TIMEOUT);
    }

    @After
    public void tearDown() {
        privateEndpoint.close();
        remoteEndpoint.close();
        client.close();
        server.close();
        executor.shutdown();
        ThreadCleanCheck.after();
        assertTrue(exceptions.isEmpty());
    }

    private void connectDefault() {
        NTU.connectPair(server, client);
    }

    private void connectWithForwarding() {
        NTU.connectPair(server, client);
        NTU.connectPair(remoteEndpoint, privateEndpoint);
    }

    // --------------------------------------------------

    private static final String MULTIPLICATIONS_SERVICE_NAME = "ManyMultiplications";
    private static final String CLIENT_CHANNEL = "ClientChannel";
    private static final String CHANNEL_HANDLER = "Progress";

    private static final RMIOperation<Void> INTERMEDIATE_RESULT_OPERATION =
        RMIOperation.valueOf(CLIENT_CHANNEL, Void.class, "intermediateResult", Long.class);
    private static final RMIOperation<Void> MULTIPLICATION_PROGRESS_OPERATION =
        RMIOperation.valueOf(CHANNEL_HANDLER, Void.class, "progress", int.class);

    private static RMIService<?> multiplications = new RMIService<Double>(MULTIPLICATIONS_SERVICE_NAME) {
        static final double HUNDRED = 100d;
        Random rnd = new Random();

        @Override
        public void openChannel(RMITask<Double> task) {
            task.setCancelListener(task1 -> {
                task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
                log.info("Server side: Cancelled task & UNPARK");
                isContinue = true;
                LockSupport.unpark(activeServerThread);
            });
            task.getChannel().addChannelHandler(new RMIService<Void>(CHANNEL_HANDLER) {
                @Override
                public void processTask(RMITask<Void> task) {
                    log.info("Server side channel: message = " + task.getRequestMessage());
                }
            });
        }

        @Override
        public void processTask(RMITask<Double> task) {
            activeServerThread = Thread.currentThread();
            double temp = 1;
            double startMult = (double) task.getRequestMessage().getParameters().getObject()[0];
            double n = (int) task.getRequestMessage().getParameters().getObject()[1];
            int percentStat = (int) task.getRequestMessage().getParameters().getObject()[2];
            double countStat = n * (double) percentStat / HUNDRED;
            int percent = 0;
            RMIRequest<Void> request = null;
            for (int i = 0; i <= n; i += countStat) {
                if (task.isCompleted())
                    return;
                // load emulation
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    log.info("Interrupted", e);
                    return;
                }
                request = task.getChannel().createRequest(new RMIRequestMessage<>(
                    RMIRequestType.ONE_WAY, MULTIPLICATION_PROGRESS_OPERATION, percent));
                percent += percentStat;
                temp *= Math.sqrt(Math.abs(rnd.nextDouble() * Math.sin(rnd.nextDouble())));
                isContinue = false;
                request.send();
                log.info("Server side: PARK");
                while (!isContinue && !task.isCompleted()) {
                    log.info("Server side: ProgressTask pause.");
                    LockSupport.park();
                }
                log.info("Server side: ProgressTask continue.");
            }
            assert request != null;
            double finalTemp = temp;
            request.setListener(req -> task.complete(finalTemp * startMult));
        }
    };

    @Test
    public void testProgressCalculations() throws InterruptedException {
        connectDefault();
        server.getServer().export(multiplications);
        final CountDownLatch processChannelLatch = new CountDownLatch(20);
        final CountDownLatch processResultLatch = new CountDownLatch(1);
        final int percentStat = 5;
        final AtomicInteger complete = new AtomicInteger(0);
        RMIRequest<Double> request = client.getClient().createRequest(null,
            RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class),
            0d, 1000000, percentStat);
        request.getChannel().addChannelHandler(new RMIService<Void>(CHANNEL_HANDLER) {
            @Override
            public void processTask(RMITask<Void> task) {
                RMIRequestMessage<Void> message = task.getRequestMessage();
                int percent = (int) message.getParameters().getObject()[0];
                processChannelLatch.countDown();
                log.info("percent = " + percent + " " + complete.get());
                assertEquals(percent, complete.getAndAdd(percentStat));
                log.info("UNPARK client");
                isContinue = true;
                LockSupport.unpark(activeServerThread);
                task.complete(null);
            }

        });
        request.setListener(request1 -> {
            try {
                if (!processChannelLatch.await(10, TimeUnit.SECONDS))
                    fail();
            } catch (InterruptedException e) {
                fail();
            }
            log.info("COMPLETED! processChannelLatch=" + processChannelLatch.getCount());
            processResultLatch.countDown();
            log.info("processResultLatch = " + processResultLatch);
        });
        request.send();
        assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
        if (!processResultLatch.await(30, TimeUnit.SECONDS))
            fail(new AssertionError(processResultLatch.getCount()).getMessage());
        assertEquals(request.getNonBlocking(), (Double) 0d);
        log.info("processResultLatch = " + processResultLatch);
        log.info("processChannelLatch = " + processChannelLatch);
    }

    // --------------------------------------------------

    @Test
    public void testCancelChannelTask() throws InterruptedException {
        ChannelHandler.update();
        connectDefault();
        ChannelTaskCancelCheckerImpl channelCancelChecker = new ChannelTaskCancelCheckerImpl();
        server.getServer().export(channelCancelChecker, ChannelTaskCancelChecker.class);
        CountDownLatch[] requestCancelListenerLatch = {new CountDownLatch(1)};

        log.info("Client channel task and result");
        RMIRequest<String> request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "resultResponseClientCheck"));
        client.getClient().setRequestRunningTimeout(100_000);
        request.getChannel().addChannelHandler(ChannelHandler.resultResponseClientHandler);
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        request.send();
        try {
            assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));
            assertEquals("OK", request.getBlocking());
        } catch (RMIException e) {
            fail(e.getType().toString());
        }
        assertTrue(ChannelHandler.resultResponseClientHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));

        log.info("Client channel task and fail");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "failedResponseClientCheck"));
        request.getChannel().addChannelHandler(ChannelHandler.failedResponseClientHandler);
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        request.send();
        try {
            assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));
            assertEquals("FAILED", request.getBlocking());
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.APPLICATION_ERROR, e.getType());
            assertEquals(RuntimeException.class, e.getCause().getClass());
            assertEquals(e.getMessage(), "OK", e.getCause().getMessage());
        }
        assertTrue(ChannelHandler.failedResponseClientHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));

        log.info("Client channel task and cancelClientCheck (cancelOrAbort)");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "cancelClientCheck"));
        request.getChannel().addChannelHandler(ChannelHandler.cancelClientHandler);
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        request.send();
        assertTrue(ChannelHandler.cancelClientHandler.channelTaskLatch.await(10, TimeUnit.SECONDS));
        request.cancelOrAbort();
        try {
            assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.CANCELLED_DURING_EXECUTION, e.getType());
        }
        assertTrue(ChannelHandler.cancelClientHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));

        log.info("Client channel task and cancelClientCheck (cancelWithConfirmation)");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        ChannelHandler.cancelClientHandler = new ChannelHandler();
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "cancelClientCheck"));
        request.getChannel().addChannelHandler(ChannelHandler.cancelClientHandler);
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        request.send();
        assertTrue(ChannelHandler.cancelClientHandler.channelTaskLatch.await(10, TimeUnit.SECONDS));
        request.cancelWithConfirmation();
        try {
            assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));
            assertTrue(ChannelHandler.cancelClientHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));
            assertEquals("OK", request.getBlocking());
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.CANCELLED_AFTER_EXECUTION, e.getType());
        }

        log.info("Server channel task and result");
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "resultResponseServerCheck"));
        RMIRequest<String> channelRequest = request.getChannel().createRequest(
            RMIOperation.valueOf(ChannelHandler.NAME, String.class, "method"));
        channelRequest.setListener(req -> log.info("client channel request listener"));
        channelRequest.send();
        request.send();
        try {
            assertEquals("OK", request.getBlocking());
        } catch (RMIException e) {
            fail(e.getType().toString());
        }
        assertTrue(ChannelHandler.resultResponseServerHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));

        log.info("Server channel task and fail");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "failResponseServerCheck"));
        channelRequest = request.getChannel().createRequest(
            RMIOperation.valueOf(ChannelHandler.NAME, String.class, "method"));
        channelRequest.setListener(req -> {
            log.info("client channel request listener");
            requestCancelListenerLatch[0].countDown();
        });
        channelRequest.send();
        request.send();
        try {
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.APPLICATION_ERROR, e.getType());
            assertEquals(RuntimeException.class, e.getCause().getClass());
            assertEquals(e.getMessage(), "OK", e.getCause().getMessage());
        }
        assertTrue(ChannelHandler.failedResponseServerHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));
        assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));

        log.info("Server channel task and cancelClientCheck (cancelOrAbort)");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "cancelServerCheck"));
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        channelRequest = request.getChannel().createRequest(
            RMIOperation.valueOf(ChannelHandler.NAME, String.class, "method"));
        channelRequest.setListener(req -> log.info("client channel request listener"));
        channelRequest.send();
        request.send();
        assertTrue(ChannelHandler.cancelServerHandler.channelTaskLatch.await(10, TimeUnit.SECONDS));
        request.cancelOrAbort();
        try {
            assertTrue(requestCancelListenerLatch[0].await(10, TimeUnit.SECONDS));
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.CANCELLED_DURING_EXECUTION, e.getType());
        }
        assertTrue(ChannelHandler.cancelServerHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));

        log.info("Server channel task and cancelClientCheck (cancelWithConfirmation)");
        requestCancelListenerLatch[0] = new CountDownLatch(1);
        ChannelTaskCancelCheckerImpl.latch = new CountDownLatch(1);
        ChannelHandler.cancelServerHandler = new ChannelHandler();
        request = client.getClient().createRequest(null,
            RMIOperation.valueOf(ChannelTaskCancelChecker.class, String.class, "cancelServerCheck"));
        request.getChannel().addChannelHandler(ChannelHandler.cancelClientHandler);
        request.setListener(req -> {
            log.info("client request listener");
            requestCancelListenerLatch[0].countDown();
        });
        channelRequest = request.getChannel().createRequest(
            RMIOperation.valueOf(ChannelHandler.NAME, String.class, "method"));
        channelRequest.setListener(req -> log.info("client channel request listener :: " + req));
        channelRequest.send();
        request.send();
        assertTrue(ChannelHandler.cancelServerHandler.channelTaskLatch.await(10, TimeUnit.SECONDS));
        request.cancelWithConfirmation();
        try {
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(RMIExceptionType.CANCELLED_AFTER_EXECUTION, e.getType());
        }
        assertTrue(ChannelHandler.cancelServerHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS));
    }

    private static class ChannelHandler extends RMIService<String> {

        private static final String NAME = "ChannelHandler";
        private static void update() {
            resultResponseClientHandler = new ChannelHandler();
            failedResponseClientHandler = new ChannelHandler();
            cancelClientHandler = new ChannelHandler();
            resultResponseServerHandler = new ChannelHandler();
            failedResponseServerHandler = new ChannelHandler();
            cancelServerHandler = new ChannelHandler();
            ChannelTaskCancelCheckerImpl.latch = new CountDownLatch(1);
        }
        static ChannelHandler resultResponseClientHandler;
        static ChannelHandler failedResponseClientHandler;
        static ChannelHandler cancelClientHandler;
        static ChannelHandler resultResponseServerHandler;
        static ChannelHandler failedResponseServerHandler;
        static ChannelHandler cancelServerHandler;

        CountDownLatch cancelTaskLatch = new CountDownLatch(1);
        CountDownLatch channelTaskLatch = new CountDownLatch(1);

        ChannelHandler() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<String> task) {
            log.info("ChannelHandler processTask");
            AtomicBoolean flag = new AtomicBoolean(false);
            task.setCancelListener(t -> {
                log.info("ChannelHandler task listener");
                cancelTaskLatch.countDown();
                flag.set(true);
            });
            channelTaskLatch.countDown();
            while (!flag.get() && ChannelTaskCancelCheckerImpl.latch.getCount() != 0){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    task.completeExceptionally(e);
                    break;
                }
            }

            task.complete("Task completed or cancelling");
            log.info("ChannelHandler processTask END");
        }
    }

    @SuppressWarnings("unused")
    private static interface ChannelTaskCancelChecker extends RMIChannelSupport<String> {
        String resultResponseClientCheck() throws InterruptedException;
        String failedResponseClientCheck() throws InterruptedException;
        String cancelClientCheck() throws InterruptedException;
        String resultResponseServerCheck() throws InterruptedException;
        String failResponseServerCheck() throws InterruptedException;
        String cancelServerCheck() throws InterruptedException;
    }

    private static class ChannelTaskCancelCheckerImpl implements ChannelTaskCancelChecker {
        static CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void openChannel(RMITask<String> task) {
            if (task.getOperation().getMethodName().contains("Client")) {
                RMIRequest<String> request = task.getChannel().createRequest(
                    RMIOperation.valueOf(ChannelHandler.NAME, String.class, "method"));
                request.setListener(req -> log.info("server channel request listener"));
                request.send();
            } else if (task.getOperation().getMethodName().equals("resultResponseServerCheck")) {
                task.getChannel().addChannelHandler(ChannelHandler.resultResponseServerHandler);
            } else if (task.getOperation().getMethodName().contains("failResponseServerCheck")) {
                task.getChannel().addChannelHandler(ChannelHandler.failedResponseServerHandler);
            } else if (task.getOperation().getMethodName().contains("cancelServerCheck")) {
                task.getChannel().addChannelHandler(ChannelHandler.cancelServerHandler);
                task.setCancelListener(t -> {
                    latch.countDown();
                    log.info("server task listener");
                });
                return;
            }
            task.setCancelListener(t -> log.info("server task listener"));
        }

        @Override
        public String resultResponseClientCheck() throws InterruptedException {
            log.info("resultResponseClientCheck");
            if (ChannelHandler.resultResponseClientHandler.channelTaskLatch.await(10, TimeUnit.SECONDS))
                return "OK";
            return "FAILED";
        }

        @Override
        public String failedResponseClientCheck() throws InterruptedException {
            log.info("failedResponseClientCheck");
            if (ChannelHandler.failedResponseClientHandler.channelTaskLatch.await(10, TimeUnit.SECONDS))
                throw new RuntimeException("OK");
            throw new RuntimeException("FAILED");
        }

        @Override
        public String cancelClientCheck() throws InterruptedException {
            log.info("cancelClientCheck");
            if (ChannelHandler.cancelClientHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS))
                return "OK";
            return "FAILED";
        }

        @Override
        public String resultResponseServerCheck() throws InterruptedException {
            log.info("resultResponseServerCheck");
            if (ChannelHandler.resultResponseServerHandler.channelTaskLatch.await(10, TimeUnit.SECONDS))
                return "OK";
            return "FAILED";
        }

        @Override
        public String failResponseServerCheck() throws InterruptedException {
            log.info("failResponseServerCheck");
            if (ChannelHandler.failedResponseServerHandler.channelTaskLatch.await(10, TimeUnit.SECONDS))
                throw new RuntimeException("OK");
            throw new RuntimeException("FAILED");
        }

        @Override
        public String cancelServerCheck() throws InterruptedException {
            log.info("cancelServerCheck");
            if (ChannelHandler.cancelServerHandler.cancelTaskLatch.await(10, TimeUnit.SECONDS))
                return "OK";
            return "FAILED";
        }
    }


    // --------------------------------------------------

    @Test
    public void testProgressCancel() throws InterruptedException {
        connectDefault();
        server.getServer().export(multiplications);
        final CountDownLatch processResultLatch = new CountDownLatch(1);
        final CountDownLatch processChannelLatch = new CountDownLatch(1);
        final int percentStat = 1;
        final RMIRequest<Double> request = client.getClient().createRequest(null,
            RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class),
            0d, 1000000, percentStat);
        final AtomicInteger process = new AtomicInteger(0);
        request.getChannel().addChannelHandler(new RMIService<Object>("*") {
            @Override
            public void processTask(RMITask<Object> task) {
                RMIRequestMessage<?> message = task.getRequestMessage();
                assertEquals(MULTIPLICATION_PROGRESS_OPERATION, task.getOperation());
                int percent = (int) message.getParameters().getObject()[0];
                log.info("=== " + percent + " task = " + task);
                if (percent == 20) {
                    request.cancelOrAbort();
                    processChannelLatch.countDown();
                }
                process.set(percent);
                log.info("UNPARK server");
                isContinue = true;
                LockSupport.unpark(activeServerThread);
                task.complete(null);
            }
        });
        request.setListener(new RMIRequestListener() {
            @Override
            public void requestCompleted(RMIRequest<?> request) {
                log.info("request completed");
                processResultLatch.countDown();
                if (request.getState() != RMIRequestState.FAILED)
                    fail();
            }

            @Override
            public String toString() {
                return " I LISTENER";
            }
        });
        request.send();
        assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
        assertTrue(processResultLatch.await(30, TimeUnit.SECONDS));
        log.info("process = " + process);
        assertTrue(process.get() < 50);
    }

    // --------------------------------------------------

    @Test
    public void testProgressDisconnect() throws InterruptedException {
        connectDefault();
        server.getServer().export(multiplications);
        final CountDownLatch processResult = new CountDownLatch(1);
        final CountDownLatch processChannelLatch = new CountDownLatch(1);
        final int percentStat = 10;
        final RMIRequest<Double> request = client.getClient().createRequest(null,
            RMIOperation.valueOf(MULTIPLICATIONS_SERVICE_NAME, double.class, "MULT", double.class, int.class, int.class),
            0d, 10000000, percentStat);
        final AtomicInteger process = new AtomicInteger(0);
        request.getChannel().addChannelHandler(new RMIService<Object>("*") {
            @Override
            public void processTask(RMITask<Object> task) {
                log.info("Client side channel: 1 step (percent = " +
                    task.getRequestMessage().getParameters().getObject()[0] + ")");
                assertEquals(task.getOperation(), MULTIPLICATION_PROGRESS_OPERATION);
                RMIRequestMessage<?> message = task.getRequestMessage();
                int percent = (int) message.getParameters().getObject()[0];
                process.set(percent);
                if (percent == 50) {
                    log.info("Client side channel: Client disconnect");
                    client.disconnect();
                    processChannelLatch.countDown();
                }
                log.info("Client side channel: 2 step");
                task.complete(null);
                log.info("Client side channel: UNPARK");
                isContinue = true;
                LockSupport.unpark(activeServerThread);
            }
        });
        request.setListener(request1 -> {
            log.info("request complete");
            processResult.countDown();
        });
        request.send();
        assertTrue(processResult.await(30, TimeUnit.SECONDS));
        log.info("process = " + process);
        assertTrue(processChannelLatch.await(30, TimeUnit.SECONDS));
        log.info("process = " + process);
        assertEquals(50, process.get());
    }

    // --------------------------------------------------

    private static final String POWER_SERVICE_NAME = "calculationPower";
    private static final String SERVER_CHANNEL = "ServerChannel";
    private static final RMIOperation<Void> SERVER_UPDATE_OPERATION =
        RMIOperation.valueOf(SERVER_CHANNEL, Void.class, "update", long.class);
    private static final RMIOperation<Void> SERVER_STOP_OPERATION =
        RMIOperation.valueOf(SERVER_CHANNEL, Void.class, "stop", long.class, boolean.class);

    private final RMIService<Long> powerService = new RMIService<Long>(POWER_SERVICE_NAME) {
        volatile long factor;
        volatile long total = 1;
        volatile boolean stop = false;
        volatile long count = 1;

        @Override
        public void openChannel(RMITask<Long> task) {
            task.setCancelListener(task1 -> {
                task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
                log.info("Server side: Cancelled task & UNPARK");
                isContinue = true;
                LockSupport.unpark(activeServerThread);
            });
            task.getChannel().addChannelHandler(new RMIService<Object>(SERVER_CHANNEL) {
                @Override
                public void processTask(RMITask<Object> task) {
                    log.info("ServerChanelMessage = " + task.getRequestMessage());
                    boolean updateOp = task.getOperation().equals(SERVER_UPDATE_OPERATION);
                    boolean stopOp = task.getOperation().equals(SERVER_STOP_OPERATION);
                    assertTrue(updateOp || stopOp);
                    RMIRequestMessage<?> message = task.getRequestMessage();
                    if (stopOp) {
                        stop = true;
                        log.info("UNPARK server stop");
                        isContinue = true;
                        LockSupport.unpark(activeServerThread);
                        return;
                    }
                    factor = (long) message.getParameters().getObject()[0];
                    total = 1;
                    count = 0;
                    log.info("UNPARK server");
                    isContinue = true;
                    LockSupport.unpark(activeServerThread);
                    task.complete(null);
                }
            });
        }

        @Override
        public void processTask(RMITask<Long> task) {
            activeServerThread = Thread.currentThread();
            factor = (long) task.getRequestMessage().getParameters().getObject()[0];
            log.info("Start process");
            RMIRequest<Void> request;
            while (!stop) {
                total *= factor;
                log.info("Count = " + count);
                if (count % 3 == 0) {
                    request = task.getChannel().createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY,
                        INTERMEDIATE_RESULT_OPERATION, total));
                    isContinue = false;
                    request.send();
                    while (!isContinue && !task.isCompleted()) {
                        log.info("ProgressTask pause.");
                        LockSupport.park();
                    }
                    log.info("ProgressTask continue.");
                }
                count++;
            }
            task.complete(total);
        }
    };


    // --------------------------------------------------

    @Test
    public void testIntermediateResultChannel() throws InterruptedException, RMIException {
        connectDefault();
        server.getServer().export(powerService);
        intermediateResultChannel();
    }

    // --------------------------------------------------

    private static volatile boolean isContinue = false;

    @Test
    public void testChannelForward() throws RMIException, InterruptedException {
        connectWithForwarding();
        remoteEndpoint.getServer().export(powerService);
        server.getServer().export(privateEndpoint.getClient().getService("*"));
        intermediateResultChannel();
    }

    private void intermediateResultChannel() throws InterruptedException, RMIException {
        final CountDownLatch firstProcessChannelLatch = new CountDownLatch(3);
        final CountDownLatch secondProcessChannelLatch = new CountDownLatch(3);
        final CountDownLatch processResultLatch = new CountDownLatch(1);
        RMIRequest<Long> request = client.getClient().createRequest(null,
            RMIOperation.valueOf(POWER_SERVICE_NAME, long.class, "calc", long.class), 2L);
        final AtomicLong step = new AtomicLong(8);
        final AtomicLong expectedResult = new AtomicLong(8);
        RMIChannel channel = request.getChannel();
        channel.addChannelHandler(new RMIService<Object>("*") {
            @Override
            public void processTask(RMITask<Object> task) {
                assertEquals(task.getOperation(), INTERMEDIATE_RESULT_OPERATION);
                RMIRequestMessage<?> message = task.getRequestMessage();
                log.info("ClientChanelMessage = " + message);
                long result = (long) message.getParameters().getObject()[0];
                if (expectedResult.get() == result) {
                    expectedResult.set(result * step.get());
                    log.info("channel: new expected result = " + expectedResult.get());
                    if (result % 2 == 0) {
                        firstProcessChannelLatch.countDown();
                        if (firstProcessChannelLatch.getCount() != 0) {
                            log.info("UNPARK first");
                            isContinue = true;
                            LockSupport.unpark(activeServerThread);
                        }
                    } else {
                        secondProcessChannelLatch.countDown();
                        if (secondProcessChannelLatch.getCount() != 0) {
                            log.info("UNPARK second");
                            isContinue = true;
                            LockSupport.unpark(activeServerThread);
                        }
                    }
                }
                task.complete(null);
            }

        });
        request.setListener(request1 -> {
            log.info("request response = " + request1);
            if (firstProcessChannelLatch.getCount() != 0 && secondProcessChannelLatch.getCount() != 0)
                fail();
            processResultLatch.countDown();
        });
        request.send();
        log.info("------ first step ------");
        firstProcessChannelLatch.await(10, TimeUnit.SECONDS);

        log.info("------ second step ------");
        expectedResult.set(27);
        log.info("second step: expected result = " + expectedResult.get());
        channel.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY, SERVER_UPDATE_OPERATION, 3L)).send();
        step.set(27);
        assertTrue(secondProcessChannelLatch.await(10, TimeUnit.SECONDS));
        channel.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY, SERVER_STOP_OPERATION, 3L, true)).send();
        assertTrue(processResultLatch.await(10, TimeUnit.SECONDS));
        long result = request.getBlocking();
        assertEquals((long) Math.pow(3, 9), result);
        System.out.println("THE END!");
    }

    // --------------------------------------------------

    @Test
    public void testChannelOpenMethod() throws RMIException, InterruptedException {
        connectDefault();
        server.getServer().export(new ChannelServiceImpl(42), ChannelService.class);

        RMIRequest<Void> request = client.getClient().getPort(null).createRequest(
            RMIOperation.valueOf(ChannelService.class, void.class, "startChannel"));
        RMIChannel channel = request.getChannel();
        request.send();
        RMIRequest<?> channelRequest = channel.createRequest(
            RMIOperation.valueOf(ChannelService.class, int.class, "getValue"));
        channelRequest.send();
        Integer a = (Integer) channelRequest.getBlocking();
        assertEquals(42, (int) a);
        assertNull(request.getNonBlocking());
        channelRequest = channel.createRequest(
            RMIOperation.valueOf(ChannelService.class, void.class, "finishChannel"));
        channelRequest.send();
        assertNull(channelRequest.getBlocking());
        assertNull(request.getBlocking());
    }

    // --------------------------------------------------

    @Test
    public void testChannelSendBeforeRequest() throws RMIException {
        connectDefault();
        server.getServer().export(new ChannelServiceImpl(42), ChannelService.class);

        RMIRequest<Void> request = client.getClient().getPort(null).createRequest(
            RMIOperation.valueOf(ChannelService.class, void.class, "startChannel"));
        RMIChannel channel = request.getChannel();
        RMIRequest<?> channelRequest = channel.createRequest(
            RMIOperation.valueOf(ChannelService.class, int.class, "getValue"));
        channelRequest.send();
        assertNull(channelRequest.getNonBlocking());
        request.send();
        Integer a = (Integer) channelRequest.getBlocking();
        assertEquals(42, (int) a);
        assertNull(request.getNonBlocking());
        channelRequest = channel.createRequest(
            RMIOperation.valueOf(ChannelService.class, void.class, "finishChannel"));
        channelRequest.send();
        assertNull(channelRequest.getBlocking());
        assertNull(request.getBlocking());
    }

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
            task.suspend((RMITaskCancelListener) task1 -> {
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

    // --------------------------------------------------

    @Test
    public void testOpenChannelError() {
        connectDefault();
        server.getServer().export(new OpenChannelError());

        RMIRequest<Integer> request = client.getClient().getPort(null).createRequest(
            RMIOperation.valueOf(OpenChannelError.NAME, int.class, "method"));
        RMIChannel channel = request.getChannel();
        RMIRequest<Integer> channelRequest = channel.createRequest(
            RMIOperation.valueOf(OpenChannelError.NAME, int.class, "channelMethod"));
        channelRequest.send();
        assertNull(channelRequest.getNonBlocking());
        request.send();
        try {
            request.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.EXECUTION_ERROR)
                fail();
            assertEquals("open channel error", e.detail.getMessage());
        }
        try {
            channelRequest.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() != RMIExceptionType.CHANNEL_CLOSED)
                fail(e.getType().getMessage());
        }
    }

    private static class OpenChannelError extends RMIService<Integer> {

        static final String NAME = "openChannelError";

        OpenChannelError() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Integer> task) {
            task.complete(5);
        }

        @Override
        public void openChannel(RMITask<Integer> task) {
            throw new RuntimeException("open channel error");
        }
    }

    // ---------------------------------------------------------

    private static final int REQUEST_COUNT = 6;
    private static class ChannelOneWayServiceImpl implements ChannelService, RMIChannelSupport<Object> {

        @Override
        public void startChannel() {
            isStart = true;
        }

        @Override
        public int getValue() {
            return 0;
        }

        @Override
        public void finishChannel() {}

        @Override
        public void openChannel(RMITask<Object> task) {
            isOpen = true;
        }
    }

    private static volatile boolean isOpen;
    private static volatile boolean isStart;

    @Test
    public void testOpenChannelForOneWayRequest() throws InterruptedException {
        connectDefault();
        isOpen = false;
        isStart = true;
        server.getServer().export(new ChannelOneWayServiceImpl(), ChannelService.class);
        CountDownLatch reqComplete = new CountDownLatch(REQUEST_COUNT);
        RMIRequest<Void> request = null;
        for (int i = 0; i < REQUEST_COUNT; i++) {
            request = client.getClient().getPort(null).createRequest(new RMIRequestMessage<>(
                RMIRequestType.ONE_WAY, RMIOperation.valueOf(ChannelService.class, void.class, "startChannel")));
            request.setListener(req -> reqComplete.countDown());
            request.send();
            System.out.println(request.getChannel());
        }
        assertTrue(reqComplete.await(10, TimeUnit.SECONDS));
        assertEquals(RMIChannelState.CLOSED, request.getChannel().getState());
        assertTrue(isStart);
        assertFalse(isOpen);
    }

    // ---------------------------------------------------------

    @Ignore("this test fails consistently. fix it or drop it")
    @Test
    public void testChannelRequestCancel() throws InterruptedException {
        connectDefault();
        server.getServer().export(new ChannelRequestCancelCheckerImpl(), ChannelRequestCancelChecker.class);
        RMIOperation<Void> requestOperation = RMIOperation.valueOf(
            ChannelRequestCancelChecker.class, void.class, "process", boolean.class, boolean.class);
        RMIOperation<Void> channelRequestOperation = RMIOperation.valueOf(
            ChannelRequestCancelCheckerImpl.ChannelHandler.class.getName(), void.class, "method");

        log.info("Server channel request, cancel before send");
        RMIRequest<Void> request = client.getClient().getPort(null).createRequest(requestOperation, true, true);
        RMIRequest<Void> channelRequest = request.getChannel().createRequest(channelRequestOperation);
        request.setListener(req -> {
            ChannelRequestCancelCheckerImpl.waitFlag = false;
            LockSupport.unpark(ChannelRequestCancelCheckerImpl.currentThread);
        });
        CountDownLatch[] channelRequestCancel = {new CountDownLatch(1)};
        channelRequest.setListener(req -> {
            ChannelRequestCancelCheckerImpl.ChannelHandler.waitFlag = false;
            LockSupport.unpark(ChannelRequestCancelCheckerImpl.ChannelHandler.currentThread);
            channelRequestCancel[0].countDown();
        });
        request.send();
        assertTrue(ChannelRequestCancelCheckerImpl.startLatch.await(10, TimeUnit.SECONDS));
        channelRequest.cancelOrAbort();
        assertTrue(channelRequestCancel[0].await(10, TimeUnit.SECONDS));
        assertTrue(request.getChannel().isOpen());
        assertEquals(RMIRequestState.SENT, request.getState());

        log.info("Server channel request, cancel after send");
        channelRequestCancel[0] = new CountDownLatch(1);
        channelRequest = request.getChannel().createRequest(channelRequestOperation);
        channelRequest.setListener(req -> {
            ChannelRequestCancelCheckerImpl.ChannelHandler.waitFlag = false;
            LockSupport.unpark(ChannelRequestCancelCheckerImpl.ChannelHandler.currentThread);
            channelRequestCancel[0].countDown();
        });
        channelRequest.send();
        assertTrue(ChannelRequestCancelCheckerImpl.ChannelHandler.startLatch.await(10, TimeUnit.SECONDS));
        channelRequest.cancelOrAbort();
        assertTrue(channelRequestCancel[0].await(10, TimeUnit.SECONDS));
        assertTrue(request.getChannel().isOpen());
        assertEquals(RMIRequestState.SENT, request.getState());
        request.cancelOrAbort();
        assertFalse(request.getChannel().isOpen());
        assertEquals(RMIRequestState.FAILED, request.getState());

        log.info("Client channel request, cancel before send");
        ChannelRequestCancelCheckerImpl.ChannelHandler.handler = new ChannelRequestCancelCheckerImpl.ChannelHandler();
        ChannelRequestCancelCheckerImpl.startLatch = new CountDownLatch(1);
        request = client.getClient().getPort(null).createRequest(requestOperation, false, true);
        request.setListener(req -> {
            ChannelRequestCancelCheckerImpl.waitFlag = false;
            LockSupport.unpark(ChannelRequestCancelCheckerImpl.currentThread);
        });
        request.getChannel().addChannelHandler(ChannelRequestCancelCheckerImpl.ChannelHandler.handler);
        request.send();
        assertTrue(ChannelRequestCancelCheckerImpl.startLatch.await(10, TimeUnit.SECONDS));
        assertTrue(ChannelRequestCancelCheckerImpl.requestCancelLatch.await(10, TimeUnit.SECONDS));
        assertTrue(request.getChannel().isOpen());
        assertEquals(RMIRequestState.SENT, request.getState());
        assertTrue(ChannelRequestCancelCheckerImpl.currentTask.getChannel().isOpen());
        assertEquals(RMITaskState.ACTIVE, ChannelRequestCancelCheckerImpl.currentTask.getState());
        request.cancelOrAbort();
        assertFalse(request.getChannel().isOpen());
        assertEquals(RMIRequestState.FAILED, request.getState());

        log.info("Client channel request, cancel after send");
        ChannelRequestCancelCheckerImpl.ChannelHandler.handler = new ChannelRequestCancelCheckerImpl.ChannelHandler();
        ChannelRequestCancelCheckerImpl.startLatch = new CountDownLatch(1);
        ChannelRequestCancelCheckerImpl.ChannelHandler.startLatch = new CountDownLatch(1);
        ChannelRequestCancelCheckerImpl.requestCancelLatch = new CountDownLatch(1);
        request = client.getClient().getPort(null).createRequest(requestOperation, false, false);
        request.setListener(req -> {
            ChannelRequestCancelCheckerImpl.waitFlag = false;
            LockSupport.unpark(ChannelRequestCancelCheckerImpl.currentThread);
        });
        request.getChannel().addChannelHandler(ChannelRequestCancelCheckerImpl.ChannelHandler.handler);
        request.send();
        assertTrue(ChannelRequestCancelCheckerImpl.startLatch.await(10, TimeUnit.SECONDS));
        assertTrue(ChannelRequestCancelCheckerImpl.ChannelHandler.startLatch.await(10, TimeUnit.SECONDS));
        assertTrue(ChannelRequestCancelCheckerImpl.requestCancelLatch.await(10, TimeUnit.SECONDS));
        assertTrue(request.getChannel().isOpen());
        assertEquals(RMIRequestState.SENT, request.getState());
        assertTrue(ChannelRequestCancelCheckerImpl.currentTask.getChannel().isOpen());
        assertEquals(RMITaskState.ACTIVE, ChannelRequestCancelCheckerImpl.currentTask.getState());
        request.cancelOrAbort();
        assertFalse(request.getChannel().isOpen());
        assertEquals(RMIRequestState.FAILED, request.getState());
    }

    private static interface ChannelRequestCancelChecker {
        void process(boolean forServerChannelRequest, boolean cancelBeforeSend);
    }

    private static class ChannelRequestCancelCheckerImpl
        implements ChannelRequestCancelChecker, RMIChannelSupport<Void>
    {
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static class ChannelHandler extends RMIService<Void> {
            static ChannelHandler handler = new ChannelHandler();
            static volatile Thread currentThread;
            static volatile boolean waitFlag;
            static CountDownLatch startLatch = new CountDownLatch(1);
            protected ChannelHandler() {
                super(ChannelHandler.class.getName());
            }

            @Override
            public void processTask(RMITask<Void> task) {
                currentThread = Thread.currentThread();
                waitFlag = true;
                startLatch.countDown();
                while (waitFlag)
                    LockSupport.park();
            }
        }

        static volatile Thread currentThread;
        static volatile boolean waitFlag;
        static CountDownLatch startLatch = new CountDownLatch(1);
        static CountDownLatch requestCancelLatch = new CountDownLatch(1);
        static volatile RMITask<Void> currentTask;


        @Override
        public void openChannel(RMITask<Void> task) {
            Object[] params = task.getRequestMessage().getParameters().getObject();
            if ((boolean) params[0]) {
                task.getChannel().addChannelHandler(ChannelHandler.handler);
                return;
            }
            ExecutorService executor = Executors.newSingleThreadExecutor();
            task.setCancelListener(t -> executor.shutdown());
            executor.execute(() -> {
                RMIRequest<Void> request = task.getChannel().createRequest(
                    RMIOperation.valueOf(ChannelHandler.class.getName(), void.class, "method"));
                if ((boolean) params[1]) {
                    request.setListener(req -> requestCancelLatch.countDown());
                    request.cancelOrAbort();
                    return;
                }
                request.setListener(req -> {
                    ChannelHandler.waitFlag = false;
                    LockSupport.unpark(ChannelHandler.currentThread);
                    requestCancelLatch.countDown();
                });
                request.send();
                try {
                    if (ChannelHandler.startLatch.await(10, TimeUnit.SECONDS))
                        request.cancelOrAbort();
                } catch (InterruptedException e) {
                    task.completeExceptionally(e);
                }
            });
        }

        @Override
        public void process(boolean forServerChannelRequest, boolean cancelBeforeSend) {
            currentThread = Thread.currentThread();
            waitFlag = true;
            currentTask = RMITask.current(void.class);
            startLatch.countDown();
            while (waitFlag)
                LockSupport.park();
        }
    }
}
