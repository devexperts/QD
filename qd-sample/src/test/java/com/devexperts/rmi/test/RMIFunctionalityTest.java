/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;

import com.devexperts.logging.Logging;
import com.devexperts.qd.samplecert.SampleCert;
import com.devexperts.rmi.*;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.samples.DifferentServices;
import com.devexperts.rmi.task.*;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

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

    @Parameterized.Parameters(name="type={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
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
        client.setRequestRunningTimeout(20000); // to make sure tests don't run forever
        this.channelLogic = new ChannelLogic(type, client, server, null);
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

    private void connectDefault(int port) {
        NTU.connect(server, ":" + NTU.port(port));
        NTU.connect(client, NTU.LOCAL_HOST + ":" + NTU.port(port));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    private void connectWith(String with, int port) {
        NTU.connect(server, with + (with.equalsIgnoreCase("tls") ? "[isServer=true]" : "") + "+:" + NTU.port(port));
        NTU.connect(client, with + "+" + NTU.LOCAL_HOST + ":" + NTU.port(port));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

        // --------------------------------------------------

    private void implTestSummator(int scale) {
        RMICommonTest.Summator summator = channelLogic.clientPort.getProxy(RMICommonTest.Summator.class, "summator");
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
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
        connectDefault(1);
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
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);

        connectWith("tls", 5);

        implTestSummator(1);
        client.disconnect();
        client.setTrustManager(null);
        assertTrue(checked[0]);
        System.setProperties(props);
    }

    @Test
    public void testWithTLS() throws InterruptedException {
        //test default tls
        Properties props = System.getProperties();
        SampleCert.init();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
//
        NTU.connect(server,  "tls[isServer]+:" + NTU.port(7));
        NTU.connect(client,    "" + NTU.LOCAL_HOST + ":" + NTU.port(7) + "[tls=true]" );
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        implTestSummator(1);
        server.disconnect();
        client.disconnect();
        Thread.sleep(500);

        System.out.println("-----------------------");

        System.getProperties().setProperty("com.devexperts.connector.codec.ssl.protocols", "TLSv1.1");
        NTU.connect(server, "tls[isServer,protocols=TLSv1.1;TLSv1.2]+:" + NTU.port(7));
        NTU.connect(client, "tls+" + NTU.LOCAL_HOST + ":" + NTU.port(7));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        implTestSummator(2);
        server.disconnect();
        client.disconnect();
        Thread.sleep(500);

        System.out.println("-----------------------");

        //test tls versions
        System.out.println("test tls versions");
        if (channelLogic.type != TestType.REGULAR) {
            System.setProperties(props);
            return;
        }
        CountDownLatch connectedVersion = new CountDownLatch(2);
        CountDownLatch notConnectedVersion = new CountDownLatch(2);
        client.addEndpointListener(endpoint -> {
            if (endpoint.isConnected()) {
                connectedVersion.countDown();
            } else {
                notConnectedVersion.countDown();
            }
        });
        server.addEndpointListener(endpoint -> {
            if (endpoint.isConnected()) {
                connectedVersion.countDown();
            } else {
                notConnectedVersion.countDown();
            }
        });
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
        client.getClient().setRequestSendingTimeout(1000);
        NTU.connect(server, "tls[isServer,protocols=TLSv1.2]+:" + NTU.port(7));
        NTU.connect(client, "tls+" + NTU.LOCAL_HOST + ":" + NTU.port(7));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(connectedVersion.await(10, TimeUnit.SECONDS));
        RMICommonTest.Summator summator = channelLogic.clientPort.getProxy(RMICommonTest.Summator.class, "summator");
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
    }

    @Test
    public void testWithSSL() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
        NTU.connect(server, "ssl[isServer=true," + SampleCert.KEY_STORE_CONFIG + "]+:" + NTU.port(9));
        NTU.connect(client, "ssl[" + SampleCert.TRUST_STORE_CONFIG + "]+" + NTU.LOCAL_HOST + ":" + NTU.port(9));
        try {
            channelLogic.initPorts();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        implTestSummator(1);
    }

    @Test
    public void testWithZLIB() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
        connectWith("zlib", 11);
        implTestSummator(1);
    }

    @Test
    public void testWithXOR() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SummatorImpl(), RMICommonTest.Summator.class, "summator"), channelLogic);
        connectWith("xor", 13);
        implTestSummator(1);
    }

