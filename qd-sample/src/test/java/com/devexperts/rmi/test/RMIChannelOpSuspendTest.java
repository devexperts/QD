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

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.task.RMIChannelSupport;
import com.devexperts.rmi.task.RMIContinuation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.Promises;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class RMIChannelOpSuspendTest {
    private static final Logging log = Logging.getLogging(RMIChannelOpSuspendTest.class);

    private static final String EVEN_RESULT = "evenResult";
    private static final String ODD_RESULT = "oddResult";
    private static final String GO_RESULT = "goResult";

    private static final int MAX_COUNTER = 20;

    RMIEndpoint server;
    RMIEndpoint client;
    TestThreadPool executor;
    List<Throwable> exceptions = new Vector<>();
    ServiceImpl serviceImpl = new ServiceImpl();
    Service serviceProxy;

    Lock channelOpLock = new ReentrantLock();

    final int nThreads;

    public RMIChannelOpSuspendTest(int nThreads) {
        this.nThreads = nThreads;
    }

    @Parameterized.Parameters(name="nTreads={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {1},
            {2}
        });
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        client = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        executor = new TestThreadPool(nThreads, "RMIChannelOpSuspendTest", exceptions);
        server.getServer().setDefaultExecutor(executor);
        server.getServer().export(serviceImpl, Service.class);
        serviceProxy = client.getClient().getProxy(Service.class);
        NTU.connectPair(server, client);
    }

    @Test
    public void testChannelOpSuspend() {
        Promise<String> goPromise = serviceProxy.go();
        RMIRequest<String> goRequest = RMIRequest.of(goPromise);
        ServiceChannel serviceChannelProxy = goRequest.getChannel().getProxy(ServiceChannel.class);
        Promise<String> evenPromise = serviceChannelProxy.doEven();
        Promise<String> oddPromise = serviceChannelProxy.doOdd();
        Promises.allOf(evenPromise, oddPromise).await(10, TimeUnit.SECONDS);
        assertEquals(EVEN_RESULT, evenPromise.getResult());
        assertEquals(ODD_RESULT, oddPromise.getResult());
        assertEquals(GO_RESULT, goPromise.await(10, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() {
        client.close();
        server.close();
        executor.shutdown();
        ThreadCleanCheck.after();
        assertTrue(exceptions.isEmpty());
    }

    interface Service {
        public Promise<String> go();
    }

    class ServiceImpl implements Service, RMIChannelSupport<Void> {
        ServiceChannelImpl serviceChannelImpl = new ServiceChannelImpl();

        @Override
        public void openChannel(RMITask<Void> task) {
            // Sleep a bit before adding a channel handler in a attempt to catch a bug
            // with processing of channel operations before handler is installed on server side
            sleep(100);
            task.getChannel().addChannelHandler(serviceChannelImpl, ServiceChannel.class);
        }

        @Override
        public Promise<String> go() {
            log.info("ServiceImpl.go");
            serviceChannelImpl.goContinuation = RMITask.current(String.class).suspend(RMITask::cancel);
            return null;
        }
    }

    interface ServiceChannel {
        public Promise<String> doEven();
        public Promise<String> doOdd();
    }

    @SuppressWarnings("rawtypes")
    class ServiceChannelImpl implements ServiceChannel {
        private int counter;
        RMIContinuation<String> goContinuation;
        RMIContinuation<String> evenContinuation;
        RMIContinuation<String> oddContinuation;

        @Override
        public Promise<String> doEven() {
            doEvenCont();
            return null; // suspended!
        }

        @Override
        public Promise<String> doOdd() {
                doOddCont();
                return null; // suspended!
        }

        private String doEvenCont() {
            assertTrue(channelOpLock.tryLock());
            try {
                log.info("doEven @ " + counter);
                // sleep a bit in attempt to catch a bug where two channels ops are scheduled concurrently
                sleep(50);
                assertTrue(counter % 2 == 0);
                counter++;
                if (oddContinuation != null) {
                    oddContinuation.resume(this::doOddCont);
                }
                if (counter >= MAX_COUNTER) {
                    evenContinuation = null;
                    checkDone();
                    return EVEN_RESULT;
                } else {
                    evenContinuation = RMITask.current(String.class).suspend(RMITask::cancel);
                    return null;
                }
            } finally {
                channelOpLock.unlock();
            }
        }

        private String doOddCont() {
            assertTrue(channelOpLock.tryLock());
            try {
                log.info("doOdd @ " + counter);
                // sleep a bit in attempt to catch a bug where two channels ops are scheduled concurrently
                sleep(50);
                assertTrue(counter % 2 != 0);
                counter++;
                if (evenContinuation != null)
                    evenContinuation.resume(this::doEvenCont);
                if (counter >= MAX_COUNTER) {
                    oddContinuation = null;
                    checkDone();
                    return ODD_RESULT;
                } else {
                    oddContinuation = RMITask.current(String.class).suspend(RMITask::cancel);
                    return null;
                }
            } finally {
                channelOpLock.unlock();
            }
        }

        void checkDone() {
            if (oddContinuation == null && evenContinuation == null) {
                goContinuation.resume(this::doGoCont);
                // sleep a bit in attempt to catch a bug where channel gets closed by resumed operation before
                // the operations in this channel complete
                sleep(50);
            }
        }

        private String doGoCont() {
            return GO_RESULT;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
