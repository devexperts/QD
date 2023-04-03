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
import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.task.RMITaskState;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class RMIAsynchronousTest {
    @Rule
    public Timeout globalTimeout= new Timeout(60, TimeUnit.SECONDS);

    private static final Logging log = Logging.getLogging(RMIAsynchronousTest.class);

    private RMIClientPort clientPort;
    private RMIEndpointImpl server;
    private RMIEndpointImpl client;
    private RMIEndpointImpl privateEndpoint;
    private RMIEndpointImpl remoteEndpoint;

    private long startTime;
    private ExecutorService executor;

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
        log.info("server = " + server);
        log.info("client = " + client);
    }

    @After
    public void tearDown() {
        client.close();
        server.close();
        if (privateEndpoint != null)
            privateEndpoint.close();
        if (remoteEndpoint != null)
            remoteEndpoint.close();
        log.info(" ======================= // =====================");
        ThreadCleanCheck.after();
    }

    private void initRemote() {
        privateEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("privateClient")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        remoteEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("remoteServer")
            .withSide(RMIEndpoint.Side.SERVER)
            .build();
        log.info("privateEndpoint = " + privateEndpoint);
        log.info("remoteEndpoint = " + remoteEndpoint);
    }

    private void connectDefault(boolean initPorts) {
        NTU.connectPair(server, client);
        if (initPorts)
            clientPort = client.getClient().getPort(null);
    }

    private void connectWithForwarding(boolean initPorts) {
        NTU.connectPair(server, client);
        NTU.connectPair(remoteEndpoint, privateEndpoint);
        if (initPorts)
            clientPort = client.getClient().getPort(null);
    }

    // --------------------------------------------------

    @SuppressWarnings({"WaitNotInLoop", "NakedNotify"})
    private static class WaitService extends RMIService<Object> {

        private static final String NAME = "waitService";
        private static final RMIOperation<Long> START_WAIT = RMIOperation.valueOf(NAME, long.class, "START_WAIT");
        private static final RMIOperation<Void> FINISH_WAIT = RMIOperation.valueOf(NAME, void.class, "FINISH_WAIT");

        private final Object lock = new Object();
        private final CountDownLatch startedWait = new CountDownLatch(1);

        private WaitService() {
            super(NAME);
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        @Override
        public void processTask(RMITask<Object> task) {
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION));
            if (task.getOperation().equals(START_WAIT)) {
                long startTime = System.currentTimeMillis();
                synchronized (lock) {
                    try {
                        startedWait.countDown();
                        lock.wait();
                    } catch (InterruptedException e) {
                        fail(e.getMessage());
                    }
                }
                task.complete(System.currentTimeMillis() - startTime);
            } else if (task.getOperation().equals(FINISH_WAIT)) {
                synchronized (lock) {
                    lock.notifyAll();
                }
                task.complete(null);
            } else
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
        }
    }

    //only for RMIClient
    @Test
    public void testAsynchronousTask() throws InterruptedException {
        executor = Executors.newFixedThreadPool(4, r -> new Thread(r, "RMIAsyncTest-async-task"));
        server.getServer().setDefaultExecutor(executor);
        startTime = System.currentTimeMillis();
        log.info("testAsynchronousTask");
        WaitService waitService = new WaitService();
        server.getServer().export(waitService);
        connectDefault(true);
        checkWaitedService(waitService);
        executor.shutdown();
    }

    //only for RMIClient
    @Test
    public void testForwardingRequest() throws InterruptedException {
        initRemote();
        startTime = System.currentTimeMillis();
        log.info("testForwardingRequest");
        WaitService waitService = new WaitService();
        server.getServer().export(privateEndpoint.getClient().getService("*"));
        remoteEndpoint.getServer().export(waitService);
        connectWithForwarding(true);
//      Thread.sleep(1000);
        checkWaitedService(waitService);
    }

    @SuppressWarnings("unchecked")
    private void checkWaitedService(WaitService waitService) throws InterruptedException {
        RMIRequest<Long> req1;
        RMIRequest<Void> req2;
        long startedTime;
        long waitedTime = 0;
        req1 = clientPort.createRequest(WaitService.START_WAIT);
        startedTime = System.currentTimeMillis();
        req1.send();
        assertTrue(waitService.startedWait.await(10, TimeUnit.SECONDS));
        log.info("-----------------------------");
        req2 = clientPort.createRequest(WaitService.FINISH_WAIT);
        req2.send();
        try {
            req2.getBlocking();
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        try {
            req1.getBlocking();
            waitedTime = System.currentTimeMillis() - startedTime;
        } catch (RMIException e) {
            fail(e.getMessage());
        }
        log.info("testWaitingService timeWork on Server = " + req1.getNonBlocking() +
            ", timeWork on Client = " + waitedTime);
        assertTrue(req1.getNonBlocking() <= waitedTime);
    }

    // --------------------------------------------------

    static class CancellationCount extends RMIService<Double> {

        static final CancellationCount INSTANCE = new CancellationCount();
        static final String NAME = "CancellationCount";

        static final RMIOperation<Void> factCancellation =
            RMIOperation.valueOf(NAME, void.class, "factCancellation", double.class);

        private double result = 0;
        private final Object lock = new Object();
        private CountDownLatch startedWait = new CountDownLatch(1);
        private CountDownLatch endCalculate = new CountDownLatch(1);
        private CountDownLatch cancelled = new CountDownLatch(1);

        CancellationCount() {
            super(NAME);
        }

        void update() {
            startedWait = new CountDownLatch(1);
            endCalculate = new CountDownLatch(1);
            cancelled = new CountDownLatch(1);
        }


        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        @Override
        public void processTask(RMITask<Double> task) {
            if (factCancellation.equals(task.getOperation())) {
                task.setCancelListener(task1 -> {
                    try {
                        if (task1.getState() == RMITaskState.CANCELLING) {
                            synchronized (lock) {
                                lock.notifyAll();
                            }
                            try {
                                if (endCalculate.await(10, TimeUnit.SECONDS)) {
                                    Throwable e = new Throwable("result = " + result);
                                    log.info("Throwable e = " + e.getMessage());
                                    task1.completeExceptionally(e);
                                } else {
                                    task1.completeExceptionally(RMIExceptionType.CANCELLED_DURING_EXECUTION, null);
                                    fail();
                                }
                            } catch (InterruptedException e) {
                                task1.completeExceptionally(e);
                            }
                        } else {
                            task1.cancel(RMIExceptionType.CANCELLED_AFTER_EXECUTION);
                        }
                    } finally {
                        cancelled.countDown();
                    }
                });
                synchronized (lock) {
                    try {
                        startedWait.countDown();
                        lock.wait();
                    } catch (InterruptedException e) {
                        task.completeExceptionally(e);
                    }
                }
                result = fact((Double) task.getRequestMessage().getParameters().getObject()[0]);
                endCalculate.countDown();
                synchronized (lock) {
                    lock.notifyAll();
                }
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }

        private double fact(double n) {
            if (Math.abs(n - 1.0) < 1e-5)
                return 1;
            else
                return n * fact(n - 1);
        }

    }

    @Test
    public void testForwardingCancellationRequest() throws InterruptedException {
        initRemote();
        startTime = System.currentTimeMillis();
        CancellationCount service = CancellationCount.INSTANCE;
        remoteEndpoint.getServer().export(service);
        server.getServer().export(privateEndpoint.getClient().getService("*"));
        connectWithForwarding(true);
        service.update();
        log.info("client = " + client + ", server = " + server + ", private = " + privateEndpoint +
            ", remote = " + remoteEndpoint);

        log.info(" -------------------------------------------------------------- ");
        callForward(clientPort, service, startTime);
        log.info("testForwardingCancellationRequest timeWork = " + (System.currentTimeMillis() - startTime));
    }

    @SuppressWarnings("unchecked")
    static void callForward(RMIClientPort clientPort, CancellationCount service, long startTime)
        throws InterruptedException
    {
        RMIRequest<Void> req1 = clientPort.createRequest(CancellationCount.factCancellation, 10.0);
        req1.send();
        assertTrue(service.startedWait.await(10, TimeUnit.SECONDS));
        Thread.sleep(10);
        log.info("-----------------------------------------");
        req1.cancelWithConfirmation();
        assertTrue(service.cancelled.await(10, TimeUnit.SECONDS));
        try {
            req1.getBlocking();
            fail();
        } catch (RMIException e) {
            log.info("req1 ex= " + req1.getException());
        }
        Double res = Double.parseDouble(req1.getException().getCause().getMessage().substring(9));
        assertEquals(res, (Double) 3628800.0);
        log.info("testForwardingCancellationRequest timeWork3 = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    private interface CountService {
        int getCount();

        int getIncrementParameter();
    }

    private static class FirstCount implements CountService {
        final AtomicInteger count = new AtomicInteger();
        final static int INCREMENT_PARAMETER = 1;

        @Override
        public int getCount() {
            return count.getAndIncrement();
        }

        @Override
        public int getIncrementParameter() {
            return INCREMENT_PARAMETER;
        }
    }

    private static class SecondCount implements CountService {
        final AtomicInteger count = new AtomicInteger();
        final static int INCREMENT_PARAMETER = 2;

        @Override
        public int getCount() {
            return count.getAndAdd(2);
        }

        @Override
        public int getIncrementParameter() {
            return INCREMENT_PARAMETER;
        }
    }

    public static class SummatorImpl implements RMICommonTest.Summator {

        @Override
        public int sum(int a, int b) {
            RMITask.current(int.class).complete(a + b);
            return 0;
        }

        @Override
        public int getOperationsCount() {
            return 0;
        }
    }

    @Test
    public void testClientServiceWithServiceFilter() {
        initRemote();
        log.info("---- testClientServiceWithServiceFilter ---- ");
        connectWithForwarding(true);

        remoteEndpoint.getServer().export(
            new RMIServiceImplementation<>(new FirstCount(), CountService.class, FirstCount.class.getSimpleName()));
        remoteEndpoint.getServer().export(
            new RMIServiceImplementation<>(new SecondCount(), CountService.class, SecondCount.class.getSimpleName()));
        remoteEndpoint.getServer().export(
            new RMIServiceImplementation<>(new SummatorImpl(), RMICommonTest.Summator.class, "Summator"));
        server.getServer().export(privateEndpoint.getClient().getService("*Count"));

        CountService proxy = client.getClient().getProxy(CountService.class, FirstCount.class.getSimpleName());
        assertEquals(proxy.getCount(), 0);
        assertEquals(proxy.getIncrementParameter(), 1);
        assertEquals(proxy.getCount(), 1);

        log.info("---");

        proxy = client.getClient().getProxy(CountService.class, SecondCount.class.getSimpleName());
        assertEquals(proxy.getCount(), 0);
        assertEquals(proxy.getIncrementParameter(), 2);
        assertEquals(proxy.getCount(), 2);

        log.info("---");

        // TODO: lowering this time wouldn't help because RMI checks for request timeouts no faster than once per second
        client.getClient().setRequestSendingTimeout(300);
        try {
            client.getClient().getProxy(RMICommonTest.Summator.class, "Summator").sum(10, 30);
            fail();
        } catch (RMIException e) {
            assertEquals(e.getType(), RMIExceptionType.REQUEST_SENDING_TIMEOUT);
        }
    }

// --------------------------------------------------

    private static class MonitoringOfTasksService extends RMIService<Long> {

        private static final String NAME = "MonitoringOfTasksService";
        private static final RMIOperation<Long> MONITORING = RMIOperation.valueOf(NAME, long.class, "MONITORING");
        private static final RMIOperation<Void> EXECUTE = RMIOperation.valueOf(NAME, void.class, "EXECUTE", long.class);

        private static final CountDownLatch START_MONITORING = new CountDownLatch(1);
        private static final CountDownLatch START_EXECUTE = new CountDownLatch(1);
        private static final CountDownLatch COMPLETE_EXECUTE = new CountDownLatch(1);

        private volatile long start = 0L;
        private volatile RMITask<Long> mon;

        MonitoringOfTasksService() {
            super(NAME);
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        @Override
        public void processTask(RMITask<Long> task) {
            if (task.getOperation().equals(MONITORING)) {
                START_MONITORING.countDown();
                mon = task;
                task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION));
                try {
                    if (!START_EXECUTE.await(10, TimeUnit.SECONDS)) {
                        task.completeExceptionally(RMIExceptionType.CANCELLED_DURING_EXECUTION, null);
                    }
                    if (!COMPLETE_EXECUTE.await(10, TimeUnit.SECONDS)) {
                        task.completeExceptionally(RMIExceptionType.CANCELLED_DURING_EXECUTION, null);
                    }
                } catch (InterruptedException e) {
                    task.completeExceptionally(RMIExceptionType.APPLICATION_ERROR, e);
                }
            } else if (task.getOperation().equals(EXECUTE)) {
                task.setCancelListener(task1 -> {
                    log.info("MON = " + mon);
                    if (task1.getState() == RMITaskState.SUCCEEDED) {
                        mon.complete(System.currentTimeMillis() - start);
                    } else {
                        mon.completeExceptionally((Exception)
                            (task1.getResponseMessage().getMarshalledResult().getObject()));
                    }
                    COMPLETE_EXECUTE.countDown();
                });
                START_EXECUTE.countDown();
                start = System.currentTimeMillis();
                try {
                    sleep((Long) task.getRequestMessage().getParameters().getObject()[0], task);
                } catch (InterruptedException e) {
                    task.completeExceptionally(e);
                }
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }

        private void sleep(long millis, RMITask task) throws InterruptedException {
            if (millis == 0)
                return;
            long deadline = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
                if (task.getState().isCompletedOrCancelling()) {
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTaskCancelListenerWithAbort() throws InterruptedException {
        startTime = System.currentTimeMillis();
        server.getServer().export(new MonitoringOfTasksService());
        connectDefault(true);
        RMIRequest<Long> mon = clientPort.createRequest(MonitoringOfTasksService.MONITORING);
        mon.send();
        assertTrue(MonitoringOfTasksService.START_MONITORING.await(10, TimeUnit.SECONDS));
        RMIRequest<Void> exec = clientPort.createRequest(MonitoringOfTasksService.EXECUTE, 3000L);
        exec.send();
        assertTrue(MonitoringOfTasksService.START_EXECUTE.await(10, TimeUnit.SECONDS));
        Thread.sleep(30);

        log.info("---------------------------------");
        exec.cancelOrAbort();
        try {
            exec.getBlocking();
            fail();
        } catch (RMIException e) {
            if (!e.getType().isCancelled())
                fail(e.toString());
        }
        try {
            mon.getBlocking();
            fail();
        } catch (RMIException e) {
            Exception exception = mon.getException();
            log.info("exception = " + exception);
            if (!((RMIException) exception.getCause()).getType().isCancelled())
                fail(exception.toString());
        }
        log.info("testTaskCancelListenerWithAbort timeWork = " + (System.currentTimeMillis() - startTime));
    }
}
