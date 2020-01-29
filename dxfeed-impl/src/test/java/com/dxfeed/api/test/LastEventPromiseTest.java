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

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.market.Trade;
import com.dxfeed.promise.Promise;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LastEventPromiseTest extends TestCase {
    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;

    private final BlockingQueue<Object> added = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> removed = new LinkedBlockingQueue<>();

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        publisher.getSubscription(Trade.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                added.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removed.addAll(symbols);
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    public void testLastEventPromise() throws InterruptedException {
        Promise<Trade> aPromise = feed.getLastEventPromise(Trade.class, "A");
        assertEquals("A", added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        Promise<Trade> bPromise = feed.getLastEventPromise(Trade.class, "B");
        assertEquals("B", added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        Promise<Trade> cPromise = feed.getLastEventPromise(Trade.class, "C");
        assertEquals("C", added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        Promise<Trade> bPromise2 = feed.getLastEventPromise(Trade.class, "B");
        assertFalse(aPromise.isDone());
        assertFalse(bPromise.isDone());
        assertFalse(cPromise.isDone());
        assertFalse(bPromise2.isDone());
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        publisher.publishEvents(Collections.singletonList(new Trade("A")));
        Trade aTrade = aPromise.await();
        assertEquals("A", aTrade.getEventSymbol());
        assertFalse(bPromise.isDone());
        assertFalse(cPromise.isDone());
        assertFalse(bPromise2.isDone());
        assertEquals("A", removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        publisher.publishEvents(Collections.singletonList(new Trade("B")));
        Trade bTrade = bPromise.await();
        Trade bTrade2 = bPromise2.await();
        assertEquals("B", bTrade.getEventSymbol());
        assertEquals("B", bTrade2.getEventSymbol());
        assertFalse(cPromise.isDone());
        assertEquals("B", removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        publisher.publishEvents(Collections.singletonList(new Trade("C")));
        Trade cTrade = cPromise.await();
        assertEquals("C", cTrade.getEventSymbol());
        assertEquals("C", removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());
    }

    public void testPromiseCancel() throws InterruptedException {
        Promise<Trade> promise = feed.getLastEventPromise(Trade.class, "T");
        assertEquals("T", added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        promise.cancel();
        assertEquals("T", removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());
    }
}
