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
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(TraceRunner.class)
public class MiniStressTest {
    private static final Logging log = Logging.getLogging(MiniStressTest.class);

    RMIEndpoint first;
    RMIEndpoint second;

    static Map<Integer, Double> idx = new ConcurrentHashMap<>();
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        first = RMIEndpoint.newBuilder().withName("FIRST").withSide(RMIEndpoint.Side.CLIENT_SERVER).build();
        second = RMIEndpoint.newBuilder().withName("SECOND").withSide(RMIEndpoint.Side.CLIENT_SERVER).build();
        first.getClient().setRequestRunningTimeout(1000 * 60 * 5); // to make sure tests don't run forever
        second.getClient().setRequestRunningTimeout(1000 * 60 * 5); // to make sure tests don't run forever
        serverExecutor = Executors.newFixedThreadPool(1000, r -> new Thread(r, "miniStressTest-server-pool"));
        clientExecutor = Executors.newFixedThreadPool(1000, r -> new Thread(r, "miniStressTest-client-pool"));
        first.getServer().setDefaultExecutor(serverExecutor);
        second.getServer().setDefaultExecutor(clientExecutor);
        log.info(" ======================= // =====================");
    }

    @After
    public void tearDown() throws Exception {
        first.close();
        second.close();
        idx.clear();
        serverExecutor.shutdown();
        clientExecutor.shutdown();
        ThreadCleanCheck.after();
    }

    void connect() {
        NTU.connectPair(first, second);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStress() {
        first.getServer().export(new ExternalService());
        second.getServer().export(new ExternalService());
        connect();
        int n = 400;
        RMIRequest<String>[] requests = new RMIRequest[2 * n];
        Random rnd = new Random();
        for (int i = 0; i < 2 * n; i++) {
            idx.put(i, rnd.nextDouble());
            requests[i] = first.getClient().getPort(null).createRequest(
                ExternalService.operation, i, rnd.nextInt(10) + 1, idx.get(i));
            idx.put(++i, rnd.nextDouble());
            requests[i] = second.getClient().getPort(null).createRequest(
                ExternalService.operation, i, rnd.nextInt(10) + 1, idx.get(i));
        }

        for (RMIRequest<?> request : requests) {
            request.getChannel().addChannelHandler(new InternalService());
            request.send();
        }

        String r;
        for (int i = 0; i < requests.length; i++) {
            try {
                r = requests[i].getBlocking();
                assertEquals("i = " + i, idx.get(i), Double.valueOf(r.substring(r.lastIndexOf('t') + 2)));
                if ((i + 1) % 100 == 0)
                    log.info( (i + 1) + " requests processed");
            } catch (RMIException e) {
                e.printStackTrace();
                fail("type = " + e.getType());
            }
        }
    }

    static class ExternalService extends RMIService<String> {

        static final String NAME = "ExternalService";
        static final RMIOperation<String> operation = RMIOperation.valueOf(NAME, String.class, "calc",
            int.class, int.class, double.class);

        protected ExternalService() {
            super(NAME);
        }

        @Override
        public void processTask(final RMITask<String> task) {
            task.setCancelListener(RMITask::cancel);
            final int id = (int) task.getRequestMessage().getParameters().getObject()[0];
            int n = (int) task.getRequestMessage().getParameters().getObject()[1];
            double result = (double) task.getRequestMessage().getParameters().getObject()[2];
            RMIRequest<String> request;

            for (int i = 0; i < n; i++) {
                request = task.getChannel().createRequest(InternalService.operation, id);
                request.send();
                try {
                    String r = request.getBlocking();
                    result *= Double.parseDouble(r.substring(r.lastIndexOf('t') + 2));
                } catch (RMIException e) {
                    task.completeExceptionally(request.getException());
                }
            }
            task.complete("id=" + id + ", result=" + result);
        }
    }

    static class InternalService extends RMIService<String> {

        static final String NAME = "InternalService";
        static final RMIOperation<String> operation = RMIOperation.valueOf(NAME, String.class, "get", int.class);

        protected InternalService() {
            super(NAME);
        }

        @Override
        public void processTask(RMITask<String> task) {
            double v = ThreadLocalRandom.current().nextDouble(100d);
            int id = (int) task.getRequestMessage().getParameters().getObject()[0];
            double res = idx.get(id);
            idx.put(id, res * v);
            task.complete("id=" + id + ", result=" + v);
        }
    }
}
