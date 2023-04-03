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
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.task.RMITaskState;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseException;
import com.dxfeed.promise.PromiseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class RMIAsynchronousFunctionalityTest {
    private static final Logging log = Logging.getLogging(RMIAsynchronousFunctionalityTest.class);

    private RMIEndpointImpl server;
    private RMIEndpointImpl client;
    private RMIEndpointImpl privateEndpoint;
    private RMIEndpointImpl remoteEndpoint;

    private long startTime;

    private final ChannelLogic channelLogic;
    private final InitFunction initPortForOneWaySanding;

    @Parameterized.Parameters(name="type={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {TestType.REGULAR},
            {TestType.CLIENT_CHANNEL},
            {TestType.SERVER_CHANNEL}
        });
    }

    public RMIAsynchronousFunctionalityTest(TestType type) {
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
        this.channelLogic = new ChannelLogic(type, client, server, remoteEndpoint);
        switch (type) {
        case REGULAR:
            initPortForOneWaySanding = () ->
                channelLogic.clientPort = client.getClient().getPort(Subject.getSubject(AccessController.getContext()));
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
    }

    @After
    public void tearDown() {
        client.close();
        server.close();
        privateEndpoint.close();
        remoteEndpoint.close();
        if (SumWithPromiseImpl.executorService != null)
            SumWithPromiseImpl.executorService.shutdown();
        log.info(" ======================= // =====================");
        ThreadCleanCheck.after();
    }

    private void connectDefault(boolean initPorts) throws InterruptedException {
        NTU.connectPair(server, client);
        if (initPorts)
            channelLogic.initPorts();
    }

    private void connectWithForwarding(boolean initPorts) throws InterruptedException {
        NTU.connectPair(server, client);
        NTU.connectPair(remoteEndpoint, privateEndpoint);
        if (initPorts)
            channelLogic.initPorts();
    }

    @Test
    public void testCancellationRequest() throws InterruptedException {
        startTime = System.currentTimeMillis();
        RMIAsynchronousTest.CancellationCount service = RMIAsynchronousTest.CancellationCount.INSTANCE;
        NTU.exportServices(server.getServer(), service, channelLogic);
        connectDefault(true);
        Thread.sleep(100);
        service.update();
        RMIAsynchronousTest.callForward(channelLogic.clientPort, service, startTime);
        log.info("testCancellationRequest timeWork = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    @Test
    public void testForwardingUnknownService() throws InterruptedException {
        channelLogic.setForward(true);
        startTime = System.currentTimeMillis();
        server.getServer().export(privateEndpoint.getClient().getService("*"));
        connectWithForwarding(true);
        client.getClient().setRequestSendingTimeout(100);

        RMIRequest<Void> req1 =
            channelLogic.clientPort.createRequest(RMIAsynchronousTest.CancellationCount.factCancellation, 2.2);
        req1.send();
        try {
            req1.getBlocking();
            fail("req1 = " + req1);
        } catch (RMIException e) {
            if (!channelLogic.isChannel() && e.getType() != RMIExceptionType.REQUEST_SENDING_TIMEOUT)
                fail("wrong RMIException type = " + e.getType());
            if (channelLogic.isChannel() && e.getType() != RMIExceptionType.UNKNOWN_SERVICE)
                fail("wrong RMIException type = " + e.getType());
        }
        log.info("testForwardingUnknownService timeWork = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    private static final String SERVICE_NAME = "infiniteLoop";
    private static final RMIOperation<Void> DISCONNECT_OP =
        RMIOperation.valueOf(SERVICE_NAME, void.class, "DISCONNECT_OP", int.class);


    private RMIService<?> infiniteLoop = new RMIService<Object>(SERVICE_NAME) {

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        @Override
        public void processTask(RMITask<Object> task) {
            log.info("PROCESS TASK");
            task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_BEFORE_EXECUTION));
            if (task.getOperation().equals(DISCONNECT_OP)) {
                int id = (Integer) (task.getRequestMessage().getParameters().getObject()[0]);
                if (id == 1) {
                    remoteEndpoint.disconnect();
                } else if (id == 2) {
                    privateEndpoint.disconnect();
                } else {
                    task.cancel();
                }
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }
    };

    @Test
    public void testForwardingServerDisconnect() throws InterruptedException {
        channelLogic.setForward(true);
        startTime = System.currentTimeMillis();
        log.info("Client=" + client.getEndpointId() + ", Server=" + server.getEndpointId() +
            ", privateClient=" + privateEndpoint.getEndpointId() + ", remoteServer=" + remoteEndpoint.getEndpointId());
        server.getServer().export(privateEndpoint.getClient().getService("*"));
        NTU.exportServices(remoteEndpoint.getServer(), infiniteLoop, channelLogic);
        connectWithForwarding(true);
        log.info(" * * * ------------------------------------ * * * ");
        Thread.sleep(100);

        RMIRequest<Void> req1 = channelLogic.clientPort.createRequest(DISCONNECT_OP, 1);
        req1.send();
        log.info(" * * * ------------------------------------ * * * ");
        try {
            req1.getBlocking();
            fail();
        } catch (RMIException e) {
            if (channelLogic.isChannel()) {
                if (e.getType() != RMIExceptionType.DISCONNECTION && e.getType() != RMIExceptionType.CHANNEL_CLOSED)
                    fail(e.getMessage());
            } else {
                if (e.getType() != RMIExceptionType.DISCONNECTION)
                    fail(e.getMessage());
            }
        }

        server.disconnect();
        client.disconnect();
        remoteEndpoint.disconnect();
        privateEndpoint.disconnect();
        log.info("testForwardingServerDisconnect timeWork = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void testCurrentRMITask() throws InterruptedException {
        startTime = System.currentTimeMillis();
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation(new RMIAsynchronousTest.SummatorImpl(), RMICommonTest.Summator.class),
            channelLogic);
        NTU.exportServices(server.getServer(), DifferentServices.SOME_SERVICE, channelLogic);
        connectDefault(true);

        RMICommonTest.Summator summator = channelLogic.clientPort.getProxy(RMICommonTest.Summator.class);
        try {
            assertEquals(summator.sum(125, 25), 150);
        } catch (RMIException e) {
            fail(e.getMessage());
        }

        log.info("testCurrentRMITask timeWork = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    private static class ResultAfterCancellationService extends RMIService<Long> {

        private static final String NAME = "ResultAfterCancellationService";
        private static final RMIOperation<Long> OP = RMIOperation.valueOf(NAME, long.class, "OP");
        private final CountDownLatch start = new CountDownLatch(1);
        private final CountDownLatch cancel = new CountDownLatch(1);

        ResultAfterCancellationService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<Long> task) {
            task.setCancelListener(task1 -> {
                if (task1.getState() == RMITaskState.CANCELLING)
                    cancel.countDown();
                else
                    task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
            });
            if (task.getOperation().equals(OP)) {
                long time = System.currentTimeMillis();
                try {
                    start.countDown();
                    if (cancel.await(10, TimeUnit.SECONDS)) {
                        task.complete(System.currentTimeMillis() - time);
                    } else {
                        task.completeExceptionally(RMIExceptionType.CANCELLED_DURING_EXECUTION, null);
                        fail();
                    }
                } catch (InterruptedException e) {
                    task.completeExceptionally(e);
                }
            } else {
                task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResultAfterCancellation() throws InterruptedException {
        startTime = System.currentTimeMillis();
        ResultAfterCancellationService service = new ResultAfterCancellationService();
        NTU.exportServices(server.getServer(), service, channelLogic);
        connectDefault(true);
        RMIRequest<Long> req = channelLogic.clientPort.createRequest(ResultAfterCancellationService.OP);
        req.send();
        try {
            assertTrue(service.start.await(10, TimeUnit.SECONDS));
            Thread.sleep(10);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        req.cancelWithConfirmation();
        try {
            req.getBlocking();
            fail();
        } catch (RMIException e) {
            assertEquals(e.getType(), RMIExceptionType.CANCELLED_AFTER_EXECUTION);
        }
        log.info("testResultAfterCancellation timeWork = " + (System.currentTimeMillis() - startTime));
    }

    // --------------------------------------------------

    private static class CalculatorWithWaitService extends RMIService<Double> {

        RMIService<Double> calculatorService = new DifferentServices.CalculatorService();
        private static final RMIOperation<Void> ERROR =
            RMIOperation.valueOf(DifferentServices.CALCULATOR_SERVICE.getServiceName(), void.class, "ERROR");
        private static volatile CountDownLatch start = new CountDownLatch(1);
        private static volatile long taskDuration = 0;

        CalculatorWithWaitService() {
            super(DifferentServices.CALCULATOR_SERVICE.getServiceName());
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        @Override
        public void processTask(RMITask<Double> task) {
            start.countDown();
            if (task.getOperation().equals(ERROR)) {
                task.setCancelListener(task1 -> task1.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION));
                task.completeExceptionally(new IOException());
            } else {
                try {
                    sleep(taskDuration);
                } catch (InterruptedException e) {
                    task.completeExceptionally(e);
                }
                calculatorService.processTask(task);
            }
        }

        private void sleep(long millis) throws InterruptedException {
            if (millis == 0)
                return;
            long deadline = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
                RMITask<String> current = RMITask.current(String.class);
                if (current.getState().isCompletedOrCancelling()) {
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRequestPromise() throws InterruptedException {
        startTime = System.currentTimeMillis();
        NTU.exportServices(server.getServer(), new CalculatorWithWaitService(), channelLogic);
        connectDefault(true);
        RMIRequest<Double> sum;
        PromiseHandlerTest promiseHandler;
        sum = channelLogic.clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 35.6, 42.4);
        promiseHandler = new PromiseHandlerTest(sum);
        sum.getPromise().whenDone(promiseHandler);
        CalculatorWithWaitService.taskDuration = 0;
        sum.send();
        try {
            assertEquals(sum.getBlocking(), (Double) 78.0);
            assertTrue(promiseHandler.done.await(10, TimeUnit.SECONDS));
        } catch (RMIException e) {
            e.printStackTrace();
        }

        log.info("-------------------------------");

        CalculatorWithWaitService.start = new CountDownLatch(1);
        CalculatorWithWaitService.taskDuration = 1_000;
        sum = channelLogic.clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 35.6, 42.4);
        promiseHandler = new PromiseHandlerTest(sum);
        sum.getPromise().whenDone(promiseHandler);
        sum.send();
        assertTrue(CalculatorWithWaitService.start.await(10, TimeUnit.SECONDS));
        CalculatorWithWaitService.start = new CountDownLatch(1);
        sum.cancelOrAbort();
        try {
            sum.getBlocking();
            fail();
        } catch (RMIException e) {
            if (!e.getType().isCancelled())
                fail(e.toString());
            assertTrue(promiseHandler.done.await(10, TimeUnit.SECONDS));
        }

        log.info("-------------------------------");
        sum = channelLogic.clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 35.6, 42.4);
        promiseHandler = new PromiseHandlerTest(sum);
        sum.getPromise().whenDone(promiseHandler);
        sum.send();
        assertTrue(CalculatorWithWaitService.start.await(10, TimeUnit.SECONDS));
        sum.getPromise().cancel();
        try {
            sum.getBlocking();
            fail();
        } catch (RMIException e) {
            if (!e.getType().isCancelled())
                fail(e.getMessage());
            assertTrue(promiseHandler.done.await(10, TimeUnit.SECONDS));
        }

        RMIRequest<Void> er = channelLogic.clientPort.createRequest(CalculatorWithWaitService.ERROR);
        promiseHandler = new PromiseHandlerTest(er);
        er.getPromise().whenDone(promiseHandler);
        er.send();
        try {
            er.getBlocking();
            fail();
        } catch (RMIException e) {
            if (e.getType() == RMIExceptionType.APPLICATION_ERROR)
                assertEquals(e.getCause().getClass(), IOException.class);
            else
                fail(e.getMessage());
            assertTrue(promiseHandler.done.await(10, TimeUnit.SECONDS));
        }
        log.info("testRequestPromise timeWork = " + (System.currentTimeMillis() - startTime));
    }

    private static class PromiseHandlerTest implements PromiseHandler<Object> {
        private final RMIRequest<?> sum;
        private final CountDownLatch done = new CountDownLatch(1);

        private PromiseHandlerTest(RMIRequest<?> sum) {
            this.sum = sum;
        }

        @Override
        public void promiseDone(Promise<?> promise) {
            done.countDown();
            if (sum.getState() == RMIRequestState.SUCCEEDED) {
                assertTrue(promise.hasResult());
                assertEquals(sum.getNonBlocking(), promise.getResult());
            } else if (sum.getState() == RMIRequestState.FAILED) {
                assertTrue(promise.isDone() && !promise.hasResult());
                RMIException e = sum.getException();

                switch (e.getType()) {
                case CANCELLED_DURING_EXECUTION:
                case CANCELLED_BEFORE_EXECUTION:
                    assertTrue(promise.isCancelled());
                    break;
                case APPLICATION_ERROR:
                    assertEquals(sum.getException().getCause(), promise.getException());
                    break;
                default:
                    assertEquals(sum.getException(), promise.getException());
                    break;
                }
            }
        }
    }

    // --------------------------------------------------

    @SuppressWarnings("unused")
    interface SumWithPromise {
        Promise<Integer> sum(int a, int b);

        Promise<Integer> fastSum(int a, int b);

        Promise<Integer> closeRequestSum(int a, int b);

        Promise<Integer> failSum(int a, int b);
    }

    private static class SumWithPromiseImpl implements SumWithPromise {

        private static final CountDownLatch startedCloseRequestSum = new CountDownLatch(1);
        private static final CountDownLatch failedCloseRequestSum = new CountDownLatch(1);
        private static final long SLEEP = 50;
        private static ExecutorService executorService;

        private SumWithPromiseImpl() {
            executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "RMIAsyncTest-sum-promise"));
        }

        @Override
        public Promise<Integer> sum(final int a, final int b) {
            final Promise<Integer> promise = new Promise<>();
            executorService.execute(() -> {
                try {
                    Thread.sleep(SLEEP);
                    promise.complete(a + b);
                } catch (InterruptedException e) {
                    promise.completeExceptionally(e);
                }
            });
            return promise;
        }

        @Override
        public Promise<Integer> fastSum(int a, int b) {
            return Promise.completed(a + b);
        }

        @Override
        public Promise<Integer> closeRequestSum(int a, int b) {
            startedCloseRequestSum.countDown();
            Promise<Integer> promise = new Promise<>();
            promise.whenDone(promise1 -> {
                if (promise1.hasException())
                    failedCloseRequestSum.countDown();
                else
                    fail();
            });
            return promise;
        }

        @Override
        public Promise<Integer> failSum(int a, int b) {
            Promise<Integer> promise = new Promise<>();
            promise.completeExceptionally(new ArrayIndexOutOfBoundsException());
            return promise;
        }
    }

    @Test
    public void testReturnedPromise() throws InterruptedException {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new SumWithPromiseImpl(), SumWithPromise.class), channelLogic);
        connectDefault(true);
        SumWithPromise impl = channelLogic.clientPort.getProxy(SumWithPromise.class);
        assertEquals((int) impl.sum(100, 254).await(10, TimeUnit.SECONDS), 354);

        assertEquals((int) impl.fastSum(59, 241).await(10, TimeUnit.SECONDS), 300);

        try {
            impl.fastSum(59, 241).await(10, TimeUnit.SECONDS);
        } catch (PromiseException t) {
            assertTrue(t.getCause() instanceof ArrayIndexOutOfBoundsException);
        }

        impl.closeRequestSum(100, 1);
        assertTrue(SumWithPromiseImpl.startedCloseRequestSum.await(10, TimeUnit.SECONDS));

        client.disconnect();
        assertTrue(SumWithPromiseImpl.failedCloseRequestSum.await(10, TimeUnit.SECONDS));
    }

    // --------------------------------------------------

    @Test
    public void testOneWaySending() throws InterruptedException {
        channelLogic.setForward(true);
        RMIFunctionalityTest.CompletingPing pingService = new RMIFunctionalityTest.CompletingPing();
        NTU.exportServices(remoteEndpoint.getServer(),
            new RMIServiceImplementation<>(pingService, RMICommonTest.Ping.class), channelLogic);
        server.getServer().export(privateEndpoint.getClient().getService("*"));

        connectWithForwarding(false);
        initPortForOneWaySanding.apply();
        RMIOperation<Void> operation;
        try {
            operation = RMIOperation.valueOf(RMICommonTest.Ping.class, RMICommonTest.Ping.class.getMethod("ping"));
        } catch (NoSuchMethodException e) {
            fail(e.getMessage());
            return;
        }
        RMIRequest<Void> request =
            channelLogic.clientPort.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY, operation));
        request.send();
        pingService.waitForCompletion();

        assertEquals(1, pingService.rmiTasks.size());
        WeakReference<RMITask> taskRef = pingService.rmiTasks.remove();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (taskRef.get() != null && System.nanoTime() < deadline ) {
            System.gc();
            Thread.sleep(100);
        }
        assertNull("RMI task leaked", taskRef.get());
    }
}