// --------------------------------------------------

    private static final StackTraceElement RMI_LAYER_SEPARATOR_FRAME = new StackTraceElement("com.devexperts.rmi", "<REMOTE-METHOD-INVOCATION>", null, -1); // copied from RMIInvocationHandler

    @Test
    public void testErrorThrowing() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new AIOOBEThrower(), ErrorThrower.class), channelLogic);
        connectDefault(15);

        ErrorThrower thrower = channelLogic.clientPort.getProxy(ErrorThrower.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();

            assertEquals(AIOOBEThrower.class.getName(), stackTrace[0].getClassName());
            assertEquals("throwError", stackTrace[0].getMethodName());

            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[1]);


            assertEquals(this.getClass().getName().substring(0, stackTrace[2].getClassName().length()),
                stackTrace[2].getClassName());
            assertEquals("testErrorThrowing", stackTrace[2].getMethodName());

            assertStackTraceIsLocal(stackTrace);
        } catch (Throwable e) {
            log.info("----------------");
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testErrorThrowingWithoutDeclare() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new AIOOBEThrowerWithoutDeclare(), WithoutDeclareError.class), channelLogic);
        connectDefault(17);

        WithoutDeclareError thrower = channelLogic.clientPort.getProxy(WithoutDeclareError.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();

            assertEquals(AIOOBEThrowerWithoutDeclare.class.getName(), stackTrace[0].getClassName());
            assertEquals("throwError", stackTrace[0].getMethodName());

            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[1]);

            assertEquals(this.getClass().getName().substring(0, stackTrace[2].getClassName().length()),
                stackTrace[2].getClassName());
            assertEquals("testErrorThrowingWithoutDeclare", stackTrace[2].getMethodName());

            assertStackTraceIsLocal(stackTrace);
        } catch (Throwable e) {
            fail(e.getMessage());
        }
    }

    interface WithoutDeclareError {
        void throwError();
    }

    public static class AIOOBEThrowerWithoutDeclare implements WithoutDeclareError {
        @Override
        public void throwError() {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    interface ErrorThrower {
        void throwError() throws Exception;
    }

    public static class AIOOBEThrower implements ErrorThrower {
        @Override
        public void throwError() throws Exception {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    private static void assertStackTraceIsLocal(StackTraceElement[] examine) {
        StackTraceElement[] reference = Thread.currentThread().getStackTrace();

        int pos = 0;
        while (!reference[pos].getMethodName().equals("assertStackTraceIsLocal"))
            pos++;

        int n = reference.length - pos - 2;

        for (int i = 0; i < n; i++)
            assertEquals(reference[reference.length - 1 - i], examine[examine.length - 1 - i]);
    }

// --------------------------------------------------

    @Test
    public void testServerDisconnect() {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.ServerDisconnectingInfiniteLooper(server), RMICommonTest.InfiniteLooper.class), channelLogic);
        connectDefault(21);
        RMICommonTest.InfiniteLooper looper = channelLogic.clientPort.getProxy(RMICommonTest.InfiniteLooper.class);
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

    @Test
    public void testRMIExceptionDeclared() {
        RemoteThrower impl = new RemoteThrower();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, Declared.class, "declared"), channelLogic);
        connectDefault(23);
        Declared de = channelLogic.clientPort.getProxy(Declared.class, "declared");
        try {
            de.generateDeclared();
            fail();
        } catch (Throwable t) {
            checkRMIException(t);
        }
    }

    @Test
    public void testRMIExceptionUndeclared() {
        RemoteThrower impl = new RemoteThrower();

        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, Undeclared.class, "undeclared"), channelLogic);
        connectDefault(25);
        Undeclared un = channelLogic.clientPort.getProxy(Undeclared.class, "undeclared");
        try {
            un.generateUndeclared();
            fail();
        } catch (Throwable t) {
            if (!(t instanceof RuntimeRMIException)) {
                fail(t.getMessage());
            }
            checkRMIException(t.getCause());
        }
    }

    private void checkRMIException(Throwable t) {
        if (!(t instanceof RMIException))
            fail(t.toString());
        assertEquals(RMIExceptionType.DISCONNECTION, ((RMIException) t).getType());
    }

    interface Declared {
        void generateDeclared() throws RMIException;
    }

    interface Undeclared {
        void generateUndeclared();
    }

    public class RemoteThrower implements Declared, Undeclared {
        @Override
        public void generateDeclared() throws RMIException {
            client.disconnect();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}
        }

        @Override
        public void generateUndeclared() {
            client.disconnect();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}
        }
    }

