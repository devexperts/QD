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
package com.dxfeed.api.test;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.Quote;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DXEndpointConnectTest {
    private static final int PORT = 7744;
    private static final long WAIT_FOR_FUTURE_TIMEOUT = 5000; // ms
    private static final long WAIT_FOR_SUBS_TIMEOUT = 10000; // ms
    private static final long WAIT_FOR_QUOTES_TIMEOUT = 10000; // ms

    DXEndpoint publishedEndpoint;
    DXEndpoint feedEndpoint;
    ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        if (publishedEndpoint != null)
            publishedEndpoint.close();
        if (feedEndpoint != null)
            feedEndpoint.close();
        if (executor != null)
            executor.shutdown();
        ThreadCleanCheck.after();
    }

    @Test
    public void testConnectInEndpointThread() throws InterruptedException, TimeoutException, ExecutionException {
        publishedEndpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
        DXPublisher publisher = publishedEndpoint
            .connect(":" + PORT)
            .getPublisher();

        BlockingQueue<Object> subs = new ArrayBlockingQueue<>(2);

        publisher.getSubscription(Quote.class).addChangeListener(subs::addAll);

        executor = Executors.newSingleThreadExecutor();
        feedEndpoint = DXEndpoint
            .create(DXEndpoint.Role.FEED)
            .executor(executor);
        DXFeed feed = feedEndpoint.getFeed();

        BlockingQueue<Quote> quotes = new ArrayBlockingQueue<>(2);

        // submit connect to the same thread as is used as executor
        Future<?> future = executor.submit(() -> {
            DXFeedSubscription<Quote> subscription = feed.createSubscription(Quote.class);
            subscription.addEventListener(quotes::addAll);
            subscription.addSymbols(Arrays.asList("AAPL", "IBM"));
            feedEndpoint.connect("localhost:" + PORT);
        });

        // will throw exception if connect does not complete in time
        future.get(WAIT_FOR_FUTURE_TIMEOUT, TimeUnit.MILLISECONDS);

        // wait until subs are received
        Set<Object> receivedSubs = new HashSet<>();
        receivedSubs.add(subs.poll(WAIT_FOR_SUBS_TIMEOUT, TimeUnit.MILLISECONDS));
        receivedSubs.add(subs.poll(WAIT_FOR_SUBS_TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(2, receivedSubs.size());
        assertTrue(receivedSubs.contains("AAPL") && receivedSubs.contains("IBM"));

        // publish events & check that we get them back
        publisher.publishEvents(Collections.singleton(new Quote("AAPL")));
        assertEquals("AAPL", quotes.poll(WAIT_FOR_QUOTES_TIMEOUT, TimeUnit.MILLISECONDS).getEventSymbol());

        publisher.publishEvents(Collections.singleton(new Quote("IBM")));
        assertEquals("IBM", quotes.poll(WAIT_FOR_QUOTES_TIMEOUT, TimeUnit.MILLISECONDS).getEventSymbol());

        // submit disconnect action into the same thread
        future = executor.submit(() -> feedEndpoint.disconnect());
        future.get(WAIT_FOR_FUTURE_TIMEOUT, TimeUnit.MILLISECONDS);

        // shutdown with blocking calls from external thread
        feedEndpoint.awaitNotConnected();
        feedEndpoint.closeAndAwaitTermination();
    }
}
