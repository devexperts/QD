/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.impl.test;

import java.util.Collections;
import java.util.concurrent.*;

import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.market.Trade;
import junit.framework.TestCase;

public class FilterTransferTest extends TestCase {
    private static final int PORT = 25899;
    private static final String CLIENT_FILTER = "[AC]*";
    private static final String SERVER_FILTER = "[AB]*";
    private static final String ACTUAL_SYMBOL = "ACTUAL";
    private static final String DXFEED_WILDCARD_ENABLE = "dxfeed.wildcard.enable";
    private static final int N = 10000; // number of filtered out events
    private static final long MAX_BYTES_SENT = 4098; // 4KB max can be sent

    private DXEndpoint server;
    private DXEndpoint client;

    private DXPublisher publisher;
    private DXFeed feed;

    private QDEndpoint serverQDEndpoint;
    private QDEndpoint clientQDEndpoint;

    private final BlockingQueue<Object> subAdded = new LinkedBlockingDeque<>();
    private final BlockingQueue<Trade> tradesReceived = new LinkedBlockingDeque<>();

    private DXFeedSubscription<Trade> sub;

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        server =  DXEndpoint.newBuilder().withProperty(DXFEED_WILDCARD_ENABLE, "true").withRole(DXEndpoint.Role.PUBLISHER).build();
        client = DXEndpoint.newBuilder().withProperty(DXFEED_WILDCARD_ENABLE, "true").withRole(DXEndpoint.Role.FEED).build();
        server.connect(SERVER_FILTER + "@:" + PORT);
        client.connect(CLIENT_FILTER +"@localhost:" + PORT);
        publisher = server.getPublisher();
        feed = client.getFeed();
        serverQDEndpoint = ((DXEndpointImpl) server).getQDEndpoint();
        clientQDEndpoint = ((DXEndpointImpl) client).getQDEndpoint();

        publisher.getSubscription(Trade.class).addChangeListener(subAdded::addAll);
        sub = feed.createSubscription(Trade.class);
        sub.addEventListener(tradesReceived::addAll);
    }

    @Override
    protected void tearDown() throws Exception {
        client.close();
        server.close();
        ThreadCleanCheck.after();
    }

    public void testFilters() throws InterruptedException {
        // subscribe to wildcard
        sub.addSymbols(WildcardSymbol.ALL);
        // wait until publisher receives wildcard
        assertEquals(WildcardSymbol.ALL, subAdded.poll(10, TimeUnit.SECONDS));
        // subscribe to "C*" symbols that should be filtered out
        for (int i = 0; i < N; i++) {
            sub.addSymbols("C" + i);
        }
        // publish a lot of symbols with this wildcard (but they don't satisfy filter)
        for (int i = 0; i < N; i++) {
            publishTrade("B" + i);
        }
        // one event that satisfies filter
        publishTrade(ACTUAL_SYMBOL);
        // make sure the last one (only) was received
        Trade tradeReceived = tradesReceived.poll(10, TimeUnit.SECONDS);
        assertEquals(ACTUAL_SYMBOL, tradeReceived.getEventSymbol());
        // make sure only few bytes were sent by both sides
        long serverBytesSent = getTotalBytesSent(serverQDEndpoint);
        assertTrue("serverBytesSent = " + serverBytesSent, serverBytesSent > 0 && serverBytesSent <= MAX_BYTES_SENT);
        long clientBytesSent = getTotalBytesSent(clientQDEndpoint);
        assertTrue("clientBytesSent = " + clientBytesSent, clientBytesSent > 0 && clientBytesSent <= MAX_BYTES_SENT);
    }

    private void publishTrade(String symbol) {
        Trade trade = new Trade(symbol);
        publisher.publishEvents(Collections.singletonList(trade));
    }

    private long getTotalBytesSent(QDEndpoint endpoint) {
        long sum = 0;
        for (MessageConnector connector : endpoint.getConnectors()) {
            sum += connector.retrieveCompleteEndpointStats().getWrittenBytes();
        }
        return sum;
    }


}
