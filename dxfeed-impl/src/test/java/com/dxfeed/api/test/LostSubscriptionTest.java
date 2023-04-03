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
import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.market.Quote;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LostSubscriptionTest implements ObservableSubscriptionChangeListener, DXFeedEventListener<Quote> {
    
    private static final List<String> SYMBOLS = Arrays.asList("AAPL", "GOOG", "IBM");
    private static final double BID_PRICE = 100.5;

    private DXEndpoint endpoint;
    private boolean closed;

    private Semaphore pubSemaphore;
    private Semaphore subSemaphore;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        pubSemaphore = new Semaphore(0);
        subSemaphore = new Semaphore(0);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testLostSub() {
        assertTrue("must have at least 2 symbols", SYMBOLS.size() >= 2);

        endpoint.getPublisher().getSubscription(Quote.class).addChangeListener(this);
        DXFeedSubscription<Quote> s = endpoint.getFeed().createSubscription(Quote.class);
        s.addEventListener(this);

        println("subscribe " + SYMBOLS);
        s.addSymbols(SYMBOLS);
        acquire(subSemaphore, 1);
        println("unsubscribe " + SYMBOLS);
        s.removeSymbols(SYMBOLS);
        pubSemaphore.release(1);
        acquire(subSemaphore, 1);
        println("subscribe " + SYMBOLS);
        s.addSymbols(SYMBOLS);

        pubSemaphore.release(SYMBOLS.size() - 1); // 1 permit was released earlier
        acquire(subSemaphore, SYMBOLS.size() + 1 - 2); // 2 permits were acquired earlier

        for (int i = 0; i < SYMBOLS.size(); i++)
            check(SYMBOLS.get(i), i == 0 ? Double.NaN : BID_PRICE);

        pubSemaphore.release(SYMBOLS.size());
        acquire(subSemaphore, SYMBOLS.size() + 1);

        for (String symbol : SYMBOLS)
            check(symbol, BID_PRICE);

        endpoint.close();
        assertTrue("should be closed", closed);
    }

    @Override
    public void symbolsAdded(Set<?> symbols) {
        println("added " + symbols);
        assertEquals(symbols, new HashSet<>(SYMBOLS));
        // Use SYMBOLS to always iterate them in same fixed pre-defined order.
        for (String symbol : SYMBOLS) {
            subSemaphore.release(1);
            acquire(pubSemaphore, 1);
            Quote q = new Quote(symbol);
            q.setBidPrice(BID_PRICE);
            println("publish " + q);
            endpoint.getPublisher().publishEvents(Collections.singleton(q));
        }
        subSemaphore.release(1);
    }

    @Override
    public void symbolsRemoved(Set<?> symbols) {
        println("removed " + symbols);
        fail(); // should never happen in this test
    }

    @Override
    public void subscriptionClosed() {
        println("subscription closed");
        closed = true;
    }

    @Override
    public void eventsReceived(List<Quote> events) {
        println("received " + events);
    }

    private void check(String symbol, double expectedBidPrice) {
        Quote q = endpoint.getFeed().getLastEvent(new Quote(symbol));
        println("get last = " + q);
        assertEquals(0, Double.compare(q.getBidPrice(), expectedBidPrice));
    }

    private void acquire(Semaphore semaphore, int permits) {
        try {
            if (!semaphore.tryAcquire(permits, 10, TimeUnit.SECONDS))
                fail("cannot acquire semaphore");
        } catch (InterruptedException e) {
            fail("unexpected thread interruption");
        }
    }

    private void println(String message) {
        String threadName = Thread.currentThread().getName();
        if (threadName.matches(".*ExecutorThread.."))
            threadName = "ET" + threadName.substring(threadName.length() - 2);
        System.out.println(TimeFormat.DEFAULT.withMillis().format(System.currentTimeMillis()) +
            " [" + threadName + "] " + message);
    }
}
