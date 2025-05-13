/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
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
import com.dxfeed.event.custom.NuamTimeAndSale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NuamTimeAndSaleTest {
    private static final String SYMBOL = "TEST";

    private DXEndpoint endpoint;
    private DXPublisher publisher;
    private DXFeedSubscription<NuamTimeAndSale> sub;

    private final BlockingQueue<NuamTimeAndSale> queue = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(NuamTimeAndSale.class);
        sub.addEventListener(queue::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testNuamTimeAndSale() throws InterruptedException {
        NuamTimeAndSale tns = new NuamTimeAndSale(SYMBOL);
        tns.setTime(1000L);
        tns.setPrice(100D);
        tns.setMatchId(1L);
        tns.setTradeId(2L);

        publisher.publishEvents(Collections.singleton(tns));

        NuamTimeAndSale received = queue.take();
        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(1000L, received.getTime());
        assertEquals(100.0, received.getPrice(), 0.0);
        assertEquals(1L, received.getMatchId());
        assertEquals(2L, received.getTradeId());
        assertEquals(tns.toString(), received.toString());

        tns.setTime(2000L);
        tns.setPrice(200D);
        tns.setMatchId(1L);
        tns.setTradeId(2L);
        publisher.publishEvents(Collections.singleton(tns));

        received = queue.take();

        assertEquals(SYMBOL, received.getEventSymbol());
        assertEquals(2000L, received.getTime());
        assertEquals(200.0, received.getPrice(), 0.0);
        assertEquals(1L, received.getMatchId());
        assertEquals(2L, received.getTradeId());
        assertEquals(tns.toString(), received.toString());
    }

    @Test
    public void testEmptyNuamTimeAndSale() {
        NuamTimeAndSale tns = new NuamTimeAndSale();

        assertNull(tns.getEventSymbol());
        assertEquals(0, tns.getMatchId());
        assertEquals(0, tns.getTradeId());
    }
}
