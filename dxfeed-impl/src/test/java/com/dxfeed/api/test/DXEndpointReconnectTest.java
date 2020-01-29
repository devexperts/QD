/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.Quote;
import com.dxfeed.promise.Promise;
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

    static final Logging log = Logging.getLogging(DXEndpointReconnectTest.class);

    private static final int PUB_COUNT = 10;

    private String testId;

    List<DXEndpoint> publishers;
    List<Integer> ports;
    DXEndpoint feedEndpoint;

    volatile CountDownLatch connectedSomewhere;
    AtomicInteger connectsCount;
    volatile String expectedSymbol;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        testId = UUID.randomUUID().toString();
        publishers = new ArrayList<>(PUB_COUNT);
        ports = new ArrayList<>(PUB_COUNT);
        connectedSomewhere = new CountDownLatch(1);
        connectsCount = new AtomicInteger(0);
    }

    @After
    public void tearDown() throws Exception {
        log.debug("======== tearDown ========");
        if (publishers != null)
            publishers.forEach(DXEndpoint::close);
        if (feedEndpoint != null)
            feedEndpoint.close();
        ThreadCleanCheck.after();
    }

    private void addPublisher(String symbol) {
        String name = testId + "-pub-" + symbol;
        Promise<Integer> port = ServerSocketTestHelper.createPortPromise(name);
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
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
        ports.add(port.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testReconnect() throws InterruptedException {

        ArrayList<String> symbols = new ArrayList<>();
        for (int i = 0; i < PUB_COUNT; i++) {
            String symbol = String.valueOf((char) ('A' + i));
            addPublisher(symbol);
            symbols.add(symbol);
        }
        String address = ports.stream().map((port) -> "127.0.0.1:" + port).collect(Collectors.joining(","));

        feedEndpoint = DXEndpoint.create(DXEndpoint.Role.FEED);
        DXFeed feed = feedEndpoint.getFeed();

        BlockingQueue<Quote> quotes = new ArrayBlockingQueue<>(PUB_COUNT);
        Set<String> receivedQuotes = new HashSet<>();

        DXFeedSubscription<Quote> subscription = feed.createSubscription(Quote.class);
        subscription.addEventListener(quotes::addAll);
        subscription.addSymbols(symbols);

        log.info("Connecting...");
        feedEndpoint.connect(address);

        for (int i = 1; i <= PUB_COUNT; i++) {
            assertTrue("Connected to a publisher", connectedSomewhere.await(10, TimeUnit.SECONDS));
            assertEquals(i, connectsCount.get());

            log.info("expectedSymbol = " + expectedSymbol);

            Quote quote = quotes.poll(10, TimeUnit.SECONDS);
            assertNotNull("Missed quote from publisher on step " + i, quote);
            receivedQuotes.add(quote.getEventSymbol());

            if (i < PUB_COUNT) {
                connectedSomewhere = new CountDownLatch(1);
                log.info("Reconnecting...");
                feedEndpoint.reconnect();
            }
        }

        assertEquals(PUB_COUNT, receivedQuotes.size());

        feedEndpoint.close();
    }
}
