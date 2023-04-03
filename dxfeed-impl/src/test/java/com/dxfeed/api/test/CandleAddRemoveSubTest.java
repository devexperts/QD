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
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CandleAddRemoveSubTest {
    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<Candle> sub;

    private final CandleSymbol symbol1 = CandleSymbol.valueOf("X", CandlePeriod.valueOf(5, CandleType.MINUTE));
    private final CandleSymbol symbol2 = CandleSymbol.valueOf("Y", CandlePeriod.valueOf(5, CandleType.MINUTE));
    private final CandleSymbol symbol3 = CandleSymbol.valueOf("Z", CandlePeriod.valueOf(5, CandleType.MINUTE));
    private final CandleSymbol[] symbols = {symbol1, symbol2, symbol3};
    private long time;

    private final BlockingQueue<Object> added = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> removed = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> received = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        endpoint.executor(Runnable::run);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(Candle.class);
        publisher.getSubscription(Candle.class).addChangeListener(new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                added.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                removed.addAll(symbols);
            }
        });
        sub.addEventListener((candles) -> {
            candles.stream().map(Candle::getEventSymbol).forEach(received::add);
        });
    }

    @After
    public void tearDown() throws Exception {
        endpoint.closeAndAwaitTermination();
        checkQueues(null, null, null);
        ThreadCleanCheck.after();
    }

    @Test
    public void testCandleAddRemoveSub() throws InterruptedException {
        // subscribe
        sub.addSymbols(symbol1);
        checkQueues(symbol1, null, null);

        // shall receive this event (subscribed)
        publish();
        checkQueues(null, null, symbol1);

        // unsubscribe
        sub.removeSymbols(symbol1);
        checkQueues(null, symbol1, null);

        // shall NOT receive this event (not subscribed)
        publish();
        checkQueues(null, null, null);
    }

    @Test
    public void testCandleAddClearSub() throws InterruptedException {
        // subscribe
        sub.addSymbols(symbol1);
        checkQueues(symbol1, null, null);

        // shall receive this event (subscribed)
        publish();
        checkQueues(null, null, symbol1);

        // unsubscribe with clear
        sub.clear();
        checkQueues(null, symbol1, null);

        // shall NOT receive this event (not subscribed)
        publish();
        checkQueues(null, null, null);
    }

    @Test
    public void testReAttach() throws InterruptedException {
        feed.detachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentAttach() throws InterruptedException {
        feed.attachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentDetach() throws InterruptedException {
        feed.detachSubscription(sub);
        feed.detachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentReAttach() throws InterruptedException {
        feed.detachSubscription(sub);
        feed.detachSubscription(sub);
        feed.attachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentAttachAfterFirstChangeListener() throws InterruptedException {
        feed.detachSubscription(sub);
        sub.addChangeListener(emptyChangeListener());
        feed.attachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentAttachAfterSecondChangeListener() throws InterruptedException {
        sub.addChangeListener(emptyChangeListener());
        feed.attachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
    }

    @Test
    public void testIdempotentReAttachWithManyChangeListener() throws InterruptedException {
        sub.addChangeListener(emptyChangeListener());
        feed.detachSubscription(sub);
        feed.detachSubscription(sub);
        sub.addChangeListener(emptyChangeListener());
        feed.attachSubscription(sub);
        feed.attachSubscription(sub);
        sub.addChangeListener(emptyChangeListener());
        testCandleAddDetachSub();
    }

    @Test
    public void testDetachAfterSecondChangeListener() throws InterruptedException {
        sub.addChangeListener(emptyChangeListener());
        testCandleAddDetachSub();
    }

    @Test
    public void testDualAttach() throws InterruptedException {
        DXEndpoint secondEndpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        secondEndpoint.executor(Runnable::run);
        secondEndpoint.getFeed().attachSubscription(sub);
        testCandleAddDetachSub();
        secondEndpoint.closeAndAwaitTermination();
    }

    @Test
    public void testDualAttachAndDetach() throws InterruptedException {
        DXEndpoint secondEndpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        secondEndpoint.executor(Runnable::run);
        secondEndpoint.getFeed().attachSubscription(sub);
        secondEndpoint.getFeed().detachSubscription(sub);
        testCandleAddDetachSub();
        secondEndpoint.closeAndAwaitTermination();
    }

    @Test
    public void testDualAttachAndReAttach() throws InterruptedException {
        DXEndpoint secondEndpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        secondEndpoint.executor(Runnable::run);
        secondEndpoint.getFeed().detachSubscription(sub);
        feed.detachSubscription(sub);
        secondEndpoint.getFeed().attachSubscription(sub);
        sub.addChangeListener(emptyChangeListener());
        secondEndpoint.getFeed().detachSubscription(sub);
        feed.attachSubscription(sub);
        testCandleAddDetachSub();
        secondEndpoint.closeAndAwaitTermination();
    }

    @Test
    public void testCandleAddDetachSub() throws InterruptedException {
        // subscribe
        sub.addSymbols(symbol1);
        checkQueues(symbol1, null, null);

        // shall receive this event (subscribed)
        publish();
        checkQueues(null, null, symbol1);

        // detach sub
        feed.detachSubscription(sub);
        checkQueues(null, symbol1, null);

        // shall NOT receive this event (detached!)
        publish();
        checkQueues(null, null, null);
    }

    @Test
    public void testCandleSetSub() throws InterruptedException {
        // set sub to symbol1
        sub.setSymbols(symbol1);
        checkQueues(symbol1, null, null);

        // shall receive this event (subscribed)
        publish();
        checkQueues(null, null, symbol1);

        // set sub to symbol2
        sub.setSymbols(symbol2);
        checkQueues(symbol2, symbol1, null);

        // shall receive only symbol2 event
        publish();
        checkQueues(null, null, symbol2);

        // clear subscription for expected tearDown
        sub.clear();
        checkQueues(null, symbol2, null);
    }

    private void publish() {
        // publish all symbols, including never subscribed ones
        List<Candle> candles = new ArrayList<>();
        for (CandleSymbol symbol : symbols) {
            Candle candle = new Candle(symbol);
            // make all candle use different time to avoid conflation
            candle.setTime(time += 1000);
            candles.add(candle);
        }
        publisher.publishEvents(candles);
    }

    private void checkQueues(CandleSymbol addedSymbol, CandleSymbol removedSymbol, CandleSymbol receivedSymbol) {
        assertEquals(addedSymbol, added.poll());
        assertEquals(removedSymbol, removed.poll());
        assertEquals(receivedSymbol, received.poll());
        assertTrue(added.isEmpty());
        assertTrue(removed.isEmpty());
        assertTrue(received.isEmpty());
    }

    private ObservableSubscriptionChangeListener emptyChangeListener() {
        return symbols -> {
            // do nothing
        };
    }
}
