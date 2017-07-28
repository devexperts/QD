/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.*;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.candle.*;
import junit.framework.TestCase;

public class CandleAddRemoveSubTest extends TestCase {
    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<Candle> sub;

    private final BlockingQueue<Object> added = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> removed = new LinkedBlockingQueue<>();
    private final BlockingQueue<Candle> received = new LinkedBlockingQueue<>();

    @Override
    protected void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
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
        sub.addEventListener(received::addAll);
    }

    @Override
    protected void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    public void testCandleAddRemoveSub() throws InterruptedException {
        CandleSymbol symbol = CandleSymbol.valueOf("T", CandlePeriod.valueOf(5, CandleType.MINUTE));

        // subscribe
        sub.addSymbols(symbol);
        assertEquals(symbol, added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall receive this event (subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(symbol, received.poll(1, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, received.size());

        // unsubscribe
        sub.removeSymbols(symbol);
        assertEquals(symbol, removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall NOT receive this event (not subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(null, received.poll(300, TimeUnit.MILLISECONDS));
    }

    public void testCandleAddClearSub() throws InterruptedException {
        CandleSymbol symbol = CandleSymbol.valueOf("T", CandlePeriod.valueOf(5, CandleType.MINUTE));

        // subscribe
        sub.addSymbols(symbol);
        assertEquals(symbol, added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall receive this event (subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(symbol, received.poll(1, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, received.size());

        // unsubscribe with clear
        sub.clear();
        assertEquals(symbol, removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall NOT receive this event (not subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(null, received.poll(300, TimeUnit.MILLISECONDS));
    }

    public void testCandleAddDetachSub() throws InterruptedException {
        CandleSymbol symbol = CandleSymbol.valueOf("T", CandlePeriod.valueOf(5, CandleType.MINUTE));

        // subscribe
        sub.addSymbols(symbol);
        assertEquals(symbol, added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall receive this event (subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(symbol, received.poll(1, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, received.size());

        // detach sub
        feed.detachSubscription(sub);
        assertEquals(symbol, removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall NOT receive this event (detached!)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol)));
        assertEquals(null, received.poll(300, TimeUnit.MILLISECONDS));
    }

    public void testCandleSetSub() throws InterruptedException {
        CandleSymbol symbol1 = CandleSymbol.valueOf("X", CandlePeriod.valueOf(5, CandleType.MINUTE));
        CandleSymbol symbol2 = CandleSymbol.valueOf("Y", CandlePeriod.valueOf(5, CandleType.MINUTE));

        // set sub to symbol1
        sub.setSymbols(symbol1);
        assertEquals(symbol1, added.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall receive this event (subscribed)
        publisher.publishEvents(Collections.singletonList(new Candle(symbol1)));
        assertEquals(symbol1, received.poll(1, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, received.size());

        // set sub to symbol2
        sub.setSymbols(symbol2);
        assertEquals(symbol2, added.poll(1, TimeUnit.SECONDS));
        assertEquals(symbol1, removed.poll(1, TimeUnit.SECONDS));
        assertEquals(0, added.size());
        assertEquals(0, removed.size());

        // shall receive only symbol2 event
        publisher.publishEvents(Collections.singletonList(new Candle(symbol1)));
        publisher.publishEvents(Collections.singletonList(new Candle(symbol2)));
        assertEquals(symbol2, received.poll(1, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, received.size());
    }
}
