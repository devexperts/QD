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
package com.devexperts.qd.qtp.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.util.IndexedSet;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.devexperts.qd.qtp.MessageType.Flag.ADDSUB;
import static com.devexperts.qd.qtp.MessageType.Flag.REMSUB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SubscriptionKeepAliveTest {

    private static final Logging log = Logging.getLogging(SubscriptionKeepAliveTest.class);
    private static final long TIMEOUT_MS = 30_000;

    private static final TestDataScheme SCHEME = new TestDataScheme();
    private static final DataRecord RECORD = SCHEME.getRecord(0);

    private QDEndpoint server;
    private QDEndpoint client;

    @Before
    public void setUp() {
        server = createEndpoint();
        client = createEndpoint();
    }

    @After
    public void tearDown() {
        if (client != null)
            client.close();
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testWithoutKeepAlive() throws InterruptedException {
        doTestKeepAlive(false);
    }

    @Test
    public void testInfiniteKeepAlive() throws InterruptedException {
        doTestKeepAlive(true);
    }

    protected void doTestKeepAlive(boolean keepAlive) throws InterruptedException {
        String testID = UUID.randomUUID().toString();
        Promise<Integer> port = ServerSocketTestHelper.createPortPromise(testID);
        server.addConnectors(MessageConnectors.createMessageConnectors(
            new AgentAdapter.Factory(server, null),
            ":0[name=" + testID + ",bindAddr=127.0.0.1,subscriptionKeepAlive=" + (keepAlive ? "inf" : "0") + "]",
            QDStats.VOID));
        server.startConnectors();

        client.addConnectors(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(client, null),
            "127.0.0.1:" + port.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
            QDStats.VOID));
        client.startConnectors();

        ArrayBlockingQueue<String> subQueue = new ArrayBlockingQueue<>(10);
        ArrayBlockingQueue<String> unsubQueue = new ArrayBlockingQueue<>(10);

        QDDistributor distributor = server.getTicker().distributorBuilder().build();
        distributor.getAddedRecordProvider().setRecordListener(provider -> retrieveSub(provider, subQueue, ADDSUB));
        distributor.getRemovedRecordProvider().setRecordListener(provider -> retrieveSub(provider, unsubQueue, REMSUB));

        QDAgent agent = client.getTicker().agentBuilder().withVoidRecordListener(true).build();
        setSubscription(agent, "A", "B", "C");

        HashSet<String> subSet = new HashSet<>();
        consumeSub(subQueue, 3, subSet::add);
        assertEquals(IndexedSet.of("A", "B", "C"), subSet);

        setSubscription(agent, "D");
        consumeSub(subQueue, 1, subSet::add);

        if (!keepAlive) {
            consumeSub(unsubQueue, 3, subSet::remove);
            assertEquals(IndexedSet.of("D"), subSet);
        }

        Thread.sleep(100); // give a chance to receive UNSUB
        assertTrue("No unsubscriptions expected", unsubQueue.isEmpty());
        assertTrue("No subscriptions expected", subQueue.isEmpty());
    }

    protected void consumeSub(BlockingQueue<String> queue, int num, Consumer<String> sink) throws InterruptedException {
        for (int i = 0; i < num; i++) {
            String s = queue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Expected subscription", s);
            sink.accept(s);
        }
    }

    protected void retrieveSub(RecordProvider provider, BlockingQueue<String> subQueue, MessageType.Flag flag) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        provider.retrieve(sub);
        sub.rewind();
        for (RecordCursor cur; (cur = sub.next()) != null; ) {
            String s = cur.getDecodedSymbol();
            log.debug("[" + flag + "]: " + s);
            subQueue.add(s);
        }
        sub.release();
    }

    protected void setSubscription(QDAgent agent, String... symbols) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        Stream.of(symbols).forEach(s -> sub.add(RECORD, SCHEME.getCodec().encode(s), s));
        agent.setSubscription(sub);
        sub.release();
    }

    protected QDEndpoint createEndpoint() {
        return QDEndpoint.newBuilder()
            .withScheme(SCHEME)
            .withCollectors(Collections.singletonList(QDContract.TICKER))
            .build();
    }
}
