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
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.api.impl.DXFeedScheme;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class RMIQDTest {
    private static final Logging log = Logging.getLogging(RMIQDTest.class);

    private static final int N_RECS = 10_000;

    private static final DataScheme SCHEME = DXFeedScheme.getInstance();
    private static final DataRecord RECORD = SCHEME.findRecordByName("Trade");

    private RMIEndpoint server;
    private RMIEndpoint client;

    private QDTicker serverTicker;
    private QDTicker clientTicker;

    private Thread mainThread;

    @Parameterized.Parameter
    public int nRMIMessages = 1;

    @Parameterized.Parameters(name = "nRMIMessages={0}")
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{ {0}, {1}, {1000}});
    }

    @Before
    public void setup() {
        ThreadCleanCheck.before();
        serverTicker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        server = RMIEndpoint.createEndpoint(RMIEndpoint.Side.SERVER);
        server.setAttachedMessageAdapterFactory(new AgentAdapter.Factory(serverTicker));

        clientTicker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        client = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        client.setAttachedMessageAdapterFactory(new DistributorAdapter.Factory(clientTicker));
    }

    @After
    public void tearDown(){
        client.close();
        server.close();
        ThreadCleanCheck.after();
    }

    private void connect() {
        log.info("Connect");
        int port = NTU.connectServer(server);
        // Set large heartbeat period to reproduce [QD-932] RMI: Potential delay of QD messages due to lost "more messages flag"
        NTU.connect(client, NTU.localHost(port) + "[initialHeartbeatPeriod=30s]");
    }

    @Test
    public void testQDOverRMI() throws InterruptedException, RMIException {
        mainThread = Thread.currentThread();

        // Get ready to send QD data
        QDDistributor serverDistributor = serverTicker.distributorBuilder().build();
        serverDistributor.getAddedRecordProvider().setRecordListener(provider -> {
            RecordBuffer data = RecordBuffer.getInstance();
            provider.retrieve(data);
            serverDistributor.processData(data);
            data.release();
        });

        // Prepare subscription
        QDAgent clientAgent = clientTicker.agentBuilder().build();
        clientAgent.setRecordListener(provider -> LockSupport.unpark(mainThread));
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        for (int i = 0; i < N_RECS; i++) {
            String symbol = "S" + i;
            sub.add(RECORD, SCHEME.getCodec().encode(symbol), symbol);
        }
        clientAgent.addSubscription(sub);
        sub.release();

        // Make a few one-way RMI calls (in advance!)
        ServiceImpl serviceImpl = new ServiceImpl();
        if (nRMIMessages > 0) {
            server.getServer().export(serviceImpl, Runnable.class);
        }

        RMIRequest<Void> request = null;
        for (int i = 0; i < nRMIMessages; i++) {
            request = client.getClient().getPort(null).createRequest(new RMIRequestMessage<>(
                RMIRequestType.ONE_WAY, RMIOperation.valueOf(Runnable.class, void.class, "run")));
            request.send();
        }

        // And now connect
        connect();

        // Wait at most 10 secs for data and RMI messages
        RecordBuffer data = RecordBuffer.getInstance();
        long timeLimit = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < timeLimit) {
            if (data.size() >= N_RECS && serviceImpl.count.get() >= nRMIMessages)
                break;
            clientAgent.retrieveData(data);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }

        // Check
        assertEquals("data records received", N_RECS, data.size());
        assertEquals("service invocations", nRMIMessages, serviceImpl.count.get());
        data.release();
    }

    private class ServiceImpl implements Runnable {
        final AtomicInteger count = new AtomicInteger();

        @Override
        public void run() {

            count.incrementAndGet();
            LockSupport.unpark(mainThread);
        }
    }
}