// --------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void testCancelOrAbort() {
        log.info(" ---- testCancelOrAbort ---- ");
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new RMICommonTest.SimpleInfiniteLooper(), RMICommonTest.InfiniteLooper.class), channelLogic);
        channelLogic.clientPort = client.getClient().getPort(Subject.getSubject(AccessController.getContext()));
        connectDefault(27);
        final int n = 1000;
        RMIRequest<Void>[] requests = (RMIRequest<Void>[]) new RMIRequest[n];
        RMIOperation<Void> operation = null;
        try {
            operation = RMIOperation.valueOf(RMICommonTest.InfiniteLooper.class, RMICommonTest.InfiniteLooper.class.getMethod("loop"));
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
        boolean done = false;

        @Override
        public synchronized void ping() {
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
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, RMICommonTest.Ping.class), channelLogic);
        NTU.connect(server, ":" + NTU.port(31));
        NTU.connect(client, NTU.LOCAL_HOST + ":" + NTU.port(31));
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

    private static final int LARGE_SIZE = 100000;
    private static final int SMALL_SIZE = 100;

    interface LargeRequestProcessor {
        public int process(byte[] data) throws RMIException;
    }

    public static class LargeRequestProcessorImpl1 implements LargeRequestProcessor {
        private volatile boolean largeReceived = false;

        @Override
        public int process(byte[] data) {
            String str = Arrays.toString(data);
            log.info("process data = " + str.substring(0, str.length() < 1000 ? str.length() : 1000));
            boolean isLarge = data.length >= LARGE_SIZE;
            if (isLarge)
                largeReceived = true;
            else if (largeReceived)
                throw new AssertionError("Received large request before small");
            return Arrays.hashCode(data);
        }
    }

    @Test
    public void testLargeRequests() throws Exception {
        setSingleThreadExecutorForLargeMethods();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new LargeRequestProcessorImpl1(), LargeRequestProcessor.class), channelLogic);
        connectDefault(16);
        RMIOperation<Integer> processOp = RMIOperation.valueOf(LargeRequestProcessor.class, LargeRequestProcessor.class.getMethod("process", byte[].class));

        byte[] smallData = new byte[SMALL_SIZE];
        byte[] largeData = new byte[LARGE_SIZE];
        ArrayList<RMIRequest<Integer>> requests = new ArrayList<>();

        Random rnd = new Random(6409837516922350791L);
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++) {
            rnd.nextBytes(smallData);
            requests.add(channelLogic.clientPort.createRequest(processOp, (Object) smallData));
        }
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++) {
            rnd.nextBytes(largeData);
            requests.add(channelLogic.clientPort.createRequest(processOp, (Object) largeData));
        }


        requests.forEach(RMIRequest::send);


        for (RMIRequest<Integer> request : requests)
            assertEquals(Arrays.hashCode((byte[]) request.getParameters()[0]), (int) request.getBlocking());
    }

