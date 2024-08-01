/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.impl.stripe.StripedFactory;
import com.devexperts.qd.kit.RangeStriper;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.rmi.test.NTU;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StripedCollectorMatchingTest {

    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    public static final DataRecord QUOTE = SCHEME.findRecordByName("Quote");
    private static final int WAIT_TIMEOUT = 10; // seconds

    private static final String[] SYMBOLS = {
        "AB0", // Stripe 0 in byhash4
        "ABA1", // Stripe 1 in byhash4
        "AB2", // Stripe 2 in byhash4
        "AB1", // Stripe 3 in byhash4
    };

    private String testId;
    private QDEndpoint server;
    private QDEndpoint client;
    private final List<MessageAdapter> serverAdapters = new CopyOnWriteArrayList<>();
    private final List<MessageAdapter> clientAdapters = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        testId = UUID.randomUUID().toString();
    }

    @After
    public void tearDown() throws Exception {
        serverAdapters.clear();
        clientAdapters.clear();

        if (client != null)
            client.close();
        if (server != null)
            server.close();
    }

    @Test
    public void testCollectorStripingMatching() {
        SymbolStriper hashStriper = SymbolStriper.valueOf(SCHEME, "byhash4");
        RangeStriper rangeStriper = RangeStriper.valueOf(SCHEME, "byrange-A-K-T-");

        QDTicker ticker = (QDTicker) StripedFactory.getInstance().collectorBuilder(QDContract.TICKER)
            .withStriper(hashStriper)
            .build();

        // Hash striping with compatible striping should result in non-striped agent/distributor
        assertFalse(isStriped(ticker.agentBuilder().withStripe(hashStriper.getStripeFilter(0)).build()));
        assertFalse(isStriped(ticker.distributorBuilder().withStripe(hashStriper.getStripeFilter(1)).build()));

        // Hash striping with range striping should result in striped agent/distributor
        assertTrue(isStriped(ticker.agentBuilder().withStripe(rangeStriper.getStripeFilter(0)).build()));
        assertTrue(isStriped(ticker.distributorBuilder().withStripe(rangeStriper.getStripeFilter(1)).build()));
    }

    @Test
    public void testNoStriping() {
        createServerClientEndpoints("by1", "by1", "by1");
        sendReceiveData();

        // Everything is non-striped
        assertServerAdapters(false, false);
        assertClientAdapters(false, false);
    }

    @Test
    public void testNonMatchingStriping1() {
        createServerClientEndpoints("byrange-K-", "byhash4", "by1");
        sendReceiveData();

        // Incompatible stripes on the server and client, so expect everything striped
        assertServerAdapters(true, true);
        assertClientAdapters(true, true);
    }

    @Test
    public void testNonMatchingStriping2() {
        createServerClientEndpoints("byrange-K-", "byhash4", "byrange-T-");
        sendReceiveData();

        // Incompatible stripes on the server and client, so expect everything striped
        assertServerAdapters(true, true);
        assertClientAdapters(true, true);
    }

    @Test
    public void testServerAndConnectionStriping() {
        createServerClientEndpoints("byrange-K-", "byhash4", "byrange-K-");
        sendReceiveData();

        // Non-striped agent should be used since there is 1-to-1 correspondence
        // between server and connection stripers.
        assertServerAdapters(true, false);
        assertClientAdapters(true, true);
    }

    @Test
    public void testHalfStriping() {
        createServerClientEndpoints("byhash4", "byhash4", "byhash2");
        sendReceiveData();

        // Striped agent should be used since there is no 1-to-1 correspondence
        // between server and connection stripers.
        assertServerAdapters(true, true);
        // Striped distributor should be used since there is no 1-to-1 correspondence
        // between client and connection stripers.
        assertClientAdapters(true, true);
    }

    @Test
    public void testOptimizedStriping() {
        createServerClientEndpoints("byhash4", "byhash4", "byhash4");
        sendReceiveData();

        // Non-striped agent should be used since there is 1-to-1 correspondence
        // between server and connection stripers.
        assertServerAdapters(true, false);
        // Non-striped distributor should be used since there is 1-to-1 correspondence
        // between client and connection stripers.
        assertClientAdapters(true, false);
    }

    private void createServerClientEndpoints(
        String serverStripe, String clientStripe, String connectionStripe)
    {
        String name = this.testId + "-pub";
        Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(name);
        server = QDEndpoint.newBuilder()
            .withScheme(SCHEME)
            .withCollectors(EnumSet.of(QDContract.TICKER))
            .withStripe(serverStripe)
            .withStoreEverything(true)
            .build();

        serverAdapters.clear();
        List<MessageConnector> serverConnectors = MessageConnectors.createMessageConnectors(
            new AgentAdapter.Factory(server.getTicker()) {
                @Override
                public MessageAdapter createAdapter(QDStats stats) {
                    MessageAdapter adapter = super.createAdapter(stats);
                    serverAdapters.add(adapter);
                    return adapter;
                }
            },
            ":0[name=" + name + ",bindAddr=127.0.0.1]",
            QDStats.VOID);
        server.addConnectors(serverConnectors).startConnectors();
        int port = portPromise.await(WAIT_TIMEOUT, TimeUnit.SECONDS);

        client = QDEndpoint.newBuilder()
            .withName("client")
            .withScheme(SCHEME)
            .withCollectors(EnumSet.of(QDContract.TICKER))
            .withStripe(clientStripe)
            .build();

        clientAdapters.clear();
        List<MessageConnector> clientConnectors = MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(client, QDFilter.ANYTHING) {
                @Override
                public MessageAdapter createAdapter(QDStats stats) {
                    MessageAdapter adapter = super.createAdapter(stats);
                    clientAdapters.add(adapter);
                    return adapter;
                }
            },
            "127.0.0.1:" + port + "[stripe=" + connectionStripe + "]",
            QDStats.VOID);
        client.addConnectors(clientConnectors).startConnectors();

        int connectionCount = SymbolStriper.definedValueOf(SCHEME, connectionStripe).getStripeCount();

        // Wait until 4 connections are established and proper adapters and distributors are created
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT * 1_000L, 10,
            () -> clientAdapters.stream()
                .filter(adapter -> adapter instanceof DistributorAdapter)
                .map(adapter -> (DistributorAdapter) adapter)
                .map(distAdapter -> distAdapter.getDistributor(QDContract.TICKER))
                .filter(Objects::nonNull)
                .count() == connectionCount));
    }

    private void sendReceiveData() {
        // Create client's agent and subscribe
        QDAgent agent = client.getTicker().agentBuilder().build();
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        Arrays.stream(SYMBOLS).forEach(s -> sub.add(QUOTE, 0, s));
        agent.setSubscription(sub);
        sub.release();

        // Create server's distributor and publish data
        QDDistributor distributor = server.getTicker().distributorBuilder().build();
        RecordBuffer buffer = RecordBuffer.getInstance(RecordMode.DATA);
        Arrays.stream(SYMBOLS).forEach(s -> buffer.add(QUOTE, 0, s).setTime(System.currentTimeMillis()));
        distributor.process(buffer);
        buffer.release();

        // Wait until all data is received on the client
        RecordBuffer result = RecordBuffer.getInstance(RecordMode.DATA);
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT * 1_000L, 10, () -> {
            agent.retrieve(result);
            return result.size() == SYMBOLS.length;
        }));
        result.release();
    }

    private void assertServerAdapters(boolean stripedCollector, boolean stripedAgent) {
        //TODO Test AgentAdapter for striping, check agent's class name:
        // AgentAdapter -> AgentChannels -> AgentChannel -> AgentConfig -> QDAgent

        for (MessageAdapter adapter : serverAdapters) {
            if (!(adapter instanceof AgentAdapter))
                fail();
        }
    }

    private void assertClientAdapters(boolean stripedCollector, boolean stripedDistributor) {
        // Test DistributorAdapter for striping, check distributor's class name:
        // DistributorAdapter -> QDDistributor

        for (MessageAdapter adapter : clientAdapters) {
            if (!(adapter instanceof DistributorAdapter))
                fail();
            DistributorAdapter distributorAdapter = (DistributorAdapter) adapter;
            QDCollector collector = distributorAdapter.getCollector(QDContract.TICKER);
            assertNotNull(collector);
            assertEquals(stripedCollector, isStriped(collector));

            QDDistributor distributor = distributorAdapter.getDistributor(QDContract.TICKER);
            assertNotNull(distributor);
            assertEquals(stripedDistributor, isStriped(distributor));
        }
    }

    private static boolean isStriped(Object object) {
        return object.getClass().getSimpleName().contains("Striped");
    }
}
