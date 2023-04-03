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
import com.devexperts.rmi.RuntimeRMIException;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.devexperts.util.SystemProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
//@UseParametersRunnerFactory(IsolatedParametersRunnerFactory.class)
//@Isolated({"com.devexperts"})
public class RMIErrorThrowingTest {

    private static final Logging log = Logging.getLogging(RMIErrorThrowingTest.class);

    private RMIEndpoint server;
    private RMIEndpoint client;

    private final ChannelLogic channelLogic;
    private boolean removeStackTraces;

    @Parameterized.Parameters(name = "type={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            {TestType.REGULAR},
            {TestType.CLIENT_CHANNEL},
            {TestType.SERVER_CHANNEL}
        });
    }

    public RMIErrorThrowingTest(TestType type) {
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
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        // keep in sync with RMIExceptionMarshaller implementation
        removeStackTraces = SystemProperties.getBooleanProperty("com.devexperts.rmi.removeStackTraces", true);
    }

    @After
    public void tearDown() {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
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

    // copied from RMIInvocationHandler
    private static final StackTraceElement RMI_LAYER_SEPARATOR_FRAME =
        new StackTraceElement("com.devexperts.rmi", "<REMOTE-METHOD-INVOCATION>", null, -1);

    @Test
    public void testErrorThrowing() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new AIOOBEThrower(), ErrorThrower.class),
            channelLogic);
        connectDefault();

        ErrorThrower thrower = channelLogic.clientPort.getProxy(ErrorThrower.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();
            int pos = 0;
            if (!removeStackTraces)
                checkStackTraceElement(stackTrace[pos++], AIOOBEThrower.class.getName(), "throwError");
            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[pos++]);
            checkStackTraceElement(stackTrace[pos], this.getClass().getName(), "testErrorThrowing");
            assertStackTraceIsLocal(stackTrace);
        } catch (Throwable e) {
            log.info("----------------");
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private static void checkStackTraceElement(StackTraceElement stackTrace, String className, String method) {
        assertEquals(className, stackTrace.getClassName());
        assertEquals(method, stackTrace.getMethodName());
    }

    @Test
    public void testErrorThrowingWithoutDeclare() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new AIOOBEThrowerWithoutDeclare(), WithoutDeclareError.class), channelLogic);
        connectDefault();

        WithoutDeclareError thrower = channelLogic.clientPort.getProxy(WithoutDeclareError.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();
            int pos = 0;
            if (!removeStackTraces)
                checkStackTraceElement(stackTrace[pos++], AIOOBEThrowerWithoutDeclare.class.getName(), "throwError");
            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[pos++]);
            checkStackTraceElement(stackTrace[pos], this.getClass().getName(), "testErrorThrowingWithoutDeclare");
            assertStackTraceIsLocal(stackTrace);
        } catch (Throwable e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testErrorThrowingWithEmptyStacktrace() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new AIOOBEThrowerWithEmptyStacktrace(false), ErrorThrower.class),
            channelLogic);
        connectDefault();

        ErrorThrower thrower = channelLogic.clientPort.getProxy(ErrorThrower.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();
            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[0]);
            checkStackTraceElement(stackTrace[1], this.getClass().getName(), "testErrorThrowingWithEmptyStacktrace");
            assertStackTraceIsLocal(stackTrace);
        } catch (Throwable e) {
            log.info("----------------");
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testErrorThrowingWithEmptyStacktraceImmutable() {
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(new AIOOBEThrowerWithEmptyStacktrace(true), ErrorThrower.class),
            channelLogic);
        connectDefault();

        ErrorThrower thrower = channelLogic.clientPort.getProxy(ErrorThrower.class);
        try {
            thrower.throwError();
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Check the stacktrace
            StackTraceElement[] stackTrace = e.getStackTrace();
            assertEquals(0, stackTrace.length);
        } catch (Throwable e) {
            log.info("----------------");
            e.printStackTrace();
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
        public void throwError() {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public static class AIOOBEThrowerWithEmptyStacktrace implements ErrorThrower {

        private final boolean immutable;

        public AIOOBEThrowerWithEmptyStacktrace(boolean immutable) {
            this.immutable = immutable;
        }

        @Override
        public void throwError() {
            throw new AIOOBExceptionWithEmptyStacktrace(immutable);
        }
    }

    public static class AIOOBExceptionWithEmptyStacktrace extends ArrayIndexOutOfBoundsException {
        private final boolean immutable;

        public AIOOBExceptionWithEmptyStacktrace(boolean immutable) {
            this.immutable = immutable;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public void setStackTrace(StackTraceElement[] stackTrace) {
            if (!immutable)
                super.setStackTrace(stackTrace);
        }
    }

    private static void assertStackTraceIsLocal(StackTraceElement[] examine) {
        StackTraceElement[] reference = Thread.currentThread().getStackTrace();
        int pos = 0;
        while (!reference[pos].getMethodName().equals("assertStackTraceIsLocal")) {
            pos++;
        }
        int n = reference.length - pos - 2;
        for (int i = 0; i < n; i++) {
            assertEquals(reference[reference.length - 1 - i], examine[examine.length - 1 - i]);
        }
    }

    // --------------------------------------------------

    @Test
    public void testRMIExceptionDeclared() {
        RemoteThrower impl = new RemoteThrower();
        NTU.exportServices(server.getServer(), new RMIServiceImplementation<>(impl, Declared.class, "declared"),
            channelLogic);
        connectDefault();
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
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(impl, Undeclared.class, "undeclared"),
            channelLogic);
        connectDefault();
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

    @Test
    public void testUndeclaredException() {
        ImplNewService impl = new ImplNewService();
        NTU.exportServices(server.getServer(),
            new RMIServiceImplementation<>(impl, NewService.class, "undeclaredException"), channelLogic);
        connectDefault();
        OldService un = channelLogic.clientPort.getProxy(OldService.class, "undeclaredException");
        try {
            un.generate();
            fail();
        } catch (Throwable t) {
            if (!(t instanceof UndeclaredThrowableException))
                fail(t.getMessage());

            StackTraceElement[] stackTrace = t.getCause().getStackTrace();
            int pos = 0;
            if (!removeStackTraces)
                checkStackTraceElement(stackTrace[pos++], ImplNewService.class.getName(), "generate");
            assertEquals(RMI_LAYER_SEPARATOR_FRAME, stackTrace[pos++]);
            checkStackTraceElement(stackTrace[pos], this.getClass().getName(), "testUndeclaredException");
            assertStackTraceIsLocal(stackTrace);
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
        public void generateDeclared() {
            client.disconnect();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }

        @Override
        public void generateUndeclared() {
            client.disconnect();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
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
}
