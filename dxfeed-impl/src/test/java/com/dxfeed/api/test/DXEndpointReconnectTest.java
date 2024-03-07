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
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.devexperts.util.SynchronizedIndexedSet;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.Promises;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class DXEndpointReconnectTest {

    private static final Logging log = Logging.getLogging(DXEndpointReconnectTest.class);

    private static final int PUB_COUNT = 10;
    private static final int WAIT_TIMEOUT = 30; // seconds

    private String testId;

    List<DXEndpoint> publishers;
    List<Integer> ports;
    DXEndpoint feedEndpoint;

    volatile CountDownLatch connectedSomewhere;
    AtomicInteger connectsCount;
    volatile String expectedSymbol;
    private BlockingQueue<Quote> quotes;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        testId = UUID.randomUUID().toString();
        publishers = new ArrayList<>(PUB_COUNT);
        ports = new ArrayList<>(PUB_COUNT);
        connectedSomewhere = new CountDownLatch(1);
        connectsCount = new AtomicInteger(0);
        quotes = new ArrayBlockingQueue<>(PUB_COUNT);
    }

    @After
    public void tearDown() throws Exception {
        log.debug("======== tearDown ========");
        if (feedEndpoint != null)
            feedEndpoint.close();
        if (publishers != null)
            publishers.forEach(DXEndpoint::close);
        ThreadCleanCheck.after();
    }

    @Test
    public void testReconnect() throws InterruptedException {
        ArrayList<String> symbols = startPublishers();
        String address = ports.stream().map((port) -> "127.0.0.1:" + port).collect(Collectors.joining(",")) +
            "[name=uplink,reconnectDelay=1]";

        feedEndpoint = createFeedEndpoint(symbols, quotes::addAll);
        log.info("Connecting...");
        feedEndpoint.connect(address);

        Set<String> receivedQuotes = new HashSet<>();
        for (int i = 1; i <= PUB_COUNT; i++) {
            awaitConnection(i);
            receivedQuotes.add(expectQuote(quotes, expectedSymbol));

            if (i < PUB_COUNT) {
                connectedSomewhere = new CountDownLatch(1);
                log.info("Reconnecting...");
                feedEndpoint.reconnect();
            }
        }
        assertEquals(PUB_COUNT, receivedQuotes.size());
    }

    @Test
    public void testOrderedConnect() throws InterruptedException {
        doTestOrderedStrategy(false);
    }

    @Test
    public void testOrderedReconnect() throws InterruptedException {
        doTestOrderedStrategy(true);
    }

    private void doTestOrderedStrategy(boolean useReconnect) throws InterruptedException {
        ArrayList<String> symbols = startPublishers();
        String address = ports.stream().map((port) -> "127.0.0.1:" + port).collect(Collectors.joining(","));
        address += "[name=uplink,reconnectDelay=1,connectOrder=ordered]";

        feedEndpoint = createFeedEndpoint(symbols, quotes::addAll);
        log.info("Connecting...");
        feedEndpoint.connect(address);

        for (int i = 1; i <= PUB_COUNT; i++) {
            awaitConnection(i);
            expectQuote(quotes, symbols.get(i - 1));

            if (i < PUB_COUNT) {
                connectedSomewhere = new CountDownLatch(1);
                if (useReconnect) {
                    log.info("Reconnecting...");
                    feedEndpoint.reconnect();
                } else {
                    // force reconnecting to the next publisher
                    log.info("Killing publisher " + symbols.get(i - 1));
                    publishers.get(i - 1).close();
                }
            }
        }
    }

    @Test
    public void testPriorityStrategy() throws InterruptedException {

        ArrayList<String> symbols = startPublishers();
        String address = ports.stream().map((port) -> "127.0.0.1:" + port).collect(Collectors.joining(","));
        address += "[name=uplink,reconnectDelay=1,connectOrder=priority]";

        Set<Integer> blockedPorts = SynchronizedIndexedSet.create();
        TestConnectorFactory.addClientSocketConnectorForBlockedPorts(testId + ":", blockedPorts);

        // block first half of publishers
        int deadPubCount = PUB_COUNT / 2;
        for (int i = 0; i < deadPubCount; i++) {
            blockedPorts.add(ports.get(i));
        }

        feedEndpoint = createFeedEndpoint(symbols, quotes::addAll);
        log.info("Connecting...");
        feedEndpoint.connect(testId + ":" + address);

        ClientSocketConnector connector = getFirstConnector(feedEndpoint);

        // sequentially block remaining publishers except the last two and force client to reconnect
        for (int i = deadPubCount + 1; i <= PUB_COUNT - 1; i++) {
            awaitConnection(i - deadPubCount);
            expectQuote(quotes, symbols.get(i - 1));
            assertEquals(ports.get(i - 1).intValue(), connector.getCurrentPort());

            if (i < PUB_COUNT - 1) {
                connectedSomewhere = new CountDownLatch(1);
                // force reconnecting to the next publisher
                log.info("Reconnecting from publisher " + symbols.get(i - 1));
                blockedPorts.add(ports.get(i - 1));
                assertEquals(ports.get(i - 1).intValue(), connector.getCurrentPort());
                connector.reconnect();
            }
        }

        // reactivate second publisher
        blockedPorts.remove(ports.get(1));
        connectsCount.set(0);
        connectedSomewhere = new CountDownLatch(1);
        blockedPorts.add(ports.get(PUB_COUNT - 2));
        assertEquals(ports.get(PUB_COUNT - 2).intValue(), connector.getCurrentPort());
        connector.reconnect();

        // should connect to the reactivated publisher
        awaitConnection(1);
        expectQuote(quotes, symbols.get(1));
        assertEquals(ports.get(1).intValue(), connector.getCurrentPort());
    }

    // Test for regression introduced in QD-1215: changing connector params shall do a clean restart.

    @Test
    public void testConnectorDirectUpdate() throws InterruptedException {
        ArrayList<String> symbols = startPublishers();
        String address = "127.0.0.1:" + ports.get(0) + "[name=uplink,reconnectDelay=1]";

        feedEndpoint = createFeedEndpoint(symbols, quotes::addAll);
        log.info("Connecting: " + address);
        feedEndpoint.connect(address);

        ClientSocketConnector connector = getFirstConnector(feedEndpoint);

        for (int i = 1; i <= PUB_COUNT; i++) {
            awaitConnection(i);
            expectQuote(quotes, symbols.get(i - 1));

            if (i < PUB_COUNT) {
                connectedSomewhere = new CountDownLatch(1);
                address = "127.0.0.1:" + ports.get(i);
                log.info("Resetting address: " + address);
                connector.setAddress(address);
            }
        }
    }

    private ClientSocketConnector getFirstConnector(DXEndpoint feedEndpoint) {
        List<MessageConnector> connectors = ((DXEndpointImpl) feedEndpoint).getQDEndpoint().getConnectors();
        assertEquals(1, connectors.size());
        assertTrue(connectors.get(0) instanceof ClientSocketConnector);
        return (ClientSocketConnector) connectors.get(0);
    }

    private Promise<Integer> addPublisher(String symbol) {
        String name = testId + "-pub-" + symbol;
        Promise<Integer> port = ServerSocketTestHelper.createPortPromise(name);
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_PUBLISHER)
            .build()
            .connect(":0[name=" + name + ",bindAddr=127.0.0.1]");
        DXPublisher publisher = endpoint.getPublisher();
        publisher.getSubscription(Quote.class)
            .addChangeListener((symbols) -> {
                expectedSymbol = symbol;
                publisher.publishEvents(Collections.singleton(new Quote(symbol)));
                connectsCount.getAndIncrement();
                connectedSomewhere.countDown();
            });
        publishers.add(endpoint);
        return port;
    }

    private ArrayList<String> startPublishers() {
        List<Promise<Integer>> promises = new ArrayList<>();
        ArrayList<String> symbols = new ArrayList<>();
        for (int i = 0; i < PUB_COUNT; i++) {
            String symbol = String.valueOf((char) ('A' + i));
            promises.add(addPublisher(symbol));
            symbols.add(symbol);
        }
        Promises.allOf(promises).await(WAIT_TIMEOUT, TimeUnit.SECONDS);
        promises.stream().map(Promise::getResult).forEach(ports::add);
        return symbols;
    }

    private DXEndpoint createFeedEndpoint(ArrayList<String> symbols, DXFeedEventListener<Quote> listener) {
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.STREAM_FEED);
        DXFeed feed = endpoint.getFeed();

        DXFeedSubscription<Quote> subscription = feed.createSubscription(Quote.class);
        subscription.addEventListener(listener);
        subscription.addSymbols(symbols);
        return endpoint;
    }

    private String expectQuote(BlockingQueue<Quote> quotes, String expectedSymbol) throws InterruptedException {
        Quote quote = quotes.poll(WAIT_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull("Missed quote from publisher", quote);
        String symbol = quote.getEventSymbol();
        if (expectedSymbol != null)
            assertEquals(expectedSymbol, symbol);
        return symbol;
    }

    private void awaitConnection(int expectedConnectsCount) throws InterruptedException {
        assertTrue("Connected to a publisher", connectedSomewhere.await(WAIT_TIMEOUT, TimeUnit.SECONDS));
        if (expectedConnectsCount > 0)
            assertEquals(expectedConnectsCount, connectsCount.get());
    }
}