// --------------------------------------------------

    @SuppressWarnings("unused")
    public static interface LargeResultGenerator {
        public byte[] getResult(boolean isLarge);
    }

    public static class LargeResultGeneratorImpl implements LargeResultGenerator {

        private static final Random rnd = new Random(1640983751692235079L);

        @Override
        public byte[] getResult(final boolean isLarge) {
            RMITask.current().setCancelListener(task -> log.info("completed!  " + isLarge));
            log.info("... gerResult(" + isLarge + ")");
            byte[] result = new byte[isLarge ? LARGE_SIZE : SMALL_SIZE];
            rnd.nextBytes(result);
            return result;
        }
    }

    @Test
    public void testLargeResponses() throws Exception {
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new LargeResultGeneratorImpl(), LargeResultGenerator.class), channelLogic);
        NTU.connect(server, "shaped[throughput=1000]+:" + NTU.port(37));
        NTU.connect(client, "shaped[throughput=1000]+" + NTU.LOCAL_HOST + ":" + NTU.port(37));
        setSingleThreadExecutorForLargeMethods();
        channelLogic.initPorts();
        RMIOperation<byte[]> getResOp = RMIOperation.valueOf(LargeResultGenerator.class, LargeResultGenerator.class.getMethod("getResult", boolean.class));
        ArrayList<RMIRequest<byte[]>> requests = new ArrayList<>();
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++)
            requests.add(channelLogic.clientPort.createRequest(getResOp, true));
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++)
            requests.add(channelLogic.clientPort.createRequest(getResOp, false));

        final boolean[] failed = new boolean[1];

        RMIRequestListener listener = new RMIRequestListener() {
            private volatile boolean largeReceived = false;

            @Override
            public void requestCompleted(RMIRequest<?> request) {
                try {
                    byte[] result = (byte[]) request.getBlocking();
                    boolean isLarge = result.length >= LARGE_SIZE;
                    if (isLarge)
                        largeReceived = true;
                    else if (largeReceived)
                        failed[0] = true;
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        };

        for (RMIRequest<?> request : requests) {
            request.setListener(listener);
            request.send();
        }

        for (RMIRequest<?> request : requests)
            request.getBlocking();
        assertFalse("Received large response before small", failed[0]);
    }

// --------------------------------------------------

    public static class LargeRequestProcessorImpl2 implements LargeRequestProcessor {
        @Override
        public int process(byte[] data) {
            if (data.length >= LARGE_SIZE)
                try {
                    Thread.sleep(1000); // make sure we have time to cancel request
                } catch (InterruptedException e) {
                    // nothing
                }
            return Arrays.hashCode(data);
        }
    }

    @Test
    public void testLargeRequestCancellations() throws Exception {
        log.info(" ---- testLargeRequestCancellations ---- ");
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new LargeRequestProcessorImpl2(), LargeRequestProcessor.class), channelLogic);
        NTU.connect(server, "shaped[throughput=1000]+:" + NTU.port(39));
        NTU.connect(client, "shaped[throughput=1000]+" + NTU.LOCAL_HOST + ":" + NTU.port(39));
        setSingleThreadExecutorForLargeMethods();
        channelLogic.initPorts();
        RMIOperation<Integer> processOp = RMIOperation.valueOf(LargeRequestProcessor.class, LargeRequestProcessor.class.getMethod("process", byte[].class));

        byte[] largeData = new byte[LARGE_SIZE];
        ArrayList<RMIRequest<Integer>> requests = new ArrayList<>();

        Random rnd = new Random(6409837516922350791L);
        for (int i = 0; i < MAX_CONCURRENT_MESSAGES / 2; i++) {
            rnd.nextBytes(largeData);
            requests.add(channelLogic.clientPort.createRequest(processOp, (Object) largeData));
        }

        byte[] smallData = new byte[SMALL_SIZE];
        rnd.nextBytes(smallData);
        RMIRequest<Integer> smallRequest = channelLogic.clientPort.createRequest(processOp, (Object) smallData);
        smallRequest.send();


        assertEquals(Arrays.hashCode(smallData), (int) smallRequest.getBlocking());

        log.info("---------------------------------");
        long currentTime = System.currentTimeMillis();
        for (RMIRequest<Integer> request : requests) {
            request.send();
            while (request.getState() != RMIRequestState.SENT) {
                Thread.sleep(10);
                if (System.currentTimeMillis() > 10000 + currentTime)
                    fail("TIMEOUT!");
            }
            request.cancelWithConfirmation();
        }

        for (RMIRequest<Integer> request : requests)
            try {
                request.getBlocking();
                fail();
            } catch (RMIException e) {
                if (e.getType() != RMIExceptionType.CANCELLED_BEFORE_EXECUTION &&
                    e.getType() != RMIExceptionType.CANCELLED_DURING_EXECUTION)
                {
                    System.err.println("=== Unexpected exception ===");
                    e.printStackTrace(System.err);
                    fail("TYPE = " + e.getType());
                }
            }
    }

    ExecutorService newTestLocalExecutor(int size, final String name) {
        ExecutorService executorService = Executors.newFixedThreadPool(size, r -> new Thread(r, name));
        executorServices.add(executorService);
        return executorService;
    }

    void setSingleThreadExecutorForLargeMethods() {
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
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(new Sam(), Human.class, "Person"), channelLogic);
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(implFed, Family.class, "Fed"), channelLogic);
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(implIva, Family.class, "Iva"), channelLogic);

        connectDefault(41);

        Human person = channelLogic.clientPort.getProxy(Human.class, "Person");
        Family fed = channelLogic.clientPort.getProxy(Family.class, "Fed");
        Family iva = channelLogic.clientPort.getProxy(Family.class, "Iva");

        assertEquals(person.getName(), "Sam");
        assertEquals(person.hashCode(), "Sam".hashCode());
        assertFalse(person.toString().equals("my name is Sam"));
        assertEquals(person.toString("test"), "test my name is Sam");


        assertEquals(fed.children(), 2);
        assertEquals(fed.childrenName()[0], "Alina");

        assertEquals(iva.children(), 0);
        assertEquals(iva.childrenName(), null);

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

    @Test
    public void testUndeclaredException() {
        ImplNewService impl = new ImplNewService();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, NewService.class, "undeclaredException"), channelLogic);
        connectDefault(45);
        OldService un = channelLogic.clientPort.getProxy(OldService.class, "undeclaredException");
        try {
            un.generate();
            fail();
        } catch (Throwable t) {
            if (!(t instanceof UndeclaredThrowableException))
                fail(t.getMessage());
            StackTraceElement[] stackTrace = t.getCause().getStackTrace();
            assertEquals(ImplNewService.class.getName(), stackTrace[0].getClassName());
            assertEquals("generate", stackTrace[0].getMethodName());

            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[1]);

            assertEquals(this.getClass().getName().substring(0, stackTrace[2].getClassName().length()),
                stackTrace[2].getClassName());
            assertEquals("testUndeclaredException", stackTrace[2].getMethodName());
            assertStackTraceIsLocal(stackTrace);
        }
    }

    @SuppressWarnings("unused")
    interface NewService {
        void generate() throws ClassNotFoundException;
    }

    @SuppressWarnings("InterfaceNeverImplemented")
    interface OldService {
        void generate();
    }

    public static class ImplNewService implements NewService {
        @Override
        public void generate() throws ClassNotFoundException {
            throw new ClassNotFoundException();
        }
    }

    // --------------------------------------------------
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testCancelExecutionTaskBeforeRunning() {
        Executor executor = command -> {
            if (command instanceof RMIExecutionTask && !((RMIExecutionTask) command).getTask().getOperation().equals(TestService.OPERATION))
                ((RMIExecutionTask) command).getTask().complete(50.0);
            command.run();
        };
        client.getClient().setDefaultExecutor(executor);
        server.getServer().setDefaultExecutor(executor);
        NTU.exportServices(server.getServer(), DifferentServices.CALCULATOR_SERVICE, channelLogic);
        connectDefault(47);
        server.getServer().setDefaultExecutor(executor);
        @SuppressWarnings("unchecked")
        RMIRequest<Double> request = channelLogic.clientPort.createRequest(DifferentServices.CalculatorService.PLUS, 25.36, 24.2);
        request.send();
        try {
            assertEquals(request.getBlocking(), (Double) 50.0);
        } catch (RMIException e) {
            fail();
        }
    }
}
